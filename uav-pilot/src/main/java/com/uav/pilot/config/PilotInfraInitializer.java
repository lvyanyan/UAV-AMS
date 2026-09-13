package com.uav.pilot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 体检相关列幂等迁移：exam_org 体检机构、source 记录来源（MANUAL/CENTER）。
 * docker/init.sql 建表时无这两列，存量库靠此补齐。
 */
@Component
@Order(1)
public class PilotInfraInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PilotInfraInitializer.class);

    private final JdbcTemplate jdbc;

    public PilotInfraInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("ALTER TABLE uav_pilot_medical ADD COLUMN IF NOT EXISTS exam_org varchar(128)");
            jdbc.execute("ALTER TABLE uav_pilot_medical ADD COLUMN IF NOT EXISTS source varchar(20) DEFAULT 'MANUAL'");
        } catch (Exception e) {
            log.warn("uav_pilot_medical 新列迁移失败（表可能尚未创建）: {}", e.getMessage());
        }
    }
}
