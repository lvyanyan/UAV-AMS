package com.uav.common.interfaces;

import com.uav.common.dto.RiskAssessmentResultDTO;

import java.util.Map;

/**
 * 飞行计划风险评估接口 - uav-risk-assessment 实现
 * 独立程序，对飞行计划进行多维度风险评估
 */
public interface RiskAssessmentService {

    /**
     * 对飞行计划进行风险评估
     * @param flightPlanId 飞行计划ID
     * @param planData 计划数据（航路点、时间、无人机信息等）
     * @return 风险评估结果
     */
    RiskAssessmentResultDTO assess(String flightPlanId, Map<String, Object> planData);

    /**
     * 快速预检（计划提交时实时调用）
     * @return 是否通过
     */
    boolean preCheck(Map<String, Object> planData);

    /**
     * 获取当前气象风险区域
     */
    Map<String, Object> getWeatherRiskZones();
}
