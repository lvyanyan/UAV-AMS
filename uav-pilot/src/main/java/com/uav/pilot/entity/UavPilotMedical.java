package com.uav.pilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("uav_pilot_medical")
public class UavPilotMedical {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pilotId;
    private LocalDateTime examDate;
    /** 体检机构（体检中心同步或手动录入均维护） */
    private String examOrg;
    private String examResult;
    private String examReportUrl;
    private LocalDateTime expireDate;
    /** 记录来源：MANUAL 手动录入 / CENTER 体检中心同步 */
    private String source;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPilotId() { return pilotId; }
    public void setPilotId(Long pilotId) { this.pilotId = pilotId; }
    public LocalDateTime getExamDate() { return examDate; }
    public void setExamDate(LocalDateTime examDate) { this.examDate = examDate; }
    public String getExamOrg() { return examOrg; }
    public void setExamOrg(String examOrg) { this.examOrg = examOrg; }
    public String getExamResult() { return examResult; }
    public void setExamResult(String examResult) { this.examResult = examResult; }
    public String getExamReportUrl() { return examReportUrl; }
    public void setExamReportUrl(String examReportUrl) { this.examReportUrl = examReportUrl; }
    public LocalDateTime getExpireDate() { return expireDate; }
    public void setExpireDate(LocalDateTime expireDate) { this.expireDate = expireDate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
