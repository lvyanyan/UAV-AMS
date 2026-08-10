package com.uav.airspace.state;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无人机状态存储 — 纯内存，线程安全
 * <p>
 * 百万级规模设计：每架无人机状态约 1KB，100 万架 ≈ 1GB 内存。
 * 生产环境可替换为 Redis + 本地 L1 缓存两层架构。
 */
@Slf4j
@Component
public class DroneStateStore {

    /** droneSn → 最新状态快照 */
    private final Map<String, DroneStateSnapshot> store = new ConcurrentHashMap<>();

    public void put(DroneStateSnapshot snapshot) {
        store.put(snapshot.getDroneSn(), snapshot);
    }

    public DroneStateSnapshot get(String droneSn) {
        return store.get(droneSn);
    }

    public DroneStateSnapshot remove(String droneSn) {
        return store.remove(droneSn);
    }

    /** 获取所有在线无人机（用于冲突检测遍历） */
    public Map<String, DroneStateSnapshot> getAll() {
        return store;
    }

    /** 按 H3 网格分组，返回指定网格内的所有无人机 */
    public java.util.List<DroneStateSnapshot> getByH3Index(long h3Index) {
        return store.values().stream()
                .filter(s -> s.getH3Index() == h3Index)
                .toList();
    }

    /** 清理过期状态 */
    public int cleanExpired(int expireSeconds) {
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue().isExpired(expireSeconds));
        int removed = before - store.size();
        if (removed > 0) {
            log.debug("清理过期无人机状态: {} 架 (剩余 {})", removed, store.size());
        }
        return removed;
    }

    public int size() {
        return store.size();
    }
}
