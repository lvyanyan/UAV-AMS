package com.uav.common.dto;

import com.uav.common.enums.AlarmLevel;
import com.uav.common.enums.AlarmType;

import java.time.Instant;

/**
 * 告警事件 DTO
 */
public class AlarmEventDTO {
    private String alarmId;
    private String droneSn;
    private AlarmType alarmType;
    private AlarmLevel alarmLevel;
    private String title;
    private String description;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double thresholdValue;   // 阈值
    private Double actualValue;      // 实际值
    private Instant alarmTime;
    private String flightPlanId;
    private String h3Index;
    private Boolean acked;           // 已确认
    private String ackedBy;
    private Instant ackedAt;

    public String getAlarmId() { return alarmId; }
    public void setAlarmId(String alarmId) { this.alarmId = alarmId; }
    public String getDroneSn() { return droneSn; }
    public void setDroneSn(String droneSn) { this.droneSn = droneSn; }
    public AlarmType getAlarmType() { return alarmType; }
    public void setAlarmType(AlarmType alarmType) { this.alarmType = alarmType; }
    public AlarmLevel getAlarmLevel() { return alarmLevel; }
    public void setAlarmLevel(AlarmLevel alarmLevel) { this.alarmLevel = alarmLevel; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getAltitude() { return altitude; }
    public void setAltitude(Double altitude) { this.altitude = altitude; }
    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }
    public Double getActualValue() { return actualValue; }
    public void setActualValue(Double actualValue) { this.actualValue = actualValue; }
    public Instant getAlarmTime() { return alarmTime; }
    public void setAlarmTime(Instant alarmTime) { this.alarmTime = alarmTime; }
    public String getFlightPlanId() { return flightPlanId; }
    public void setFlightPlanId(String flightPlanId) { this.flightPlanId = flightPlanId; }
    public String getH3Index() { return h3Index; }
    public void setH3Index(String h3Index) { this.h3Index = h3Index; }
    public Boolean getAcked() { return acked; }
    public void setAcked(Boolean acked) { this.acked = acked; }
    public String getAckedBy() { return ackedBy; }
    public void setAckedBy(String ackedBy) { this.ackedBy = ackedBy; }
    public Instant getAckedAt() { return ackedAt; }
    public void setAckedAt(Instant ackedAt) { this.ackedAt = ackedAt; }
}
