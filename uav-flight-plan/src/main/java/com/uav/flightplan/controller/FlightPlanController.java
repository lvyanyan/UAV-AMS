package com.uav.flightplan.controller;

import com.uav.common.base.R;
import com.uav.flightplan.dto.ReleaseCheckResult;
import com.uav.flightplan.entity.FlightPlan;
import com.uav.flightplan.entity.FlightPlanApproval;
import com.uav.flightplan.entity.UavRoute;
import com.uav.flightplan.kafka.PlanEventProducer;
import com.uav.flightplan.service.ComplianceService;
import com.uav.flightplan.service.FlightPlanProcessService;
import com.uav.flightplan.service.FlightPlanService;
import com.uav.flightplan.service.FlightPlanApprovalService;
import com.uav.flightplan.service.ReleaseCheckService;
import com.uav.flightplan.service.UavRouteService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/flight-plan")
public class FlightPlanController {

    private static final Logger log = LoggerFactory.getLogger(FlightPlanController.class);

    private final FlightPlanService planService;
    private final FlightPlanApprovalService approvalService;
    private final FlightPlanProcessService processService;
    private final UavRouteService routeService;
    private final ComplianceService complianceService;
    private final ReleaseCheckService releaseCheckService;
    private final PlanEventProducer planEventProducer;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();

    public FlightPlanController(FlightPlanService planService,
                                FlightPlanApprovalService approvalService,
                                FlightPlanProcessService processService,
                                UavRouteService routeService,
                                ComplianceService complianceService,
                                ReleaseCheckService releaseCheckService,
                                PlanEventProducer planEventProducer,
                                org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.planService = planService;
        this.approvalService = approvalService;
        this.processService = processService;
        this.routeService = routeService;
        this.complianceService = complianceService;
        this.releaseCheckService = releaseCheckService;
        this.planEventProducer = planEventProducer;
        this.jdbc = jdbc;
    }

    // ===== 计划 CRUD =====
    @PostMapping
    public R<FlightPlan> create(@Valid @RequestBody FlightPlan plan) {
        plan.setId(null);
        plan.setPlanStatus("DRAFT");
        return R.ok(planService.createWithCode(plan));
    }

    @GetMapping("/{id}")
    public R<FlightPlan> getById(@PathVariable Long id) {
        return R.ok(planService.getById(id));
    }

    @GetMapping("/list")
    public R<List<FlightPlan>> list() {
        sweepLifecycle();
        return R.ok(planService.list());
    }

    @GetMapping("/list/status/{status}")
    public R<List<FlightPlan>> listByStatus(@PathVariable String status) {
        sweepLifecycle();
        return R.ok(planService.lambdaQuery()
                .eq(FlightPlan::getPlanStatus, status).list());
    }

    /**
     * 生命周期收口（查询时惰性触发，用数据库时钟避免时区漂移）：
     *   IN_FLIGHT 且 planned_end 已过     → COMPLETED（补 actual_end）
     *   RELEASED 且 planned_end 已过（超时未起飞）→ EXPIRED
     *   APPROVED 且 planned_end 已过（始终未放行）→ EXPIRED
     */
    private void sweepLifecycle() {
        jdbc.update("update flight_plan set plan_status = 'COMPLETED', "
                + "actual_end = coalesce(actual_end, now()) "
                + "where plan_status = 'IN_FLIGHT' and planned_end < now()");
        jdbc.update("update flight_plan set plan_status = 'EXPIRED' "
                + "where plan_status = 'RELEASED' and planned_end < now()");
        jdbc.update("update flight_plan set plan_status = 'EXPIRED' "
                + "where plan_status = 'APPROVED' and planned_end < now()");
    }

    @PutMapping("/{id}")
    public R<FlightPlan> update(@PathVariable Long id, @RequestBody FlightPlan plan) {
        plan.setId(id);
        planService.updateById(plan);
        return R.ok(planService.getById(id));
    }

