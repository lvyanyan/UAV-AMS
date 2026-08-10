package com.uav.track.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 拟合后的统一轨迹 — 融合多源数据 + 平滑处理后的输出
 * <p>
 * 这是向前端推送的最终轨迹数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FittedTrack {

    /** 无人机 SN */
    private String droneSn;

    /** 生成时间 */
    private Instant generatedAt;

    /** 轨迹段列表（按时间有序） */
    private List<TrackSegment> segments;

    /** 关联的飞行计划 ID（如已关联） */
    private String flightPlanId;

    /** 是否偏离飞行计划 */
    private Boolean deviatedFromPlan;

    /** 偏离距离（米，仅当偏离时有效） */
    private Double deviationDistanceMeters;

    /** 参与融合的来源列表 */
    private List<PositionSource> fusionSources;

    /** 融合质量评分（0.0 ~ 1.0） */
    private Double fusionQuality;
}
