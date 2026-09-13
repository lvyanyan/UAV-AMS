package com.uav.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RBAC 初始化器：建表 + 幂等种子（角色 / 权限 / 角色-权限映射）
 * 与 deploy/init-db.sql、docker/init.sql 的 RBAC 段完全一致，
 * 保证不手工执行 SQL 时服务启动即可用；老库缺列/旧结构自动迁移。
 * 运行顺序：本类(Order 1) → DictInitializer(Order 2) → DataInitializer(默认最后，账户创建逻辑不变)
 */
@Component
@Order(1)
public class RbacInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RbacInitializer.class);

    private final JdbcTemplate jdbc;

    public RbacInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 建表 + 老库兼容迁移（全部幂等） */
    private static final String[] DDL = {
        // 角色表（role_code 主键）
        "CREATE TABLE IF NOT EXISTS sys_role ("
            + "role_code VARCHAR(32) PRIMARY KEY, "
            + "role_name VARCHAR(64) NOT NULL, "
            + "description VARCHAR(255), "
            + "enabled BOOLEAN DEFAULT TRUE, "
            + "created_at TIMESTAMP DEFAULT now())",
        // 老版本 sys_role 为 id 主键 + create_time，缺列补齐
        "ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE",
        "ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT now()",
        // 权限表（perm_type 仅 MENU/BUTTON）
        "CREATE TABLE IF NOT EXISTS sys_permission ("
            + "id BIGSERIAL PRIMARY KEY, "
            + "perm_code VARCHAR(64) NOT NULL UNIQUE, "
            + "perm_name VARCHAR(64) NOT NULL, "
            + "perm_type VARCHAR(16) DEFAULT 'MENU' CHECK (perm_type IN ('MENU','BUTTON')), "
            + "parent_id BIGINT DEFAULT 0, "
            + "path VARCHAR(255), "
            + "icon VARCHAR(64), "
            + "sort_order INT DEFAULT 0, "
            + "created_at TIMESTAMP DEFAULT now())",
        "ALTER TABLE sys_permission ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT now()",
        // 角色权限关联表（role_code + perm_code 联合主键）
        "CREATE TABLE IF NOT EXISTS sys_role_permission ("
            + "role_code VARCHAR(32) NOT NULL, "
            + "perm_code VARCHAR(64) NOT NULL, "
            + "PRIMARY KEY (role_code, perm_code))",
        // 老版本 role_id/perm_id 结构为空表，直接替换
        "DO $$ BEGIN "
            + "IF EXISTS (SELECT 1 FROM information_schema.columns "
            + "WHERE table_name = 'sys_role_permission' AND column_name = 'role_id') THEN "
            + "DROP TABLE sys_role_permission; "
            + "CREATE TABLE sys_role_permission ("
            + "role_code VARCHAR(32) NOT NULL, "
            + "perm_code VARCHAR(64) NOT NULL, "
            + "PRIMARY KEY (role_code, perm_code)); "
            + "END IF; END $$",
        // 审计日志补 created_at 列（AuditAspect 写入该列，create_time 保留兼容旧读取方）
        "ALTER TABLE sys_audit_log ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT now()"
    };

    /** 角色种子：code / name / description */
    private static final String[][] ROLE_SEED = {
        {"ADMIN",      "系统管理员", "最高权限，拥有全部菜单与操作权限"},
        {"OPERATOR",   "操作员",     "登记备案与飞行计划创建、提交"},
        {"REGULATOR",  "监管员",     "业务监管与飞行计划审批"},
        {"MILITARY",   "军民协调员", "军事调度、军事审批与一键清场"},
        {"SUPERVISOR", "上级领导",   "只读查看所有业务数据"},
        {"PILOT",      "驾驶员",     "飞行计划填报与基础查询"}
    };

    /** 权限种子：perm_code / perm_name / perm_type / path / icon / sort_order */
    private static final String[][] PERM_SEED = {
        {"dashboard:menu",    "仪表盘",     "MENU", "/dashboard",      "Odometer",             "1"},
        {"monitor:menu",      "飞行监控",   "MENU", "/flight-monitor", "Monitor",              "2"},
        {"replay:menu",       "消息重放",   "MENU", "/message-replay", "VideoPlay",            "3"},
        {"flightplan:menu",   "飞行计划",   "MENU", "/flight-plan",    "Document",             "4"},
        {"airspace:menu",     "空域管理",   "MENU", "/airspace",       "MapLocation",          "5"},
        {"airroute:menu",     "航路管理",   "MENU", "/air-route",      "Guide",                "6"},
        {"airport:menu",      "起降场管理", "MENU", "/airport",        "LocationInformation",  "7"},
        {"registry:menu",     "实名登记",   "MENU", "/registry",       "Files",                "8"},
        {"pilot:menu",        "飞手管理",   "MENU", "/pilot",          "UserFilled",           "9"},
        {"alarm:menu",        "告警中心",   "MENU", "/alarm",          "Bell",                 "10"},
        {"violation:menu",    "违规处置",   "MENU", "/violation",      "WarningFilled",        "11"},
        {"military:menu",     "军事调度",   "MENU", "/military",       "Medal",                "12"},
        {"system:menu",       "系统管理",   "MENU", "/system",         "Setting",              "13"},
        {"system:user:menu",  "用户管理",   "MENU", "/system/users",   "UserFilled",           "1"},
        {"system:role:menu",  "角色管理",   "MENU", "/system/roles",   "Avatar",               "2"},
        {"system:audit:menu", "审计日志",   "MENU", "/system/audit",   "Memo",                 "3"},
        {"system:dict:menu",  "字典管理",   "MENU", "/system/dict",    "Collection",           "4"},
        {"flightplan:create",     "计划创建",   "BUTTON", "", "", "1"},
        {"flightplan:submit",     "计划提交",   "BUTTON", "", "", "2"},
        {"flightplan:approve",    "计划审批",   "BUTTON", "", "", "3"},
        {"flightplan:military",   "军事协调",   "BUTTON", "", "", "4"},
        {"military:approve",      "军事批准",   "BUTTON", "", "", "1"},
        {"alarm:suppress",        "告警抑制",   "BUTTON", "", "", "1"},
        {"system:user:create",    "新增用户",   "BUTTON", "", "", "1"},
        {"system:user:update",    "编辑用户",   "BUTTON", "", "", "2"},
        {"system:user:delete",    "删除用户",   "BUTTON", "", "", "3"},
        {"system:user:reset-pwd", "重置密码",   "BUTTON", "", "", "4"},
        {"system:user:enable",    "启停用户",   "BUTTON", "", "", "5"},
        {"system:role:create",    "新增角色",   "BUTTON", "", "", "1"},
        {"system:role:update",    "编辑角色",   "BUTTON", "", "", "2"},
        {"system:role:delete",    "删除角色",   "BUTTON", "", "", "3"},
        {"system:role:assign",    "分配权限",   "BUTTON", "", "", "4"},
        {"system:dict:manage",    "字典维护",   "BUTTON", "", "", "1"},
        {"system:perm:manage",    "权限项维护", "BUTTON", "", "", "5"}
    };

    /** 旧版单词风格权限码（被 域:资源:操作 取代，启动时清理） */
    private static final String[] LEGACY_PERM_CODES = {
        "dashboard", "flight_monitor", "flight_plan", "airspace_mgmt", "registry", "pilot_mgmt",
        "alarm_center", "violation", "system_mgmt", "military_ops", "plan_approve",
        "military_approve", "airspace_clear"
    };

    /** 非 ADMIN 角色的权限映射（ADMIN 由“全量 SELECT”覆盖） */
    private static final Map<String, List<String>> ROLE_PERMS = Map.ofEntries(
        Map.entry("REGULATOR", List.of(
            "dashboard:menu", "monitor:menu", "replay:menu",
            "flightplan:menu", "flightplan:create", "flightplan:submit", "flightplan:approve",
            "airspace:menu", "airroute:menu", "airport:menu", "registry:menu", "pilot:menu",
            "alarm:menu", "violation:menu")),
        Map.entry("OPERATOR", List.of(
            "dashboard:menu", "monitor:menu", "replay:menu",
            "flightplan:menu", "flightplan:create", "flightplan:submit",
            "airspace:menu", "airroute:menu", "airport:menu", "registry:menu", "pilot:menu",
            "alarm:menu")),
        Map.entry("MILITARY", List.of(
            "dashboard:menu", "monitor:menu", "replay:menu",
            "flightplan:menu", "flightplan:military",
            "military:menu", "military:approve", "alarm:menu")),
        Map.entry("SUPERVISOR", List.of(
            "dashboard:menu", "monitor:menu", "replay:menu",
            "flightplan:menu", "airspace:menu", "airroute:menu", "airport:menu",
            "registry:menu", "pilot:menu", "alarm:menu", "violation:menu", "military:menu")),
        Map.entry("PILOT", List.of(
            "dashboard:menu", "flightplan:menu", "flightplan:create", "flightplan:submit",
            "alarm:menu"))
    );

    @Override
    public void run(String... args) {
        for (String ddl : DDL) {
            jdbc.execute(ddl);
        }

        // 清理旧版权限码及其残留映射
        for (String legacy : LEGACY_PERM_CODES) {
            jdbc.update("DELETE FROM sys_role_permission WHERE perm_code = ?", legacy);
            jdbc.update("DELETE FROM sys_permission WHERE perm_code = ?", legacy);
        }

        // 角色种子（幂等：保留既有 enabled 状态）
        for (String[] r : ROLE_SEED) {
            jdbc.update("INSERT INTO sys_role (role_code, role_name, description, enabled) VALUES (?,?,?,TRUE) "
                    + "ON CONFLICT (role_code) DO UPDATE SET "
                    + "role_name = EXCLUDED.role_name, description = EXCLUDED.description, "
                    + "created_at = COALESCE(sys_role.created_at, EXCLUDED.created_at)",
                r[0], r[1], r[2]);
        }

        // 权限种子（幂等，parent_id 先置 0）
        int perms = 0;
        for (String[] p : PERM_SEED) {
            perms += jdbc.update("INSERT INTO sys_permission (perm_code, perm_name, perm_type, parent_id, path, icon, sort_order) "
                    + "VALUES (?,?,?,0,?,?,?) "
                    + "ON CONFLICT (perm_code) DO UPDATE SET "
                    + "perm_name = EXCLUDED.perm_name, perm_type = EXCLUDED.perm_type, "
                    + "path = EXCLUDED.path, icon = EXCLUDED.icon, sort_order = EXCLUDED.sort_order",
                p[0], p[1], p[2], p[3], p[4], Integer.valueOf(p[5]));
        }

        // 系统管理子菜单挂到 system:menu 下
        jdbc.update("UPDATE sys_permission child SET parent_id = parent.id "
                + "FROM sys_permission parent "
                + "WHERE parent.perm_code = 'system:menu' AND child.perm_code IN "
                + "('system:user:menu','system:role:menu','system:audit:menu','system:dict:menu')");

        // 按钮权限挂到同域菜单下（flightplan:x → flightplan:menu 等）
        jdbc.update("UPDATE sys_permission child SET parent_id = parent.id "
                + "FROM sys_permission parent "
                + "WHERE child.perm_type = 'BUTTON' "
                + "AND parent.perm_code = split_part(child.perm_code, ':', 1) || ':menu'");

        // 角色-权限映射（幂等）
        jdbc.update("INSERT INTO sys_role_permission (role_code, perm_code) "
                + "SELECT 'ADMIN', perm_code FROM sys_permission "
                + "ON CONFLICT (role_code, perm_code) DO NOTHING");
        int mappings = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_code = 'ADMIN'", Integer.class);
        for (Map.Entry<String, List<String>> e : ROLE_PERMS.entrySet()) {
            for (String perm : e.getValue()) {
                jdbc.update("INSERT INTO sys_role_permission (role_code, perm_code) VALUES (?,?) "
                        + "ON CONFLICT (role_code, perm_code) DO NOTHING", e.getKey(), perm);
            }
        }

        int roleCount = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role", Integer.class);
        int permCount = jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission", Integer.class);
        int mapCount = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission", Integer.class);
        log.info("RBAC 初始化完成：角色 {} 个（新增种子 {} 条）/ 权限 {} 项（本轮 upsert {}）/ 角色-权限映射 {} 条（ADMIN {} 条）",
            roleCount, ROLE_SEED.length, permCount, perms, mapCount, mappings);
    }
}
