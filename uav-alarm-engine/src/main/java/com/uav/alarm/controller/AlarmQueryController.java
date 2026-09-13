package com.uav.alarm.controller;

import com.uav.common.base.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 告警查询接口 —— 读取 alarm_record（由 TelemetryConsumer 落库）
 * 前端契约见 uav-frontend/src/api/alarm.ts
 *
 * 告警为开关语义：status = OPEN（开启） / CLOSED（已关闭）。
 * list 只返回开启中的告警；处理动作 = 关闭，关闭后同机同类型命中可重新触发。
 * 违规台账（只读）由开启中的危急/严重告警派生，处置动作即关闭告警。
 */
@RestController
@RequestMapping("/api/alarm")
public class AlarmQueryController {

    private final JdbcTemplate jdbc;
    private final com.uav.alarm.core.AlarmOpenStateStore openStore;
    private final com.uav.alarm.core.AlarmSuppressStore suppressStore;
    private final com.uav.alarm.config.QualificationExpireRunner qualificationExpireRunner;

    public AlarmQueryController(JdbcTemplate jdbc,
                                com.uav.alarm.core.AlarmOpenStateStore openStore,
                                com.uav.alarm.core.AlarmSuppressStore suppressStore,
                                com.uav.alarm.config.QualificationExpireRunner qualificationExpireRunner) {
        this.jdbc = jdbc;
        this.openStore = openStore;
        this.suppressStore = suppressStore;
        this.qualificationExpireRunner = qualificationExpireRunner;
    }

    private static final String COLS = "id, drone_sn, alarm_type, alarm_level, alarm_content, lat, lng, alt, handled, status, create_time, closed_time";

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
            m.put("status", rs.getString("status"));
            m.put("createTime", rs.getTimestamp("create_time"));
            m.put("closedTime", rs.getTimestamp("closed_time"));
            return m;
        }, args);
    }

    /** 告警中心：只返回开启中的告警（已关闭的不占列表，关闭后同机同类型可重新触发） */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(query("select " + COLS + " from alarm_record where status = 'OPEN' order by id desc limit 200"));
    }

    @GetMapping("/drone/{sn}")
    public R<List<Map<String, Object>>> byDrone(@PathVariable String sn) {
        return R.ok(query("select " + COLS + " from alarm_record where drone_sn = ? order by id desc limit 100", sn));
    }

    /** 处理 = 关闭告警；关闭后同机同类型命中会重新开启 */
    @PutMapping("/{id}/handle")
    public R<Map<String, Object>> handle(@PathVariable long id) {
        List<Map<String, Object>> rows = query("select " + COLS + " from alarm_record where id = ?", id);
        if (rows.isEmpty()) return R.fail("告警不存在");
        Map<String, Object> row = rows.get(0);
        jdbc.update("update alarm_record set handled = true, status = 'CLOSED', closed_time = now() "
            + "where drone_sn = ? and alarm_type = ? and status = 'OPEN'",
            row.get("droneSn"), row.get("alarmType"));
        openStore.markClosed(String.valueOf(row.get("droneSn")), String.valueOf(row.get("alarmType")));
        row.put("handled", true);
        row.put("status", "CLOSED");
        return R.ok(row);
    }

    /** 活跃告警全集（供监控大屏轮询着色；上限 2000） */
    @GetMapping("/active")
    public R<List<Map<String, Object>>> active() {
        return R.ok(query("select " + COLS + " from alarm_record where status = 'OPEN' order by id desc limit 2000"));
    }

    // ===== 告警抑制规则（服务端记录用户选择，生成侧过滤）=====
    @GetMapping("/suppress")
    public R<List<Map<String, Object>>> suppressList(@RequestParam String userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("select id, user_id, alarm_type, alarm_level, drone_sn, create_time from alarm_suppress "
                + "where user_id = ? order by id", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("userId", rs.getString("user_id"));
            m.put("alarmType", rs.getString("alarm_type"));
            m.put("alarmLevel", rs.getString("alarm_level"));
            m.put("droneSn", rs.getString("drone_sn"));
            m.put("createTime", rs.getTimestamp("create_time"));
            out.add(m);
        }, userId);
        return R.ok(out);
    }

    /** 新增抑制规则：alarm_type / alarm_level / drone_sn 至少一个 */
    @PostMapping("/suppress")
    public R<String> suppressAdd(@RequestBody Map<String, Object> body) {
        String userId = str(body.get("userId"));
        String type = str(body.get("alarmType"));
        String level = str(body.get("alarmLevel"));
        String sn = str(body.get("droneSn"));
        if (userId == null || (type == null && level == null && sn == null)) {
            return R.fail("userId 与至少一个抑制条件必填");
        }
        jdbc.update("insert into alarm_suppress (user_id, alarm_type, alarm_level, drone_sn) values (?,?,?,?)",
                userId, type, level, sn);
        suppressStore.reload();
        return R.ok("ok");
    }

    @DeleteMapping("/suppress/{id}")
    public R<String> suppressDelete(@PathVariable long id, @RequestParam String userId) {
        jdbc.update("delete from alarm_suppress where id = ? and user_id = ?", id, userId);
        suppressStore.reload();
        return R.ok("ok");
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    // ===== 违规台账（自 uav-system 迁入：违规属告警/监管域）=====
    /** 只读：由开启中的危急/严重告警派生，OPEN→待处理、CLOSED→已结案 */
    @GetMapping("/violation/list")
    public R<List<Map<String, Object>>> violationList() {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("select id, drone_sn, alarm_type, alarm_level, alarm_content, handled, create_time "
                + "from alarm_record where status = 'OPEN' and alarm_level in ('CRITICAL','SERIOUS') "
                + "order by id desc limit 200", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("droneSn", rs.getString("drone_sn"));
            m.put("violationType", rs.getString("alarm_type"));
            m.put("violationLevel", rs.getString("alarm_level"));
            m.put("description", rs.getString("alarm_content"));
            m.put("status", rs.getBoolean("handled") ? "CLOSED" : "PENDING");
            m.put("createTime", rs.getTimestamp("create_time"));
            out.add(m);
        });
        return R.ok(out);
    }

    /** 统计：total=累计全量；byLevel/byType=开启中的活跃分布 */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Integer total = jdbc.queryForObject("select count(*) from alarm_record", Integer.class);
        Map<String, Object> out = new HashMap<>();
        out.put("total", total == null ? 0 : total);
        Map<String, Integer> byType = new HashMap<>(), byLevel = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("select alarm_type, count(*) c from alarm_record where status = 'OPEN' group by alarm_type")) {
            byType.put((String) row.get("alarm_type"), ((Number) row.get("c")).intValue());
        }
        for (Map<String, Object> row : jdbc.queryForList("select alarm_level, count(*) c from alarm_record where status = 'OPEN' group by alarm_level")) {
            byLevel.put((String) row.get("alarm_level"), ((Number) row.get("c")).intValue());
        }
        out.put("byType", byType);
        out.put("byLevel", byLevel);
        return R.ok(out);
    }

    // ===== 资质到期检查（执照/体检）：手动触发一轮（定时每 10 分钟自动执行）=====
    @PostMapping("/qualification/check")
    public R<Map<String, Object>> qualificationCheck() {
        return R.ok(qualificationExpireRunner.check());
    }
}
