package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CheckoutSessionResponse;
import com.vetos.modules.platformadmin.api.dto.PlanResponse;
import com.vetos.modules.platformadmin.api.dto.PlatformInvoiceResponse;
import com.vetos.modules.platformadmin.api.dto.TenantBillingOverviewResponse;
import com.vetos.modules.platformadmin.application.InitiateInvoiceCheckoutUseCase;
import com.vetos.modules.platformadmin.application.ListPlansUseCase;
import com.vetos.modules.platformadmin.application.ListPlatformInvoicesForTenantUseCase;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.platform.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Kiracinin (klinigin) KENDI platform faturalarini goruntuleyebilmesi icin --
 * PlatformInvoicesController'dan (platform-admin, PLATFORM_ADMIN-only) farkli
 * olarak bu controller normal tenant JWT'siyle (ADMIN rolu) korunur ve
 * tenantId'yi istek govdesinden/URL'den DEGIL, TenantContext.current()'tan
 * alir -- baska bir kiracinin faturalarini asla gosteremez.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class TenantBillingController {

    private final ListPlatformInvoicesForTenantUseCase listPlatformInvoicesForTenantUseCase;
    private final ListPlansUseCase listPlansUseCase;
    private final InitiateInvoiceCheckoutUseCase initiateInvoiceCheckoutUseCase;
    private final String paymentInstructions;

    TenantBillingController(
        ListPlatformInvoicesForTenantUseCase listPlatformInvoicesForTenantUseCase,
        ListPlansUseCase listPlansUseCase,
        InitiateInvoiceCheckoutUseCase initiateInvoiceCheckoutUseCase,
        @Value("${platform-billing.payment-instructions}") String paymentInstructions
    ) {
        this.listPlatformInvoicesForTenantUseCase = listPlatformInvoicesForTenantUseCase;
        this.listPlansUseCase = listPlansUseCase;
        this.initiateInvoiceCheckoutUseCase = initiateInvoiceCheckoutUseCase;
        this.paymentInstructions = paymentInstructions;
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('ADMIN')")
    public TenantBillingOverviewResponse invoices() {
        var invoices = listPlatformInvoicesForTenantUseCase.execute(TenantContext.current()).stream()
            .map(PlatformInvoiceResponse::from)
            .toList();
        return new TenantBillingOverviewResponse(paymentInstructions, invoices);
    }

    /**
     * Plan katalogunu (aktif planlar) tenant'a gosterir -- abonelik panelinde
     * kendi planinin aciklama/ozellik/badge bilgilerini eslestirmek icin kullanilir.
     */
    @GetMapping("/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PlanResponse> plans() {
        return listPlansUseCase.execute().stream()
            .filter(Plan::isActive)
            .map(PlanResponse::from)
            .toList();
    }

    @PostMapping("/invoices/{invoiceId}/checkout")
    @PreAuthorize("hasRole('ADMIN')")
    public CheckoutSessionResponse checkout(@PathVariable UUID invoiceId) {
        CheckoutSession session = initiateInvoiceCheckoutUseCase.execute(TenantContext.current(), invoiceId);
        return new CheckoutSessionResponse(session.checkoutFormUrl());
    }
}
