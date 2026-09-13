package com.uav.flightplan.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 起降场表初始化 + 航路/起降场演示种子（仅空表时插入）
 */
@Component
@Order(1)
public class InfraInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InfraInitializer.class);

    private final JdbcTemplate jdbc;

    public InfraInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        // 飞行闭环新增列（幂等迁移）：actual_start/actual_end 实际起降时间，cmd_sent_at 起飞指令下发时间
        try {
            jdbc.execute("ALTER TABLE flight_plan ADD COLUMN IF NOT EXISTS actual_start timestamp");
            jdbc.execute("ALTER TABLE flight_plan ADD COLUMN IF NOT EXISTS actual_end timestamp");
            jdbc.execute("ALTER TABLE flight_plan ADD COLUMN IF NOT EXISTS cmd_sent_at timestamp");
        } catch (Exception e) {
            log.warn("flight_plan 新列迁移失败（表可能尚未创建）: {}", e.getMessage());
        }

        jdbc.execute("CREATE TABLE IF NOT EXISTS uav_airport ("
            + "id bigserial PRIMARY KEY, "
            + "airport_name varchar(128) NOT NULL, "
            + "airport_code varchar(64), "
            + "airport_type varchar(32) DEFAULT 'ALL', "
            + "lon double precision, "
            + "lat double precision, "
            + "elevation_m double precision, "
            + "capacity int DEFAULT 0, "
            + "is_active boolean DEFAULT true, "
            + "remark varchar(255), "
            + "create_time timestamp DEFAULT now())");

        Integer routes = jdbc.queryForObject("select count(*) from uav_route", Integer.class);
        if (routes != null && routes == 0) {
            seedRoute("通州-亦庄低空走廊", "RT-TZ-YZ-01",
                "[[116.65,39.78],[116.72,39.79],[116.78,39.80]]", 200.0, "TWO_WAY", "通州大运河巡检段至亦庄物流枢纽");
            seedRoute("亦庄-大兴低空走廊", "RT-YZ-DX-02",
                "[[116.50,39.72],[116.44,39.60],[116.36,39.54]]", 200.0, "ONE_WAY", "亦庄滨河公园至大兴区机场路线");
            seedRoute("城市南部巡检航路", "RT-CS-03",
                "[[116.44,39.72],[116.50,39.78],[116.58,39.82]]", 150.0, "TWO_WAY", "南部城区基础设施巡检");
        }

        // 演示态势：每 5 架 STRESS 压力机取 1 架补登记 + 长期已批准计划 → 转为合规机（其余保持黑飞，形成红蓝对比）
        Integer stressPlans = jdbc.queryForObject("select count(*) from flight_plan where plan_code like 'FP-STRESS-%'", Integer.class);
        if (stressPlans == null || stressPlans == 0) {
            String[] purposes = {"物流配送", "电力巡检", "航空测绘", "空域巡逻"};
            int seeded = 0;
            for (int i = 1; i <= 1000; i += 5) {
                String sn = String.format("STRESS-%04d", i);
                String code = String.format("FP-STRESS-%04d", i);
                String regId = String.format("REG-STRESS-%04d", i);
                String purpose = purposes[(i / 5) % purposes.length];
                String destination = i % 10 == 0 ? "廊坊高新区物流起降点" : "亦庄滨河公园起降场";
                // 补实名登记（无则插入）
                jdbc.update("insert into uav_registration (owner_id, drone_sn, drone_model, drone_type, weight_g, registration_id, register_status) "
                    + "select 1, ?, 'DJI-M30T', 'MULTIROTOR', 1400, ?, 'APPROVED' "
                    + "where not exists (select 1 from uav_registration where drone_sn = ?)", sn, regId, sn);
                // 长期已批准计划（30 天窗口，覆盖黑飞白名单判定）
                jdbc.update("insert into flight_plan (plan_code, plan_status, drone_sn, departure, destination, "
                    + "planned_start, planned_end, alt_ceiling_m, flight_purpose, create_time) "
                    + "values (?, 'APPROVED', ?, '通州大运河巡检起降场', ?, now() - interval '1 hour', now() + interval '30 days', 300, ?, now())",
                    code, sn, destination, purpose);
                // 关闭这些机的存量黑飞告警（已合规，无需等待 30 分钟陈旧过期）
                jdbc.update("update alarm_record set status = 'CLOSED', closed_time = now() "
                    + "where alarm_type = 'NO_FLIGHT_PLAN' and status = 'OPEN' and drone_sn = ?", sn);
                seeded++;
            }
            log.info("合规 STRESS 机初始化：{} 架已补登记 + 长期已批准计划", seeded);
        }

        Integer airports = jdbc.queryForObject("select count(*) from uav_airport", Integer.class);
        if (airports != null && airports == 0) {
            seedAirport("亦庄滨河公园起降场", "AP-YZ-01", "ALL", 116.5021, 39.7183, 32.0, 8, "亦庄新城低空物流枢纽");
            seedAirport("通州大运河巡检起降场", "AP-TZ-01", "ALL", 116.6560, 39.9020, 28.0, 6, "大运河巡检段基地");
            seedAirport("大兴低空示范起降场", "AP-DX-01", "ALL", 116.3380, 39.5260, 30.0, 10, "大兴区低空示范运营基地");
            seedAirport("廊坊高新区物流起降点", "AP-LF-01", "LANDING", 116.6810, 39.5320, 26.0, 4, "物流枢纽降落专用");
        }
        log.info("航路/起降场初始化完成：routes={}, airports={}", routes, airports);
    }

    private void seedRoute(String name, String code, String waypoints, double width, String direction, String desc) {
        jdbc.update("insert into uav_route (route_name, route_code, waypoints, corridor_width_m, direction, is_active, description) "
            + "values (?,?,?,?,?,true,?)", name, code, waypoints, width, direction, desc);
    }

    private void seedAirport(String name, String code, String type, double lon, double lat, double elev, int cap, String remark) {
        jdbc.update("insert into uav_airport (airport_name, airport_code, airport_type, lon, lat, elevation_m, capacity, is_active, remark) "
            + "values (?,?,?,?,?,?,?,true,?)", name, code, type, lon, lat, elev, cap, remark);
    }
}
