package com.uav.common.enums;

/**
 * 飞行阶段状态
 */
public enum FlightPhase {
    PLANNED("计划中"),
    APPROVED("已批准"),
    READY("准备起飞"),
    TAKEOFF("起飞中"),
    CRUISE("巡航中"),
    RETURNING("返航中"),
    LANDING("降落中"),
    LANDED("已降落"),
    CANCELLED("已取消"),
    REJECTED("已驳回"),
    EMERGENCY("紧急状态");

    private final String label;
    FlightPhase(String label) { this.label = label; }
    public String getLabel() { return label; }
}
