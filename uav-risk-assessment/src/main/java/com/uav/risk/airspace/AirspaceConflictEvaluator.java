package com.uav.risk.airspace;

import com.uav.common.dto.RiskAssessmentResultDTO;
import com.uav.common.enums.AlarmLevel;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 空域冲突评估器
 * 检查飞行计划是否与已知空域（禁飞区/管制空域/其他计划）冲突
 */
@Component
public class AirspaceConflictEvaluator {

    /**
     * 评估空域冲突
     */
    public RiskAssessmentResultDTO.RiskItem evaluate(Map<String, Object> planData) {
        RiskAssessmentResultDTO.RiskItem item = new RiskAssessmentResultDTO.RiskItem();
        item.setCategory("空域冲突");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> waypoints =
                (List<Map<String, Object>>) planData.getOrDefault("waypoints", Collections.emptyList());

        // 检查每个航路点是否在禁飞区
        for (Map<String, Object> wp : waypoints) {
            double lat = toDouble(wp.get("latitude"));
            double lon = toDouble(wp.get("longitude"));

            if (isInRestrictedZone(lat, lon)) {
                item.setLevel(AlarmLevel.CRITICAL);
                item.setDescription("航路点 (" + lat + ", " + lon + ") 位于禁飞区内");
                item.setMitigation("修改航路避开禁飞区");
                return item;
            }

            if (isInControlledZone(lat, lon)) {
                item.setLevel(AlarmLevel.SERIOUS);
                item.setDescription("航路点 (" + lat + ", " + lon + ") 位于管制空域，需额外审批");
                item.setMitigation("申请管制空域使用许可或避开管制区域");
                return item;
            }
        }

        item.setLevel(AlarmLevel.GENERAL);
        item.setDescription("空域无冲突");
        item.setMitigation("");
        return item;
    }

    public Map<String, Object> getAirspaceStatus(Map<String, Object> planData) {
        // TODO: 从 PostGIS 查询空域占用状态
        return Map.of("status", "clear", "nearbyFlights", 0);
    }

    private boolean isInRestrictedZone(double lat, double lon) {
        // TODO: 使用 PostGIS ST_Contains 查询 geo_fence 表
        return false;
    }

    private boolean isInControlledZone(double lat, double lon) {
        // TODO: 查询管制空域
        return false;
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) return Double.parseDouble((String) obj);
        return 0;
    }
}
