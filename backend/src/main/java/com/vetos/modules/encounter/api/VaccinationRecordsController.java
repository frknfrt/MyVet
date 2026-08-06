package com.vetos.modules.encounter.api;

import com.vetos.modules.encounter.api.dto.MarkVaccinationAdministeredRequest;
import com.vetos.modules.encounter.api.dto.RecordVaccinationRequest;
import com.vetos.modules.encounter.api.dto.VaccinationScheduleItemResponse;
import com.vetos.modules.encounter.application.CancelVaccinationUseCase;
import com.vetos.modules.encounter.application.ListVaccinationsByPatientUseCase;
import com.vetos.modules.encounter.application.ListVaccinationsUseCase;
import com.vetos.modules.encounter.application.MarkVaccinationAdministeredUseCase;
import com.vetos.modules.encounter.application.RecordVaccinationUseCase;
import com.vetos.modules.encounter.application.dto.RecordVaccinationCommand;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vaccination-records")
@RequiredArgsConstructor
public class VaccinationRecordsController {

    private final RecordVaccinationUseCase recordVaccinationUseCase;
    private final ListVaccinationsByPatientUseCase listVaccinationsByPatientUseCase;
    private final ListVaccinationsUseCase listVaccinationsUseCase;
    private final MarkVaccinationAdministeredUseCase markVaccinationAdministeredUseCase;
    private final CancelVaccinationUseCase cancelVaccinationUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public ResponseEntity<Void> record(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid RecordVaccinationRequest request
    ) {
        UUID id = recordVaccinationUseCase.execute(new RecordVaccinationCommand(
            TenantContext.current(), request.patientId(), request.encounterId(), request.vaccineName(), request.lotNumber(),
            request.administeredDate(), request.nextDueDate(), principal.staffUserId(), request.status(), request.notes()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/vaccination-records/" + id)).build();
    }

    @GetMapping
    public List<VaccinationScheduleItemResponse> list(@RequestParam(required = false) UUID patientId) {
        var items = patientId != null
            ? listVaccinationsByPatientUseCase.execute(patientId)
            : listVaccinationsUseCase.execute(TenantContext.current());
        return items.stream().map(VaccinationScheduleItemResponse::from).toList();
    }

    @PostMapping("/{id}/administer")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public void markAdministered(@PathVariable UUID id, @RequestBody(required = false) MarkVaccinationAdministeredRequest request) {
        markVaccinationAdministeredUseCase.execute(id, request != null ? request.administeredDate() : null);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public void cancel(@PathVariable UUID id) {
        cancelVaccinationUseCase.execute(id);
    }
}
