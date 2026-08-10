package com.uav.track.correlation;

import com.uav.track.model.FittedTrack;
import com.uav.track.model.TrackSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 飞行计划关联器
 * <p>
 * 将拟合后的轨迹与飞行计划进行比对：
 * <ul>
 *   <li>实际轨迹 vs 计划航路 → 偏离检测</li>
 *   <li>ETA 对比（当前进度 vs 计划时间表）</li>
 * </ul>
 * <p>
 * 当前版本：通过 Kafka 消费飞行计划数据（uav-flight-plan 模块发布），
 * 内存缓存计划航路点，与拟合轨迹做空间比对。
 * 飞行计划模块开发完成后对接真实数据。
 */
@Slf4j
@Component
public class FlightPlanCorrelator {

    /**
     * 将拟合轨迹与飞行计划关联
     * <p>
     * 当前为 Stub 实现：不做实际比对，仅保留接口。
     * 飞行计划模块（uav-flight-plan）开发完成后替换为真实实现。
     *
     * @param track    拟合轨迹
     * @param droneSn  无人机 SN
     * @return 关联后的轨迹（含偏离标记）
     */
    public FittedTrack correlate(FittedTrack track, String droneSn) {
        // TODO: 飞行计划模块完成后实现
        // 1. 从 Redis 缓存获取该无人机的活跃飞行计划
        // 2. 提取计划的航路点列表
        // 3. 对每个轨迹段，计算与计划航路的最近距离
        // 4. 超过偏离阈值（如 200m）→ 标记 deviatedFromPlan
        // 5. 计算 ETA 偏差

        track.setDeviatedFromPlan(false);
        track.setDeviationDistanceMeters(0.0);
        return track;
    }

    /**
     * 计算轨迹点与计划航路之间的最大偏离距离
     *
     * @param segments    轨迹段列表
     * @param routePoints 计划航路点 [lat, lon, alt]
     * @return 最大偏离距离（米）
     */
    public double computeMaxDeviation(List<TrackSegment> segments, List<double[]> routePoints) {
        if (routePoints == null || routePoints.size() < 2) return 0.0;

        double maxDist = 0.0;
        for (TrackSegment seg : segments) {
            if (seg.getSmoothedPoints() == null) continue;
            for (double[] pt : seg.getSmoothedPoints()) {
                double minDist = minDistanceToRoute(pt, routePoints);
                if (minDist > maxDist) maxDist = minDist;
            }
        }
        return maxDist;
    }

    /**
     * 点到折线的最短距离（Haversine 近似）
     */
    private double minDistanceToRoute(double[] point, List<double[]> route) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < route.size() - 1; i++) {
            double[] a = route.get(i);
            double[] b = route.get(i + 1);
            double dist = pointToSegmentDistanceHaversine(point, a, b);
            if (dist < min) min = dist;
        }
        return min;
    }

    /**
     * 点到线段的大圆距离（米）
     */
    private double pointToSegmentDistanceHaversine(double[] p, double[] a, double[] b) {
        double latP = Math.toRadians(p[0]), lonP = Math.toRadians(p[1]);
        double latA = Math.toRadians(a[0]), lonA = Math.toRadians(a[1]);
        double latB = Math.toRadians(b[0]), lonB = Math.toRadians(b[1]);

        // 线段两端点的大圆距离
        double abDist = haversine(latA, lonA, latB, lonB);
        if (abDist < 1e-6) return haversine(latP, lonP, latA, lonA);

        // 使用球面几何投影（简化：平面近似在短距离下足够）
        // 转为 ENU 局部坐标
        double R = 6371000.0;
        double xA = R * Math.cos(latA) * Math.cos(lonA);
        double yA = R * Math.cos(latA) * Math.sin(lonA);
        double zA = R * Math.sin(latA);
        double xB = R * Math.cos(latB) * Math.cos(lonB);
        double yB = R * Math.cos(latB) * Math.sin(lonB);
        double zB = R * Math.sin(latB);
        double xP = R * Math.cos(latP) * Math.cos(lonP);
        double yP = R * Math.cos(latP) * Math.sin(lonP);
        double zP = R * Math.sin(latP);

        // 向量 AB 和 AP
        double abx = xB - xA, aby = yB - yA, abz = zB - zA;
        double apx = xP - xA, apy = yP - yA, apz = zP - zA;

        // 投影参数 t
        double t = (apx * abx + apy * aby + apz * abz) / (abx * abx + aby * aby + abz * abz);
        t = Math.max(0, Math.min(1, t));

        // 投影点
        double cx = xA + t * abx, cy = yA + t * aby, cz = zA + t * abz;

        // 距离
        double dx = xP - cx, dy = yP - cy, dz = zP - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dlat = lat2 - lat1, dlon = lon2 - lon1;
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dlon / 2) * Math.sin(dlon / 2);
        return 6371000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
