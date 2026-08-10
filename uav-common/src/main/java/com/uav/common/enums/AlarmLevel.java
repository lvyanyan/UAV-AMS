package com.uav.common.enums;

/**
 * 告警等级
 */
public enum AlarmLevel {
    GENERAL("一般", 1),
    SERIOUS("严重", 2),
    CRITICAL("危急", 3);

    private final String label;
    private final int level;

    AlarmLevel(String label, int level) { this.label = label; this.level = level; }
    public String getLabel() { return label; }
    public int getLevel() { return level; }
}
