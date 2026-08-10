package com.uav.track.fusion;

import com.uav.track.model.PositionSource;
import com.uav.track.model.SourceQuality;
import org.springframework.stereotype.Component;

/**
 * 来源权重计算器
 * <p>
 * 综合考量：
 * <ul>
 *   <li>来源固有精度（accuracyMeters 越小权重越高）</li>
 *   <li>更新率（越高越好）</li>
 *   <li>实时质量指标（丢包率、跳变次数）</li>
 * </ul>
 */
@Component
public class SourceWeightCalculator {

    /**
     * 计算来源在当前质量下的融合权重
     *
     * @param source  位置来源类型
     * @param quality 实时质量数据（可能为 null）
     * @return 权重值（0.0 ~ 1.0）
     */
    public double calculate(PositionSource source, SourceQuality quality) {
        if (source == null) return 0.0;

        // 基础权重：精度越高质量越好
        double accuracyWeight = 1.0 / (1.0 + source.getAccuracyMeters());

        // 更新率权重：归一化到 [0, 1]
        double updateRateWeight = Math.min(1.0, source.getUpdateRateHz() / 10.0);

        // 质量惩罚
        double qualityPenalty = 1.0;
        if (quality != null) {
            // 丢包率惩罚
            qualityPenalty *= (1.0 - Math.min(quality.getDropoutRate(), 0.9));

            // 跳变惩罚
            if (quality.getJumpCount() > 3) {
                qualityPenalty *= 0.5;
            }

            // 长时间未更新惩罚
            long idleMs = System.currentTimeMillis() - quality.getLastUpdateTime();
            if (idleMs > 5000) {
                qualityPenalty *= Math.max(0.1, 1.0 - (idleMs - 5000) / 30000.0);
            }
        }

        return accuracyWeight * 0.5 + updateRateWeight * 0.3 + 0.2 * qualityPenalty;
    }
}
