package com.uav.track.controller;

import com.uav.track.fusion.MultiSourceFusionEngine;
import com.uav.track.kafka.FittedTrackProducer;
import com.uav.track.model.PositionSource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 轨迹融合 REST API
 */
@RestController
@RequestMapping("/api/track-fusion")
@RequiredArgsConstructor
public class TrackFusionController {

    private final MultiSourceFusionEngine fusionEngine;
    private final FittedTrackProducer trackProducer;

    /**
     * 获取指定无人机的活跃来源列表
     */
    @GetMapping("/sources/{droneSn}")
    public Map<String, Object> getActiveSources(@PathVariable String droneSn) {
        List<PositionSource> sources = fusionEngine.getActiveSources(droneSn);
        return Map.of(
                "droneSn", droneSn,
                "activeSources", sources.stream().map(PositionSource::getLabel).toList(),
                "sourceCount", sources.size()
        );
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "module", "uav-track-fusion",
                "timestamp", System.currentTimeMillis()
        );
    }
}
