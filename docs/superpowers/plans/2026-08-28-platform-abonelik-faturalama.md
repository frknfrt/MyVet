# Platform Abonelik Faturalama Sistemi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Platform admin panelinde gerçek bir fatura/ödeme kayıt sistemi kurmak — otomatik fatura üretimi, 7 günlük dunning döngüsü (e-posta+SMS bildirimi), otomatik askıya alma/geri açılma ve şu an tamamen kozmetik olan "Askıya Al" enforcement'ının gerçekten çalışır hale getirilmesi.

**Architecture:** Yeni `PlatformInvoice`/`PlatformPayment` entity'leri `modules/platformadmin`'de yaşıyor. Günlük bir `@Scheduled` job üç use-case'i sırayla çalıştırır (fatura üret → son-gün hatırlat → gecikeni askıya al). Kiracılar-arası (tenant-agnostic) platform aksiyonları, mevcut `TenantAdminPort` (platformadmin↔tenant arası tek sanctioned köprü) üzerinden `tenant` modülüne yazıyor. Bildirimler için mevcut kiracı-içi `notification` modülü BİLİNÇLİ olarak kullanılmıyor (TenantContext'e bağımlı, farklı amaç) — platformadmin kendi izole `PlatformBillingEmailPort`/`PlatformBillingSmsPort` çiftini kuruyor (TARBİL/davet e-postası ile aynı mock-adaptör deseni).

**Tech Stack:** Spring Boot 3.5, Spring Modulith, JPA/Hibernate, Flyway, JUnit 5 + Mockito + AssertJ, React/TypeScript (frontend).

**Spec:** `docs/superpowers/specs/2026-08-28-platform-abonelik-faturalama-design.md`

## Global Constraints

- Fatura döngüsü toplam **7 gün**, ek grace süresi yok: gün 0 kesim, gün 5 hatırlatma (dueDate - 2 gün), gün 7 (`dueDate` geçince) `OVERDUE` + `SUSPENDED` aynı anda.
- Kısmi ödeme yok — bir fatura ya tam ödenir ya ödenmez.
- Gerçek ödeme sağlayıcısı/otomatik kart çekimi yok — sadece elle ödeme kaydı.
- Trial abonelikler (`planCode == "TRIAL"`) hiç faturalanmaz.
- Mevcut "Plan / Durum Değiştir" elle-düzenleme modalı kaldırılmıyor.
- Domain metodları/use-case'ler `LocalDate.now()`'u kendi içlerinde ÇAĞIRMAZ — `today` her zaman çağıran taraftan (scheduler → use-case → entity factory) parametre olarak akar (mevcut `AppointmentReminderScheduler`/`SendAppointmentRemindersUseCase` deseniyle aynı — test edilebilirlik için).
- Exception isimlendirmesi `coding-conventions.md`'deki `<Durum>Exception` kuralına uyar: `*NotFoundException`→404, `*ConflictException`→409, `*ForbiddenException`→403, `*InvalidCredentialsException`/`*UnauthorizedException`→401, diğerleri→422 (`GlobalExceptionHandler.resolveStatus`).
- Test isimlendirmesi: `should_<beklenenSonuç>_when_<koşul>` (`coding-conventions.md`).

---

## File Structure

**Backend — yeni dosyalar:**
```
backend/src/main/resources/db/migration/V26__platform_invoices_and_payments.sql
backend/src/main/java/com/vetos/modules/platformadmin/domain/
  PlatformInvoiceStatus.java
  PlatformPaymentMethod.java
  PlatformInvoice.java
  PlatformPayment.java
  PlatformInvoiceRepository.java
  PlatformPaymentRepository.java
  PlatformBillingEmailPort.java
  PlatformBillingSmsPort.java
  exception/PlatformInvoiceNotFoundException.java
  exception/PlatformInvoiceInvalidTransitionException.java
backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/
  PlatformInvoiceJpaRepository.java
  PlatformInvoiceRepositoryAdapter.java
  PlatformPaymentJpaRepository.java
  PlatformPaymentRepositoryAdapter.java
backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/
  MockPlatformBillingEmailAdapter.java
  MockPlatformBillingSmsAdapter.java
backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/scheduling/
  PlatformBillingScheduler.java
backend/src/main/java/com/vetos/modules/platformadmin/application/
  GenerateDueInvoicesUseCase.java
  RemindDueSoonInvoicesUseCase.java
  FlagOverdueAndSuspendUseCase.java
  RecordPlatformPaymentUseCase.java
  VoidPlatformInvoiceUseCase.java
  ListPlatformInvoicesForTenantUseCase.java
  dto/RecordPlatformPaymentCommand.java
backend/src/main/java/com/vetos/modules/platformadmin/api/
  PlatformInvoicesController.java
  dto/PlatformInvoiceResponse.java
  dto/RecordPlatformPaymentRequest.java
backend/src/main/java/com/vetos/modules/tenant/domain/
  BillableSubscription.java
backend/src/main/java/com/vetos/modules/tenant/domain/exception/
  TenantSuspendedForbiddenException.java
backend/src/test/java/com/vetos/modules/platformadmin/domain/
  PlatformInvoiceTest.java
  PlatformPaymentTest.java
backend/src/test/java/com/vetos/modules/tenant/domain/
  SubscriptionTest.java
backend/src/test/java/com/vetos/modules/tenant/application/
  LoginUseCaseTest.java
backend/src/test/java/com/vetos/modules/platformadmin/application/
  RecordPlatformPaymentUseCaseTest.java
  VoidPlatformInvoiceUseCaseTest.java
  GenerateDueInvoicesUseCaseTest.java
  RemindDueSoonInvoicesUseCaseTest.java
  FlagOverdueAndSuspendUseCaseTest.java
```

**Backend — değiştirilecek dosyalar:**
```
backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java          — 5 yeni metod
backend/src/main/java/com/vetos/modules/tenant/domain/Subscription.java             — advanceRenewal/updateBillingStatus
backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java — yeni metodların implementasyonu
backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/StaffUserJpaRepository.java — findByBranchIdInAndRole
backend/src/main/java/com/vetos/modules/tenant/application/LoginUseCase.java        — SUSPENDED kontrolü
backend/src/main/java/com/vetos/modules/platformadmin/domain/PlanRepository.java    — findByCode
backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlanJpaRepository.java — findByCode
backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlanRepositoryAdapter.java — findByCode
backend/src/main/resources/application.yml                                          — platform-billing config bloğu
```

**Frontend — değiştirilecek dosyalar:**
```
frontend/src/api/platformAdminApi.ts                       — invoice tip/endpoint'leri
frontend/src/pages/platform-admin/tenantBadges.ts           — invoice status label/tone
frontend/src/pages/platform-admin/TenantDetailPage.tsx      — Faturalar kartı + ödeme modalı
frontend/src/pages/platform-admin/PlatformBillingPage.tsx   — açıklama metni güncellemesi
```

---

### Task 1: `PlatformInvoice` — enum'lar + entity + migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V26__platform_invoices_and_payments.sql`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformInvoiceStatus.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPaymentMethod.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PlatformInvoiceInvalidTransitionException.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformInvoice.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/domain/PlatformInvoiceTest.java`

**Interfaces:**
- Produces: `PlatformInvoice.issue(UUID tenantId, String planCode, BigDecimal amount, LocalDate periodStart, LocalDate periodEnd, LocalDate issuedOn): PlatformInvoice`, `.markPaid()`, `.markOverdue()`, `.voidInvoice()`, getters (`getId/getTenantId/getPlanCode/getAmount/getPeriodStart/getPeriodEnd/getDueDate/getStatus/getIssuedAt/getPaidAt`)

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=PlatformInvoiceTest`
Expected: FAIL — compilation error (`PlatformInvoice`, `PlatformInvoiceStatus`, `PlatformInvoiceInvalidTransitionException` bulunamıyor).

- [ ] **Step 3: Write minimal implementation**

`PlatformInvoiceStatus.java`:
```java
package com.vetos.modules.platformadmin.domain;

public enum PlatformInvoiceStatus { ISSUED, PAID, OVERDUE, VOID }
```

`PlatformPaymentMethod.java`:
```java
package com.vetos.modules.platformadmin.domain;

public enum PlatformPaymentMethod { BANK_TRANSFER, CARD, OTHER }
```

`exception/PlatformInvoiceInvalidTransitionException.java`:
```java
package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.platform.exception.DomainException;

public class PlatformInvoiceInvalidTransitionException extends DomainException {
    public PlatformInvoiceInvalidTransitionException(PlatformInvoiceStatus from, PlatformInvoiceStatus to) {
        super("PLATFORM_INVOICE_INVALID_TRANSITION", "Fatura " + from + " durumundan " + to + " durumuna gecemez");
    }
}
```

`PlatformInvoice.java`:
```java
package com.vetos.modules.platformadmin.domain;

import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "platform_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformInvoice {

    private static final int DUE_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformInvoiceStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    public static PlatformInvoice issue(
        UUID tenantId, String planCode, BigDecimal amount, LocalDate periodStart, LocalDate periodEnd, LocalDate issuedOn
    ) {
        PlatformInvoice invoice = new PlatformInvoice();
        invoice.tenantId = tenantId;
        invoice.planCode = planCode;
        invoice.amount = amount;
        invoice.periodStart = periodStart;
        invoice.periodEnd = periodEnd;
        invoice.dueDate = issuedOn.plusDays(DUE_DAYS);
        invoice.issuedAt = Instant.now();
        invoice.status = PlatformInvoiceStatus.ISSUED;
        return invoice;
    }

    public void markPaid() {
        if (status != PlatformInvoiceStatus.ISSUED && status != PlatformInvoiceStatus.OVERDUE) {
            throw new PlatformInvoiceInvalidTransitionException(status, PlatformInvoiceStatus.PAID);
        }
        this.status = PlatformInvoiceStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void markOverdue() {
        if (status != PlatformInvoiceStatus.ISSUED) {
            throw new PlatformInvoiceInvalidTransitionException(status, PlatformInvoiceStatus.OVERDUE);
        }
        this.status = PlatformInvoiceStatus.OVERDUE;
    }

    public void voidInvoice() {
        if (status != PlatformInvoiceStatus.ISSUED && status != PlatformInvoiceStatus.OVERDUE) {
            throw new PlatformInvoiceInvalidTransitionException(status, PlatformInvoiceStatus.VOID);
        }
        this.status = PlatformInvoiceStatus.VOID;
    }
}
```

`db/migration/V26__platform_invoices_and_payments.sql`:
```sql
CREATE TABLE platform_invoices (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    plan_code     TEXT NOT NULL,
    amount        NUMERIC(10,2) NOT NULL,
    period_start  DATE NOT NULL,
    period_end    DATE NOT NULL,
    due_date      DATE NOT NULL,
    status        TEXT NOT NULL,
    issued_at     TIMESTAMPTZ NOT NULL,
    paid_at       TIMESTAMPTZ
);
CREATE INDEX idx_platform_invoices_tenant_id ON platform_invoices (tenant_id);
CREATE INDEX idx_platform_invoices_status_due_date ON platform_invoices (status, due_date);

CREATE TABLE platform_payments (
    id                    UUID PRIMARY KEY,
    invoice_id            UUID NOT NULL REFERENCES platform_invoices (id),
    amount                NUMERIC(10,2) NOT NULL,
    method                TEXT NOT NULL,
    paid_at               DATE NOT NULL,
    recorded_by_admin_id  UUID NOT NULL,
    notes                 TEXT
);
CREATE INDEX idx_platform_payments_invoice_id ON platform_payments (invoice_id);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=PlatformInvoiceTest`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V26__platform_invoices_and_payments.sql backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformInvoiceStatus.java backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPaymentMethod.java backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PlatformInvoiceInvalidTransitionException.java backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformInvoice.java backend/src/test/java/com/vetos/modules/platformadmin/domain/PlatformInvoiceTest.java
git commit -m "feat: add PlatformInvoice entity with state machine + migration"
```

