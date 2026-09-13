package com.uav.flightplan.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.flightplan.entity.FlightPlan;
import com.uav.flightplan.mapper.FlightPlanMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class FlightPlanService extends ServiceImpl<FlightPlanMapper, FlightPlan> {

    private final JdbcTemplate jdbc;

    public FlightPlanService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 提交计划 → 状态从 DRAFT 变为 PENDING_LEVEL1 */
    @Transactional
    public FlightPlan submit(Long planId) {
        FlightPlan plan = getById(planId);
        if (plan != null && "DRAFT".equals(plan.getPlanStatus())) {
            plan.setPlanStatus("PENDING_LEVEL1");
            plan.setSubmitTime(LocalDateTime.now());
            updateById(plan);
        }
        return plan;
    }

    /**
     * 生成当日计划编号 FP-yyyyMMdd-XXXX（当天序号 = 查当天最大序号 + 1）。
     * plan_code 有唯一约束，并发创建时靠唯一冲突重试兜底（最多 3 次）。
     */
    public String nextPlanCode() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Integer max = jdbc.queryForObject(
                "select coalesce(max(cast(right(plan_code, 4) as integer)), 0) "
              + "from flight_plan where plan_code like ?",
                Integer.class, "FP-" + date + "-%");
        int seq = (max == null ? 0 : max) + 1;
        return String.format("FP-%s-%04d", date, seq);
    }

    /** 创建计划：自动补编号，唯一冲突（并发窗口）时重新取号重试 */
    public FlightPlan createWithCode(FlightPlan plan) {
        if (plan.getPlanCode() == null || plan.getPlanCode().isBlank()) {
            plan.setPlanCode(nextPlanCode());
        }
        for (int attempt = 0; ; attempt++) {
            try {
                save(plan);
                return plan;
            } catch (DataIntegrityViolationException e) {
                if (attempt >= 2 || plan.getPlanCode() == null) throw e;
                // 编号被并发占用 → 重取序号（自定义编号冲突则直接抛出）
                plan.setPlanCode(nextPlanCode());
            }
        }
    }
}
