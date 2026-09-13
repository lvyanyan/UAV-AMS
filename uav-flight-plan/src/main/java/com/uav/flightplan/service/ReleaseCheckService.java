package com.uav.flightplan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.flightplan.dto.ReleaseCheckResult;
import com.uav.flightplan.entity.FlightPlan;
import com.uav.flightplan.entity.UavRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 放行前检查清单（六项，逐项返回 pass/reason，全部通过方可 RELEASED）：
 *   1) 计划状态 = APPROVED
 *   2) 无人机已实名登记且 register_status = APPROVED（HTTP → uav-registry）
 *   3) 飞手 status = ACTIVE 且执照未过期（HTTP → uav-pilot）
 *   4) 飞手体检有效：最新体检过期/不合格即拒绝，无体检记录不阻断（HTTP → uav-pilot）
 *   5) 当前时间在计划窗口内（plannedStart-30min ≤ now ≤ plannedEnd）
 *   6) 禁飞区（NO_FLY）合规复检（复用 ComplianceService）
 * 外部服务不可达按不通过处理（fail-closed）。
 */
@Service
public class ReleaseCheckService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseCheckService.class);

    /** 计划开始前允许提前放行的宽限期 */
    private static final int RELEASE_LEAD_MINUTES = 30;

    private final JdbcTemplate jdbc;
    private final ComplianceService complianceService;
    private final RestTemplate restTemplate;
    private final String registryBaseUrl;
    private final String pilotBaseUrl;
    private final ObjectMapper om = new ObjectMapper();

    public ReleaseCheckService(JdbcTemplate jdbc,
                               ComplianceService complianceService,
                               RestTemplate restTemplate,
                               @Qualifier("registryBaseUrl") String registryBaseUrl,
                               @Qualifier("pilotBaseUrl") String pilotBaseUrl) {
        this.jdbc = jdbc;
        this.complianceService = complianceService;
        this.restTemplate = restTemplate;
        this.registryBaseUrl = registryBaseUrl;
        this.pilotBaseUrl = pilotBaseUrl;
    }

    /** 执行全量放行检查（不落地状态，仅出清单） */
    public ReleaseCheckResult check(FlightPlan plan) {
        ReleaseCheckResult result = new ReleaseCheckResult();
        result.setPlanId(plan.getId());
        result.setPlanCode(plan.getPlanCode());

        List<ReleaseCheckResult.CheckItem> checks = new ArrayList<>();
        checks.add(checkStatus(plan));
        checks.add(checkDroneRegistration(plan));
        checks.add(checkPilot(plan));
        checks.add(checkMedical(plan));
        checks.add(checkTimeWindow(plan));
        checks.add(checkNoFly(plan));

        boolean passed = checks.stream().allMatch(ReleaseCheckResult.CheckItem::isPass);
        result.setChecks(checks);
        result.setPassed(passed);
        return result;
    }

    /** 1) 计划状态 = APPROVED */
    private ReleaseCheckResult.CheckItem checkStatus(FlightPlan plan) {
        boolean ok = "APPROVED".equals(plan.getPlanStatus());
        return new ReleaseCheckResult.CheckItem("计划已审批通过", ok,
                ok ? "当前状态 APPROVED" : "当前状态为 " + plan.getPlanStatus() + "，仅 APPROVED 计划可放行");
    }

    /** 2) 无人机实名登记且 APPROVED（uav-registry） */
    private ReleaseCheckResult.CheckItem checkDroneRegistration(FlightPlan plan) {
        String sn = plan.getDroneSn();
        if (sn == null || sn.isBlank()) {
            return new ReleaseCheckResult.CheckItem("无人机已实名登记", false, "计划未绑定无人机 SN");
        }
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(
                    registryBaseUrl + "/api/registry/drones/by-sn/" + sn, String.class);
            JsonNode data = om.readTree(resp.getBody()).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return new ReleaseCheckResult.CheckItem("无人机已实名登记", false, "SN " + sn + " 未找到实名登记记录");
            }
            String status = data.path("registerStatus").asText("");
            if ("APPROVED".equals(status)) {
                return new ReleaseCheckResult.CheckItem("无人机已实名登记", true,
                        "SN " + sn + " 登记号 " + data.path("registrationId").asText("-") + " 审核通过");
            }
            return new ReleaseCheckResult.CheckItem("无人机已实名登记", false,
                    "SN " + sn + " 登记状态为 " + status + "，未通过审核");
        } catch (Exception e) {
            log.warn("查询实名登记失败（SN={}）: {}", sn, e.getMessage());
            return new ReleaseCheckResult.CheckItem("无人机已实名登记", false, "实名登记服务不可用，无法核验");
        }
    }

    /** 3) 飞手 ACTIVE 且执照未过期（uav-pilot） */
    private ReleaseCheckResult.CheckItem checkPilot(FlightPlan plan) {
        Long pilotId = plan.getPilotId();
        if (pilotId == null) {
            return new ReleaseCheckResult.CheckItem("飞手资质有效", false, "计划未绑定飞手");
        }
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(
                    pilotBaseUrl + "/api/pilot/" + pilotId, String.class);
            JsonNode data = om.readTree(resp.getBody()).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return new ReleaseCheckResult.CheckItem("飞手资质有效", false, "飞手 ID " + pilotId + " 不存在");
            }
            String name = data.path("pilotName").asText("");
            String status = data.path("status").asText("");
            if (!"ACTIVE".equals(status)) {
                return new ReleaseCheckResult.CheckItem("飞手资质有效", false,
                        "飞手 " + name + " 当前状态 " + status + "，处于停飞/不可用状态");
            }
            String expire = data.path("licenseExpire").asText("");
            LocalDateTime expireAt = null;
            try {
                expireAt = LocalDateTime.parse(expire.length() == 10 ? expire + "T00:00:00" : expire);
            } catch (Exception ignore) { }
            if (expireAt != null && expireAt.isBefore(LocalDateTime.now())) {
                return new ReleaseCheckResult.CheckItem("飞手资质有效", false,
                        "飞手 " + name + " 执照已于 " + expire + " 过期");
            }
            return new ReleaseCheckResult.CheckItem("飞手资质有效", true,
                    "飞手 " + name + " 状态正常，执照有效期至 " + (expire.isEmpty() ? "-" : expire));
        } catch (Exception e) {
            log.warn("查询飞手失败（ID={}）: {}", pilotId, e.getMessage());
            return new ReleaseCheckResult.CheckItem("飞手资质有效", false, "驾驶员服务不可用，无法核验");
        }
    }

    /** 4) 飞手体检有效（uav-pilot）：最新体检过期或不合格 → 不通过；无体检记录不阻断（体检中心对接前存量缺失） */
    private ReleaseCheckResult.CheckItem checkMedical(FlightPlan plan) {
        Long pilotId = plan.getPilotId();
        if (pilotId == null) {
            return new ReleaseCheckResult.CheckItem("飞手体检有效", false, "计划未绑定飞手");
        }
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(
                    pilotBaseUrl + "/api/pilot/" + pilotId + "/medical/valid", String.class);
            JsonNode data = om.readTree(resp.getBody()).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return new ReleaseCheckResult.CheckItem("飞手体检有效", false, "体检核验无返回数据");
            }
            boolean valid = data.path("valid").asBoolean(false);
            String state = data.path("state").asText("");
            String reason = data.path("reason").asText("");
            return new ReleaseCheckResult.CheckItem("飞手体检有效", valid, reason.isEmpty() ? "体检状态 " + state : reason);
        } catch (Exception e) {
            log.warn("核验体检失败（飞手 ID={}）: {}", pilotId, e.getMessage());
            return new ReleaseCheckResult.CheckItem("飞手体检有效", false, "驾驶员服务不可用，无法核验");
        }
    }

    /** 5) 时间窗：plannedStart-30min ≤ now ≤ plannedEnd */
    private ReleaseCheckResult.CheckItem checkTimeWindow(FlightPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        if (plan.getPlannedStart() == null || plan.getPlannedEnd() == null) {
            return new ReleaseCheckResult.CheckItem("在计划时间窗内", false, "计划缺少起止时间");
        }
        LocalDateTime earliest = plan.getPlannedStart().minusMinutes(RELEASE_LEAD_MINUTES);
        boolean ok = !now.isBefore(earliest) && !now.isAfter(plan.getPlannedEnd());
        if (ok) {
            return new ReleaseCheckResult.CheckItem("在计划时间窗内", true, "当前时间在计划窗口内");
        }
        if (now.isAfter(plan.getPlannedEnd())) {
            return new ReleaseCheckResult.CheckItem("在计划时间窗内", false,
                    "计划已于 " + plan.getPlannedEnd() + " 结束，超过放行时限");
        }
        return new ReleaseCheckResult.CheckItem("在计划时间窗内", false,
                "计划 " + plan.getPlannedStart() + " 起飞，最早提前 " + RELEASE_LEAD_MINUTES + " 分钟放行，当前时间未到");
    }

    /** 6) 禁飞区合规复检：起降点坐标 + 航路点均不得落入 NO_FLY */
    private ReleaseCheckResult.CheckItem checkNoFly(FlightPlan plan) {
        List<double[]> points = new ArrayList<>();
        try {
            for (String name : new String[]{plan.getDeparture(), plan.getDestination()}) {
                if (name == null || name.isBlank()) continue;
                points.addAll(jdbc.query(
                    "select lon, lat from uav_airport where is_active = true and airport_name = ?",
                    rs -> {
                        List<double[]> l = new ArrayList<>();
                        while (rs.next()) l.add(new double[]{rs.getDouble(1), rs.getDouble(2)});
                        return l;
                    }, name));
            }
            if (plan.getRouteId() != null) {
                points.addAll(routeWaypoints(plan.getRouteId()));
            }
        } catch (Exception e) {
            log.warn("放行合规复检取点失败（放行）: {}", e.getMessage());
        }
        List<String> violations = complianceService.checkPoints(points);
        if (violations.isEmpty()) {
            return new ReleaseCheckResult.CheckItem("禁飞区合规复检", true, "起降点与航路点均未落入禁飞区");
        }
        return new ReleaseCheckResult.CheckItem("禁飞区合规复检", false, String.join("；", violations));
    }

    private List<double[]> routeWaypoints(Long routeId) {
        List<double[]> points = new ArrayList<>();
        try {
            UavRoute route = jdbc.queryForObject(
                    "select id, route_name, route_code, waypoints from uav_route where id = ?",
                    (rs, i) -> {
                        UavRoute r = new UavRoute();
                        r.setId(rs.getLong(1));
                        r.setRouteName(rs.getString(2));
                        r.setRouteCode(rs.getString(3));
                        r.setWaypoints(rs.getString(4));
                        return r;
                    }, routeId);
            if (route != null && route.getWaypoints() != null) {
                JsonNode wp = om.readTree(route.getWaypoints());
                if (wp.isArray()) {
                    for (JsonNode p : wp) points.add(new double[]{p.get(0).asDouble(), p.get(1).asDouble()});
                }
            }
        } catch (Exception e) {
            log.warn("读取航路点失败: {}", e.getMessage());
        }
        return points;
    }
}
