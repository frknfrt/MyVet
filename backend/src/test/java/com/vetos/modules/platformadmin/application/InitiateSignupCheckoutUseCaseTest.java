package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.*;
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

    private InitiateSignupCheckoutUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InitiateSignupCheckoutUseCase(
            planRepository, tenantSignupRequestRepository, tenantAdminPort, paymentGatewayPort
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

        CheckoutSession result = useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", "+905551112233", "PRO");

        assertThat(result).isEqualTo(expectedSession);
        ArgumentCaptor<TenantSignupRequest> captor = ArgumentCaptor.forClass(TenantSignupRequest.class);
        verify(tenantSignupRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getClinicName()).isEqualTo("Mutlu Pati");
        assertThat(captor.getValue().getAdminEmail()).isEqualTo("ayse@example.com");
        assertThat(captor.getValue().getPlanCode()).isEqualTo("PRO");
    }

    @Test
    void should_throwPlanNotFound_when_planDoesNotExist() {
        when(planRepository.findByCode("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "GHOST"))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void should_throwPlanNotFound_when_planIsInactive() {
        Plan plan = Plan.create("OLD", "Eski Plan", new BigDecimal("500.00"));
        plan.deactivate();
        when(planRepository.findByCode("OLD")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "OLD"))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void should_throwConflict_when_emailAlreadyRegistered() {
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.isEmailRegistered("ayse@example.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO"))
            .isInstanceOf(SignupEmailAlreadyRegisteredConflictException.class);

        verifyNoInteractions(paymentGatewayPort);
        verifyNoInteractions(tenantSignupRequestRepository);
    }
}
