package com.vetos.modules.encounter.api;

import com.vetos.modules.encounter.api.dto.RecordVaccinationRequest;
import com.vetos.modules.encounter.api.dto.VaccinationRecordResponse;
import com.vetos.modules.encounter.application.ListVaccinationsByPatientUseCase;
import com.vetos.modules.encounter.application.RecordVaccinationUseCase;
import com.vetos.modules.encounter.application.dto.RecordVaccinationCommand;
import com.vetos.platform.security.AuthenticatedStaffUser;
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
@PreAuthorize("hasAnyRole('VET', 'ADMIN')")
public class VaccinationRecordsController {

    private final RecordVaccinationUseCase recordVaccinationUseCase;
    private final ListVaccinationsByPatientUseCase listVaccinationsByPatientUseCase;

    @PostMapping
    public ResponseEntity<Void> record(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid RecordVaccinationRequest request
    ) {
        UUID id = recordVaccinationUseCase.execute(new RecordVaccinationCommand(
            request.patientId(), request.encounterId(), request.vaccineName(), request.lotNumber(),
            request.administeredDate(), request.nextDueDate(), principal.staffUserId()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/vaccination-records/" + id)).build();
    }

    @GetMapping
    public List<VaccinationRecordResponse> listByPatient(@RequestParam UUID patientId) {
        return listVaccinationsByPatientUseCase.execute(patientId).stream().map(VaccinationRecordResponse::from).toList();
    }
}
