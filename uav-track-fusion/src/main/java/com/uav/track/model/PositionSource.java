package com.uav.track.model;

/**
 * 位置信息来源枚举 — 支持无人机自报 + 监视设备上报
 */
public enum PositionSource {

    /** 无人机机载 GPS（MQTT 遥测），精度 2-5m，更新率 1-5Hz */
    DRONE_TELEMETRY("无人机遥测", 3.0, 5),

    /** ADS-B 广播（大型无人机标配），精度 10-30m，更新率 1-2Hz */
    ADS_B("ADS-B广播", 20.0, 2),

    /** 地面监视雷达，精度 50-100m，更新率 1Hz */
    GROUND_RADAR("地面雷达", 75.0, 1),

    /** RTK 差分 GPS，精度 < 0.1m，更新率 5-20Hz */
    RTK_BASE("RTK基站", 0.05, 10),

    /** Remote ID 标准广播，精度 5-15m，更新率 1Hz */
    REMOTE_ID("RemoteID", 10.0, 1),

    /** 光电跟踪设备，精度 5-20m（取决于距离），更新率 10-30Hz */
    OPTICAL_TRACKING("光电跟踪", 15.0, 20),

    /** 移动监测站（车载/便携），精度 10-30m */
    MOBILE_MONITOR("移动监测", 20.0, 2),

    /** 其他/未知来源 */
    UNKNOWN("未知来源", 50.0, 1);

    private final String label;
    /** GPS 位置精度（米，1σ） */
    private final double accuracyMeters;
    /** 典型更新率（Hz） */
    private final int updateRateHz;

    PositionSource(String label, double accuracyMeters, int updateRateHz) {
        this.label = label;
        this.accuracyMeters = accuracyMeters;
        this.updateRateHz = updateRateHz;
    }

    public String getLabel() { return label; }
    public double getAccuracyMeters() { return accuracyMeters; }
    public int getUpdateRateHz() { return updateRateHz; }
}
