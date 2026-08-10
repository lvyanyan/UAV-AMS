package com.uav.risk.core;

import com.uav.common.dto.RiskAssessmentResultDTO;
import com.uav.common.enums.AlarmLevel;
import com.uav.common.interfaces.RiskAssessmentService;
import com.uav.risk.airspace.AirspaceConflictEvaluator;
import com.uav.risk.weather.WeatherRiskEvaluator;
import com.uav.risk.route.RouteSafetyEvaluator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险评估核心引擎
 * 五维独立评估 → 加权综合评分
 */
@Service
public class RiskAssessmentCore implements RiskAssessmentService {

    private final AirspaceConflictEvaluator airspaceEvaluator;
    private final WeatherRiskEvaluator weatherEvaluator;
    private final RouteSafetyEvaluator routeSafetyEvaluator;

    // 模拟的设备能力和人员资质评估（后续对接真实数据源）

    public RiskAssessmentCore(AirspaceConflictEvaluator airspaceEvaluator,
                               WeatherRiskEvaluator weatherEvaluator,
                               RouteSafetyEvaluator routeSafetyEvaluator) {
        this.airspaceEvaluator = airspaceEvaluator;
        this.weatherEvaluator = weatherEvaluator;
        this.routeSafetyEvaluator = routeSafetyEvaluator;
    }

    @Override
    public RiskAssessmentResultDTO assess(String flightPlanId, Map<String, Object> planData) {
        List<RiskAssessmentResultDTO.RiskItem> items = new ArrayList<>();
        int totalScore = 0;

        // 维度1: 空域冲突评估 (权重 30%)
        RiskAssessmentResultDTO.RiskItem airspaceRisk = airspaceEvaluator.evaluate(planData);
        items.add(airspaceRisk);
        totalScore += airspaceRisk.getLevel().getLevel() * 30;

        // 维度2: 气象风险评估 (权重 25%)
        RiskAssessmentResultDTO.RiskItem weatherRisk = weatherEvaluator.evaluate(planData);
        items.add(weatherRisk);
        totalScore += weatherRisk.getLevel().getLevel() * 25;

        // 维度3: 航路安全评估 (权重 20%)
        RiskAssessmentResultDTO.RiskItem routeRisk = routeSafetyEvaluator.evaluate(planData);
        items.add(routeRisk);
        totalScore += routeRisk.getLevel().getLevel() * 20;

        // 维度4: 设备能力评估 (权重 15%)
        RiskAssessmentResultDTO.RiskItem equipmentRisk = evaluateEquipmentCapability(planData);
        items.add(equipmentRisk);
        totalScore += equipmentRisk.getLevel().getLevel() * 15;

        // 维度5: 人员资质评估 (权重 10%)
        RiskAssessmentResultDTO.RiskItem pilotRisk = evaluatePilotQualification(planData);
        items.add(pilotRisk);
        totalScore += pilotRisk.getLevel().getLevel() * 10;

        // 综合评分: 归一化为 0-100 (原始 100-300)
        int riskScore = Math.min(100, Math.max(0, (totalScore - 100) * 100 / 200));

        RiskAssessmentResultDTO result = new RiskAssessmentResultDTO();
        result.setFlightPlanId(flightPlanId);
        result.setRiskScore(riskScore);
        result.setRiskItems(items);
        result.setApproved(riskScore < 60); // 60分以下通过
        result.setRiskLevel(determineLevel(riskScore));
        result.setSuggestion(generateSuggestion(items));
        result.setDetails(buildDetails(planData));

        return result;
    }

    @Override
    public boolean preCheck(Map<String, Object> planData) {
        // 快速预检：只做空域和气象两项关键检查
        RiskAssessmentResultDTO.RiskItem airspace = airspaceEvaluator.evaluate(planData);
        if (airspace.getLevel() == AlarmLevel.CRITICAL) return false;

        RiskAssessmentResultDTO.RiskItem weather = weatherEvaluator.evaluate(planData);
        if (weather.getLevel() == AlarmLevel.CRITICAL) return false;

        return true;
    }

