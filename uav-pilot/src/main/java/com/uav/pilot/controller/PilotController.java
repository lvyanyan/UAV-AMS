package com.uav.pilot.controller;

import com.uav.common.base.R;
import com.uav.pilot.entity.UavPilot;
import com.uav.pilot.entity.UavPilotMedical;
import com.uav.pilot.service.UavPilotService;
import com.uav.pilot.service.UavPilotMedicalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pilot")
public class PilotController {

    private final UavPilotService pilotService;
    private final UavPilotMedicalService medicalService;

    public PilotController(UavPilotService pilotService, UavPilotMedicalService medicalService) {
        this.pilotService = pilotService;
        this.medicalService = medicalService;
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

    // ===== 体检记录（预留，手动上传） =====
    @PostMapping("/{pilotId}/medical")
    public R<UavPilotMedical> uploadMedical(@PathVariable Long pilotId, @Valid @RequestBody UavPilotMedical medical) {
        medical.setPilotId(pilotId);
        medicalService.save(medical);
        return R.ok(medical);
    }

    @GetMapping("/{pilotId}/medical/list")
    public R<List<UavPilotMedical>> listMedical(@PathVariable Long pilotId) {
        return R.ok(medicalService.lambdaQuery()
                .eq(UavPilotMedical::getPilotId, pilotId)
                .orderByDesc(UavPilotMedical::getExamDate).list());
    }
}
