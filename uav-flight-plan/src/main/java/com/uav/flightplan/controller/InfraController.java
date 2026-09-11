package com.uav.flightplan.controller;

import com.uav.common.base.R;
import com.uav.flightplan.entity.UavAirport;
import com.uav.flightplan.entity.UavRoute;
import com.uav.flightplan.service.ComplianceService;
import com.uav.flightplan.service.UavAirportService;
import com.uav.flightplan.service.UavRouteService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 航路管理 + 起降场管理：飞行流程的地基数据
 * 完整链路 = 空域划设 → 航路 → 起降场 → 登记无人机 → 飞行计划（选航路/起降场）→ 合规审批
 */
@RestController
public class InfraController {

    private final UavRouteService routeService;
    private final UavAirportService airportService;
    private final ComplianceService complianceService;
    private final ObjectMapper om = new ObjectMapper();

    public InfraController(UavRouteService routeService,
                           UavAirportService airportService,
                           ComplianceService complianceService) {
        this.routeService = routeService;
        this.airportService = airportService;
        this.complianceService = complianceService;
    }

    // ===== 航路 =====
    @GetMapping("/api/route/list")
    public R<List<UavRoute>> routeList(@RequestParam(required = false) Boolean active) {
        if (Boolean.TRUE.equals(active)) {
            return R.ok(routeService.lambdaQuery().eq(UavRoute::getIsActive, true).list());
        }
        return R.ok(routeService.list());
    }

    @PostMapping("/api/route")
    public R<UavRoute> createRoute(@RequestBody UavRoute route) {
        route.setIsActive(route.getIsActive() == null || route.getIsActive());
        routeService.save(route);
        return R.ok(route);
    }

    @PutMapping("/api/route/{id}")
    public R<UavRoute> updateRoute(@PathVariable Long id, @RequestBody UavRoute route) {
        route.setId(id);
        routeService.updateById(route);
        return R.ok(routeService.getById(id));
    }

    @DeleteMapping("/api/route/{id}")
    public R<String> deleteRoute(@PathVariable Long id) {
        routeService.removeById(id);
        return R.ok("ok");
    }

    /** 航路合规预检：航点是否穿越禁飞区 */
    @PostMapping("/api/route/check")
    public R<List<String>> checkRoute(@RequestBody Map<String, Object> body) {
        List<double[]> points = new ArrayList<>();
        try {
            JsonNode wp = om.readTree(om.writeValueAsString(body.get("waypoints")));
            if (wp.isArray()) {
                for (JsonNode p : wp) points.add(new double[]{p.get(0).asDouble(), p.get(1).asDouble()});
            }
        } catch (Exception ignore) { }
        return R.ok(complianceService.checkPoints(points));
    }

    // ===== 起降场 =====
    @GetMapping("/api/airport/list")
    public R<List<UavAirport>> airportList(@RequestParam(required = false) Boolean active) {
        if (Boolean.TRUE.equals(active)) {
            return R.ok(airportService.lambdaQuery().eq(UavAirport::getIsActive, true).list());
        }
        return R.ok(airportService.list());
    }

    @PostMapping("/api/airport")
    public R<UavAirport> createAirport(@RequestBody UavAirport airport) {
        airport.setIsActive(airport.getIsActive() == null || airport.getIsActive());
        airportService.save(airport);
        return R.ok(airport);
    }

    @PutMapping("/api/airport/{id}")
    public R<UavAirport> updateAirport(@PathVariable Long id, @RequestBody UavAirport airport) {
        airport.setId(id);
        airportService.updateById(airport);
        return R.ok(airportService.getById(id));
    }

    @DeleteMapping("/api/airport/{id}")
    public R<String> deleteAirport(@PathVariable Long id) {
        airportService.removeById(id);
        return R.ok("ok");
    }

    /** 起降点合规预检 */
    @PostMapping("/api/airport/check")
    public R<List<String>> checkAirport(@RequestBody Map<String, Object> body) {
        List<double[]> points = new ArrayList<>();
        Object lon = body.get("lon"), lat = body.get("lat");
        if (lon instanceof Number && lat instanceof Number) {
            points.add(new double[]{((Number) lon).doubleValue(), ((Number) lat).doubleValue()});
        }
        return R.ok(complianceService.checkPoints(points));
    }
}
