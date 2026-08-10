package com.uav.track.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.track.fusion.MultiSourceFusionEngine;
import com.uav.track.model.PositionSource;
import com.uav.track.model.RawPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * 多源位置数据消费者
 * <p>
 * 监听多个 Kafka Topic，接收不同来源的无人机位置数据：
 * <ul>
 *   <li>uav.telemetry — 无人机 MQTT 遥测（主来源）</li>
 *   <li>uav.adsb.position — ADS-B 接收机</li>
 *   <li>uav.radar.position — 地面监视雷达</li>
 *   <li>uav.rtk.position — RTK 差分基站</li>
 *   <li>uav.remoteid.position — Remote ID 广播</li>
 * </ul>
 * <p>
 * 统一转换为 RawPosition → 送入融合引擎。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawPositionConsumer {

    private final MultiSourceFusionEngine fusionEngine;
    private final FittedTrackProducer trackProducer;
    private final ObjectMapper objectMapper;

    /**
     * 消费无人机遥测（主来源）
     */
    @KafkaListener(topics = "uav.telemetry", groupId = "track-fusion-telemetry",
            containerFactory = "kafkaListenerContainerFactory")
    public void onTelemetry(String message) {
        processMessage(message, PositionSource.DRONE_TELEMETRY);
    }

    /**
     * 消费 ADS-B 数据
     */
    @KafkaListener(topics = "uav.adsb.position", groupId = "track-fusion-adsb",
            containerFactory = "kafkaListenerContainerFactory")
    public void onAdsb(String message) {
        processMessage(message, PositionSource.ADS_B);
    }

    /**
     * 消费雷达数据
     */
    @KafkaListener(topics = "uav.radar.position", groupId = "track-fusion-radar",
            containerFactory = "kafkaListenerContainerFactory")
    public void onRadar(String message) {
        processMessage(message, PositionSource.GROUND_RADAR);
    }

    /**
     * 消费 RTK 数据
     */
    @KafkaListener(topics = "uav.rtk.position", groupId = "track-fusion-rtk",
            containerFactory = "kafkaListenerContainerFactory")
    public void onRtk(String message) {
        processMessage(message, PositionSource.RTK_BASE);
    }

    /**
     * 消费 Remote ID 数据
     */
    @KafkaListener(topics = "uav.remoteid.position", groupId = "track-fusion-remoteid",
            containerFactory = "kafkaListenerContainerFactory")
    public void onRemoteId(String message) {
        processMessage(message, PositionSource.REMOTE_ID);
    }

    @SuppressWarnings("unchecked")
    private void processMessage(String message, PositionSource source) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);

            RawPosition raw = RawPosition.builder()
                    .droneSn(getString(data, "droneSn", "drone_sn", "icao24"))
                    .source(source)
                    .latitude(getDouble(data, "latitude", "lat"))
                    .longitude(getDouble(data, "longitude", "lon", "lng"))
                    .altitude(getDouble(data, "altitude", "alt", "height"))
                    .heading(getDouble(data, "heading", "course", "track"))
                    .groundSpeed(getDouble(data, "groundSpeed", "speed", "gs"))
                    .climbRate(getDouble(data, "climbRate", "verticalRate", "vr"))
                    .timestamp(parseTimestamp(data))
                    .rawPayload(message)
                    .build();

            if (raw.getDroneSn() == null || raw.getLatitude() == null || raw.getLongitude() == null) {
                log.debug("跳过不完整的位置数据: source={}, sn={}", source, raw.getDroneSn());
                return;
            }

            // 送入多源融合引擎
            RawPosition fused = fusionEngine.ingest(raw);
            if (fused != null) {
                // 融合结果 → 送入轨迹拟合管道
                trackProducer.onFusedPosition(fused);
            }

        } catch (Exception e) {
            log.error("处理位置数据失败: source={}, msg={}", source,
                    message.length() > 200 ? message.substring(0, 200) + "..." : message, e);
        }
    }

    private String getString(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object val = data.get(key);
            if (val != null) return val.toString();
        }
        return null;
    }

    private Double getDouble(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object val = data.get(key);
            if (val == null) continue;
            if (val instanceof Number n) return n.doubleValue();
            try { return Double.parseDouble(val.toString()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Instant parseTimestamp(Map<String, Object> data) {
        Object ts = data.get("timestamp");
        if (ts == null) ts = data.get("time");
        if (ts == null) ts = data.get("ts");
        if (ts == null) return Instant.now();

        if (ts instanceof Number n) {
            long ms = n.longValue();
            // 秒级时间戳 → 毫秒
            if (ms < 1e12) ms *= 1000;
            return Instant.ofEpochMilli(ms);
        }
        try {
            return Instant.parse(ts.toString());
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
