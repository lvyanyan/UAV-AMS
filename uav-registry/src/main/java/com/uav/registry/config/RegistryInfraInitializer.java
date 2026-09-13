package com.uav.registry.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * UOM 对接基础设施：external_integration_log 对接日志表（与 docker/init.sql 同构，存量库兜底创建）
 * + 登记/所有人表的 UOM 上报状态列（uom_status / uom_report_time），全部幂等。
 */
@Component
@Order(1)
public class RegistryInfraInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RegistryInfraInitializer.class);

    private final JdbcTemplate jdbc;

    public RegistryInfraInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS external_integration_log ("
                + "id bigserial PRIMARY KEY, "
                + "system_code varchar(50) NOT NULL, "
                + "event_type varchar(50) NOT NULL, "
                + "payload text, "
                + "status varchar(20), "
                + "retry_count int DEFAULT 0, "
                + "error_msg text, "
                + "create_time timestamp DEFAULT now())");
            jdbc.execute("ALTER TABLE uav_owner ADD COLUMN IF NOT EXISTS uom_status varchar(20)");
            jdbc.execute("ALTER TABLE uav_owner ADD COLUMN IF NOT EXISTS uom_report_time timestamp");
            jdbc.execute("ALTER TABLE uav_registration ADD COLUMN IF NOT EXISTS uom_status varchar(20)");
            jdbc.execute("ALTER TABLE uav_registration ADD COLUMN IF NOT EXISTS uom_report_time timestamp");
        } catch (Exception e) {
            log.warn("UOM 对接基础设施初始化失败: {}", e.getMessage());
        }
    }
}