    // ===== 多级审批（Flowable BPMN 驱动） =====
    // 流程定义: processes/flight-plan-approval.bpmn20.xml
    // DRAFT → PENDING_LEVEL1 → PENDING_LEVEL2 → PENDING_LEVEL3 → APPROVED
    // 任意阶段可 REJECTED; 军民协调员可 ONE_CLICK_APPROVED（终止在途流程直批）

    @PutMapping("/{id}/submit")
    public R<FlightPlan> submit(@PathVariable Long id) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");

        // 合规校验：起降点 + 航路点不得落入禁飞区（NO_FLY），命中即拒绝提交
        List<String> violations = complianceCheck(plan);
        if (!violations.isEmpty()) {
            return R.fail("合规校验未通过：" + String.join("；", violations));
        }
        FlightPlan submitted = planService.submit(id);
        if (submitted != null) processService.startApproval(submitted);
        return R.ok(submitted);
    }

    /** 汇集计划的空间点（起降场坐标 + 航路点）做禁飞区校验 */
    private List<String> complianceCheck(FlightPlan plan) {
        List<double[]> points = new java.util.ArrayList<>();
        try {
            for (String name : new String[]{plan.getDeparture(), plan.getDestination()}) {
                if (name == null || name.isBlank()) continue;
                points.addAll(jdbc.query(
                    "select lon, lat from uav_airport where is_active = true and airport_name = ?",
                    rs -> {
                        List<double[]> l = new java.util.ArrayList<>();
                        while (rs.next()) l.add(new double[]{rs.getDouble(1), rs.getDouble(2)});
                        return l;
                    }, name));
            }
            if (plan.getRouteId() != null) {
                UavRoute route = routeService.getById(plan.getRouteId());
                if (route != null && route.getWaypoints() != null) {
                    JsonNode wp = om.readTree(route.getWaypoints());
                    if (wp.isArray()) {
                        for (JsonNode p : wp) points.add(new double[]{p.get(0).asDouble(), p.get(1).asDouble()});
                    }
                }
            }
        } catch (Exception e) {
            log.warn("合规校验取点失败（放行）: {}", e.getMessage());
        }
        return complianceService.checkPoints(points);
    }

    @PutMapping("/{id}/approve")
    public R<FlightPlan> approve(@PathVariable Long id,
                                  @RequestParam Long approverId,
                                  @RequestParam(required = false) String comment) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");
        return R.ok(processService.approve(id, approverId, comment));
    }

    @PutMapping("/{id}/reject")
    public R<FlightPlan> reject(@PathVariable Long id,
                                 @RequestParam Long approverId,
                                 @RequestParam String comment) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");
        return R.ok(processService.reject(id, approverId, comment));
    }

    // ===== 军民协调：一键批准（跳过全部审批链） =====
    @PutMapping("/{id}/military-one-click")
    public R<FlightPlan> militaryOneClickApprove(@PathVariable Long id,
                                                  @RequestParam Long militaryApproverId) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");

        processService.terminateProcess(id, "军事调度一键批准");
        plan.setPlanStatus("APPROVED");
        plan.setMilitaryApproved(true);
        plan.setMilitaryApprovalId(militaryApproverId);
        planService.updateById(plan);

        FlightPlanApproval approval = new FlightPlanApproval();
        approval.setPlanId(id);
        approval.setApproverId(militaryApproverId);
        approval.setApprovalLevel(99);
        approval.setResult("MILITARY_ONE_CLICK");
        approval.setComment("军事调度一键批准");
        approvalService.save(approval);

        return R.ok(plan);
    }

    @PutMapping("/{id}/military-cancel")
    public R<FlightPlan> militaryCancel(@PathVariable Long id,
                                         @RequestParam Long militaryApproverId,
                                         @RequestParam String reason) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");

        processService.terminateProcess(id, "军事协调撤销: " + reason);
        plan.setPlanStatus("MILITARY_CANCELLED");
        plan.setMilitaryApproved(false);
        plan.setMilitaryApprovalId(militaryApproverId);
        planService.updateById(plan);

        FlightPlanApproval approval = new FlightPlanApproval();
        approval.setPlanId(id);
        approval.setApproverId(militaryApproverId);
        approval.setApprovalLevel(99);
        approval.setResult("MILITARY_CANCELLED");
        approval.setComment(reason);
        approvalService.save(approval);

        return R.ok(plan);
    }

    @GetMapping("/{id}/approvals")
    public R<List<FlightPlanApproval>> getApprovals(@PathVariable Long id) {
        return R.ok(approvalService.lambdaQuery()
                .eq(FlightPlanApproval::getPlanId, id)
                .orderByAsc(FlightPlanApproval::getCreateTime).list());
    }

    // ===== 放行 → 起飞 → 在飞 → 降落/中止 闭环 =====
    // 状态机：APPROVED → RELEASED → IN_FLIGHT → COMPLETED；放行后取消 → CANCELLED；窗口过期 → EXPIRED

    /**
     * 放行：先跑五项检查清单（状态/实名登记/飞手资质/时间窗/禁飞区复检）。
     * 全部通过 → RELEASED + Kafka；任何一项不过 → HTTP 400，响应体带 checks 清单。
     * 无论成败响应都含 checks 数组（成功在 R.data.checks，失败在 R.data.checks）。
     */
    @PutMapping("/{id}/release")
    public ResponseEntity<R<ReleaseCheckResult>> release(@PathVariable Long id) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) {
            return ResponseEntity.badRequest().body(R.fail(400, "计划不存在"));
        }
        ReleaseCheckResult result = releaseCheckService.check(plan);
        if (!result.isPassed()) {
            R<ReleaseCheckResult> body = R.fail(400, "放行检查未通过，请处理失败项后重试");
            body.setData(result);
            return ResponseEntity.badRequest().body(body);
        }
        plan.setPlanStatus("RELEASED");
        planService.updateById(plan);
        log.info("计划 {} 放行通过，状态 → RELEASED", plan.getPlanCode());
        return ResponseEntity.ok(R.ok(result));
    }

    /** 起飞：RELEASED → 下发 Kafka TAKEOFF 指令（经 realtime 桥转 MQTT）。状态等仿真器 TAKEOFF_ACK 遥测回流后置 IN_FLIGHT。 */
    @PutMapping("/{id}/takeoff")
    public R<FlightPlan> takeoff(@PathVariable Long id) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");
        if (!"RELEASED".equals(plan.getPlanStatus())) {
            return R.fail("仅 RELEASED 状态可下发起飞指令，当前状态 " + plan.getPlanStatus());
        }
        planEventProducer.sendTakeoff(plan, plan.getAltCeilingM());
        plan.setCmdSentAt(LocalDateTime.now());
        planService.updateById(plan);
        return R.ok(plan);
    }

    /** 中止：RELEASED/IN_FLIGHT → 下发 Kafka ABORT 指令（仿真器紧急降落），状态直接置 CANCELLED。 */
    @PutMapping("/{id}/abort")
    public R<FlightPlan> abort(@PathVariable Long id) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");
        if (!"RELEASED".equals(plan.getPlanStatus()) && !"IN_FLIGHT".equals(plan.getPlanStatus())) {
            return R.fail("仅 RELEASED/IN_FLIGHT 状态可中止，当前状态 " + plan.getPlanStatus());
        }
        planEventProducer.sendAbort(plan);
        plan.setPlanStatus("CANCELLED");
        planService.updateById(plan);
        log.info("计划 {} 已中止，状态 → CANCELLED", plan.getPlanCode());
        return R.ok(plan);
    }

    /** 返航：IN_FLIGHT → 下发 Kafka RTL 指令（仿真器返航降落），状态不变（降落后由生命周期事件收口 COMPLETED）。 */
    @PutMapping("/{id}/rtl")
    public R<FlightPlan> rtl(@PathVariable Long id) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");
        if (!"IN_FLIGHT".equals(plan.getPlanStatus())) {
            return R.fail("仅 IN_FLIGHT 状态可指令返航，当前状态 " + plan.getPlanStatus());
        }
        planEventProducer.sendRtl(plan);
        return R.ok(plan);
    }
}
