package com.uav.registry.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("uav_registration")
public class UavRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String registrationId;
    private String droneSn;
    private Long ownerId;
    private String droneModel;
    private String droneType;
    private Double weightG;
    private String manufacturer;
    private String registerStatus;
    /** UOM 上报状态：REPORTED 已上报 / FAILED 上报失败 / NULL 未上报 */
    private String uomStatus;
    private LocalDateTime uomReportTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }
    public String getDroneSn() { return droneSn; }
    public void setDroneSn(String droneSn) { this.droneSn = droneSn; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getDroneModel() { return droneModel; }
    public void setDroneModel(String droneModel) { this.droneModel = droneModel; }
    public String getDroneType() { return droneType; }
    public void setDroneType(String droneType) { this.droneType = droneType; }
    public Double getWeightG() { return weightG; }
    public void setWeightG(Double weightG) { this.weightG = weightG; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getRegisterStatus() { return registerStatus; }
    public void setRegisterStatus(String registerStatus) { this.registerStatus = registerStatus; }
    public String getUomStatus() { return uomStatus; }
    public void setUomStatus(String uomStatus) { this.uomStatus = uomStatus; }
    public LocalDateTime getUomReportTime() { return uomReportTime; }
    public void setUomReportTime(LocalDateTime uomReportTime) { this.uomReportTime = uomReportTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
