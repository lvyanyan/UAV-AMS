package com.uav.airspace.grid;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import com.uav.airspace.config.AirspaceControllerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * H3 六边形网格服务（uber/h3 官方库）
 * <p>
 * 分辨率取 {@link AirspaceControllerProperties#getH3Resolution()}（默认 9 级，边长约 174m），
 * 用于：遥测网格索引、冲突检测分组预筛（O(N²) → O(N×K)）、网格密度 / 容量评估。
 */
@Slf4j
@Service
public class H3GridService {

    private final H3Core h3;
    private final int resolution;

    public H3GridService(AirspaceControllerProperties props) {
        try {
            this.h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new IllegalStateException("H3Core 初始化失败（native 库加载异常）", e);
        }
        this.resolution = props.getH3Resolution();
        log.info("H3 网格服务就绪: resolution={}, 平均边长≈{}m", resolution, edgeLengthMeters(resolution));
    }

    /** 经纬度 → H3 单元格索引（long 形式，供 DroneStateStore / ConflictDetector 使用） */
    public long index(double lat, double lon) {
        return h3.latLngToCell(lat, lon, resolution);
    }

    /** H3 单元格索引 → 字符串形式（如 8928308280fffff） */
    public String indexToString(long h3Index) {
        return h3.h3ToString(h3Index);
    }

    /** H3 单元格中心点 [lat, lon] */
    public double[] cellCenter(long h3Index) {
        LatLng geo = h3.cellToLatLng(h3Index);
        return new double[]{geo.lat, geo.lng};
    }

    /** k 环邻居（同分辨率相邻单元格），供容量扩散 / 热力图取邻域用 */
    public List<Long> kRing(long h3Index, int k) {
        return h3.gridDisk(h3Index, k);
    }

    /** 各分辨率近似平均边长（米），仅用于日志与文档展示 */
    public static int edgeLengthMeters(int res) {
        int[] edges = {1108000, 418676, 158244, 59810, 22598, 8544, 3229, 1220, 461, 174, 66, 25, 9, 3};
        return (res >= 0 && res < edges.length) ? edges[res] : -1;
    }
}
