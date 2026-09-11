package com.uav.flightplan.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** 起降场（垂直起降设施） */
@TableName("uav_airport")
public class UavAirport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String airportName;
    private String airportCode;
    /** TAKEOFF 起飞场 / LANDING 降落场 / ALL 综合起降场 */
    private String airportType;
    private Double lon;
    private Double lat;
    private Double elevationM;
    private Integer capacity;
    private Boolean isActive;
    private String remark;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAirportName() { return airportName; }
    public void setAirportName(String v) { this.airportName = v; }
    public String getAirportCode() { return airportCode; }
    public void setAirportCode(String v) { this.airportCode = v; }
    public String getAirportType() { return airportType; }
    public void setAirportType(String v) { this.airportType = v; }
    public Double getLon() { return lon; }
    public void setLon(Double v) { this.lon = v; }
    public Double getLat() { return lat; }
    public void setLat(Double v) { this.lat = v; }
    public Double getElevationM() { return elevationM; }
    public void setElevationM(Double v) { this.elevationM = v; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer v) { this.capacity = v; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean v) { this.isActive = v; }
    public String getRemark() { return remark; }
    public void setRemark(String v) { this.remark = v; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
}
