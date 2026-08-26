package com.vetos.modules.encounter.api;

import com.vetos.modules.encounter.api.dto.*;
import com.vetos.modules.encounter.application.*;
import com.vetos.modules.encounter.application.dto.RecordVitalsCommand;
import com.vetos.modules.encounter.application.dto.StartEncounterCommand;
import com.vetos.modules.encounter.application.dto.UpdateEncounterSoapCommand;
import com.vetos.modules.encounter.application.dto.UpdatePhysicalExamCommand;
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
 * api-conventions.md rol matrisi: SOAP/vital/fizik muayene YAZMA ve muayeneyi
 * baslatma/tamamlama sadece VET ve ADMIN -- klinik/hukuki hassasiyet tasir.
 * OKUMA (gecmis SOAP, kullanilan malzeme listesi) TECHNICIAN'a da acik --
 * tedavi gorevi/asi uygulamasi icin tedavi planini gormesi gerekir (Faz 2
 * role-bazli yetkilendirme turu, kullanici onayiyla). RECEPTIONIST bu
 * modulun hicbir ucuna erisemez.
 */
@RestController
@RequestMapping("/api/v1/encounters")
@RequiredArgsConstructor
public class EncountersController {

    private final StartEncounterUseCase startEncounterUseCase;
    private final UpdateEncounterSoapUseCase updateEncounterSoapUseCase;
    private final RecordVitalsUseCase recordVitalsUseCase;
    private final UpdatePhysicalExamUseCase updatePhysicalExamUseCase;
    private final FinalizeEncounterUseCase finalizeEncounterUseCase;
    private final GetEncounterUseCase getEncounterUseCase;
    private final ListEncountersByPatientUseCase listEncountersByPatientUseCase;
    private final FindEncounterByAppointmentUseCase findEncounterByAppointmentUseCase;
    private final RecordInventoryUsageUseCase recordInventoryUsageUseCase;
    private final ListInventoryUsageUseCase listInventoryUsageUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public EncounterResponse get(@PathVariable UUID id) {
        return EncounterResponse.from(getEncounterUseCase.execute(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public List<EncounterResponse> listByPatient(@RequestParam UUID patientId) {
        return listEncountersByPatientUseCase.execute(patientId).stream().map(EncounterResponse::from).toList();
    }

    @GetMapping("/by-appointment/{appointmentId}")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public ResponseEntity<EncounterResponse> getByAppointment(@PathVariable UUID appointmentId) {
        return findEncounterByAppointmentUseCase.execute(appointmentId)
            .map(detail -> ResponseEntity.ok(EncounterResponse.from(detail)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/soap")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public void updateSoap(@PathVariable UUID id, @RequestBody UpdateSoapRequest request) {
        updateEncounterSoapUseCase.execute(new UpdateEncounterSoapCommand(
            id, request.subjective(), request.objective(), request.assessment(), request.plan(), request.aiGenerated()
        ));
    }

    @PutMapping("/{id}/vitals")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public void updateVitals(@PathVariable UUID id, @RequestBody RecordVitalsRequest request) {
        recordVitalsUseCase.execute(new RecordVitalsCommand(
            id, request.weightKg(), request.temperatureC(), request.heartRate(), request.respiratoryRate()
        ));
    }

    @PutMapping("/{id}/physical-exam")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public void updatePhysicalExam(@PathVariable UUID id, @RequestBody UpdatePhysicalExamRequest request) {
        updatePhysicalExamUseCase.execute(new UpdatePhysicalExamCommand(id, request.findings()));
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public void finalize(@PathVariable UUID id) {
        finalizeEncounterUseCase.execute(id);
    }

    @PostMapping("/{id}/materials")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public ResponseEntity<Void> recordMaterial(@PathVariable UUID id, @RequestBody @Valid RecordInventoryUsageRequest request) {
        UUID usageId = recordInventoryUsageUseCase.execute(id, request.inventoryItemId(), request.quantity());
        return ResponseEntity.created(java.net.URI.create("/api/v1/encounters/" + id + "/materials/" + usageId)).build();
    }

    @GetMapping("/{id}/materials")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public List<InventoryUsageResponse> listMaterials(@PathVariable UUID id) {
        return listInventoryUsageUseCase.execute(id).stream().map(InventoryUsageResponse::from).toList();
    }
}
