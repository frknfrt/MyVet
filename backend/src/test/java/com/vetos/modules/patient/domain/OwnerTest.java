package com.vetos.modules.patient.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerTest {

    @Test
    void should_createAnonymousPlaceholder_withExpectedDefaults() {
        UUID tenantId = UUID.randomUUID();

        Owner owner = Owner.createAnonymousPlaceholder(tenantId);

        assertThat(owner.getTenantId()).isEqualTo(tenantId);
        assertThat(owner.getFullName()).isEqualTo("Anonim Müşteri");
        assertThat(owner.getPhone()).isEqualTo("0000000000");
        assertThat(owner.isAnonymousPlaceholder()).isTrue();
    }
}
