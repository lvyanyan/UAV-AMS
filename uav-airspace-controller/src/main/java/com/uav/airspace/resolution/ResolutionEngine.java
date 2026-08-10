package com.uav.airspace.resolution;

import com.uav.airspace.config.AirspaceControllerProperties;
import com.uav.airspace.detection.ConflictResult;
import com.uav.airspace.state.DroneStateSnapshot;
import com.uav.airspace.state.DroneStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 解脱引擎 — 按优先级链式尝试各策略
 * <p>
 * 策略链：SPEED → ALTITUDE → HEADING → COMBINED
 * 对冲突中的两架无人机分别生成解脱建议。
 * 优先给更灵活的无人机（速度低、高度适中）下发指令。
 * <p>
 * 注意：只给其中一架生成指令，避免两机同时调整产生振荡。
 */
@Slf4j
@Component
public class ResolutionEngine {

    private final List<ResolutionStrategy> strategies;
    private final DroneStateStore stateStore;
    private final AirspaceControllerProperties props;

    public ResolutionEngine(List<ResolutionStrategy> strategies,
                            DroneStateStore stateStore,
                            AirspaceControllerProperties props) {
        // 按优先级排序：SPEED → ALTITUDE → HEADING → COMBINED
        this.strategies = new ArrayList<>(strategies);
        this.strategies.sort(Comparator.comparingInt(s ->
                switch (s.getName()) {
                    case "SPEED" -> 0;
                    case "ALTITUDE" -> 1;
                    case "HEADING" -> 2;
                    case "COMBINED" -> 3;
                    default -> 99;
                }));
        this.stateStore = stateStore;
        this.props = props;
    }

    /**
     * 为冲突生成解脱指令
     *
     * @param conflict 冲突结果
     * @return 解脱指令（可能为 null，若无需干预）
     */
    public ResolutionCommand resolve(ConflictResult conflict) {

        DroneStateSnapshot snapA = stateStore.get(conflict.getDroneSnA());
        DroneStateSnapshot snapB = stateStore.get(conflict.getDroneSnB());

        if (snapA == null || snapB == null) {
            log.debug("冲突双方中有一方已离线，跳过解脱: {} vs {}",
                    conflict.getDroneSnA(), conflict.getDroneSnB());
            return null;
        }

        // 选择承担解脱动作的无人机（优先选更灵活的）
        DroneStateSnapshot target = selectTargetDrone(snapA, snapB);
        DroneStateSnapshot other = (target == snapA) ? snapB : snapA;

        // 计算优先级（基于冲突严重程度）
        int priority = computePriority(conflict);

        if (priority < props.getResolutionMinPriority()) {
            log.debug("冲突优先级 {} 低于阈值 {}，不生成解脱指令",
                    priority, props.getResolutionMinPriority());
            return null;
        }

        // 按策略链尝试
        for (ResolutionStrategy strategy : strategies) {
            if (strategy.isApplicable(conflict, target, other)) {
                ResolutionCommand cmd = strategy.generate(conflict, target, other, priority);
                cmd.setResolutionId(cmd.generateResolutionId());
                cmd.setConflictId(conflict.getConflictId());
                log.info("生成解脱指令: type={} target={} desc={}",
                        cmd.getResolutionType(), cmd.getTargetDroneSn(), cmd.getDescription());
                return cmd;
            }
        }

        log.warn("所有解脱策略均不适用，冲突 {} 无法自动解脱", conflict.getConflictId());
        return null;
    }

    /**
     * 选择承担解脱动作的无人机
     * 优先选择：速度较低的 → 更容易调速；高度适中的 → 有更大的垂直空间
     */
    private DroneStateSnapshot selectTargetDrone(DroneStateSnapshot a, DroneStateSnapshot b) {
        double speedA = a.getLatestTelemetry().getSpeed();
        double speedB = b.getLatestTelemetry().getSpeed();
        double altA = a.getLatestTelemetry().getAlt();
        double altB = b.getLatestTelemetry().getAlt();

        // 评分：速度低 + 分，高度极端(太低或太高) + 分
        double scoreA = speedA + Math.abs(altA - 120) * 0.01;
        double scoreB = speedB + Math.abs(altB - 120) * 0.01;

        return scoreA <= scoreB ? a : b;
    }

    private int computePriority(ConflictResult conflict) {
        return switch (conflict.getAlarmLevel()) {
            case "CRITICAL" -> 100;
            case "SERIOUS" -> 60;
            default -> 30;
        };
    }
}
