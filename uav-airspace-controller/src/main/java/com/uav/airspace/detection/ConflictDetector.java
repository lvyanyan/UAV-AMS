package com.uav.airspace.detection;

import com.uav.airspace.config.AirspaceControllerProperties;
import com.uav.airspace.prediction.KalmanTrajectoryPredictor;
import com.uav.airspace.prediction.PredictionEnvelope;
import com.uav.airspace.state.DroneStateSnapshot;
import com.uav.airspace.state.DroneStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冲突检测引擎
 * <p>
 * 每 200ms 执行一次全局检测：
 * 1. 遍历所有在线无人机 → 卡尔曼预测包络
 * 2. 按 H3 网格分组 → 同网格内两两检测
 * 3. 椭球相交判断 → 碰撞概率 → 阈值过滤
 * <p>
 * 复杂度控制：H3 分组将 O(N²) 降为 O(N × K)，K = 同网格无人机数（通常 < 50）
 */
@Slf4j
@Component
public class ConflictDetector {

    private final DroneStateStore stateStore;
    private final KalmanTrajectoryPredictor predictor;
    private final AirspaceControllerProperties props;

    /** 已检测到的活跃冲突（用于去重和持续跟踪） */
    private final Map<String, ConflictResult> activeConflicts = new ConcurrentHashMap<>();

    /** 冲突去重窗口（秒），同一对无人机在此时间内不重复上报 */
    private static final double DEDUP_WINDOW_SECONDS = 5.0;

    public ConflictDetector(DroneStateStore stateStore,
                            KalmanTrajectoryPredictor predictor,
                            AirspaceControllerProperties props) {
        this.stateStore = stateStore;
        this.predictor = predictor;
        this.props = props;
    }

