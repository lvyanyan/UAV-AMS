package com.uav.track.fusion;

import com.uav.track.model.PositionSource;
import com.uav.track.model.RawPosition;
import com.uav.track.model.SourceQuality;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多源融合引擎
 * <p>
 * 核心逻辑：
 * <ol>
 *   <li>维护每个无人机的原始位置缓冲区（按来源分队列）</li>
 *   <li>时间窗口内（如 500ms）的多源观测 → 加权平均融合</li>
 *   <li>只有一个来源时直接使用</li>
 *   <li>实时跟踪每个来源的质量指标</li>
 * </ol>
 */
@Slf4j
@Component
public class MultiSourceFusionEngine {

    /** 融合时间窗口（ms），窗口内的多源观测视为同一时刻 */
    private static final long FUSION_WINDOW_MS = 500;

    /** 每个无人机每来源最多缓冲多少个点 */
    private static final int MAX_BUFFER_PER_SOURCE = 50;

    /** 位置缓冲区：droneSn → (PositionSource → 位置队列) */
    private final ConcurrentHashMap<String, Map<PositionSource, Deque<RawPosition>>> buffer = new ConcurrentHashMap<>();

    /** 来源质量跟踪：droneSn → (PositionSource → SourceQuality) */
    private final ConcurrentHashMap<String, Map<PositionSource, SourceQuality>> qualityMap = new ConcurrentHashMap<>();

    private final SourceWeightCalculator weightCalculator;

    public MultiSourceFusionEngine(SourceWeightCalculator weightCalculator) {
        this.weightCalculator = weightCalculator;
    }

    /**
     * 接收一个原始位置，返回融合后的位置（可能为 null，表示还需等待更多数据）
     *
     * @param raw 原始位置
     * @return 融合后的位置，或 null（缓冲区中等待）
     */
    public RawPosition ingest(RawPosition raw) {
        String sn = raw.getDroneSn();
        PositionSource source = raw.getSource();
        raw.setReceivedAt(Instant.now());

        // 加入缓冲区
        buffer.computeIfAbsent(sn, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(source, k -> new ArrayDeque<>())
                .add(raw);

        // 裁剪缓冲区
        Deque<RawPosition> deque = buffer.get(sn).get(source);
        while (deque.size() > MAX_BUFFER_PER_SOURCE) {
            deque.pollFirst();
        }

        // 更新质量跟踪
        updateQuality(sn, source, raw);

        // 查找时间窗口内的所有来源观测
        List<RawPosition> windowPositions = collectWindowPositions(sn, raw.getTimestamp());

        if (windowPositions.size() <= 1) {
            // 只有一个来源 → 直接返回（不做融合，但保持缓冲区）
            return enhanceWithQuality(raw, sn, source);
        }

        // 多源融合：加权平均
        return weightedFuse(windowPositions, sn);
    }

    /**
     * 收集时间窗口内的所有来源观测
     */
    private List<RawPosition> collectWindowPositions(String sn, Instant timestamp) {
        List<RawPosition> result = new ArrayList<>();
        Map<PositionSource, Deque<RawPosition>> droneBuffer = buffer.get(sn);
        if (droneBuffer == null) return result;

        long windowStart = timestamp.toEpochMilli() - FUSION_WINDOW_MS;
        long windowEnd = timestamp.toEpochMilli() + FUSION_WINDOW_MS;

        for (Deque<RawPosition> queue : droneBuffer.values()) {
            // 取队列最后一个在窗口内的数据
            RawPosition best = null;
            long bestDist = Long.MAX_VALUE;
            for (RawPosition p : queue) {
                long t = p.getTimestamp().toEpochMilli();
                if (t >= windowStart && t <= windowEnd) {
                    long dist = Math.abs(t - timestamp.toEpochMilli());
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = p;
                    }
                }
            }
            if (best != null) {
                result.add(best);
            }
        }
        return result;
    }

