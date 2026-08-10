package com.uav.alarm.core;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警规则配置 - 支持动态加载
 */
@Component
public class AlarmRuleConfig {

    // ===== 开关 =====
    private volatile boolean airspaceEnabled = true;
    private volatile boolean weatherEnabled = true;
    private volatile boolean routeEnabled = true;
    private volatile boolean equipmentEnabled = true;
    private volatile boolean terrainEnabled = true;

    // ===== 阈值 =====
    private volatile int batteryWarning = 30;      // 电量警告阈值 %
    private volatile int batteryCritical = 10;     // 电量危急阈值 %
    private volatile int minRssi = -85;            // 最小信号强度 dBm
    private volatile int minGpsSatellites = 8;     // 最小GPS卫星数
    private volatile double maxWindSpeed = 12.0;   // 最大风速 m/s
    private volatile double minVisibility = 2000.0;// 最小能见度 m
    private volatile double maxRouteDeviationMeters = 100.0; // 最大航路偏离 m
    private volatile double minSafeHeightMeters = 30.0;      // 最小安全高度 m

    // ===== 禁飞区 H3 网格缓存 =====
    private final Set<String> restrictedH3Cells = ConcurrentHashMap.newKeySet();

    public boolean isAirspaceEnabled() { return airspaceEnabled; }
    public boolean isWeatherEnabled() { return weatherEnabled; }
    public boolean isRouteEnabled() { return routeEnabled; }
    public boolean isEquipmentEnabled() { return equipmentEnabled; }
    public boolean isTerrainEnabled() { return terrainEnabled; }

    public int getBatteryWarning() { return batteryWarning; }
    public int getBatteryCritical() { return batteryCritical; }
    public int getMinRssi() { return minRssi; }
    public int getMinGpsSatellites() { return minGpsSatellites; }
    public double getMaxWindSpeed() { return maxWindSpeed; }
    public double getMinVisibility() { return minVisibility; }
    public double getMaxRouteDeviationMeters() { return maxRouteDeviationMeters; }
    public double getMinSafeHeightMeters() { return minSafeHeightMeters; }

    /**
     * 判断 H3 网格是否在禁飞区内
     */
    public boolean isH3InRestrictedZone(String h3Index) {
        return restrictedH3Cells.contains(h3Index);
    }

    /**
     * 更新禁飞区 H3 网格集合
     */
    public void updateRestrictedZones(Set<String> cells) {
        restrictedH3Cells.clear();
        restrictedH3Cells.addAll(cells);
    }

    /**
     * 获取当前位置气象快照
     */
    public WeatherSnapshot getWeatherAt(double lat, double lon) {
        // TODO: 调用气象数据源 (Open-Meteo 免费 API)
        return null;
    }

    /**
     * 计算航路偏离距离 (米)
     */
    public double calculateRouteDeviation(String flightPlanId, double lat, double lon) {
        // TODO: 从缓存/DB 获取计划航路，计算点到线段的距离
        return 0;
    }

    /**
     * 获取地形高程 (SRTM)
     */
    public double getTerrainElevation(double lat, double lon) {
        // TODO: 查询本地 SRTM 高程缓存
        return 0;
    }

    public List<String> getRuleDescriptions() {
        return Arrays.asList(
                "空域入侵: " + (airspaceEnabled ? "启用" : "禁用"),
                "气象风险: " + (weatherEnabled ? "启用" : "禁用") + " 风速>" + maxWindSpeed + "m/s 能见度<" + minVisibility + "m",
                "航路偏离: " + (routeEnabled ? "启用" : "禁用") + " 最大偏离>" + maxRouteDeviationMeters + "m",
                "设备故障: " + (equipmentEnabled ? "启用" : "禁用") + " 电量告警<" + batteryWarning + "% 危急<" + batteryCritical + "%",
                "地形碰撞: " + (terrainEnabled ? "启用" : "禁用") + " 安全高度<" + minSafeHeightMeters + "m"
        );
    }
}
