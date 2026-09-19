package com.vetos.platform.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TenantContext.set()'in ROOT_TENANT_ID sentinel'ini reddettigini kilitler
 * (code review bulgusu 1 -- bkz. task-1-report.md "Fix round 1"). Bu sentinel
 * SADECE TenantContextIdentifierResolver'in ic kullanimidir; set() ile elle
 * atanabilir olsaydi, bozuk bir JWT claim'i / hatali bir fixture / ileride
 * bir bug, kimlik-dogrulanmis bir istegi sessizce ve tamamen izolasyonsuz
 * calistirabilirdi.
 */
class TenantContextTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void set_rejectsRootTenantIdSentinel() {
        assertThatThrownBy(() -> TenantContext.set(TenantContextIdentifierResolver.ROOT_TENANT_ID))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void set_acceptsOrdinaryTenantId() {
        UUID tenantId = UUID.randomUUID();

        TenantContext.set(tenantId);

        assertThat(TenantContext.current()).isEqualTo(tenantId);
    }
}
