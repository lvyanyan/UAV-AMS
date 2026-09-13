package com.uav.alarm.config;

import com.uav.common.dto.AlarmEventDTO;
import com.uav.common.enums.AlarmLevel;
import com.uav.common.enums.AlarmType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.uav.alarm.core.AlarmOpenStateStore;
import com.uav.alarm.kafka.AlarmEventProducer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 资质到期检查（执照/体检）：扫描 uav_pilot 与 uav_pilot_medical，
 * 到期（已过期）→ SERIOUS 告警；30 天内到期 → GENERAL 预警；条件恢复（续证/复检后）→ 自动关闭存量告警。
 *
 * 与飞行告警的差异：资质告警是持续状态，drone_sn 存 "PILOT-{id}"（非真实无人机），
 * 不参与陈旧关闭（AlarmStaleCloseRunner 已排除），只有条件恢复或人工处置才关闭。
 */
@Component
public class QualificationExpireRunner {

    private static final Logger log = LoggerFactory.getLogger(QualificationExpireRunner.class);
    /** 到期预警窗口 */
    private static final int EXPIRY_WARN_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final AlarmOpenStateStore openStore;
    private final AlarmEventProducer producer;

    public QualificationExpireRunner(JdbcTemplate jdbc, AlarmOpenStateStore openStore, AlarmEventProducer producer) {
        this.jdbc = jdbc;
        this.openStore = openStore;
        this.producer = producer;
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 45_000)
    public void scheduledCheck() {
        try {
            check();
        } catch (Exception e) {
            log.warn("资质到期检查失败: {}", e.getMessage());
        }
    }

    /** 执行一轮资质检查，返回摘要（供手动触发接口） */
    public synchronized Map<String, Object> check() {
        int raised = 0;
        int resolved = 0;
        raised += checkLicenses();
        resolved += resolveValidLicenses();
        raised += checkMedicals();
        resolved += resolveValidMedicals();
        if (raised + resolved > 0) {
            log.info("资质到期检查：新开 {} 条，恢复关闭 {} 条", raised, resolved);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("checkedAt", LocalDateTime.now().toString());
        summary.put("raised", raised);
        summary.put("resolved", resolved);
        return summary;
    }

    /** 执照：过期 → SERIOUS；EXPIRY_WARN_DAYS 内 → GENERAL */
    private int checkLicenses() {
        int raised = 0;
        for (Map<String, Object> p : jdbc.queryForList(
            "select id, pilot_name, license_expire from uav_pilot where license_expire is not null")) {
            LocalDateTime expire = ((Timestamp) p.get("license_expire")).toLocalDateTime();
            String sn = pilotSn(p.get("id"));
            String name = String.valueOf(p.get("pilot_name"));
            if (expire.isBefore(LocalDateTime.now())) {
                raised += raise(sn, AlarmType.LICENSE_EXPIRE, AlarmLevel.SERIOUS,
                    "飞手 " + name + " 执照已过期（有效期至 " + expire.toLocalDate() + "），禁止执行飞行任务");
            } else if (expire.isBefore(LocalDateTime.now().plusDays(EXPIRY_WARN_DAYS))) {
                raised += raise(sn, AlarmType.LICENSE_EXPIRE, AlarmLevel.GENERAL,
                    "飞手 " + name + " 执照将于 " + expire.toLocalDate() + " 到期，请及时换发");
            }
        }
        return raised;
    }

    /** 体检：取每名飞手最新一次体检的有效期，规则同执照 */
    private int checkMedicals() {
        int raised = 0;
        for (Map<String, Object> r : jdbc.queryForList(
            "select p.id as pid, p.pilot_name, m.expire_date from uav_pilot p "
          + "join (select pilot_id, expire_date, row_number() over (partition by pilot_id order by exam_date desc, id desc) rn "
          + "      from uav_pilot_medical where expire_date is not null) m on m.pilot_id = p.id and m.rn = 1")) {
            LocalDateTime expire = ((Timestamp) r.get("expire_date")).toLocalDateTime();
            String sn = pilotSn(r.get("pid"));
            String name = String.valueOf(r.get("pilot_name"));
            if (expire.isBefore(LocalDateTime.now())) {
                raised += raise(sn, AlarmType.MEDICAL_EXPIRE, AlarmLevel.SERIOUS,
                    "飞手 " + name + " 体检证已过期（有效期至 " + expire.toLocalDate() + "），禁止执行飞行任务");
            } else if (expire.isBefore(LocalDateTime.now().plusDays(EXPIRY_WARN_DAYS))) {
                raised += raise(sn, AlarmType.MEDICAL_EXPIRE, AlarmLevel.GENERAL,
                    "飞手 " + name + " 体检证将于 " + expire.toLocalDate() + " 到期，请及时复检");
            }
        }
        return raised;
    }

    /** 条件恢复：执照已续期内但仍有 OPEN 告警 → 自动关闭 */
    private int resolveValidLicenses() {
        return resolve(AlarmType.LICENSE_EXPIRE,
            "select id from uav_pilot where license_expire is not null and license_expire >= now() + interval '" + EXPIRY_WARN_DAYS + " days'");
    }

    private int resolveValidMedicals() {
        return resolve(AlarmType.MEDICAL_EXPIRE,
            "select p.id from uav_pilot p join (select pilot_id, max(expire_date) me from uav_pilot_medical group by pilot_id) m "
          + "on m.pilot_id = p.id where m.me >= now() + interval '" + EXPIRY_WARN_DAYS + " days'");
    }

    private int resolve(AlarmType type, String validPilotSql) {
        int closed = 0;
        for (Long id : jdbc.queryForList(validPilotSql, Long.class)) {
            String sn = pilotSn(id);
            if (openStore.isOpen(sn, type.name())) {
                jdbc.update("update alarm_record set status = 'CLOSED', closed_time = now() "
                    + "where drone_sn = ? and alarm_type = ? and status = 'OPEN'", sn, type.name());
                openStore.markClosed(sn, type.name());
                closed++;
            }
        }
        return closed;
    }

    private int raise(String sn, AlarmType type, AlarmLevel level, String content) {
        if (openStore.isOpen(sn, type.name())) return 0;
        openStore.markOpen(sn, type.name());

        AlarmEventDTO alarm = new AlarmEventDTO();
        alarm.setAlarmId(java.util.UUID.randomUUID().toString());
        alarm.setDroneSn(sn);
        alarm.setAlarmType(type);
        alarm.setAlarmLevel(level);
        alarm.setTitle(content);
        alarm.setDescription("资质到期检查（执照/体检有效期管理）");
        alarm.setAlarmTime(Instant.now());
        alarm.setAcked(false);
        producer.sendAlarm(alarm);
        try {
            jdbc.update("insert into alarm_record (drone_sn, alarm_type, alarm_level, alarm_content, handled, status) "
                + "values (?,?,?,?,false,'OPEN')", sn, type.name(), level.name(), content);
        } catch (Exception e) {
            log.error("资质告警落库失败: {}", e.getMessage());
        }
        log.warn("ALARM[OPEN]: [{}] {} | {}", level, type.getLabel(), content);
        return 1;
    }

    private String pilotSn(Object pilotId) {
        return "PILOT-" + pilotId;
    }
}
