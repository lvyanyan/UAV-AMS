package com.uav.flinkcep.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * 告警事件（uav.alarm.event JSON，字段与 uav-common AlarmEventDTO 对齐）。
 * CEP 模式命中后构造，由 uav-realtime (Go) 消费并推送 WebSocket → 前端标牌。
 */
public class AlarmEvent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String alarmId;
    private String droneSn;
    private String alarmType;
    private String alarmLevel;
    private String title;
    private String description;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double thresholdValue;
    private Double actualValue;
    private String alarmTime;
    private String flightPlanId;
    private String h3Index;

    public static AlarmEvent of(String alarmType, TelemetryEvent e, String title,
                                String description, Double threshold, Double actual) {
        AlarmEvent a = new AlarmEvent();
        a.alarmId = alarmType + "-" + e.getDroneSn() + "-" + e.getTsEpochMs();
        a.droneSn = e.getDroneSn();
        a.alarmType = alarmType;
        a.alarmLevel = "WARNING";
        a.title = title;
        a.description = description;
        a.latitude = e.getLat();
        a.longitude = e.getLon();
        a.altitude = e.getAlt();
        a.thresholdValue = threshold;
        a.actualValue = actual;
        a.alarmTime = Instant.ofEpochMilli(e.getTsEpochMs()).toString();
        a.flightPlanId = e.getFlightPlanId();
        a.h3Index = e.getH3Index();
        return a;
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("AlarmEvent 序列化失败", e);
        }
    }

    public String getDroneSn() { return droneSn; }
}
