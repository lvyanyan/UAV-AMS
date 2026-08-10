package com.uav.airspace.resolution;

import com.uav.airspace.detection.ConflictResult;
import com.uav.airspace.state.DroneStateSnapshot;
import org.springframework.stereotype.Component;

/**
 * 高度调整策略 — 第二优先
 * <p>
 * 垂直分离是最安全的方式（立体空间利用率高）。
 * 决策：若本机高度更低 → 下降；否则 → 爬升 50 米。
 */
@Component
public class AltitudeResolutionStrategy implements ResolutionStrategy {

    private static final double ALTITUDE_STEP_METERS = 50.0;

    @Override
    public boolean isApplicable(ConflictResult conflict,
                                DroneStateSnapshot snapSelf,
                                DroneStateSnapshot snapOther) {
        double currentAlt = snapSelf.getLatestTelemetry().getAlt();
        // 高度调整适用条件：当前高度允许 ±50m 调整（不低于 10m，不高于 500m）
        return currentAlt >= 60.0 && currentAlt <= 450.0;
    }

    @Override
    public ResolutionCommand generate(ConflictResult conflict,
                                      DroneStateSnapshot snapSelf,
                                      DroneStateSnapshot snapOther,
                                      int priority) {

        double currentAlt = snapSelf.getLatestTelemetry().getAlt();
        double otherAlt = snapOther.getLatestTelemetry().getAlt();

        double targetAlt;
        String desc;

        if (currentAlt <= otherAlt) {
            // 本机更低 → 下降避让
            targetAlt = Math.max(currentAlt - ALTITUDE_STEP_METERS, 10.0);
            desc = String.format("建议下降至 %.0f 米，从下方通过", targetAlt);
        } else {
            // 本机更高 → 爬升避让
            targetAlt = Math.min(currentAlt + ALTITUDE_STEP_METERS, 500.0);
            desc = String.format("建议爬升至 %.0f 米，从上方通过", targetAlt);
        }

        return ResolutionCommand.builder()
                .resolutionType("ALTITUDE")
                .targetDroneSn(snapSelf.getDroneSn())
                .otherDroneSn(snapOther.getDroneSn())
                .targetAltitude(targetAlt)
                .description(desc)
                .executeWithinSeconds(conflict.getTcpaSeconds() * 0.4)
                .priority(priority)
                .generatedTimestamp(System.currentTimeMillis())
                .status("PENDING")
                .build();
    }

    @Override
    public String getName() {
        return "ALTITUDE";
    }
}
