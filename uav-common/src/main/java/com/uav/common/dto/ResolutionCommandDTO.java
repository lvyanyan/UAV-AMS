package com.uav.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 解脱指令 DTO — 空域控制器生成的冲突解脱建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 解脱指令唯一ID */
    private String resolutionId;

    /** 关联的冲突ID */
    private String conflictId;

    /** 目标无人机 SN（需要执行动作的无人机） */
    private String targetDroneSn;

    /** 冲突对方 SN */
    private String otherDroneSn;

    /** 解脱类型 */
    private String resolutionType;  // SPEED / ALTITUDE / HEADING / COMBINED

    // ─── 具体指令参数 ───
    /** 目标速度 (m/s)，调速策略时使用 */
    private Double targetSpeed;

    /** 目标高度 (m)，调高策略时使用 */
    private Double targetAltitude;

    /** 目标航向 (度，0=北)，调向策略时使用 */
    private Double targetHeading;

    /** 指令描述（人类可读） */
    private String description;

    /** 建议执行时间窗口（秒） */
    private double executeWithinSeconds;

    /** 优先级 0-100，数值越大越紧急 */
    private int priority;

    /** 生成时间戳 */
    private long generatedTimestamp;

    /** 指令状态 */
    private String status;          // PENDING / DISPATCHED / ACKNOWLEDGED / EXECUTED / EXPIRED
}