---

### Task 2: `PlatformPayment` entity

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPayment.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/domain/PlatformPaymentTest.java`

**Interfaces:**
- Consumes: `PlatformPaymentMethod` (Task 1)
- Produces: `PlatformPayment.record(UUID invoiceId, BigDecimal amount, PlatformPaymentMethod method, LocalDate paidAt, UUID recordedByAdminId, String notes): PlatformPayment`, getters

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=PlatformPaymentTest`
Expected: FAIL — compilation error (`PlatformPayment` bulunamıyor)

- [ ] **Step 3: Write minimal implementation**

```java
package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "platform_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformPaymentMethod method;

    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;

    @Column(name = "recorded_by_admin_id", nullable = false)
    private UUID recordedByAdminId;

    @Column(columnDefinition = "text")
    private String notes;

    public static PlatformPayment record(
        UUID invoiceId, BigDecimal amount, PlatformPaymentMethod method, LocalDate paidAt, UUID recordedByAdminId, String notes
    ) {
        PlatformPayment payment = new PlatformPayment();
        payment.invoiceId = invoiceId;
        payment.amount = amount;
        payment.method = method;
        payment.paidAt = paidAt;
        payment.recordedByAdminId = recordedByAdminId;
        payment.notes = notes;
        return payment;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=PlatformPaymentTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPayment.java backend/src/test/java/com/vetos/modules/platformadmin/domain/PlatformPaymentTest.java
git commit -m "feat: add PlatformPayment entity"
```

---

### Task 3: Repository port'ları + JPA + adaptörler (PlatformInvoice, PlatformPayment)

Bu task saf CRUD altyapısı — mevcut `PlatformInvoiceLogRepository`/`PlanRepository` deseninin birebir aynısı, dedike birim testi yok (mevcut kod tabanı konvansiyonu: repository adaptörleri sadece use-case testlerinde Mockito ile mocklanır, veya e2e curl ile doğrulanır — bkz. Task 19).

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformInvoiceRepository.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPaymentRepository.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PlatformInvoiceNotFoundException.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlatformInvoiceJpaRepository.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlatformInvoiceRepositoryAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlatformPaymentJpaRepository.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlatformPaymentRepositoryAdapter.java`

**Interfaces:**
- Produces: `PlatformInvoiceRepository{save, findById, findByTenantIdAndPeriodStart, findByTenantId, findByStatusAndDueDate, findByStatusAndDueDateBefore}`, `PlatformPaymentRepository{save, findByInvoiceId}`, `PlatformInvoiceNotFoundException(UUID)`

- [ ] **Step 1: Write the port interfaces + exception**

`domain/exception/PlatformInvoiceNotFoundException.java`:
```java
package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class PlatformInvoiceNotFoundException extends DomainException {
    public PlatformInvoiceNotFoundException(UUID id) {
        super("PLATFORM_INVOICE_NOT_FOUND", "Fatura bulunamadi: " + id);
    }
}
```

`domain/PlatformInvoiceRepository.java`:
```java
package com.vetos.modules.platformadmin.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformInvoiceRepository {
    PlatformInvoice save(PlatformInvoice invoice);
    Optional<PlatformInvoice> findById(UUID id);
    Optional<PlatformInvoice> findByTenantIdAndPeriodStart(UUID tenantId, LocalDate periodStart);
    List<PlatformInvoice> findByTenantId(UUID tenantId);
    List<PlatformInvoice> findByStatusAndDueDate(PlatformInvoiceStatus status, LocalDate dueDate);
    List<PlatformInvoice> findByStatusAndDueDateBefore(PlatformInvoiceStatus status, LocalDate date);
}
```

`domain/PlatformPaymentRepository.java`:
```java
package com.vetos.modules.platformadmin.domain;

import java.util.Optional;
import java.util.UUID;

public interface PlatformPaymentRepository {
    PlatformPayment save(PlatformPayment payment);
    Optional<PlatformPayment> findByInvoiceId(UUID invoiceId);
}
```

- [ ] **Step 2: Write the JPA repositories + adapters**

`infrastructure/persistence/PlatformInvoiceJpaRepository.java`:
```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlatformInvoiceJpaRepository extends JpaRepository<PlatformInvoice, UUID> {
    Optional<PlatformInvoice> findByTenantIdAndPeriodStart(UUID tenantId, LocalDate periodStart);
    List<PlatformInvoice> findByTenantId(UUID tenantId);
    List<PlatformInvoice> findByStatusAndDueDate(PlatformInvoiceStatus status, LocalDate dueDate);
    List<PlatformInvoice> findByStatusAndDueDateBefore(PlatformInvoiceStatus status, LocalDate date);
}
```

`infrastructure/persistence/PlatformInvoiceRepositoryAdapter.java`:
```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PlatformInvoiceRepositoryAdapter implements PlatformInvoiceRepository {

    private final PlatformInvoiceJpaRepository jpaRepository;

    @Override
    public PlatformInvoice save(PlatformInvoice invoice) { return jpaRepository.save(invoice); }

    @Override
    public Optional<PlatformInvoice> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<PlatformInvoice> findByTenantIdAndPeriodStart(UUID tenantId, LocalDate periodStart) {
        return jpaRepository.findByTenantIdAndPeriodStart(tenantId, periodStart);
    }

    @Override
    public List<PlatformInvoice> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<PlatformInvoice> findByStatusAndDueDate(PlatformInvoiceStatus status, LocalDate dueDate) {
        return jpaRepository.findByStatusAndDueDate(status, dueDate);
    }

    @Override
    public List<PlatformInvoice> findByStatusAndDueDateBefore(PlatformInvoiceStatus status, LocalDate date) {
        return jpaRepository.findByStatusAndDueDateBefore(status, date);
    }
}
```

`infrastructure/persistence/PlatformPaymentJpaRepository.java`:
```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PlatformPaymentJpaRepository extends JpaRepository<PlatformPayment, UUID> {
    Optional<PlatformPayment> findByInvoiceId(UUID invoiceId);
}
```

`infrastructure/persistence/PlatformPaymentRepositoryAdapter.java`:
```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformPayment;
import com.vetos.modules.platformadmin.domain.PlatformPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PlatformPaymentRepositoryAdapter implements PlatformPaymentRepository {

    private final PlatformPaymentJpaRepository jpaRepository;

    @Override
    public PlatformPayment save(PlatformPayment payment) { return jpaRepository.save(payment); }

    @Override
    public Optional<PlatformPayment> findByInvoiceId(UUID invoiceId) { return jpaRepository.findByInvoiceId(invoiceId); }
}
```

- [ ] **Step 3: Compile check**

Run: `cd backend && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformInvoiceRepository.java backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformPaymentRepository.java backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PlatformInvoiceNotFoundException.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlatformInvoiceJpaRepository.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlatformInvoiceRepositoryAdapter.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlatformPaymentJpaRepository.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlatformPaymentRepositoryAdapter.java
git commit -m "feat: add PlatformInvoice/PlatformPayment repository ports and adapters"
```

---

### Task 4: `TenantAdminPort` genişletmesi — faturalama sorgu/yazma metodları

Bu task `platformadmin`'in `tenant` modülüne erişebildiği TEK kapı olan `TenantAdminPort`'a 5 yeni metod ekliyor (bkz. `architecture.md` §6.1 — bu port zaten "kiracıyı görüntüleyip DEĞİŞTİREBİLME" için bilinçli bir istisna).

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/tenant/domain/BillableSubscription.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/Subscription.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/StaffUserJpaRepository.java`
- Test: `backend/src/test/java/com/vetos/modules/tenant/domain/SubscriptionTest.java`

**Interfaces:**
- Produces: `BillableSubscription(UUID tenantId, String planCode, LocalDate renewsAt)`, `Subscription.advanceRenewal(LocalDate)`, `Subscription.updateBillingStatus(BillingStatus)`, `TenantAdminPort.listSubscriptionsDueOnOrBefore(LocalDate): List<BillableSubscription>`, `TenantAdminPort.advanceRenewal(UUID, LocalDate)`, `TenantAdminPort.updateBillingStatus(UUID, BillingStatus)`, `TenantAdminPort.findBillingContactEmail(UUID): Optional<String>`, `TenantAdminPort.findBillingContactPhone(UUID): Optional<String>`

- [ ] **Step 1: Write the failing test (Subscription domain metodları)**

```java
package com.vetos.modules.tenant.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTest {

    @Test
    void should_updateRenewsAt_when_advanceRenewalCalled() {
        Subscription subscription = Subscription.startTrial(UUID.randomUUID());
        LocalDate nextPeriod = LocalDate.of(2026, 9, 28);

        subscription.advanceRenewal(nextPeriod);

        assertThat(subscription.getRenewsAt()).isEqualTo(nextPeriod);
    }

    @Test
    void should_updateBillingStatusOnly_when_updateBillingStatusCalled() {
        Subscription subscription = Subscription.startTrial(UUID.randomUUID());
        String originalPlanCode = subscription.getPlanCode();

        subscription.updateBillingStatus(BillingStatus.PAST_DUE);

        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.PAST_DUE);
        assertThat(subscription.getPlanCode()).isEqualTo(originalPlanCode);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=SubscriptionTest`
Expected: FAIL — `advanceRenewal`/`updateBillingStatus` bulunamıyor

- [ ] **Step 3: Write minimal implementation**

`domain/BillableSubscription.java`:
```java
package com.vetos.modules.tenant.domain;

import java.time.LocalDate;
import java.util.UUID;

public record BillableSubscription(UUID tenantId, String planCode, LocalDate renewsAt) {}
```

`domain/Subscription.java` — mevcut `changePlan` metodunun hemen altına ekle:
```java
    public void advanceRenewal(LocalDate newRenewsAt) {
        this.renewsAt = newRenewsAt;
    }

    public void updateBillingStatus(BillingStatus billingStatus) {
        this.billingStatus = billingStatus;
    }
```

`domain/TenantAdminPort.java` — tam dosya (mevcut 5 metodun üzerine 5 yeni metod):
```java
package com.vetos.modules.tenant.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DIKKAT: normal *LookupPort deseninden BILINCLI bir sapma -- diger
 * LookupPort'lar (BranchLookupPort, StaffUserLookupPort, TenantLookupPort)
 * sadece okuma sunar. Bu port ise platform admin modulunun herhangi bir
 * kiraciyi goruntuleyip DEGISTIREBILMESI icin yazma da icerir. Sadece
 * modules.platformadmin bu portu kullanir (architecture.md'ye not
 * dusulmustur). Diger hicbir modul bu portu import ETMEMELIDIR.
 */
public interface TenantAdminPort {
    List<TenantAdminOverview> listAll();
    TenantAdminOverview getOverview(UUID tenantId);
    void updateSubscription(UUID tenantId, String planCode, BillingStatus billingStatus, LocalDate renewsAt);
    void suspend(UUID tenantId);
    void activate(UUID tenantId);

    /** planCode != TRIAL ve renewsAt <= date olan tum abonelikler -- platform faturalama scheduler'i icin. */
    List<BillableSubscription> listSubscriptionsDueOnOrBefore(LocalDate date);
    void advanceRenewal(UUID tenantId, LocalDate newRenewsAt);
    void updateBillingStatus(UUID tenantId, BillingStatus billingStatus);
    Optional<String> findBillingContactEmail(UUID tenantId);
    Optional<String> findBillingContactPhone(UUID tenantId);
}
```

