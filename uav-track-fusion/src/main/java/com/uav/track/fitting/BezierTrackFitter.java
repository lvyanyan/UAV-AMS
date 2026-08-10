package com.uav.track.fitting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 贝塞尔曲线轨迹拟合器
 * <p>
 * 在卡尔曼平滑后的轨迹点之间插值生成贝塞尔曲线段，
 * 使前端渲染的轨迹更平滑美观。
 * <p>
 * 使用二次贝塞尔（3 个控制点/段），插值密度可配置。
 */
@Slf4j
@Component
public class BezierTrackFitter {

    /** 每段原始轨迹之间的贝塞尔插值点数 */
    private static final int INTERPOLATION_POINTS = 4;

    /**
     * 对平滑后的轨迹点进行贝塞尔插值
     *
     * @param smoothedPoints 卡尔曼平滑后的点 [lat, lon, alt]
     * @return 贝塞尔插值后的密集点列表
     */
    public List<double[]> fit(List<double[]> smoothedPoints) {
        if (smoothedPoints == null || smoothedPoints.size() < 3) {
            return smoothedPoints == null ? List.of() : smoothedPoints;
        }

        List<double[]> result = new ArrayList<>();
        // 第一个点直接加入
        result.add(smoothedPoints.get(0));

        // 每三个连续点作为一段二次贝塞尔曲线
        for (int i = 0; i < smoothedPoints.size() - 2; i++) {
            double[] p0 = smoothedPoints.get(i);
            double[] p1 = smoothedPoints.get(i + 1);
            double[] p2 = smoothedPoints.get(i + 2);

            // p0 → p1 → p2 的二次贝塞尔，p1 是控制点
            // 但为了让曲线过 p1，使用中点方案
            double[] mid01 = midpoint(p0, p1);
            double[] mid12 = midpoint(p1, p2);

            // 二次贝塞尔: 控制点为 p1，端点为 mid01 和 mid12
            for (int k = 1; k <= INTERPOLATION_POINTS; k++) {
                double t = (double) k / (INTERPOLATION_POINTS + 1);
                double[] pt = quadraticBezier(mid01, p1, mid12, t);
                result.add(pt);
            }
            result.add(mid12);
        }

        // 最后一个点
        result.add(smoothedPoints.get(smoothedPoints.size() - 1));

        return result;
    }

    /**
     * 二次贝塞尔: B(t) = (1-t)²·P0 + 2(1-t)t·P1 + t²·P2
     */
    private double[] quadraticBezier(double[] p0, double[] p1, double[] p2, double t) {
        double u = 1.0 - t;
        return new double[]{
                u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
                u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1],
                u * u * p0[2] + 2 * u * t * p1[2] + t * t * p2[2]
        };
    }

    private double[] midpoint(double[] a, double[] b) {
        return new double[]{
                (a[0] + b[0]) / 2.0,
                (a[1] + b[1]) / 2.0,
                (a[2] + b[2]) / 2.0
        };
    }
}
