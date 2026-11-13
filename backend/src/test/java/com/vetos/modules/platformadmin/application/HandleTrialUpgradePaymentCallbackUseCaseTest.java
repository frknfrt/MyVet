package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponDiscountType;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;
import com.vetos.modules.platformadmin.domain.TrialUpgradeRequest;
import com.vetos.modules.platformadmin.domain.TrialUpgradeRequestRepository;
import com.vetos.modules.tenant.domain.BillingPeriod;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.subscription.SubscriptionAccessPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HandleTrialUpgradePaymentCallbackUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();

    private final PaymentGatewayPort paymentGatewayPort = mock(PaymentGatewayPort.class);
    private final TrialUpgradeRequestRepository upgradeRepository = mock(TrialUpgradeRequestRepository.class);
    private final TenantAdminPort tenantAdminPort = mock(TenantAdminPort.class);
    private final PlatformInvoiceRepository platformInvoiceRepository = mock(PlatformInvoiceRepository.class);
    private final RecordPlatformPaymentUseCase recordPlatformPaymentUseCase = mock(RecordPlatformPaymentUseCase.class);
    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final SubscriptionAccessPort subscriptionAccessPort = mock(SubscriptionAccessPort.class);

    private final HandleTrialUpgradePaymentCallbackUseCase useCase = new HandleTrialUpgradePaymentCallbackUseCase(
        paymentGatewayPort, upgradeRepository, tenantAdminPort, platformInvoiceRepository,
        recordPlatformPaymentUseCase, couponRepository, subscriptionAccessPort
    );

    @BeforeEach
    void setUp() {
        when(paymentGatewayPort.retrieveCheckoutResult("tok"))
            .thenReturn(new CheckoutResult(true, requestId.toString(), "pay_123"));
        when(platformInvoiceRepository.save(any())).thenAnswer(invocation -> {
            PlatformInvoice invoice = invocation.getArgument(0);
            ReflectionTestUtils.setField(invoice, "id", invoiceId);
            return invoice;
        });
    }

    private TrialUpgradeRequest pending(BillingPeriod period, String couponCode, String amount) {
        TrialUpgradeRequest request = TrialUpgradeRequest.create(tenantId, "PRO", period, couponCode, new BigDecimal(amount));
        ReflectionTestUtils.setField(request, "id", requestId);
        when(upgradeRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void should_activatePlanIssuePaidInvoiceAndEvictAccess_when_paymentSucceeds() {
        TrialUpgradeRequest request = pending(BillingPeriod.MONTHLY, null, "1490.00");

        boolean result = useCase.execute("tok", TODAY);

        assertThat(result).isTrue();
        verify(tenantAdminPort).activatePaidPlan(tenantId, "PRO", TODAY.plusMonths(1), BillingPeriod.MONTHLY);

        ArgumentCaptor<PlatformInvoice> invoice = ArgumentCaptor.forClass(PlatformInvoice.class);
        verify(platformInvoiceRepository).save(invoice.capture());
        assertThat(invoice.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(invoice.getValue().getPlanCode()).isEqualTo("PRO");
        assertThat(invoice.getValue().getAmount()).isEqualByComparingTo("1490.00");
        assertThat(invoice.getValue().getPeriodStart()).isEqualTo(TODAY);
        assertThat(invoice.getValue().getPeriodEnd()).isEqualTo(TODAY.plusMonths(1));

        ArgumentCaptor<RecordPlatformPaymentCommand> payment = ArgumentCaptor.forClass(RecordPlatformPaymentCommand.class);
        verify(recordPlatformPaymentUseCase).execute(payment.capture());
        assertThat(payment.getValue().invoiceId()).isEqualTo(invoiceId);
        assertThat(payment.getValue().amount()).isEqualByComparingTo("1490.00");
        assertThat(payment.getValue().method()).isEqualTo(PlatformPaymentMethod.CARD_ONLINE);
        assertThat(payment.getValue().notes()).contains("pay_123");
        assertThat(payment.getValue().recordedByAdminId()).isNull();

        assertThat(request.isCompleted()).isTrue();
        verify(upgradeRepository).save(request);
        verify(subscriptionAccessPort).evict(tenantId);
    }

    @Test
    void should_evictAccessAfterPaymentRecorded_when_paymentSucceeds() {
        pending(BillingPeriod.MONTHLY, null, "1490.00");

        useCase.execute("tok", TODAY);

        // Odeme kaydi (TenantBillingReconciler) aboneligi yeniden yazar; onbellek en son temizlenmeli.
        InOrder order = inOrder(recordPlatformPaymentUseCase, subscriptionAccessPort);
        order.verify(recordPlatformPaymentUseCase).execute(any(RecordPlatformPaymentCommand.class));
        order.verify(subscriptionAccessPort).evict(tenantId);
    }

    @Test
    void should_setAnnualRenewal_when_periodIsAnnual() {
        pending(BillingPeriod.ANNUAL, null, "14900.00");

        useCase.execute("tok", TODAY);

        verify(tenantAdminPort).activatePaidPlan(tenantId, "PRO", TODAY.plusMonths(12), BillingPeriod.ANNUAL);
    }

    @Test
    void should_changeNothing_when_paymentFails() {
        when(paymentGatewayPort.retrieveCheckoutResult("tok"))
            .thenReturn(new CheckoutResult(false, requestId.toString(), null));

        boolean result = useCase.execute("tok", TODAY);

        assertThat(result).isFalse();
        verifyNoInteractions(tenantAdminPort, platformInvoiceRepository, recordPlatformPaymentUseCase, subscriptionAccessPort, upgradeRepository);
    }

    @Test
    void should_doNothingAndReturnTrue_when_callbackRepeated() {
        TrialUpgradeRequest request = pending(BillingPeriod.MONTHLY, null, "1490.00");

        assertThat(useCase.execute("tok", TODAY)).isTrue();
        assertThat(useCase.execute("tok", TODAY)).isTrue();

        assertThat(request.isCompleted()).isTrue();
        verify(tenantAdminPort, times(1)).activatePaidPlan(any(), any(), any(), any());
        verify(platformInvoiceRepository, times(1)).save(any());
        verify(recordPlatformPaymentUseCase, times(1)).execute(any(RecordPlatformPaymentCommand.class));
    }

    @Test
    void should_incrementCouponRedemption_when_couponUsed() {
        pending(BillingPeriod.MONTHLY, "HOSGELDIN", "1341.00");
        Coupon coupon = Coupon.create("HOSGELDIN", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);
        when(couponRepository.findByCode("HOSGELDIN")).thenReturn(Optional.of(coupon));

        useCase.execute("tok", TODAY);

        assertThat(coupon.getRedemptionCount()).isEqualTo(1);
        verify(couponRepository).save(coupon);
    }

    @Test
    void should_notLookUpCoupon_when_noCouponUsed() {
        pending(BillingPeriod.MONTHLY, null, "1490.00");

        useCase.execute("tok", TODAY);

        verify(couponRepository, never()).findByCode(any());
    }
}