`infrastructure/persistence/StaffUserJpaRepository.java` — mevcut `countByBranchIdIn`'in altına ekle:
```java
    List<StaffUser> findByBranchIdInAndRole(List<UUID> branchIds, StaffRole role);
```
(dosyanın en üstündeki importlara `import com.vetos.modules.tenant.domain.StaffRole;` eklenmesi gerekmiyor çünkü aynı pakette değil — `com.vetos.modules.tenant.infrastructure.persistence` paketinden `com.vetos.modules.tenant.domain.StaffRole`'e import satırı eklenmeli.)

`infrastructure/persistence/TenantAdminPortAdapter.java` — tam dosya:
```java
package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.BillableSubscription;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.exception.SubscriptionNotFoundException;
import com.vetos.modules.tenant.domain.exception.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TenantAdminPortAdapter implements TenantAdminPort {

    private final TenantJpaRepository tenantJpaRepository;
    private final SubscriptionJpaRepository subscriptionJpaRepository;
    private final BranchJpaRepository branchJpaRepository;
    private final StaffUserJpaRepository staffUserJpaRepository;

    @Override
    public List<TenantAdminOverview> listAll() {
        return tenantJpaRepository.findAll().stream().map(this::toOverview).toList();
    }

    @Override
    public TenantAdminOverview getOverview(UUID tenantId) {
        Tenant tenant = tenantJpaRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return toOverview(tenant);
    }

    @Override
    public void updateSubscription(UUID tenantId, String planCode, BillingStatus billingStatus, LocalDate renewsAt) {
        Subscription subscription = subscriptionJpaRepository.findByTenantId(tenantId)
            .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));
        subscription.changePlan(planCode, billingStatus, renewsAt);
        subscriptionJpaRepository.save(subscription);
    }

    @Override
    public void suspend(UUID tenantId) {
        Tenant tenant = tenantJpaRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        tenant.suspend();
        tenantJpaRepository.save(tenant);
    }

    @Override
    public void activate(UUID tenantId) {
        Tenant tenant = tenantJpaRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        tenant.activate();
        tenantJpaRepository.save(tenant);
    }

    @Override
    public List<BillableSubscription> listSubscriptionsDueOnOrBefore(LocalDate date) {
        return subscriptionJpaRepository.findAll().stream()
            .filter(s -> !"TRIAL".equals(s.getPlanCode()))
            .filter(s -> s.getRenewsAt() != null && !s.getRenewsAt().isAfter(date))
            .map(s -> new BillableSubscription(s.getTenantId(), s.getPlanCode(), s.getRenewsAt()))
            .toList();
    }

    @Override
    public void advanceRenewal(UUID tenantId, LocalDate newRenewsAt) {
        Subscription subscription = subscriptionJpaRepository.findByTenantId(tenantId)
            .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));
        subscription.advanceRenewal(newRenewsAt);
        subscriptionJpaRepository.save(subscription);
    }

    @Override
    public void updateBillingStatus(UUID tenantId, BillingStatus billingStatus) {
        Subscription subscription = subscriptionJpaRepository.findByTenantId(tenantId)
            .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));
        subscription.updateBillingStatus(billingStatus);
        subscriptionJpaRepository.save(subscription);
    }

    @Override
    public Optional<String> findBillingContactEmail(UUID tenantId) {
        return findBillingContact(tenantId).map(StaffUser::getEmail);
    }

    @Override
    public Optional<String> findBillingContactPhone(UUID tenantId) {
        return findBillingContact(tenantId).map(StaffUser::getPhone);
    }

    private Optional<StaffUser> findBillingContact(UUID tenantId) {
        List<UUID> branchIds = branchJpaRepository.findByTenantId(tenantId).stream().map(Branch::getId).toList();
        if (branchIds.isEmpty()) {
            return Optional.empty();
        }
        return staffUserJpaRepository.findByBranchIdInAndRole(branchIds, StaffRole.ADMIN).stream().findFirst();
    }

    private TenantAdminOverview toOverview(Tenant tenant) {
        Subscription subscription = subscriptionJpaRepository.findByTenantId(tenant.getId())
            .orElseThrow(() -> new SubscriptionNotFoundException(tenant.getId()));
        List<Branch> branches = branchJpaRepository.findByTenantId(tenant.getId());
        List<UUID> branchIds = branches.stream().map(Branch::getId).toList();
        long staffCount = branchIds.isEmpty() ? 0 : staffUserJpaRepository.countByBranchIdIn(branchIds);

        return new TenantAdminOverview(
            tenant.getId(), tenant.getName(), tenant.getTaxNumber(), tenant.getStatus(), tenant.getCreatedAt(),
            subscription.getPlanCode(), subscription.getBillingStatus(), subscription.getStartedAt(), subscription.getRenewsAt(),
            branches.size(), (int) staffCount
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=SubscriptionTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Full compile + module boundary check**

Run: `cd backend && ./mvnw test -Dtest=ApplicationModulesTest`
Expected: PASS (platformadmin zaten `modules.tenant::domain`'e erişebiliyor — `package-info.java`'daki `allowedDependencies` listesinde var, bu yüzden yeni port metodları sınır ihlali oluşturmaz)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/tenant/domain/BillableSubscription.java backend/src/main/java/com/vetos/modules/tenant/domain/Subscription.java backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/StaffUserJpaRepository.java backend/src/test/java/com/vetos/modules/tenant/domain/SubscriptionTest.java
git commit -m "feat: extend TenantAdminPort with billing query/write methods"
```

---

### Task 5: Login enforcement — `Tenant.status == SUSPENDED` kontrolü

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/tenant/domain/exception/TenantSuspendedForbiddenException.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/application/LoginUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/tenant/application/LoginUseCaseTest.java`

**Interfaces:**
- Consumes: `TenantRepository.findById(UUID): Optional<Tenant>` (mevcut), `Tenant.getStatus(): TenantStatus` (mevcut)
- Produces: `TenantSuspendedForbiddenException` (403)

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.LoginCommand;
import com.vetos.modules.tenant.domain.*;
import com.vetos.modules.tenant.domain.exception.InvalidCredentialsException;
import com.vetos.modules.tenant.domain.exception.TenantSuspendedForbiddenException;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock private StaffUserRepository staffUserRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @Test
    void should_throwTenantSuspendedForbiddenException_when_tenantIsSuspended() {
        UUID branchId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        StaffUser staffUser = StaffUser.register(branchId, "Dr. Test", "test@example.com", "hash", StaffRole.VET);
        Branch branch = Branch.create(tenantId, "Merkez");
        Tenant tenant = Tenant.register("Test Klinik", "1234567890");
        tenant.suspend();

        when(staffUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        LoginUseCase useCase = new LoginUseCase(staffUserRepository, branchRepository, tenantRepository, passwordEncoder, jwtTokenProvider);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("test@example.com", "password123")))
            .isInstanceOf(TenantSuspendedForbiddenException.class);
    }

    @Test
    void should_succeed_when_tenantIsActive() {
        UUID branchId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        StaffUser staffUser = StaffUser.register(branchId, "Dr. Test", "test@example.com", "hash", StaffRole.VET);
        Branch branch = Branch.create(tenantId, "Merkez");
        Tenant tenant = Tenant.register("Test Klinik", "1234567890");
        tenant.activate();

        when(staffUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(jwtTokenProvider.generateToken(staffUser.getId(), tenantId, java.util.List.of(branch.getId()), "VET")).thenReturn("a-jwt-token");

        LoginUseCase useCase = new LoginUseCase(staffUserRepository, branchRepository, tenantRepository, passwordEncoder, jwtTokenProvider);
        var session = useCase.execute(new LoginCommand("test@example.com", "password123"));

        assertThat(session.token()).isEqualTo("a-jwt-token");
    }

    @Test
    void should_throwInvalidCredentials_when_passwordDoesNotMatch() {
        StaffUser staffUser = StaffUser.register(UUID.randomUUID(), "Dr. Test", "test@example.com", "hash", StaffRole.VET);
        when(staffUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        LoginUseCase useCase = new LoginUseCase(staffUserRepository, branchRepository, tenantRepository, passwordEncoder, jwtTokenProvider);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("test@example.com", "wrong")))
            .isInstanceOf(InvalidCredentialsException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=LoginUseCaseTest`
Expected: FAIL — `LoginUseCase`'in 5-argümanlı bir constructor'ı yok (şu an `TenantRepository` almıyor), `TenantSuspendedForbiddenException` bulunamıyor.

- [ ] **Step 3: Write minimal implementation**

`domain/exception/TenantSuspendedForbiddenException.java`:
```java
package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TenantSuspendedForbiddenException extends DomainException {
    public TenantSuspendedForbiddenException() {
        super(
            "TENANT_SUSPENDED",
            "Kliniginizin aboneligi askiya alinmis. Odeme kaydedildikten sonra hesabiniz otomatik olarak yeniden aktif olur."
        );
    }
}
```

`application/LoginUseCase.java` — tam dosya:
```java
package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.AuthSession;
import com.vetos.modules.tenant.application.dto.LoginCommand;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.modules.tenant.domain.TenantStatus;
import com.vetos.modules.tenant.domain.exception.InvalidCredentialsException;
import com.vetos.modules.tenant.domain.exception.TenantSuspendedForbiddenException;
import com.vetos.platform.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final StaffUserRepository staffUserRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public AuthSession execute(LoginCommand command) {
        StaffUser staffUser = staffUserRepository.findByEmail(command.email())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(command.password(), staffUser.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!staffUser.isActive()) {
            throw new InvalidCredentialsException();
        }

        Branch branch = branchRepository.findById(staffUser.getBranchId())
            .orElseThrow(InvalidCredentialsException::new);

        Tenant tenant = tenantRepository.findById(branch.getTenantId())
            .orElseThrow(InvalidCredentialsException::new);

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new TenantSuspendedForbiddenException();
        }

        String token = jwtTokenProvider.generateToken(
            staffUser.getId(), branch.getTenantId(), List.of(branch.getId()), staffUser.getRole().name()
        );
        return new AuthSession(
            token, staffUser.getId(), branch.getTenantId(), branch.getId(), staffUser.getFullName(), staffUser.getRole()
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=LoginUseCaseTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS (tüm testler yeşil — `LoginUseCase`'i çağıran başka yer varsa, örn. `AuthController`, constructor imzası değişmediği için Spring DI otomatik çözümlenir; ekstra kod değişikliği gerekmez)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/tenant/domain/exception/TenantSuspendedForbiddenException.java backend/src/main/java/com/vetos/modules/tenant/application/LoginUseCase.java backend/src/test/java/com/vetos/modules/tenant/application/LoginUseCaseTest.java
git commit -m "fix: enforce Tenant.status=SUSPENDED at login (Askıya Al was cosmetic before)"
```

---

### Task 6: Bildirim port'ları — `PlatformBillingEmailPort` + `PlatformBillingSmsPort` + mock adaptörler + config

Mock adaptörler `MockInviteEmailAdapter`/`MockTarbilAdapter` ile aynı desen — dedike birim testi yok (bu iki sınıf sadece loglar, davranışları use-case testlerinde Mockito ile port mock'lanarak dolaylı doğrulanır — bkz. Task 7-11).

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformBillingEmailPort.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformBillingSmsPort.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/MockPlatformBillingEmailAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/MockPlatformBillingSmsAdapter.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Produces: `PlatformBillingEmailPort{sendInvoiceIssued, sendInvoiceDueSoon, sendTenantSuspended}`, `PlatformBillingSmsPort{aynı 3 metod}` (her ikisi de `(PlatformInvoice invoice, String tenantName, String recipient)` veya `(String tenantName, String recipient)` — bkz. aşağıdaki tam imzalar)

- [ ] **Step 1: Write the ports**

`domain/PlatformBillingEmailPort.java`:
```java
package com.vetos.modules.platformadmin.domain;

/**
 * Platform faturalama e-posta bildirimlerini soyutlayan port -- TARBIL/davet
 * e-postasi ile ayni desen (@docs/architecture.md Bolum 3). Gercek bir
 * e-posta saglayicisi (SendGrid/SMTP) hesabi bu ortamda yok;
 * MockPlatformBillingEmailAdapter bu portu simule eder.
 */
public interface PlatformBillingEmailPort {
    void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientEmail);
    void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientEmail);
    void sendTenantSuspended(String tenantName, String recipientEmail);
}
```

`domain/PlatformBillingSmsPort.java`:
```java
package com.vetos.modules.platformadmin.domain;

/**
 * Platform faturalama SMS bildirimlerini soyutlayan port. Mevcut
 * modules.notification'daki NotificationSendPort BILINCLI olarak
 * kullanilmadi -- o modul kiraci-ici (klinik -> hayvan sahibi) mesajlasma
 * icin tasarlandi ve TenantContext'e bagimli; platform admin aksiyonlari
 * kiracilar-arasi oldugu icin platformadmin kendi izole altyapisini kurar
 * (architecture.md SS6.1'deki paralel altyapi felsefesiyle tutarli).
 * Gercek bir SMS saglayicisi hesabi bu ortamda yok;
 * MockPlatformBillingSmsAdapter bu portu simule eder.
 */
public interface PlatformBillingSmsPort {
    void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientPhone);
    void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientPhone);
    void sendTenantSuspended(String tenantName, String recipientPhone);
}
```

- [ ] **Step 2: Write the mock adapters**

`infrastructure/adapter/MockPlatformBillingEmailAdapter.java`:
```java
package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class MockPlatformBillingEmailAdapter implements PlatformBillingEmailPort {

    private final String paymentInstructions;

    MockPlatformBillingEmailAdapter(@Value("${platform-billing.payment-instructions}") String paymentInstructions) {
        this.paymentInstructions = paymentInstructions;
    }

    @Override
    public void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientEmail) {
        log.info(
            "Platform fatura e-postasi (mock, kesildi): to={}, klinik={}, tutar={}, sonOdemeTarihi={}, talimat={}",
            recipientEmail, tenantName, invoice.getAmount(), invoice.getDueDate(), paymentInstructions
        );
    }

    @Override
    public void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientEmail) {
        log.info(
            "Platform fatura e-postasi (mock, son gun yaklasiyor): to={}, klinik={}, tutar={}, sonOdemeTarihi={}",
            recipientEmail, tenantName, invoice.getAmount(), invoice.getDueDate()
        );
    }

    @Override
    public void sendTenantSuspended(String tenantName, String recipientEmail) {
        log.info("Platform fatura e-postasi (mock, askiya alindi): to={}, klinik={}", recipientEmail, tenantName);
    }
}
```

`infrastructure/adapter/MockPlatformBillingSmsAdapter.java`:
```java
package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class MockPlatformBillingSmsAdapter implements PlatformBillingSmsPort {

    @Override
    public void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientPhone) {
        log.info(
            "Platform fatura SMS'i (mock, kesildi): to={}, klinik={}, tutar={}, sonOdemeTarihi={}",
            recipientPhone, tenantName, invoice.getAmount(), invoice.getDueDate()
        );
    }

    @Override
    public void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientPhone) {
        log.info(
            "Platform fatura SMS'i (mock, son gun yaklasiyor): to={}, klinik={}, tutar={}, sonOdemeTarihi={}",
            recipientPhone, tenantName, invoice.getAmount(), invoice.getDueDate()
        );
    }

    @Override
    public void sendTenantSuspended(String tenantName, String recipientPhone) {
        log.info("Platform fatura SMS'i (mock, askiya alindi): to={}, klinik={}", recipientPhone, tenantName);
    }
}
```

- [ ] **Step 3: application.yml'e config ekle**

`backend/src/main/resources/application.yml`'in sonuna ekle:
```yaml
platform-billing:
  # Otomatik kart cekimi yok -- fatura e-posta/SMS bildirimlerinde gosterilen
  # sabit odeme talimati. Gercek/coklu banka hesabi yonetimi kapsam disi.
  payment-instructions: >
    Odemenizi IBAN TR00 0000 0000 0000 0000 0000 00 (Vetly Yazilim A.S.) hesabina
    yapip aciklama kismina klinik adinizi yazmaniz yeterlidir.
```

- [ ] **Step 4: Compile check**

Run: `cd backend && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformBillingEmailPort.java backend/src/main/java/com/vetos/modules/platformadmin/domain/PlatformBillingSmsPort.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/MockPlatformBillingEmailAdapter.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/MockPlatformBillingSmsAdapter.java backend/src/main/resources/application.yml
git commit -m "feat: add platform billing email/SMS notification ports (mock adapters)"
```

---

### Task 7: `RecordPlatformPaymentUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/dto/RecordPlatformPaymentCommand.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/RecordPlatformPaymentUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/RecordPlatformPaymentUseCaseTest.java`

**Interfaces:**
- Consumes: `PlatformInvoiceRepository` (Task 3), `PlatformPaymentRepository` (Task 3), `TenantAdminPort.updateBillingStatus/getOverview/activate` (Task 4, mevcut)
- Produces: `RecordPlatformPaymentCommand(UUID invoiceId, BigDecimal amount, PlatformPaymentMethod method, LocalDate paidAt, String notes, UUID recordedByAdminId)`, `RecordPlatformPaymentUseCase.execute(RecordPlatformPaymentCommand)`

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordPlatformPaymentUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private PlatformPaymentRepository platformPaymentRepository;
    @Mock private TenantAdminPort tenantAdminPort;

    @Test
    void should_markInvoicePaidAndReactivateTenant_when_tenantWasSuspended() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        UUID adminId = UUID.randomUUID();
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId, TenantStatus.SUSPENDED));

        RecordPlatformPaymentUseCase useCase = new RecordPlatformPaymentUseCase(platformInvoiceRepository, platformPaymentRepository, tenantAdminPort);
        useCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), new BigDecimal("500.00"), PlatformPaymentMethod.BANK_TRANSFER, today, "Havale ref: 1", adminId
        ));

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.PAID);
        verify(platformPaymentRepository).save(any(PlatformPayment.class));
        verify(tenantAdminPort).updateBillingStatus(tenantId, BillingStatus.ACTIVE);
        verify(tenantAdminPort).activate(tenantId);
    }

    @Test
    void should_notReactivateTenant_when_tenantWasAlreadyActive() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId, TenantStatus.ACTIVE));

        RecordPlatformPaymentUseCase useCase = new RecordPlatformPaymentUseCase(platformInvoiceRepository, platformPaymentRepository, tenantAdminPort);
        useCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), new BigDecimal("500.00"), PlatformPaymentMethod.CARD, today, null, UUID.randomUUID()
        ));

        verify(tenantAdminPort, never()).activate(tenantId);
    }

    @Test
    void should_throwPlatformInvoiceNotFoundException_when_invoiceDoesNotExist() {
        UUID invoiceId = UUID.randomUUID();
        when(platformInvoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        RecordPlatformPaymentUseCase useCase = new RecordPlatformPaymentUseCase(platformInvoiceRepository, platformPaymentRepository, tenantAdminPort);

        assertThatThrownBy(() -> useCase.execute(new RecordPlatformPaymentCommand(
            invoiceId, BigDecimal.TEN, PlatformPaymentMethod.OTHER, LocalDate.of(2026, 8, 28), null, UUID.randomUUID()
        ))).isInstanceOf(PlatformInvoiceNotFoundException.class);
    }

    @Test
    void should_throwInvalidTransition_when_invoiceAlreadyPaid() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        invoice.markPaid();
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        RecordPlatformPaymentUseCase useCase = new RecordPlatformPaymentUseCase(platformInvoiceRepository, platformPaymentRepository, tenantAdminPort);

        assertThatThrownBy(() -> useCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), new BigDecimal("500.00"), PlatformPaymentMethod.CARD, today, null, UUID.randomUUID()
        ))).isInstanceOf(PlatformInvoiceInvalidTransitionException.class);
    }

    private TenantAdminOverview overview(UUID tenantId, TenantStatus status) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", status, Instant.now(), "PRO", BillingStatus.PAST_DUE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 28), 1, 3
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RecordPlatformPaymentUseCaseTest`
Expected: FAIL — compilation error (`RecordPlatformPaymentCommand`/`RecordPlatformPaymentUseCase` bulunamıyor)

- [ ] **Step 3: Write minimal implementation**

`application/dto/RecordPlatformPaymentCommand.java`:
```java
package com.vetos.modules.platformadmin.application.dto;

