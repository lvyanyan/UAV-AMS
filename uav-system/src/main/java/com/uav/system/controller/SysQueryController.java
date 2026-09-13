package com.uav.system.controller;

import com.uav.common.base.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统管理只读查询接口（审计日志）
 * 用户/角色的分页查询与写操作已升级至 SysUserController / SysRoleController（RBAC），
 * 本类保留 /api/audit/list 供审计日志页使用
 */
@RestController
@RequestMapping("/api")
public class SysQueryController {

    private final JdbcTemplate jdbc;

    public SysQueryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final java.time.format.DateTimeFormatter TS =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 统一时间出口：Timestamp → "yyyy-MM-dd HH:mm:ss" */
    private static String fmt(Timestamp t) { return t == null ? null : TS.format(t.toLocalDateTime()); }

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
            m.put("createTime", fmt(t));
            out.add(m);
        });
        return R.ok(out);
    }


}
