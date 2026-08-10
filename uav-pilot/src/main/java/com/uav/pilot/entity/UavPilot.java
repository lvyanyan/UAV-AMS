package com.uav.pilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("uav_pilot")
public class UavPilot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String pilotName;
    private String idNumber;
    private String phone;
    private String email;
    private String licenseLevel;
    private String licenseNo;
    private LocalDateTime licenseExpire;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPilotName() { return pilotName; }
    public void setPilotName(String pilotName) { this.pilotName = pilotName; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLicenseLevel() { return licenseLevel; }
    public void setLicenseLevel(String licenseLevel) { this.licenseLevel = licenseLevel; }
    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String licenseNo) { this.licenseNo = licenseNo; }
    public LocalDateTime getLicenseExpire() { return licenseExpire; }
    public void setLicenseExpire(LocalDateTime licenseExpire) { this.licenseExpire = licenseExpire; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
