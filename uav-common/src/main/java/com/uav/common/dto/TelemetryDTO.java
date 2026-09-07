package com.uav.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.Map;

/**
 * 无人机遥测数据 DTO - 所有模块通用
 * <p>
 * 字段注解对齐 uav-realtime (Go) 生产 JSON 的 snake_case 键名。
 */
public class TelemetryDTO {
    @JsonProperty("drone_sn")
    private String droneSn;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double heading;
    @JsonProperty("ground_speed")
    private Double groundSpeed;
    private Double climbRate;
    @JsonProperty("battery_percent")
    private Integer batteryPercent;
    private Integer rssi;
    private Integer gpsSatellites;
    private Double roll;
    private Double pitch;
    private Double yaw;
    private String flightMode;
    @JsonProperty("flight_phase")
    private String flightPhase;
    @JsonProperty("flight_plan_id")
    private String flightPlanId;
    private String h3Index;
    @JsonProperty("timestamp")
    @JsonDeserialize(using = EpochMilliInstantDeserializer.class)
    private Instant timestamp;
    @JsonProperty("model")
    private String model;
    @JsonProperty("wind_speed_ms")
    private Double windSpeedMs;
    @JsonProperty("temperature_c")
    private Double temperatureC;
    private Map<String, Object> extra;

    // ---- 别名方法（兼容不同模块的字段名） ----
    public Double getSpeed() { return groundSpeed; }
    public void setSpeed(Double speed) { this.groundSpeed = speed; }
    public Double getAlt() { return altitude; }
    public void setAlt(Double alt) { this.altitude = alt; }
    public Double getLat() { return latitude; }
    public void setLat(Double lat) { this.latitude = lat; }
    public Double getLon() { return longitude; }
    public void setLon(Double lon) { this.longitude = lon; }

    // ---- 标准 getters/setters ----
    public String getDroneSn() { return droneSn; }
    public void setDroneSn(String droneSn) { this.droneSn = droneSn; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getAltitude() { return altitude; }
    public void setAltitude(Double altitude) { this.altitude = altitude; }
    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }
    public Double getGroundSpeed() { return groundSpeed; }
    public void setGroundSpeed(Double groundSpeed) { this.groundSpeed = groundSpeed; }
    public Double getClimbRate() { return climbRate; }
    public void setClimbRate(Double climbRate) { this.climbRate = climbRate; }
    public Integer getBatteryPercent() { return batteryPercent; }
    public void setBatteryPercent(Integer batteryPercent) { this.batteryPercent = batteryPercent; }
    public Integer getRssi() { return rssi; }
    public void setRssi(Integer rssi) { this.rssi = rssi; }
    public Integer getGpsSatellites() { return gpsSatellites; }
    public void setGpsSatellites(Integer gpsSatellites) { this.gpsSatellites = gpsSatellites; }
    public Double getRoll() { return roll; }
    public void setRoll(Double roll) { this.roll = roll; }
    public Double getPitch() { return pitch; }
    public void setPitch(Double pitch) { this.pitch = pitch; }
    public Double getYaw() { return yaw; }
    public void setYaw(Double yaw) { this.yaw = yaw; }
    public String getFlightMode() { return flightMode; }
    public void setFlightMode(String flightMode) { this.flightMode = flightMode; }
    public String getFlightPhase() { return flightPhase; }
    public void setFlightPhase(String flightPhase) { this.flightPhase = flightPhase; }
    public String getFlightPlanId() { return flightPlanId; }
    public void setFlightPlanId(String flightPlanId) { this.flightPlanId = flightPlanId; }
    public String getH3Index() { return h3Index; }
    public void setH3Index(String h3Index) { this.h3Index = h3Index; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getWindSpeedMs() { return windSpeedMs; }
    public void setWindSpeedMs(Double windSpeedMs) { this.windSpeedMs = windSpeedMs; }
    public Double getTemperatureC() { return temperatureC; }
    public void setTemperatureC(Double temperatureC) { this.temperatureC = temperatureC; }
}
