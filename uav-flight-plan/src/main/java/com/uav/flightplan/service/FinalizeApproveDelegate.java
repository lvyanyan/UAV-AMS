package com.uav.flightplan.service;

import com.uav.flightplan.entity.FlightPlan;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 批准收口：三级审核全部通过后，由 BPMN serviceTask 调用，落最终状态 APPROVED。
 */
@Slf4j
@Component("finalizeApproveDelegate")
public class FinalizeApproveDelegate implements JavaDelegate {

    private final FlightPlanService planService;

    public FinalizeApproveDelegate(FlightPlanService planService) {
        this.planService = planService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        long planId = Long.parseLong((String) execution.getVariable("planId"));
        FlightPlan plan = planService.getById(planId);
        if (plan != null) {
            plan.setPlanStatus("APPROVED");
            planService.updateById(plan);
            log.info("计划 {} 审批链走完，最终状态 APPROVED", planId);
        }
    }
}
