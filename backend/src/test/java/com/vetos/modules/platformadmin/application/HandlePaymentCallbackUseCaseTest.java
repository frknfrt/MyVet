package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandlePaymentCallbackUseCaseTest {

    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;

    private HandlePaymentCallbackUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new HandlePaymentCallbackUseCase(paymentGatewayPort, platformInvoiceRepository, recordPlatformPaymentUseCase);
    }

    @Test
    void should_recordCardOnlinePayment_when_gatewayReportsSuccess() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(paymentGatewayPort.retrieveCheckoutResult("tok-1"))
            .thenReturn(new CheckoutResult(true, invoice.getId().toString(), "pay_123"));
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        boolean result = useCase.execute("tok-1", today);

        assertThat(result).isTrue();
        ArgumentCaptor<RecordPlatformPaymentCommand> captor = ArgumentCaptor.forClass(RecordPlatformPaymentCommand.class);
        verify(recordPlatformPaymentUseCase).execute(captor.capture());
        RecordPlatformPaymentCommand command = captor.getValue();
        assertThat(command.invoiceId()).isEqualTo(invoice.getId());
        assertThat(command.amount()).isEqualByComparingTo("500.00");
        assertThat(command.method()).isEqualTo(PlatformPaymentMethod.CARD_ONLINE);
        assertThat(command.recordedByAdminId()).isNull();
        assertThat(command.notes()).contains("pay_123");
    }

    @Test
    void should_returnFalse_when_gatewayReportsFailure() {
        when(paymentGatewayPort.retrieveCheckoutResult("tok-2"))
            .thenReturn(new CheckoutResult(false, null, null));

        boolean result = useCase.execute("tok-2", LocalDate.of(2026, 8, 30));

        assertThat(result).isFalse();
        verifyNoInteractions(recordPlatformPaymentUseCase);
    }

    @Test
    void should_returnTrue_when_invoiceAlreadyPaid_repeatedCallback() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        invoice.markPaid(); // Mark invoice as already paid (simulates duplicate callback scenario)
        when(paymentGatewayPort.retrieveCheckoutResult("tok-3"))
            .thenReturn(new CheckoutResult(true, invoice.getId().toString(), "pay_123"));
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        boolean result = useCase.execute("tok-3", today);

        assertThat(result).isTrue();
        verify(recordPlatformPaymentUseCase, never()).execute(any());
    }

    @Test
    void should_throwNotFound_when_conversationIdDoesNotMatchAnyInvoice() {
        UUID unknownId = UUID.randomUUID();
        when(paymentGatewayPort.retrieveCheckoutResult("tok-4"))
            .thenReturn(new CheckoutResult(true, unknownId.toString(), "pay_123"));
        when(platformInvoiceRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("tok-4", LocalDate.of(2026, 8, 30)))
            .isInstanceOf(PlatformInvoiceNotFoundException.class);
    }
}
