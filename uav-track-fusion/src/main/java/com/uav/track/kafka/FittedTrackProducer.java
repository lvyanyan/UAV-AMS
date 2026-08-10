package com.uav.track.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.common.dto.AlarmEventDTO;
import com.uav.track.correlation.AlertCorrelator;
import com.uav.track.correlation.FlightPlanCorrelator;
import com.uav.track.fitting.BezierTrackFitter;
import com.uav.track.fitting.KalmanTrackSmoother;
import com.uav.track.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * 拟合轨迹生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FittedTrackProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KalmanTrackSmoother smoother;
    private final BezierTrackFitter bezierFitter;
    private final AlertCorrelator alertCorrelator;
    private final FlightPlanCorrelator flightPlanCorrelator;

    private final ConcurrentHashMap<String, List<RawPosition>> positionBuffer = new ConcurrentHashMap<>();

    private static final int MAX_BUFFER_SIZE = 200;
    private static final int FITTING_INTERVAL_SEC = 5;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "track-fitting");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean started = false;

    public void onFusedPosition(RawPosition fused) {
        positionBuffer.computeIfAbsent(fused.getDroneSn(), k -> new ArrayList<>()).add(fused);
        List<RawPosition> list = positionBuffer.get(fused.getDroneSn());
        while (list.size() > MAX_BUFFER_SIZE) {
            list.remove(0);
        }
    }

    public void start() {
        if (started) return;
        started = true;

        scheduler.scheduleAtFixedRate(() -> {
            try {
                fitAllTracks();
            } catch (Exception e) {
                log.error("轨迹拟合任务异常", e);
            }
        }, FITTING_INTERVAL_SEC, FITTING_INTERVAL_SEC, TimeUnit.SECONDS);

        log.info("轨迹拟合定时任务已启动，间隔 {} 秒", FITTING_INTERVAL_SEC);
    }

    private void fitAllTracks() {
        long now = System.currentTimeMillis();
        int fitCount = 0;

        for (Map.Entry<String, List<RawPosition>> entry : positionBuffer.entrySet()) {
            String droneSn = entry.getKey();
            List<RawPosition> positions = entry.getValue();

            if (positions.size() < 3) continue;

            long cutoff = now - (FITTING_INTERVAL_SEC + 5) * 1000L;
            List<RawPosition> recent = positions.stream()
                    .filter(p -> p.getReceivedAt().toEpochMilli() > cutoff)
                    .toList();

            if (recent.size() < 3) continue;

            try {
                FittedTrack track = fitSingleTrack(droneSn, recent);
                publishTrack(track);
                fitCount++;
            } catch (Exception e) {
                log.error("拟合轨迹失败: droneSn={}", droneSn, e);
            }
        }

        if (fitCount > 0) {
            log.debug("轨迹拟合完成: {} 架无人机", fitCount);
        }
    }

    private FittedTrack fitSingleTrack(String droneSn, List<RawPosition> positions) {
        List<double[]> smoothed = smoother.smooth(positions);
        List<double[]> bezierPoints = bezierFitter.fit(smoothed);

        TrackSegment segment = TrackSegment.builder()
                .segmentIndex(0)
                .startTime(positions.get(0).getReceivedAt())
                .endTime(positions.get(positions.size() - 1).getReceivedAt())
                .smoothedPoints(bezierPoints)
                .rawPointCount(positions.size())
                .dominantSource(determineDominantSource(positions))
                .avgSpeed(computeAvgSpeed(positions))
                .avgAltitude(computeAvgAltitude(positions))
                .hasAlert(false)
                .alertAnnotations(List.of())
                .maxAlertLevel("GENERAL")
                .build();

        FittedTrack track = FittedTrack.builder()
                .droneSn(droneSn)
                .generatedAt(Instant.now())
                .segments(List.of(segment))
                .fusionSources(extractSources(positions))
                .fusionQuality(computeFusionQuality(positions))
                .build();

        alertCorrelator.annotate(droneSn, track.getSegments());
        flightPlanCorrelator.correlate(track, droneSn);

        return track;
    }

    private void publishTrack(FittedTrack track) {
        try {
            String json = objectMapper.writeValueAsString(track);
            kafkaTemplate.send("uav.track.fitted", track.getDroneSn(), json);
        } catch (JsonProcessingException e) {
            log.error("序列化拟合轨迹失败: droneSn={}", track.getDroneSn(), e);
        }
    }

    @KafkaListener(topics = "uav.alarm.event", groupId = "track-fusion-alert",
            containerFactory = "kafkaListenerContainerFactory")
    public void onAlarmEvent(String message) {
        try {
            AlarmEventDTO alert = objectMapper.readValue(message, AlarmEventDTO.class);
            alertCorrelator.receiveAlert(alert);
        } catch (Exception e) {
            log.error("解析告警事件失败", e);
        }
    }

    private PositionSource determineDominantSource(List<RawPosition> positions) {
        Map<PositionSource, Long> counts = new HashMap<>();
        for (RawPosition p : positions) {
            counts.merge(p.getSource(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(PositionSource.UNKNOWN);
    }

    private List<PositionSource> extractSources(List<RawPosition> positions) {
        return positions.stream()
                .map(RawPosition::getSource)
                .distinct()
                .toList();
    }

    private double computeFusionQuality(List<RawPosition> positions) {
        long sourceCount = positions.stream().map(RawPosition::getSource).distinct().count();
        return sourceCount >= 2 ? 0.9 : 0.6;
    }

    private Double computeAvgSpeed(List<RawPosition> positions) {
        return positions.stream()
                .mapToDouble(p -> p.getGroundSpeed() != null ? p.getGroundSpeed() : 0)
                .average().orElse(0);
    }

    private Double computeAvgAltitude(List<RawPosition> positions) {
        return positions.stream()
                .mapToDouble(p -> p.getAltitude() != null ? p.getAltitude() : 0)
                .average().orElse(0);
    }
}
