package com.uav.alarm.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 告警开关状态列迁移：alarm_record 增加 status(OPEN/CLOSED) 与 closed_time，
 * 存量数据按 handled 回填。
 */
@Component
@Order(1)
public class AlarmMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public AlarmMigrationRunner(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        jdbc.execute("ALTER TABLE alarm_record ADD COLUMN IF NOT EXISTS status varchar(16)");
        jdbc.execute("ALTER TABLE alarm_record ADD COLUMN IF NOT EXISTS closed_time timestamp");
        jdbc.update("UPDATE alarm_record SET status = CASE WHEN handled THEN 'CLOSED' ELSE 'OPEN' END WHERE status IS NULL");
        jdbc.update("UPDATE alarm_record SET closed_time = create_time WHERE handled = true AND closed_time IS NULL");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_alarm_record_open ON alarm_record (status) WHERE status = 'OPEN'");
        // 存量去重：同一 无人机|告警类型 只保留最新一条 OPEN，其余关闭（开关语义的不变量）
        jdbc.update("UPDATE alarm_record SET status = 'CLOSED', closed_time = now() "
            + "WHERE status = 'OPEN' AND id NOT IN ("
            + "  SELECT max(id) FROM alarm_record WHERE status = 'OPEN' GROUP BY drone_sn, alarm_type)");
    }
}
