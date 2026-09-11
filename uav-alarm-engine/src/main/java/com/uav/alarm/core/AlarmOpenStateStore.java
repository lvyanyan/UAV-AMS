package com.uav.alarm.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警开关状态存储：同一 无人机|告警类型 只允许一条 OPEN 记录。
 * 命中时先查是否已开启——开启则不再推送/落库，直到人工关闭后才能重新触发。
 * 内存缓存 + 数据库兜底（缓存未命中时回查一次库，避免多实例/重启后误判）。
 */
@Component
public class AlarmOpenStateStore {

    private final JdbcTemplate jdbc;
    private final Set<String> openKeys = ConcurrentHashMap.newKeySet();
    private volatile boolean loaded = false;

    public AlarmOpenStateStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static String key(String droneSn, String type) {
        return droneSn + "|" + type;
    }

    /** 启动加载存量 OPEN 记录 */
    public synchronized void loadIfNeed() {
        if (loaded) return;
        try {
            openKeys.addAll(jdbc.queryForList(
                "select drone_sn || '|' || alarm_type from alarm_record where status = 'OPEN'", String.class));
            loaded = true;
        } catch (Exception ignore) { }
    }

    /** 是否已开启：缓存优先，未命中回查数据库（查到回填缓存） */
    public boolean isOpen(String droneSn, String type) {
        loadIfNeed();
        String k = key(droneSn, type);
        if (openKeys.contains(k)) return true;
        try {
            Integer n = jdbc.queryForObject(
                "select count(*) from alarm_record where drone_sn = ? and alarm_type = ? and status = 'OPEN'",
                Integer.class, droneSn, type);
            if (n != null && n > 0) { openKeys.add(k); return true; }
        } catch (Exception ignore) { }
        return false;
    }

    public void markOpen(String droneSn, String type) {
        loadIfNeed();
        openKeys.add(key(droneSn, type));
    }

    public void markClosed(String droneSn, String type) {
        openKeys.remove(key(droneSn, type));
    }
}
