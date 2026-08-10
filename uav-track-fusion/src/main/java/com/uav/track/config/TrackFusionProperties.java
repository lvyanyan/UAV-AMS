package com.uav.track.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 轨迹融合配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "uav.track-fusion")
public class TrackFusionProperties {

    /** 融合时间窗口（ms） */
    private long fusionWindowMs = 500;

    /** 轨迹拟合间隔（秒） */
    private int fittingIntervalSec = 5;

    /** 每架无人机最大缓冲位置数 */
    private int maxBufferSize = 200;

    /** 缓冲清理间隔（秒） */
    private int cleanupIntervalSec = 60;

    /** 离线超时（秒），超过此时间无数据的无人机清空缓冲区 */
    private long offlineTimeoutSec = 120;

    /** 飞行计划偏离告警阈值（米） */
    private double deviationThresholdMeters = 200;

    /** 贝塞尔插值密度 */
    private int bezierInterpolationPoints = 4;
}
