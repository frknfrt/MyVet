package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.platformadmin.domain.exception.CouponNotRedeemableException;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import com.vetos.modules.platformadmin.domain.exception.SignupEmailAlreadyRegisteredConflictException;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitiateSignupCheckoutUseCaseTest {

    @Mock private PlanRepository planRepository;
    @Mock private TenantSignupRequestRepository tenantSignupRequestRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private CouponRepository couponRepository;

    private InitiateSignupCheckoutUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InitiateSignupCheckoutUseCase(
            planRepository, tenantSignupRequestRepository, tenantAdminPort, paymentGatewayPort, couponRepository
        );
    }

    @Test
    void should_initializeCheckout_when_planActiveAndEmailFree() {
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.isEmailRegistered("ayse@example.com")).thenReturn(false);
        when(tenantSignupRequestRepository.save(any(TenantSignupRequest.class))).thenAnswer(inv -> {
            TenantSignupRequest req = inv.getArgument(0);
            ReflectionTestUtils.setField(req, "id", UUID.randomUUID());
            return req;
        });
        CheckoutSession expectedSession = new CheckoutSession("https://sandbox.iyzipay.com/pay/abc", "abc");
        when(paymentGatewayPort.initializeCheckout(any(), eq(plan.getMonthlyPrice()), eq("Ayse Yilmaz"), eq("ayse@example.com")))
            .thenReturn(expectedSession);

        CheckoutSession result = useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", "+905551112233", "PRO", null);

        assertThat(result).isEqualTo(expectedSession);
        ArgumentCaptor<TenantSignupRequest> captor = ArgumentCaptor.forClass(TenantSignupRequest.class);
        verify(tenantSignupRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getClinicName()).isEqualTo("Mutlu Pati");
        assertThat(captor.getValue().getAdminEmail()).isEqualTo("ayse@example.com");
        assertThat(captor.getValue().getPlanCode()).isEqualTo("PRO");
        assertThat(captor.getValue().getChargedAmount()).isEqualByComparingTo("2000.00");
        assertThat(captor.getValue().getCouponCode()).isNull();
        verifyNoInteractions(couponRepository);
    }

    @Test
    void should_applyDiscount_when_validCouponCodeProvided() {
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.isEmailRegistered("ayse@example.com")).thenReturn(false);
        Coupon coupon = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);
        when(couponRepository.findByCode("WELCOME10")).thenReturn(Optional.of(coupon));
        when(tenantSignupRequestRepository.save(any(TenantSignupRequest.class))).thenAnswer(inv -> {
            TenantSignupRequest req = inv.getArgument(0);
            ReflectionTestUtils.setField(req, "id", UUID.randomUUID());
            return req;
        });
        CheckoutSession expectedSession = new CheckoutSession("https://sandbox.iyzipay.com/pay/abc", "abc");
        when(paymentGatewayPort.initializeCheckout(any(), eq(new BigDecimal("1800.00")), eq("Ayse Yilmaz"), eq("ayse@example.com")))
            .thenReturn(expectedSession);

        CheckoutSession result = useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO", "welcome10");

        assertThat(result).isEqualTo(expectedSession);
        ArgumentCaptor<TenantSignupRequest> captor = ArgumentCaptor.forClass(TenantSignupRequest.class);
        verify(tenantSignupRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getChargedAmount()).isEqualByComparingTo("1800.00");
        assertThat(captor.getValue().getCouponCode()).isEqualTo("WELCOME10");
    }

    @Test
    void should_throwCouponNotRedeemable_when_couponCodeUnknown() {
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.isEmailRegistered("ayse@example.com")).thenReturn(false);
        when(couponRepository.findByCode("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO", "ghost"))
            .isInstanceOf(CouponNotRedeemableException.class);

        verifyNoInteractions(tenantSignupRequestRepository, paymentGatewayPort);
    }

    @Test
    void should_throwCouponNotRedeemable_when_couponExpired() {
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.isEmailRegistered("ayse@example.com")).thenReturn(false);
        Coupon coupon = Coupon.create("OLD10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, LocalDate.now().minusDays(1));
        when(couponRepository.findByCode("OLD10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO", "old10"))
            .isInstanceOf(CouponNotRedeemableException.class);

        verifyNoInteractions(tenantSignupRequestRepository, paymentGatewayPort);
    }

    @Test
    void should_throwPlanNotFound_when_planDoesNotExist() {
        when(planRepository.findByCode("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "GHOST", null))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void should_throwPlanNotFound_when_planIsInactive() {
        Plan plan = Plan.create("OLD", "Eski Plan", new BigDecimal("500.00"));
        plan.deactivate();
        when(planRepository.findByCode("OLD")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "OLD", null))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void should_throwConflict_when_emailAlreadyRegistered() {
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.isEmailRegistered("ayse@example.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO", null))
            .isInstanceOf(SignupEmailAlreadyRegisteredConflictException.class);

        verifyNoInteractions(paymentGatewayPort);
        verifyNoInteractions(tenantSignupRequestRepository);
    }
}
