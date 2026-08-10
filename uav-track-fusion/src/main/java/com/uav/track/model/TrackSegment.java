package com.uav.track.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 轨迹段 — 一段平滑后的轨迹 + 告警标注
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackSegment {

    /** 段序号 */
    private int segmentIndex;

    /** 起始时间 */
    private Instant startTime;
    /** 结束时间 */
    private Instant endTime;

    /** 平滑后的轨迹点 [lat, lon, alt] 有序列表 */
    private List<double[]> smoothedPoints;

    /** 原始观测点数量 */
    private int rawPointCount;

    /** 使用的来源 */
    private PositionSource dominantSource;

    /** 该段的平均速度（m/s） */
    private Double avgSpeed;

    /** 该段的平均高度（m） */
    private Double avgAltitude;

    // ─── 告警标注 ───

    /** 该段是否关联告警 */
    private Boolean hasAlert;

    /** 关联的告警信息列表 */
    private List<TrackAlertAnnotation> alertAnnotations;

    /** 段级别最大告警等级（用于前端颜色映射） */
    private String maxAlertLevel;
}
