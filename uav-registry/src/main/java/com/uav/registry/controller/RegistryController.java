package com.uav.registry.controller;

import com.uav.common.base.R;
import com.uav.registry.entity.UavOwner;
import com.uav.registry.entity.UavRegistration;
import com.uav.registry.service.UavOwnerService;
import com.uav.registry.service.UavRegistrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/registry")
public class RegistryController {

    private final UavOwnerService ownerService;
    private final UavRegistrationService regService;

    public RegistryController(UavOwnerService ownerService, UavRegistrationService regService) {
        this.ownerService = ownerService;
        this.regService = regService;
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
}
