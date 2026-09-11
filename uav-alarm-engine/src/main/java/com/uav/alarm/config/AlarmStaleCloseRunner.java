package com.uav.alarm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 陈旧告警自动关闭：OPEN 超过 staleMinutes 的告警视为失效自动关闭，
 * 防止历史积压把活跃告警淹没（无人机下次命中会重新开启新记录）。
 * 启动时先清理一次存量，随后每 60s 增量清扫。
 */
@Component
public class AlarmStaleCloseRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AlarmStaleCloseRunner.class);
    private static final int STALE_MINUTES = 30;

    private final JdbcTemplate jdbc;
    private final com.uav.alarm.core.AlarmOpenStateStore openStore;

    public AlarmStaleCloseRunner(JdbcTemplate jdbc, com.uav.alarm.core.AlarmOpenStateStore openStore) {
        this.jdbc = jdbc;
        this.openStore = openStore;
    }

    @Override
    public void run(String... args) {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    List<String> keys = jdbc.queryForList(
                        "select distinct drone_sn || '|' || alarm_type from alarm_record "
                      + "where status = 'OPEN' and create_time < now() - interval '" + STALE_MINUTES + " minutes'",
                        String.class);
                    if (!keys.isEmpty()) {
                        int n = jdbc.update(
                            "update alarm_record set status = 'CLOSED', closed_time = now() "
                          + "where status = 'OPEN' and create_time < now() - interval '" + STALE_MINUTES + " minutes'");
                        keys.forEach(openStore::evictKey);
                        if (n > 0) log.info("陈旧告警自动关闭 {} 条", n);
                    }
                } catch (Exception e) {
                    log.warn("陈旧告警清扫失败: {}", e.getMessage());
                }
                try { Thread.sleep(60_000); } catch (InterruptedException e) { return; }
            }
        }, "alarm-stale-close");
        t.setDaemon(true);
        t.start();
    }
}
