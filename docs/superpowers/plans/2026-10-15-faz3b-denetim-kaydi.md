# Faz 3b — Klinik Denetim Kaydı Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Klinik içindeki 22 hassas işlemi değiştirilemez `audit_events` tablosuna kaydetmek; klinik yöneticisi ve platform admin için okuma uçları ve ekranları sağlamak; impersonate oturumunda yapılan işlemleri destek ekibine doğru atfetmek.

**Architecture:** `com.vetos.platform.audit` paketi: `AuditPort` (yazma), `AuditEventQuery` (okuma), JDBC deposu, aktör çözücü. Use case'ler işlem başarıyla bittiğinde `auditPort.record(...)` çağırır (aynı transaction). Giriş olayları kendi transaction'ında. Impersonate JWT'sine `imp` claim'i eklenir.

**Tech Stack:** Spring Boot 3.5.16, Java 21, Spring Modulith, NamedParameterJdbcTemplate, Flyway (Postgres), React 18 + TypeScript (Vite).

**Spec:** `docs/superpowers/specs/2026-10-15-denetim-kaydi-faz3b-design.md`

## Global Constraints

- **Commit YOK, push YOK.** Değişiklikler working tree'de kalır. `git add/commit/stash/checkout -- /reset/restore/clean` çalıştırma.
- Doğrudan `main`; branch/worktree yok.
- Testler `backend/` içinden: `./mvnw -q test -Dtest=<Sınıf>`. `@SpringBootTest` canlı docker Postgres (5433) ister. Testcontainers yok.
- Kod yorumları ASCII Türkçe; kullanıcıya görünen metinler (etiketler, özetler) Türkçe karakterli.
- Yeni paket `com.vetos.platform.audit` → `package-info.java`: `@org.springframework.modulith.NamedInterface("audit")`. `"platform::audit"` yalnızca `allowedDependencies` listesi OLAN modüllere eklenir: `billing`, `encounter`, `integration.tarbil`, `platformadmin`. `tenant` ve `patient` listesizdir — dokunma.
- Migration: `V81__audit_events.sql` (V80 dolu).
- 22 `AuditAction` kodu ve Türkçe etiketleri spec §3 tablosundaki gibi, harfi harfine.
- **PII kuralı:** `summary` ve `target_label` içinde hasta / hayvan sahibi adı, telefon, TCKN, adres, serbest klinik notu ASLA yok. Personel adı serbest. Personel e-postası `LogMasking.email(...)` ile maskeli. `summary` en fazla 500 karakter (fazlası kesilir).
- Kayıt iş transaction'ına katılır (`record`); giriş olayları `recordStandalone` (REQUIRES_NEW).
- Olay yalnızca işlem BAŞARIYLA tamamlandığında yazılır (istisna fırlatılmadan, use case'in sonunda) — tek istisna `STAFF_LOGIN_FAILED`.
- Okuma ucu sayfa boyutu: varsayılan 50, en az 1, en fazla 200; `page >= 0`.

## Review Focus

1. **Read-only transaction'da INSERT:** `LoginUseCase` `@Transactional(readOnly = true)`; giriş olayı aynı transaction'a katılırsa Postgres "cannot execute INSERT in a read-only transaction" verir ve **giriş tamamen bozulur**. Task 3'te `LoginAuditIntegrationTest` gerçek DB ile başarılı girişin 200 döndüğünü ve olayın yazıldığını doğrular.
2. **Kiracı yalıtımı okumada:** A kliniğinin yöneticisi B kliniğinin kaydını hiçbir parametreyle göremez. Task 6'da `should_notSeeOtherTenantsEvents`.
3. **Impersonate atfı:** destek ekibinin klinik içindeki işlemleri `impersonated_by` ile işaretlenmeli; normal giriş token'ında alan null kalmalı. Task 2'de JWT gidiş-dönüş testleri + Task 1 çözücü testi.
4. **Geri alınan iş + kayıt:** iş transaction'ı geri alınınca `record` ile yazılan olay da yok olmalı; `recordStandalone` kalmalı. Task 1'de `AuditPortTransactionTest`.
5. **Bağlamsız çağrı:** zamanlanmış iş gibi güvenlik bağlamı olmayan bir yerden `record(AuditRecord)` çağrılırsa sessizce "bilinmeyen" yazmak yerine `IllegalStateException` fırlatılmalı (bu fazda tüm çağrılar kullanıcı isteğinden geliyor). Task 1'de `should_throw_when_noAuthentication`.

---

### Task 1: Denetim çekirdeği — tablo, port, depo, aktör çözücü

**Files:**
- Create: `backend/src/main/resources/db/migration/V81__audit_events.sql`
- Create (paket `backend/src/main/java/com/vetos/platform/audit/`): `package-info.java`, `AuditAction.java`, `AuditRecord.java`, `AuditActor.java`, `AuditPort.java`, `AuditActorNames.java`, `AuditLabels.java`, `AuditEventFilter.java`, `AuditEventView.java`, `AuditEventPage.java`, `AuditEventQuery.java`, `JdbcAuditEventStore.java`, `AuditActorResolver.java`, `AuditPortAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/adapter/StaffAuditActorNames.java`
- Test: `backend/src/test/java/com/vetos/platform/audit/JdbcAuditEventStoreTest.java`, `AuditActorResolverTest.java`, `AuditPortTransactionTest.java`, `AuditLabelsTest.java`, `AuditActionTest.java`

**Interfaces (Produces — sonraki görevler bunları kullanır):**
- `enum AuditAction` (22 sabit, `String label()`, `static String labelOf(String code)` — bilinmeyen kodda kodun kendisi)
- `record AuditRecord(UUID tenantId, AuditAction action, String targetType, UUID targetId, String targetLabel, String summary)`; `static AuditRecord of(AuditAction, String targetType, UUID targetId, String targetLabel, String summary)` (tenantId null); `AuditRecord forTenant(UUID)`
- `record AuditActor(ActorType type, UUID id, String name, String role, String impersonatedBy, Channel channel)`; `enum ActorType { STAFF, PLATFORM_ADMIN }`, `enum Channel { WEB, EXTENSION }`; `static AuditActor staff(UUID id, String name, String role)` (impersonatedBy null, WEB); `static AuditActor platformAdmin(UUID id, String email)`
- `interface AuditPort { void record(AuditRecord); void record(AuditRecord, AuditActor); void recordStandalone(AuditRecord, AuditActor); }`
- `interface AuditActorNames { String staffName(UUID tenantId, UUID staffUserId); }` (bulunamazsa null)
- `final class AuditLabels { static String shortId(UUID); static String money(BigDecimal); }`
- `record AuditEventFilter(Instant from, Instant to, AuditAction action, UUID actorId)` (hepsi null olabilir)
- `record AuditEventView(UUID id, Instant occurredAt, String actorType, UUID actorId, String actorName, String actorRole, String impersonatedBy, String channel, String action, String actionLabel, String targetType, UUID targetId, String targetLabel, String summary, String ipAddress)`
- `record AuditEventPage(List<AuditEventView> items, int page, int size, boolean hasMore)`
- `interface AuditEventQuery { AuditEventPage list(UUID tenantId, AuditEventFilter filter, int page, int size); }`

- [ ] **Step 1: Migration**

```sql
-- Faz 3b (spec 2026-10-15): klinik ici hassas islemlerin degistirilemez kaydi.
-- Platform admin islemleri ayri tabloda kalir (audit_log_entries).
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    actor_type      VARCHAR(16)  NOT NULL,
    actor_id        UUID,
    actor_name      TEXT,
    actor_role      VARCHAR(32),
    impersonated_by TEXT,
    channel         VARCHAR(16)  NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    target_type     VARCHAR(32),
    target_id       UUID,
    target_label    TEXT,
    summary         TEXT,
    ip_address      TEXT,
    request_id      TEXT
);
CREATE INDEX idx_audit_events_tenant_time ON audit_events (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_events_tenant_action_time ON audit_events (tenant_id, action, occurred_at DESC);

-- Kaza / uygulama hatasina karsi: kayitlar guncellenemez ve silinemez.
CREATE FUNCTION audit_events_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events kayitlari degistirilemez';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_immutable();
```

- [ ] **Step 2: Başarısız testleri yaz**

`AuditActionTest.java`:

```java
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
```

`AuditLabelsTest.java`:

```java
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
```

`AuditActorResolverTest.java`:

```java
package com.vetos.platform.audit;

import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.security.ExtensionTokenAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditActorResolverTest {

    private final AuditActorResolver resolver = new AuditActorResolver();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Object principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void should_resolveStaffViaWeb() {
        authenticate(new AuthenticatedStaffUser(STAFF, TENANT, List.of(), "VET"), "VET");
        AuditActor actor = resolver.current();
        assertThat(actor).isEqualTo(new AuditActor(AuditActor.ActorType.STAFF, STAFF, null, "VET", null, AuditActor.Channel.WEB));
    }

    @Test
    void should_markExtensionChannel() {
        authenticate(new AuthenticatedStaffUser(STAFF, TENANT, List.of(), "VET"), ExtensionTokenAuthenticationFilter.ROLE);
        assertThat(resolver.current().channel()).isEqualTo(AuditActor.Channel.EXTENSION);
    }

    @Test
    void should_resolvePlatformAdmin() {
        UUID adminId = UUID.randomUUID();
        authenticate(new AuthenticatedPlatformAdmin(adminId, "furkan@vetly.com.tr"), "PLATFORM_ADMIN");
        assertThat(resolver.current()).isEqualTo(AuditActor.platformAdmin(adminId, "furkan@vetly.com.tr"));
    }

    @Test
    void should_throw_when_noAuthentication() {
        assertThatThrownBy(resolver::current).isInstanceOf(IllegalStateException.class);
    }
}
```

(Impersonate testi (`should_carryImpersonator`) Task 2'de eklenir — 5 parametreli `AuthenticatedStaffUser` kurucusu orada gelir. Task 1'de çözücü `impersonatedBy` için null kullanır.)

`JdbcAuditEventStoreTest.java` (canlı Postgres):

```java
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
```

`AuditPortTransactionTest.java` (canlı Postgres):

```java
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
```

(`TransactionTemplate` bean'i Spring Boot tarafından otomatik sağlanır. `should_useTenantContext_and_securityActor_byDefault` personel adını `AuditActorNames` ile arar — rastgele personel id'si bulunmaz, ad null kalır; test bunu doğrulamaz.)

- [ ] **Step 3: Testlerin derlenmediğini gör**

Run: `./mvnw -q test -Dtest='AuditActionTest,AuditLabelsTest,AuditActorResolverTest'`
Expected: derleme hatası.

- [ ] **Step 4: Paketi yaz**

`package-info.java`:

```java
@org.springframework.modulith.NamedInterface("audit")
package com.vetos.platform.audit;
```

`AuditAction.java`:

```java
package com.vetos.platform.audit;

/** Klinik denetim kaydi olaylari (spec 2026-10-15 §3). Kod DB'ye yazilir; etiket ekranda gosterilir. */
public enum AuditAction {
    STAFF_LOGIN_SUCCEEDED("Giriş yapıldı"),
    STAFF_LOGIN_FAILED("Başarısız giriş denemesi"),
    STAFF_CREATED("Personel eklendi"),
    STAFF_UPDATED("Personel bilgisi değişti"),
    STAFF_DEACTIVATED("Personel pasife alındı"),
    STAFF_REACTIVATED("Personel yeniden aktif edildi"),
    STAFF_INVITED("Personel davet edildi"),
    STAFF_INVITE_REVOKED("Davet iptal edildi"),
    STAFF_INVITE_ACCEPTED("Davet kabul edildi"),
    INVOICE_VOIDED("Fatura iptal edildi"),
    INVOICE_PAYMENT_RECORDED("Ödeme alındı"),
    CASH_REGISTER_CLOSED("Kasa kapatıldı"),
    PRESCRIPTION_ISSUED("Reçete yazıldı"),
    PRESCRIPTION_CANCELLED("Reçete iptal edildi"),
    ENCOUNTER_FINALIZED("Muayene kesinleştirildi"),
    CONSENT_REVOKED("KVKK onayı geri çekildi"),
    TARBIL_MARKED_SUBMITTED("TARBİL'e gönderildi işaretlendi"),
    TARBIL_SUBMISSION_DISMISSED("TARBİL gönderimi yok sayıldı"),
    TARBIL_SUBMISSION_RESTORED("TARBİL gönderimi geri alındı"),
    TARBIL_EXTENSION_PAIRED("TARBİL eklentisi eşleştirildi"),
    TARBIL_EXTENSION_REVOKED("TARBİL eklenti anahtarı iptal edildi"),
    SUPPORT_IMPERSONATION_STARTED("Vetly destek ekibi hesaba girdi");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** DB'deki kod artik enum'da yoksa (yeniden adlandirma) kodun kendisi gosterilir. */
    public static String labelOf(String code) {
        for (AuditAction a : values()) {
            if (a.name().equals(code)) {
                return a.label;
            }
        }
        return code;
    }
}
```

`AuditRecord.java`:

```java
package com.vetos.platform.audit;

import java.util.UUID;

/**
 * Yazilacak olay. tenantId null ise TenantContext'ten alinir. summary/targetLabel'da hasta/sahip adi,
 * telefon, TCKN, adres, serbest klinik notu YOK (spec §3 PII kurali).
 */
public record AuditRecord(UUID tenantId, AuditAction action, String targetType, UUID targetId, String targetLabel, String summary) {

    public static AuditRecord of(AuditAction action, String targetType, UUID targetId, String targetLabel, String summary) {
        return new AuditRecord(null, action, targetType, targetId, targetLabel, summary);
    }

    public AuditRecord forTenant(UUID tenantId) {
        return new AuditRecord(tenantId, action, targetType, targetId, targetLabel, summary);
    }
}
```

`AuditActor.java`:

```java
package com.vetos.platform.audit;

import java.util.UUID;

/** Islemi yapan. STAFF icin name null verilirse AuditPortAdapter AuditActorNames ile doldurur. */
public record AuditActor(ActorType type, UUID id, String name, String role, String impersonatedBy, Channel channel) {

    public enum ActorType { STAFF, PLATFORM_ADMIN }

    public enum Channel { WEB, EXTENSION }

    public static AuditActor staff(UUID id, String name, String role) {
        return new AuditActor(ActorType.STAFF, id, name, role, null, Channel.WEB);
    }

    public static AuditActor platformAdmin(UUID id, String email) {
        return new AuditActor(ActorType.PLATFORM_ADMIN, id, email, null, null, Channel.WEB);
    }
}
```

`AuditPort.java`:

```java
package com.vetos.platform.audit;

/** Klinik denetim kaydi yazma (spec 2026-10-15 §5). Olay, islem BASARIYLA bittikten sonra cagrilir. */
public interface AuditPort {

    /** Aktor guvenlik baglamindan; cagiranin transaction'ina katilir (is geri alinirsa kayit da geri alinir). */
    void record(AuditRecord record);

    /** Aktor acikca verilir (kimliksiz uclar: davet kabulu, eklenti eslestirme; impersonate baslangici). */
    void record(AuditRecord record, AuditActor actor);

    /** Kendi transaction'inda (REQUIRES_NEW): giris olaylari -- read-only tx ve istisnayla geri alinan tx'e ragmen kalir. */
    void recordStandalone(AuditRecord record, AuditActor actor);
}
```

`AuditActorNames.java`:

```java
package com.vetos.platform.audit;

import java.util.UUID;

/** Personel adini yazma aninda okur (uygulamasi tenant modulunde). Bulunamazsa null. */
public interface AuditActorNames {

    String staffName(UUID tenantId, UUID staffUserId);
}
```

`AuditLabels.java`:

```java
package com.vetos.platform.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

/** Olay etiketi/ozeti icin ortak bicimler. */
public final class AuditLabels {

    private AuditLabels() {
    }

    public static String shortId(UUID id) {
        return id == null ? null : id.toString().substring(0, 8);
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("tr-TR")));
        return format.format(amount.setScale(2, RoundingMode.HALF_UP)) + " ₺";
    }
}
```

`AuditEventFilter.java`, `AuditEventView.java`, `AuditEventPage.java`, `AuditEventQuery.java`: Interfaces bloğundaki imzalarla birebir (record'lar ve arayüz; ek mantık yok).

`JdbcAuditEventStore.java`:

```java
package com.vetos.platform.audit;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * audit_events deposu (native SQL). Tum sorgular tenant_id'yi ACIKCA alir -- RLS yok.
 * Transaction yonetimi AuditPortAdapter'da.
 */
@Repository
public class JdbcAuditEventStore implements AuditEventQuery {

    static final int MAX_SUMMARY = 500;

    private static final RowMapper<AuditEventView> MAPPER = (rs, i) -> new AuditEventView(
        rs.getObject("id", UUID.class),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getString("actor_type"),
        rs.getObject("actor_id", UUID.class),
        rs.getString("actor_name"),
        rs.getString("actor_role"),
        rs.getString("impersonated_by"),
        rs.getString("channel"),
        rs.getString("action"),
        AuditAction.labelOf(rs.getString("action")),
        rs.getString("target_type"),
        rs.getObject("target_id", UUID.class),
        rs.getString("target_label"),
        rs.getString("summary"),
        rs.getString("ip_address")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAuditEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, Instant occurredAt, AuditActor actor, AuditRecord record, String ip, String requestId) {
        String summary = record.summary();
        if (summary != null && summary.length() > MAX_SUMMARY) {
            summary = summary.substring(0, MAX_SUMMARY);
        }
        jdbc.update("""
            INSERT INTO audit_events (id, tenant_id, occurred_at, actor_type, actor_id, actor_name, actor_role,
                impersonated_by, channel, action, target_type, target_id, target_label, summary, ip_address, request_id)
            VALUES (:id, :tenantId, :occurredAt, :actorType, :actorId, :actorName, :actorRole,
                :impersonatedBy, :channel, :action, :targetType, :targetId, :targetLabel, :summary, :ip, :requestId)
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", tenantId)
            .addValue("occurredAt", Timestamp.from(occurredAt))
            .addValue("actorType", actor.type().name())
            .addValue("actorId", actor.id())
            .addValue("actorName", actor.name())
            .addValue("actorRole", actor.role())
            .addValue("impersonatedBy", actor.impersonatedBy())
            .addValue("channel", actor.channel().name())
            .addValue("action", record.action().name())
            .addValue("targetType", record.targetType())
            .addValue("targetId", record.targetId())
            .addValue("targetLabel", record.targetLabel())
            .addValue("summary", summary)
            .addValue("ip", ip)
            .addValue("requestId", requestId));
    }

    @Override
    public AuditEventPage list(UUID tenantId, AuditEventFilter filter, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_events WHERE tenant_id = :tenantId");
        MapSqlParameterSource p = new MapSqlParameterSource("tenantId", tenantId);
        if (filter.from() != null) {
            sql.append(" AND occurred_at >= :from");
            p.addValue("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND occurred_at < :to");
            p.addValue("to", Timestamp.from(filter.to()));
        }
        if (filter.action() != null) {
            sql.append(" AND action = :action");
            p.addValue("action", filter.action().name());
        }
        if (filter.actorId() != null) {
            sql.append(" AND actor_id = :actorId");
            p.addValue("actorId", filter.actorId());
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset");
        p.addValue("limit", size + 1).addValue("offset", (long) page * size);
        List<AuditEventView> rows = jdbc.query(sql.toString(), p, MAPPER);
        boolean hasMore = rows.size() > size;
        return new AuditEventPage(hasMore ? rows.subList(0, size) : rows, page, size, hasMore);
    }
}
```

`AuditActorResolver.java`:

```java
package com.vetos.platform.audit;

import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.security.ExtensionTokenAuthenticationFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Guvenlik baglamindaki kimligi aktore cevirir. Ad burada cozulmez (AuditPortAdapter doldurur). */
@Component
public class AuditActorResolver {

    private static final String EXTENSION_AUTHORITY = "ROLE_" + ExtensionTokenAuthenticationFilter.ROLE;

    public AuditActor current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        if (principal instanceof AuthenticatedStaffUser staff) {
            boolean extension = auth.getAuthorities().stream().anyMatch(a -> EXTENSION_AUTHORITY.equals(a.getAuthority()));
            return new AuditActor(AuditActor.ActorType.STAFF, staff.staffUserId(), null, staff.role(),
                null, // Task 2: staff.impersonatedBy()
                extension ? AuditActor.Channel.EXTENSION : AuditActor.Channel.WEB);
        }
        if (principal instanceof AuthenticatedPlatformAdmin admin) {
            return AuditActor.platformAdmin(admin.platformAdminId(), admin.email());
        }
        throw new IllegalStateException("Denetim kaydi icin kimlik yok -- acik aktorlu AuditPort.record(record, actor) kullanin");
    }
}
```

`AuditPortAdapter.java`:

```java
package com.vetos.platform.audit;

import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.UUID;

@Component
class AuditPortAdapter implements AuditPort {

    private final JdbcAuditEventStore store;
    private final AuditActorResolver resolver;
    private final AuditActorNames names;
    private final Clock clock;

    @Autowired
    AuditPortAdapter(JdbcAuditEventStore store, AuditActorResolver resolver, AuditActorNames names) {
        this(store, resolver, names, Clock.systemUTC());
    }

    AuditPortAdapter(JdbcAuditEventStore store, AuditActorResolver resolver, AuditActorNames names, Clock clock) {
        this.store = store;
        this.resolver = resolver;
        this.names = names;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(AuditRecord record) {
        write(record, resolver.current());
    }

    @Override
    @Transactional
    public void record(AuditRecord record, AuditActor actor) {
        write(record, actor);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStandalone(AuditRecord record, AuditActor actor) {
        write(record, actor);
    }

    private void write(AuditRecord record, AuditActor actor) {
        UUID tenantId = record.tenantId() != null ? record.tenantId() : TenantContext.currentOrNull();
        if (tenantId == null) {
            throw new IllegalStateException("Denetim kaydi icin kiraci yok: " + record.action());
        }
        AuditActor named = actor;
        if (actor.type() == AuditActor.ActorType.STAFF && actor.name() == null && actor.id() != null) {
            named = new AuditActor(actor.type(), actor.id(), names.staffName(tenantId, actor.id()), actor.role(),
                actor.impersonatedBy(), actor.channel());
        }
        store.insert(UUID.randomUUID(), tenantId, clock.instant(), named, record, clientIp(), MDC.get("requestId"));
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
```

`StaffAuditActorNames.java` (tenant modülü):

```java
package com.vetos.modules.tenant.infrastructure.adapter;

import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.platform.audit.AuditActorNames;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Denetim kaydina yazma anindaki personel adi. Cagiranin kiraci oturumunda calisir (ayni kiraci). */
@Component
class StaffAuditActorNames implements AuditActorNames {

    private final StaffUserRepository staffUserRepository;

    StaffAuditActorNames(StaffUserRepository staffUserRepository) {
        this.staffUserRepository = staffUserRepository;
    }

    @Override
    public String staffName(UUID tenantId, UUID staffUserId) {
        return staffUserRepository.findById(staffUserId)
            .filter(s -> tenantId.equals(s.getTenantId()))
            .map(StaffUser::getFullName)
            .orElse(null);
    }
}
```

(`StaffUser`'da `getTenantId()`/`getFullName()` getter'ı yoksa (Lombok `@Getter` varsa vardır) mevcut adlara uyarla ve Deviations'a yaz.)

- [ ] **Step 5: Testleri çalıştır**

Run: `./mvnw -q test -Dtest='AuditActionTest,AuditLabelsTest,AuditActorResolverTest,JdbcAuditEventStoreTest,AuditPortTransactionTest,ApplicationModulesTest'`
Expected: PASS.

---

### Task 2: Impersonate atfı — JWT `imp` claim'i ve destek girişi olayı

**Files:**
- Modify: `backend/src/main/java/com/vetos/platform/security/AuthenticatedStaffUser.java`
- Modify: `backend/src/main/java/com/vetos/platform/security/JwtTokenProvider.java`
- Modify: `backend/src/main/java/com/vetos/platform/audit/AuditActorResolver.java` (`impersonatedBy`)
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/application/StartImpersonationUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/package-info.java` (`"platform::audit"`)
- Test: `backend/src/test/java/com/vetos/platform/security/JwtTokenProviderImpersonationTest.java`
- Test: `backend/src/test/java/com/vetos/platform/audit/AuditActorResolverTest.java` (`should_carryImpersonator` ekle)
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/StartImpersonationUseCaseTest.java` (genişlet)

**Interfaces:**
- Consumes: `AuditPort#record(AuditRecord, AuditActor)`, `AuditRecord`, `AuditActor.platformAdmin`, `AuditAction.SUPPORT_IMPERSONATION_STARTED` (Task 1).
- Produces: `AuthenticatedStaffUser(UUID staffUserId, UUID tenantId, List<UUID> branchIds, String role, String impersonatedBy)` + 4 parametreli kurucu (impersonatedBy=null); `JwtTokenProvider#generateImpersonationToken(UUID staffUserId, UUID tenantId, List<UUID> branchIds, String role, String impersonatorEmail): String`.

- [ ] **Step 1: Başarısız testler**

`JwtTokenProviderImpersonationTest.java`:

```java
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
```

`StartImpersonationUseCaseTest`'e (mevcut kurulumu kullan; `AuditPort` mock'unu constructor'ın son parametresi olarak ekle; `JwtTokenProvider` mock'unda artık `generateImpersonationToken` stub'lanmalı):

```java
    @Test
    void should_issueImpersonationToken_and_recordClinicVisibleEvent() {
        // mevcut basarili senaryonun kurulumu (target bulunur)
        // ...
        useCase.execute(tenantId, platformAdminId, "destek@vetly.com.tr");

        verify(jwtTokenProvider).generateImpersonationToken(
            eq(target.staffUserId()), eq(tenantId), eq(List.of(target.branchId())), eq(target.role().name()), eq("destek@vetly.com.tr"));
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture(), eq(AuditActor.platformAdmin(platformAdminId, "destek@vetly.com.tr")));
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.SUPPORT_IMPERSONATION_STARTED);
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().targetType()).isEqualTo("TENANT");
        assertThat(captor.getValue().summary()).isEqualTo("Yönetici hesabıyla");
    }
```

(Yorum satırlarındaki "mevcut kurulum"u dosyadaki gerçek başarılı-senaryo testinin `given/when` kısmından kopyala — `target`, `tenantId`, `platformAdminId` değişken adları oradakilere uyarlanır.)

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `./mvnw -q test -Dtest='JwtTokenProviderImpersonationTest,StartImpersonationUseCaseTest'`
Expected: derleme hatası.

- [ ] **Step 3: Uygula**

`AuthenticatedStaffUser`:

```java
/**
 * JWT claim'lerinden cozulmus, istek suresince gecerli kimlik dogrulama
 * bilgisi. api-conventions.md'deki JWT Claim Yapisi ile birebir eslesir.
 * impersonatedBy: platform admin "kiraci olarak gir" oturumunda admin e-postasi
 * (JWT "imp" claim'i); normal giriste null. Denetim kaydi bunu impersonated_by'a yazar.
 */
public record AuthenticatedStaffUser(
    UUID staffUserId,
    UUID tenantId,
    List<UUID> branchIds,
    String role,
    String impersonatedBy
) {
    public AuthenticatedStaffUser(UUID staffUserId, UUID tenantId, List<UUID> branchIds, String role) {
        this(staffUserId, tenantId, branchIds, role, null);
    }
}
```

`JwtTokenProvider`: mevcut `generateToken`'ın gövdesini özel bir `build(staffUserId, tenantId, branchIds, role, impersonatorEmail)` metoduna taşı (`impersonatorEmail != null` ise `.claim("imp", impersonatorEmail)`); `generateToken(...)` → `build(..., null)`; yeni:

```java
    /** Platform admin "kiraci olarak gir": normal personel token'i + "imp" claim'i (denetim kaydi icin). */
    public String generateImpersonationToken(UUID staffUserId, UUID tenantId, List<UUID> branchIds, String role, String impersonatorEmail) {
        return build(staffUserId, tenantId, branchIds, role, impersonatorEmail);
    }
```

`parse` sonunda 5 parametreli kurucu ile `claims.get("imp", String.class)` (yoksa null).

`AuditActorResolver`: `null, // Task 2: staff.impersonatedBy()` → `staff.impersonatedBy(),`. `AuditActorResolverTest`'e ekle:

```java
    @Test
    void should_carryImpersonator() {
        authenticate(new AuthenticatedStaffUser(STAFF, TENANT, List.of(), "ADMIN", "destek@vetly.com.tr"), "ADMIN");
        assertThat(resolver.current().impersonatedBy()).isEqualTo("destek@vetly.com.tr");
    }
```

`StartImpersonationUseCase`: alan `private final AuditPort auditPort;` (son alan); `jwtTokenProvider.generateToken(...)` → `generateImpersonationToken(..., platformAdminEmail)`; `recordAuditLogUseCase.execute(...)` satırından sonra:

```java
        // Klinige gorunen kayit: destek ekibinin hesaba girdigi klinik yoneticisinin "Islem Gecmisi"nde gorunur.
        auditPort.record(
            AuditRecord.of(AuditAction.SUPPORT_IMPERSONATION_STARTED, "TENANT", tenantId, null, "Yönetici hesabıyla").forTenant(tenantId),
            AuditActor.platformAdmin(platformAdminId, platformAdminEmail)
        );
```

`StartImpersonationUseCase` `@Transactional` değilse: `AuditPort.record(record, actor)` kendi `@Transactional`'ıyla (REQUIRED → yeni transaction açar) yazar — ek değişiklik gerekmez.

`platformadmin/package-info.java` listesine `"platform::audit"` ekle.

- [ ] **Step 4: Testleri çalıştır**

Run: `./mvnw -q test -Dtest='JwtTokenProviderImpersonationTest,StartImpersonationUseCaseTest,AuditActorResolverTest,TenantRateLimitWiringTest,TarbilExtensionSecurityIntegrationTest,ApplicationModulesTest'`
Expected: PASS.

---

### Task 3: Tenant modülü olayları — giriş, personel, davet

**Files:**
- Modify (`backend/src/main/java/com/vetos/modules/tenant/application/`): `LoginUseCase.java`, `CreateStaffUserUseCase.java`, `UpdateStaffUserUseCase.java`, `DeactivateStaffUserUseCase.java`, `ReactivateStaffUserUseCase.java`, `InviteStaffMemberUseCase.java`, `RevokeStaffInviteUseCase.java`, `AcceptStaffInviteUseCase.java`
- Test: her biri için mevcut `*UseCaseTest` genişlet (yoksa oluştur) + `backend/src/test/java/com/vetos/modules/tenant/LoginAuditIntegrationTest.java`

**Interfaces:**
- Consumes: `AuditPort` (3 metot), `AuditRecord.of/forTenant`, `AuditActor.staff`, `AuditAction.*`, `LogMasking.email` (`com.vetos.platform.observability.LogMasking`).

Her use case'e `private final AuditPort auditPort;` SON alan olarak eklenir (Lombok `@RequiredArgsConstructor` sırası). Kayıt use case'in SONUNDA, başarı yolunda yapılır.

**Olay tablosu (birebir uygula):**

| Use case | Çağrı | AuditRecord (action, targetType, targetId, targetLabel, summary) |
|---|---|---|
| `LoginUseCase` başarı | `recordStandalone(rec.forTenant(tenantId), AuditActor.staff(staff.getId(), staff.getFullName(), staff.getRole().name()))` | `STAFF_LOGIN_SUCCEEDED, "STAFF", staffId, fullName, null` |
| `LoginUseCase` hatalı şifre | aynı, sonra `throw` | `STAFF_LOGIN_FAILED, "STAFF", staffId, fullName, "Hatalı şifre"` |
| `LoginUseCase` pasif kullanıcı | aynı, sonra `throw` | `STAFF_LOGIN_FAILED, ..., "Pasif kullanıcı"` |
| `LoginUseCase` askıdaki klinik | aynı, sonra `throw` | `STAFF_LOGIN_FAILED, ..., "Askıdaki klinik"` |
| `LoginUseCase` e-posta yok | KAYIT YOK | — |
| `CreateStaffUserUseCase` | `record(rec)` | `STAFF_CREATED, "STAFF", newId, fullName, "Rol: " + role` |
| `UpdateStaffUserUseCase` | `record(rec)` | `STAFF_UPDATED, "STAFF", id, fullName (güncel), rol değiştiyse "Rol: ESKİ → YENİ" yoksa "Bilgiler güncellendi"` |
| `DeactivateStaffUserUseCase` | `record(rec)` | `STAFF_DEACTIVATED, "STAFF", id, fullName, null` |
| `ReactivateStaffUserUseCase` | `record(rec)` | `STAFF_REACTIVATED, "STAFF", id, fullName, null` |
| `InviteStaffMemberUseCase` | `record(rec)` | `STAFF_INVITED, "STAFF_INVITE", inviteId, LogMasking.email(email), "Rol: " + role` |
| `RevokeStaffInviteUseCase` | `record(rec)` | `STAFF_INVITE_REVOKED, "STAFF_INVITE", inviteId, LogMasking.email(email), null` |
| `AcceptStaffInviteUseCase` (kimliksiz uç) | `record(rec.forTenant(invite tenantId), AuditActor.staff(newStaffId, fullName, role))` | `STAFF_INVITE_ACCEPTED, "STAFF", newStaffId, fullName, null` |

Giriş: e-posta eşleştiği anda kiracı `staffUser.getTenantId()`'dir (alan mevcut). Başarısız dalda önce `recordStandalone`, sonra mevcut istisna fırlatılır (istisna tipi/mesajı değişmez). `TenantSuspendedForbiddenException` dalında da aynı. Rol için `staffUser.getRole().name()`.

- [ ] **Step 1: Başarısız testler**

Her use case testinde (Mockito; `AuditPort auditPort = mock(AuditPort.class)` constructor'ın son parametresi) şu desen:

```java
    @Test
    void should_recordAuditEvent_onSuccess() {
        // mevcut basari senaryosu kurulumu
        // ... useCase.execute(...)
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.STAFF_DEACTIVATED);
        assertThat(rec.targetType()).isEqualTo("STAFF");
        assertThat(rec.targetId()).isEqualTo(staffId);
        assertThat(rec.targetLabel()).isEqualTo("Ayşe Yılmaz");
    }

    @Test
    void should_notRecord_whenUseCaseFails() {
        // mevcut hata senaryosu (ör. personel bulunamadi)
        // ... assertThatThrownBy(() -> useCase.execute(...))
        verifyNoInteractions(auditPort);
    }
```

Tablodaki her satır için bir başarı testi (action, targetType, targetId, targetLabel, summary değerlerini tablodaki gibi doğrula) ve use case'in mevcut bir hata yolu varsa bir "kayıt yok" testi. Ek olarak:

- `UpdateStaffUserUseCaseTest`: `should_describeRoleChange` (VET→ADMIN → `"Rol: VET → ADMIN"`), `should_describeOtherChanges` (rol aynı → `"Bilgiler güncellendi"`).
- `InviteStaffMemberUseCaseTest`: `targetLabel` `"a****@klinik.com"` biçiminde maskeli — `assertThat(rec.targetLabel()).isEqualTo(LogMasking.email(email)).doesNotContain(localPart)` (localPart en az 2 karakterli bir e-posta seç).
- `LoginUseCaseTest`: başarıda `recordStandalone` (STAFF_LOGIN_SUCCEEDED, `forTenant` = staff tenantId, aktör adı fullName); hatalı şifrede `recordStandalone` (STAFF_LOGIN_FAILED, `"Hatalı şifre"`) VE `InvalidCredentialsException`; pasif ve askıdaki klinik için özetler; bilinmeyen e-postada `verifyNoInteractions(auditPort)`.
- `AcceptStaffInviteUseCaseTest`: `verify(auditPort).record(captor.capture(), actorCaptor.capture())`; `tenantId` = davetin kiracısı; aktör id = yeni personel id.

`LoginAuditIntegrationTest.java` (canlı Postgres — Review Focus #1): mevcut bir SpringBootTest'in (ör. `TenantIsolationTest` / `TarbilExtensionSecurityIntegrationTest`) kiracı + şube + personel oluşturma yardımcılarını kullanarak bilinen şifreli aktif bir personel oluştur; `MockMvc` ile `POST /api/v1/auth/login` (doğru şifre) → 200 ve `AuditEventQuery.list(tenantId, boş filtre, 0, 10)` içinde `STAFF_LOGIN_SUCCEEDED`; yanlış şifre → 401 ve `STAFF_LOGIN_FAILED` (özet `"Hatalı şifre"`). (Rate limit: auth havuzu IP başına 5 dk'da 10 — bu test 2 istek atar, sorun değil; ama aynı JVM'deki diğer giriş testleriyle havuz paylaşılıyorsa farklı `remoteAddr` kullan: `.with(request -> { request.setRemoteAddr("10.9.0.1"); return request; })`.)

- [ ] **Step 2: RED**

Run: `./mvnw -q test -Dtest='LoginUseCaseTest,CreateStaffUserUseCaseTest,UpdateStaffUserUseCaseTest,DeactivateStaffUserUseCaseTest,ReactivateStaffUserUseCaseTest,InviteStaffMemberUseCaseTest,RevokeStaffInviteUseCaseTest,AcceptStaffInviteUseCaseTest'`
Expected: derleme hatası / başarısız.

- [ ] **Step 3: Uygula** — tablodaki çağrıları ekle. Örnek (Deactivate):

```java
    @Transactional
    public void execute(UUID staffUserId, UUID actingStaffUserId) {
        // ... mevcut govde (pasife alma + kaydetme) aynen ...
        auditPort.record(AuditRecord.of(AuditAction.STAFF_DEACTIVATED, "STAFF", staffUser.getId(), staffUser.getFullName(), null));
    }
```

Örnek (Login hatalı şifre dalı):

```java
        if (!passwordEncoder.matches(command.password(), staffUser.getPasswordHash())) {
            recordLogin(staffUser, AuditAction.STAFF_LOGIN_FAILED, "Hatalı şifre");
            throw new InvalidCredentialsException();
        }
```

ve sınıfa:

```java
    /** Giris olaylari kendi transaction'inda: bu metot readOnly tx'te calisir ve hata dallari istisnayla biter. */
    private void recordLogin(StaffUser staffUser, AuditAction action, String summary) {
        auditPort.recordStandalone(
            AuditRecord.of(action, "STAFF", staffUser.getId(), staffUser.getFullName(), summary).forTenant(staffUser.getTenantId()),
            AuditActor.staff(staffUser.getId(), staffUser.getFullName(), staffUser.getRole().name())
        );
    }
```

(Başarı dalında `recordLogin(staffUser, AuditAction.STAFF_LOGIN_SUCCEEDED, null)` token üretildikten sonra.)

- [ ] **Step 4: GREEN**

Run: Step 2'deki komut + `LoginAuditIntegrationTest,ApplicationModulesTest` + giriş/personel ile ilgili mevcut SpringBootTest'ler (`./mvnw -q test -Dtest='*Login*,*Staff*,*Invite*,*Auth*'`).
Expected: PASS.

---

### Task 4: Fatura, kasa, reçete, muayene ve KVKK olayları

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/VoidInvoiceUseCase.java`, `RecordPaymentUseCase.java`, `CloseCashRegisterUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/IssuePrescriptionUseCase.java`, `CancelPrescriptionUseCase.java`, `FinalizeEncounterUseCase.java`
- Modify: `RevokeConsentUseCase.java` (patient modülü; `find backend/src -name RevokeConsentUseCase.java`)
- Modify: `backend/src/main/java/com/vetos/modules/billing/package-info.java`, `.../encounter/package-info.java` (`"platform::audit"`; patient listesiz — dokunma)
- Test: her use case'in mevcut testini genişlet (yoksa oluştur)

**Interfaces:**
- Consumes: `AuditPort#record(AuditRecord)`, `AuditRecord.of`, `AuditAction.*`, `AuditLabels.shortId/money` (Task 1).

Her use case'e `private final AuditPort auditPort;` SON alan; kayıt başarı yolunun sonunda `auditPort.record(AuditRecord.of(...))` (kiracı ve aktör bağlamdan).

**Olay tablosu (birebir):**

| Use case | AuditRecord (action, targetType, targetId, targetLabel, summary) |
|---|---|
| `VoidInvoiceUseCase` | `INVOICE_VOIDED, "INVOICE", invoiceId, AuditLabels.shortId(invoiceId), "Tutar: " + AuditLabels.money(invoice.getTotalAmount())` |
| `RecordPaymentUseCase` | `INVOICE_PAYMENT_RECORDED, "INVOICE", invoiceId, AuditLabels.shortId(invoiceId), AuditLabels.money(amount) + " — " + yöntemTürkçe` |
| `CloseCashRegisterUseCase` | `CASH_REGISTER_CLOSED, "CASH_REGISTER", sessionId, null, "Kapanış: " + AuditLabels.money(closingBalance)` |
| `IssuePrescriptionUseCase` | `PRESCRIPTION_ISSUED, "PRESCRIPTION", newId, AuditLabels.shortId(newId), kalemSayısı + " kalem"` |
| `CancelPrescriptionUseCase` | `PRESCRIPTION_CANCELLED, "PRESCRIPTION", prescriptionId, AuditLabels.shortId(prescriptionId), null` |
| `FinalizeEncounterUseCase` | `ENCOUNTER_FINALIZED, "ENCOUNTER", encounterId, AuditLabels.shortId(encounterId), null` |
| `RevokeConsentUseCase` | `CONSENT_REVOKED, "CONSENT", consentRecordId, onayTürüTürkçe (yoksa enum adı), null` |

Ödeme yöntemi Türkçe: ödeme yöntemi enum'unu oku (`RecordPaymentCommand.method()` tipi); değerleri `CASH→"Nakit"`, `CARD→"Kart"`, `BANK_TRANSFER→"Havale/EFT"`, diğerleri → enum adı olarak eşleyen küçük bir `private static String methodLabel(...)` yaz (gerçek enum sabitleri farklıysa onlara göre; Deviations'a yaz). Onay türü için de aynı yaklaşım (consent enum'u — `SMS`→"SMS", `WHATSAPP`→"WhatsApp", `KVKK`→"KVKK" vb.; yoksa enum adı).

**PII:** fatura/reçete/muayene için sahip veya hasta adı, kalem adları (ilaç adları klinik bilgidir) ve notlar YAZILMAZ — yalnızca tabloda gösterilenler.

- [ ] **Step 1: Başarısız testler** — Task 3'teki desenle her satır için `should_recordAuditEvent_onSuccess` (action/targetType/targetId/targetLabel/summary birebir) ve mevcut bir hata yolu için `verifyNoInteractions(auditPort)`. Ek:
  - `VoidInvoiceUseCaseTest`: toplam `1250` → summary `"Tutar: 1.250,00 ₺"`.
  - `RecordPaymentUseCaseTest`: nakit 450.5 → `"450,50 ₺ — Nakit"`.
  - `IssuePrescriptionUseCaseTest`: 3 kalemli komut → `"3 kalem"`; ve `assertThat(rec.summary()).doesNotContain(<testteki ilaç adı>)`.

- [ ] **Step 2: RED** — Run: `./mvnw -q test -Dtest='VoidInvoiceUseCaseTest,RecordPaymentUseCaseTest,CloseCashRegisterUseCaseTest,IssuePrescriptionUseCaseTest,CancelPrescriptionUseCaseTest,FinalizeEncounterUseCaseTest,RevokeConsentUseCaseTest'` → derleme hatası.

- [ ] **Step 3: Uygula** — tablodaki çağrılar + package-info güncellemeleri. Örnek:

```java
        // ... mevcut iptal govdesi aynen ...
        auditPort.record(AuditRecord.of(AuditAction.INVOICE_VOIDED, "INVOICE", invoice.getId(),
            AuditLabels.shortId(invoice.getId()), "Tutar: " + AuditLabels.money(invoice.getTotalAmount())));
```

- [ ] **Step 4: GREEN** — Step 2 komutu + `ApplicationModulesTest` + `./mvnw -q test -Dtest='*Invoice*,*Payment*,*CashRegister*,*Prescription*,*Encounter*,*Consent*'` → PASS. Bu use case'leri çağıran mevcut `@SpringBootTest`'ler güvenlik bağlamı kurmadan çağırıyorsa `AuditActorResolver` `IllegalStateException` fırlatır — o testlerde gerekli kimliği kur (ör. `SecurityContextHolder`'a `AuthenticatedStaffUser`), ürün koduna geri dönüş/sessiz yutma EKLEME; Deviations'a yaz.

---

### Task 5: TARBİL olayları

**Files:**
- Modify (`backend/src/main/java/com/vetos/modules/integration/tarbil/application/`): `MarkSubmittedUseCase.java`, `DismissSubmissionUseCase.java`, `RestoreSubmissionUseCase.java`, `PairExtensionUseCase.java`, `RevokeExtensionTokenUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/package-info.java` (`"platform::audit"`)
- Test: her use case'in mevcut testini genişlet (yoksa oluştur)

**Interfaces:** Consumes: `AuditPort#record(AuditRecord)`, `#record(AuditRecord, AuditActor)`, `AuditRecord.of/forTenant`, `AuditActor.staff`, `AuditLabels.shortId`, `AuditAction.*`.

`private final AuditPort auditPort;` SON alan.

| Use case | Çağrı | AuditRecord |
|---|---|---|
| `MarkSubmittedUseCase` | `record(rec.forTenant(tenantId))` — yalnızca gerçekten işaretlendiğinde (Optional dolu dönen yol) | `TARBIL_MARKED_SUBMITTED, "TARBIL_SUBMISSION", submissionId, AuditLabels.shortId(submissionId), null` |
| `DismissSubmissionUseCase` | `record(rec.forTenant(tenantId))` | `TARBIL_SUBMISSION_DISMISSED, "TARBIL_SUBMISSION", submissionId, shortId, reason (null olabilir; 200 karakterde kes)` |
| `RestoreSubmissionUseCase` | `record(rec.forTenant(tenantId))` | `TARBIL_SUBMISSION_RESTORED, "TARBIL_SUBMISSION", submissionId, shortId, null` |
| `PairExtensionUseCase` (kimliksiz uç) | `record(rec.forTenant(kodun kiracısı), AuditActor.staff(kodun personeli, null, kodun personelinin rolü veya null))` | `TARBIL_EXTENSION_PAIRED, "EXTENSION_TOKEN", yeniTokenId, label, null` |
| `RevokeExtensionTokenUseCase` | `record(rec.forTenant(tenantId))` | `TARBIL_EXTENSION_REVOKED, "EXTENSION_TOKEN", tokenId, tokenLabel, null` |

`PairExtensionUseCase`'te kiracı ve personel eşleştirme kodu kaydından okunur (kodun gerçek alan adlarına göre); ad `null` verilir — `AuditPortAdapter` `AuditActorNames` ile doldurur. Rol bilinmiyorsa `null`. Tarbil eklenti uçları (`MarkSubmitted`, `Dismiss` eklentiden) çağrıldığında kanal `AuditActorResolver` tarafından otomatik `EXTENSION` olur — ek kod yok.

**Dikkat:** dismiss gerekçesi kullanıcı metnidir; klinik personeli hasta adı yazmış olabilir. Spec "gerekçe (≤200)" diyor — 200 karakterde kes, başka işlem yok (ledger'a bu risk zaten not edildi).

- [ ] **Step 1: Başarısız testler** — Task 3 deseniyle her satır; ek: `MarkSubmittedUseCaseTest` `should_notRecord_whenNothingMarked` (Optional.empty yolu → `verifyNoInteractions(auditPort)`); `DismissSubmissionUseCaseTest` 300 karakterlik gerekçe → summary uzunluğu 200; `PairExtensionUseCaseTest` → `verify(auditPort).record(captor.capture(), actorCaptor.capture())`, `tenantId` = kodun kiracısı, aktör id = kodun personeli; geçersiz kodda kayıt yok.
- [ ] **Step 2: RED** — Run: `./mvnw -q test -Dtest='MarkSubmittedUseCaseTest,DismissSubmissionUseCaseTest,RestoreSubmissionUseCaseTest,PairExtensionUseCaseTest,RevokeExtensionTokenUseCaseTest'`
- [ ] **Step 3: Uygula** — tablodaki çağrılar + package-info.
- [ ] **Step 4: GREEN** — Step 2 komutu + `TarbilExtensionSecurityIntegrationTest,ApplicationModulesTest` + `./mvnw -q test -Dtest='*Tarbil*'` → PASS. (`TarbilExtensionSecurityIntegrationTest` gerçek eklenti token'ıyla bu uçları çağırıyorsa olaylar `EXTENSION` kanalıyla yazılır — beklenen.)

---

### Task 6: Okuma uçları

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/tenant/api/AuditEventsController.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/PlatformAdminTenantsController.java`
- Test: `backend/src/test/java/com/vetos/AuditEventsEndpointTest.java`

**Interfaces:**
- Consumes: `AuditEventQuery#list`, `AuditEventFilter`, `AuditEventPage`, `AuditAction` (Task 1).
- Produces (JSON, Task 7 kullanır):
  - `GET /api/v1/audit-events?from=<ISO instant>&to=<ISO instant>&action=<KOD>&actorId=<uuid>&page=0&size=50` → `AuditEventPage` JSON `{ items: [...AuditEventView alanları...], page, size, hasMore }` (ADMIN).
  - `GET /api/v1/audit-events/actions` → `[{ code, label }]` (ADMIN).
  - `GET /api/v1/platform-admin/tenants/{id}/audit-events?...` → aynı.

- [ ] **Step 1: Başarısız test** — `AuditEventsEndpointTest.java` (canlı Postgres):

```java
package com.vetos;

import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditActor;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.audit.JdbcAuditEventStore;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. */
@SpringBootTest
@AutoConfigureMockMvc
class AuditEventsEndpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcAuditEventStore store;

    private String bearer(UUID tenantId, String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), role);
    }

    private void seed(UUID tenant, AuditAction action) {
        store.insert(UUID.randomUUID(), tenant, Instant.now(), AuditActor.staff(UUID.randomUUID(), "Ayşe Yılmaz", "VET"),
            AuditRecord.of(action, "INVOICE", UUID.randomUUID(), "3f2a9c1e", null), "10.0.0.1", null);
    }

    @Test
    void should_listOwnTenantEvents_forClinicAdmin() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, AuditAction.INVOICE_VOIDED);
        mockMvc.perform(get("/api/v1/audit-events").header("Authorization", bearer(tenant, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].action").value("INVOICE_VOIDED"))
            .andExpect(jsonPath("$.items[0].actionLabel").value("Fatura iptal edildi"))
            .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void should_notSeeOtherTenantsEvents() throws Exception {
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        seed(other, AuditAction.INVOICE_VOIDED);
        mockMvc.perform(get("/api/v1/audit-events").header("Authorization", bearer(mine, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void should_filterByAction() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, AuditAction.INVOICE_VOIDED);
        seed(tenant, AuditAction.STAFF_CREATED);
        mockMvc.perform(get("/api/v1/audit-events").param("action", "STAFF_CREATED").header("Authorization", bearer(tenant, "ADMIN")))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].action").value("STAFF_CREATED"));
    }

    @Test
    void should_forbidVet() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events").header("Authorization", bearer(UUID.randomUUID(), "VET")))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_rejectOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events").param("size", "500").header("Authorization", bearer(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_rejectUnknownAction() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events").param("action", "NOPE").header("Authorization", bearer(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_listActionLabels() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events/actions").header("Authorization", bearer(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(22)))
            .andExpect(jsonPath("$[0].code").exists())
            .andExpect(jsonPath("$[0].label").exists());
    }
}
```

(Platform admin ucu için bu dosyaya platform admin token'ıyla bir test ekle — platform admin JWT'si nasıl üretiliyorsa (`grep -rn "PlatformAdmin.*Token\|generatePlatformAdmin" backend/src/main/java`) onunla `GET /api/v1/platform-admin/tenants/{tenant}/audit-events` → 200 ve seed edilen olay; personel ADMIN token'ıyla aynı uç → 401/403.)

- [ ] **Step 2: RED** — Run: `./mvnw -q test -Dtest=AuditEventsEndpointTest` → 404'ler / derleme hatası.

- [ ] **Step 3: Uygula**

`AuditEventsController.java`:

```java
package com.vetos.modules.tenant.api;

import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditEventFilter;
import com.vetos.platform.audit.AuditEventPage;
import com.vetos.platform.audit.AuditEventQuery;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Klinik yoneticisi: kendi klinigin islem gecmisi (spec 2026-10-15 §7). Kiraci yalnizca TenantContext'ten. */
@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditEventsController {

    private final AuditEventQuery auditEventQuery;

    @GetMapping
    public AuditEventPage list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) AuditAction action,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        return auditEventQuery.list(TenantContext.current(), new AuditEventFilter(from, to, action, actorId), page, size);
    }

    @GetMapping("/actions")
    public List<ActionOption> actions() {
        return Arrays.stream(AuditAction.values()).map(a -> new ActionOption(a.name(), a.label())).toList();
    }

    public record ActionOption(String code, String label) {}
}
```

(`@Min/@Max` Spring 6.2 yerleşik metot doğrulamasıyla `HandlerMethodValidationException` (400) üretir — `GlobalExceptionHandler`'ın 4xx dalı 400 döndürür. Test 500 görürse controller'a `@Validated` ekle ve `ConstraintViolationException` için `GlobalExceptionHandler`'a 400 handler'ı ekle; Deviations'a yaz.)

`PlatformAdminTenantsController`'a (alan `private final AuditEventQuery auditEventQuery;`):

```java
    /** Kiraci Detayi -- "Klinik islem gecmisi" karti. */
    @GetMapping("/{id}/audit-events")
    public AuditEventPage auditEvents(
        @PathVariable UUID id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) AuditAction action,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        return auditEventQuery.list(id, new AuditEventFilter(from, to, action, actorId), page, size);
    }
```

- [ ] **Step 4: GREEN** — Run: `./mvnw -q test -Dtest='AuditEventsEndpointTest,ApplicationModulesTest'` → PASS.

---

### Task 7: Ön yüz — İşlem Geçmişi sekmesi ve admin kartı

**Files:**
- Create: `frontend/src/api/auditApi.ts`
- Create: `frontend/src/components/audit/AuditEventTable.tsx`, `frontend/src/components/audit/AuditEventTable.module.css`
- Create: `frontend/src/pages/settings/ActivityHistoryPanel.tsx`
- Modify: `frontend/src/pages/settings/SettingsPage.tsx` (sekme) + sekme→panel yönlendirmesi neredeyse orası (`grep -rn "ai-merkezi\|AiCenterPanel" frontend/src`)
- Modify: `frontend/src/api/platformAdminApi.ts`, `frontend/src/pages/platform-admin/TenantDetailPage.tsx`

**Interfaces:** Consumes Task 6 JSON. Produces `<AuditEventTable fetchPage={...} loadActions={...} staffOptions={...} />`.

Ön yüzde birim test altyapısı yok; doğrulama `npm run build`. Yeni test altyapısı KURMA.

- [ ] **Step 1: API**

`auditApi.ts`:

```ts
import { apiClient } from './client';

export interface AuditEvent {
  id: string;
  occurredAt: string;
  actorType: 'STAFF' | 'PLATFORM_ADMIN';
  actorId: string | null;
  actorName: string | null;
  actorRole: string | null;
  impersonatedBy: string | null;
  channel: 'WEB' | 'EXTENSION';
  action: string;
  actionLabel: string;
  targetType: string | null;
  targetId: string | null;
  targetLabel: string | null;
  summary: string | null;
  ipAddress: string | null;
}

export interface AuditEventPage {
  items: AuditEvent[];
  page: number;
  size: number;
  hasMore: boolean;
}

export interface AuditActionOption {
  code: string;
  label: string;
}

export interface AuditQuery {
  from?: string; // ISO instant
  to?: string; // ISO instant (haric)
  action?: string;
  actorId?: string;
  page: number;
  size?: number;
}

export function toQueryString(q: AuditQuery): string {
  const params = new URLSearchParams();
  if (q.from) params.set('from', q.from);
  if (q.to) params.set('to', q.to);
  if (q.action) params.set('action', q.action);
  if (q.actorId) params.set('actorId', q.actorId);
  params.set('page', String(q.page));
  params.set('size', String(q.size ?? 50));
  return params.toString();
}

export const auditApi = {
  list: (q: AuditQuery) => apiClient.get<AuditEventPage>(`/api/v1/audit-events?${toQueryString(q)}`),
  actions: () => apiClient.get<AuditActionOption[]>('/api/v1/audit-events/actions'),
};
```

`platformAdminApi.ts` nesnesine (tipleri `./auditApi`'den import et):

```ts
  listTenantAuditEvents: (tenantId: string, q: AuditQuery) =>
    platformAdminClient.get<AuditEventPage>(`/api/v1/platform-admin/tenants/${tenantId}/audit-events?${toQueryString(q)}`),
```

Platform admin tarafında işlem etiketleri için `/api/v1/audit-events/actions` personel ucudur (platform admin token'ı ile çağrılamaz) — admin kartında filtre seçenekleri, gelen sayfadaki `actionLabel`'lardan değil, **ön yüzde sabit olmayan** bir kaynaktan gelmeli: kartta işlem filtresini GÖSTERME (yalnız tarih filtresi); klinik panelinde göster.

- [ ] **Step 2: `AuditEventTable` bileşeni**

```tsx
import { useCallback, useEffect, useState } from 'react';
import type { AuditActionOption, AuditEvent, AuditEventPage, AuditQuery } from '../../api/auditApi';
import { ApiError } from '../../api/client';
import { Button } from '../ui/Button';
import styles from './AuditEventTable.module.css';

const dateTime = new Intl.DateTimeFormat('tr-TR', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Istanbul' });

/** 'YYYY-MM-DD' (Istanbul gunu) -> o gunun 00:00'i, ISO instant. */
function dayStart(day: string): string {
  return new Date(`${day}T00:00:00+03:00`).toISOString();
}

/** 'YYYY-MM-DD' -> ertesi gunun 00:00'i (haric ust sinir). */
function nextDayStart(day: string): string {
  const d = new Date(`${day}T00:00:00+03:00`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString();
}

function actorText(e: AuditEvent): string {
  const name = e.actorType === 'PLATFORM_ADMIN' ? 'Vetly destek ekibi' : (e.actorName ?? 'Bilinmeyen personel');
  const notes: string[] = [];
  if (e.impersonatedBy) notes.push('Vetly destek ekibi aracılığıyla');
  if (e.channel === 'EXTENSION') notes.push('TARBİL eklentisi');
  return notes.length ? `${name} (${notes.join(', ')})` : name;
}

interface Props {
  fetchPage: (q: AuditQuery) => Promise<AuditEventPage>;
  actionOptions?: AuditActionOption[];
  staffOptions?: { id: string; name: string }[];
}

export function AuditEventTable({ fetchPage, actionOptions, staffOptions }: Props) {
  const [fromDay, setFromDay] = useState('');
  const [toDay, setToDay] = useState('');
  const [action, setAction] = useState('');
  const [actorId, setActorId] = useState('');
  const [items, setItems] = useState<AuditEvent[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (nextPage: number, append: boolean) => {
      setLoading(true);
      setError(null);
      try {
        const result = await fetchPage({
          from: fromDay ? dayStart(fromDay) : undefined,
          to: toDay ? nextDayStart(toDay) : undefined,
          action: action || undefined,
          actorId: actorId || undefined,
          page: nextPage,
        });
        setItems((prev) => (append ? [...prev, ...result.items] : result.items));
        setPage(nextPage);
        setHasMore(result.hasMore);
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'İşlem geçmişi yüklenemedi');
      } finally {
        setLoading(false);
      }
    },
    [fetchPage, fromDay, toDay, action, actorId]
  );

  useEffect(() => {
    load(0, false);
  }, [load]);

  return (
    <div className={styles.wrap}>
      <div className={styles.filters}>
        <label>
          Başlangıç
          <input type="date" value={fromDay} onChange={(e) => setFromDay(e.target.value)} />
        </label>
        <label>
          Bitiş
          <input type="date" value={toDay} onChange={(e) => setToDay(e.target.value)} />
        </label>
        {actionOptions && (
          <label>
            İşlem
            <select value={action} onChange={(e) => setAction(e.target.value)}>
              <option value="">Tümü</option>
              {actionOptions.map((o) => (
                <option key={o.code} value={o.code}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        )}
        {staffOptions && (
          <label>
            Kişi
            <select value={actorId} onChange={(e) => setActorId(e.target.value)}>
              <option value="">Herkes</option>
              {staffOptions.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {error && <div className={styles.error}>{error}</div>}

      <table className={styles.table}>
        <thead>
          <tr>
            <th>Tarih</th>
            <th>Kişi</th>
            <th>İşlem</th>
            <th>Kayıt</th>
            <th>Açıklama</th>
            <th>IP</th>
          </tr>
        </thead>
        <tbody>
          {items.map((e) => (
            <tr key={e.id}>
              <td>{dateTime.format(new Date(e.occurredAt))}</td>
              <td>{actorText(e)}</td>
              <td>{e.actionLabel}</td>
              <td>{e.targetLabel ?? '—'}</td>
              <td>{e.summary ?? '—'}</td>
              <td>{e.ipAddress ?? '—'}</td>
            </tr>
          ))}
          {!loading && items.length === 0 && (
            <tr>
              <td colSpan={6} className={styles.empty}>
                Kayıt yok.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {hasMore && (
        <Button variant="secondary" onClick={() => load(page + 1, true)} disabled={loading}>
          {loading ? 'Yükleniyor...' : 'Daha fazla yükle'}
        </Button>
      )}
    </div>
  );
}
```

`AuditEventTable.module.css` — projenin mevcut CSS değişkenlerini kullan (`grep -rn "var(--color" frontend/src/components | head`): `.wrap` dikey flex 12px boşluk; `.filters` yatay flex wrap 12px boşluk, label'lar 12px metin; `.table` tam genişlik, `border-collapse: collapse`, hücreler 8px dolgu, alt kenar `var(--color-border)`, 13px; `.empty` ortalı, soluk metin; `.error` tehlike rengi. Dar ekranda tablo kapsayıcısı `overflow-x: auto`.

- [ ] **Step 3: Klinik paneli ve sekme**

`ActivityHistoryPanel.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { auditApi, type AuditActionOption } from '../../api/auditApi';
import { staffUsersApi } from '../../api/staffUsersApi';
import { AuditEventTable } from '../../components/audit/AuditEventTable';

export function ActivityHistoryPanel() {
  const [actions, setActions] = useState<AuditActionOption[] | undefined>(undefined);
  const [staff, setStaff] = useState<{ id: string; name: string }[] | undefined>(undefined);

  useEffect(() => {
    auditApi.actions().then(setActions).catch(() => setActions(undefined));
    staffUsersApi
      .list()
      .then((list) => setStaff(list.map((s) => ({ id: s.id, name: s.fullName }))))
      .catch(() => setStaff(undefined));
  }, []);

  return <AuditEventTable fetchPage={auditApi.list} actionOptions={actions} staffOptions={staff} />;
}
```

(`staffUsersApi`'nin export adı ve `StaffUserItem` alan adları (`id`, `fullName`) farklıysa gerçek adlara uyarla.) `SettingsPage.tsx` sekme listesine `{ path: 'islem-gecmisi', label: 'İşlem Geçmişi', roles: ['ADMIN'] }` (Abonelik'ten sonra) ve sekme yolunu `ActivityHistoryPanel`'e bağlayan route'u mevcut sekmelerle aynı yerde ekle. Kısa bir açıklama satırı: "Klinikte yapılan hassas işlemlerin değiştirilemez kaydı."

- [ ] **Step 4: Admin kartı**

`TenantDetailPage.tsx`: "Bu ayki kullanım" kartının altına, aynı kart sarmalayıcısıyla:

```tsx
            <div className={styles.card}>
              <div className={styles.cardTitle}>Klinik işlem geçmişi</div>
              <AuditEventTable fetchPage={(q) => platformAdminApi.listTenantAuditEvents(tenantId!, q)} />
            </div>
```

(`fetchPage` her render'da yeni fonksiyon olursa `useCallback` bağımlılığı sonsuz yükleme döngüsü yaratır — `useCallback(..., [tenantId])` ile sar. Kart sınıf adlarını mevcut "Entegrasyon Durumu" kartından kopyala; `tenantId` değişken adı sayfadakiyle aynı olmalı.)

- [ ] **Step 5: Derle** — Run (frontend/): `npm run build` → hatasız.

---

### Task 8: Dokümantasyon, yol haritası, tam doğrulama

**Files:**
- Modify: `docs/deployment.md`
- Modify: `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md`

- [ ] **Step 1: `docs/deployment.md`** — "Rate limit ve paket kotaları (Faz 4)" bölümünden sonra (Türkçe karakterli):

```markdown
## İşlem geçmişi / denetim kaydı (Faz 3b)

Klinik içindeki hassas işlemler (giriş ve başarısız giriş denemesi, personel ve davet değişiklikleri, fatura iptali ve ödeme, kasa kapatma, reçete yazma/iptal, muayene kesinleştirme, KVKK onayı geri çekme, TARBİL işaretleme/yok sayma/eklenti eşleştirme, Vetly destek ekibinin hesaba girmesi) `audit_events` tablosuna yazılır.

- **Klinik yöneticisi:** Ayarlar → İşlem Geçmişi.
- **Platform admin:** Kiracılar → ilgili kiracı → "Klinik işlem geçmişi".
- Kayıtlar **değiştirilemez**: tablo üzerindeki tetikleyici UPDATE ve DELETE'i reddeder. Hasta/sahip adı, telefon, TCKN kayda yazılmaz.
- Destek ekibi "kiracı olarak gir" ile bir hesaba girdiğinde bu, kliniğin İşlem Geçmişi'nde görünür ve o oturumdaki her işlem "Vetly destek ekibi aracılığıyla" diye işaretlenir.
- Kayıt bu sürümden itibaren başlar; eski işlemler için geçmiş yoktur. Saklama süresi şimdilik sınırsız.
```

- [ ] **Step 2: Yol haritası** — Faz 3b başlığını `## Faz 3b — Audit log ✅` yap (`← **SIRADAKİ**` kaldır), altına `- ✅ 2026-10-15: 22 klinik olayı, değiştirilemez audit_events, klinik + admin ekranları, impersonate atfı (spec docs/superpowers/specs/2026-10-15-denetim-kaydi-faz3b-design.md).`; Faz 5 başlığına `← **SIRADAKİ**` ekle.

- [ ] **Step 3: Tam doğrulama** — Run (backend/): `./mvnw -q test` → tüm testler yeşil; toplamı `target/surefire-reports/TEST-*.xml`'den topla (Faz 4 sonunda 715). Kırmızı varsa ürün koduna dokunmadan raporla. Run (frontend/): `npm run build` → hatasız.
