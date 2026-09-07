package com.uav.flightplan.service;

import com.uav.flightplan.entity.FlightPlan;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 审批任务创建监听器：把 BPMN 任务节点同步回 flight_plan.plan_status，
 * 保证前端按 PENDING_LEVEL1/2/3 展示的行为与原硬编码状态机完全一致。
 */
@Slf4j
@Component("planStatusSyncListener")
public class PlanStatusSyncListener implements TaskListener {

    private final FlightPlanService planService;

    public PlanStatusSyncListener(FlightPlanService planService) {
        this.planService = planService;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        long planId = Long.parseLong((String) delegateTask.getVariable("planId"));
        String status = switch (delegateTask.getCategory() == null ? "" : delegateTask.getCategory()) {
            case "1" -> "PENDING_LEVEL1";
            case "2" -> "PENDING_LEVEL2";
            case "3" -> "PENDING_LEVEL3";
            default -> null;
        };
        if (status == null) return;

        FlightPlan plan = planService.getById(planId);
        if (plan != null) {
            plan.setPlanStatus(status);
            planService.updateById(plan);
            log.info("计划 {} 状态推进至 {}（任务: {}）", planId, status, delegateTask.getName());
        }
    }
}
