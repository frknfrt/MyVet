package com.vetos.modules.integration.tarbil.api;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.api.dto.DismissRequest;
import com.vetos.modules.integration.tarbil.api.dto.ExtensionProfileResponse;
import com.vetos.modules.integration.tarbil.api.dto.LearnMappingRequest;
import com.vetos.modules.integration.tarbil.api.dto.MarkSubmittedRequest;
import com.vetos.modules.integration.tarbil.api.dto.PairRequest;
import com.vetos.modules.integration.tarbil.api.dto.PairResponse;
import com.vetos.modules.integration.tarbil.api.dto.StockSnapshotRequest;
import com.vetos.modules.integration.tarbil.api.dto.StockSnapshotResponse;
import com.vetos.modules.integration.tarbil.api.dto.TarbilSubmissionResponse;
import com.vetos.modules.integration.tarbil.application.DismissSubmissionUseCase;
import com.vetos.modules.integration.tarbil.application.GetExtensionProfileUseCase;
import com.vetos.modules.integration.tarbil.application.GetSubmissionUseCase;
import com.vetos.modules.integration.tarbil.application.LearnTarbilMappingUseCase;
import com.vetos.modules.integration.tarbil.application.ListPendingSubmissionsUseCase;
import com.vetos.modules.integration.tarbil.application.MarkSubmittedUseCase;
import com.vetos.modules.integration.tarbil.application.PairExtensionUseCase;
import com.vetos.modules.integration.tarbil.application.RecordStockSnapshotUseCase;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Yalnizca eklenti anahtariyla (TarbilExtensionSecurityConfig) erisilir; /pair herkese acik ve hiz sinirli. */
@RestController
@RequestMapping("/api/v1/tarbil-extension")
@RequiredArgsConstructor
public class TarbilExtensionController {

    private final PairExtensionUseCase pairExtensionUseCase;
    private final GetExtensionProfileUseCase getExtensionProfileUseCase;
    private final ListPendingSubmissionsUseCase listPendingSubmissionsUseCase;
    private final GetSubmissionUseCase getSubmissionUseCase;
    private final MarkSubmittedUseCase markSubmittedUseCase;
    private final DismissSubmissionUseCase dismissSubmissionUseCase;
    private final LearnTarbilMappingUseCase learnTarbilMappingUseCase;
    private final RecordStockSnapshotUseCase recordStockSnapshotUseCase;

    @PostMapping("/pair")
    public PairResponse pair(@Valid @RequestBody PairRequest request) {
        return new PairResponse(pairExtensionUseCase.execute(request.code(), request.label()));
    }

    @GetMapping("/me")
    public ExtensionProfileResponse me(@AuthenticationPrincipal AuthenticatedStaffUser user) {
        return ExtensionProfileResponse.from(getExtensionProfileUseCase.execute(TenantContext.current(), user.staffUserId()));
    }

    @GetMapping("/pending")
    public List<TarbilSubmissionResponse> pending(@RequestParam(name = "type", required = false) TarbilDocumentType type) {
        return listPendingSubmissionsUseCase.execute(TenantContext.current(), type).stream().map(TarbilSubmissionResponse::from).toList();
    }

    @GetMapping("/submissions/{id}")
    public TarbilSubmissionResponse submission(@PathVariable UUID id) {
        return TarbilSubmissionResponse.from(getSubmissionUseCase.byId(TenantContext.current(), id));
    }

    @GetMapping("/submissions/by-vaccination/{vaccinationRecordId}")
    public TarbilSubmissionResponse submissionByVaccination(@PathVariable UUID vaccinationRecordId) {
        return TarbilSubmissionResponse.from(getSubmissionUseCase.byVaccination(TenantContext.current(), vaccinationRecordId));
    }

    @PostMapping("/submissions/{id}/submitted")
    public ResponseEntity<TarbilSubmissionResponse> submitted(@PathVariable UUID id, @Valid @RequestBody MarkSubmittedRequest request,
                                                              @AuthenticationPrincipal AuthenticatedStaffUser user) {
        // 204: kaydedildi ama asi Vetly'de artik gosterilemiyor (iptal edilmis).
        return markSubmittedUseCase.execute(TenantContext.current(), user.staffUserId(), id, request.method(), request.tarbilReference())
            .map(view -> ResponseEntity.ok(TarbilSubmissionResponse.from(view)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/submissions/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable UUID id, @Valid @RequestBody DismissRequest request,
                        @AuthenticationPrincipal AuthenticatedStaffUser user) {
        dismissSubmissionUseCase.execute(TenantContext.current(), user.staffUserId(), id, request.reason());
    }

    @PutMapping("/mappings/{kind}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void learnMapping(@PathVariable TarbilMappingKind kind, @Valid @RequestBody LearnMappingRequest request,
                             @AuthenticationPrincipal AuthenticatedStaffUser user) {
        learnTarbilMappingUseCase.execute(TenantContext.current(), user.staffUserId(), kind, request.key(), request.fields().toString());
    }

    @PostMapping("/stock-snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    public StockSnapshotResponse uploadStockSnapshot(@Valid @RequestBody StockSnapshotRequest request,
                                                     @AuthenticationPrincipal AuthenticatedStaffUser user) {
        return new StockSnapshotResponse(recordStockSnapshotUseCase.execute(
            TenantContext.current(), user.staffUserId(), request.system(), request.toInputs()));
    }
}
