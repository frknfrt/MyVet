package com.vetos.modules.imaging.api;

import com.vetos.modules.imaging.api.dto.*;
import com.vetos.modules.imaging.application.*;
import com.vetos.modules.imaging.application.dto.CompleteImagingRecordCommand;
import com.vetos.modules.imaging.application.dto.RequestImagingRecordCommand;
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
@RequestMapping("/api/v1/imaging-records")
@RequiredArgsConstructor
public class ImagingRecordsController {

    private final RequestImagingRecordUseCase requestImagingRecordUseCase;
    private final CompleteImagingRecordUseCase completeImagingRecordUseCase;
    private final CancelImagingRecordUseCase cancelImagingRecordUseCase;
    private final ListImagingRecordsUseCase listImagingRecordsUseCase;
    private final ListImagingRecordsByPatientUseCase listImagingRecordsByPatientUseCase;
    private final GetImagingRecordUseCase getImagingRecordUseCase;
    private final UploadImagingRecordFileUseCase uploadImagingRecordFileUseCase;
    private final DownloadImagingRecordFileUseCase downloadImagingRecordFileUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public ResponseEntity<ImagingRecordResponse> request(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid RequestImagingRecordRequest request
    ) {
        UUID id = requestImagingRecordUseCase.execute(new RequestImagingRecordCommand(
            TenantContext.current(), request.patientId(), principal.staffUserId(),
            request.modality(), request.bodyRegion(), request.notes()
        ));
        return ResponseEntity.status(201).body(new ImagingRecordResponse(id, request.modality()));
    }

    @GetMapping
    public List<ImagingRecordSummaryResponse> list(@RequestParam(required = false) UUID patientId) {
        var results = patientId != null
            ? listImagingRecordsByPatientUseCase.execute(patientId)
            : listImagingRecordsUseCase.execute(TenantContext.current());
        return results.stream().map(ImagingRecordSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ImagingRecordDetailResponse get(@PathVariable UUID id) {
        return ImagingRecordDetailResponse.from(getImagingRecordUseCase.execute(id));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public void complete(@PathVariable UUID id, @RequestBody @Valid CompleteImagingRecordRequest request) {
        completeImagingRecordUseCase.execute(new CompleteImagingRecordCommand(id, request.findings()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public void cancel(@PathVariable UUID id) {
        cancelImagingRecordUseCase.execute(id);
    }

    @PostMapping("/{id}/files")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public ResponseEntity<Void> uploadFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        try {
            UUID fileId = uploadImagingRecordFileUseCase.execute(
                id, file.getOriginalFilename(), file.getContentType(), file.getBytes()
            );
            return ResponseEntity.created(java.net.URI.create("/api/v1/imaging-records/files/" + fileId)).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable UUID fileId) {
        var file = downloadImagingRecordFileUseCase.execute(fileId);
        MediaType mediaType = file.contentType() != null
            ? MediaType.parseMediaType(file.contentType())
            : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.fileName()).build().toString())
            .body(new ByteArrayResource(file.content()));
    }
}
