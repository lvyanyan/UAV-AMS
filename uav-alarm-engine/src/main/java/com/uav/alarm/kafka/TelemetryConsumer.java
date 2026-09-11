package com.uav.alarm.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.alarm.core.AlarmEngineCore;
import com.uav.alarm.core.AlarmOpenStateStore;
import com.uav.common.dto.AlarmEventDTO;
import com.uav.common.dto.TelemetryDTO;
import com.uav.common.enums.AlarmLevel;
import com.uav.common.enums.AlarmType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka 遥测数据消费者
 * 消费 Topic: uav.telemetry（与 uav-airspace-controller 共享同一 topic）
 *
 * 告警推送为开关语义：同一 无人机|告警类型 只存在一条 OPEN 记录，
 * 命中时若已开启则不再推送，直到被关闭后才能重新触发。
 */
@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);

    private static final long NO_PLAN_DEDUP_MS = 600_000; // 黑飞告警：同机 10 分钟复检
    private static final long LEGAL_REFRESH_MS = 30_000;  // 合法飞行白名单刷新周期

    private final AlarmEngineCore alarmEngine;
    private final ObjectMapper objectMapper;
    private final AlarmEventProducer alarmEventProducer;
    private final JdbcTemplate jdbc;
    private final AlarmOpenStateStore openStore;

    // 黑飞复检节流（黑飞告警关闭前无需反复评估）
    private final Map<String, Long> lastNoPlanEval = new ConcurrentHashMap<>();

    // 合法飞行白名单：已登记 且 当前时段存在已批准计划（黑飞判定的反向集合）
    private volatile Set<String> legalSns = Set.of();
    private volatile long legalRefreshedAt = 0;

    public TelemetryConsumer(AlarmEngineCore alarmEngine,
                             ObjectMapper objectMapper,
                             AlarmEventProducer alarmEventProducer,
                             JdbcTemplate jdbc,
                             AlarmOpenStateStore openStore) {
        this.alarmEngine = alarmEngine;
        this.objectMapper = objectMapper;
        this.alarmEventProducer = alarmEventProducer;
        this.jdbc = jdbc;
        this.openStore = openStore;
    }

    @KafkaListener(topics = "uav.telemetry", groupId = "alarm-engine")
    public void onTelemetry(String message) {
        try {
            TelemetryDTO telemetry = objectMapper.readValue(message, TelemetryDTO.class);

            // 核心告警评估
            List<AlarmEventDTO> alarms = alarmEngine.evaluate(telemetry);

            // 开关语义推送：已开启的同机同类型告警不再推送/落库，直到被关闭
            for (AlarmEventDTO alarm : alarms) {
                if (openStore.isOpen(alarm.getDroneSn(), alarm.getAlarmType().name())) continue;

                openStore.markOpen(alarm.getDroneSn(), alarm.getAlarmType().name());
                alarmEventProducer.sendAlarm(alarm);
                persist(alarm, "OPEN");
                log.warn("ALARM[OPEN]: [{}] {} | {} | drone={} | pos=({},{})",
                        alarm.getAlarmLevel(), alarm.getAlarmType().getLabel(),
                        alarm.getTitle(), alarm.getDroneSn(),
                        alarm.getLatitude(), alarm.getLongitude());
            }

            // 黑飞检测：未登记 / 无有效计划的无人机在飞 → 无计划飞行告警
            checkNoFlightPlan(telemetry);
        } catch (Exception e) {
            log.error("Failed to process telemetry: {}", e.getMessage());
        }
    }

    /**
     * 黑飞（无计划飞行）规则：SN 不在「已登记 + 当前时段已批准计划」白名单内即违规。
     * 白名单 30s 刷新；同机 10 分钟复检（开关状态下开启中的黑飞告警不会重复落库）。
     */
    private void checkNoFlightPlan(TelemetryDTO telemetry) {
        long now = System.currentTimeMillis();
        if (now - legalRefreshedAt > LEGAL_REFRESH_MS) {
            legalRefreshedAt = now;
            try {
                Set<String> sns = new HashSet<>(jdbc.queryForList(
                    "select distinct p.drone_sn from flight_plan p "
                  + "where p.drone_sn is not null and p.plan_status = 'APPROVED' "
                  + "  and p.planned_start <= now() + interval '1 hour' "
                  + "  and p.planned_end   >= now() - interval '1 hour'", String.class));
                legalSns = sns;
            } catch (Exception e) {
                log.error("刷新合法飞行白名单失败: {}", e.getMessage());
            }
        }

        String sn = telemetry.getDroneSn();
        if (sn == null || legalSns.contains(sn)) return;
        if (openStore.isOpen(sn, AlarmType.NO_FLIGHT_PLAN.name())) return;

        Long last = lastNoPlanEval.get(sn);
        if (last != null && now - last < NO_PLAN_DEDUP_MS) return;
        if (lastNoPlanEval.size() > 20000) lastNoPlanEval.clear();
        lastNoPlanEval.put(sn, now);

        openStore.markOpen(sn, AlarmType.NO_FLIGHT_PLAN.name());

        AlarmEventDTO alarm = new AlarmEventDTO();
        alarm.setAlarmId(java.util.UUID.randomUUID().toString());
        alarm.setDroneSn(sn);
        alarm.setAlarmType(AlarmType.NO_FLIGHT_PLAN);
        alarm.setAlarmLevel(AlarmLevel.SERIOUS);
        alarm.setTitle("黑飞嫌疑：无计划飞行");
        alarm.setDescription("该无人机未登记或当前时段无已批准的飞行计划");
        alarm.setLatitude(telemetry.getLatitude());
        alarm.setLongitude(telemetry.getLongitude());
        alarm.setAltitude(telemetry.getAltitude());
        alarm.setAlarmTime(Instant.now());
        alarm.setAcked(false);

        alarmEventProducer.sendAlarm(alarm);
        persist(alarm, "OPEN");
    }

    private void persist(AlarmEventDTO alarm, String status) {
        try {
            jdbc.update("insert into alarm_record (drone_sn, alarm_type, alarm_level, alarm_content, lat, lng, alt, handled, status) "
                    + "values (?, ?, ?, ?, ?, ?, ?, false, ?)",
                    alarm.getDroneSn(), alarm.getAlarmType().name(), alarm.getAlarmLevel().name(),
                    alarm.getTitle(), alarm.getLatitude(), alarm.getLongitude(), alarm.getAltitude(), status);
        } catch (Exception e) {
            log.error("Failed to persist alarm: {}", e.getMessage());
        }
    }
}
