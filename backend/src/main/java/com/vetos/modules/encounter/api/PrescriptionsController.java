package com.vetos.modules.encounter.api;

import com.vetos.modules.encounter.api.dto.IssuePrescriptionRequest;
import com.vetos.modules.encounter.api.dto.PrescriptionResponse;
import com.vetos.modules.encounter.application.CancelPrescriptionUseCase;
import com.vetos.modules.encounter.application.GetPrescriptionUseCase;
import com.vetos.modules.encounter.application.IssuePrescriptionUseCase;
import com.vetos.modules.encounter.application.ListPrescriptionsByPatientUseCase;
import com.vetos.modules.encounter.application.dto.IssuePrescriptionCommand;
import com.vetos.modules.encounter.application.dto.PrescriptionItemInput;
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
@RequestMapping("/api/v1/prescriptions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('VET', 'ADMIN')")
public class PrescriptionsController {

    private final IssuePrescriptionUseCase issuePrescriptionUseCase;
    private final GetPrescriptionUseCase getPrescriptionUseCase;
    private final ListPrescriptionsByPatientUseCase listPrescriptionsByPatientUseCase;
    private final CancelPrescriptionUseCase cancelPrescriptionUseCase;

    @PostMapping
    public ResponseEntity<Void> issue(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid IssuePrescriptionRequest request
    ) {
        UUID id = issuePrescriptionUseCase.execute(new IssuePrescriptionCommand(
            request.patientId(), request.encounterId(), principal.staffUserId(), request.controlledSubstance(),
            request.tarbilDiseaseId(),
            request.items().stream()
                .map(i -> new PrescriptionItemInput(i.drugId(), i.drugName(), i.dosage(), i.frequency(), i.durationDays(), i.route(),
                    i.inventoryItemId(), i.stockQuantity()))
                .toList()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/prescriptions/" + id)).build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public PrescriptionResponse get(@PathVariable UUID id) {
        return PrescriptionResponse.from(getPrescriptionUseCase.execute(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public List<PrescriptionResponse> listByPatient(@RequestParam UUID patientId) {
        return listPrescriptionsByPatientUseCase.execute(patientId).stream().map(PrescriptionResponse::from).toList();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        cancelPrescriptionUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}

