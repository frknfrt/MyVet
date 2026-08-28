package com.vetos.modules.platformadmin.domain;

import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformInvoiceTest {

    @Test
    void should_setDueDateSevenDaysAfterIssuedOn_when_issued() {
        LocalDate issuedOn = LocalDate.of(2026, 8, 28);

        PlatformInvoice invoice = PlatformInvoice.issue(
            UUID.randomUUID(), "PRO", new BigDecimal("500.00"), issuedOn, issuedOn.plusMonths(1), issuedOn
        );

        assertThat(invoice.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.ISSUED);
        assertThat(invoice.getPaidAt()).isNull();
    }

    @Test
    void should_markPaid_when_statusIsIssued() {
        PlatformInvoice invoice = anIssuedInvoice();

        invoice.markPaid();

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.PAID);
        assertThat(invoice.getPaidAt()).isNotNull();
    }

    @Test
    void should_markPaid_when_statusIsOverdue() {
        PlatformInvoice invoice = anIssuedInvoice();
        invoice.markOverdue();

        invoice.markPaid();

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.PAID);
    }

    @Test
    void should_throwInvalidTransition_when_markingAlreadyPaidInvoiceAsPaidAgain() {
        PlatformInvoice invoice = anIssuedInvoice();
        invoice.markPaid();

        assertThatThrownBy(invoice::markPaid).isInstanceOf(PlatformInvoiceInvalidTransitionException.class);
    }

    @Test
    void should_throwInvalidTransition_when_voidingAlreadyPaidInvoice() {
        PlatformInvoice invoice = anIssuedInvoice();
        invoice.markPaid();

        assertThatThrownBy(invoice::voidInvoice).isInstanceOf(PlatformInvoiceInvalidTransitionException.class);
    }

    @Test
    void should_voidInvoice_when_statusIsIssuedOrOverdue() {
        PlatformInvoice issued = anIssuedInvoice();
        issued.voidInvoice();
        assertThat(issued.getStatus()).isEqualTo(PlatformInvoiceStatus.VOID);

        PlatformInvoice overdue = anIssuedInvoice();
        overdue.markOverdue();
        overdue.voidInvoice();
        assertThat(overdue.getStatus()).isEqualTo(PlatformInvoiceStatus.VOID);
    }

    private PlatformInvoice anIssuedInvoice() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        return PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
    }
}
