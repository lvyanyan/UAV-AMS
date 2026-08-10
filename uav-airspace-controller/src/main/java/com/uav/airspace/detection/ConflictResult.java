package com.uav.airspace.detection;

import com.uav.airspace.prediction.PredictionEnvelope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 冲突检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictResult {

    /** 冲突唯一标识 */
    private String conflictId;

    /** 无人机 A SN */
    private String droneSnA;

    /** 无人机 B SN */
    private String droneSnB;

    /** 告警等级 */
    private String alarmLevel;

    /** 预计碰撞时间 TCPA（秒） */
    private double tcpaSeconds;

    /** 碰撞位置 */
    private double collisionLat;
    private double collisionLon;
    private double collisionAlt;

    /** 碰撞概率 */
    private double collisionProbability;

    /** 最小预测距离（米） */
    private double minPredictedDistance;

    /** 发生冲突的预测步数 */
    private int conflictStepIndex;

    /** 冲突双方预测包络 */
    private PredictionEnvelope envelopeA;
    private PredictionEnvelope envelopeB;

    /** 检测时间 */
    private long detectTimestamp;

    public String generateConflictId() {
        long ts = System.currentTimeMillis();
        return "CFL-" + droneSnA.substring(Math.max(0, droneSnA.length() - 6))
                + "-" + droneSnB.substring(Math.max(0, droneSnB.length() - 6))
                + "-" + ts;
    }
}
