package com.uav.flightplan.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.flightplan.entity.FlightPlan;
import com.uav.flightplan.service.FlightPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 飞行计划生命周期消费者：消费 uav.plan.lifecycle（生产方为 uav-realtime 遥测边沿桥），
 * 把仿真器回流的实际起飞/降落事件同步回 flight_plan 状态机。
 *
 * 事件契约（JSON）：
 *   { action: "TAKEOFF_ACK"|"COMPLETED"|"ABORT_ACK", planCode, droneSn,
 *     actualStart?: epochMs, actualEnd?: epochMs, phase?: string }
 *
 * 状态推进（带前置状态守卫，防止陈旧/乱序事件破坏终态）：
 *   TAKEOFF_ACK → IN_FLIGHT + actualStart   （仅 RELEASED 可进入）
 *   COMPLETED   → COMPLETED + actualEnd     （仅 IN_FLIGHT 可进入）
 *   ABORT_ACK   → CANCELLED                 （仅 RELEASED/IN_FLIGHT 可进入）
 * planCode 找不到计划时只打日志（STRESS 压测机的随机计划号属正常噪音）。
 */
@Component
public class PlanLifecycleConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlanLifecycleConsumer.class);

    public static final String TOPIC_PLAN_LIFECYCLE = "uav.plan.lifecycle";

    private final FlightPlanService planService;
    private final ObjectMapper om = new ObjectMapper();

    public PlanLifecycleConsumer(FlightPlanService planService) {
        this.planService = planService;
    }

    @KafkaListener(topics = TOPIC_PLAN_LIFECYCLE, groupId = "flight-plan")
    public void onLifecycleEvent(String message) {
        try {
            JsonNode node = om.readTree(message);
            String action = node.path("action").asText("");
            String planCode = node.path("planCode").asText("");
            String droneSn = node.path("droneSn").asText("");
            if (planCode.isEmpty()) {
                log.debug("生命周期事件缺少 planCode，忽略: {}", message);
                return;
            }

            FlightPlan plan = planService.lambdaQuery()
                    .eq(FlightPlan::getPlanCode, planCode).one();
            if (plan == null) {
                // 压测机队的计划号不在库中属正常情况，降级为 DEBUG
                log.debug("生命周期事件对应计划不存在: planCode={}, action={}, droneSn={}",
                        planCode, action, droneSn);
                return;
            }

            switch (action) {
                case "TAKEOFF_ACK" -> {
                    if (!"RELEASED".equals(plan.getPlanStatus())) {
                        log.debug("计划 {} 状态 {} 不接受 TAKEOFF_ACK，忽略", planCode, plan.getPlanStatus());
                        return;
                    }
                    plan.setPlanStatus("IN_FLIGHT");
                    LocalDateTime st = toLocalDateTime(node.path("actualStart"));
                    if (st != null) plan.setActualStart(st);
                    planService.updateById(plan);
                    log.info("计划 {} 已起飞（drone={}），状态 → IN_FLIGHT，actualStart={}", planCode, droneSn, st);
                }
                case "COMPLETED" -> {
                    if (!"IN_FLIGHT".equals(plan.getPlanStatus())) {
                        log.debug("计划 {} 状态 {} 不接受 COMPLETED，忽略", planCode, plan.getPlanStatus());
                        return;
                    }
                    plan.setPlanStatus("COMPLETED");
                    LocalDateTime et = toLocalDateTime(node.path("actualEnd"));
                    if (et != null) plan.setActualEnd(et);
                    planService.updateById(plan);
                    log.info("计划 {} 已降落（drone={}），状态 → COMPLETED，actualEnd={}", planCode, droneSn, et);
                }
                case "ABORT_ACK" -> {
                    if (!"RELEASED".equals(plan.getPlanStatus()) && !"IN_FLIGHT".equals(plan.getPlanStatus())) {
                        log.debug("计划 {} 状态 {} 不接受 ABORT_ACK，忽略", planCode, plan.getPlanStatus());
                        return;
                    }
                    plan.setPlanStatus("CANCELLED");
                    LocalDateTime et = toLocalDateTime(node.path("actualEnd"));
                    if (et != null) plan.setActualEnd(et);
                    planService.updateById(plan);
                    log.info("计划 {} 中止确认（drone={}），状态 → CANCELLED", planCode, droneSn);
                }
                default -> log.debug("未知生命周期事件 action={}，忽略: {}", action, message);
            }
        } catch (Exception e) {
            log.error("处理生命周期事件失败: {}, err={}", message, e.getMessage());
        }
    }

    /** epoch 毫秒 → 本地时间；兼容直接传 ISO 字符串的调用方 */
    private LocalDateTime toLocalDateTime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.canConvertToLong()) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(node.asLong()), java.time.ZoneId.systemDefault());
        }
        try {
            return LocalDateTime.parse(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
