package com.uav.alarm.controller;

import com.uav.common.base.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 告警查询接口 —— 读取 alarm_record（由 TelemetryConsumer 落库）
 * 前端契约见 uav-frontend/src/api/alarm.ts
 */
@RestController
@RequestMapping("/api/alarm")
public class AlarmQueryController {

    private final JdbcTemplate jdbc;

    public AlarmQueryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String COLS = "id, drone_sn, alarm_type, alarm_level, alarm_content, lat, lng, alt, handled, create_time";

    private List<Map<String, Object>> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("droneSn", rs.getString("drone_sn"));
            m.put("alarmType", rs.getString("alarm_type"));
            m.put("alarmLevel", rs.getString("alarm_level"));
            m.put("message", rs.getString("alarm_content"));
            m.put("latitude", rs.getObject("lat"));
            m.put("longitude", rs.getObject("lng"));
            m.put("altitude", rs.getObject("alt"));
            m.put("handled", rs.getBoolean("handled"));
            m.put("createTime", rs.getTimestamp("create_time"));
            return m;
        }, args);
    }

    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(query("select " + COLS + " from alarm_record order by id desc limit 200"));
    }

    @GetMapping("/drone/{sn}")
    public R<List<Map<String, Object>>> byDrone(@PathVariable String sn) {
        return R.ok(query("select " + COLS + " from alarm_record where drone_sn = ? order by id desc limit 100", sn));
    }

    @PutMapping("/{id}/handle")
    public R<Map<String, Object>> handle(@PathVariable long id) {
        jdbc.update("update alarm_record set handled = true where id = ?", id);
        List<Map<String, Object>> rows = query("select " + COLS + " from alarm_record where id = ?", id);
        return R.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Integer total = jdbc.queryForObject("select count(*) from alarm_record", Integer.class);
        Map<String, Object> out = new HashMap<>();
        out.put("total", total == null ? 0 : total);
        Map<String, Integer> byType = new HashMap<>(), byLevel = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("select alarm_type, count(*) c from alarm_record group by alarm_type")) {
            byType.put((String) row.get("alarm_type"), ((Number) row.get("c")).intValue());
        }
        for (Map<String, Object> row : jdbc.queryForList("select alarm_level, count(*) c from alarm_record group by alarm_level")) {
            byLevel.put((String) row.get("alarm_level"), ((Number) row.get("c")).intValue());
        }
        out.put("byType", byType);
        out.put("byLevel", byLevel);
        return R.ok(out);
    }
}
