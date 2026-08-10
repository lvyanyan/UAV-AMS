package com.uav.airspace.init;

import com.uav.airspace.entity.Airspace;
import com.uav.airspace.service.AirspaceService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AirspaceDataInitializer implements CommandLineRunner {

    private final AirspaceService service;

    public AirspaceDataInitializer(AirspaceService s) { this.service = s; }

    @Override
    public void run(String... args) {
        long count = service.count();
        if (count > 0) {
            System.out.println("[AirspaceInit] 空域表已有 " + count + " 条记录, 跳过初始化");
            return;
        }

        System.out.println("[AirspaceInit] ===== 初始化示例空域数据 =====");

        insertAirspace("首都核心区禁飞区", "CN-CORE-NOFLY", "RESTRICTED",
                0, 500,
                "{\"type\":\"Polygon\",\"coordinates\":[[[116.375,39.895],[116.375,39.925],[116.405,39.925],[116.405,39.895],[116.375,39.895]]]}");

        insertAirspace("大兴机场净空保护区", "CN-DAXING-APPROACH", "RESTRICTED",
                0, 600,
                "{\"type\":\"Polygon\",\"coordinates\":[[[116.25,39.45],[116.25,39.55],[116.50,39.55],[116.50,39.45],[116.25,39.45]]]}");

        insertAirspace("延庆无人机训练空域", "CN-YANQING-TRAINING", "TRAINING",
                100, 300,
                "{\"type\":\"Polygon\",\"coordinates\":[[[115.90,40.35],[115.90,40.50],[116.10,40.50],[116.10,40.35],[115.90,40.35]]]}");

        insertAirspace("通州临时限制区", "CN-TONGZHOU-TEMP", "RESTRICTED",
                0, 200,
                "{\"type\":\"Polygon\",\"coordinates\":[[[116.60,39.82],[116.60,39.92],[116.75,39.92],[116.75,39.82],[116.60,39.82]]]}");

        insertAirspace("海淀公园适飞空域", "CN-HAIDIAN-PARK", "PERMITTED",
                0, 120,
                "{\"type\":\"Polygon\",\"coordinates\":[[[116.28,39.96],[116.28,40.00],[116.34,40.00],[116.34,39.96],[116.28,39.96]]]}");

        insertAirspace("昌平-延庆通航走廊", "CN-CP-YQ-CORRIDOR", "AIRWAY",
                150, 300,
                "{\"type\":\"LineString\",\"coordinates\":[[116.20,40.10],[116.15,40.20],[116.10,40.30],[116.00,40.40]]}");

        insertAirspace("八达岭景区临时禁飞", "CN-BADALING-NOFLY", "RESTRICTED",
                0, 500,
                "{\"type\":\"Point\",\"coordinates\":[115.97,40.36]}");

        System.out.println("[AirspaceInit] ===== 示例空域数据初始化完成, 共 7 条 =====");
    }

    private void insertAirspace(String name, String code, String type,
                                int altFloor, int altCeiling, String geoJson) {
        Airspace a = new Airspace();
        a.setAirspaceName(name);
        a.setAirspaceCode(code);
        a.setAirspaceType(type);
        a.setAltFloorM((double) altFloor);
        a.setAltCeilingM((double) altCeiling);
        a.setGeoJson(geoJson);
        a.setIsActive(true);
        service.save(a);
        System.out.println("[AirspaceInit] 添加空域: " + name + " (" + code + ")");
    }
}
