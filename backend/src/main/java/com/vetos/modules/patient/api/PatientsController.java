package com.vetos.modules.patient.api;

import com.vetos.modules.patient.api.dto.*;
import com.vetos.modules.patient.application.*;
import com.vetos.modules.patient.application.dto.RegisterPatientCommand;
import com.vetos.modules.patient.application.dto.UpdatePatientCommand;
import com.vetos.modules.patient.application.dto.UpdatePatientIdentificationCommand;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientsController {

    private final RegisterPatientUseCase registerPatientUseCase;
    private final UpdatePatientUseCase updatePatientUseCase;
    private final GetPatientProfileUseCase getPatientProfileUseCase;
    private final SearchPatientsUseCase searchPatientsUseCase;
    private final MarkPatientDeceasedUseCase markPatientDeceasedUseCase;
    private final UpdatePatientIdentificationUseCase updatePatientIdentificationUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<PatientResponse> register(@RequestBody @Valid RegisterPatientRequest request) {
        UUID id = registerPatientUseCase.execute(new RegisterPatientCommand(
            request.ownerId(), request.speciesId(), request.breedId(), request.name(), request.sex(), request.birthDate()
        ));
        return ResponseEntity.status(201).body(new PatientResponse(id, request.name()));
    }

    @GetMapping("/{id}")
    public PatientProfileResponse get(@PathVariable UUID id) {
        return PatientProfileResponse.from(getPatientProfileUseCase.execute(id));
    }

    @GetMapping
    public List<PatientSearchResultResponse> search(@RequestParam(defaultValue = "") String query) {
        return searchPatientsUseCase.execute(TenantContext.current(), query).stream()
            .map(PatientSearchResultResponse::from)
            .toList();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public void update(@PathVariable UUID id, @RequestBody @Valid UpdatePatientRequest request) {
        updatePatientUseCase.execute(new UpdatePatientCommand(
            id, request.name(), request.breedId(), request.sex(), request.birthDate(), request.neutered()
        ));
    }

    @PostMapping("/{id}/deceased")
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public void markDeceased(@PathVariable UUID id) {
        markPatientDeceasedUseCase.execute(id);
    }

    @PutMapping("/{id}/identification")
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public void updateIdentification(@PathVariable UUID id, @RequestBody UpdatePatientIdentificationRequest request) {
        updatePatientIdentificationUseCase.execute(new UpdatePatientIdentificationCommand(
            id, request.microchipNumber(), request.tarbilAnimalId()
        ));
    }
}
