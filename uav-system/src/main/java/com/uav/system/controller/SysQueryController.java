package com.uav.system.controller;

import com.uav.common.base.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统管理只读查询接口（用户 / 角色 / 审计日志）
 * 密码等敏感字段不下发；写操作后续按 RBAC 权限补齐
 */
@RestController
@RequestMapping("/api")
public class SysQueryController {

    private final JdbcTemplate jdbc;

    public SysQueryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/user/list")
    public R<List<Map<String, Object>>> userList() {
        return R.ok(jdbc.queryForList(
            "select id, username, real_name, phone, email, org_id, role_code, enabled, "
          + "mfa_enabled, create_time from sys_user order by id"));
    }

    @GetMapping("/role/list")
    public R<List<Map<String, Object>>> roleList() {
        return R.ok(jdbc.queryForList(
            "select id, role_code, role_name, description, create_time from sys_role order by id"));
    }

    @GetMapping("/audit/list")
    public R<List<Map<String, Object>>> auditList() {
        return R.ok(jdbc.queryForList(
            "select id, user_id, username, action, target, detail, ip_address, create_time "
          + "from sys_audit_log order by id desc limit 200"));
    }
}