import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordPlatformPaymentCommand(
    UUID invoiceId, BigDecimal amount, PlatformPaymentMethod method, LocalDate paidAt, String notes, UUID recordedByAdminId
) {}
```

`application/RecordPlatformPaymentUseCase.java`:
```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformPayment;
import com.vetos.modules.platformadmin.domain.PlatformPaymentRepository;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordPlatformPaymentUseCase {

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final PlatformPaymentRepository platformPaymentRepository;
    private final TenantAdminPort tenantAdminPort;

    @Transactional
    public void execute(RecordPlatformPaymentCommand command) {
        PlatformInvoice invoice = platformInvoiceRepository.findById(command.invoiceId())
            .orElseThrow(() -> new PlatformInvoiceNotFoundException(command.invoiceId()));

        invoice.markPaid();
        platformInvoiceRepository.save(invoice);

        platformPaymentRepository.save(PlatformPayment.record(
            invoice.getId(), command.amount(), command.method(), command.paidAt(), command.recordedByAdminId(), command.notes()
        ));

        tenantAdminPort.updateBillingStatus(invoice.getTenantId(), BillingStatus.ACTIVE);

        TenantAdminOverview overview = tenantAdminPort.getOverview(invoice.getTenantId());
        if (overview.status() == TenantStatus.SUSPENDED) {
            tenantAdminPort.activate(invoice.getTenantId());
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RecordPlatformPaymentUseCaseTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/application/dto/RecordPlatformPaymentCommand.java backend/src/main/java/com/vetos/modules/platformadmin/application/RecordPlatformPaymentUseCase.java backend/src/test/java/com/vetos/modules/platformadmin/application/RecordPlatformPaymentUseCaseTest.java
git commit -m "feat: add RecordPlatformPaymentUseCase (manual payment recording + auto-reactivation)"
```

---

### Task 8: `VoidPlatformInvoiceUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/VoidPlatformInvoiceUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/VoidPlatformInvoiceUseCaseTest.java`

**Interfaces:**
- Consumes: `PlatformInvoiceRepository` (Task 3)
- Produces: `VoidPlatformInvoiceUseCase.execute(UUID invoiceId)`

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoidPlatformInvoiceUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;

    @Test
    void should_voidInvoice_when_statusIsIssued() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        new VoidPlatformInvoiceUseCase(platformInvoiceRepository).execute(invoice.getId());

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.VOID);
    }

    @Test
    void should_throwPlatformInvoiceNotFoundException_when_invoiceDoesNotExist() {
        UUID invoiceId = UUID.randomUUID();
        when(platformInvoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new VoidPlatformInvoiceUseCase(platformInvoiceRepository).execute(invoiceId))
            .isInstanceOf(PlatformInvoiceNotFoundException.class);
    }

    @Test
    void should_throwInvalidTransition_when_invoiceAlreadyPaid() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        invoice.markPaid();
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> new VoidPlatformInvoiceUseCase(platformInvoiceRepository).execute(invoice.getId()))
            .isInstanceOf(PlatformInvoiceInvalidTransitionException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=VoidPlatformInvoiceUseCaseTest`
Expected: FAIL — compilation error

- [ ] **Step 3: Write minimal implementation**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoidPlatformInvoiceUseCase {

    private final PlatformInvoiceRepository platformInvoiceRepository;

    @Transactional
    public void execute(UUID invoiceId) {
        PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new PlatformInvoiceNotFoundException(invoiceId));
        invoice.voidInvoice();
        platformInvoiceRepository.save(invoice);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=VoidPlatformInvoiceUseCaseTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/application/VoidPlatformInvoiceUseCase.java backend/src/test/java/com/vetos/modules/platformadmin/application/VoidPlatformInvoiceUseCaseTest.java
git commit -m "feat: add VoidPlatformInvoiceUseCase"
```

---

### Task 9: `ListPlatformInvoicesForTenantUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/ListPlatformInvoicesForTenantUseCase.java`

**Interfaces:**
- Consumes: `PlatformInvoiceRepository.findByTenantId` (Task 3)
- Produces: `ListPlatformInvoicesForTenantUseCase.execute(UUID tenantId): List<PlatformInvoice>` (issuedAt'a göre azalan sıralı)

Bu use-case tek satırlık bir sıralama dışında iş mantığı içermiyor (`ListPlansUseCase`/`GetTenantAdminOverviewUseCase` ile aynı basitlikte) — dedike birim testi yok, Task 14'teki controller ile birlikte curl ile doğrulanır.

- [ ] **Step 1: Write the implementation directly**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListPlatformInvoicesForTenantUseCase {

    private final PlatformInvoiceRepository platformInvoiceRepository;

    @Transactional(readOnly = true)
    public List<PlatformInvoice> execute(UUID tenantId) {
        return platformInvoiceRepository.findByTenantId(tenantId).stream()
            .sorted(Comparator.comparing(PlatformInvoice::getIssuedAt).reversed())
            .toList();
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd backend && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/application/ListPlatformInvoicesForTenantUseCase.java
git commit -m "feat: add ListPlatformInvoicesForTenantUseCase"
```

---

### Task 10: `GenerateDueInvoicesUseCase` (+ `PlanRepository.findByCode`)

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlanRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlanJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlanRepositoryAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/GenerateDueInvoicesUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/GenerateDueInvoicesUseCaseTest.java`

**Interfaces:**
- Consumes: `TenantAdminPort.listSubscriptionsDueOnOrBefore/advanceRenewal/getOverview/findBillingContactEmail/findBillingContactPhone` (Task 4), `PlanRepository.findByCode` (bu task), `PlatformInvoiceRepository.findByTenantIdAndPeriodStart/save` (Task 3), `PlatformBillingEmailPort/PlatformBillingSmsPort.sendInvoiceIssued` (Task 6)
- Produces: `GenerateDueInvoicesUseCase.execute(LocalDate today)`

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.tenant.domain.BillableSubscription;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateDueInvoicesUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlanRepository planRepository;
    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private PlatformBillingEmailPort platformBillingEmailPort;
    @Mock private PlatformBillingSmsPort platformBillingSmsPort;

    @Test
    void should_generateInvoiceAndAdvanceRenewalAndNotify_when_subscriptionIsDue() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        BillableSubscription subscription = new BillableSubscription(tenantId, "PRO", today);
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("500.00"));

        when(tenantAdminPort.listSubscriptionsDueOnOrBefore(today)).thenReturn(List.of(subscription));
        when(platformInvoiceRepository.findByTenantIdAndPeriodStart(tenantId, today)).thenReturn(Optional.empty());
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.of("admin@klinik.com"));
        when(tenantAdminPort.findBillingContactPhone(tenantId)).thenReturn(Optional.of("+905551112233"));

        GenerateDueInvoicesUseCase useCase = new GenerateDueInvoicesUseCase(
            tenantAdminPort, planRepository, platformInvoiceRepository, platformBillingEmailPort, platformBillingSmsPort
        );
        useCase.execute(today);

        verify(platformInvoiceRepository).save(any(PlatformInvoice.class));
        verify(tenantAdminPort).advanceRenewal(tenantId, today.plusMonths(1));
        verify(platformBillingEmailPort).sendInvoiceIssued(any(), eq("Test Klinik"), eq("admin@klinik.com"));
        verify(platformBillingSmsPort).sendInvoiceIssued(any(), eq("Test Klinik"), eq("+905551112233"));
    }

    @Test
    void should_skipTenant_when_invoiceAlreadyExistsForPeriod() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        BillableSubscription subscription = new BillableSubscription(tenantId, "PRO", today);
        PlatformInvoice existing = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);

        when(tenantAdminPort.listSubscriptionsDueOnOrBefore(today)).thenReturn(List.of(subscription));
        when(platformInvoiceRepository.findByTenantIdAndPeriodStart(tenantId, today)).thenReturn(Optional.of(existing));

        GenerateDueInvoicesUseCase useCase = new GenerateDueInvoicesUseCase(
            tenantAdminPort, planRepository, platformInvoiceRepository, platformBillingEmailPort, platformBillingSmsPort
        );
        useCase.execute(today);

        verify(platformInvoiceRepository, never()).save(any());
        verify(tenantAdminPort, never()).advanceRenewal(any(), any());
    }

    @Test
    void should_skipTenant_when_planNoLongerExists() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        BillableSubscription subscription = new BillableSubscription(tenantId, "GHOST", today);

        when(tenantAdminPort.listSubscriptionsDueOnOrBefore(today)).thenReturn(List.of(subscription));
        when(platformInvoiceRepository.findByTenantIdAndPeriodStart(tenantId, today)).thenReturn(Optional.empty());
        when(planRepository.findByCode("GHOST")).thenReturn(Optional.empty());

        GenerateDueInvoicesUseCase useCase = new GenerateDueInvoicesUseCase(
            tenantAdminPort, planRepository, platformInvoiceRepository, platformBillingEmailPort, platformBillingSmsPort
        );
        useCase.execute(today);

        verify(platformInvoiceRepository, never()).save(any());
    }

    private TenantAdminOverview overview(UUID tenantId) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.ACTIVE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 28), 1, 3
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GenerateDueInvoicesUseCaseTest`
Expected: FAIL — `PlanRepository.findByCode` ve `GenerateDueInvoicesUseCase` bulunamıyor

- [ ] **Step 3: Write minimal implementation**

`domain/PlanRepository.java` — tam dosya:
```java
package com.vetos.modules.platformadmin.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository {
    Plan save(Plan plan);
    Optional<Plan> findById(UUID id);
    Optional<Plan> findByCode(String code);
    List<Plan> findAll();
    boolean existsByCode(String code);
    void deleteById(UUID id);
}
```

`infrastructure/persistence/PlanJpaRepository.java` — tam dosya:
```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PlanJpaRepository extends JpaRepository<Plan, UUID> {
    boolean existsByCode(String code);
    Optional<Plan> findByCode(String code);
}
```

`infrastructure/persistence/PlanRepositoryAdapter.java` — tam dosya:
```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PlanRepositoryAdapter implements PlanRepository {

    private final PlanJpaRepository jpaRepository;

    @Override
    public Plan save(Plan plan) { return jpaRepository.save(plan); }

    @Override
    public Optional<Plan> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<Plan> findByCode(String code) { return jpaRepository.findByCode(code); }

    @Override
    public List<Plan> findAll() { return jpaRepository.findAll(); }

    @Override
    public boolean existsByCode(String code) { return jpaRepository.existsByCode(code); }

    @Override
    public void deleteById(UUID id) { jpaRepository.deleteById(id); }
}
```

`application/GenerateDueInvoicesUseCase.java`:
```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.tenant.domain.BillableSubscription;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenerateDueInvoicesUseCase {

    private final TenantAdminPort tenantAdminPort;
    private final PlanRepository planRepository;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final PlatformBillingEmailPort platformBillingEmailPort;
    private final PlatformBillingSmsPort platformBillingSmsPort;

    @Transactional
    public void execute(LocalDate today) {
        for (BillableSubscription subscription : tenantAdminPort.listSubscriptionsDueOnOrBefore(today)) {
            if (platformInvoiceRepository.findByTenantIdAndPeriodStart(subscription.tenantId(), subscription.renewsAt()).isPresent()) {
                continue;
            }

            Plan plan = planRepository.findByCode(subscription.planCode()).orElse(null);
            if (plan == null) {
                log.warn("Fatura uretilemedi, plan bulunamadi: tenantId={}, planCode={}", subscription.tenantId(), subscription.planCode());
                continue;
            }

            LocalDate periodStart = subscription.renewsAt();
            LocalDate periodEnd = periodStart.plusMonths(1);
            PlatformInvoice invoice = PlatformInvoice.issue(
                subscription.tenantId(), subscription.planCode(), plan.getMonthlyPrice(), periodStart, periodEnd, today
            );
            platformInvoiceRepository.save(invoice);
            tenantAdminPort.advanceRenewal(subscription.tenantId(), periodEnd);

            notifyInvoiceIssued(invoice, subscription.tenantId());
        }
    }

    private void notifyInvoiceIssued(PlatformInvoice invoice, UUID tenantId) {
        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantId);
        tenantAdminPort.findBillingContactEmail(tenantId)
            .ifPresent(email -> platformBillingEmailPort.sendInvoiceIssued(invoice, overview.name(), email));
        tenantAdminPort.findBillingContactPhone(tenantId)
            .ifPresent(phone -> platformBillingSmsPort.sendInvoiceIssued(invoice, overview.name(), phone));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GenerateDueInvoicesUseCaseTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Run full PlanController-adjacent tests to check no regression**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/domain/PlanRepository.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlanJpaRepository.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/PlanRepositoryAdapter.java backend/src/main/java/com/vetos/modules/platformadmin/application/GenerateDueInvoicesUseCase.java backend/src/test/java/com/vetos/modules/platformadmin/application/GenerateDueInvoicesUseCaseTest.java
git commit -m "feat: add GenerateDueInvoicesUseCase (scheduler step 1)"
```

---

### Task 11: `RemindDueSoonInvoicesUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/RemindDueSoonInvoicesUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/RemindDueSoonInvoicesUseCaseTest.java`

**Interfaces:**
- Consumes: `PlatformInvoiceRepository.findByStatusAndDueDate` (Task 3), `TenantAdminPort.getOverview/findBillingContactEmail/findBillingContactPhone` (Task 4), `PlatformBillingEmailPort/PlatformBillingSmsPort.sendInvoiceDueSoon` (Task 6)
- Produces: `RemindDueSoonInvoicesUseCase.execute(LocalDate today)`

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemindDueSoonInvoicesUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlatformBillingEmailPort platformBillingEmailPort;
    @Mock private PlatformBillingSmsPort platformBillingSmsPort;

    @Test
    void should_notifyBothChannels_when_invoiceDueInTwoDays() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        LocalDate issuedOn = today.minusDays(5);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), issuedOn, issuedOn.plusMonths(1), issuedOn);

        when(platformInvoiceRepository.findByStatusAndDueDate(PlatformInvoiceStatus.ISSUED, today.plusDays(2))).thenReturn(List.of(invoice));
        when(tenantAdminPort.getOverview(invoice.getTenantId())).thenReturn(overview(invoice.getTenantId()));
        when(tenantAdminPort.findBillingContactEmail(invoice.getTenantId())).thenReturn(Optional.of("admin@klinik.com"));
        when(tenantAdminPort.findBillingContactPhone(invoice.getTenantId())).thenReturn(Optional.of("+905551112233"));

        new RemindDueSoonInvoicesUseCase(platformInvoiceRepository, tenantAdminPort, platformBillingEmailPort, platformBillingSmsPort).execute(today);

        verify(platformBillingEmailPort).sendInvoiceDueSoon(invoice, "Test Klinik", "admin@klinik.com");
        verify(platformBillingSmsPort).sendInvoiceDueSoon(invoice, "Test Klinik", "+905551112233");
    }

    @Test
    void should_skipSmsChannel_when_phoneNotFound() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        LocalDate issuedOn = today.minusDays(5);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), issuedOn, issuedOn.plusMonths(1), issuedOn);

        when(platformInvoiceRepository.findByStatusAndDueDate(PlatformInvoiceStatus.ISSUED, today.plusDays(2))).thenReturn(List.of(invoice));
        when(tenantAdminPort.getOverview(invoice.getTenantId())).thenReturn(overview(invoice.getTenantId()));
        when(tenantAdminPort.findBillingContactEmail(invoice.getTenantId())).thenReturn(Optional.of("admin@klinik.com"));
        when(tenantAdminPort.findBillingContactPhone(invoice.getTenantId())).thenReturn(Optional.empty());

        new RemindDueSoonInvoicesUseCase(platformInvoiceRepository, tenantAdminPort, platformBillingEmailPort, platformBillingSmsPort).execute(today);

        verify(platformBillingSmsPort, never()).sendInvoiceDueSoon(any(), any(), any());
        verify(platformBillingEmailPort).sendInvoiceDueSoon(any(), any(), any());
    }

    private TenantAdminOverview overview(UUID tenantId) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.ACTIVE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 27), 1, 3
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RemindDueSoonInvoicesUseCaseTest`
Expected: FAIL — compilation error

- [ ] **Step 3: Write minimal implementation**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RemindDueSoonInvoicesUseCase {

    private static final int REMINDER_DAYS_BEFORE_DUE = 2;

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PlatformBillingEmailPort platformBillingEmailPort;
    private final PlatformBillingSmsPort platformBillingSmsPort;

    @Transactional
    public void execute(LocalDate today) {
        LocalDate targetDueDate = today.plusDays(REMINDER_DAYS_BEFORE_DUE);
        for (PlatformInvoice invoice : platformInvoiceRepository.findByStatusAndDueDate(PlatformInvoiceStatus.ISSUED, targetDueDate)) {
            TenantAdminOverview overview = tenantAdminPort.getOverview(invoice.getTenantId());
            tenantAdminPort.findBillingContactEmail(invoice.getTenantId())
                .ifPresent(email -> platformBillingEmailPort.sendInvoiceDueSoon(invoice, overview.name(), email));
            tenantAdminPort.findBillingContactPhone(invoice.getTenantId())
                .ifPresent(phone -> platformBillingSmsPort.sendInvoiceDueSoon(invoice, overview.name(), phone));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RemindDueSoonInvoicesUseCaseTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/application/RemindDueSoonInvoicesUseCase.java backend/src/test/java/com/vetos/modules/platformadmin/application/RemindDueSoonInvoicesUseCaseTest.java
git commit -m "feat: add RemindDueSoonInvoicesUseCase (scheduler step 2)"
```

