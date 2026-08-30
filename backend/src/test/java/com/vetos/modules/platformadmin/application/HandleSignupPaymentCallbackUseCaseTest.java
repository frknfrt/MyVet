package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.platformadmin.domain.exception.TenantSignupRequestNotFoundException;
import com.vetos.modules.tenant.domain.InviteEmailPort;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantSignupResult;
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
class HandleSignupPaymentCallbackUseCaseTest {

    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private TenantSignupRequestRepository tenantSignupRequestRepository;
    @Mock private PlanRepository planRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private StaffInviteRepository staffInviteRepository;
    @Mock private InviteEmailPort inviteEmailPort;
    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;

    private HandleSignupPaymentCallbackUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new HandleSignupPaymentCallbackUseCase(
            paymentGatewayPort, tenantSignupRequestRepository, planRepository, tenantAdminPort,
            staffInviteRepository, inviteEmailPort, platformInvoiceRepository, recordPlatformPaymentUseCase
        );
        ReflectionTestUtils.setField(useCase, "frontendBaseUrl", "http://localhost:5173");
    }

    @Test
    void should_createTenantAndInviteAndInvoice_when_paymentSucceeds() {
        LocalDate today = LocalDate.of(2026, 8, 30);
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", "+905551112233", "PRO"
        );
        ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        StaffInvite savedInvite = StaffInvite.create(tenantId, branchId, "ayse@example.com", "Ayse Yilmaz", StaffRole.ADMIN, null);
        PlatformInvoice savedInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("2000.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(savedInvoice, "id", UUID.randomUUID());

        when(paymentGatewayPort.retrieveCheckoutResult("tok-1"))
            .thenReturn(new CheckoutResult(true, request.getId().toString(), "pay_123"));
        when(tenantSignupRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.createTenantForPaidSignup(
            "Mutlu Pati", "-", "Mutlu Pati", "-", "-", "PRO", today.plusMonths(1)
        )).thenReturn(new TenantSignupResult(tenantId, branchId));
        when(staffInviteRepository.save(any(StaffInvite.class))).thenReturn(savedInvite);
        when(platformInvoiceRepository.save(any(PlatformInvoice.class))).thenReturn(savedInvoice);

        boolean result = useCase.execute("tok-1", today);

        assertThat(result).isTrue();
        verify(inviteEmailPort).sendInvite(eq(savedInvite), eq("Mutlu Pati"), eq("http://localhost:5173/davet/" + savedInvite.getToken()));
        ArgumentCaptor<RecordPlatformPaymentCommand> captor = ArgumentCaptor.forClass(RecordPlatformPaymentCommand.class);
        verify(recordPlatformPaymentUseCase).execute(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("2000.00");
        assertThat(captor.getValue().method()).isEqualTo(PlatformPaymentMethod.CARD_ONLINE);
        assertThat(captor.getValue().recordedByAdminId()).isNull();
        assertThat(request.isCompleted()).isTrue();
    }

    @Test
    void should_returnFalse_when_paymentFails() {
        when(paymentGatewayPort.retrieveCheckoutResult("tok-2")).thenReturn(new CheckoutResult(false, null, null));

        boolean result = useCase.execute("tok-2", LocalDate.of(2026, 8, 30));

        assertThat(result).isFalse();
        verifyNoInteractions(tenantSignupRequestRepository, tenantAdminPort, staffInviteRepository, platformInvoiceRepository);
    }

    @Test
    void should_returnTrue_when_requestAlreadyCompleted_repeatedCallback() {
        TenantSignupRequest request = TenantSignupRequest.create("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO");
        ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
        request.complete();
        when(paymentGatewayPort.retrieveCheckoutResult("tok-3"))
            .thenReturn(new CheckoutResult(true, request.getId().toString(), "pay_123"));
        when(tenantSignupRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        boolean result = useCase.execute("tok-3", LocalDate.of(2026, 8, 30));

        assertThat(result).isTrue();
        verifyNoInteractions(tenantAdminPort, staffInviteRepository, platformInvoiceRepository);
    }

    @Test
    void should_throwNotFound_when_conversationIdDoesNotMatchAnyRequest() {
        UUID unknownId = UUID.randomUUID();
        when(paymentGatewayPort.retrieveCheckoutResult("tok-4"))
            .thenReturn(new CheckoutResult(true, unknownId.toString(), "pay_123"));
        when(tenantSignupRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("tok-4", LocalDate.of(2026, 8, 30)))
            .isInstanceOf(TenantSignupRequestNotFoundException.class);
    }
}
