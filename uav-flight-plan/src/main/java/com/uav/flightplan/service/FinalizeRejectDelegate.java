package com.uav.flightplan.service;

import com.uav.flightplan.entity.FlightPlan;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 驳回收口：任意审核节点 approved=false 后，由 BPMN serviceTask 调用，落最终状态 REJECTED。
 */
@Slf4j
@Component("finalizeRejectDelegate")
public class FinalizeRejectDelegate implements JavaDelegate {

    private final FlightPlanService planService;

    public FinalizeRejectDelegate(FlightPlanService planService) {
        this.planService = planService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        long planId = Long.parseLong((String) execution.getVariable("planId"));
        FlightPlan plan = planService.getById(planId);
        if (plan != null) {
            plan.setPlanStatus("REJECTED");
            planService.updateById(plan);
            log.info("计划 {} 被驳回，最终状态 REJECTED", planId);
        }
    }
}