---

### Task 12: `FlagOverdueAndSuspendUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/FlagOverdueAndSuspendUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/FlagOverdueAndSuspendUseCaseTest.java`

**Interfaces:**
- Consumes: `PlatformInvoiceRepository.findByStatusAndDueDateBefore/save` (Task 3), `TenantAdminPort.updateBillingStatus/suspend/getOverview/findBillingContactEmail/findBillingContactPhone` (Task 4), `PlatformBillingEmailPort/PlatformBillingSmsPort.sendTenantSuspended` (Task 6)
- Produces: `FlagOverdueAndSuspendUseCase.execute(LocalDate today)`

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagOverdueAndSuspendUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlatformBillingEmailPort platformBillingEmailPort;
    @Mock private PlatformBillingSmsPort platformBillingSmsPort;

    @Test
    void should_flagOverdueAndSuspendTenantAndNotify_when_dueDatePassed() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        LocalDate issuedOn = today.minusDays(8);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), issuedOn, issuedOn.plusMonths(1), issuedOn);
        UUID tenantId = invoice.getTenantId();

        when(platformInvoiceRepository.findByStatusAndDueDateBefore(PlatformInvoiceStatus.ISSUED, today)).thenReturn(List.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.of("admin@klinik.com"));
        when(tenantAdminPort.findBillingContactPhone(tenantId)).thenReturn(Optional.of("+905551112233"));

        new FlagOverdueAndSuspendUseCase(platformInvoiceRepository, tenantAdminPort, platformBillingEmailPort, platformBillingSmsPort).execute(today);

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.OVERDUE);
        verify(platformInvoiceRepository).save(invoice);
        verify(tenantAdminPort).updateBillingStatus(tenantId, BillingStatus.PAST_DUE);
        verify(tenantAdminPort).suspend(tenantId);
        verify(platformBillingEmailPort).sendTenantSuspended("Test Klinik", "admin@klinik.com");
        verify(platformBillingSmsPort).sendTenantSuspended("Test Klinik", "+905551112233");
    }

    @Test
    void should_doNothing_when_noInvoicesPastDueDate() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        when(platformInvoiceRepository.findByStatusAndDueDateBefore(PlatformInvoiceStatus.ISSUED, today)).thenReturn(List.of());

        new FlagOverdueAndSuspendUseCase(platformInvoiceRepository, tenantAdminPort, platformBillingEmailPort, platformBillingSmsPort).execute(today);

        verify(tenantAdminPort, never()).suspend(any());
    }

    private TenantAdminOverview overview(UUID tenantId) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.ACTIVE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 20), 1, 3
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=FlagOverdueAndSuspendUseCaseTest`
Expected: FAIL — compilation error

- [ ] **Step 3: Write minimal implementation**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FlagOverdueAndSuspendUseCase {

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PlatformBillingEmailPort platformBillingEmailPort;
    private final PlatformBillingSmsPort platformBillingSmsPort;

    @Transactional
    public void execute(LocalDate today) {
        for (PlatformInvoice invoice : platformInvoiceRepository.findByStatusAndDueDateBefore(PlatformInvoiceStatus.ISSUED, today)) {
            invoice.markOverdue();
            platformInvoiceRepository.save(invoice);

            tenantAdminPort.updateBillingStatus(invoice.getTenantId(), BillingStatus.PAST_DUE);
            tenantAdminPort.suspend(invoice.getTenantId());

            TenantAdminOverview overview = tenantAdminPort.getOverview(invoice.getTenantId());
            tenantAdminPort.findBillingContactEmail(invoice.getTenantId())
                .ifPresent(email -> platformBillingEmailPort.sendTenantSuspended(overview.name(), email));
            tenantAdminPort.findBillingContactPhone(invoice.getTenantId())
                .ifPresent(phone -> platformBillingSmsPort.sendTenantSuspended(overview.name(), phone));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=FlagOverdueAndSuspendUseCaseTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/application/FlagOverdueAndSuspendUseCase.java backend/src/test/java/com/vetos/modules/platformadmin/application/FlagOverdueAndSuspendUseCaseTest.java
git commit -m "feat: add FlagOverdueAndSuspendUseCase (scheduler step 3)"
```

