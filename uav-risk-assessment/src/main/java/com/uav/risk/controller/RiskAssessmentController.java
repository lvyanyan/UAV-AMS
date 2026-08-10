package com.uav.risk.controller;

import com.uav.common.base.R;
import com.uav.common.dto.RiskAssessmentResultDTO;
import com.uav.risk.core.RiskAssessmentCore;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 风险评估 REST API
 */
@RestController
@RequestMapping("/api/v1/risk")
public class RiskAssessmentController {

    private final RiskAssessmentCore riskCore;

    public RiskAssessmentController(RiskAssessmentCore riskCore) {
        this.riskCore = riskCore;
    }

    /**
     * 对飞行计划进行完整风险评估
     */
    @PostMapping("/assess/{planId}")
    public R<RiskAssessmentResultDTO> assess(
            @PathVariable String planId,
            @RequestBody Map<String, Object> planData) {
        RiskAssessmentResultDTO result = riskCore.assess(planId, planData);
        return R.ok(result);
    }

    /**
     * 快速预检
     */
    @PostMapping("/pre-check")
    public R<Boolean> preCheck(@RequestBody Map<String, Object> planData) {
        boolean pass = riskCore.preCheck(planData);
        return R.ok(pass);
    }

    /**
     * 获取当前气象风险区域
     */
    @GetMapping("/weather-zones")
    public R<Map<String, Object>> weatherZones() {
        return R.ok(riskCore.getWeatherRiskZones());
    }
}
