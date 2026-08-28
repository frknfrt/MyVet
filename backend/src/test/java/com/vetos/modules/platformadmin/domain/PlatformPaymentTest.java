package com.vetos.modules.platformadmin.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformPaymentTest {

    @Test
    void should_populateAllFields_when_recorded() {
        UUID invoiceId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        LocalDate paidAt = LocalDate.of(2026, 8, 28);

        PlatformPayment payment = PlatformPayment.record(
            invoiceId, new BigDecimal("500.00"), PlatformPaymentMethod.BANK_TRANSFER, paidAt, adminId, "Havale ref: 12345"
        );

        assertThat(payment.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(payment.getAmount()).isEqualByComparingTo("500.00");
        assertThat(payment.getMethod()).isEqualTo(PlatformPaymentMethod.BANK_TRANSFER);
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
        assertThat(payment.getRecordedByAdminId()).isEqualTo(adminId);
        assertThat(payment.getNotes()).isEqualTo("Havale ref: 12345");
    }
}
