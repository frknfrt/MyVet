package com.vetos.platform.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Canli docker-compose Postgres gerekir. Kayitlar silinemedigi icin her test rastgele kiraci kullanir. */
@SpringBootTest
class JdbcAuditEventStoreTest {

    @Autowired private JdbcAuditEventStore store;
    @Autowired private JdbcTemplate jdbc;

    private UUID insert(UUID tenant, Instant at, AuditAction action, UUID actorId) {
        UUID id = UUID.randomUUID();
        store.insert(id, tenant, at, AuditActor.staff(actorId, "Ayşe Yılmaz", "VET"),
            AuditRecord.of(action, "INVOICE", UUID.randomUUID(), "3f2a9c1e", "Tutar: 450,00 ₺"), "10.0.0.1", "req-1");
        return id;
    }

    @Test
    void should_listNewestFirst_andIsolateTenants() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID older = insert(a, Instant.parse("2026-10-01T10:00:00Z"), AuditAction.INVOICE_VOIDED, actor);
        UUID newer = insert(a, Instant.parse("2026-10-02T10:00:00Z"), AuditAction.STAFF_CREATED, actor);
        insert(b, Instant.parse("2026-10-03T10:00:00Z"), AuditAction.INVOICE_VOIDED, actor);

        AuditEventPage page = store.list(a, new AuditEventFilter(null, null, null, null), 0, 50);

        assertThat(page.items()).extracting(AuditEventView::id).containsExactly(newer, older);
        assertThat(page.hasMore()).isFalse();
        AuditEventView v = page.items().get(1);
        assertThat(v.actionLabel()).isEqualTo("Fatura iptal edildi");
        assertThat(v.actorName()).isEqualTo("Ayşe Yılmaz");
        assertThat(v.channel()).isEqualTo("WEB");
        assertThat(v.ipAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    void should_filterByDateActionAndActor() {
        UUID t = UUID.randomUUID();
        UUID ali = UUID.randomUUID();
        UUID veli = UUID.randomUUID();
        insert(t, Instant.parse("2026-10-01T10:00:00Z"), AuditAction.INVOICE_VOIDED, ali);
        UUID match = insert(t, Instant.parse("2026-10-05T10:00:00Z"), AuditAction.INVOICE_VOIDED, ali);
        insert(t, Instant.parse("2026-10-05T11:00:00Z"), AuditAction.STAFF_CREATED, ali);
        insert(t, Instant.parse("2026-10-05T12:00:00Z"), AuditAction.INVOICE_VOIDED, veli);

        AuditEventPage page = store.list(t, new AuditEventFilter(
            Instant.parse("2026-10-04T00:00:00Z"), Instant.parse("2026-10-06T00:00:00Z"), AuditAction.INVOICE_VOIDED, ali), 0, 50);

        assertThat(page.items()).extracting(AuditEventView::id).containsExactly(match);
    }

    @Test
    void should_pageWithHasMore() {
        UUID t = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            insert(t, Instant.parse("2026-10-01T10:00:00Z").plusSeconds(i), AuditAction.STAFF_CREATED, UUID.randomUUID());
        }
        AuditEventPage first = store.list(t, new AuditEventFilter(null, null, null, null), 0, 2);
        AuditEventPage last = store.list(t, new AuditEventFilter(null, null, null, null), 2, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.hasMore()).isTrue();
        assertThat(last.items()).hasSize(1);
        assertThat(last.hasMore()).isFalse();
    }

    @Test
    void should_rejectUpdateAndDelete() {
        UUID id = insert(UUID.randomUUID(), Instant.now(), AuditAction.STAFF_CREATED, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET summary = 'x' WHERE id = ?", id))
            .hasMessageContaining("degistirilemez");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE id = ?", id))
            .hasMessageContaining("degistirilemez");
    }

    @Test
    void should_truncateSummaryTo500() {
        UUID t = UUID.randomUUID();
        store.insert(UUID.randomUUID(), t, Instant.now(), AuditActor.staff(UUID.randomUUID(), "X", "VET"),
            AuditRecord.of(AuditAction.STAFF_UPDATED, "STAFF", null, null, "a".repeat(800)), null, null);
        assertThat(store.list(t, new AuditEventFilter(null, null, null, null), 0, 1).items().get(0).summary()).hasSize(500);
    }
}
