package com.vetos.platform.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderImpersonationTest {

    private final JwtTokenProvider provider = new JwtTokenProvider("test-secret-test-secret-test-secret-test-secret-123456", 60);

    @Test
    void should_carryImpersonator_inImpersonationToken() {
        UUID staff = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        String token = provider.generateImpersonationToken(staff, tenant, List.of(), "ADMIN", "destek@vetly.com.tr");

        AuthenticatedStaffUser parsed = provider.parse(token);

        assertThat(parsed.staffUserId()).isEqualTo(staff);
        assertThat(parsed.tenantId()).isEqualTo(tenant);
        assertThat(parsed.role()).isEqualTo("ADMIN");
        assertThat(parsed.impersonatedBy()).isEqualTo("destek@vetly.com.tr");
    }

    @Test
    void should_haveNoImpersonator_inNormalToken() {
        String token = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), "VET");
        assertThat(provider.parse(token).impersonatedBy()).isNull();
    }
}
