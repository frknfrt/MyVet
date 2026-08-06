package com.vetos.modules.lab.api;

import com.vetos.modules.lab.api.dto.*;
import com.vetos.modules.lab.application.*;
import com.vetos.modules.lab.application.dto.CompleteLabResultCommand;
import com.vetos.modules.lab.application.dto.LabResultItemInput;
import com.vetos.modules.lab.application.dto.RequestLabResultCommand;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/v1/lab-results")
@RequiredArgsConstructor
public class LabResultsController {

    private final RequestLabResultUseCase requestLabResultUseCase;
    private final CompleteLabResultUseCase completeLabResultUseCase;
    private final CancelLabResultUseCase cancelLabResultUseCase;
    private final ListLabResultsUseCase listLabResultsUseCase;
    private final ListLabResultsByPatientUseCase listLabResultsByPatientUseCase;
    private final GetLabResultUseCase getLabResultUseCase;
    private final UploadLabResultFileUseCase uploadLabResultFileUseCase;
    private final DownloadLabResultFileUseCase downloadLabResultFileUseCase;
    private final EvaluateLabResultItemsUseCase evaluateLabResultItemsUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public ResponseEntity<LabResultResponse> request(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid RequestLabResultRequest request
    ) {
        UUID id = requestLabResultUseCase.execute(new RequestLabResultCommand(
            TenantContext.current(), request.patientId(), principal.staffUserId(), request.testName(), request.notes()
        ));
        return ResponseEntity.status(201).body(new LabResultResponse(id, request.testName()));
    }

    @GetMapping
    public List<LabResultSummaryResponse> list(@RequestParam(required = false) UUID patientId) {
        var results = patientId != null
            ? listLabResultsByPatientUseCase.execute(patientId)
            : listLabResultsUseCase.execute(TenantContext.current());
        return results.stream().map(LabResultSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public LabResultDetailResponse get(@PathVariable UUID id) {
        return LabResultDetailResponse.from(getLabResultUseCase.execute(id));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public void complete(@PathVariable UUID id, @RequestBody @Valid CompleteLabResultRequest request) {
        List<LabResultItemInput> items = (request.items() == null ? List.<LabResultItemRequest>of() : request.items()).stream()
            .map(i -> new LabResultItemInput(i.parameterName(), i.value(), i.unit(), i.referenceRange(), i.flag()))
            .toList();
        completeLabResultUseCase.execute(new CompleteLabResultCommand(id, request.resultSummary(), items));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public EvaluateLabResultItemsResponse evaluate(@RequestBody @Valid EvaluateLabResultItemsRequest request) {
        List<LabResultItemInput> items = (request.items() == null ? List.<LabResultItemRequest>of() : request.items()).stream()
            .map(i -> new LabResultItemInput(i.parameterName(), i.value(), i.unit(), i.referenceRange(), i.flag()))
            .toList();
        return EvaluateLabResultItemsResponse.from(evaluateLabResultItemsUseCase.execute(items));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public void cancel(@PathVariable UUID id) {
        cancelLabResultUseCase.execute(id);
    }

    @PostMapping("/{id}/files")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public ResponseEntity<Void> uploadFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        try {
            UUID fileId = uploadLabResultFileUseCase.execute(
                id, file.getOriginalFilename(), file.getContentType(), file.getBytes()
            );
            return ResponseEntity.created(java.net.URI.create("/api/v1/lab-results/files/" + fileId)).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable UUID fileId) {
        var file = downloadLabResultFileUseCase.execute(fileId);
        MediaType mediaType = file.contentType() != null
            ? MediaType.parseMediaType(file.contentType())
            : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.fileName()).build().toString())
            .body(new ByteArrayResource(file.content()));
    }
}
