package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CreatePlatformTenantRequest;
import com.vetos.modules.platformadmin.api.dto.ImpersonationSessionResponse;
import com.vetos.modules.platformadmin.api.dto.TenantAdminOverviewResponse;
import com.vetos.modules.platformadmin.api.dto.SuspendTenantRequest;
import com.vetos.modules.platformadmin.api.dto.UpdateTenantSubscriptionRequest;
import com.vetos.modules.platformadmin.application.ActivateTenantUseCase;
import com.vetos.modules.platformadmin.application.CreatePlatformTenantUseCase;
import com.vetos.modules.platformadmin.application.GetTenantAdminOverviewUseCase;
import com.vetos.modules.platformadmin.application.ListTenantsForAdminUseCase;
import com.vetos.modules.platformadmin.application.StartImpersonationUseCase;
import com.vetos.modules.platformadmin.application.SuspendTenantUseCase;
import com.vetos.modules.platformadmin.application.UpdateTenantSubscriptionUseCase;
import com.vetos.modules.platformadmin.application.dto.CreatePlatformTenantCommand;
import com.vetos.modules.platformadmin.application.dto.UpdateTenantSubscriptionCommand;
import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform-admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminTenantsController {

    private final ListTenantsForAdminUseCase listTenantsForAdminUseCase;
    private final GetTenantAdminOverviewUseCase getTenantAdminOverviewUseCase;
    private final CreatePlatformTenantUseCase createPlatformTenantUseCase;
    private final UpdateTenantSubscriptionUseCase updateTenantSubscriptionUseCase;
    private final SuspendTenantUseCase suspendTenantUseCase;
    private final ActivateTenantUseCase activateTenantUseCase;
    private final StartImpersonationUseCase startImpersonationUseCase;

    @GetMapping
    public List<TenantAdminOverviewResponse> list() {
        return listTenantsForAdminUseCase.execute().stream()
            .map(TenantAdminOverviewResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public TenantAdminOverviewResponse get(@PathVariable UUID id) {
        return TenantAdminOverviewResponse.from(getTenantAdminOverviewUseCase.execute(id));
    }

    @PostMapping
    public ResponseEntity<TenantAdminOverviewResponse> create(
        @RequestBody @Valid CreatePlatformTenantRequest request, @AuthenticationPrincipal AuthenticatedPlatformAdmin principal
    ) {
        UUID tenantId = createPlatformTenantUseCase.execute(new CreatePlatformTenantCommand(
            request.tenantName(), request.taxNumber(), request.branchName(), request.address(), request.city(),
            request.adminFullName(), request.adminEmail(), request.adminPassword()
        ), principal.platformAdminId(), principal.email());
        var overview = getTenantAdminOverviewUseCase.execute(tenantId);
        return ResponseEntity.status(201).body(TenantAdminOverviewResponse.from(overview));
    }

    @PutMapping("/{id}/subscription")
    public void updateSubscription(
        @PathVariable UUID id, @RequestBody @Valid UpdateTenantSubscriptionRequest request,
        @AuthenticationPrincipal AuthenticatedPlatformAdmin principal
    ) {
        updateTenantSubscriptionUseCase.execute(new UpdateTenantSubscriptionCommand(
            id, request.planCode(), request.billingStatus(), request.renewsAt()
        ), principal.platformAdminId(), principal.email());
    }

    @PostMapping("/{id}/suspend")
    public void suspend(
        @PathVariable UUID id, @RequestBody @Valid SuspendTenantRequest request,
        @AuthenticationPrincipal AuthenticatedPlatformAdmin principal
    ) {
        suspendTenantUseCase.execute(id, request.reason(), request.note(), principal.platformAdminId(), principal.email());
    }

    @PostMapping("/{id}/activate")
    public void activate(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedPlatformAdmin principal) {
        activateTenantUseCase.execute(id, principal.platformAdminId(), principal.email());
    }

    /**
     * Destek amacli: o kiracinin ADMIN'i YERINE gecen bir JWT uretir --
     * sifresi BILINMEDEN. Frontend, donen token/staffUserId/tenantId/
     * branchId/fullName/role ile normal kiraci oturumunu (myvet.session)
     * dolduruyor (bkz. StartImpersonationUseCase).
     */
    @PostMapping("/{id}/impersonate")
    public ImpersonationSessionResponse impersonate(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedPlatformAdmin principal) {
        return ImpersonationSessionResponse.from(
            startImpersonationUseCase.execute(id, principal.platformAdminId(), principal.email())
        );
    }
}
