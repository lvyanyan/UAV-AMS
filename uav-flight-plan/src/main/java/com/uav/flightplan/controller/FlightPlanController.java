package com.uav.flightplan.controller;

import com.uav.common.base.R;
import com.uav.flightplan.entity.FlightPlan;
import com.uav.flightplan.entity.FlightPlanApproval;
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

    public FlightPlanController(FlightPlanService planService, FlightPlanApprovalService approvalService) {
        this.planService = planService;
        this.approvalService = approvalService;
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
        return R.ok(planService.list());
    }

    @GetMapping("/list/status/{status}")
    public R<List<FlightPlan>> listByStatus(@PathVariable String status) {
        return R.ok(planService.lambdaQuery()
                .eq(FlightPlan::getPlanStatus, status).list());
    }

    @PutMapping("/{id}")
    public R<FlightPlan> update(@PathVariable Long id, @RequestBody FlightPlan plan) {
        plan.setId(id);
        planService.updateById(plan);
        return R.ok(planService.getById(id));
    }

    // ===== 多级审批状态机 =====
    // DRAFT → PENDING_LEVEL1 → PENDING_LEVEL2 → PENDING_LEVEL3 → APPROVED
    // 任意阶段可 REJECTED; 军民协调员可 ONE_CLICK_APPROVED

    @PutMapping("/{id}/submit")
    public R<FlightPlan> submit(@PathVariable Long id) {
        return R.ok(planService.submit(id));
    }

    @PutMapping("/{id}/approve")
    public R<FlightPlan> approve(@PathVariable Long id,
                                  @RequestParam Long approverId,
                                  @RequestParam(required = false) String comment) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");

        String currentStatus = plan.getPlanStatus();
        String nextStatus;
        int approvalLevel;

        if ("PENDING_LEVEL1".equals(currentStatus)) {
            nextStatus = "PENDING_LEVEL2";
            approvalLevel = 1;
        } else if ("PENDING_LEVEL2".equals(currentStatus)) {
            nextStatus = "PENDING_LEVEL3";
            approvalLevel = 2;
        } else if ("PENDING_LEVEL3".equals(currentStatus)) {
            nextStatus = "APPROVED";
            approvalLevel = 3;
        } else {
            return R.fail("当前状态不可审批: " + currentStatus);
        }

        plan.setPlanStatus(nextStatus);
        planService.updateById(plan);

        FlightPlanApproval approval = new FlightPlanApproval();
        approval.setPlanId(id);
        approval.setApproverId(approverId);
        approval.setApprovalLevel(approvalLevel);
        approval.setResult("APPROVED");
        approval.setComment(comment);
        approvalService.save(approval);

        return R.ok(plan);
    }

    @PutMapping("/{id}/reject")
    public R<FlightPlan> reject(@PathVariable Long id,
                                 @RequestParam Long approverId,
                                 @RequestParam String comment) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");

        plan.setPlanStatus("REJECTED");
        planService.updateById(plan);

        FlightPlanApproval approval = new FlightPlanApproval();
        approval.setPlanId(id);
        approval.setApproverId(approverId);
        approval.setApprovalLevel(0);
        approval.setResult("REJECTED");
        approval.setComment(comment);
        approvalService.save(approval);

        return R.ok(plan);
    }

    // ===== 军民协调：一键批准（跳过全部审批链） =====
    @PutMapping("/{id}/military-one-click")
    public R<FlightPlan> militaryOneClickApprove(@PathVariable Long id,
                                                  @RequestParam Long militaryApproverId) {
        FlightPlan plan = planService.getById(id);
        if (plan == null) return R.fail("计划不存在");

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