---

### Task 13: `PlatformBillingScheduler`

Schedulerlar bu kod tabanında dedike birim testi almıyor (`AppointmentReminderScheduler`da da yok) — 3 use-case'i sırayla çağırıp `today`'yi bir kez hesaplayan ince bir wiring katmanı, iş mantığının tamamı zaten Task 10-12'de test edildi.

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/scheduling/PlatformBillingScheduler.java`

**Interfaces:**
- Consumes: `GenerateDueInvoicesUseCase`, `RemindDueSoonInvoicesUseCase`, `FlagOverdueAndSuspendUseCase` (Task 10-12)

- [ ] **Step 1: Write the implementation directly**

```java
package com.vetos.modules.platformadmin.infrastructure.scheduling;

import com.vetos.modules.platformadmin.application.FlagOverdueAndSuspendUseCase;
import com.vetos.modules.platformadmin.application.GenerateDueInvoicesUseCase;
import com.vetos.modules.platformadmin.application.RemindDueSoonInvoicesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Gunde bir kez calisir -- AppointmentReminderScheduler ile ayni desen.
 * Uc adim sirayla: fatura uret, son-gun hatirlat, gecikeni askiya al. Her
 * adim kendi try/catch'inde -- biri patlarsa digerleri yine de calisir.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class PlatformBillingScheduler {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final GenerateDueInvoicesUseCase generateDueInvoicesUseCase;
    private final RemindDueSoonInvoicesUseCase remindDueSoonInvoicesUseCase;
    private final FlagOverdueAndSuspendUseCase flagOverdueAndSuspendUseCase;

    @Scheduled(cron = "0 0 6 * * *", zone = "Europe/Istanbul")
    public void runDailyBilling() {
        LocalDate today = LocalDate.now(ISTANBUL);

        try {
            generateDueInvoicesUseCase.execute(today);
        } catch (Exception e) {
            log.error("Fatura uretimi basarisiz: today={}", today, e);
        }
        try {
            remindDueSoonInvoicesUseCase.execute(today);
        } catch (Exception e) {
            log.error("Son-gun hatirlatmasi basarisiz: today={}", today, e);
        }
        try {
            flagOverdueAndSuspendUseCase.execute(today);
        } catch (Exception e) {
            log.error("Gecikme/askiya alma basarisiz: today={}", today, e);
        }
    }
}
```

- [ ] **Step 2: Full backend test suite + module boundary check**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS (tüm testler yeşil, `ApplicationModulesTest` dahil)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/scheduling/PlatformBillingScheduler.java
git commit -m "feat: wire PlatformBillingScheduler (daily 06:00 Europe/Istanbul)"
```

---

### Task 14: `PlatformInvoicesController` (API katmanı)

