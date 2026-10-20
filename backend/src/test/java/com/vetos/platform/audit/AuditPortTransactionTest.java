package com.vetos.platform.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Canli docker-compose Postgres gerekir. */
@SpringBootTest
class AuditPortTransactionTest {

    @Autowired private AuditPort auditPort;
    @Autowired private AuditEventQuery query;
    @Autowired private TransactionTemplate tx;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private int count(UUID tenant) {
        return query.list(tenant, new AuditEventFilter(null, null, null, null), 0, 200).items().size();
    }

    @Test
    void should_rollBackWithCaller() {
        UUID tenant = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            auditPort.record(AuditRecord.of(AuditAction.INVOICE_VOIDED, "INVOICE", UUID.randomUUID(), null, null).forTenant(tenant),
                AuditActor.staff(UUID.randomUUID(), "X", "ADMIN"));
            status.setRollbackOnly();
        });
        assertThat(count(tenant)).isZero();
    }

    @Test
    void should_keepStandalone_evenIfCallerRollsBack() {
        UUID tenant = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            auditPort.recordStandalone(AuditRecord.of(AuditAction.STAFF_LOGIN_FAILED, "STAFF", UUID.randomUUID(), "X", "Hatalı şifre").forTenant(tenant),
                AuditActor.staff(UUID.randomUUID(), "X", "VET"));
            status.setRollbackOnly();
        });
        assertThat(count(tenant)).isEqualTo(1);
    }

    @Test
    void should_useTenantContext_and_securityActor_byDefault() {
        UUID tenant = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        TenantContext.set(tenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedStaffUser(staff, tenant, List.of(), "ADMIN"), null, List.of()));

        tx.executeWithoutResult(status -> auditPort.record(AuditRecord.of(AuditAction.CASH_REGISTER_CLOSED, "CASH_REGISTER", null, null, null)));

        AuditEventView v = query.list(tenant, new AuditEventFilter(null, null, null, null), 0, 1).items().get(0);
        assertThat(v.actorId()).isEqualTo(staff);
        assertThat(v.actorRole()).isEqualTo("ADMIN");
    }

    @Test
    void should_throw_when_noTenant() {
        assertThatThrownBy(() -> auditPort.record(AuditRecord.of(AuditAction.STAFF_CREATED, "STAFF", null, null, null),
            AuditActor.staff(UUID.randomUUID(), "X", "ADMIN")))
            .isInstanceOf(IllegalStateException.class);
    }
}