    /**
     * 多源加权融合
     */
    private RawPosition weightedFuse(List<RawPosition> positions, String sn) {
        // 计算每个来源的权重
        double[] weights = new double[positions.size()];
        double totalWeight = 0;

        for (int i = 0; i < positions.size(); i++) {
            RawPosition p = positions.get(i);
            SourceQuality quality = getQuality(sn, p.getSource());
            weights[i] = weightCalculator.calculate(p.getSource(), quality);
            totalWeight += weights[i];
        }

        if (totalWeight <= 0) {
            // 所有权重为 0 → 回退到 DRONE_TELEMETRY 或第一个
            for (RawPosition p : positions) {
                if (p.getSource() == PositionSource.DRONE_TELEMETRY) return p;
            }
            return positions.get(0);
        }

        // 加权平均
        double fusedLat = 0, fusedLon = 0, fusedAlt = 0;
        double fusedHeading = 0, fusedSpeed = 0, fusedClimb = 0;
        double fusedAccuracy = 0;

        for (int i = 0; i < positions.size(); i++) {
            double w = weights[i] / totalWeight;
            RawPosition p = positions.get(i);
            fusedLat += p.getLatitude() * w;
            fusedLon += p.getLongitude() * w;
            fusedAlt += p.getAltitude() * w;
            fusedHeading += p.getHeading() != null ? p.getHeading() * w : 0;
            fusedSpeed += p.getGroundSpeed() != null ? p.getGroundSpeed() * w : 0;
            fusedClimb += p.getClimbRate() != null ? p.getClimbRate() * w : 0;
            fusedAccuracy += p.getSource().getAccuracyMeters() * w;
        }

        // 用时间戳最新的
        RawPosition newest = positions.stream()
                .max(Comparator.comparing(RawPosition::getTimestamp))
                .orElse(positions.get(0));

        return RawPosition.builder()
                .droneSn(sn)
                .source(PositionSource.DRONE_TELEMETRY) // 融合后标记为遥测源
                .latitude(fusedLat).longitude(fusedLon).altitude(fusedAlt)
                .heading(fusedHeading).groundSpeed(fusedSpeed).climbRate(fusedClimb)
                .timestamp(newest.getTimestamp())
                .reportedAccuracy(fusedAccuracy)
                .receivedAt(Instant.now())
                .build();
    }

    private RawPosition enhanceWithQuality(RawPosition raw, String sn, PositionSource source) {
        SourceQuality q = getQuality(sn, source);
        if (raw.getReportedAccuracy() == null) {
            raw.setReportedAccuracy(source.getAccuracyMeters());
        }
        return raw;
    }

    private SourceQuality getQuality(String sn, PositionSource source) {
        return qualityMap.computeIfAbsent(sn, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(source, k -> SourceQuality.builder()
                        .source(source)
                        .trustWeight(0.8)
                        .lastUpdateTime(System.currentTimeMillis())
                        .build());
    }

    private void updateQuality(String sn, PositionSource source, RawPosition raw) {
        SourceQuality q = getQuality(sn, source);
        long now = System.currentTimeMillis();
        long interval = now - q.getLastUpdateTime();

        // 指数平滑更新平均间隔
        if (q.getAvgUpdateIntervalMs() == 0) {
            q.setAvgUpdateIntervalMs(interval);
        } else {
            q.setAvgUpdateIntervalMs((long) (q.getAvgUpdateIntervalMs() * 0.8 + interval * 0.2));
        }

        q.setLastUpdateTime(now);
        q.setTotalReceived(q.getTotalReceived() + 1);

        // 更新可信度权重
        q.setTrustWeight(weightCalculator.calculate(source, q));
    }

    /**
     * 获取指定无人机的活跃来源列表
     */
    public List<PositionSource> getActiveSources(String droneSn) {
        Map<PositionSource, SourceQuality> m = qualityMap.get(droneSn);
        if (m == null) return List.of();
        long now = System.currentTimeMillis();
        return m.entrySet().stream()
                .filter(e -> (now - e.getValue().getLastUpdateTime()) < 10000) // 10s 内有更新
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 清理离线无人机的缓冲区（超过 60s 无更新）
     */
    public void cleanup(long maxIdleMs) {
        long now = System.currentTimeMillis();
        buffer.entrySet().removeIf(entry -> {
            Map<PositionSource, Deque<RawPosition>> m = entry.getValue();
            boolean allIdle = m.values().stream().allMatch(q -> {
                RawPosition last = q.peekLast();
                return last != null && (now - last.getReceivedAt().toEpochMilli()) > maxIdleMs;
            });
            if (allIdle) {
                qualityMap.remove(entry.getKey());
            }
            return allIdle;
        });
    }
}
