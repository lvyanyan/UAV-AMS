package com.uav.track.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 原始位置数据 — 来自任意来源的单个位置点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawPosition {

    /** 无人机 SN */
    private String droneSn;

    /** 位置来源 */
    private PositionSource source;

    /** WGS84 纬度 */
    private Double latitude;
    /** WGS84 经度 */
    private Double longitude;
    /** 海拔高度（米） */
    private Double altitude;

    /** 航向角（度，0=正北） */
    private Double heading;
    /** 地速（m/s） */
    private Double groundSpeed;
    /** 爬升率（m/s，正=上升） */
    private Double climbRate;

    /** 观测时间戳 */
    private Instant timestamp;

    /** 来源特有精度（米，1σ），覆盖 PositionSource 默认值 */
    private Double reportedAccuracy;

    /** 数据到达系统的时间（不是观测时间） */
    private Instant receivedAt;

    /** 该来源本次上报的原始 JSON（调试用） */
    private String rawPayload;
}
