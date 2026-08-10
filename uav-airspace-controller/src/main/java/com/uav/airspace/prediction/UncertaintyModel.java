package com.uav.airspace.prediction;

/**
 * 不确定性膨胀模型
 * <p>
 * 随着预测时间增加，位置不确定性线性增长。
 * σ(t) = σ₀ + k × t，其中 k = uncertaintyGrowthRateMps（可配置，默认 2.0 m/s）
 * <p>
 * 这是简化的线性模型。生产环境可升级为：
 * - 基于加速度残差的自适应膨胀
 * - 基于气象（风速方差）的膨胀因子
 * - 基于 GNSS 精度（HDOP/VDOP）的基础不确定性
 */
public class UncertaintyModel {

    /** 基础不确定性（米）— GPS 单点定位典型误差 */
    private static final double BASE_UNCERTAINTY = 3.0;

    /** 膨胀率（米/秒），每秒预测增加的不确定性 */
    private final double growthRateMps;

    public UncertaintyModel(double growthRateMps) {
        this.growthRateMps = growthRateMps;
    }

    /**
     * 计算指定预测时间的不确定椭球半轴
     *
     * @param predictionTimeSeconds 预测时间（秒），越大越不确定
     * @return [semiEast, semiNorth, semiUp] 单位：米
     */
    public double[] computeUncertainty(double predictionTimeSeconds) {
        double sigma = BASE_UNCERTAINTY + growthRateMps * predictionTimeSeconds;
        // 水平方向：同等膨胀；垂直方向：减半（气压高度计辅助）
        return new double[]{sigma, sigma, sigma * 0.5};
    }

    /**
     * 将不确定椭球膨胀为碰撞检测用的"安全包络"
     * 安全包络 = 3σ + 最小安全距离
     *
     * @param sigmaAxes          原始 3σ 半轴 [east, north, up]
     * @param safetyMarginMeters 安全裕度（米）
     * @return 膨胀后的半轴
     */
    public double[] inflateForSafety(double[] sigmaAxes, double safetyMarginMeters) {
        return new double[]{
                sigmaAxes[0] + safetyMarginMeters,
                sigmaAxes[1] + safetyMarginMeters,
                sigmaAxes[2] + safetyMarginMeters
        };
    }
}
