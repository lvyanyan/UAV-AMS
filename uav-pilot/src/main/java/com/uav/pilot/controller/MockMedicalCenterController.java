package com.uav.pilot.controller;

import com.uav.common.base.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置 mock 体检中心（演示链路）：按库内飞手生成确定性的体检结果目录，
 * 供 MedicalCenterSyncService 定时拉取，跑通「体检中心 → 落库 → 到期告警/放行校验」全链路。
 * 生产环境将 uav.medical-center.base-url 指向真实体检中心后，本端点即闲置。
 *
 * 数据约定（确定性，重复拉取靠 pilot_id+exam_date 去重）：
 *   - id 最小的飞手：体检日期 2025-09-01，有效期 10 天前 → 触发体检到期（已过期）告警
 *   - 其余飞手：体检日期 2026-01-01 起按 id 错开，有效期 +2 年 → 有效
 */
@RestController
@RequestMapping("/api/pilot/mock-center")
public class MockMedicalCenterController {

    private final JdbcTemplate jdbc;

    public MockMedicalCenterController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/exams")
    public R<List<Map<String, Object>>> exams() {
        List<Map<String, Object>> items = new ArrayList<>();
        jdbc.query("select id, pilot_name, id_number, license_no from uav_pilot order by id", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            long id = rs.getLong("id");
            boolean expiredOne = id == firstPilotId();
            LocalDate examDate = expiredOne ? LocalDate.of(2025, 9, 1) : LocalDate.of(2026, 1, 1).plusDays(id % 200);
            String expireDate = expiredOne
                ? LocalDate.now().minusDays(10).toString()
                : examDate.plusYears(2).toString();
            m.put("idNumber", rs.getString("id_number"));
            m.put("pilotName", rs.getString("pilot_name"));
            m.put("examDate", examDate.toString());
            m.put("examOrg", "民航医学中心（模拟）");
            m.put("examResult", "PASS");
            m.put("expireDate", expireDate);
            m.put("reportUrl", "/mock/reports/medical-" + id + ".pdf");
            items.add(m);
        });
        return R.ok(items);
    }

    private long firstPilotId() {
        Long min = jdbc.query("select min(id) from uav_pilot", rs -> rs.next() ? rs.getLong(1) : null);
        return min == null ? -1 : min;
    }
}
