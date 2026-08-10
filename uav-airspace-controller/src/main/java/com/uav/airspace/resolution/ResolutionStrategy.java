package com.uav.airspace.resolution;

import com.uav.airspace.detection.ConflictResult;
import com.uav.airspace.state.DroneStateSnapshot;

/**
 * 解脱策略接口
 */
public interface ResolutionStrategy {

    /**
     * 评估该策略是否适用于此冲突
     */
    boolean isApplicable(ConflictResult conflict,
                         DroneStateSnapshot snapSelf,
                         DroneStateSnapshot snapOther);

    /**
     * 生成解脱指令
     *
     * @param priority 指令优先级（基于冲突严重程度）
     */
    ResolutionCommand generate(ConflictResult conflict,
                               DroneStateSnapshot snapSelf,
                               DroneStateSnapshot snapOther,
                               int priority);

    /** 策略名称 */
    String getName();
}