    @Override
    public Map<String, Object> getWeatherRiskZones() {
        return weatherEvaluator.getCurrentRiskZones();
    }

    // ===== 私有方法 =====

    private RiskAssessmentResultDTO.RiskItem evaluateEquipmentCapability(Map<String, Object> planData) {
        RiskAssessmentResultDTO.RiskItem item = new RiskAssessmentResultDTO.RiskItem();
        item.setCategory("设备能力");

        // 检查无人机型号是否适合该航程
        Object maxEndurance = planData.getOrDefault("maxEnduranceMinutes", 0);
        Object estimatedDuration = planData.getOrDefault("estimatedDurationMinutes", 0);
        int endurance = toInt(maxEndurance);
        int duration = toInt(estimatedDuration);

        if (duration > endurance) {
            item.setLevel(AlarmLevel.CRITICAL);
            item.setDescription("预计飞行时长 " + duration + "min 超过无人机续航 " + endurance + "min");
            item.setMitigation("更换长续航机型或缩短航程");
        } else if (duration > endurance * 0.8) {
            item.setLevel(AlarmLevel.SERIOUS);
            item.setDescription("预计飞行时长接近续航上限，无安全余量");
            item.setMitigation("建议预留至少20%电量余量");
        } else {
            item.setLevel(AlarmLevel.GENERAL);
            item.setDescription("设备续航正常");
            item.setMitigation("");
        }
        return item;
    }

    private RiskAssessmentResultDTO.RiskItem evaluatePilotQualification(Map<String, Object> planData) {
        RiskAssessmentResultDTO.RiskItem item = new RiskAssessmentResultDTO.RiskItem();
        item.setCategory("人员资质");

        Object licenseValid = planData.getOrDefault("licenseValid", true);
        Object medicalValid = planData.getOrDefault("medicalValid", true);
        Object flightHours = planData.getOrDefault("pilotFlightHours", 0);

        if (!Boolean.TRUE.equals(licenseValid)) {
            item.setLevel(AlarmLevel.CRITICAL);
            item.setDescription("驾驶员执照无效或已过期");
            item.setMitigation("更新执照后再提交计划");
        } else if (!Boolean.TRUE.equals(medicalValid)) {
            item.setLevel(AlarmLevel.SERIOUS);
            item.setDescription("驾驶员体检报告已过期");
            item.setMitigation("上传最新体检报告");
        } else if (toInt(flightHours) < 10) {
            item.setLevel(AlarmLevel.GENERAL);
            item.setDescription("驾驶员飞行经历较少 (" + flightHours + " 小时)");
            item.setMitigation("建议在有经验的观察员陪同下飞行");
        } else {
            item.setLevel(AlarmLevel.GENERAL);
            item.setDescription("驾驶员资质正常");
            item.setMitigation("");
        }
        return item;
    }

    private AlarmLevel determineLevel(int score) {
        if (score >= 80) return AlarmLevel.CRITICAL;
        if (score >= 50) return AlarmLevel.SERIOUS;
        return AlarmLevel.GENERAL;
    }

    private String generateSuggestion(List<RiskAssessmentResultDTO.RiskItem> items) {
        StringBuilder sb = new StringBuilder();
        for (RiskAssessmentResultDTO.RiskItem item : items) {
            if (item.getLevel() == AlarmLevel.CRITICAL) {
                sb.append("【严重】").append(item.getDescription()).append("；");
            }
        }
        if (sb.length() == 0) {
            sb.append("风险可控，建议批准飞行计划。");
        } else {
            sb.append("请处理以上严重风险后重新提交。");
        }
        return sb.toString();
    }

    private Map<String, Object> buildDetails(Map<String, Object> planData) {
        Map<String, Object> details = new HashMap<>();
        details.put("airspaceStatus", airspaceEvaluator.getAirspaceStatus(planData));
        details.put("weatherForecast", weatherEvaluator.getForecast(planData));
        return details;
    }

    private int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof String) return Integer.parseInt((String) obj);
        return 0;
    }
}
