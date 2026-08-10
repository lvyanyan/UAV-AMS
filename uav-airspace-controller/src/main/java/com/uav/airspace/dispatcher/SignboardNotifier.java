package com.uav.airspace.dispatcher;

import com.uav.common.dto.ConflictAlertDTO;
import com.uav.common.dto.ResolutionCommandDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 标牌推送器 — 通过 Kafka Topic 向 Cesium 前端推送解脱指令和冲突告警
 */
@Slf4j
@Component
public class SignboardNotifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public SignboardNotifier(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void push(String droneSn, ResolutionCommandDTO dto) {
        try {
            String json = MAPPER.writeValueAsString(dto);
            kafkaTemplate.send("uav.signboard.resolution", droneSn, json);
            log.debug("标牌推送 → drone={} resolution={}", droneSn, dto.getResolutionId());
        } catch (Exception e) {
            log.error("标牌推送失败 drone={}: {}", droneSn, e.getMessage());
        }
    }

    public void pushConflictAlert(ConflictAlertDTO alert) {
        try {
            String json = MAPPER.writeValueAsString(alert);
            kafkaTemplate.send("uav.signboard.conflict", alert.getConflictId(), json);
            log.debug("冲突告警推送 → conflict={} level={}", alert.getConflictId(), alert.getAlarmLevel());
        } catch (Exception e) {
            log.error("冲突告警推送失败: {}", e.getMessage());
        }
    }
}
