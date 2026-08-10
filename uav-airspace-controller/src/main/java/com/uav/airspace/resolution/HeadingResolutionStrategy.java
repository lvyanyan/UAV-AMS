package com.uav.airspace.resolution;

import com.uav.airspace.detection.ConflictResult;
import com.uav.airspace.state.DroneStateSnapshot;
import org.springframework.stereotype.Component;

/**
 * 航向调整策略 — 第三优先
 * <p>
 * 水平方向偏转避让。对飞行路径有较大改变，作为后备方案。
 * 决策：右转 15°（标准航空避让规则）。
 */
@Component
public class HeadingResolutionStrategy implements ResolutionStrategy {

    private static final double HEADING_DEVIATION_DEG = 15.0;

    @Override
    public boolean isApplicable(ConflictResult conflict,
                                DroneStateSnapshot snapSelf,
                                DroneStateSnapshot snapOther) {
        // 航向调整始终可用
        return true;
    }

    @Override
    public ResolutionCommand generate(ConflictResult conflict,
                                      DroneStateSnapshot snapSelf,
                                      DroneStateSnapshot snapOther,
                                      int priority) {

        double currentHeading = snapSelf.getLatestTelemetry().getHeading();

        // 标准规则：右转避让
        double targetHeading = (currentHeading + HEADING_DEVIATION_DEG) % 360.0;
        String desc = String.format("建议右转 %.0f° 至航向 %.0f°，水平避让", HEADING_DEVIATION_DEG, targetHeading);

        return ResolutionCommand.builder()
                .resolutionType("HEADING")
                .targetDroneSn(snapSelf.getDroneSn())
                .otherDroneSn(snapOther.getDroneSn())
                .targetHeading(targetHeading)
                .description(desc)
                .executeWithinSeconds(conflict.getTcpaSeconds() * 0.3)
                .priority(priority)
                .generatedTimestamp(System.currentTimeMillis())
                .status("PENDING")
                .build();
    }

    @Override
    public String getName() {
        return "HEADING";
    }
}
