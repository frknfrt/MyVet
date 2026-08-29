package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CreatePlatformTenantRequest;
import com.vetos.modules.platformadmin.api.dto.TenantAdminOverviewResponse;
import com.vetos.modules.platformadmin.api.dto.UpdateTenantSubscriptionRequest;
import com.vetos.modules.platformadmin.application.ActivateTenantUseCase;
import com.vetos.modules.platformadmin.application.CreatePlatformTenantUseCase;
import com.vetos.modules.platformadmin.application.GetTenantAdminOverviewUseCase;
import com.vetos.modules.platformadmin.application.ListTenantsForAdminUseCase;
import com.vetos.modules.platformadmin.application.SuspendTenantUseCase;
import com.vetos.modules.platformadmin.application.UpdateTenantSubscriptionUseCase;
import com.vetos.modules.platformadmin.application.dto.CreatePlatformTenantCommand;
import com.vetos.modules.platformadmin.application.dto.UpdateTenantSubscriptionCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ResponseEntity<TenantAdminOverviewResponse> create(@RequestBody @Valid CreatePlatformTenantRequest request) {
        UUID tenantId = createPlatformTenantUseCase.execute(new CreatePlatformTenantCommand(
            request.tenantName(), request.taxNumber(), request.branchName(), request.address(), request.city(),
            request.adminFullName(), request.adminEmail(), request.adminPassword()
        ));
        var overview = getTenantAdminOverviewUseCase.execute(tenantId);
        return ResponseEntity.status(201).body(TenantAdminOverviewResponse.from(overview));
    }

    @PutMapping("/{id}/subscription")
    public void updateSubscription(@PathVariable UUID id, @RequestBody @Valid UpdateTenantSubscriptionRequest request) {
        updateTenantSubscriptionUseCase.execute(new UpdateTenantSubscriptionCommand(
            id, request.planCode(), request.billingStatus(), request.renewsAt()
        ));
    }

    @PostMapping("/{id}/suspend")
    public void suspend(@PathVariable UUID id) {
        suspendTenantUseCase.execute(id);
    }

    @PostMapping("/{id}/activate")
    public void activate(@PathVariable UUID id) {
        activateTenantUseCase.execute(id);
    }
}
