package com.uav.flightplan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 飞行计划合规校验：起降点 / 航路点是否落入禁飞区（NO_FLY）。
 * 禁飞区判定用射线法 point-in-polygon（GeoJSON Polygon 第一个环）。
 * 命中即返回违规描述，提交计划时据此硬拒绝。
 */
@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ComplianceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 校验结果：违规列表为空即通过 */
    public List<String> checkPoints(List<double[]> lonLatPoints) {
        List<String> violations = new ArrayList<>();
        if (lonLatPoints == null || lonLatPoints.isEmpty()) return violations;

        List<Object[]> zones = new ArrayList<>();
        try {
            zones = jdbc.query(
                "select airspace_name, geo_json from uav_airspace "
              + "where is_active = true and airspace_type in ('NO_FLY') and geo_json is not null",
                rs -> {
                    List<Object[]> list = new ArrayList<>();
                    while (rs.next()) list.add(new Object[]{rs.getString(1), rs.getString(2)});
                    return list;
                });
        } catch (Exception e) {
            log.warn("读取禁飞区失败，跳过合规校验: {}", e.getMessage());
            return violations;
        }

        for (double[] p : lonLatPoints) {
            for (Object[] zone : zones) {
                String name = (String) zone[0];
                if (inPolygon(p[0], p[1], (String) zone[1])) {
                    violations.add("坐标(" + p[0] + "," + p[1] + ") 位于禁飞区「" + name + "」");
                }
            }
        }
        return violations;
    }

    /** 射线法：点是否在 GeoJSON Polygon（取 coordinates 第一个环）内 */
    private boolean inPolygon(double lon, double lat, String geoJson) {
        try {
            JsonNode ring = objectMapper.readTree(geoJson).path("coordinates").path(0);
            int n = ring.size();
            if (n < 3) return false;
            boolean inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = ring.get(i).get(0).asDouble(), yi = ring.get(i).get(1).asDouble();
                double xj = ring.get(j).get(0).asDouble(), yj = ring.get(j).get(1).asDouble();
                boolean intersect = ((yi > lat) != (yj > lat))
                        && (lon < (xj - xi) * (lat - yi) / (yj - yi + 1e-12) + xi);
                if (intersect) inside = !inside;
            }
            return inside;
        } catch (Exception e) {
            log.warn("解析空域 GeoJSON 失败: {}", e.getMessage());
            return false;
        }
    }
}
