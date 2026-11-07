package com.vetos.modules.consentform.api;

import com.vetos.modules.consentform.api.dto.CreateFormTemplateRequest;
import com.vetos.modules.consentform.api.dto.IdResponse;
import com.vetos.modules.consentform.api.dto.UpdateFormTemplateRequest;
import com.vetos.modules.consentform.application.*;
import com.vetos.modules.consentform.application.dto.FormSourceImageContent;
import com.vetos.modules.consentform.application.dto.FormTemplateDetail;
import com.vetos.modules.consentform.application.dto.FormTemplateSummary;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/form-templates")
@RequiredArgsConstructor
public class FormTemplatesController {

    /** Liste filtresi: ACTIVE (varsayilan) ya da arsivlenmisler dahil ALL. */
    public enum StatusFilter { ACTIVE, ALL }

    private final UploadFormSourceImageUseCase uploadFormSourceImageUseCase;
    private final GetFormSourceImageUseCase getFormSourceImageUseCase;
    private final CreateFormTemplateUseCase createFormTemplateUseCase;
    private final UpdateFormTemplateUseCase updateFormTemplateUseCase;
    private final ArchiveFormTemplateUseCase archiveFormTemplateUseCase;
    private final ListFormTemplatesUseCase listFormTemplatesUseCase;
    private final GetFormTemplateUseCase getFormTemplateUseCase;
    private final RenderBlankTemplatePdfUseCase renderBlankTemplatePdfUseCase;

    @PostMapping("/source-images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IdResponse> uploadSourceImage(
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        try {
            UUID id = uploadFormSourceImageUseCase.execute(principal.staffUserId(), file.getBytes());
            return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/source-images/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ByteArrayResource> getSourceImage(@PathVariable UUID id) {
        FormSourceImageContent image = getFormSourceImageUseCase.execute(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.contentType()))
            .header("Cache-Control", "private, max-age=3600")
            .body(new ByteArrayResource(image.content()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IdResponse> create(
        @RequestBody @Valid CreateFormTemplateRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        UUID id = createFormTemplateUseCase.execute(principal.staffUserId(), request.content(), request.sourceImageId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> update(
        @PathVariable UUID id,
        @RequestBody @Valid UpdateFormTemplateRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        updateFormTemplateUseCase.execute(principal.staffUserId(), id, request.content());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archive(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedStaffUser principal) {
        archiveFormTemplateUseCase.execute(principal.staffUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public List<FormTemplateSummary> list(@RequestParam(defaultValue = "ACTIVE") StatusFilter status) {
        return listFormTemplatesUseCase.execute(status == StatusFilter.ALL);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public FormTemplateDetail get(@PathVariable UUID id) {
        return getFormTemplateUseCase.execute(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        return ConsentPdfResponses.attachment(renderBlankTemplatePdfUseCase.execute(id));
    }
}
