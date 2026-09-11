package com.uav.alarm.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.common.dto.AlarmEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 告警事件 Kafka 生产者
 * 输出 Topic: uav.alarm.event
 * → uav-realtime (Go) 消费此 topic 推送到 WebSocket → 前端
 */
@Component
public class AlarmEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AlarmEventProducer.class);
    private static final String TOPIC = "uav.alarm.event";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AlarmEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendAlarm(AlarmEventDTO alarm) {
        try {
            String json = objectMapper.writeValueAsString(alarm);
            kafkaTemplate.send(TOPIC, alarm.getDroneSn(), json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Kafka] 告警事件发送失败: {}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("[Kafka] 告警事件序列化失败: {}", e.getMessage());
        }
    }
}
