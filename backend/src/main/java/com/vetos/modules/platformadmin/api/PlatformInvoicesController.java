package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.PlatformInvoiceResponse;
import com.vetos.modules.platformadmin.api.dto.RecordPlatformPaymentRequest;
import com.vetos.modules.platformadmin.application.ListPlatformInvoicesForTenantUseCase;
import com.vetos.modules.platformadmin.application.RecordPlatformPaymentUseCase;
import com.vetos.modules.platformadmin.application.VoidPlatformInvoiceUseCase;
import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform-admin/tenants/{tenantId}/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformInvoicesController {

    private final ListPlatformInvoicesForTenantUseCase listPlatformInvoicesForTenantUseCase;
    private final RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;
    private final VoidPlatformInvoiceUseCase voidPlatformInvoiceUseCase;

    @GetMapping
    public List<PlatformInvoiceResponse> list(@PathVariable UUID tenantId) {
        return listPlatformInvoicesForTenantUseCase.execute(tenantId).stream().map(PlatformInvoiceResponse::from).toList();
    }

    @PostMapping("/{invoiceId}/payments")
    public void recordPayment(
        @AuthenticationPrincipal AuthenticatedPlatformAdmin principal,
        @PathVariable UUID tenantId,
        @PathVariable UUID invoiceId,
        @RequestBody @Valid RecordPlatformPaymentRequest request
    ) {
        recordPlatformPaymentUseCase.execute(new RecordPlatformPaymentCommand(
            invoiceId, request.amount(), request.method(), request.paidAt(), request.notes(), principal.platformAdminId()
        ));
    }

    @PostMapping("/{invoiceId}/void")
    public void voidInvoice(@PathVariable UUID tenantId, @PathVariable UUID invoiceId) {
        voidPlatformInvoiceUseCase.execute(invoiceId);
    }
}
