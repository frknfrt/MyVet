package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;
import com.vetos.modules.platformadmin.domain.TrialUpgradeRequest;
import com.vetos.modules.platformadmin.domain.TrialUpgradeRequestRepository;
import com.vetos.modules.platformadmin.domain.exception.TenantSignupRequestNotFoundException;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.subscription.SubscriptionAccessPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * iyzico callback'i, conversationId bir TrialUpgradeRequest'e aitse cagrilir (bkz.
 * PublicPaymentCallbackController): deneme surumundeki klinik sectigi pakete gecer. Fatura
 * burada, odeme basarili olduktan SONRA kesilir ve hemen odenmis isaretlenir.
 * Idempotent: istek zaten COMPLETED ise ikinci fatura / ikinci plan degisikligi olmaz.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HandleTrialUpgradePaymentCallbackUseCase {

    private final PaymentGatewayPort paymentGatewayPort;
    private final TrialUpgradeRequestRepository trialUpgradeRequestRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;
    private final CouponRepository couponRepository;
    private final SubscriptionAccessPort subscriptionAccessPort;

    @Transactional
    public boolean execute(String token, LocalDate today) {
        CheckoutResult result = paymentGatewayPort.retrieveCheckoutResult(token);
        if (!result.success() || result.conversationId() == null) {
            log.info("iyzico deneme-yukseltme odemesi basarisiz: conversationId={}", result.conversationId());
            return false;
        }

        UUID requestId = UUID.fromString(result.conversationId());
        TrialUpgradeRequest request = trialUpgradeRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new TenantSignupRequestNotFoundException(requestId));
        if (request.isCompleted()) {
            log.info("iyzico deneme-yukseltme callback'i tekrarlandi, zaten tamamlanmis: requestId={}", requestId);
            return true;
        }

        UUID tenantId = request.getTenantId();
        LocalDate renewsAt = request.getBillingPeriod().endDate(today);
        tenantAdminPort.activatePaidPlan(tenantId, request.getPlanCode(), renewsAt, request.getBillingPeriod());

        PlatformInvoice invoice = platformInvoiceRepository.save(
            PlatformInvoice.issue(tenantId, request.getPlanCode(), request.getChargedAmount(), today, renewsAt, today)
        );
        recordPlatformPaymentUseCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), request.getChargedAmount(), PlatformPaymentMethod.CARD_ONLINE,
            today, "iyzico odeme referansi: " + result.paymentId(), null
        ));

        if (request.getCouponCode() != null) {
            couponRepository.findByCode(request.getCouponCode()).ifPresent(coupon -> {
                coupon.incrementRedemption();
                couponRepository.save(coupon);
            });
        }

        request.complete();
        trialUpgradeRequestRepository.save(request);
        // En sonda: odeme kaydi (TenantBillingReconciler) aboneligi yeniden yazar; kilit hemen kalksin.
        subscriptionAccessPort.evict(tenantId);
        return true;
    }
}
