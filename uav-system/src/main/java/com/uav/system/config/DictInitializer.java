package com.uav.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 字典表初始化：建表 + 幂等种子数据
 * 枚举值盘点自生产库（2026-09-11）：
 *   airspace_type 实际含 CTR/RESTRICTED/TEST，alarm_type 实际含 TERRAIN_COLLISION，
 *   drone_type 实际含 FIXED_WING/MULTIROTOR，role_code 五种。
 */
@Component
@Order(2)
public class DictInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DictInitializer.class);

    private final JdbcTemplate jdbc;

    public DictInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final List<String[]> SEED = List.of(
        // dict_type, dict_value, dict_label, sort
        row("airspace_type", "CONTROL", "管制区", 1),
        row("airspace_type", "CTR", "机场管制区", 2),
        row("airspace_type", "OPERATION", "作业区", 3),
        row("airspace_type", "CORRIDOR", "走廊", 4),
        row("airspace_type", "DEMO", "示范区", 5),
        row("airspace_type", "NO_FLY", "禁飞区", 6),
        row("airspace_type", "RESTRICTED", "限制区", 7),
        row("airspace_type", "TEST", "试验区", 8),
        row("airspace_type", "TEMP_NO_FLY", "临时禁飞", 9),

        row("drone_type", "MULTIROTOR", "多旋翼", 1),
        row("drone_type", "FIXED_WING", "固定翼", 2),
        row("drone_type", "HELICOPTER", "直升机", 3),
        row("drone_type", "VTOL", "垂直起降", 4),

        row("register_status", "PENDING", "待审核", 1),
        row("register_status", "APPROVED", "已通过", 2),
        row("register_status", "REJECTED", "已拒绝", 3),

        row("pilot_status", "ACTIVE", "正常", 1),
        row("pilot_status", "SUSPENDED", "停飞", 2),

        row("plan_status", "DRAFT", "草稿", 1),
        row("plan_status", "PENDING_LEVEL1", "一级审批中", 2),
        row("plan_status", "PENDING_LEVEL2", "二级审批中", 3),
        row("plan_status", "PENDING_LEVEL3", "三级审批中", 4),
        row("plan_status", "APPROVED", "已批准", 5),
        row("plan_status", "REJECTED", "已拒绝", 6),
        row("plan_status", "MILITARY_CANCELLED", "军事取消", 7),
        row("plan_status", "COMPLETED", "已完成", 8),

        row("alarm_level", "CRITICAL", "危急", 1),
        row("alarm_level", "MAJOR", "重大", 2),
        row("alarm_level", "SERIOUS", "严重", 3),
        row("alarm_level", "WARNING", "警告", 4),
        row("alarm_level", "GENERAL", "一般", 5),
        row("alarm_level", "MINOR", "轻微", 6),

        row("alarm_type", "TERRAIN_COLLISION", "地形碰撞", 1),
        row("alarm_type", "AIRSPACE", "空域违规", 2),
        row("alarm_type", "NO_PLAN", "无计划飞行", 3),
        row("alarm_type", "ALTITUDE", "高度超限", 4),
        row("alarm_type", "SPEED", "超速飞行", 5),
        row("alarm_type", "GEOFENCE", "围栏闯入", 6),
        row("alarm_type", "CONFLICT", "飞行冲突", 7),
        row("alarm_type", "ROUTE", "航路偏离", 8),
        row("alarm_type", "WEATHER", "气象风险", 9),
        row("alarm_type", "EQUIPMENT", "设备异常", 10),
        row("alarm_type", "TERRAIN", "地形风险", 11),

        row("user_role", "ADMIN", "系统管理员", 1),
        row("user_role", "REGULATOR", "监管员", 2),
        row("user_role", "OPERATOR", "操作员", 3),
        row("user_role", "PILOT", "飞手", 4),
        row("user_role", "MILITARY", "军民协调员", 5),

        row("violation_status", "PENDING", "待处理", 1),
        row("violation_status", "PROCESSING", "处理中", 2),
        row("violation_status", "CLOSED", "已结案", 3)
    );

    private static String[] row(String type, String value, String label, int sort) {
        return new String[]{type, value, label, String.valueOf(sort)};
    }

    @Override
    public void run(String... args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_dict ("
            + "id bigserial PRIMARY KEY, "
            + "dict_type varchar(64) NOT NULL, "
            + "dict_value varchar(64) NOT NULL, "
            + "dict_label varchar(64) NOT NULL, "
            + "sort_order int DEFAULT 0, "
            + "remark varchar(255))");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_dict_type_value ON sys_dict (dict_type, dict_value)");

        int inserted = 0;
        for (String[] r : SEED) {
            inserted += jdbc.update(
                "INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort_order) VALUES (?,?,?,?) "
                + "ON CONFLICT (dict_type, dict_value) DO NOTHING",
                r[0], r[1], r[2], Integer.valueOf(r[3]));
        }
        log.info("字典初始化完成：{} 个类型 / 新插入 {} 条（共 {} 条）",
            jdbc.queryForObject("select count(distinct dict_type) from sys_dict", Integer.class),
            inserted,
            jdbc.queryForObject("select count(*) from sys_dict", Integer.class));
    }
}
