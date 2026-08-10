package com.uav.common.enums;

/**
 * 告警类型 - 六维告警引擎
 */
public enum AlarmType {
    AIRSPACE_INTRUSION("空域入侵"),
    DRONE_CONFLICT("无人机冲突"),
    WEATHER_RISK("气象风险"),
    ROUTE_DEVIATION("航路偏离"),
    EQUIPMENT_FAULT("设备故障"),
    TERRAIN_COLLISION("地形碰撞"),
    BATTERY_LOW("低电量"),
    SIGNAL_LOST("信号丢失"),
    NO_FLIGHT_PLAN("无计划飞行"),
    SPEED_VIOLATION("超速"),
    ALTITUDE_VIOLATION("超高"),
    GEOFENCE_VIOLATION("围栏越界");

    private final String label;
    AlarmType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
