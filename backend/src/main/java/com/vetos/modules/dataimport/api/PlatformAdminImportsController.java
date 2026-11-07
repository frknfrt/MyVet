package com.vetos.modules.dataimport.api;

import com.vetos.modules.dataimport.api.dto.CommitImportRequest;
import com.vetos.modules.dataimport.api.dto.ImportBatchResponse;
import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.application.CreateImportPreviewUseCase;
import com.vetos.modules.dataimport.application.DiscardImportUseCase;
import com.vetos.modules.dataimport.application.GetImportBatchUseCase;
import com.vetos.modules.dataimport.application.ImportRollbackEligibility;
import com.vetos.modules.dataimport.application.ListImportBatchesUseCase;
import com.vetos.modules.dataimport.application.RollbackImportUseCase;
import com.vetos.modules.dataimport.application.RollbackResult;
import com.vetos.modules.dataimport.application.StartImportCommitUseCase;
import com.vetos.modules.dataimport.application.excel.ImportTemplateWriter;
import com.vetos.modules.dataimport.domain.exception.InvalidImportFileException;
import com.vetos.modules.dataimport.domain.exception.InvalidImportRequestException;
import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import com.vetos.platform.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/platform-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminImportsController {

    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CreateImportPreviewUseCase createImportPreviewUseCase;
    private final ListImportBatchesUseCase listImportBatchesUseCase;
    private final GetImportBatchUseCase getImportBatchUseCase;
    private final DiscardImportUseCase discardImportUseCase;
    private final StartImportCommitUseCase startImportCommitUseCase;
    private final RollbackImportUseCase rollbackImportUseCase;
    private final ImportRollbackEligibility rollbackEligibility;
    private final ImportTemplateWriter templateWriter;

    @PostMapping(path = "/tenants/{tenantId}/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportBatchResponse preview(
        @PathVariable UUID tenantId,
        @RequestParam("file") MultipartFile file,
        @RequestParam("sourceSystem") String sourceSystem,
        @AuthenticationPrincipal AuthenticatedPlatformAdmin admin
    ) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "aktarim.xlsx" : file.getOriginalFilename();
        // Yeni onizleme (PREVIEWED) geri alinamaz.
        return ImportBatchResponse.from(createImportPreviewUseCase.execute(
            tenantId, sourceSystem, fileName, file.getBytes(), admin.platformAdminId()), false);
    }

    @GetMapping("/tenants/{tenantId}/imports")
    public List<ImportBatchResponse> list(@PathVariable UUID tenantId) {
        List<ImportBatch> batches = listImportBatchesUseCase.execute(tenantId);
        Set<UUID> rollbackable = rollbackEligibility.rollbackableIds(tenantId, batches);
        return batches.stream().map(b -> ImportBatchResponse.from(b, rollbackable.contains(b.getId()))).toList();
    }

    @GetMapping("/tenants/{tenantId}/imports/{batchId}")
    public ImportBatchResponse get(@PathVariable UUID tenantId, @PathVariable UUID batchId) {
        ImportBatch batch = getImportBatchUseCase.execute(tenantId, batchId);
        return ImportBatchResponse.from(batch, rollbackEligibility.isRollbackable(batch));
    }

    @PostMapping("/tenants/{tenantId}/imports/{batchId}/discard")
    public ResponseEntity<Void> discard(@PathVariable UUID tenantId, @PathVariable UUID batchId) {
        getImportBatchUseCase.execute(tenantId, batchId);
        discardImportUseCase.execute(batchId);
        return ResponseEntity.noContent().build();
    }

    /** Aktarimi baslatir; is arka planda surer, ilerleme GET ile izlenir. */
    @PostMapping("/tenants/{tenantId}/imports/{batchId}/commit")
    public ResponseEntity<ImportBatchResponse> commit(
        @PathVariable UUID tenantId,
        @PathVariable UUID batchId,
        @RequestBody(required = false) CommitImportRequest body,
        @AuthenticationPrincipal AuthenticatedPlatformAdmin admin
    ) {
        List<String> rejected = body == null || body.rejectedMatchSourceIds() == null
            ? List.of() : body.rejectedMatchSourceIds();
        // Yeni baslayan aktarim (COMMITTING) geri alinamaz.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ImportBatchResponse.from(startImportCommitUseCase.execute(
            tenantId, batchId, admin.platformAdminId(), admin.email(), rejected), false));
    }

    /** Engel varsa da 200 doner (rolledBack=false + blockers); durum uygun degilse 409. */
    @PostMapping("/tenants/{tenantId}/imports/{batchId}/rollback")
    public RollbackResult rollback(
        @PathVariable UUID tenantId,
        @PathVariable UUID batchId,
        @AuthenticationPrincipal AuthenticatedPlatformAdmin admin
    ) {
        return rollbackImportUseCase.execute(tenantId, batchId, admin.platformAdminId(), admin.email());
    }

    @GetMapping("/imports/template.xlsx")
    public ResponseEntity<byte[]> template() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        templateWriter.write(out);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vetly-aktarim-sablonu.xlsx\"")
            .contentType(XLSX)
            .body(out.toByteArray());
    }

    /** Global isleyiciye dokunmadan (platform -> modul bagimliligi olmasin) bu denetleyiciye ozel 400. */
    @ExceptionHandler({InvalidImportFileException.class, InvalidImportRequestException.class})
    public ResponseEntity<ErrorResponse> handleBadInput(RuntimeException ex, HttpServletRequest request) {
        String code = ex instanceof InvalidImportFileException ? "INVALID_IMPORT_FILE" : "INVALID_IMPORT_REQUEST";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(code, ex.getMessage(), request.getRequestURI()));
    }
}
