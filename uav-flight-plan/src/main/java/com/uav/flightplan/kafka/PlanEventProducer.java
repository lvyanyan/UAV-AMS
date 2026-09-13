package com.uav.flightplan.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uav.flightplan.entity.FlightPlan;
import com.uav.flightplan.entity.UavRoute;
import com.uav.flightplan.service.UavRouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 飞行计划指令生产者：向 uav.plan.cmd 下发起飞/返航/中止指令。
 * 消费方只有 uav-realtime 指令桥（Kafka → MQTT uav/{sn}/cmd → 仿真器）。
 *
 * payload 契约（JSON）：
 *   { action: "TAKEOFF"|"RTL"|"ABORT", planCode, droneSn,
 *     routeWaypoints: [[lon,lat],...], cruiseAltM, plannedStart, plannedEnd, ts }
 */
@Service
public class PlanEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PlanEventProducer.class);

    /** 指令 topic（生产方仅本服务，消费方仅 realtime 桥） */
    public static final String TOPIC_PLAN_CMD = "uav.plan.cmd";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper om = new ObjectMapper();
    private final UavRouteService routeService;

    public PlanEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                             UavRouteService routeService) {
        this.kafkaTemplate = kafkaTemplate;
        this.routeService = routeService;
    }

    /** 下发带航点的起飞指令 */
    public void sendTakeoff(FlightPlan plan, Double cruiseAltM) {
        ObjectNode node = base(plan, "TAKEOFF");
        node.set("routeWaypoints", waypointsOf(plan));
        if (cruiseAltM != null) node.put("cruiseAltM", cruiseAltM);
        if (plan.getPlannedStart() != null) node.put("plannedStart", plan.getPlannedStart().toString());
        if (plan.getPlannedEnd() != null) node.put("plannedEnd", plan.getPlannedEnd().toString());
        send(plan, node);
    }

    /** 在飞返航指令（状态不变） */
    public void sendRtl(FlightPlan plan) {
        send(plan, base(plan, "RTL"));
    }

    /** 中止指令（仿真器执行紧急降落） */
    public void sendAbort(FlightPlan plan) {
        send(plan, base(plan, "ABORT"));
    }

    private ObjectNode base(FlightPlan plan, String action) {
        ObjectNode node = om.createObjectNode();
        node.put("action", action);
        node.put("planCode", plan.getPlanCode());
        node.put("droneSn", plan.getDroneSn());
        node.put("ts", System.currentTimeMillis());
        return node;
    }

    /** 航点取自航路表的结构化 JSON（[[lon,lat],...]）；无航路则空数组，仿真器自行按任务生成路径 */
    private ArrayNode waypointsOf(FlightPlan plan) {
        ArrayNode arr = om.createArrayNode();
        if (plan.getRouteId() == null) return arr;
        try {
            UavRoute route = routeService.getById(plan.getRouteId());
            if (route != null && route.getWaypoints() != null) {
                JsonNode wp = om.readTree(route.getWaypoints());
                if (wp.isArray()) {
                    for (JsonNode p : wp) {
                        ArrayNode pair = arr.addArray();
                        pair.add(p.get(0).asDouble());
                        pair.add(p.get(1).asDouble());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取航路航点失败（计划 {}）: {}", plan.getPlanCode(), e.getMessage());
        }
        return arr;
    }

    private void send(FlightPlan plan, ObjectNode payload) {
        String json = payload.toString();
        // key 用 droneSn，保证同机指令有序（同分区）
        kafkaTemplate.send(TOPIC_PLAN_CMD, plan.getDroneSn(), json);
        log.info("Kafka 指令已下发 [{}]: {}", TOPIC_PLAN_CMD, json);
    }
}
