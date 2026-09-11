package com.uav.flightplan.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("uav_route")
public class UavRoute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String routeName;
    private String routeCode;
    /** 航点 JSON：[[lon,lat],...] */
    private String waypoints;
    private Double corridorWidthM;
    /** ONE_WAY / TWO_WAY */
    private String direction;
    private Boolean isActive;
    private String description;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRouteName() { return routeName; }
    public void setRouteName(String v) { this.routeName = v; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String v) { this.routeCode = v; }
    public String getWaypoints() { return waypoints; }
    public void setWaypoints(String v) { this.waypoints = v; }
    public Double getCorridorWidthM() { return corridorWidthM; }
    public void setCorridorWidthM(Double v) { this.corridorWidthM = v; }
    public String getDirection() { return direction; }
    public void setDirection(String v) { this.direction = v; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean v) { this.isActive = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
}
