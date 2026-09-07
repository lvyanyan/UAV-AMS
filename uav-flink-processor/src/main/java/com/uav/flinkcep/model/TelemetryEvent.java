package com.uav.flinkcep.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 遥测事件（uav.telemetry JSON 的独立解析视图）。
 * <p>
 * 字段命名兼容：lat/latitude、lon/longitude 双命名，时间戳兼容
 * ISO Instant（timestamp）与 epoch 毫秒（ts）两种格式——以 Go 生产端实际字段为准做防御性读取。
 */
public class TelemetryEvent {

    private String droneSn;
    private double lat;
    private double lon;
    private double alt;
    private Double groundSpeed;
    private Double climbRate;
    private Integer batteryPercent;
    private String flightPlanId;
    private String h3Index;
    private long tsEpochMs;

    public static TelemetryEvent parse(String json, ObjectMapper mapper) {
        try {
            JsonNode n = mapper.readTree(json);
            if (n == null || !n.hasNonNull("droneSn")) return null;
            TelemetryEvent e = new TelemetryEvent();
            e.droneSn = n.get("droneSn").asText();
            e.lat = firstDouble(n, "lat", "latitude");
            e.lon = firstDouble(n, "lon", "longitude");
            e.alt = firstDouble(n, "alt", "altitude");
            e.groundSpeed = optDouble(n, "groundSpeed", "speed");
            e.climbRate = optDouble(n, "climbRate");
            e.batteryPercent = optInt(n, "batteryPercent");
            e.flightPlanId = optText(n, "flightPlanId");
            e.h3Index = optText(n, "h3Index");
            e.tsEpochMs = parseTs(n);
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    private static double firstDouble(JsonNode n, String primary, String fallback) {
        JsonNode v = n.hasNonNull(primary) ? n.get(primary) : n.get(fallback);
        return v != null ? v.asDouble() : 0.0;
    }

    private static Double optDouble(JsonNode n, String... names) {
        for (String name : names) {
            if (n.hasNonNull(name)) return n.get(name).asDouble();
        }
        return null;
    }

    private static Integer optInt(JsonNode n, String name) {
        return n.hasNonNull(name) ? n.get(name).asInt() : null;
    }

    private static String optText(JsonNode n, String name) {
        return n.hasNonNull(name) ? n.get(name).asText() : null;
    }

    private static long parseTs(JsonNode n) {
        if (n.hasNonNull("ts")) return n.get("ts").asLong();
        if (n.hasNonNull("timestamp")) {
            try {
                return java.time.Instant.parse(n.get("timestamp").asText()).toEpochMilli();
            } catch (Exception ignore) {
                return System.currentTimeMillis();
            }
        }
        return System.currentTimeMillis();
    }

    public String getDroneSn() { return droneSn; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getAlt() { return alt; }
    public Double getGroundSpeed() { return groundSpeed; }
    public Double getClimbRate() { return climbRate; }
    public Integer getBatteryPercent() { return batteryPercent; }
    public String getFlightPlanId() { return flightPlanId; }
    public String getH3Index() { return h3Index; }
    public long getTsEpochMs() { return tsEpochMs; }
}
