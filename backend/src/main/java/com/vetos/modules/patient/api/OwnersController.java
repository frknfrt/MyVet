package com.vetos.modules.patient.api;

import com.vetos.modules.patient.api.dto.*;
import com.vetos.modules.patient.application.*;
import com.vetos.modules.patient.application.dto.RecordConsentCommand;
import com.vetos.modules.patient.application.dto.RegisterOwnerCommand;
import com.vetos.modules.patient.application.dto.UpdateOwnerCommand;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners")
@RequiredArgsConstructor
public class OwnersController {

    private final RegisterOwnerUseCase registerOwnerUseCase;
    private final UpdateOwnerUseCase updateOwnerUseCase;
    private final GetOwnerProfileUseCase getOwnerProfileUseCase;
    private final SearchOwnersUseCase searchOwnersUseCase;
    private final RecordConsentUseCase recordConsentUseCase;
    private final RevokeConsentUseCase revokeConsentUseCase;
    private final ListConsentsUseCase listConsentsUseCase;

    @GetMapping
    public List<OwnerSearchResultResponse> search(@RequestParam(defaultValue = "") String query) {
        return searchOwnersUseCase.execute(TenantContext.current(), query).stream()
            .map(OwnerSearchResultResponse::from)
            .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<OwnerResponse> register(@RequestBody @Valid RegisterOwnerRequest request) {
        UUID id = registerOwnerUseCase.execute(new RegisterOwnerCommand(
            TenantContext.current(), request.fullName(), request.middleName(), request.phone(),
            request.secondaryPhone(), request.email(), request.address(), request.city(), request.district(),
            request.occupation(), request.referralSource(), request.clientDiscount(), request.criticalAlert(),
            request.notes(), request.marketingConsent(), request.smsConsent(), request.whatsappConsent(),
            request.notificationConsent(), request.protocolNumber()
        ));
        return ResponseEntity.status(201).body(new OwnerResponse(id, request.fullName()));
    }

    @GetMapping("/{id}")
    public OwnerProfileResponse get(@PathVariable UUID id) {
        return OwnerProfileResponse.from(getOwnerProfileUseCase.execute(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public void update(@PathVariable UUID id, @RequestBody @Valid UpdateOwnerRequest request) {
        updateOwnerUseCase.execute(new UpdateOwnerCommand(
            id, request.phone(), request.email(), request.address(), request.middleName(),
            request.secondaryPhone(), request.city(), request.district(), request.occupation(),
            request.referralSource(), request.clientDiscount(), request.criticalAlert(), request.notes(),
            request.smsConsent(), request.whatsappConsent(), request.notificationConsent(), request.protocolNumber()
        ));
    }

    @GetMapping("/{id}/consents")
    public List<ConsentRecordResponse> listConsents(@PathVariable UUID id) {
        return listConsentsUseCase.execute(id).stream().map(ConsentRecordResponse::from).toList();
    }

    @PostMapping("/{id}/consents")
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<Void> recordConsent(
        @PathVariable UUID id, @RequestBody @Valid RecordConsentRequest request, HttpServletRequest httpRequest
    ) {
        recordConsentUseCase.execute(new RecordConsentCommand(id, request.consentType(), httpRequest.getRemoteAddr()));
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/{id}/consents/{consentId}/revoke")
    @PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")
    public void revokeConsent(@PathVariable UUID id, @PathVariable UUID consentId) {
        revokeConsentUseCase.execute(consentId);
    }
}
