package com.uav.alarm.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.alarm.core.AlarmEngineCore;
import com.uav.common.dto.AlarmEventDTO;
import com.uav.common.dto.TelemetryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka 遥测数据消费者
 * 消费 Topic: uav.telemetry（与 uav-airspace-controller 共享同一 topic）
 */
@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);
    private static final long DEDUP_MS = 60_000; // 同机同类型告警 60s 内只发一次

    private final AlarmEngineCore alarmEngine;
    private final ObjectMapper objectMapper;
    private final AlarmEventProducer alarmEventProducer;
    private final JdbcTemplate jdbc;
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public TelemetryConsumer(AlarmEngineCore alarmEngine,
                             ObjectMapper objectMapper,
                             AlarmEventProducer alarmEventProducer,
                             JdbcTemplate jdbc) {
        this.alarmEngine = alarmEngine;
        this.objectMapper = objectMapper;
        this.alarmEventProducer = alarmEventProducer;
        this.jdbc = jdbc;
    }

    private long probeSeq = 0;

    @KafkaListener(topics = "uav.telemetry", groupId = "alarm-engine")
    public void onTelemetry(String message) {
        try {
            if (probeSeq++ % 2000 == 0) {
                log.info("RAW[{}]: {}", probeSeq, message.length() > 180 ? message.substring(0, 180) : message);
            }
            TelemetryDTO telemetry = objectMapper.readValue(message, TelemetryDTO.class);

            // 核心告警评估
            List<AlarmEventDTO> alarms = alarmEngine.evaluate(telemetry);

            // 发送告警事件到 Kafka + 落库（同机同类型 60s 去重）
            for (AlarmEventDTO alarm : alarms) {
                String dedupKey = alarm.getDroneSn() + "|" + alarm.getAlarmType();
                long now = System.currentTimeMillis();
                Long last = lastSent.get(dedupKey);
                if (last != null && now - last < DEDUP_MS) continue;
                if (lastSent.size() > 20000) lastSent.clear();
                lastSent.put(dedupKey, now);

                alarmEventProducer.sendAlarm(alarm);
                persist(alarm);
                log.warn("ALARM: [{}] {} | {} | drone={} | pos=({},{})",
                        alarm.getAlarmLevel(), alarm.getAlarmType().getLabel(),
                        alarm.getTitle(), alarm.getDroneSn(),
                        alarm.getLatitude(), alarm.getLongitude());
            }
        } catch (Exception e) {
            log.error("Failed to process telemetry: {}", e.getMessage());
        }
    }

    private void persist(AlarmEventDTO alarm) {
        try {
            jdbc.update("insert into alarm_record (drone_sn, alarm_type, alarm_level, alarm_content, lat, lng, alt, handled) "
                    + "values (?, ?, ?, ?, ?, ?, ?, false)",
                    alarm.getDroneSn(), alarm.getAlarmType().name(), alarm.getAlarmLevel().name(),
                    alarm.getTitle(), alarm.getLatitude(), alarm.getLongitude(), alarm.getAltitude());
        } catch (Exception e) {
            log.error("Failed to persist alarm: {}", e.getMessage());
        }
    }
}
