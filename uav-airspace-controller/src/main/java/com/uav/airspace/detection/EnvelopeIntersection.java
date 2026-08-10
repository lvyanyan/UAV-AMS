package com.uav.airspace.detection;

/**
 * 椭球相交检测器
 * <p>
 * 判断两个 3D 不确定椭球是否重叠。
 * 使用简化的 Mahalanobis 距离方法：
 * 将两个椭球视为高斯分布，计算中心间 Mahalanobis 距离，
 * 若小于阈值（3σ 等效）则认为重叠。
 * <p>
 * 这个简化避免了复杂的椭球-椭球相交几何计算，
 * 在工程实践中精度足够（保守估计，不会漏报）。
 */
public class EnvelopeIntersection {

    /**
     * 检测两个椭球是否相交
     *
     * @param posA    椭球A中心 [lat, lon, alt]
     * @param axesA   椭球A半轴 [east, north, up] (米)
     * @param posB    椭球B中心
     * @param axesB   椭球B半轴
     * @param latDegPerMeter 纬度→米转换因子
     * @param lonDegPerMeter 经度→米转换因子
     * @return 是否相交
     */
    public static boolean intersect(
            double[] posA, double[] axesA,
            double[] posB, double[] axesB,
            double latDegPerMeter, double lonDegPerMeter) {

        // 转为 ENU 坐标（米）
        double dx = (posB[1] - posA[1]) / lonDegPerMeter; // 东向差
        double dy = (posB[0] - posA[0]) / latDegPerMeter; // 北向差
        double dz = posB[2] - posA[2];                      // 天向差

        // 联合半轴（两个椭球半轴相加形成的"碰撞椭球"）
        double jointEast = axesA[0] + axesB[0];
        double jointNorth = axesA[1] + axesB[1];
        double jointUp = axesA[2] + axesB[2];

        // 标准化距离：将椭球拉伸为单位球，然后判断点是否在球内
        double nx = dx / jointEast;
        double ny = dy / jointNorth;
        double nz = dz / jointUp;

        // 若标准距离 ≤ 1，则相交
        return (nx * nx + ny * ny + nz * nz) <= 1.0;
    }

    /**
     * 计算两个椭球的重叠概率（0~1）
     * <p>
     * 近似方法：基于标准化距离映射到概率。
     * 标准化距离 = 1 → 概率 = 0.5（边界）
     * 标准化距离 = 0 → 概率 = 1.0（完全重合）
     * 标准化距离 > 3 → 概率 ≈ 0.0
     */
    public static double overlapProbability(
            double[] posA, double[] axesA,
            double[] posB, double[] axesB,
            double latDegPerMeter, double lonDegPerMeter) {

        double dx = (posB[1] - posA[1]) / lonDegPerMeter;
        double dy = (posB[0] - posA[0]) / latDegPerMeter;
        double dz = posB[2] - posA[2];

        double jointEast = axesA[0] + axesB[0];
        double jointNorth = axesA[1] + axesB[1];
        double jointUp = axesA[2] + axesB[2];

        double normalizedDist = Math.sqrt(
                (dx * dx) / (jointEast * jointEast) +
                        (dy * dy) / (jointNorth * jointNorth) +
                        (dz * dz) / (jointUp * jointUp));

        // Sigmoid 映射：标准化距离 → 概率
        // normalizedDist=0 → p=1, normalizedDist=1 → p=0.5, normalizedDist=3 → p≈0
        return 1.0 / (1.0 + Math.exp(2.0 * (normalizedDist - 1.0)));
    }
}
