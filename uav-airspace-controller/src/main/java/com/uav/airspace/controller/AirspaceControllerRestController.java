package com.uav.airspace.controller;

import com.uav.airspace.detection.ConflictDetector;
import com.uav.airspace.detection.ConflictResult;
import com.uav.airspace.dispatcher.ResolutionDispatcher;
import com.uav.airspace.grid.H3DensityService;
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
    private final H3DensityService h3DensityService;

    public AirspaceControllerRestController(DroneStateStore stateStore,
                                            ConflictDetector conflictDetector,
                                            ResolutionDispatcher dispatcher,
                                            H3DensityService h3DensityService) {
        this.stateStore = stateStore;
        this.conflictDetector = conflictDetector;
        this.dispatcher = dispatcher;
        this.h3DensityService = h3DensityService;
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

    /** H3 网格密度（含单元格中心点与超限标记，供前端热力图渲染） */
    @GetMapping("/grid/density")
    public Map<String, Object> gridDensity() {
        Map<String, Object> result = new HashMap<>();
        result.put("onlineDrones", stateStore.size());
        result.put("cells", h3DensityService.densityView());
        result.put("overloadedCells", h3DensityService.overloadedCells().size());
        return result;
    }
}
