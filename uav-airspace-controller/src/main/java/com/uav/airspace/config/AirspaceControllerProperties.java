package com.uav.airspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 空域控制器可配置参数
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "airspace")
public class AirspaceControllerProperties {

    /** 冲突检测周期（毫秒） */
    private long detectionIntervalMs = 200;

    /** 预测步长（秒） */
    private double predictionStepSeconds = 1.0;

    /** 预测总步数 */
    private int predictionSteps = 60;

    /** 冲突最小接近距离（米） */
    private double conflictMinDistanceMeters = 50.0;

    /** 碰撞概率阈值 */
    private double collisionProbabilityThreshold = 0.3;

    /** 解脱建议最低优先级 */
    private int resolutionMinPriority = 30;

    /** H3 分辨率 */
    private int h3Resolution = 9;

    /** 状态过期时间（秒） */
    private int stateExpireSeconds = 10;

    /** 不确定性膨胀率（米/秒） */
    private double uncertaintyGrowthRateMps = 2.0;
}
