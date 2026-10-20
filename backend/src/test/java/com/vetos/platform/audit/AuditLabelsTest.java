package com.vetos.platform.audit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLabelsTest {

    @Test
    void should_shortenUuidToFirstEightChars() {
        assertThat(AuditLabels.shortId(UUID.fromString("3f2a9c1e-0000-0000-0000-000000000000"))).isEqualTo("3f2a9c1e");
        assertThat(AuditLabels.shortId(null)).isNull();
    }

    @Test
    void should_formatMoneyTurkish() {
        assertThat(AuditLabels.money(new BigDecimal("1250"))).isEqualTo("1.250,00 ₺");
        assertThat(AuditLabels.money(new BigDecimal("450.5"))).isEqualTo("450,50 ₺");
        assertThat(AuditLabels.money(null)).isNull();
    }
}
