package com.uav.common.interfaces;

import com.uav.common.dto.AlarmEventDTO;
import com.uav.common.dto.TelemetryDTO;

import java.util.List;

/**
 * 告警引擎接口 - uav-alarm-engine 实现
 * 其他模块通过此接口调用告警服务
 */
public interface AlarmEngineService {

    /**
     * 对单条遥测数据进行告警评估
     * @param telemetry 无人机遥测数据
     * @return 触发的告警列表（空列表代表无告警）
     */
    List<AlarmEventDTO> evaluate(TelemetryDTO telemetry);

    /**
     * 批量评估
     */
    List<AlarmEventDTO> evaluateBatch(List<TelemetryDTO> telemetryList);

    /**
     * 确认告警
     */
    void acknowledgeAlarm(String alarmId, String userId);

    /**
     * 查询活跃告警
     */
    List<AlarmEventDTO> getActiveAlarms(String droneSn);

    /**
     * 查询告警规则
     */
    List<String> getRules();
}
