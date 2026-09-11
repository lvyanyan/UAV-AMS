package com.uav.system.controller;

import com.uav.common.base.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统管理只读查询接口（用户 / 角色 / 审计日志 / 违规台账）
 * 密码等敏感字段不下发；写操作后续按 RBAC 权限补齐
 */
@RestController
@RequestMapping("/api")
public class SysQueryController {

    private final JdbcTemplate jdbc;

    public SysQueryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/user/list")
    public R<List<Map<String, Object>>> userList() {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("select id, username, real_name, phone, email, role_code, enabled, create_time "
                + "from sys_user order by id", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("username", rs.getString("username"));
            m.put("realName", rs.getString("real_name"));
            m.put("email", rs.getString("email"));
            m.put("phone", rs.getString("phone"));
            m.put("roleName", rs.getString("role_code"));
            m.put("status", rs.getBoolean("enabled") ? "ACTIVE" : "DISABLED");
            Timestamp t = rs.getTimestamp("create_time");
            m.put("createTime", t == null ? null : t.toString());
            out.add(m);
        });
        return R.ok(out);
    }

    @GetMapping("/role/list")
    public R<List<Map<String, Object>>> roleList() {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("select id, role_code, role_name, description, create_time from sys_role order by id", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("roleCode", rs.getString("role_code"));
            m.put("roleName", rs.getString("role_name"));
            m.put("description", rs.getString("description"));
            Timestamp t = rs.getTimestamp("create_time");
            m.put("createTime", t == null ? null : t.toString());
            out.add(m);
        });
        return R.ok(out);
    }

    @GetMapping("/audit/list")
    public R<List<Map<String, Object>>> auditList() {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("select id, username, action, target, ip_address, create_time "
                + "from sys_audit_log order by id desc limit 200", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("username", rs.getString("username"));
            m.put("action", rs.getString("action"));
            m.put("target", rs.getString("target"));
            m.put("ip", rs.getString("ip_address"));
            Timestamp t = rs.getTimestamp("create_time");
            m.put("createTime", t == null ? null : t.toString());
            out.add(m);
        });
        return R.ok(out);
    }

    /**
     * 违规台账（只读）：由告警记录派生 —— 危急/严重级告警视为违规线索，
     * 处理状态沿用告警的 handled 标记（处置动作走 /api/alarm/{id}/handle）。
     */
    @GetMapping("/violation/list")
    public R<List<Map<String, Object>>> violationList() {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("select id, drone_sn, alarm_type, alarm_level, alarm_content, handled, create_time "
                + "from alarm_record where alarm_level in ('CRITICAL','SERIOUS') order by id desc limit 200", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("droneSn", rs.getString("drone_sn"));
            m.put("violationType", rs.getString("alarm_type"));
            m.put("violationLevel", rs.getString("alarm_level"));
            m.put("description", rs.getString("alarm_content"));
            m.put("status", rs.getBoolean("handled") ? "CLOSED" : "PENDING");
            Timestamp t = rs.getTimestamp("create_time");
            m.put("createTime", t == null ? null : t.toString());
            out.add(m);
        });
        return R.ok(out);
    }
}
