package com.uav.alarm.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 告警抑制规则存储（服务端记录用户选择，落库）。
 * 命中抑制规则的告警在生成侧直接过滤：不推送 Kafka/WS、不落 alarm_record。
 */
@Component
public class AlarmSuppressStore {

    private final JdbcTemplate jdbc;
    private final List<Map<String, Object>> rules = new CopyOnWriteArrayList<>();
    private volatile long refreshedAt = 0;

    public AlarmSuppressStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 规则 30s 自动刷新（增删改由接口写入后调用 refresh 立即生效） */
    public synchronized void refresh() {
        if (System.currentTimeMillis() - refreshedAt < 30_000) return;
        reload();
    }

    public synchronized void reload() {
        try {
            rules.clear();
            rules.addAll(jdbc.queryForList(
                "select id, user_id, alarm_type, alarm_level, drone_sn from alarm_suppress order by id"));
            refreshedAt = System.currentTimeMillis();
        } catch (Exception ignore) { }
    }

    public List<Map<String, Object>> all() {
        refresh();
        return rules;
    }

    public boolean isSuppressed(String type, String level, String sn) {
        refresh();
        for (Map<String, Object> r : rules) {
            boolean typeHit = r.get("alarm_type") == null || type.equals(r.get("alarm_type"));
            boolean levelHit = r.get("alarm_level") == null || level.equals(r.get("alarm_level"));
            boolean snHit = r.get("drone_sn") == null || sn.equals(r.get("drone_sn"));
            // 一条规则内的字段为 AND（未填=不限），规则之间为 OR
            if (typeHit && levelHit && snHit) return true;
        }
        return false;
    }
}
