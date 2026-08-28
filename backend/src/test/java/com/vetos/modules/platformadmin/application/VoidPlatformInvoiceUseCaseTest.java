package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoidPlatformInvoiceUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;

    @Test
    void should_voidInvoice_when_statusIsIssued() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        new VoidPlatformInvoiceUseCase(platformInvoiceRepository).execute(invoice.getId());

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.VOID);
    }

    @Test
    void should_throwPlatformInvoiceNotFoundException_when_invoiceDoesNotExist() {
        UUID invoiceId = UUID.randomUUID();
        when(platformInvoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new VoidPlatformInvoiceUseCase(platformInvoiceRepository).execute(invoiceId))
            .isInstanceOf(PlatformInvoiceNotFoundException.class);
    }

    @Test
    void should_throwInvalidTransition_when_invoiceAlreadyPaid() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        invoice.markPaid();
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> new VoidPlatformInvoiceUseCase(platformInvoiceRepository).execute(invoice.getId()))
            .isInstanceOf(PlatformInvoiceInvalidTransitionException.class);
    }
}
