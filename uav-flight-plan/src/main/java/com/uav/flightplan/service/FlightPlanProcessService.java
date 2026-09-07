package com.uav.flightplan.service;

import com.uav.flightplan.entity.FlightPlan;
import com.uav.flightplan.entity.FlightPlanApproval;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 飞行计划审批流程服务（Flowable BPMN 驱动）
 * <p>
 * 流程定义：resources/processes/flight-plan-approval.bpmn20.xml
 * 状态机与原控制器硬编码逻辑一一对应：
 * DRAFT → PENDING_LEVEL1 → PENDING_LEVEL2 → PENDING_LEVEL3 → APPROVED；任意节点可 REJECTED。
 * 审批记录（uav_flight_approval）在此层落库，与原行为一致。
 */
@Slf4j
@Service
public class FlightPlanProcessService {

    public static final String PROCESS_KEY = "flightPlanApproval";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final FlightPlanService planService;
    private final FlightPlanApprovalService approvalService;

    public FlightPlanProcessService(RuntimeService runtimeService,
                                    TaskService taskService,
                                    FlightPlanService planService,
                                    FlightPlanApprovalService approvalService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.planService = planService;
        this.approvalService = approvalService;
    }

    /** 提交后启动审批流程实例（businessKey = planId；已存在活跃实例则跳过） */
    @Transactional
    public void startApproval(FlightPlan plan) {
        boolean exists = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(String.valueOf(plan.getId()))
                .active()
                .count() > 0;
        if (exists) {
            log.info("计划 {} 已有在途审批流程，跳过启动", plan.getId());
            return;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("planId", String.valueOf(plan.getId()));
        vars.put("planCode", plan.getPlanCode());
        vars.put("riskLevel", plan.getRiskLevel() == null ? "low" : plan.getRiskLevel());
        runtimeService.startProcessInstanceByKey(PROCESS_KEY, String.valueOf(plan.getId()), vars);
        log.info("计划 {} 审批流程已启动（riskLevel={}）", plan.getId(), vars.get("riskLevel"));
    }

    /** 当前活跃审批任务（三级串行推进，同一时刻至多一个） */
    public Task activeTask(Long planId) {
        return taskService.createTaskQuery()
                .processInstanceBusinessKey(String.valueOf(planId))
                .active()
                .singleResult();
    }

    /** 审批通过当前节点：落审批记录 → 完成任务（BPMN 网关按 approved 推进 / 收口落库） */
    @Transactional
    public FlightPlan approve(Long planId, Long approverId, String comment) {
        FlightPlan plan = planService.getById(planId);
        if (plan == null) return null;

        Task task = activeTask(planId);
        if (task == null) {
            // 兜底：无在途流程（历史数据），直接置为已批准
            log.warn("计划 {} 无在途审批任务，直接置 APPROVED", planId);
            plan.setPlanStatus("APPROVED");
            planService.updateById(plan);
            return plan;
        }
        int level = levelOf(task);
        record(planId, approverId, level, "APPROVED", comment);
        taskService.setAssignee(task.getId(), String.valueOf(approverId));
        taskService.complete(task.getId(), Map.of("approved", true, "lastLevel", level));
        return planService.getById(planId);
    }

    /** 驳回：落审批记录 → 完成任务（BPMN 路由到驳回落库节点） */
    @Transactional
    public FlightPlan reject(Long planId, Long approverId, String comment) {
        FlightPlan plan = planService.getById(planId);
        if (plan == null) return null;

        Task task = activeTask(planId);
        int level = task != null ? levelOf(task) : 0;
        record(planId, approverId, level, "REJECTED", comment);
        if (task != null) {
            taskService.setAssignee(task.getId(), String.valueOf(approverId));
            taskService.complete(task.getId(), Map.of("approved", false, "lastLevel", level));
        } else {
            plan.setPlanStatus("REJECTED");
            planService.updateById(plan);
        }
        return planService.getById(planId);
    }

    /** 军民直批 / 直撤等旁路场景：终止在途流程实例，避免悬挂任务 */
    @Transactional
    public void terminateProcess(Long planId, String reason) {
        runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(String.valueOf(planId))
                .active()
                .list()
                .forEach(pi -> runtimeService.deleteProcessInstance(pi.getId(), reason));
    }

    /** 任务 category（"1"/"2"/"3"，见 BPMN flowable:category）→ 审批级别 */
    private int levelOf(Task task) {
        try {
            return Integer.parseInt(task.getCategory());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void record(Long planId, Long approverId, int level, String result, String comment) {
        FlightPlanApproval approval = new FlightPlanApproval();
        approval.setPlanId(planId);
        approval.setApproverId(approverId);
        approval.setApprovalLevel(level);
        approval.setResult(result);
        approval.setComment(comment);
        approvalService.save(approval);
    }
}
