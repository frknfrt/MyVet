package com.vetos.modules.patient.api;

import com.vetos.modules.patient.api.dto.*;
import com.vetos.modules.patient.application.*;
import com.vetos.modules.patient.application.dto.RegisterPatientCommand;
import com.vetos.modules.patient.application.dto.UpdatePatientCommand;
import com.vetos.modules.patient.application.dto.UpdatePatientIdentificationCommand;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
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
    private final GetPatientGrowthSummaryUseCase getPatientGrowthSummaryUseCase;
    private final UploadPatientPhotoUseCase uploadPatientPhotoUseCase;
    private final RemovePatientPhotoUseCase removePatientPhotoUseCase;
    private final GetPatientPhotoUseCase getPatientPhotoUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<PatientResponse> register(@RequestBody @Valid RegisterPatientRequest request) {
        UUID id = registerPatientUseCase.execute(new RegisterPatientCommand(
            request.ownerId(), request.speciesId(), request.breedId(), request.name(), request.sex(), request.birthDate(),
            request.color(), request.temperament(), request.distinguishingMarks(), request.aggressive(),
            request.bloodType(), request.foodBrand(), request.criticalAlert(), request.notes(),
            request.protocolNumber(), request.rabiesTag()
        ));
        return ResponseEntity.status(201).body(new PatientResponse(id, request.name()));
    }

    @GetMapping("/growth-summary")
    public PatientGrowthSummaryResponse growthSummary() {
        return PatientGrowthSummaryResponse.from(getPatientGrowthSummaryUseCase.execute(TenantContext.current()));
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
            id, request.name(), request.breedId(), request.sex(), request.birthDate(), request.neutered(),
            request.color(), request.temperament(), request.distinguishingMarks(), request.aggressive(),
            request.bloodType(), request.foodBrand(), request.criticalAlert(), request.notes(),
            request.protocolNumber(), request.rabiesTag()
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
            id, request.microchipNumber(), request.passportNumber()
        ));
    }

    @PutMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public PatientPhotoResponse uploadPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        try {
            return new PatientPhotoResponse(uploadPatientPhotoUseCase.execute(id, file.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DeleteMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<Void> removePhoto(@PathVariable UUID id) {
        removePatientPhotoUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    // URL ?v=<photoVersion> ile surumlenir; bu yuzden degismez onbellek verilir.
    @GetMapping("/{id}/photo")
    public ResponseEntity<ByteArrayResource> getPhoto(@PathVariable UUID id) {
        var photo = getPatientPhotoUseCase.execute(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(photo.contentType()))
            .header("Cache-Control", "private, max-age=31536000, immutable")
            .body(new ByteArrayResource(photo.content()));
    }
}
