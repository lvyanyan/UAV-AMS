package com.uav.airspace.resolution;

import com.uav.airspace.detection.ConflictResult;
import com.uav.airspace.state.DroneStateSnapshot;
import org.springframework.stereotype.Component;

/**
 * 综合解脱策略 — 高危急情况使用
 * <p>
 * 同时调整速度、高度和航向，适用于碰撞概率 > 0.8 的危急冲突。
 * 三管齐下，快速建立安全间隔。
 */
@Component
public class CombinedResolutionStrategy implements ResolutionStrategy {

    @Override
    public boolean isApplicable(ConflictResult conflict,
                                DroneStateSnapshot snapSelf,
                                DroneStateSnapshot snapOther) {
        // 仅在高危冲突时启用
        return "CRITICAL".equals(conflict.getAlarmLevel())
                || conflict.getCollisionProbability() > 0.8
                || conflict.getTcpaSeconds() < 10.0;
    }

    @Override
    public ResolutionCommand generate(ConflictResult conflict,
                                      DroneStateSnapshot snapSelf,
                                      DroneStateSnapshot snapOther,
                                      int priority) {

        double currentSpeed = snapSelf.getLatestTelemetry().getSpeed();
        double currentAlt = snapSelf.getLatestTelemetry().getAlt();
        double currentHeading = snapSelf.getLatestTelemetry().getHeading();

        // 急减速 + 爬升 + 右转 — 建立最大安全间隔
        double targetSpeed = Math.max(currentSpeed * 0.5, 1.0);
        double targetAlt = Math.min(currentAlt + 80.0, 500.0);
        double targetHeading = (currentHeading + 25.0) % 360.0;

        String desc = String.format(
                "⚠ 危急冲突！建议立即: 减速至 %.1f m/s + 爬升至 %.0f 米 + 右转至 %.0f°",
                targetSpeed, targetAlt, targetHeading);

        return ResolutionCommand.builder()
                .resolutionType("COMBINED")
                .targetDroneSn(snapSelf.getDroneSn())
                .otherDroneSn(snapOther.getDroneSn())
                .targetSpeed(targetSpeed)
                .targetAltitude(targetAlt)
                .targetHeading(targetHeading)
                .description(desc)
                .executeWithinSeconds(5.0) // 5 秒内执行
                .priority(100)             // 最高优先级
                .generatedTimestamp(System.currentTimeMillis())
                .status("PENDING")
                .build();
    }

    @Override
    public String getName() {
        return "COMBINED";
    }
}
