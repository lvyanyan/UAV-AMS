package com.uav.flightplan.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("flight_plan")
public class FlightPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String planCode;
    private String planStatus;
    private String droneSn;
    private Long pilotId;
    private Long routeId;
    private String departure;
    private String destination;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private Double altFloorM;
    private Double altCeilingM;
    private String flightPurpose;
    private String riskLevel;
    private Long submitterId;
    private LocalDateTime submitTime;
    private Long militaryApprovalId;
    private Boolean militaryApproved;
    /** 实际起飞时间（仿真器起飞遥测回流后写入） */
    private LocalDateTime actualStart;
    /** 实际降落时间（LANDED 边沿事件回流后写入） */
    private LocalDateTime actualEnd;
    /** 起飞指令下发时间（仅记录，鲁棒排查用） */
    private LocalDateTime cmdSentAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getPlanStatus() { return planStatus; }
    public void setPlanStatus(String planStatus) { this.planStatus = planStatus; }
    public String getDroneSn() { return droneSn; }
    public void setDroneSn(String droneSn) { this.droneSn = droneSn; }
    public Long getPilotId() { return pilotId; }
    public void setPilotId(Long pilotId) { this.pilotId = pilotId; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public String getDeparture() { return departure; }
    public void setDeparture(String departure) { this.departure = departure; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public LocalDateTime getPlannedStart() { return plannedStart; }
    public void setPlannedStart(LocalDateTime plannedStart) { this.plannedStart = plannedStart; }
    public LocalDateTime getPlannedEnd() { return plannedEnd; }
    public void setPlannedEnd(LocalDateTime plannedEnd) { this.plannedEnd = plannedEnd; }
    public Double getAltFloorM() { return altFloorM; }
    public void setAltFloorM(Double altFloorM) { this.altFloorM = altFloorM; }
    public Double getAltCeilingM() { return altCeilingM; }
    public void setAltCeilingM(Double altCeilingM) { this.altCeilingM = altCeilingM; }
    public String getFlightPurpose() { return flightPurpose; }
    public void setFlightPurpose(String flightPurpose) { this.flightPurpose = flightPurpose; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public Long getSubmitterId() { return submitterId; }
    public void setSubmitterId(Long submitterId) { this.submitterId = submitterId; }
    public LocalDateTime getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDateTime submitTime) { this.submitTime = submitTime; }
    public Long getMilitaryApprovalId() { return militaryApprovalId; }
    public void setMilitaryApprovalId(Long militaryApprovalId) { this.militaryApprovalId = militaryApprovalId; }
    public Boolean getMilitaryApproved() { return militaryApproved; }
    public void setMilitaryApproved(Boolean militaryApproved) { this.militaryApproved = militaryApproved; }
    public LocalDateTime getActualStart() { return actualStart; }
    public void setActualStart(LocalDateTime actualStart) { this.actualStart = actualStart; }
    public LocalDateTime getActualEnd() { return actualEnd; }
    public void setActualEnd(LocalDateTime actualEnd) { this.actualEnd = actualEnd; }
    public LocalDateTime getCmdSentAt() { return cmdSentAt; }
    public void setCmdSentAt(LocalDateTime cmdSentAt) { this.cmdSentAt = cmdSentAt; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
