package com.uav.track.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 轨迹告警耦合标注 — 将告警事件绑定到轨迹的时间/空间位置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackAlertAnnotation {

    /** 告警 ID */
    private String alarmId;

    /** 告警类型 */
    private String alarmType;

    /** 告警等级: GENERAL / SERIOUS / CRITICAL */
    private String alarmLevel;

    /** 告警标题 */
    private String title;

    /** 告警发生时间 */
    private Instant alarmTime;

    /** 告警位置 lat */
    private Double latitude;
    /** 告警位置 lon */
    private Double longitude;
    /** 告警位置 alt */
    private Double altitude;

    /** 在轨迹段中的点索引 */
    private int pointIndexInSegment;

    /** 是否已确认 */
    private Boolean acked;
}
