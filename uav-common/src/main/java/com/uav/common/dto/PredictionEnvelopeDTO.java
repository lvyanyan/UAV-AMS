package com.uav.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 预测包络 DTO — 描述无人机未来轨迹的3σ不确定椭球
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionEnvelopeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 无人机唯一标识 */
    private String droneSn;

    /** 预测时间戳（本包络对应的时间点） */
    private long timestamp;

    /** 预测位置 — 椭球中心 (WGS84) */
    private double lat;
    private double lon;
    private double alt;

    /** 椭球半轴长度（米），分别对应东向/北向/天向 */
    private double semiAxisEast;
    private double semiAxisNorth;
    private double semiAxisUp;

    /** 预测步长索引（0 = 当前，1/2/3... = 未来第N步） */
    private int stepIndex;

    /** 该包络对应的碰撞概率阈值 */
    private double probabilityThreshold;

    // ─── 轨迹中心线（用于前端渲染）───
    /** 从当前点到未来N步的完整预测轨迹（按时间排序） */
    private List<double[]> trajectoryCenterLine;
}
