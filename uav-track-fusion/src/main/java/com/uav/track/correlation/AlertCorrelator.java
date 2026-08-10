package com.uav.track.correlation;

import com.uav.common.dto.AlarmEventDTO;
import com.uav.track.model.TrackAlertAnnotation;
import com.uav.track.model.TrackSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警轨迹耦合器
 * <p>
 * 将从 Kafka 消费的告警事件绑定到轨迹段上：
 * <ul>
 *   <li>时间维度：告警时间落在哪个轨迹段内</li>
 *   <li>空间维度：告警位置与轨迹点的最近匹配</li>
 * </ul>
 * <p>
 * 输出：带告警标注的轨迹段列表，前端可据此为轨迹着色。
 */
@Slf4j
@Component
public class AlertCorrelator {

    /** 告警缓冲区：droneSn → 告警事件列表 */
    private final ConcurrentHashMap<String, List<AlarmEventDTO>> alertBuffer = new ConcurrentHashMap<>();

    /** 每个无人机最多缓存多少条告警 */
    private static final int MAX_ALERTS_PER_DRONE = 200;

    /**
     * 接收告警事件，存入缓冲区
     */
    public void receiveAlert(AlarmEventDTO alert) {
        alertBuffer.computeIfAbsent(alert.getDroneSn(), k -> new ArrayList<>()).add(alert);
        List<AlarmEventDTO> list = alertBuffer.get(alert.getDroneSn());
        while (list.size() > MAX_ALERTS_PER_DRONE) {
            list.remove(0);
        }
    }

    /**
     * 将告警事件与轨迹段关联
     *
     * @param droneSn  无人机 SN
     * @param segments 轨迹段列表
     * @return 标注后的轨迹段列表（原地修改 + 返回）
     */
    public List<TrackSegment> annotate(String droneSn, List<TrackSegment> segments) {
        List<AlarmEventDTO> alerts = alertBuffer.get(droneSn);
        if (alerts == null || alerts.isEmpty()) return segments;

        // 按时间排序
        alerts.sort((a, b) -> {
            Instant ta = a.getAlarmTime(), tb = b.getAlarmTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return -1;
            if (tb == null) return 1;
            return ta.compareTo(tb);
        });

        for (TrackSegment seg : segments) {
            List<TrackAlertAnnotation> annotations = new ArrayList<>();
            String maxLevel = "GENERAL";

            for (AlarmEventDTO alert : alerts) {
                Instant alarmTime = alert.getAlarmTime();
                if (alarmTime == null) continue;

                // 告警时间是否落在此段内
                if (!alarmTime.isBefore(seg.getStartTime()) && !alarmTime.isAfter(seg.getEndTime())) {
                    // 找到最近的轨迹点索引
                    int nearestIdx = findNearestPointIndex(seg.getSmoothedPoints(),
                            alert.getLatitude(), alert.getLongitude());

                    TrackAlertAnnotation annotation = TrackAlertAnnotation.builder()
                            .alarmId(alert.getAlarmId())
                            .alarmType(alert.getAlarmType() != null ? alert.getAlarmType().name() : "UNKNOWN")
                            .alarmLevel(alert.getAlarmLevel() != null ? alert.getAlarmLevel().name() : "GENERAL")
                            .title(alert.getTitle())
                            .alarmTime(alarmTime)
                            .latitude(alert.getLatitude())
                            .longitude(alert.getLongitude())
                            .altitude(alert.getAltitude())
                            .pointIndexInSegment(nearestIdx)
                            .acked(alert.getAcked() != null && alert.getAcked())
                            .build();

                    annotations.add(annotation);

                    // 更新段内最大告警等级
                    if (isHigherLevel(annotation.getAlarmLevel(), maxLevel)) {
                        maxLevel = annotation.getAlarmLevel();
                    }
                }
            }

            if (!annotations.isEmpty()) {
                seg.setHasAlert(true);
                seg.setAlertAnnotations(annotations);
                seg.setMaxAlertLevel(maxLevel);
            }
        }

        return segments;
    }

    /**
     * 在轨迹点列表中找到离告警位置最近的点索引
     */
    private int findNearestPointIndex(List<double[]> points, Double alertLat, Double alertLon) {
        if (points == null || points.isEmpty() || alertLat == null || alertLon == null) return 0;

        int nearest = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            double[] pt = points.get(i);
            double dlat = pt[0] - alertLat;
            double dlon = pt[1] - alertLon;
            double dist = dlat * dlat + dlon * dlon; // 平方比较，避免 sqrt
            if (dist < minDist) {
                minDist = dist;
                nearest = i;
            }
        }
        return nearest;
    }

    private boolean isHigherLevel(String a, String b) {
        return levelWeight(a) > levelWeight(b);
    }

    private int levelWeight(String level) {
        return switch (level) {
            case "CRITICAL" -> 3;
            case "SERIOUS" -> 2;
            case "GENERAL" -> 1;
            default -> 0;
        };
    }

    /**
     * 获取指定无人机的活跃告警数
     */
    public int getActiveAlertCount(String droneSn) {
        List<AlarmEventDTO> alerts = alertBuffer.get(droneSn);
        return alerts == null ? 0 : (int) alerts.stream().filter(a -> a.getAcked() == null || !a.getAcked()).count();
    }

    /**
     * 清理已确认的告警
     */
    public void evictAcked(String droneSn) {
        List<AlarmEventDTO> alerts = alertBuffer.get(droneSn);
        if (alerts != null) {
            alerts.removeIf(a -> a.getAcked() != null && a.getAcked());
        }
    }
}
