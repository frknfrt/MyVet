package com.vetos.modules.encounter.api;

import com.vetos.modules.encounter.api.dto.*;
import com.vetos.modules.encounter.application.*;
import com.vetos.modules.encounter.application.dto.RecordVitalsCommand;
import com.vetos.modules.encounter.application.dto.StartEncounterCommand;
import com.vetos.modules.encounter.application.dto.UpdateEncounterSoapCommand;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * api-conventions.md rol matrisi: /encounters/** sadece VET ve ADMIN --
 * SOAP notlari klinik/hukuki hassasiyet tasidigi icin teknisyen/resepsiyon
 * bu uc noktalari hic gormez.
 */
@RestController
@RequestMapping("/api/v1/encounters")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('VET', 'ADMIN')")
public class EncountersController {

    private final StartEncounterUseCase startEncounterUseCase;
    private final UpdateEncounterSoapUseCase updateEncounterSoapUseCase;
    private final RecordVitalsUseCase recordVitalsUseCase;
    private final FinalizeEncounterUseCase finalizeEncounterUseCase;
    private final GetEncounterUseCase getEncounterUseCase;
    private final ListEncountersByPatientUseCase listEncountersByPatientUseCase;
    private final RecordInventoryUsageUseCase recordInventoryUsageUseCase;
    private final ListInventoryUsageUseCase listInventoryUsageUseCase;

    @PostMapping
    public ResponseEntity<Void> start(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid StartEncounterRequest request
    ) {
        UUID id = startEncounterUseCase.execute(new StartEncounterCommand(
            request.patientId(), principal.staffUserId(), request.appointmentId(), request.templateUsed()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/encounters/" + id)).build();
    }

    @GetMapping("/{id}")
    public EncounterResponse get(@PathVariable UUID id) {
        return EncounterResponse.from(getEncounterUseCase.execute(id));
    }

    @GetMapping
    public List<EncounterResponse> listByPatient(@RequestParam UUID patientId) {
        return listEncountersByPatientUseCase.execute(patientId).stream().map(EncounterResponse::from).toList();
    }

    @PutMapping("/{id}/soap")
    public void updateSoap(@PathVariable UUID id, @RequestBody UpdateSoapRequest request) {
        updateEncounterSoapUseCase.execute(new UpdateEncounterSoapCommand(
            id, request.subjective(), request.objective(), request.assessment(), request.plan()
        ));
    }

    @PutMapping("/{id}/vitals")
    public void updateVitals(@PathVariable UUID id, @RequestBody RecordVitalsRequest request) {
        recordVitalsUseCase.execute(new RecordVitalsCommand(
            id, request.weightKg(), request.temperatureC(), request.heartRate(), request.respiratoryRate()
        ));
    }

    @PostMapping("/{id}/finalize")
    public void finalize(@PathVariable UUID id) {
        finalizeEncounterUseCase.execute(id);
    }

    @PostMapping("/{id}/materials")
    public ResponseEntity<Void> recordMaterial(@PathVariable UUID id, @RequestBody @Valid RecordInventoryUsageRequest request) {
        UUID usageId = recordInventoryUsageUseCase.execute(id, request.inventoryItemId(), request.quantity());
        return ResponseEntity.created(java.net.URI.create("/api/v1/encounters/" + id + "/materials/" + usageId)).build();
    }

    @GetMapping("/{id}/materials")
    public List<InventoryUsageResponse> listMaterials(@PathVariable UUID id) {
        return listInventoryUsageUseCase.execute(id).stream().map(InventoryUsageResponse::from).toList();
    }
}
