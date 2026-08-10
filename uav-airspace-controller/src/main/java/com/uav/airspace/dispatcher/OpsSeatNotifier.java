package com.uav.airspace.dispatcher;

import com.uav.common.dto.ResolutionCommandDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 运服席位推送器 — 向运营服务席位控制台推送解脱指令
 */
@Slf4j
@Component
public class OpsSeatNotifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OpsSeatNotifier(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void push(ResolutionCommandDTO dto) {
        try {
            String json = MAPPER.writeValueAsString(dto);
            kafkaTemplate.send("uav.ops-seat.resolution", dto.getResolutionId(), json);
            log.debug("运服席位推送 → resolution={} target={} priority={}",
                    dto.getResolutionId(), dto.getTargetDroneSn(), dto.getPriority());
        } catch (Exception e) {
            log.error("运服席位推送失败: {}", e.getMessage());
        }
    }
}
