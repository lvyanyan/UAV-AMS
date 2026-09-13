package com.uav.flightplan.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 放行检查结果（契约：无论成败都带 checks 数组，前端逐项渲染 pass/reason）
 */
public class ReleaseCheckResult {

    /** 单项检查 */
    public static class CheckItem {
        private String name;   // 检查项名称（中文）
        private boolean pass;  // 是否通过
        private String reason; // 未通过原因 / 通过说明

        public CheckItem() {}

        public CheckItem(String name, boolean pass, String reason) {
            this.name = name;
            this.pass = pass;
            this.reason = reason;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isPass() { return pass; }
        public void setPass(boolean pass) { this.pass = pass; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    private Long planId;
    private String planCode;
    private boolean passed;
    private List<CheckItem> checks = new ArrayList<>();

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public List<CheckItem> getChecks() { return checks; }
    public void setChecks(List<CheckItem> checks) { this.checks = checks; }
}
