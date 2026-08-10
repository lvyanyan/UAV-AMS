package com.uav.airspace.dispatcher;

import com.uav.airspace.resolution.ResolutionCommand;
import com.uav.common.dto.ConflictAlertDTO;
import com.uav.common.dto.ResolutionCommandDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 解脱指令分发器
 * <p>
 * 双通道分发：
 * 1. 标牌通道 → WebSocket 推送到 Cesium 前端，显示在无人机标牌上
 * 2. 运服席位通道 → WebSocket 推送到运服席位控制台
 * <p>
 * 当前实现：通过内部事件总线 + Kafka Topic 中转。
 * 前端和运服席位各自订阅对应 Topic。
 */
@Slf4j
@Component
public class ResolutionDispatcher {

    private final SignboardNotifier signboardNotifier;
    private final OpsSeatNotifier opsSeatNotifier;

    /** 最近 N 条指令（用于 REST 查询） */
    private final List<ResolutionCommand> recentCommands = new CopyOnWriteArrayList<>();
    private static final int MAX_RECENT = 200;

    public ResolutionDispatcher(SignboardNotifier signboardNotifier,
                                OpsSeatNotifier opsSeatNotifier) {
        this.signboardNotifier = signboardNotifier;
        this.opsSeatNotifier = opsSeatNotifier;
    }

    /**
     * 分发解脱指令到标牌和运服席位
     */
    public void dispatch(ResolutionCommand command) {
        command.setStatus("DISPATCHED");
        ResolutionCommandDTO dto = command.toDto();

        // 通道 1: 标牌推送（前端 Cesium 无人机标签显示）
        signboardNotifier.push(command.getTargetDroneSn(), dto);

        // 通道 2: 运服席位推送（运营人员控制台）
        opsSeatNotifier.push(dto);

        // 记录
        recentCommands.add(0, command);
        if (recentCommands.size() > MAX_RECENT) {
            recentCommands.remove(recentCommands.size() - 1);
        }

        log.info("解脱指令已分发: id={} target={} → 标牌 + 运服席位",
                command.getResolutionId(), command.getTargetDroneSn());
    }

    /**
     * 同时分发冲突告警和对应的解脱指令
     */
    public void dispatchWithConflict(ConflictAlertDTO conflict,
                                     ResolutionCommandDTO resolution) {
        if (conflict != null) {
            signboardNotifier.pushConflictAlert(conflict);
        }
        if (resolution != null) {
            opsSeatNotifier.push(resolution);
        }
    }

    public List<ResolutionCommand> getRecentCommands() {
        return List.copyOf(recentCommands);
    }
}
