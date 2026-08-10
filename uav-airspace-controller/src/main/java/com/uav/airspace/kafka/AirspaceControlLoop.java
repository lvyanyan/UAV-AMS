package com.uav.airspace.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.airspace.config.AirspaceControllerProperties;
import com.uav.airspace.detection.ConflictDetector;
import com.uav.airspace.detection.ConflictResult;
import com.uav.airspace.dispatcher.ResolutionDispatcher;
import com.uav.airspace.resolution.ResolutionCommand;
import com.uav.airspace.resolution.ResolutionEngine;
import com.uav.airspace.state.DroneStateStore;
import com.uav.common.dto.ConflictAlertDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 空域控制器调度循环
 * <p>
 * 每 200ms 执行一次：清理过期状态 → 冲突检测 → 生成解脱指令 → 分发
 */
@Slf4j
@Component
public class AirspaceControlLoop {

    private final DroneStateStore stateStore;
    private final ConflictDetector conflictDetector;
    private final ResolutionEngine resolutionEngine;
    private final ResolutionDispatcher dispatcher;
    private final AirspaceControllerProperties props;
    private final ObjectMapper objectMapper;

    public AirspaceControlLoop(DroneStateStore stateStore,
                               ConflictDetector conflictDetector,
                               ResolutionEngine resolutionEngine,
                               ResolutionDispatcher dispatcher,
                               AirspaceControllerProperties props) {
        this.stateStore = stateStore;
        this.conflictDetector = conflictDetector;
        this.resolutionEngine = resolutionEngine;
        this.dispatcher = dispatcher;
        this.props = props;
        this.objectMapper = new ObjectMapper();
    }

    @Scheduled(fixedDelayString = "${airspace.detection-interval-ms:200}")
    public void tick() {
        long start = System.currentTimeMillis();

        // 1. 清理过期状态
        stateStore.cleanExpired(props.getStateExpireSeconds());

        int onlineCount = stateStore.size();
        if (onlineCount < 2) return; // 少于 2 架无需检测

        // 2. 冲突检测
        List<ConflictResult> conflicts = conflictDetector.detectAll();

        if (conflicts.isEmpty()) return;

        // 3. 为每个冲突生成解脱指令并分发
        for (ConflictResult conflict : conflicts) {
            try {
                // 生成解脱指令
                ResolutionCommand resolution = resolutionEngine.resolve(conflict);
                if (resolution == null) continue;

                // 转换为 DTO
                ConflictAlertDTO alertDto = toAlertDto(conflict);

                // 分发
                dispatcher.dispatch(resolution);
                dispatcher.dispatchWithConflict(alertDto, resolution.toDto());

            } catch (Exception e) {
                log.error("处理冲突失败 conflict={}: {}", conflict.getConflictId(), e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > 150) {
            log.warn("控制循环耗时 {}ms (在线 {} 架, 冲突 {} 个)", elapsed, onlineCount, conflicts.size());
        }
    }

    private ConflictAlertDTO toAlertDto(ConflictResult conflict) {
        return ConflictAlertDTO.builder()
                .conflictId(conflict.getConflictId())
                .droneSnA(conflict.getDroneSnA())
                .droneSnB(conflict.getDroneSnB())
                .alarmLevel(conflict.getAlarmLevel())
                .tcpaSeconds(conflict.getTcpaSeconds())
                .collisionLat(conflict.getCollisionLat())
                .collisionLon(conflict.getCollisionLon())
                .collisionAlt(conflict.getCollisionAlt())
                .collisionProbability(conflict.getCollisionProbability())
                .envelopeA(conflict.getEnvelopeA() != null
                        ? conflict.getEnvelopeA().toDtoList() : null)
                .envelopeB(conflict.getEnvelopeB() != null
                        ? conflict.getEnvelopeB().toDtoList() : null)
                .detectTimestamp(conflict.getDetectTimestamp())
                .active(true)
                .build();
    }
}
