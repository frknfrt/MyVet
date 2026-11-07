package com.vetos.modules.consentform.api;

import com.vetos.modules.consentform.api.dto.CreateSignedFormRequest;
import com.vetos.modules.consentform.api.dto.IdResponse;
import com.vetos.modules.consentform.api.dto.VoidSignedFormRequest;
import com.vetos.modules.consentform.application.*;
import com.vetos.modules.consentform.application.dto.CreateSignedFormCommand;
import com.vetos.modules.consentform.application.dto.SignedFormDetail;
import com.vetos.modules.consentform.application.dto.SignedFormSummary;
import com.vetos.modules.consentform.application.dto.SignerInput;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Imzali formlar degistirilemez: PUT/PATCH/DELETE yoktur; yalniz iptal (void) edilebilir. */
@RestController
@RequestMapping("/api/v1/signed-forms")
@RequiredArgsConstructor
public class SignedFormsController {

    private final CreateSignedFormUseCase createSignedFormUseCase;
    private final ListSignedFormsUseCase listSignedFormsUseCase;
    private final GetSignedFormUseCase getSignedFormUseCase;
    private final GetSignatureImageUseCase getSignatureImageUseCase;
    private final VoidSignedFormUseCase voidSignedFormUseCase;
    private final RenderSignedFormPdfUseCase renderSignedFormPdfUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<IdResponse> create(
        @RequestBody @Valid CreateSignedFormRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        HttpServletRequest httpRequest
    ) {
        var s = request.signer();
        UUID id = createSignedFormUseCase.execute(principal.staffUserId(), new CreateSignedFormCommand(
            request.templateId(), request.patientId(), request.encounterId(), request.values(),
            new SignerInput(s.type(), s.name(), s.nationalId(), s.phone()),
            request.signaturePngBase64(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent")));
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public List<SignedFormSummary> list(@RequestParam(required = false) UUID patientId,
                                        @RequestParam(required = false) UUID ownerId) {
        return listSignedFormsUseCase.execute(patientId, ownerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public SignedFormDetail get(@PathVariable UUID id) {
        return getSignedFormUseCase.execute(id);
    }

    @GetMapping("/{id}/signature")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<ByteArrayResource> signature(@PathVariable UUID id) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header("Cache-Control", "private, max-age=3600")
            .body(new ByteArrayResource(getSignatureImageUseCase.execute(id)));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        return ConsentPdfResponses.attachment(renderSignedFormPdfUseCase.execute(id));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public ResponseEntity<Void> voidForm(
        @PathVariable UUID id, @RequestBody @Valid VoidSignedFormRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        voidSignedFormUseCase.execute(principal.staffUserId(), id, request.reason());
        return ResponseEntity.noContent().build();
    }
}