Controller'lar bu kod tabanında dedike birim testi almıyor — Task 19'daki curl ile uçtan uca doğrulanıyor.

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/PlatformInvoiceResponse.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/RecordPlatformPaymentRequest.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/api/PlatformInvoicesController.java`

**Interfaces:**
- Consumes: `ListPlatformInvoicesForTenantUseCase` (Task 9), `RecordPlatformPaymentUseCase` (Task 7), `VoidPlatformInvoiceUseCase` (Task 8), `AuthenticatedPlatformAdmin` (mevcut, `platform.security`)
- Produces: `GET /api/v1/platform-admin/tenants/{tenantId}/invoices`, `POST .../invoices/{invoiceId}/payments`, `POST .../invoices/{invoiceId}/void`

- [ ] **Step 1: Write the DTOs**

`api/dto/PlatformInvoiceResponse.java`:
```java
package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlatformInvoiceResponse(
    UUID id, String planCode, BigDecimal amount, LocalDate periodStart, LocalDate periodEnd,
    LocalDate dueDate, PlatformInvoiceStatus status, Instant issuedAt, Instant paidAt
) {
    public static PlatformInvoiceResponse from(PlatformInvoice invoice) {
        return new PlatformInvoiceResponse(
            invoice.getId(), invoice.getPlanCode(), invoice.getAmount(), invoice.getPeriodStart(), invoice.getPeriodEnd(),
            invoice.getDueDate(), invoice.getStatus(), invoice.getIssuedAt(), invoice.getPaidAt()
        );
    }
}
```

`api/dto/RecordPlatformPaymentRequest.java`:
```java
package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordPlatformPaymentRequest(
    @NotNull BigDecimal amount, @NotNull PlatformPaymentMethod method, @NotNull LocalDate paidAt, String notes
) {}
```

- [ ] **Step 2: Write the controller**

```java
package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.PlatformInvoiceResponse;
import com.vetos.modules.platformadmin.api.dto.RecordPlatformPaymentRequest;
import com.vetos.modules.platformadmin.application.ListPlatformInvoicesForTenantUseCase;
import com.vetos.modules.platformadmin.application.RecordPlatformPaymentUseCase;
import com.vetos.modules.platformadmin.application.VoidPlatformInvoiceUseCase;
import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform-admin/tenants/{tenantId}/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformInvoicesController {

    private final ListPlatformInvoicesForTenantUseCase listPlatformInvoicesForTenantUseCase;
    private final RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;
    private final VoidPlatformInvoiceUseCase voidPlatformInvoiceUseCase;

    @GetMapping
    public List<PlatformInvoiceResponse> list(@PathVariable UUID tenantId) {
        return listPlatformInvoicesForTenantUseCase.execute(tenantId).stream().map(PlatformInvoiceResponse::from).toList();
    }

    @PostMapping("/{invoiceId}/payments")
    public void recordPayment(
        @AuthenticationPrincipal AuthenticatedPlatformAdmin principal,
        @PathVariable UUID tenantId,
        @PathVariable UUID invoiceId,
        @RequestBody @Valid RecordPlatformPaymentRequest request
    ) {
        recordPlatformPaymentUseCase.execute(new RecordPlatformPaymentCommand(
            invoiceId, request.amount(), request.method(), request.paidAt(), request.notes(), principal.platformAdminId()
        ));
    }

    @PostMapping("/{invoiceId}/void")
    public void voidInvoice(@PathVariable UUID tenantId, @PathVariable UUID invoiceId) {
        voidPlatformInvoiceUseCase.execute(invoiceId);
    }
}
```

- [ ] **Step 3: Full backend build**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/api/dto/PlatformInvoiceResponse.java backend/src/main/java/com/vetos/modules/platformadmin/api/dto/RecordPlatformPaymentRequest.java backend/src/main/java/com/vetos/modules/platformadmin/api/PlatformInvoicesController.java
git commit -m "feat: add PlatformInvoicesController (list/record-payment/void)"
```

---

### Task 15: Frontend — `platformAdminApi.ts` + `tenantBadges.ts` genişletmesi

**Files:**
- Modify: `frontend/src/api/platformAdminApi.ts`
- Modify: `frontend/src/pages/platform-admin/tenantBadges.ts`

**Interfaces:**
- Produces: `PlatformInvoiceStatus`, `PlatformPaymentMethod`, `PlatformInvoice` (TS tipleri), `platformAdminApi.listInvoices/recordInvoicePayment/voidInvoice`, `PLATFORM_INVOICE_STATUS_LABELS`, `PLATFORM_INVOICE_STATUS_TONES`

- [ ] **Step 1: `platformAdminApi.ts`'e ekle**

Mevcut `export const platformAdminApi = { ... }` bloğunun ÜSTÜNE yeni tipler:
```typescript
export type PlatformInvoiceStatus = 'ISSUED' | 'PAID' | 'OVERDUE' | 'VOID';
export type PlatformPaymentMethod = 'BANK_TRANSFER' | 'CARD' | 'OTHER';

export interface PlatformInvoice {
  id: string;
  planCode: string;
  amount: number;
  periodStart: string;
  periodEnd: string;
  dueDate: string;
  status: PlatformInvoiceStatus;
  issuedAt: string;
  paidAt: string | null;
}

export interface RecordPlatformPaymentPayload {
  amount: number;
  method: PlatformPaymentMethod;
  paidAt: string;
  notes?: string;
}
```

`platformAdminApi` nesnesinin İÇİNE (mevcut `deletePlan` satırının altına), üç yeni metod:
```typescript
  listInvoices: (tenantId: string) =>
    platformAdminClient.get<PlatformInvoice[]>(`/api/v1/platform-admin/tenants/${tenantId}/invoices`),
  recordInvoicePayment: (tenantId: string, invoiceId: string, payload: RecordPlatformPaymentPayload) =>
    platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${tenantId}/invoices/${invoiceId}/payments`, payload),
  voidInvoice: (tenantId: string, invoiceId: string) =>
    platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${tenantId}/invoices/${invoiceId}/void`),
```

- [ ] **Step 2: `tenantBadges.ts`'e ekle**

Dosyanın üstündeki import satırını güncelle:
```typescript
import { BadgeTone } from '../../components/ui/Badge';
import { BillingStatus, PlatformInvoiceStatus, TenantStatus } from '../../api/platformAdminApi';
```

`BILLING_STATUS_TONES` bloğunun altına ekle:
```typescript
export const PLATFORM_INVOICE_STATUS_LABELS: Record<PlatformInvoiceStatus, string> = {
  ISSUED: 'Kesildi',
  PAID: 'Ödendi',
  OVERDUE: 'Gecikti',
  VOID: 'İptal',
};

export const PLATFORM_INVOICE_STATUS_TONES: Record<PlatformInvoiceStatus, BadgeTone> = {
  ISSUED: 'neutral',
  PAID: 'success',
  OVERDUE: 'danger',
  VOID: 'neutral',
};
```

- [ ] **Step 3: TypeScript derleme kontrolü**

