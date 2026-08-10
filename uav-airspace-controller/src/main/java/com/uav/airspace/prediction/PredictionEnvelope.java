package com.uav.airspace.prediction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 预测包络 — 包含中心轨迹 + 每个时间步的 3σ 椭球参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionEnvelope {

    /** 无人机 SN */
    private String droneSn;

    /** 预测时间戳（生成时刻） */
    private long generatedAt;

    /** 预测总步数 */
    private int totalSteps;

    /** 每步时间（秒） */
    private double stepSeconds;

    // ─── 中心轨迹（预测位置序列）───
    /** [stepIndex][lat, lon, alt] */
    private List<double[]> centerLine;

    // ─── 每个时间步的不确定椭球参数 ───
    /** [stepIndex] → {semiEast, semiNorth, semiUp} 单位：米 */
    private List<double[]> uncertaintySemiAxes;

    /**
     * 获取指定步数的椭球半轴（东/北/天）
     */
    public double[] getSemiAxesAt(int step) {
        if (step < 0 || step >= uncertaintySemiAxes.size()) return new double[]{0, 0, 0};
        return uncertaintySemiAxes.get(step);
    }

    /**
     * 获取指定步数的预测位置 [lat, lon, alt]
     */
    public double[] getPositionAt(int step) {
        if (step < 0 || step >= centerLine.size()) return null;
        return centerLine.get(step);
    }

    /**
     * 转换为 DTO 列表（用于 Kafka 传输）
     */
    public List<com.uav.common.dto.PredictionEnvelopeDTO> toDtoList() {
        return java.util.stream.IntStream.range(0, centerLine.size())
                .mapToObj(i -> {
                    double[] pos = centerLine.get(i);
                    double[] axes = uncertaintySemiAxes.get(i);
                    return com.uav.common.dto.PredictionEnvelopeDTO.builder()
                            .droneSn(droneSn)
                            .timestamp(generatedAt + (long) (i * stepSeconds * 1000))
                            .lat(pos[0]).lon(pos[1]).alt(pos[2])
                            .semiAxisEast(axes[0])
                            .semiAxisNorth(axes[1])
                            .semiAxisUp(axes[2])
                            .stepIndex(i)
                            .probabilityThreshold(0.997) // 3σ
                            .trajectoryCenterLine(centerLine)
                            .build();
                }).toList();
    }
}
