package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.CheckoutPricing.PricedCheckout;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.TrialUpgradeRequest;
import com.vetos.modules.platformadmin.domain.TrialUpgradeRequestRepository;
import com.vetos.modules.platformadmin.domain.exception.SubscriptionNotOnTrialException;
import com.vetos.modules.tenant.domain.BillingPeriod;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.subscription.ReservedPlanCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Deneme surumundeki klinigin sectigi paket icin odemeyi baslatir. Bu adimda fatura
 * kesilmez ve abonelik degismez -- ikisi de odeme onaylaninca
 * (HandleTrialUpgradePaymentCallbackUseCase) yapilir.
 */
@Service
@RequiredArgsConstructor
public class InitiateTrialUpgradeCheckoutUseCase {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final String FALLBACK_BUYER_EMAIL = "destek@myvet.app";

    private final CheckoutPricing checkoutPricing;
    private final TrialUpgradeRequestRepository trialUpgradeRequestRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PaymentGatewayPort paymentGatewayPort;

    @Transactional
    public CheckoutSession execute(UUID tenantId, String planCode, BillingPeriod billingPeriod, String couponCode) {
        String currentPlan = tenantAdminPort.findPlanCode(tenantId).orElse(null);
        if (!ReservedPlanCodes.TRIAL.equals(currentPlan)) {
            throw new SubscriptionNotOnTrialException();
        }
        PricedCheckout priced = checkoutPricing.price(planCode, billingPeriod, couponCode, LocalDate.now(ISTANBUL));

        TrialUpgradeRequest request = trialUpgradeRequestRepository.save(TrialUpgradeRequest.create(
            tenantId, priced.plan().getCode(), priced.billingPeriod(), priced.normalizedCouponCode(), priced.chargedAmount()
        ));

        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantId);
        String buyerEmail = tenantAdminPort.findBillingContactEmail(tenantId).orElse(FALLBACK_BUYER_EMAIL);
        return paymentGatewayPort.initializeCheckout(
            request.getId().toString(), priced.chargedAmount(), overview.name(), buyerEmail
        );
    }
}
