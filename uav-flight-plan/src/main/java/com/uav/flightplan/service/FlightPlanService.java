package com.uav.flightplan.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.flightplan.entity.FlightPlan;
import com.uav.flightplan.mapper.FlightPlanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class FlightPlanService extends ServiceImpl<FlightPlanMapper, FlightPlan> {

    /** 提交计划 → 状态从 DRAFT 变为 PENDING_LEVEL1 */
    @Transactional
    public FlightPlan submit(Long planId) {
        FlightPlan plan = getById(planId);
        if (plan != null && "DRAFT".equals(plan.getPlanStatus())) {
            plan.setPlanStatus("PENDING_LEVEL1");
            plan.setSubmitTime(LocalDateTime.now());
            updateById(plan);
        }
        return plan;
    }
}
