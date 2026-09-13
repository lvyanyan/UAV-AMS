package com.uav.pilot.controller;

import com.uav.common.base.R;
import com.uav.pilot.entity.UavPilot;
import com.uav.pilot.entity.UavPilotMedical;
import com.uav.pilot.service.MedicalCenterSyncService;
import com.uav.pilot.service.UavPilotService;
import com.uav.pilot.service.UavPilotMedicalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pilot")
public class PilotController {

    private final UavPilotService pilotService;
    private final UavPilotMedicalService medicalService;
    private final MedicalCenterSyncService medicalCenterSyncService;

    public PilotController(UavPilotService pilotService, UavPilotMedicalService medicalService,
                           MedicalCenterSyncService medicalCenterSyncService) {
        this.pilotService = pilotService;
        this.medicalService = medicalService;
        this.medicalCenterSyncService = medicalCenterSyncService;
    }

    // ===== 驾驶员 CRUD =====
    @PostMapping
    public R<UavPilot> create(@Valid @RequestBody UavPilot pilot) {
        pilot.setStatus("ACTIVE");
        pilotService.save(pilot);
        return R.ok(pilot);
    }

    @GetMapping("/{id}")
    public R<UavPilot> getById(@PathVariable Long id) {
        return R.ok(pilotService.getById(id));
    }

    @GetMapping("/list")
    public R<List<UavPilot>> list() {
        return R.ok(pilotService.list());
    }

    @PutMapping("/{id}")
    public R<UavPilot> update(@PathVariable Long id, @RequestBody UavPilot pilot) {
        pilot.setId(id);
        pilotService.updateById(pilot);
        return R.ok(pilotService.getById(id));
    }

    @PutMapping("/{id}/suspend")
    public R<UavPilot> suspend(@PathVariable Long id) {
        UavPilot pilot = pilotService.getById(id);
        if (pilot != null) {
            pilot.setStatus("SUSPENDED");
            pilotService.updateById(pilot);
        }
        return R.ok(pilot);
    }

    @PutMapping("/{id}/reactivate")
    public R<UavPilot> reactivate(@PathVariable Long id) {
        UavPilot pilot = pilotService.getById(id);
        if (pilot != null) {
            pilot.setStatus("ACTIVE");
            pilotService.updateById(pilot);
        }
        return R.ok(pilot);
    }

    // ===== 体检记录（手动录入 + 体检中心同步） =====
    @PostMapping("/{pilotId}/medical")
    public R<UavPilotMedical> uploadMedical(@PathVariable Long pilotId, @Valid @RequestBody UavPilotMedical medical) {
        medical.setPilotId(pilotId);
        if (medical.getSource() == null) medical.setSource("MANUAL");
        medicalService.save(medical);
        return R.ok(medical);
    }

    @GetMapping("/{pilotId}/medical/list")
    public R<List<UavPilotMedical>> listMedical(@PathVariable Long pilotId) {
        return R.ok(medicalService.lambdaQuery()
                .eq(UavPilotMedical::getPilotId, pilotId)
                .orderByDesc(UavPilotMedical::getExamDate).list());
    }

    /** 体检有效性核验（供 uav-flight-plan 放行检查与前端展示）。无体检记录时不阻断（fail-open），过期/不合格才拒绝。 */
    @GetMapping("/{pilotId}/medical/valid")
    public R<Map<String, Object>> medicalValid(@PathVariable Long pilotId) {
        UavPilot pilot = pilotService.getById(pilotId);
        if (pilot == null) return R.fail("飞手不存在");
        List<UavPilotMedical> records = medicalService.lambdaQuery()
                .eq(UavPilotMedical::getPilotId, pilotId)
                .orderByDesc(UavPilotMedical::getExamDate)
                .orderByDesc(UavPilotMedical::getId)
                .list();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pilotId", pilotId);
        result.put("pilotName", pilot.getPilotName());
        if (records.isEmpty()) {
            result.put("valid", true);
            result.put("state", "NO_RECORD");
            result.put("reason", "暂无体检记录（体检中心未采集），不阻断放行");
            return R.ok(result);
        }
        UavPilotMedical latest = records.get(0);
        result.put("examDate", latest.getExamDate());
        result.put("examOrg", latest.getExamOrg());
        result.put("examResult", latest.getExamResult());
        result.put("expireDate", latest.getExpireDate());
        if ("FAIL".equalsIgnoreCase(latest.getExamResult())) {
            result.put("valid", false);
            result.put("state", "FAILED");
            result.put("reason", "飞手 " + pilot.getPilotName() + " 最近一次体检结论为不合格");
            return R.ok(result);
        }
        LocalDateTime expire = latest.getExpireDate();
        if (expire == null) {
            result.put("valid", true);
            result.put("state", "NO_EXPIRE");
            result.put("reason", "体检结论合格，未维护有效期");
            return R.ok(result);
        }
        if (expire.isBefore(LocalDateTime.now())) {
            result.put("valid", false);
            result.put("state", "EXPIRED");
            result.put("reason", "飞手 " + pilot.getPilotName() + " 体检证已于 " + expire.toLocalDate() + " 过期");
            return R.ok(result);
        }
        boolean expiringSoon = expire.isBefore(LocalDateTime.now().plusDays(30));
        result.put("valid", true);
        result.put("state", expiringSoon ? "EXPIRING_SOON" : "VALID");
        result.put("reason", "体检有效期至 " + expire.toLocalDate() + (expiringSoon ? "（30 天内到期，请及时复检）" : ""));
        return R.ok(result);
    }

    // ===== 体检中心对接 =====
    /** 手动触发一次体检中心同步（定时任务每 5 分钟自动执行） */
    @PostMapping("/medical-center/sync")
    public R<Map<String, Object>> syncMedicalCenter() {
        return R.ok(medicalCenterSyncService.syncOnce());
    }

    @GetMapping("/medical-center/status")
    public R<Map<String, Object>> medicalCenterStatus() {
        Map<String, Object> status = new LinkedHashMap<>(medicalCenterSyncService.getLastSync());
        status.put("enabled", medicalCenterSyncService.isEnabled());
        return R.ok(status);
    }
}
