package com.uav.airspace.resolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解脱指令 — 冲突解脱的具体操作建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionCommand {

    private String resolutionId;
    private String conflictId;
    private String targetDroneSn;
    private String otherDroneSn;
    private String resolutionType;
    private Double targetSpeed;
    private Double targetAltitude;
    private Double targetHeading;
    private String description;
    private double executeWithinSeconds;
    private int priority;
    private long generatedTimestamp;
    private String status;

    public String generateResolutionId() {
        return "RES-" + targetDroneSn.substring(Math.max(0, targetDroneSn.length() - 6))
                + "-" + System.currentTimeMillis();
    }

    public com.uav.common.dto.ResolutionCommandDTO toDto() {
        return com.uav.common.dto.ResolutionCommandDTO.builder()
                .resolutionId(resolutionId)
                .conflictId(conflictId)
                .targetDroneSn(targetDroneSn)
                .otherDroneSn(otherDroneSn)
                .resolutionType(resolutionType)
                .targetSpeed(targetSpeed)
                .targetAltitude(targetAltitude)
                .targetHeading(targetHeading)
                .description(description)
                .executeWithinSeconds(executeWithinSeconds)
                .priority(priority)
                .generatedTimestamp(generatedTimestamp)
                .status(status)
                .build();
    }
}
