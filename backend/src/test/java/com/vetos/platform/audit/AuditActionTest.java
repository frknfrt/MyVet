package com.vetos.platform.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActionTest {

    @Test
    void should_haveTwentyTwoActions_withTurkishLabels() {
        assertThat(AuditAction.values()).hasSize(22);
        assertThat(AuditAction.INVOICE_VOIDED.label()).isEqualTo("Fatura iptal edildi");
        assertThat(AuditAction.SUPPORT_IMPERSONATION_STARTED.label()).isEqualTo("Vetly destek ekibi hesaba girdi");
    }

    @Test
    void should_fallBackToCode_forUnknownLabel() {
        assertThat(AuditAction.labelOf("STAFF_CREATED")).isEqualTo("Personel eklendi");
        assertThat(AuditAction.labelOf("REMOVED_ACTION")).isEqualTo("REMOVED_ACTION");
    }
}
