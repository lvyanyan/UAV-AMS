package com.uav.airspace.resolution;

import com.uav.airspace.detection.ConflictResult;
import com.uav.airspace.state.DroneStateSnapshot;
import org.springframework.stereotype.Component;

/**
 * 速度调整策略 — 优先策略
 * <p>
 * 原理：改变飞行速度 → 改变到达冲突点的时间 → 错开时间窗口。
 * 对飞行任务影响最小（不改变航线和高度）。
 * <p>
 * 决策：若本机在后方 → 减速；若本机在前方 → 加速。
 */
@Component
public class SpeedResolutionStrategy implements ResolutionStrategy {

    @Override
    public boolean isApplicable(ConflictResult conflict,
                                DroneStateSnapshot snapSelf,
                                DroneStateSnapshot snapOther) {
        // 速度调整几乎总是可用，除非无人机已达速度极限
        double currentSpeed = snapSelf.getLatestTelemetry().getSpeed();
        return currentSpeed > 0.5 && currentSpeed < 30.0;
    }

    @Override
    public ResolutionCommand generate(ConflictResult conflict,
                                      DroneStateSnapshot snapSelf,
                                      DroneStateSnapshot snapOther,
                                      int priority) {

        double currentSpeed = snapSelf.getLatestTelemetry().getSpeed();
        double otherSpeed = snapOther.getLatestTelemetry().getSpeed();

        // 比较速度：若本机更快 → 加速通过；更慢 → 减速让行
        double targetSpeed;
        String desc;

        if (currentSpeed >= otherSpeed) {
            // 加速 20%，让对方先过
            targetSpeed = Math.min(currentSpeed * 1.20, 28.0);
            desc = String.format("建议加速至 %.1f m/s，优先通过冲突点", targetSpeed);
        } else {
            // 减速 20%，让对方先过
            targetSpeed = Math.max(currentSpeed * 0.80, 1.0);
            desc = String.format("建议减速至 %.1f m/s，让行对方通过冲突点", targetSpeed);
        }

        return ResolutionCommand.builder()
                .resolutionType("SPEED")
                .targetDroneSn(snapSelf.getDroneSn())
                .otherDroneSn(snapOther.getDroneSn())
                .targetSpeed(targetSpeed)
                .description(desc)
                .executeWithinSeconds(conflict.getTcpaSeconds() * 0.5)
                .priority(priority)
                .generatedTimestamp(System.currentTimeMillis())
                .status("PENDING")
                .build();
    }

    @Override
    public String getName() {
        return "SPEED";
    }
}
