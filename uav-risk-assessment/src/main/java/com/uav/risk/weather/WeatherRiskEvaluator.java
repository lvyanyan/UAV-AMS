package com.uav.risk.weather;

import com.uav.common.dto.RiskAssessmentResultDTO;
import com.uav.common.enums.AlarmLevel;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 气象风险评估器
 * 使用免费气象数据源 (Open-Meteo) 评估沿航路气象风险
 */
@Component
public class WeatherRiskEvaluator {

    private static final double MAX_SAFE_WIND = 12.0;     // m/s
    private static final double MIN_SAFE_VISIBILITY = 2000; // m
    private static final double MAX_SAFE_PRECIPITATION = 10.0; // mm/h

    /**
     * 评估气象风险
     */
    public RiskAssessmentResultDTO.RiskItem evaluate(Map<String, Object> planData) {
        RiskAssessmentResultDTO.RiskItem item = new RiskAssessmentResultDTO.RiskItem();
        item.setCategory("气象风险");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> waypoints =
                (List<Map<String, Object>>) planData.getOrDefault("waypoints", Collections.emptyList());

        // 对每个航路点查询气象预报
        double maxWind = 0;
        double minVisibility = Double.MAX_VALUE;

        for (Map<String, Object> wp : waypoints) {
            double lat = toDouble(wp.get("latitude"));
            double lon = toDouble(wp.get("longitude"));
            Map<String, Object> weather = fetchWeatherForecast(lat, lon);
            maxWind = Math.max(maxWind, toDouble(weather.get("windSpeed")));
            minVisibility = Math.min(minVisibility, toDouble(weather.get("visibility")));
        }

        if (maxWind > MAX_SAFE_WIND * 1.5) {
            item.setLevel(AlarmLevel.CRITICAL);
            item.setDescription("航路最大风速 " + String.format("%.1f", maxWind) + "m/s，严重超过安全阈值");
            item.setMitigation("推迟飞行计划，等待气象条件改善");
        } else if (maxWind > MAX_SAFE_WIND) {
            item.setLevel(AlarmLevel.SERIOUS);
            item.setDescription("航路最大风速 " + String.format("%.1f", maxWind) + "m/s，超过安全阈值");
            item.setMitigation("确认无人机抗风等级是否满足要求");
        } else if (minVisibility < MIN_SAFE_VISIBILITY) {
            item.setLevel(AlarmLevel.SERIOUS);
            item.setDescription("航路能见度不足 " + String.format("%.0f", minVisibility) + "m");
            item.setMitigation("推迟飞行计划");
        } else {
            item.setLevel(AlarmLevel.GENERAL);
            item.setDescription("气象条件正常 (风速 " + String.format("%.1f", maxWind) + "m/s)");
            item.setMitigation("");
        }

        return item;
    }

    public Map<String, Object> getForecast(Map<String, Object> planData) {
        // TODO: 调用 Open-Meteo API
        return Map.of("source", "open-meteo", "status", "ok");
    }

    public Map<String, Object> getCurrentRiskZones() {
        // TODO: 返回当前气象风险区域 GeoJSON
        return Map.of("type", "FeatureCollection", "features", Collections.emptyList());
    }

    /**
     * 获取指定位置气象预报
     * 数据源: Open-Meteo (免费, 无需 API Key)
     * https://api.open-meteo.com/v1/forecast
     */
    private Map<String, Object> fetchWeatherForecast(double lat, double lon) {
        // TODO: HTTP 调用 Open-Meteo API
        Map<String, Object> weather = new HashMap<>();
        weather.put("windSpeed", 5.0);
        weather.put("windGust", 8.0);
        weather.put("visibility", 5000.0);
        weather.put("precipitation", 0.0);
        weather.put("thunderstorm", false);
        return weather;
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) return Double.parseDouble((String) obj);
        return 0;
    }
}
