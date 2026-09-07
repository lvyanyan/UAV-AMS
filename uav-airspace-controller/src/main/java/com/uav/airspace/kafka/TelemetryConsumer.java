package com.uav.airspace.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.airspace.grid.H3GridService;
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
    private final H3GridService h3GridService;
    private final ObjectMapper objectMapper;

    public TelemetryConsumer(DroneStateStore stateStore, H3GridService h3GridService) {
        this.stateStore = stateStore;
        this.h3GridService = h3GridService;
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

        // H3 索引（uber/h3 官方库，分辨率 airspace.h3-resolution，默认 9 级）
        snap.setH3Index(h3GridService.index(telemetry.getLat(), telemetry.getLon()));

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
}
