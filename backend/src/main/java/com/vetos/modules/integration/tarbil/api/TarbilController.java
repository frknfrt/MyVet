package com.vetos.modules.integration.tarbil.api;

import com.vetos.modules.integration.tarbil.application.ListTarbilDiseasesUseCase;
import com.vetos.modules.integration.tarbil.api.dto.TarbilDiseaseResponse;
import com.vetos.modules.integration.tarbil.api.dto.DismissRequest;
import com.vetos.modules.integration.tarbil.api.dto.ExtensionTokenResponse;
import com.vetos.modules.integration.tarbil.api.dto.PairingCodeResponse;
import com.vetos.modules.integration.tarbil.api.dto.TarbilMappingResponse;
import com.vetos.modules.integration.tarbil.api.dto.TarbilStatusResponse;
import com.vetos.modules.integration.tarbil.api.dto.TarbilSyncLogResponse;
import com.vetos.modules.integration.tarbil.application.CreatePairingCodeUseCase;
import com.vetos.modules.integration.tarbil.application.DeleteTarbilMappingUseCase;
import com.vetos.modules.integration.tarbil.application.DismissSubmissionUseCase;
import com.vetos.modules.integration.tarbil.application.GetTarbilStatusSummaryUseCase;
import com.vetos.modules.integration.tarbil.application.ListExtensionTokensUseCase;
import com.vetos.modules.integration.tarbil.application.ListTarbilMappingsUseCase;
import com.vetos.modules.integration.tarbil.application.ListTarbilSyncLogsUseCase;
import com.vetos.modules.integration.tarbil.application.RestoreSubmissionUseCase;
import com.vetos.modules.integration.tarbil.application.RevokeExtensionTokenUseCase;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** api-conventions.md rol matrisi: /tarbil/** -> ADMIN, VET (aktarimi hekimler yapar). */
@RestController
@RequestMapping("/api/v1/tarbil")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','VET')")
public class TarbilController {

    private final GetTarbilStatusSummaryUseCase getTarbilStatusSummaryUseCase;
    private final ListTarbilSyncLogsUseCase listTarbilSyncLogsUseCase;
    private final DismissSubmissionUseCase dismissSubmissionUseCase;
    private final RestoreSubmissionUseCase restoreSubmissionUseCase;
    private final CreatePairingCodeUseCase createPairingCodeUseCase;
    private final ListExtensionTokensUseCase listExtensionTokensUseCase;
    private final RevokeExtensionTokenUseCase revokeExtensionTokenUseCase;
    private final ListTarbilMappingsUseCase listTarbilMappingsUseCase;
    private final DeleteTarbilMappingUseCase deleteTarbilMappingUseCase;
    private final ListTarbilDiseasesUseCase listTarbilDiseasesUseCase;

    @GetMapping("/status")
    public TarbilStatusResponse status() {
        return TarbilStatusResponse.from(getTarbilStatusSummaryUseCase.execute(TenantContext.current()));
    }

    @GetMapping("/sync-logs")
    public List<TarbilSyncLogResponse> syncLogs() {
        return listTarbilSyncLogsUseCase.execute(TenantContext.current()).stream()
            .map(TarbilSyncLogResponse::from)
            .toList();
    }

    @PostMapping("/sync-logs/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable UUID id, @Valid @RequestBody DismissRequest request,
                        @AuthenticationPrincipal AuthenticatedStaffUser user) {
        dismissSubmissionUseCase.execute(TenantContext.current(), user.staffUserId(), id, request.reason());
    }

    @PostMapping("/sync-logs/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable UUID id) {
        restoreSubmissionUseCase.execute(TenantContext.current(), id);
    }

    @PostMapping("/extension/pairing-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public PairingCodeResponse createPairingCode(@AuthenticationPrincipal AuthenticatedStaffUser user) {
        return PairingCodeResponse.from(createPairingCodeUseCase.execute(TenantContext.current(), user.staffUserId()));
    }

    @GetMapping("/extension/tokens")
    public List<ExtensionTokenResponse> tokens(@AuthenticationPrincipal AuthenticatedStaffUser user) {
        return listExtensionTokensUseCase.execute(TenantContext.current(), user.staffUserId(), "ADMIN".equals(user.role()))
            .stream().map(ExtensionTokenResponse::from).toList();
    }

    @DeleteMapping("/extension/tokens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedStaffUser user) {
        revokeExtensionTokenUseCase.execute(TenantContext.current(), user.staffUserId(), "ADMIN".equals(user.role()), id);
    }

    @GetMapping("/diseases")
    public List<TarbilDiseaseResponse> diseases() {
        return listTarbilDiseasesUseCase.execute().stream().map(TarbilDiseaseResponse::from).toList();
    }

    @GetMapping("/mappings")
    public List<TarbilMappingResponse> mappings() {
        return listTarbilMappingsUseCase.execute(TenantContext.current()).stream().map(TarbilMappingResponse::from).toList();
    }

    @DeleteMapping("/mappings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMapping(@PathVariable UUID id) {
        deleteTarbilMappingUseCase.execute(TenantContext.current(), id);
    }
}