    /**
     * 执行一轮冲突检测
     *
     * @return 本轮新检测到的冲突列表（不含已去重的持续冲突）
     */
    public List<ConflictResult> detectAll() {

        Map<String, DroneStateSnapshot> allDrones = stateStore.getAll();
        if (allDrones.size() < 2) return Collections.emptyList();

        // ─── Step 1: 批量预测 ───
        Map<String, PredictionEnvelope> envelopes = new HashMap<>();
        for (DroneStateSnapshot snap : allDrones.values()) {
            if (snap.isExpired(props.getStateExpireSeconds())) continue;
            try {
                PredictionEnvelope env = predictor.predict(
                        snap, props.getPredictionSteps(), props.getPredictionStepSeconds());
                envelopes.put(snap.getDroneSn(), env);
            } catch (Exception e) {
                log.debug("预测失败 drone={}: {}", snap.getDroneSn(), e.getMessage());
            }
        }

        // ─── Step 2: H3 分组 ───
        Map<Long, List<String>> h3Groups = new HashMap<>();
        for (DroneStateSnapshot snap : allDrones.values()) {
            h3Groups.computeIfAbsent(snap.getH3Index(), k -> new ArrayList<>())
                    .add(snap.getDroneSn());
        }

        // ─── Step 3: 同网格内两两检测 ───
        List<ConflictResult> newConflicts = new ArrayList<>();
        Set<String> checkedPairs = new HashSet<>();

        for (List<String> group : h3Groups.values()) {
            if (group.size() < 2) continue;
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    String snA = group.get(i);
                    String snB = group.get(j);

                    // 避免重复检查
                    String pairKey = snA.compareTo(snB) < 0
                            ? snA + "|" + snB : snB + "|" + snA;
                    if (!checkedPairs.add(pairKey)) continue;

                    PredictionEnvelope envA = envelopes.get(snA);
                    PredictionEnvelope envB = envelopes.get(snB);
                    if (envA == null || envB == null) continue;

                    ConflictResult conflict = detectPair(envA, envB);
                    if (conflict != null && !isDuplicate(conflict)) {
                        conflict.setConflictId(conflict.generateConflictId());
                        conflict.setEnvelopeA(envA);
                        conflict.setEnvelopeB(envB);
                        newConflicts.add(conflict);
                        activeConflicts.put(conflict.getConflictId(), conflict);
                    }
                }
            }
        }

        // ─── Step 4: 清理失效冲突（无人机已离线或冲突已解除）───
        cleanInactiveConflicts();

        return newConflicts;
    }

    /**
     * 检测一对无人机的冲突
     */
    private ConflictResult detectPair(PredictionEnvelope envA, PredictionEnvelope envB) {

        double minDistance = Double.MAX_VALUE;
        double maxProbability = 0;
        int conflictStep = -1;

        int steps = Math.min(envA.getTotalSteps(), envB.getTotalSteps());

        // 参考纬度（用于度→米转换）
        double refLat = envA.getPositionAt(0)[0];
        double latDegPerMeter = 1.0 / 111320.0;
        double lonDegPerMeter = 1.0 / (111320.0 * Math.cos(Math.toRadians(refLat)));

        for (int step = 0; step <= steps; step++) {
            double[] posA = envA.getPositionAt(step);
            double[] posB = envB.getPositionAt(step);
            if (posA == null || posB == null) continue;

            double[] axesA = envA.getSemiAxesAt(step);
            double[] axesB = envB.getSemiAxesAt(step);

            // 计算中心距离
            double dx = (posB[1] - posA[1]) / lonDegPerMeter;
            double dy = (posB[0] - posA[0]) / latDegPerMeter;
            double dz = posB[2] - posA[2];
            double centerDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (centerDist < minDistance) {
                minDistance = centerDist;
                conflictStep = step;
            }

            // 椭球相交检查
            double prob = EnvelopeIntersection.overlapProbability(
                    posA, axesA, posB, axesB, latDegPerMeter, lonDegPerMeter);
            if (prob > maxProbability) maxProbability = prob;
        }

        // 判断冲突
        if (minDistance < props.getConflictMinDistanceMeters()
                || maxProbability > props.getCollisionProbabilityThreshold()) {

            double[] posA = envA.getPositionAt(conflictStep);
            double tcpa = conflictStep * props.getPredictionStepSeconds();

            String level;
            if (maxProbability > 0.8 || minDistance < 10) {
                level = "CRITICAL";
            } else if (maxProbability > 0.5 || minDistance < 30) {
                level = "SERIOUS";
            } else {
                level = "GENERAL";
            }

            return ConflictResult.builder()
                    .droneSnA(envA.getDroneSn())
                    .droneSnB(envB.getDroneSn())
                    .alarmLevel(level)
                    .tcpaSeconds(tcpa)
                    .collisionLat(posA != null ? posA[0] : 0)
                    .collisionLon(posA != null ? posA[1] : 0)
                    .collisionAlt(posA != null ? posA[2] : 0)
                    .collisionProbability(maxProbability)
                    .minPredictedDistance(minDistance)
                    .conflictStepIndex(conflictStep)
                    .detectTimestamp(System.currentTimeMillis())
                    .build();
        }

        return null;
    }

    private boolean isDuplicate(ConflictResult conflict) {
        String keyA = conflict.getDroneSnA() + "|" + conflict.getDroneSnB();
        String keyB = conflict.getDroneSnB() + "|" + conflict.getDroneSnA();
        long now = System.currentTimeMillis();

        return activeConflicts.values().stream()
                .anyMatch(existing -> {
                    String existingKey = existing.getDroneSnA() + "|" + existing.getDroneSnB();
                    boolean samePair = existingKey.equals(keyA) || existingKey.equals(keyB);
                    boolean recentEnough = (now - existing.getDetectTimestamp())
                            < DEDUP_WINDOW_SECONDS * 1000;
                    return samePair && recentEnough;
                });
    }

    private void cleanInactiveConflicts() {
        long now = System.currentTimeMillis();
        long timeout = props.getStateExpireSeconds() * 2000L; // 2×过期时间
        activeConflicts.entrySet().removeIf(e ->
                (now - e.getValue().getDetectTimestamp()) > timeout);
    }

    public Map<String, ConflictResult> getActiveConflicts() {
        return Collections.unmodifiableMap(activeConflicts);
    }
}
