package com.uav.airspace.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.airspace.state.DroneStateSnapshot;
import com.uav.airspace.state.DroneStateStore;
import com.uav.common.dto.TelemetryDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 遥测数据消费者
 * <p>
 * 消费 Topic: uav.telemetry
 * 每收到一条遥测 → 更新 DroneStateStore → 计算速度/加速度/生成 H3 索引
 */
@Slf4j
@Component
public class TelemetryConsumer {

    private final DroneStateStore stateStore;
    private final ObjectMapper objectMapper;

    public TelemetryConsumer(DroneStateStore stateStore) {
        this.stateStore = stateStore;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = "uav.telemetry", groupId = "airspace-controller")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            TelemetryDTO telemetry = objectMapper.readValue(record.value(), TelemetryDTO.class);
            updateState(telemetry);
        } catch (Exception e) {
            log.debug("遥测解析失败: {}", e.getMessage());
        }
    }

    private void updateState(TelemetryDTO telemetry) {
        DroneStateSnapshot prev = stateStore.get(telemetry.getDroneSn());

        DroneStateSnapshot snap = new DroneStateSnapshot();
        snap.setDroneSn(telemetry.getDroneSn());
        snap.setLatestTelemetry(telemetry);
        snap.setLastUpdateEpochMs(System.currentTimeMillis());

        // H3 索引（简化：基于经纬度计算近似 H3 索引）
        snap.setH3Index(computeApproximateH3(telemetry.getLat(), telemetry.getLon()));

        // 速度估计
        if (prev != null && prev.getLatestTelemetry() != null) {
            long dtMs = snap.getLastUpdateEpochMs() - prev.getLastUpdateEpochMs();
            if (dtMs > 0 && dtMs < 5000) {
                double dtSec = dtMs / 1000.0;

                double latDegPerMeter = 1.0 / 111320.0;
                double lonDegPerMeter = 1.0 / (111320.0
                        * Math.cos(Math.toRadians(telemetry.getLat())));

                snap.setVelocityNorth(
                        (telemetry.getLat() - prev.getLatestTelemetry().getLat())
                                / latDegPerMeter / dtSec);
                snap.setVelocityEast(
                        (telemetry.getLon() - prev.getLatestTelemetry().getLon())
                                / lonDegPerMeter / dtSec);
                snap.setVelocityUp(
                        (telemetry.getAlt() - prev.getLatestTelemetry().getAlt())
                                / dtSec);

                // 加速度估计
                double prevSpeed = Math.sqrt(
                        prev.getVelocityEast() * prev.getVelocityEast()
                                + prev.getVelocityNorth() * prev.getVelocityNorth()
                                + prev.getVelocityUp() * prev.getVelocityUp());
                double currSpeed = Math.sqrt(
                        snap.getVelocityEast() * snap.getVelocityEast()
                                + snap.getVelocityNorth() * snap.getVelocityNorth()
                                + snap.getVelocityUp() * snap.getVelocityUp());
                snap.setAccelerationMagnitude(
                        Math.abs(currSpeed - prevSpeed) / dtSec);
            }
        } else {
            // 首条遥测：使用遥测自带速度
            double spd = telemetry.getSpeed();
            double hdg = Math.toRadians(telemetry.getHeading());
            snap.setVelocityEast(spd * Math.sin(hdg));
            snap.setVelocityNorth(spd * Math.cos(hdg));
            snap.setVelocityUp(0);
        }

        stateStore.put(snap);
    }

    /**
     * 简化 H3 索引计算（避免引入 H3 库依赖）
     * <p>
     * 使用经纬度网格近似：将地球表面按 0.01° 划分网格，
     * 用网格 ID 替代 H3 索引。生产环境替换为真正的 H3（uber/h3）。
     */
    private long computeApproximateH3(double lat, double lon) {
        // 分辨率 9 级对应约 0.01°（~1km），用网格坐标替代
        long gridLat = (long) ((lat + 90.0) * 10000);
        long gridLon = (long) ((lon + 180.0) * 10000);
        // 组合为一个 long（高 32 位 = lat，低 32 位 = lon）
        return (gridLat << 32) | (gridLon & 0xFFFFFFFFL);
    }
}
