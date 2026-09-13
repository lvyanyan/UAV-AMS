package com.uav.registry.controller;

import com.uav.common.base.R;
import com.uav.registry.entity.UavOwner;
import com.uav.registry.entity.UavRegistration;
import com.uav.registry.service.UavOwnerService;
import com.uav.registry.service.UavRegistrationService;
import com.uav.registry.service.UomReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registry")
public class RegistryController {

    private final UavOwnerService ownerService;
    private final UavRegistrationService regService;
    private final UomReportService uomReportService;

    public RegistryController(UavOwnerService ownerService, UavRegistrationService regService,
                              UomReportService uomReportService) {
        this.ownerService = ownerService;
        this.regService = regService;
        this.uomReportService = uomReportService;
    }

    // ===== 所有人登记 =====
    @PostMapping("/owner")
    public R<UavOwner> registerOwner(@Valid @RequestBody UavOwner owner) {
        owner.setRegisterStatus("PENDING");
        ownerService.save(owner);
        return R.ok(owner);
    }

    @GetMapping("/owner/{id}")
    public R<UavOwner> getOwner(@PathVariable Long id) {
        return R.ok(ownerService.getById(id));
    }

    @GetMapping("/owner/list")
    public R<List<UavOwner>> listOwners() {
        return R.ok(ownerService.list());
    }

    @PutMapping("/owner/{id}/approve")
    public R<UavOwner> approveOwner(@PathVariable Long id) {
        UavOwner owner = ownerService.getById(id);
        if (owner != null) {
            owner.setRegisterStatus("APPROVED");
            ownerService.updateById(owner);
            // 审批通过 → 自动上报 UOM（内部异常不阻断审批）
            uomReportService.reportOwnerAsync(owner);
            owner = ownerService.getById(id);
        }
        return R.ok(owner);
    }

    @PutMapping("/owner/{id}/reject")
    public R<UavOwner> rejectOwner(@PathVariable Long id) {
        UavOwner owner = ownerService.getById(id);
        if (owner != null) {
            owner.setRegisterStatus("REJECTED");
            ownerService.updateById(owner);
        }
        return R.ok(owner);
    }

    // ===== 无人机登记 =====
    @PostMapping("/drone")
    public R<UavRegistration> registerDrone(@Valid @RequestBody UavRegistration reg) {
        reg.setRegisterStatus("PENDING");
        regService.save(reg);
        return R.ok(reg);
    }

    @GetMapping("/drone/{id}")
    public R<UavRegistration> getDrone(@PathVariable Long id) {
        return R.ok(regService.getById(id));
    }

    /** 按 SN 查询实名登记（供 uav-flight-plan 放行检查核验 register_status） */
    @GetMapping("/drones/by-sn/{sn}")
    public R<UavRegistration> getDroneBySn(@PathVariable String sn) {
        List<UavRegistration> list = regService.lambdaQuery()
                .eq(UavRegistration::getDroneSn, sn)
                .orderByDesc(UavRegistration::getId)
                .last("limit 1")
                .list();
        return R.ok(list.isEmpty() ? null : list.get(0));
    }

    @GetMapping("/drone/list")
    public R<List<UavRegistration>> listDrones() {
        return R.ok(regService.list());
    }

    @GetMapping("/drone/by-owner/{ownerId}")
    public R<List<UavRegistration>> listDronesByOwner(@PathVariable Long ownerId) {
        return R.ok(regService.lambdaQuery()
                .eq(UavRegistration::getOwnerId, ownerId).list());
    }

    @PutMapping("/drone/{id}/approve")
    public R<UavRegistration> approveDrone(@PathVariable Long id) {
        UavRegistration reg = regService.getById(id);
        if (reg != null) {
            reg.setRegisterStatus("APPROVED");
            regService.updateById(reg);
            // 审批通过 → 自动上报 UOM（内部异常不阻断审批）
            uomReportService.reportDroneAsync(reg);
            reg = regService.getById(id);
        }
        return R.ok(reg);
    }

    @PutMapping("/drone/{id}/reject")
    public R<UavRegistration> rejectDrone(@PathVariable Long id) {
        UavRegistration reg = regService.getById(id);
        if (reg != null) {
            reg.setRegisterStatus("REJECTED");
            regService.updateById(reg);
        }
        return R.ok(reg);
    }

    // ===== UOM 对接（民航局无人驾驶航空器一体化综合监管服务平台）=====

    /** 手动上报单条所有人登记 */
    @PutMapping("/owner/{id}/uom-report")
    public R<UavOwner> uomReportOwner(@PathVariable Long id) {
        UavOwner owner = ownerService.getById(id);
        if (owner == null) return R.fail("所有人登记不存在");
        if (!"APPROVED".equals(owner.getRegisterStatus())) return R.fail("仅审批通过的登记可上报 UOM");
        uomReportService.reportOwnerAsync(owner);
        return R.ok(ownerService.getById(id));
    }

    /** 手动上报单条无人机登记 */
    @PutMapping("/drone/{id}/uom-report")
    public R<UavRegistration> uomReportDrone(@PathVariable Long id) {
        UavRegistration reg = regService.getById(id);
        if (reg == null) return R.fail("无人机登记不存在");
        if (!"APPROVED".equals(reg.getRegisterStatus())) return R.fail("仅审批通过的登记可上报 UOM");
        uomReportService.reportDroneAsync(reg);
        return R.ok(regService.getById(id));
    }

    /** 批量补报：所有已通过但未上报/失败的登记 */
    @PostMapping("/uom/report-all")
    public R<Map<String, Object>> uomReportAll() {
        return R.ok(uomReportService.reportAllPending());
    }

    /** 失败重试（定时任务每分钟自动执行，此为手动触发） */
    @PostMapping("/uom/retry")
    public R<Map<String, Object>> uomRetry() {
        return R.ok(uomReportService.retryFailed());
    }

    /** UOM 对接日志（最近 N 条） */
    @GetMapping("/uom/logs")
    public R<List<Map<String, Object>>> uomLogs(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(uomReportService.logs(limit));
    }
}
