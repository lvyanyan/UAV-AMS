package com.uav.pilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("uav_pilot_medical")
public class UavPilotMedical {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pilotId;
    private LocalDateTime examDate;
    private String examResult;
    private String examReportUrl;
    private LocalDateTime expireDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPilotId() { return pilotId; }
    public void setPilotId(Long pilotId) { this.pilotId = pilotId; }
    public LocalDateTime getExamDate() { return examDate; }
    public void setExamDate(LocalDateTime examDate) { this.examDate = examDate; }
    public String getExamResult() { return examResult; }
    public void setExamResult(String examResult) { this.examResult = examResult; }
    public String getExamReportUrl() { return examReportUrl; }
    public void setExamReportUrl(String examReportUrl) { this.examReportUrl = examReportUrl; }
    public LocalDateTime getExpireDate() { return expireDate; }
    public void setExpireDate(LocalDateTime expireDate) { this.expireDate = expireDate; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
