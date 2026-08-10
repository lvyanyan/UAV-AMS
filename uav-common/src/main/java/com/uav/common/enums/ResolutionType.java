package com.uav.common.enums;

/**
 * 解脱策略类型
 */
public enum ResolutionType {

    /** 速度调整 */
    SPEED("调速"),

    /** 高度调整 */
    ALTITUDE("调高"),

    /** 航向调整 */
    HEADING("调向"),

    /** 综合调整（速度+高度+航向组合） */
    COMBINED("综合");

    private final String label;

    ResolutionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
