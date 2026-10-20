package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CreatePlatformTenantRequest;
import com.vetos.modules.platformadmin.api.dto.ImpersonationSessionResponse;
import com.vetos.modules.platformadmin.api.dto.TenantAdminOverviewResponse;
import com.vetos.modules.platformadmin.api.dto.SuspendTenantRequest;
import com.vetos.modules.platformadmin.api.dto.TenantIntegrationHealthResponse;
import com.vetos.modules.platformadmin.api.dto.UpdateTenantSubscriptionRequest;
import com.vetos.modules.platformadmin.application.ActivateTenantUseCase;
import com.vetos.modules.platformadmin.application.CreatePlatformTenantUseCase;
import com.vetos.modules.platformadmin.application.GetTenantAdminOverviewUseCase;
import com.vetos.modules.platformadmin.application.GetTenantIntegrationHealthUseCase;
import com.vetos.modules.platformadmin.application.ListTenantsForAdminUseCase;
import com.vetos.modules.platformadmin.application.StartImpersonationUseCase;
import com.vetos.modules.platformadmin.application.SuspendTenantUseCase;
import com.vetos.modules.platformadmin.application.UpdateTenantSubscriptionUseCase;
import com.vetos.modules.platformadmin.application.dto.CreatePlatformTenantCommand;
import com.vetos.modules.platformadmin.application.dto.UpdateTenantSubscriptionCommand;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditEventFilter;
import com.vetos.platform.audit.AuditEventPage;
import com.vetos.platform.audit.AuditEventQuery;
import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.quota.UsageSnapshot;
import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
    private final GetTenantIntegrationHealthUseCase getTenantIntegrationHealthUseCase;
    private final UsageQuotaPort usageQuotaPort;
    private final AuditEventQuery auditEventQuery;

    /** Kiraci Detayi -- "Klinik islem gecmisi" karti. */
    @GetMapping("/{id}/audit-events")
    public AuditEventPage auditEvents(
        @PathVariable UUID id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) AuditAction action,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        return auditEventQuery.list(id, new AuditEventFilter(from, to, action, actorId), page, size);
    }

    /** Tenant Detayi -- "Bu ayki kullanim" kutusu (spec 2026-10-15 §3.2). */
    @GetMapping("/{id}/usage")
    public UsageSnapshot usage(@PathVariable UUID id) {
        return usageQuotaPort.currentUsage(id);
    }

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

    /**
     * Tenant Detayi sayfasi -- "Entegrasyon Durumu" ozeti (bkz.
     * GetTenantIntegrationHealthUseCase). Global Sistem Sagligi panelindeki
     * detayli listelerin aksine sadece bu kiracinin basarisiz sayilarini doner.
     */
    @GetMapping("/{id}/integration-health")
    public TenantIntegrationHealthResponse integrationHealth(@PathVariable UUID id) {
        return TenantIntegrationHealthResponse.from(getTenantIntegrationHealthUseCase.execute(id));
    }
}
