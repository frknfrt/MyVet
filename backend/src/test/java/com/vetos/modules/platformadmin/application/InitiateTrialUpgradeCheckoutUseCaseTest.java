package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponDiscountType;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.TrialUpgradeRequest;
import com.vetos.modules.platformadmin.domain.TrialUpgradeRequestRepository;
import com.vetos.modules.platformadmin.domain.exception.AnnualPlanUnavailableException;
import com.vetos.modules.platformadmin.domain.exception.CouponNotRedeemableException;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import com.vetos.modules.platformadmin.domain.exception.SubscriptionNotOnTrialException;
import com.vetos.modules.tenant.domain.BillingPeriod;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InitiateTrialUpgradeCheckoutUseCaseTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID savedId = UUID.randomUUID();
    private final CheckoutSession session = new CheckoutSession("https://sandbox.iyzipay.com/pay/abc", "abc");

    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final TrialUpgradeRequestRepository upgradeRepository = mock(TrialUpgradeRequestRepository.class);
    private final TenantAdminPort tenantAdminPort = mock(TenantAdminPort.class);
    private final PaymentGatewayPort paymentGatewayPort = mock(PaymentGatewayPort.class);
    private final PlatformInvoiceRepository platformInvoiceRepository = mock(PlatformInvoiceRepository.class);

    private final InitiateTrialUpgradeCheckoutUseCase useCase = new InitiateTrialUpgradeCheckoutUseCase(
        new CheckoutPricing(planRepository, couponRepository), upgradeRepository, tenantAdminPort, paymentGatewayPort
    );

    @BeforeEach
    void setUp() {
        Plan pro = Plan.create("PRO", "Pro", new BigDecimal("1490.00"));
        pro.updateDetails("Pro", new BigDecimal("1490.00"), new BigDecimal("14900.00"), null, null, null, List.of(), Set.of());
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(planRepository.findByCode("BASIC")).thenReturn(Optional.of(Plan.create("BASIC", "Başlangıç", new BigDecimal("790.00"))));
        when(tenantAdminPort.findPlanCode(tenantId)).thenReturn(Optional.of("TRIAL"));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(new TenantAdminOverview(
            tenantId, "Pati Klinik", "-", TenantStatus.TRIAL, Instant.now(), "TRIAL", BillingStatus.TRIAL,
            LocalDate.now(), LocalDate.now().plusDays(7), 1, 1, null, null
        ));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.of("ayse@ornek.com"));
        when(upgradeRepository.save(any())).thenAnswer(invocation -> {
            TrialUpgradeRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", savedId);
            return request;
        });
        when(paymentGatewayPort.initializeCheckout(anyString(), any(), anyString(), anyString())).thenReturn(session);
    }

    private TrialUpgradeRequest savedRequest() {
        ArgumentCaptor<TrialUpgradeRequest> captor = ArgumentCaptor.forClass(TrialUpgradeRequest.class);
        verify(upgradeRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_createRequestAndReturnCheckoutUrl_when_tenantOnTrial() {
        CheckoutSession result = useCase.execute(tenantId, "PRO", BillingPeriod.MONTHLY, null);

        assertThat(result).isEqualTo(session);
        verify(paymentGatewayPort).initializeCheckout(savedId.toString(), new BigDecimal("1490.00"), "Pati Klinik", "ayse@ornek.com");
        TrialUpgradeRequest saved = savedRequest();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getPlanCode()).isEqualTo("PRO");
        assertThat(saved.getBillingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
        assertThat(saved.getChargedAmount()).isEqualByComparingTo("1490.00");
        assertThat(saved.isCompleted()).isFalse();
    }

    @Test
    void should_chargeAnnualPrice_when_periodIsAnnual() {
        useCase.execute(tenantId, "PRO", BillingPeriod.ANNUAL, null);

        assertThat(savedRequest().getChargedAmount()).isEqualByComparingTo("14900.00");
        assertThat(savedRequest().getBillingPeriod()).isEqualTo(BillingPeriod.ANNUAL);
    }

    @Test
    void should_defaultToMonthly_when_periodIsNull() {
        useCase.execute(tenantId, "PRO", null, null);

        assertThat(savedRequest().getBillingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
    }

    @Test
    void should_applyCoupon_when_couponRedeemable() {
        when(couponRepository.findByCode("HOSGELDIN")).thenReturn(Optional.of(
            Coupon.create("HOSGELDIN", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null)));

        useCase.execute(tenantId, "PRO", BillingPeriod.MONTHLY, " hosgeldin ");

        TrialUpgradeRequest saved = savedRequest();
        assertThat(saved.getChargedAmount()).isEqualByComparingTo("1341.00");
        assertThat(saved.getCouponCode()).isEqualTo("HOSGELDIN");
    }

    @Test
    void should_throwCouponNotRedeemable_when_couponUnknown() {
        when(couponRepository.findByCode("YOK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(tenantId, "PRO", BillingPeriod.MONTHLY, "yok"))
            .isInstanceOf(CouponNotRedeemableException.class);
        verify(upgradeRepository, never()).save(any());
    }

    @Test
    void should_throwAnnualUnavailable_when_planHasNoAnnualPrice() {
        assertThatThrownBy(() -> useCase.execute(tenantId, "BASIC", BillingPeriod.ANNUAL, null))
            .isInstanceOf(AnnualPlanUnavailableException.class);
    }

    @Test
    void should_throwNotOnTrial_when_tenantAlreadyPaid() {
        when(tenantAdminPort.findPlanCode(tenantId)).thenReturn(Optional.of("PRO"));

        assertThatThrownBy(() -> useCase.execute(tenantId, "PRO", BillingPeriod.MONTHLY, null))
            .isInstanceOf(SubscriptionNotOnTrialException.class);
        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    void should_throwNotOnTrial_when_tenantIsInternal() {
        when(tenantAdminPort.findPlanCode(tenantId)).thenReturn(Optional.of("INTERNAL"));

        assertThatThrownBy(() -> useCase.execute(tenantId, "PRO", BillingPeriod.MONTHLY, null))
            .isInstanceOf(SubscriptionNotOnTrialException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRIAL", "INTERNAL", "YOK"})
    void should_throwPlanNotFound_when_planCodeIsReservedOrUnknown(String planCode) {
        when(planRepository.findByCode("YOK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(tenantId, planCode, BillingPeriod.MONTHLY, null))
            .isInstanceOf(PlanNotFoundException.class);
        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    void should_throwPlanNotFound_when_planInactive() {
        Plan retired = Plan.create("ESKI", "Eski", new BigDecimal("100.00"));
        retired.deactivate();
        when(planRepository.findByCode("ESKI")).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> useCase.execute(tenantId, "ESKI", BillingPeriod.MONTHLY, null))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void should_notTouchInvoicesOrSubscription_when_checkoutInitiated() {
        useCase.execute(tenantId, "PRO", BillingPeriod.MONTHLY, null);

        verifyNoInteractions(platformInvoiceRepository);
        verify(tenantAdminPort, never()).updateSubscription(any(), any(), any(), any());
        verify(tenantAdminPort, never()).updateBillingStatus(any(), any());
    }
}
