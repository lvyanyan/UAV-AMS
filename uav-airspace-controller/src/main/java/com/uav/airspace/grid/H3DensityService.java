package com.uav.airspace.grid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.airspace.config.AirspaceControllerProperties;
import com.uav.airspace.state.DroneStateSnapshot;
import com.uav.airspace.state.DroneStateStore;
import com.uav.common.dto.AlarmEventDTO;
import com.uav.common.enums.AlarmLevel;
import com.uav.common.enums.AlarmType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * H3 网格密度统计 + 容量评估
 * <p>
 * 基于 {@link DroneStateStore} 中在线无人机的 H3 索引做实时聚合：
 * <ul>
 *   <li>网格密度：每单元格当前架数（供前端热力图 / REST 查询）</li>
 *   <li>容量评估：单元格架数超过阈值即判定超限，进入超限状态时向
 *       {@code uav.alarm.event} 发布 {@link AlarmType#CAPACITY_EXCEEDED} 告警
 *       （仅状态迁移时触发一次，退出超限后重新武装）</li>
 * </ul>
 */
@Slf4j
@Service
public class H3DensityService {

    /** 输出 Topic：与 uav-alarm-engine 的 AlarmEventProducer 相同，uav-realtime (Go) 消费后推 WebSocket */
    private static final String ALARM_TOPIC = "uav.alarm.event";

    private final DroneStateStore stateStore;
    private final H3GridService h3GridService;
    private final AirspaceControllerProperties props;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 当前处于超限状态的单元格（仅用于状态迁移去重） */
    private final Set<Long> overloadedCells = new HashSet<>();

    public H3DensityService(DroneStateStore stateStore,
                            H3GridService h3GridService,
                            AirspaceControllerProperties props,
                            KafkaTemplate<String, String> kafkaTemplate) {
        this.stateStore = stateStore;
        this.h3GridService = h3GridService;
        this.props = props;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** 在线无人机按 H3 单元格聚合：h3Index → 架数 */
    public Map<Long, Integer> density() {
        Map<Long, Integer> density = new TreeMap<>();
        for (DroneStateSnapshot snap : stateStore.getAll().values()) {
            density.merge(snap.getH3Index(), 1, Integer::sum);
        }
        return density;
    }

    /** 超限单元格列表（架数 > 容量阈值） */
    public List<Long> overloadedCells() {
        int max = props.getCapacityMaxDronesPerCell();
        List<Long> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : density().entrySet()) {
            if (e.getValue() > max) result.add(e.getKey());
        }
        return result;
    }

    /**
     * 定时容量评估：状态迁移进入超限时发布一次告警，退出后重新武装。
     * 周期与冲突检测对齐（200ms），发布走 KafkaTemplate 异步，不阻塞检测主循环。
     */
    @Scheduled(fixedDelayString = "${airspace.detection-interval-ms:200}")
    public void evaluateCapacity() {
        if (!props.isCapacityAlarmEnabled()) return;

        Set<Long> current = new HashSet<>(overloadedCells());
        // 新进入超限的单元格 → 发告警
        for (Long cell : current) {
            if (overloadedCells.add(cell)) {
                publishCapacityAlarm(cell);
            }
        }
        // 已退出超限的单元格 → 解除武装
        overloadedCells.retainAll(current);
    }

    private void publishCapacityAlarm(long cell) {
        int count = density().getOrDefault(cell, 0);
        double[] center = h3GridService.cellCenter(cell);

        AlarmEventDTO alarm = new AlarmEventDTO();
        alarm.setAlarmId("CAP-" + Long.toHexString(cell));
        alarm.setAlarmType(AlarmType.CAPACITY_EXCEEDED);
        alarm.setAlarmLevel(AlarmLevel.SERIOUS);
        alarm.setTitle("空域容量超限");
        alarm.setDescription(String.format("H3 网格 %s 当前 %d 架，超过容量阈值 %d 架",
                h3GridService.indexToString(cell), count, props.getCapacityMaxDronesPerCell()));
        alarm.setLatitude(center[0]);
        alarm.setLongitude(center[1]);
        alarm.setThresholdValue((double) props.getCapacityMaxDronesPerCell());
        alarm.setActualValue((double) count);
        alarm.setAlarmTime(Instant.now());
        alarm.setH3Index(String.valueOf(cell));

        try {
            kafkaTemplate.send(ALARM_TOPIC, alarm.getAlarmId(), objectMapper.writeValueAsString(alarm));
            log.warn("容量超限告警: cell={}, count={}", h3GridService.indexToString(cell), count);
        } catch (Exception e) {
            log.error("容量告警发布失败: {}", e.getMessage());
        }
    }

    /** REST 视图：密度 + 超限标记 + 单元格中心点（供前端热力图渲染） */
    public List<Map<String, Object>> densityView() {
        int max = props.getCapacityMaxDronesPerCell();
        List<Map<String, Object>> cells = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : density().entrySet()) {
            long cell = e.getKey();
            double[] center = h3GridService.cellCenter(cell);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("h3Index", h3GridService.indexToString(cell));
            item.put("lat", center[0]);
            item.put("lon", center[1]);
            item.put("count", e.getValue());
            item.put("overloaded", e.getValue() > max);
            cells.add(item);
        }
        return cells;
    }
}
