package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CheckoutSessionResponse;
import com.vetos.modules.platformadmin.api.dto.InitiateTrialUpgradeCheckoutRequest;
import com.vetos.modules.platformadmin.application.InitiateTrialUpgradeCheckoutUseCase;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Denemeden ucretli pakete gecis (spec 2026-11-13 S4.4). TenantBillingController gibi normal
 * klinik oturumuyla (ADMIN) korunur; tenantId yalnizca TenantContext'ten alinir.
 * /api/v1/subscriptions altinda oldugu icin deneme suresi dolmusken de erisilebilir.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class TrialUpgradeController {

    private final InitiateTrialUpgradeCheckoutUseCase initiateTrialUpgradeCheckoutUseCase;

    @PostMapping("/upgrade-checkout")
    @PreAuthorize("hasRole('ADMIN')")
    public CheckoutSessionResponse upgradeCheckout(@RequestBody @Valid InitiateTrialUpgradeCheckoutRequest request) {
        CheckoutSession session = initiateTrialUpgradeCheckoutUseCase.execute(
            TenantContext.current(), request.planCode(), request.billingPeriod(), request.couponCode()
        );
        return new CheckoutSessionResponse(session.checkoutFormUrl());
    }
}
