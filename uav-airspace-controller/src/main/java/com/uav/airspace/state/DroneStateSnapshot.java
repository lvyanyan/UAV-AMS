package com.uav.airspace.state;

import com.uav.common.dto.TelemetryDTO;
import lombok.Data;

import java.time.Instant;

/**
 * 无人机运行时状态快照 — 内存中维护的最新遥测 + 统计信息
 */
@Data
public class DroneStateSnapshot {

    /** 无人机 SN */
    private String droneSn;

    /** H3 索引（当前所在网格） */
    private long h3Index;

    /** 最新遥测 */
    private TelemetryDTO latestTelemetry;

    /** 最后更新时间 */
    private long lastUpdateEpochMs;

    // ─── 用于轨迹预测的衍生状态 ───

    /** 东向速度 (m/s) — 从 lat/lon 差分计算 */
    private double velocityEast;

    /** 北向速度 (m/s) */
    private double velocityNorth;

    /** 天向速度 (m/s) */
    private double velocityUp;

    /** 加速度估计（用于卡尔曼滤波过程噪声自适应） */
    private double accelerationMagnitude;

    public boolean isExpired(int expireSeconds) {
        return Instant.now().toEpochMilli() - lastUpdateEpochMs > expireSeconds * 1000L;
    }
}
