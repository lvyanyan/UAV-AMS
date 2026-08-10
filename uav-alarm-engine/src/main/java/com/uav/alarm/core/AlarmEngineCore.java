package com.uav.alarm.core;

import com.uav.common.dto.AlarmEventDTO;
import com.uav.common.dto.TelemetryDTO;
import com.uav.common.enums.AlarmLevel;
import com.uav.common.enums.AlarmType;
import com.uav.common.interfaces.AlarmEngineService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 告警引擎核心 - 六维告警评估
 *
 * 每个维度独立评估，互不干扰，可单独开关
 */
@Service
public class AlarmEngineCore implements AlarmEngineService {

    private final AlarmRuleConfig ruleConfig;

    public AlarmEngineCore(AlarmRuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    @Override
    public List<AlarmEventDTO> evaluate(TelemetryDTO t) {
        List<AlarmEventDTO> alarms = new ArrayList<>();

        // 维度1: 空域入侵检测
        if (ruleConfig.isAirspaceEnabled()) {
            AlarmEventDTO airspaceAlarm = checkAirspaceIntrusion(t);
            if (airspaceAlarm != null) alarms.add(airspaceAlarm);
        }

        // 维度2: 无人机冲突检测 (需要邻近查询，由外部传入邻近无人机列表时触发)
        // 此处为单机遥测评估，冲突检测在 Go 实时服务中处理

        // 维度3: 气象风险评估
        if (ruleConfig.isWeatherEnabled()) {
            AlarmEventDTO weatherAlarm = checkWeatherRisk(t);
            if (weatherAlarm != null) alarms.add(weatherAlarm);
        }

        // 维度4: 航路偏离检测
        if (ruleConfig.isRouteEnabled()) {
            AlarmEventDTO routeAlarm = checkRouteDeviation(t);
            if (routeAlarm != null) alarms.add(routeAlarm);
        }

        // 维度5: 设备故障检测
        if (ruleConfig.isEquipmentEnabled()) {
            List<AlarmEventDTO> equipmentAlarms = checkEquipmentFault(t);
            alarms.addAll(equipmentAlarms);
        }

        // 维度6: 地形碰撞检测
        if (ruleConfig.isTerrainEnabled()) {
            AlarmEventDTO terrainAlarm = checkTerrainCollision(t);
            if (terrainAlarm != null) alarms.add(terrainAlarm);
        }

        return alarms;
    }

    // ==================== 各维度实现 ====================

    private AlarmEventDTO checkAirspaceIntrusion(TelemetryDTO t) {
        // 基于 H3 网格判断是否进入禁飞区/管制空域
        if (t.getH3Index() == null) return null;

        boolean inRestricted = ruleConfig.isH3InRestrictedZone(t.getH3Index());
        if (inRestricted) {
            return buildAlarm(t, AlarmType.AIRSPACE_INTRUSION, AlarmLevel.CRITICAL,
                    "无人机进入禁飞空域",
                    "H3 网格 " + t.getH3Index() + " 位于禁飞区",
                    null, null);
        }
        return null;
    }

    private AlarmEventDTO checkWeatherRisk(TelemetryDTO t) {
        // 获取当前位置气象数据，判断风速/能见度/雷暴风险
        WeatherSnapshot weather = ruleConfig.getWeatherAt(t.getLatitude(), t.getLongitude());
        if (weather == null) return null;

        // 风速超限
        if (t.getGroundSpeed() != null && weather.getWindSpeed() > ruleConfig.getMaxWindSpeed()) {
            return buildAlarm(t, AlarmType.WEATHER_RISK, AlarmLevel.SERIOUS,
                    "风速超限",
                    "当前风速 " + weather.getWindSpeed() + "m/s，超过阈值 " + ruleConfig.getMaxWindSpeed() + "m/s",
                    ruleConfig.getMaxWindSpeed(), weather.getWindSpeed());
        }
        // 能见度不足
        if (weather.getVisibility() < ruleConfig.getMinVisibility()) {
            return buildAlarm(t, AlarmType.WEATHER_RISK, AlarmLevel.SERIOUS,
                    "能见度不足",
                    "当前能见度 " + weather.getVisibility() + "m，低于阈值 " + ruleConfig.getMinVisibility() + "m",
                    ruleConfig.getMinVisibility(), weather.getVisibility());
        }
        return null;
    }

    private AlarmEventDTO checkRouteDeviation(TelemetryDTO t) {
        // 判断是否偏离已批准的飞行计划航路
        if (t.getFlightPlanId() == null) return null;

        double deviation = ruleConfig.calculateRouteDeviation(t.getFlightPlanId(),
                t.getLatitude(), t.getLongitude());
        double maxDeviation = ruleConfig.getMaxRouteDeviationMeters();
        if (deviation > maxDeviation) {
            return buildAlarm(t, AlarmType.ROUTE_DEVIATION, AlarmLevel.SERIOUS,
                    "航路偏离",
                    "偏离计划航路 " + String.format("%.0f", deviation) + "m，阈值 " + maxDeviation + "m",
                    maxDeviation, deviation);
        }
        return null;
    }

    private List<AlarmEventDTO> checkEquipmentFault(TelemetryDTO t) {
        List<AlarmEventDTO> alarms = new ArrayList<>();

        // 低电量告警
        if (t.getBatteryPercent() != null) {
            if (t.getBatteryPercent() <= ruleConfig.getBatteryCritical()) {
                alarms.add(buildAlarm(t, AlarmType.BATTERY_LOW, AlarmLevel.CRITICAL,
                        "电量危急", "电量 " + t.getBatteryPercent() + "%",
                        (double) ruleConfig.getBatteryCritical(), t.getBatteryPercent().doubleValue()));
            } else if (t.getBatteryPercent() <= ruleConfig.getBatteryWarning()) {
                alarms.add(buildAlarm(t, AlarmType.BATTERY_LOW, AlarmLevel.GENERAL,
                        "电量偏低", "电量 " + t.getBatteryPercent() + "%",
                        (double) ruleConfig.getBatteryWarning(), t.getBatteryPercent().doubleValue()));
            }
        }

        // 信号丢失
        if (t.getRssi() != null && t.getRssi() < ruleConfig.getMinRssi()) {
            alarms.add(buildAlarm(t, AlarmType.SIGNAL_LOST, AlarmLevel.SERIOUS,
                    "信号弱", "RSSI " + t.getRssi(),
                    (double) ruleConfig.getMinRssi(), t.getRssi().doubleValue()));
        }

        // GPS 卫星数不足
        if (t.getGpsSatellites() != null && t.getGpsSatellites() < ruleConfig.getMinGpsSatellites()) {
            alarms.add(buildAlarm(t, AlarmType.EQUIPMENT_FAULT, AlarmLevel.GENERAL,
                    "GPS卫星数不足", "卫星数 " + t.getGpsSatellites(),
                    (double) ruleConfig.getMinGpsSatellites(), t.getGpsSatellites().doubleValue()));
        }

        return alarms;
    }

    private AlarmEventDTO checkTerrainCollision(TelemetryDTO t) {
        // 基于 SRTM 数字高程模型判断地形碰撞风险
        if (t.getAltitude() == null || t.getLatitude() == null || t.getLongitude() == null) return null;

        double terrainElevation = ruleConfig.getTerrainElevation(t.getLatitude(), t.getLongitude());
        double relativeHeight = t.getAltitude() - terrainElevation;
        double minSafeHeight = ruleConfig.getMinSafeHeightMeters();

        if (relativeHeight < minSafeHeight) {
            return buildAlarm(t, AlarmType.TERRAIN_COLLISION, AlarmLevel.CRITICAL,
                    "地形碰撞风险",
                    "相对地面高度 " + String.format("%.0f", relativeHeight) + "m，安全高度 " + minSafeHeight + "m",
                    minSafeHeight, relativeHeight);
        }
        return null;
    }

    // ==================== 工具方法 ====================

    private AlarmEventDTO buildAlarm(TelemetryDTO t, AlarmType type, AlarmLevel level,
                                      String title, String desc,
                                      Double threshold, Double actual) {
        AlarmEventDTO alarm = new AlarmEventDTO();
        alarm.setAlarmId(UUID.randomUUID().toString());
        alarm.setDroneSn(t.getDroneSn());
        alarm.setAlarmType(type);
        alarm.setAlarmLevel(level);
        alarm.setTitle(title);
        alarm.setDescription(desc);
        alarm.setLatitude(t.getLatitude());
        alarm.setLongitude(t.getLongitude());
        alarm.setAltitude(t.getAltitude());
        alarm.setThresholdValue(threshold);
        alarm.setActualValue(actual);
        alarm.setAlarmTime(Instant.now());
        alarm.setFlightPlanId(t.getFlightPlanId());
        alarm.setH3Index(t.getH3Index());
        alarm.setAcked(false);
        return alarm;
    }

    @Override
    public List<AlarmEventDTO> evaluateBatch(List<TelemetryDTO> telemetryList) {
        List<AlarmEventDTO> all = new ArrayList<>();
        for (TelemetryDTO t : telemetryList) {
            all.addAll(evaluate(t));
        }
        return all;
    }

    @Override
    public void acknowledgeAlarm(String alarmId, String userId) {
        // TODO: 持久化到数据库
    }

    @Override
    public List<AlarmEventDTO> getActiveAlarms(String droneSn) {
        // TODO: 从 Redis 查询活跃告警
        return new ArrayList<>();
    }

    @Override
    public List<String> getRules() {
        return ruleConfig.getRuleDescriptions();
    }
}
