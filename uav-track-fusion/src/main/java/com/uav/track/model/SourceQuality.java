package com.uav.track.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 来源质量评估 — 实时跟踪每个来源的数据质量
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceQuality {

    /** 来源类型 */
    private PositionSource source;

    /** 最近 N 次更新的平均间隔（ms） */
    private long avgUpdateIntervalMs;

    /** 丢包率（0.0 ~ 1.0），连续缺失次数 / 期望次数 */
    private double dropoutRate;

    /** 位置跳变次数（相邻点间距 > 500m 视为跳变） */
    private int jumpCount;

    /** 当前可信度权重（0.0 ~ 1.0），用于多源融合 */
    private double trustWeight;

    /** 最后更新时间 */
    private long lastUpdateTime;

    /** 累计接收点数 */
    private long totalReceived;
}
