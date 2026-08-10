package com.uav.common.dto;

import com.uav.common.enums.AlarmLevel;

import java.util.List;
import java.util.Map;

/**
 * 飞行计划风险评估结果
 */
public class RiskAssessmentResultDTO {
    private String flightPlanId;
    private boolean approved;           // 是否通过
    private AlarmLevel riskLevel;       // 风险等级
    private int riskScore;              // 风险评分 0-100
    private List<RiskItem> riskItems;   // 各项风险明细
    private String suggestion;          // 建议
    private Map<String, Object> details;

    public static class RiskItem {
        private String category;        // 风险类别：空域/气象/航路/设备/人员
        private AlarmLevel level;
        private String description;
        private String mitigation;      // 缓解措施

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public AlarmLevel getLevel() { return level; }
        public void setLevel(AlarmLevel level) { this.level = level; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMitigation() { return mitigation; }
        public void setMitigation(String mitigation) { this.mitigation = mitigation; }
    }

    public String getFlightPlanId() { return flightPlanId; }
    public void setFlightPlanId(String flightPlanId) { this.flightPlanId = flightPlanId; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public AlarmLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(AlarmLevel riskLevel) { this.riskLevel = riskLevel; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public List<RiskItem> getRiskItems() { return riskItems; }
    public void setRiskItems(List<RiskItem> riskItems) { this.riskItems = riskItems; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}
