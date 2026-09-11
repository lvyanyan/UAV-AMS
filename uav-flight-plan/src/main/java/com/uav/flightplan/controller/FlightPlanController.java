package com.uav.flightplan.controller;

import com.uav.common.base.R;
import com.uav.flightplan.entity.FlightPlan;
import com.uav.flightplan.entity.FlightPlanApproval;
import com.uav.flightplan.service.FlightPlanProcessService;
import com.uav.flightplan.service.FlightPlanService;
import com.uav.flightplan.service.FlightPlanApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/flight-plan")
public class FlightPlanController {

    private final FlightPlanService planService;
    private final FlightPlanApprovalService approvalService;
    private final FlightPlanProcessService processService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public FlightPlanController(FlightPlanService planService,
                                FlightPlanApprovalService approvalService,
                                FlightPlanProcessService processService,
                                org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.planService = planService;
        this.approvalService = approvalService;
        this.processService = processService;
        this.jdbc = jdbc;
    }

    // ===== 计划 CRUD =====
    @PostMapping
    public R<FlightPlan> create(@Valid @RequestBody FlightPlan plan) {
        plan.setPlanStatus("DRAFT");
        planService.save(plan);
        return R.ok(plan);
    }

    @GetMapping("/{id}")
    public R<FlightPlan> getById(@PathVariable Long id) {
        return R.ok(planService.getById(id));
    }

    @GetMapping("/list")
    public R<List<FlightPlan>> list() {
        sweepCompleted();
        return R.ok(planService.list());
    }

    @GetMapping("/list/status/{status}")
    public R<List<FlightPlan>> listByStatus(@PathVariable String status) {
        sweepCompleted();
        return R.ok(planService.lambdaQuery()
                .eq(FlightPlan::getPlanStatus, status).list());
    }

    /** 生命周期收口：已批准且计划结束时间已过的计划自动置为 COMPLETED（查询时惰性触发，用数据库时钟避免时区漂移） */
    private void sweepCompleted() {
        jdbc.update("update flight_plan set plan_status = 'COMPLETED' "
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
        FlightPlan plan = planService.submit(id);
        if (plan != null) processService.startApproval(plan);
        return R.ok(plan);
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
}
