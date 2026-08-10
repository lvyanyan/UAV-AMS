package com.uav.risk.route;

import com.uav.common.dto.RiskAssessmentResultDTO;
import com.uav.common.enums.AlarmLevel;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 航路安全评估器
 * 检查飞行计划航路是否经过高风险区域（人口密集区、机场附近、高压线等）
 */
@Component
public class RouteSafetyEvaluator {

    private static final double AIRPORT_NO_FLY_RADIUS = 5000; // 机场周边禁飞半径 5km
    private static final double POPULATION_BUFFER = 500;      // 人口密集区缓冲 500m

    /**
     * 评估航路安全
     */
    public RiskAssessmentResultDTO.RiskItem evaluate(Map<String, Object> planData) {
        RiskAssessmentResultDTO.RiskItem item = new RiskAssessmentResultDTO.RiskItem();
        item.setCategory("航路安全");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> waypoints =
                (List<Map<String, Object>>) planData.getOrDefault("waypoints", Collections.emptyList());

        // 检查是否经过机场附近
        for (Map<String, Object> wp : waypoints) {
            double lat = toDouble(wp.get("latitude"));
            double lon = toDouble(wp.get("longitude"));

            if (isNearAirport(lat, lon)) {
                item.setLevel(AlarmLevel.CRITICAL);
                item.setDescription("航路经过机场周边 " + AIRPORT_NO_FLY_RADIUS / 1000 + "km 禁飞范围");
                item.setMitigation("必须申请机场空域特别许可或重新规划航路");
                return item;
            }

            if (isOverPopulatedArea(lat, lon)) {
                item.setLevel(AlarmLevel.SERIOUS);
                item.setDescription("航路经过人口密集区上空，存在公共安全风险");
                item.setMitigation("降低飞行高度或绕行人口密集区");
                return item;
            }
        }

        // 检查飞行高度是否合理
        Object maxAlt = planData.get("maxAltitude");
        if (maxAlt != null && toDouble(maxAlt) > 500) {
            item.setLevel(AlarmLevel.SERIOUS);
            item.setDescription("飞行高度超过 500m，可能与载人航空器产生冲突");
            item.setMitigation("限制飞行高度在 500m 以下或申请特殊空域");
            return item;
        }

        item.setLevel(AlarmLevel.GENERAL);
        item.setDescription("航路安全，无已知风险区域");
        item.setMitigation("");
        return item;
    }

    private boolean isNearAirport(double lat, double lon) {
        // TODO: 从数据库查询机场位置，使用 PostGIS ST_DWithin
        return false;
    }

    private boolean isOverPopulatedArea(double lat, double lon) {
        // TODO: 查询人口密度数据 (WorldPop 或本地数据)
        return false;
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) return Double.parseDouble((String) obj);
        return 0;
    }
}
