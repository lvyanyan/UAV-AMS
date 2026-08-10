package com.uav.alarm.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.alarm.core.AlarmEngineCore;
import com.uav.common.dto.AlarmEventDTO;
import com.uav.common.dto.TelemetryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka 遥测数据消费者
 * 消费 Topic: uav.telemetry（与 uav-airspace-controller 共享同一 topic）
 */
@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);
    private final AlarmEngineCore alarmEngine;
    private final ObjectMapper objectMapper;
    private final AlarmEventProducer alarmEventProducer;

    public TelemetryConsumer(AlarmEngineCore alarmEngine,
                             ObjectMapper objectMapper,
                             AlarmEventProducer alarmEventProducer) {
        this.alarmEngine = alarmEngine;
        this.objectMapper = objectMapper;
        this.alarmEventProducer = alarmEventProducer;
    }

    @KafkaListener(topics = "uav.telemetry", groupId = "alarm-engine")
    public void onTelemetry(String message) {
        try {
            TelemetryDTO telemetry = objectMapper.readValue(message, TelemetryDTO.class);

            // 核心告警评估
            List<AlarmEventDTO> alarms = alarmEngine.evaluate(telemetry);

            // 发送告警事件到 Kafka
            for (AlarmEventDTO alarm : alarms) {
                alarmEventProducer.sendAlarm(alarm);
                log.warn("ALARM: [{}] {} | {} | drone={} | pos=({},{})",
                        alarm.getAlarmLevel(), alarm.getAlarmType().getLabel(),
                        alarm.getTitle(), alarm.getDroneSn(),
                        alarm.getLatitude(), alarm.getLongitude());
            }
        } catch (Exception e) {
            log.error("Failed to process telemetry: {}", e.getMessage());
        }
    }
}