Run: `cd frontend && npx tsc --noEmit`
Expected: hata yok (henüz kullanılmayan export'lar TS'te hata vermez)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/platformAdminApi.ts frontend/src/pages/platform-admin/tenantBadges.ts
git commit -m "feat: add platform invoice API types/endpoints (frontend)"
```

---

### Task 16: Frontend — `TenantDetailPage.tsx` Faturalar kartı + ödeme modalı

**Files:**
- Modify: `frontend/src/pages/platform-admin/TenantDetailPage.tsx`

**Interfaces:**
- Consumes: `platformAdminApi.listInvoices/recordInvoicePayment/voidInvoice` (Task 15), `PLATFORM_INVOICE_STATUS_LABELS/TONES` (Task 15)

- [ ] **Step 1: Importları güncelle**

Dosyanın en üstündeki import bloğunu şu şekilde değiştir:
```typescript
import { FormEvent, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import {
  BillingStatus, Plan, PlatformInvoice, PlatformPaymentMethod, platformAdminApi, TenantAdminOverview,
} from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './PlatformAdminPages.module.css';
import {
  BILLING_STATUS_LABELS, BILLING_STATUS_TONES, formatDate, PLATFORM_INVOICE_STATUS_LABELS,
  PLATFORM_INVOICE_STATUS_TONES, TENANT_STATUS_LABELS, TENANT_STATUS_TONES,
} from './tenantBadges';
```

- [ ] **Step 2: State ve `load()` fonksiyonunu genişlet**

`const [form, setForm] = useState<SubscriptionFormState>(...)` satırının altına ekle:
```typescript
  const [invoices, setInvoices] = useState<PlatformInvoice[]>([]);
  const [paymentInvoiceId, setPaymentInvoiceId] = useState<string | null>(null);
  const [paymentForm, setPaymentForm] = useState({ amount: '', method: 'BANK_TRANSFER' as PlatformPaymentMethod, paidAt: '', notes: '' });
  const [savingPayment, setSavingPayment] = useState(false);
```

Mevcut `function load() { ... }` fonksiyonunu (sadece `platformAdminApi.getTenant(tenantId).then(setTenant).catch(...)` içeriyor) şu şekilde genişlet:
```typescript
  function load() {
    if (!tenantId) return;
    platformAdminApi.getTenant(tenantId).then(setTenant).catch((err) => setError(errorMessageOf(err)));
    platformAdminApi.listInvoices(tenantId).then(setInvoices).catch(() => undefined);
  }
```

- [ ] **Step 3: Ödeme modalı işlevlerini ekle**

Mevcut `async function handleToggleStatus() { ... }` fonksiyonunun ALTINA ekle:
```typescript
  function openPaymentModal(invoiceId: string) {
    setPaymentForm({ amount: '', method: 'BANK_TRANSFER', paidAt: new Date().toISOString().slice(0, 10), notes: '' });
    setPaymentInvoiceId(invoiceId);
  }

  async function handleRecordPayment(e: FormEvent) {
    e.preventDefault();
    if (!tenantId || !paymentInvoiceId) return;
    setSavingPayment(true);
    setError(null);
    try {
      await platformAdminApi.recordInvoicePayment(tenantId, paymentInvoiceId, {
        amount: Number(paymentForm.amount),
        method: paymentForm.method,
        paidAt: paymentForm.paidAt,
        notes: paymentForm.notes || undefined,
      });
      setPaymentInvoiceId(null);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSavingPayment(false);
    }
  }

  async function handleVoidInvoice(invoiceId: string) {
    if (!tenantId) return;
    if (!window.confirm('Bu faturayi iptal etmek istediginizden emin misiniz?')) return;
    try {
      await platformAdminApi.voidInvoice(tenantId, invoiceId);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    }
  }
```

- [ ] **Step 4: JSX'e Faturalar kartını ve ödeme modalını ekle**

Mevcut `</div>` (kapanan `detailGrid` div'i) ile mevcut `<Modal open={modalOpen} ...>` (Abonelik Değiştir modalı) arasına yeni bir kart ekle:
```tsx
          <div className={styles.tableCard}>
            <div className={styles.cardTitle}>Faturalar</div>
            {invoices.length === 0 ? (
              <div className={styles.empty}>Henüz fatura kesilmedi</div>
            ) : (
              invoices.map((inv) => (
                <div key={inv.id} className={styles.row}>
                  <div>{formatDate(inv.periodStart)} — {formatDate(inv.periodEnd)}</div>
                  <div>{inv.amount.toFixed(2)} ₺</div>
                  <div>Son ödeme: {formatDate(inv.dueDate)}</div>
                  <div>
                    <Badge tone={PLATFORM_INVOICE_STATUS_TONES[inv.status]}>{PLATFORM_INVOICE_STATUS_LABELS[inv.status]}</Badge>
                  </div>
                  <div className={styles.modalActions}>
                    {(inv.status === 'ISSUED' || inv.status === 'OVERDUE') && (
                      <>
                        <Button variant="primary" onClick={() => openPaymentModal(inv.id)}>
                          Ödeme Kaydet
                        </Button>
                        <Button variant="danger" onClick={() => handleVoidInvoice(inv.id)}>
                          İptal Et
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>

          <Modal open={paymentInvoiceId !== null} onClose={() => setPaymentInvoiceId(null)} width={420}>
            <form onSubmit={handleRecordPayment}>
              <div className={styles.modalTitle}>Ödeme Kaydet</div>

              <FieldWrap label="Tutar (₺)">
                <Input
                  type="number" step="0.01" min={0} required
                  value={paymentForm.amount}
                  onChange={(e) => setPaymentForm((f) => ({ ...f, amount: e.target.value }))}
                />
              </FieldWrap>
              <FieldWrap label="Yöntem">
                <Select
                  value={paymentForm.method}
                  onChange={(e) => setPaymentForm((f) => ({ ...f, method: e.target.value as PlatformPaymentMethod }))}
                >
                  <option value="BANK_TRANSFER">Banka Havalesi</option>
                  <option value="CARD">Kart</option>
                  <option value="OTHER">Diğer</option>
                </Select>
              </FieldWrap>
              <FieldWrap label="Ödeme Tarihi">
                <Input
                  type="date" required
                  value={paymentForm.paidAt}
                  onChange={(e) => setPaymentForm((f) => ({ ...f, paidAt: e.target.value }))}
                />
              </FieldWrap>
              <FieldWrap label="Not (opsiyonel)">
                <Input
                  value={paymentForm.notes}
                  onChange={(e) => setPaymentForm((f) => ({ ...f, notes: e.target.value }))}
                  placeholder="Örn. Havale referans no"
                />
              </FieldWrap>

              <div className={styles.modalActions}>
                <Button type="button" variant="secondary" onClick={() => setPaymentInvoiceId(null)}>
                  Vazgeç
                </Button>
                <Button type="submit" variant="primary" disabled={savingPayment}>
                  {savingPayment ? 'Kaydediliyor...' : 'Kaydet'}
                </Button>
              </div>
            </form>
          </Modal>
```

`styles.tableCard`/`styles.row`/`styles.empty` sınıfları `PlatformAdminPages.module.css`'de zaten var (`PlatformBillingPage.tsx` aynı sınıfları kullanıyor) — yeni CSS gerekmiyor. `styles.row`'un grid-template-columns'u mevcut haliyle 4 sütunlu olmayabilir; bu satır 5 alan (dönem/tutar/son ödeme/durum/aksiyon) gösterdiği için tarayıcıda görsel kontrol Task 19'da yapılacak, gerekirse `PlatformAdminPages.module.css`'e invoice satırı için özel bir grid class eklenir (bu adımda placeholder BIRAKILMAZ — Task 19'da görsel kontrol sırasında gerekirse küçük bir CSS düzeltmesi yapılır ve ayrı commit edilir).

- [ ] **Step 5: TypeScript derleme kontrolü**

Run: `cd frontend && npx tsc --noEmit`
Expected: hata yok

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/platform-admin/TenantDetailPage.tsx
git commit -m "feat: add Faturalar card + payment modal to TenantDetailPage"
```

---

### Task 17: Frontend — `PlatformBillingPage.tsx` açıklama metni güncellemesi

**Files:**
- Modify: `frontend/src/pages/platform-admin/PlatformBillingPage.tsx`

- [ ] **Step 1: Metni güncelle**

```typescript
      <p className={billingStyles.note}>
        Gerçek bir ödeme sağlayıcı entegrasyonu (otomatik kart çekimi) henüz yok — faturalar otomatik kesiliyor,
        ödemeler kiracı tarafından yapılıp platform admin tarafından elle kaydediliyor. Aşağıdaki özet gerçek
        fatura/ödeme kayıtlarına dayanıyor.
      </p>
```
(mevcut `<p className={billingStyles.note}>Gerçek bir ödeme tahsilat entegrasyonu henüz yok — ...</p>` satırının yerine geçer)

- [ ] **Step 2: TypeScript derleme + tam build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/platform-admin/PlatformBillingPage.tsx
git commit -m "docs: update PlatformBillingPage copy to reflect real invoice data"
```

---

### Task 18: Tam backend test suite + modül sınırı doğrulaması

**Files:** (yok — sadece doğrulama)

- [ ] **Step 1: Tüm backend testlerini çalıştır**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS — `ApplicationModulesTest` (modül sınırı), tüm yeni testler (Task 1-12'de yazılanlar, toplam ~25 yeni test) + önceden var olan tüm testler yeşil.

- [ ] **Step 2: Frontend tam build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 3: Mevcut Playwright altın yolunu çalıştır (regresyon kontrolü)**

Run: `cd frontend && npx playwright test`
Expected: PASS — bu değişiklikler platform-admin alanında, mevcut klinik golden-path akışını etkilememeli.

Bu adımda hata bulunursa bir sonraki task'a geçmeden düzelt.

---

### Task 19: Manuel uçtan uca doğrulama (izole port, gerçek Postgres)

Bu son task kod yazmıyor — mevcut backend'i (kullanıcının 8080'deki oturumuna dokunmadan) ayrı bir portta (8081) başlatıp gerçek Postgres'e karşı curl ile doğruluyor. Scheduler'ın 3 adımı zaten Task 10-12'de deterministik `today` parametresiyle birim test edildi (gerçek zaman geçmesini beklemeye gerek yok) — bu task'ta odak, **API katmanının** ve **login enforcement**'ın gerçek bir ortamda uçtan uca çalıştığını doğrulamak.

- [ ] **Step 1: Backend'i izole bir portta başlat**

```bash
cd backend && ./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--server.port=8081 > /tmp/platform-billing-verify.log 2>&1 &
```
`grep -q "Started VetosApplication" /tmp/platform-billing-verify.log` ile başlamayı bekle (Flyway `V26` migration'ının başarıyla uygulandığını log'da doğrula: `grep "26 - platform invoices" /tmp/platform-billing-verify.log`).

- [ ] **Step 2: Klinik kaydı + platform admin girişi**

```bash
BASE="http://localhost:8081"
RUNID=$(date +%s)
REG=$(curl -s -X POST $BASE/api/v1/auth/register-clinic -H "Content-Type: application/json" \
  -d '{"tenantName":"Billing Verify Klinik","taxNumber":"9998887771","branchName":"Merkez","adminFullName":"Admin Verify","adminEmail":"billing-admin-'"$RUNID"'@example.com","adminPassword":"password123"}')
TENANT_ADMIN_TOKEN=$(echo "$REG" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
TENANT_ID=$(echo "$REG" | sed -n 's/.*"tenantId":"\([^"]*\)".*/\1/p')

PA_LOGIN=$(curl -s -X POST $BASE/api/v1/platform-admin/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@myvet.local","password":"change-me-local-dev-only"}')
PA_TOKEN=$(echo "$PA_LOGIN" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "tenant admin token: ${TENANT_ADMIN_TOKEN:0:10}... / platform admin token: ${PA_TOKEN:0:10}..."
```
Beklenen: her iki token da boş değil (`.env`'de platform admin şifresi override edildiyse o değeri kullanın).

- [ ] **Step 3: Askıya alma → login reddi → geri açma → login başarılı (login enforcement doğrulaması)**

```bash
echo "--- once basarili login (SUSPENDED oncesi) ---"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"billing-admin-'"$RUNID"'@example.com","password":"password123"}'

echo "--- platform admin tenanti askiya aliyor ---"
curl -s -o /dev/null -X POST $BASE/api/v1/platform-admin/tenants/$TENANT_ID/suspend -H "Authorization: Bearer $PA_TOKEN"

echo "--- SUSPENDED iken login (403 beklenir) ---"
curl -s -i -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"billing-admin-'"$RUNID"'@example.com","password":"password123"}' | head -1

echo "--- platform admin tenanti tekrar aktif ediyor ---"
curl -s -o /dev/null -X POST $BASE/api/v1/platform-admin/tenants/$TENANT_ID/activate -H "Authorization: Bearer $PA_TOKEN"

echo "--- ACTIVE iken login (200 beklenir) ---"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"billing-admin-'"$RUNID"'@example.com","password":"password123"}'
```
Beklenen sıra: `200`, sonra `403` (`TENANT_SUSPENDED`), sonra `200`.

- [ ] **Step 4: Fatura üretimi + ödeme kaydı + otomatik geri açılma (manuel SQL ile fatura enjekte edilerek — scheduler zaten Task 10-12'de test edildi)**

Öncelikle tenant'ı PRO plana geçirip (`GenerateDueInvoicesUseCase`'in gerçekten üretebileceği bir plan olması için) elle bir fatura ekleyin — plan yoksa önce oluşturun:

```bash
curl -s -o /dev/null -X POST $BASE/api/v1/platform-admin/plans -H "Authorization: Bearer $PA_TOKEN" -H "Content-Type: application/json" \
  -d '{"code":"PRO","name":"Pro Plan","monthlyPrice":500.00}'
curl -s -o /dev/null -X PUT $BASE/api/v1/platform-admin/tenants/$TENANT_ID/subscription -H "Authorization: Bearer $PA_TOKEN" -H "Content-Type: application/json" \
  -d '{"planCode":"PRO","billingStatus":"ACTIVE","renewsAt":"2026-08-28"}'

INVOICE_ID=$(docker exec backend-postgres-1 psql -U myvet -d myvet -t -c \
  "INSERT INTO platform_invoices (id, tenant_id, plan_code, amount, period_start, period_end, due_date, status, issued_at) \
   VALUES (gen_random_uuid(), '$TENANT_ID', 'PRO', 500.00, '2026-08-28', '2026-09-28', '2026-09-04', 'ISSUED', now()) \
   RETURNING id;" | tr -d ' ')
echo "invoice: $INVOICE_ID"

echo "--- fatura listesi (ISSUED gorunmeli) ---"
curl -s $BASE/api/v1/platform-admin/tenants/$TENANT_ID/invoices -H "Authorization: Bearer $PA_TOKEN"

echo "--- odeme kaydet ---"
curl -s -i -X POST $BASE/api/v1/platform-admin/tenants/$TENANT_ID/invoices/$INVOICE_ID/payments \
  -H "Authorization: Bearer $PA_TOKEN" -H "Content-Type: application/json" \
  -d '{"amount":500.00,"method":"BANK_TRANSFER","paidAt":"2026-08-28","notes":"Havale ref: TEST"}' | head -1

echo "--- fatura listesi (PAID gorunmeli) ---"
curl -s $BASE/api/v1/platform-admin/tenants/$TENANT_ID/invoices -H "Authorization: Bearer $PA_TOKEN"
```
Beklenen: ödeme kaydı `200`, ikinci liste çağrısında `"status":"PAID"`.

- [ ] **Step 5: Mock bildirim loglarını doğrula**

```bash
grep "Platform fatura" /tmp/platform-billing-verify.log | tail -5
```
Beklenen: en az bir "askiya alindi" veya ödeme ile ilgili log satırı yok (bu senaryoda invoice elle eklendiği için `sendInvoiceIssued` tetiklenmedi) — ama Task 3'teki suspend/activate curl'leri sırasında `FlagOverdueAndSuspendUseCase` tetiklenmediği için de bildirim logu beklenmiyor. Bu adımın asıl amacı log formatının hatasız basıldığını (exception fırlatmadığını) görmek — `grep -i "error\|exception"` ile log'da beklenmeyen bir hata olmadığını doğrulayın.

- [ ] **Step 6: İzole instance'ı kapat**

```bash
PID=$(netstat -ano | grep ":8081" | grep LISTENING | awk '{print $5}' | head -1)
[ -n "$PID" ] && taskkill //F //PID $PID
```

- [ ] **Step 7: `docs/superpowers/specs/2026-08-28-platform-abonelik-faturalama-design.md`'nin başındaki durumu güncelle**

`**Durum:** Onaylandı — implementasyon planı bekleniyor` satırını şuna çevir: `**Durum:** Implementasyon tamamlandı`

- [ ] **Step 8: Son commit**

```bash
git add docs/superpowers/specs/2026-08-28-platform-abonelik-faturalama-design.md
git commit -m "docs: mark platform billing spec as implemented"
```

---

## Spec Coverage Checklist (self-review)

- §3.1 `PlatformInvoice` veri modeli + durum geçişleri → Task 1
- §3.2 `PlatformPayment` veri modeli → Task 2
- §3.3 Migration → Task 1
- §4 Scheduler 3 adım (üret/hatırlat/askıya al, 7 gün toplam, ek grace yok) → Task 10-13
- §4.1 E-posta + SMS bildirimi (3 dokunuş), alıcı lookup, config → Task 4 (lookup), 6 (port/adapter), 10-12 (tetikleyiciler)
- §4.2 Otomatik askıya alma + geri açılma + login enforcement düzeltmesi → Task 5 (login), 12 (suspend), 7 (reactivate)
- §5 Use-case listesi → Task 7-12
- §6 API → Task 14
- §7 Frontend (`TenantDetailPage`, `PlatformBillingPage`) → Task 16-17
- §8 Test stratejisi (TDD + curl e2e) → tüm task'lara dağıtılmış + Task 19
