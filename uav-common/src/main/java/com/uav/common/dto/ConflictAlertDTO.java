package com.uav.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 冲突告警 DTO — 两架无人机预测包络相交产生的冲突事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictAlertDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 冲突唯一ID */
    private String conflictId;

    /** 本机 SN */
    private String droneSnA;

    /** 冲突目标机 SN */
    private String droneSnB;

    /** 冲突告警等级 */
    private String alarmLevel;   // GENERAL / SERIOUS / CRITICAL

    /** 预计碰撞时间 TCPA（秒） */
    private double tcpaSeconds;

    /** 预计碰撞位置 */
    private double collisionLat;
    private double collisionLon;
    private double collisionAlt;

    /** 碰撞概率 0.0 ~ 1.0 */
    private double collisionProbability;

    /** 两机的预测包络（用于前端渲染冲突区域） */
    private List<PredictionEnvelopeDTO> envelopeA;
    private List<PredictionEnvelopeDTO> envelopeB;

    /** 检测时间 */
    private long detectTimestamp;

    /** 冲突是否仍然有效 */
    private boolean active;
}
