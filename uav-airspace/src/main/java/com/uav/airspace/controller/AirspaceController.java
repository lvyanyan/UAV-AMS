package com.uav.airspace.controller;

import com.uav.airspace.entity.Airspace;
import com.uav.airspace.service.AirspaceService;
import com.uav.common.base.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/airspace")
public class AirspaceController {

    private final AirspaceService service;

    public AirspaceController(AirspaceService s) { this.service = s; }

    @GetMapping
    public R<List<Airspace>> list() {
        return R.ok(service.lambdaQuery().orderByAsc(Airspace::getCreateTime).list());
    }

    // 前端 airspaceApi.list() 调用的是 /list；显式注册，避免被 /{id} 当 Long 解析 400
    @GetMapping("/list")
    public R<List<Airspace>> listAlias() {
        return list();
    }

    @GetMapping("/{id}")
    public R<Airspace> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @PostMapping
    public R<Airspace> create(@RequestBody Airspace airspace) {
        airspace.setIsActive(true);
        service.save(airspace);
        return R.ok(airspace);
    }

    @PutMapping("/{id}")
    public R<Airspace> update(@PathVariable Long id, @RequestBody Airspace airspace) {
        airspace.setId(id);
        service.updateById(airspace);
        return R.ok(airspace);
    }

    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        service.removeById(id);
        return R.ok("ok");
    }

    /** 按经纬度查询覆盖该点的空域 */
    @GetMapping("/query")
    public R<List<Airspace>> queryByPoint(@RequestParam double lng, @RequestParam double lat) {
        return R.ok(service.findByPoint(lng, lat));
    }

    /** 获取所有活跃空域的 GeoJSON FeatureCollection */
    @GetMapping("/geojson")
    public R<Map<String, Object>> geoJson() {
        List<Airspace> list = service.lambdaQuery().eq(Airspace::getIsActive, true).list();
        var features = list.stream().map(a -> {
            try {
                var geometry = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(a.getGeoJson());
                return Map.of(
                    "type", "Feature",
                    "geometry", (Object) geometry,
                    "properties", Map.of(
                        "id", a.getId(),
                        "name", a.getAirspaceName(),
                        "code", a.getAirspaceCode(),
                        "type", a.getAirspaceType(),
                        "altFloor", a.getAltFloorM(),
                        "altCeiling", a.getAltCeilingM()
                    )
                );
            } catch (Exception e) {
                return null;
            }
        }).filter(f -> f != null).toList();

        return R.ok(Map.of("type", "FeatureCollection", "features", features));
    }
}
