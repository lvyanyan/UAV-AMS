package com.uav.airspace.controller;

import com.uav.airspace.detection.ConflictDetector;
import com.uav.airspace.detection.ConflictResult;
import com.uav.airspace.dispatcher.ResolutionDispatcher;
import com.uav.airspace.resolution.ResolutionCommand;
import com.uav.airspace.state.DroneStateStore;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 空域控制器 REST API
 */
@RestController
@RequestMapping("/api/airspace-controller")
public class AirspaceControllerRestController {

    private final DroneStateStore stateStore;
    private final ConflictDetector conflictDetector;
    private final ResolutionDispatcher dispatcher;

    public AirspaceControllerRestController(DroneStateStore stateStore,
                                            ConflictDetector conflictDetector,
                                            ResolutionDispatcher dispatcher) {
        this.stateStore = stateStore;
        this.conflictDetector = conflictDetector;
        this.dispatcher = dispatcher;
    }

    /** 在线无人机数量 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("onlineDrones", stateStore.size());
        result.put("activeConflicts", conflictDetector.getActiveConflicts().size());
        result.put("recentResolutions", dispatcher.getRecentCommands().size());
        return result;
    }

    /** 活跃冲突列表 */
    @GetMapping("/conflicts")
    public List<ConflictResult> activeConflicts() {
        return List.copyOf(conflictDetector.getActiveConflicts().values());
    }

    /** 最近解脱指令列表 */
    @GetMapping("/resolutions")
    public List<ResolutionCommand> recentResolutions(
            @RequestParam(defaultValue = "50") int limit) {
        List<ResolutionCommand> all = dispatcher.getRecentCommands();
        return all.size() > limit ? all.subList(0, limit) : all;
    }
}
