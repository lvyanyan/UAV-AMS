package com.uav.airspace.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("uav_airspace")
public class Airspace {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String airspaceName;
    private String airspaceCode;
    private String airspaceType;
    private String geoJson;
    private Double altFloorM;
    private Double altCeilingM;
    private Boolean isActive;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private String h3Cells;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAirspaceName() { return airspaceName; }
    public void setAirspaceName(String n) { this.airspaceName = n; }
    public String getAirspaceCode() { return airspaceCode; }
    public void setAirspaceCode(String c) { this.airspaceCode = c; }
    public String getAirspaceType() { return airspaceType; }
    public void setAirspaceType(String t) { this.airspaceType = t; }
    public String getGeoJson() { return geoJson; }
    public void setGeoJson(String g) { this.geoJson = g; }
    public Double getAltFloorM() { return altFloorM; }
    public void setAltFloorM(Double d) { this.altFloorM = d; }
    public Double getAltCeilingM() { return altCeilingM; }
    public void setAltCeilingM(Double d) { this.altCeilingM = d; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean b) { this.isActive = b; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime t) { this.startTime = t; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime t) { this.endTime = t; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public String getH3Cells() { return h3Cells; }
    public void setH3Cells(String h) { this.h3Cells = h; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime t) { this.createTime = t; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime t) { this.updateTime = t; }
}
