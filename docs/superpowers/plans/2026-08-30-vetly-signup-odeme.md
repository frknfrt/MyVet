# vetly.com Ödeme Sonrası Otomatik Tenant Oluşturma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** vetly.com'da bir ziyaretçinin plan seçip ödeme yapması sonrası, ödeme başarılı olunca otomatik olarak yeni bir klinik/tenant oluşturulmasını sağlamak.

**Architecture:** Yeni `TenantSignupRequest` (bekleyen kayıt) domain nesnesi + iki yeni use-case (`InitiateSignupCheckoutUseCase`, `HandleSignupPaymentCallbackUseCase`) — sub-proje #3'ün `PaymentGatewayPort`'unu yeniden kullanır. Mevcut `PublicPaymentCallbackController`'a bir try/catch fallback eklenir (önce mevcut-fatura-ödemesi dener, `PlatformInvoiceNotFoundException` gelirse yeni-kayıt-ödemesi dener) — #3'ün kodu değişmez. Yönetici hesabı, mevcut `StaffInvite`/`AcceptStaffInviteUseCase`/`/davet/:token` akışı yeniden kullanılarak (davetçi alanı nullable yapılarak "sistem daveti" temsil edilir) kurulur — bu akış da değişmez. vetly-site (ayrı proje) gerçek plan verisine bağlanır ve checkout başlatan bir form kazanır.

**Tech Stack:** Spring Boot 3.5 / Java 21 (backend, `modules/platformadmin` + paylaşılan `modules/tenant`), React 19 / TypeScript (vetly-site), PostgreSQL + Flyway.

**Spec:** `docs/superpowers/specs/2026-08-30-vetly-signup-odeme-design.md`

## Global Constraints

- Sadece aylık ödeme — yıllık seçeneği kapsam dışı.
- vetly-site'taki form minimal kalır: klinik adı, yönetici adı-soyadı, e-posta, telefon (opsiyonel) — vergi no/adres/şehir gibi alanlar **placeholder** (`"-"`) ile oluşturulur, klinik ilk girişte panelden tamamlar.
- Yönetici şifresi ziyaretçi tarafından formda **girilmez** — mevcut `StaffInvite`/`/davet/:token` akışı yeniden kullanılır.
- `TenantAdminPort.createTenant(...)` metodu (platform admin'in manuel akışı) **hiç değişmez** — yeni bir `createTenantForPaidSignup(...)` metodu eklenir, mevcut metod dokunulmadan kalır.
- `HandlePaymentCallbackUseCase`, `InitiateInvoiceCheckoutUseCase`, `PaymentGatewayPort`, `IyzicoPaymentGatewayAdapter` (sub-proje #3) **hiç değişmez** — sadece `PublicPaymentCallbackController`'a (ve onun mevcut testine) bir fallback eklenir.
- Son kullanılan migration `V29` — bu plan `V30` ile devam eder.
- Bu repoda controller-seviyesinde otomatik test yok (Mockito ile controller birim testi hariç — #3'te `PublicPaymentCallbackControllerTest` emsali var); persistence-adapter seviyesinde de otomatik test yok (`TenantAdminPortAdapter` gibi JPA adaptörleri sadece curl ile uçtan uca doğrulanır, hiçbir yerde `@DataJpaTest` kullanılmıyor).
- `LocalDate.now()` gibi saat okumaları use-case içine gömülmez — çağıran sınır (controller) `today`'i parametre olarak geçirir (istisna: `Subscription.startTrial()`'ın kendisi zaten `LocalDate.now()` çağırıyor — yeni `Subscription.startPaid(...)` de aynı dosyanın mevcut yerel konvansiyonunu takip eder).

---

### Task 1: `TenantSignupRequest` domain nesnesi + persistence + migration

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/TenantSignupRequestStatus.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/TenantSignupRequest.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/TenantSignupRequestRepository.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/TenantSignupRequestJpaRepository.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/TenantSignupRequestRepositoryAdapter.java`
- Create: `backend/src/main/resources/db/migration/V30__signup_requests.sql`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/domain/TenantSignupRequestTest.java`

**Interfaces:**
- Produces: `TenantSignupRequest.create(String clinicName, String adminFullName, String adminEmail, String phone, String planCode): TenantSignupRequest` (statik factory), `.complete()`, `.isCompleted(): boolean`, getter'lar (`getId()`, `getClinicName()`, `getAdminFullName()`, `getAdminEmail()`, `getPhone()`, `getPlanCode()`). `TenantSignupRequestRepository.save(TenantSignupRequest): TenantSignupRequest`, `.findById(UUID): Optional<TenantSignupRequest>`. Task 3 ve 4 bunları kullanacak.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/modules/platformadmin/domain/TenantSignupRequestTest.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSignupRequestTest {

    @Test
    void should_startAsPending_when_created() {
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", "+905551112233", "PRO"
        );

        assertThat(request.getClinicName()).isEqualTo("Mutlu Pati");
        assertThat(request.getAdminFullName()).isEqualTo("Ayse Yilmaz");
        assertThat(request.getAdminEmail()).isEqualTo("ayse@example.com");
        assertThat(request.getPhone()).isEqualTo("+905551112233");
        assertThat(request.getPlanCode()).isEqualTo("PRO");
        assertThat(request.isCompleted()).isFalse();
    }

    @Test
    void should_allowNullPhone_when_created() {
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO"
        );

        assertThat(request.getPhone()).isNull();
    }

    @Test
    void should_becomeCompleted_when_completeCalled() {
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO"
        );

        request.complete();

        assertThat(request.isCompleted()).isTrue();
    }

    @Test
    void should_stayCompleted_when_completeCalledTwice() {
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO"
        );
        request.complete();

        request.complete();

        assertThat(request.isCompleted()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `backend/`): `./mvnw -q -o test -Dtest=TenantSignupRequestTest`
Expected: derleme hatası (`TenantSignupRequest` henüz yok).

- [ ] **Step 3: Create the status enum**

`backend/src/main/java/com/vetos/modules/platformadmin/domain/TenantSignupRequestStatus.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain;

public enum TenantSignupRequestStatus { PENDING, COMPLETED }
```

- [ ] **Step 4: Create the domain entity**

`backend/src/main/java/com/vetos/modules/platformadmin/domain/TenantSignupRequest.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * vetly.com'da bir ziyaretcinin odeme baslatmasi ile tenant'in gercekten
 * olusturulmasi arasindaki bekleme durumunu tutar -- odeme aninda henuz
 * ne bir Tenant ne bir PlatformInvoice var, bu yuzden iyzico'nun
 * conversationId'si bu nesnenin id'sine baglanir.
 */
@Entity
@Table(name = "tenant_signup_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantSignupRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "clinic_name", nullable = false)
    private String clinicName;

    @Column(name = "admin_full_name", nullable = false)
    private String adminFullName;

    @Column(name = "admin_email", nullable = false)
    private String adminEmail;

    @Column
    private String phone;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantSignupRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TenantSignupRequest create(
        String clinicName, String adminFullName, String adminEmail, String phone, String planCode
    ) {
        TenantSignupRequest request = new TenantSignupRequest();
        request.clinicName = clinicName;
        request.adminFullName = adminFullName;
        request.adminEmail = adminEmail;
        request.phone = phone;
        request.planCode = planCode;
        request.status = TenantSignupRequestStatus.PENDING;
        request.createdAt = Instant.now();
        return request;
    }

    public void complete() {
        this.status = TenantSignupRequestStatus.COMPLETED;
    }

    public boolean isCompleted() {
        return status == TenantSignupRequestStatus.COMPLETED;
    }
}
```

- [ ] **Step 5: Create the repository port**

`backend/src/main/java/com/vetos/modules/platformadmin/domain/TenantSignupRequestRepository.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain;

import java.util.Optional;
import java.util.UUID;

public interface TenantSignupRequestRepository {
    TenantSignupRequest save(TenantSignupRequest request);
    Optional<TenantSignupRequest> findById(UUID id);
}
```

- [ ] **Step 6: Create the JPA repository + adapter**

`backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/TenantSignupRequestJpaRepository.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TenantSignupRequestJpaRepository extends JpaRepository<TenantSignupRequest, UUID> {
}
```

`backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/TenantSignupRequestRepositoryAdapter.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TenantSignupRequestRepositoryAdapter implements TenantSignupRequestRepository {

    private final TenantSignupRequestJpaRepository jpaRepository;

    @Override
    public TenantSignupRequest save(TenantSignupRequest request) { return jpaRepository.save(request); }

    @Override
    public Optional<TenantSignupRequest> findById(UUID id) { return jpaRepository.findById(id); }
}
```

- [ ] **Step 7: Write the migration**

`backend/src/main/resources/db/migration/V30__signup_requests.sql` (yeni dosya):

```sql
CREATE TABLE tenant_signup_requests (
    id               UUID PRIMARY KEY,
    clinic_name      TEXT NOT NULL,
    admin_full_name  TEXT NOT NULL,
    admin_email      TEXT NOT NULL,
    phone            TEXT,
    plan_code        TEXT NOT NULL,
    status           TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL
);

ALTER TABLE staff_invites ALTER COLUMN invited_by_staff_user_id DROP NOT NULL;
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./mvnw -q -o test -Dtest=TenantSignupRequestTest`
Expected: PASS (4 test, 0 hata).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/domain/TenantSignupRequestStatus.java backend/src/main/java/com/vetos/modules/platformadmin/domain/TenantSignupRequest.java backend/src/main/java/com/vetos/modules/platformadmin/domain/TenantSignupRequestRepository.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/TenantSignupRequestJpaRepository.java backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/TenantSignupRequestRepositoryAdapter.java backend/src/main/resources/db/migration/V30__signup_requests.sql backend/src/test/java/com/vetos/modules/platformadmin/domain/TenantSignupRequestTest.java
git commit -m "feat: add TenantSignupRequest domain object and persistence"
```

---

### Task 2: `TenantAdminPort` — ödeme sonrası tenant oluşturma + e-posta kontrolü

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/tenant/domain/TenantSignupResult.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/Subscription.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/tenant/domain/SubscriptionTest.java` (mevcut dosyaya yeni test eklenir)

**Interfaces:**
- Consumes: hiçbir önceki task'a bağımlı değil.
- Produces: `TenantSignupResult(UUID tenantId, UUID branchId)`. `TenantAdminPort.createTenantForPaidSignup(String tenantName, String taxNumber, String branchName, String address, String city, String planCode, LocalDate renewsAt): TenantSignupResult`. `TenantAdminPort.isEmailRegistered(String email): boolean`. `Subscription.startPaid(UUID tenantId, String planCode, LocalDate renewsAt): Subscription`. Task 4 bunları kullanacak.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/modules/tenant/domain/SubscriptionTest.java` dosyasının sonuna (mevcut iki testin altına, kapanış `}`'den önce) ekle:

```java
    @Test
    void should_startActiveWithChosenPlan_when_startPaidCalled() {
        UUID tenantId = UUID.randomUUID();
        LocalDate renewsAt = LocalDate.of(2026, 9, 30);

        Subscription subscription = Subscription.startPaid(tenantId, "PRO", renewsAt);

        assertThat(subscription.getTenantId()).isEqualTo(tenantId);
        assertThat(subscription.getPlanCode()).isEqualTo("PRO");
        assertThat(subscription.getRenewsAt()).isEqualTo(renewsAt);
        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.ACTIVE);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `backend/`): `./mvnw -q -o test -Dtest=SubscriptionTest`
Expected: derleme hatası (`Subscription.startPaid` henüz yok).

- [ ] **Step 3: Add `startPaid` to `Subscription`**

`backend/src/main/java/com/vetos/modules/tenant/domain/Subscription.java` içinde `startTrial(...)` metodunun hemen altına ekle:

```java
    public static Subscription startPaid(UUID tenantId, String planCode, LocalDate renewsAt) {
        Subscription subscription = new Subscription();
        subscription.tenantId = tenantId;
        subscription.planCode = planCode;
        subscription.startedAt = LocalDate.now();
        subscription.renewsAt = renewsAt;
        subscription.billingStatus = BillingStatus.ACTIVE;
        return subscription;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -o test -Dtest=SubscriptionTest`
Expected: PASS (3 test, 0 hata).

- [ ] **Step 5: Create `TenantSignupResult`**

`backend/src/main/java/com/vetos/modules/tenant/domain/TenantSignupResult.java` (yeni dosya):

```java
package com.vetos.modules.tenant.domain;

import java.util.UUID;

public record TenantSignupResult(UUID tenantId, UUID branchId) {}
```

- [ ] **Step 6: Extend `TenantAdminPort`**

`backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java` — mevcut `createTenant(...)` metodunun kapanışından sonra, interface'in kapanış `}`'inden önce ekle:

```java

    /**
     * Odeme sonrasi self-servis kayit icin: Tenant + Branch + Subscription
     * (secilen plan, ACTIVE durumda -- TRIAL DEGIL) olusturur ama StaffUser
     * OLUSTURMAZ -- ilk admin, ayri bir StaffInvite kabul ederek kendi
     * hesabini/sifresini olusturur (mevcut invite-accept akisiyla ayni).
     * createTenant(...)'tan farkli olarak burada admin sifresi CAGIRAN
     * TARAFTAN gelmiyor -- odeme yapan ziyaretci henuz hicbir sifre girmedi.
     */
    TenantSignupResult createTenantForPaidSignup(
        String tenantName, String taxNumber, String branchName, String address, String city,
        String planCode, LocalDate renewsAt
    );

    /** Odeme oncesi e-posta benzersizligini kontrol etmek icin. */
    boolean isEmailRegistered(String email);
```

- [ ] **Step 7: Implement in `TenantAdminPortAdapter`**

`backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java` içinde, `createTenant(...)` metodunun kapanışından (`private Optional<StaffUser> findBillingContact`'tan önce) sonra ekle:

```java

    @Override
    public TenantSignupResult createTenantForPaidSignup(
        String tenantName, String taxNumber, String branchName, String address, String city,
        String planCode, LocalDate renewsAt
    ) {
        Tenant tenant = tenantJpaRepository.save(Tenant.register(tenantName, taxNumber));
        tenant.activate();
        tenant = tenantJpaRepository.save(tenant);

        Branch branch = Branch.create(tenant.getId(), branchName);
        branch.updateDetails(address, city, branch.getTimezone(), null);
        branch = branchJpaRepository.save(branch);

        subscriptionJpaRepository.save(Subscription.startPaid(tenant.getId(), planCode, renewsAt));

        return new TenantSignupResult(tenant.getId(), branch.getId());
    }

    @Override
    public boolean isEmailRegistered(String email) {
        return staffUserJpaRepository.existsByEmail(email);
    }
```

- [ ] **Step 8: Compile to verify nothing broke**

Run (from `backend/`): `./mvnw -q -o test-compile`
Expected: derleme hatasız biter (çıktı yok) — `TenantAdminPort`'un tek implementasyonu olan `TenantAdminPortAdapter` yeni metodları eksiksiz uyguladığı için derleme geçmeli.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/tenant/domain/TenantSignupResult.java backend/src/main/java/com/vetos/modules/tenant/domain/Subscription.java backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java backend/src/test/java/com/vetos/modules/tenant/domain/SubscriptionTest.java
git commit -m "feat: add createTenantForPaidSignup and isEmailRegistered to TenantAdminPort"
```

---

### Task 3: `InitiateSignupCheckoutUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/SignupEmailAlreadyRegisteredConflictException.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PlanNotFoundException.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/InitiateSignupCheckoutUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/InitiateSignupCheckoutUseCaseTest.java`

**Interfaces:**
- Consumes: `PlanRepository.findByCode(String): Optional<Plan>` (mevcut), `TenantAdminPort.isEmailRegistered(String): boolean` (Task 2), `TenantSignupRequestRepository.save(TenantSignupRequest): TenantSignupRequest` (Task 1), `PaymentGatewayPort.initializeCheckout(String, BigDecimal, String, String): CheckoutSession` (sub-proje #3, mevcut).
- Produces: `InitiateSignupCheckoutUseCase.execute(String clinicName, String adminFullName, String adminEmail, String phone, String planCode): CheckoutSession`. Task 5'teki `PublicSignupController` bunu çağıracak.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/modules/platformadmin/application/InitiateSignupCheckoutUseCaseTest.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import com.vetos.modules.platformadmin.domain.exception.SignupEmailAlreadyRegisteredConflictException;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitiateSignupCheckoutUseCaseTest {

    @Mock private PlanRepository planRepository;
    @Mock private TenantSignupRequestRepository tenantSignupRequestRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PaymentGatewayPort paymentGatewayPort;

    private InitiateSignupCheckoutUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InitiateSignupCheckoutUseCase(
            planRepository, tenantSignupRequestRepository, tenantAdminPort, paymentGatewayPort
        );
    }

    @Test
    void should_initializeCheckout_when_planActiveAndEmailFree() {
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.isEmailRegistered("ayse@example.com")).thenReturn(false);
        when(tenantSignupRequestRepository.save(any(TenantSignupRequest.class))).thenAnswer(inv -> {
            TenantSignupRequest req = inv.getArgument(0);
            ReflectionTestUtils.setField(req, "id", UUID.randomUUID());
            return req;
        });
        CheckoutSession expectedSession = new CheckoutSession("https://sandbox.iyzipay.com/pay/abc", "abc");
        when(paymentGatewayPort.initializeCheckout(any(), eq(plan.getMonthlyPrice()), eq("Ayse Yilmaz"), eq("ayse@example.com")))
            .thenReturn(expectedSession);

        CheckoutSession result = useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", "+905551112233", "PRO");

        assertThat(result).isEqualTo(expectedSession);
        ArgumentCaptor<TenantSignupRequest> captor = ArgumentCaptor.forClass(TenantSignupRequest.class);
        verify(tenantSignupRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getClinicName()).isEqualTo("Mutlu Pati");
        assertThat(captor.getValue().getAdminEmail()).isEqualTo("ayse@example.com");
        assertThat(captor.getValue().getPlanCode()).isEqualTo("PRO");
    }

    @Test
    void should_throwPlanNotFound_when_planDoesNotExist() {
        when(planRepository.findByCode("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "GHOST"))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void should_throwPlanNotFound_when_planIsInactive() {
        Plan plan = Plan.create("OLD", "Eski Plan", new BigDecimal("500.00"));
        plan.deactivate();
        when(planRepository.findByCode("OLD")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "OLD"))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void should_throwConflict_when_emailAlreadyRegistered() {
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.isEmailRegistered("ayse@example.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO"))
            .isInstanceOf(SignupEmailAlreadyRegisteredConflictException.class);

        verifyNoInteractions(paymentGatewayPort);
        verifyNoInteractions(tenantSignupRequestRepository);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -o test -Dtest=InitiateSignupCheckoutUseCaseTest`
Expected: derleme hatası (`InitiateSignupCheckoutUseCase`, `SignupEmailAlreadyRegisteredConflictException` henüz yok).

- [ ] **Step 3: Add the `String code` overload to `PlanNotFoundException`**

`backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PlanNotFoundException.java` — tüm dosyanın yeni hali:

```java
package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class PlanNotFoundException extends DomainException {
    public PlanNotFoundException(UUID id) {
        super("PLAN_NOT_FOUND", "Plan bulunamadi: " + id);
    }

    public PlanNotFoundException(String code) {
        super("PLAN_NOT_FOUND", "Plan bulunamadi: " + code);
    }
}
```

- [ ] **Step 4: Create the new exception**

`backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/SignupEmailAlreadyRegisteredConflictException.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class SignupEmailAlreadyRegisteredConflictException extends DomainException {
    public SignupEmailAlreadyRegisteredConflictException(String email) {
        super("SIGNUP_EMAIL_ALREADY_REGISTERED", "Bu e-posta adresi zaten kayitli: " + email);
    }
}
```

- [ ] **Step 5: Implement the use case**

`backend/src/main/java/com/vetos/modules/platformadmin/application/InitiateSignupCheckoutUseCase.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import com.vetos.modules.platformadmin.domain.exception.SignupEmailAlreadyRegisteredConflictException;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InitiateSignupCheckoutUseCase {

    private final PlanRepository planRepository;
    private final TenantSignupRequestRepository tenantSignupRequestRepository;
    private final TenantAdminPort tenantAdminPort;
    private final PaymentGatewayPort paymentGatewayPort;

    @Transactional
    public CheckoutSession execute(String clinicName, String adminFullName, String adminEmail, String phone, String planCode) {
        Plan plan = planRepository.findByCode(planCode)
            .filter(Plan::isActive)
            .orElseThrow(() -> new PlanNotFoundException(planCode));

        if (tenantAdminPort.isEmailRegistered(adminEmail)) {
            throw new SignupEmailAlreadyRegisteredConflictException(adminEmail);
        }

        TenantSignupRequest request = tenantSignupRequestRepository.save(
            TenantSignupRequest.create(clinicName, adminFullName, adminEmail, phone, planCode)
        );

        return paymentGatewayPort.initializeCheckout(
            request.getId().toString(), plan.getMonthlyPrice(), adminFullName, adminEmail
        );
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q -o test -Dtest=InitiateSignupCheckoutUseCaseTest`
Expected: PASS (4 test, 0 hata).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/SignupEmailAlreadyRegisteredConflictException.java backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/PlanNotFoundException.java backend/src/main/java/com/vetos/modules/platformadmin/application/InitiateSignupCheckoutUseCase.java backend/src/test/java/com/vetos/modules/platformadmin/application/InitiateSignupCheckoutUseCaseTest.java
git commit -m "feat: add InitiateSignupCheckoutUseCase"
```

---

### Task 4: `HandleSignupPaymentCallbackUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/TenantSignupRequestNotFoundException.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/HandleSignupPaymentCallbackUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/HandleSignupPaymentCallbackUseCaseTest.java`

**Interfaces:**
- Consumes: `PaymentGatewayPort.retrieveCheckoutResult(String): CheckoutResult` (#3, mevcut), `TenantSignupRequestRepository.findById/save` (Task 1), `PlanRepository.findByCode` (mevcut), `TenantAdminPort.createTenantForPaidSignup(...)` (Task 2), `com.vetos.modules.tenant.domain.StaffInviteRepository.save(StaffInvite): StaffInvite` (mevcut), `StaffInvite.create(UUID tenantId, UUID branchId, String email, String fullName, StaffRole role, UUID invitedByStaffUserId): StaffInvite` (mevcut, `invitedByStaffUserId` artık nullable — Task 1'in migration'ı), `com.vetos.modules.tenant.domain.InviteEmailPort.sendInvite(StaffInvite, String tenantName, String acceptUrl)` (mevcut), `PlatformInvoice.issue(UUID, String, BigDecimal, LocalDate, LocalDate, LocalDate): PlatformInvoice` (mevcut), `PlatformInvoiceRepository.save` (mevcut), `RecordPlatformPaymentUseCase.execute(RecordPlatformPaymentCommand)` (mevcut, değişmez).
- Produces: `HandleSignupPaymentCallbackUseCase.execute(String token, LocalDate today): boolean` — `HandlePaymentCallbackUseCase` (#3) ile **aynı imza şekli**. Task 5'teki `PublicPaymentCallbackController` bunu fallback olarak çağıracak.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/modules/platformadmin/application/HandleSignupPaymentCallbackUseCaseTest.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.platformadmin.domain.exception.TenantSignupRequestNotFoundException;
import com.vetos.modules.tenant.domain.InviteEmailPort;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantSignupResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandleSignupPaymentCallbackUseCaseTest {

    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private TenantSignupRequestRepository tenantSignupRequestRepository;
    @Mock private PlanRepository planRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private StaffInviteRepository staffInviteRepository;
    @Mock private InviteEmailPort inviteEmailPort;
    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;

    private HandleSignupPaymentCallbackUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new HandleSignupPaymentCallbackUseCase(
            paymentGatewayPort, tenantSignupRequestRepository, planRepository, tenantAdminPort,
            staffInviteRepository, inviteEmailPort, platformInvoiceRepository, recordPlatformPaymentUseCase
        );
        ReflectionTestUtils.setField(useCase, "frontendBaseUrl", "http://localhost:5173");
    }

    @Test
    void should_createTenantAndInviteAndInvoice_when_paymentSucceeds() {
        LocalDate today = LocalDate.of(2026, 8, 30);
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", "+905551112233", "PRO"
        );
        ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("2000.00"));
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        StaffInvite savedInvite = StaffInvite.create(tenantId, branchId, "ayse@example.com", "Ayse Yilmaz", StaffRole.ADMIN, null);
        PlatformInvoice savedInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("2000.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(savedInvoice, "id", UUID.randomUUID());

        when(paymentGatewayPort.retrieveCheckoutResult("tok-1"))
            .thenReturn(new CheckoutResult(true, request.getId().toString(), "pay_123"));
        when(tenantSignupRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.createTenantForPaidSignup(
            "Mutlu Pati", "-", "Mutlu Pati", "-", "-", "PRO", today.plusMonths(1)
        )).thenReturn(new TenantSignupResult(tenantId, branchId));
        when(staffInviteRepository.save(any(StaffInvite.class))).thenReturn(savedInvite);
        when(platformInvoiceRepository.save(any(PlatformInvoice.class))).thenReturn(savedInvoice);

        boolean result = useCase.execute("tok-1", today);

        assertThat(result).isTrue();
        verify(inviteEmailPort).sendInvite(eq(savedInvite), eq("Mutlu Pati"), eq("http://localhost:5173/davet/" + savedInvite.getToken()));
        ArgumentCaptor<RecordPlatformPaymentCommand> captor = ArgumentCaptor.forClass(RecordPlatformPaymentCommand.class);
        verify(recordPlatformPaymentUseCase).execute(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("2000.00");
        assertThat(captor.getValue().method()).isEqualTo(PlatformPaymentMethod.CARD_ONLINE);
        assertThat(captor.getValue().recordedByAdminId()).isNull();
        assertThat(request.isCompleted()).isTrue();
    }

    @Test
    void should_returnFalse_when_paymentFails() {
        when(paymentGatewayPort.retrieveCheckoutResult("tok-2")).thenReturn(new CheckoutResult(false, null, null));

        boolean result = useCase.execute("tok-2", LocalDate.of(2026, 8, 30));

        assertThat(result).isFalse();
        verifyNoInteractions(tenantSignupRequestRepository, tenantAdminPort, staffInviteRepository, platformInvoiceRepository);
    }

    @Test
    void should_returnTrue_when_requestAlreadyCompleted_repeatedCallback() {
        TenantSignupRequest request = TenantSignupRequest.create("Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO");
        ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
        request.complete();
        when(paymentGatewayPort.retrieveCheckoutResult("tok-3"))
            .thenReturn(new CheckoutResult(true, request.getId().toString(), "pay_123"));
        when(tenantSignupRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        boolean result = useCase.execute("tok-3", LocalDate.of(2026, 8, 30));

        assertThat(result).isTrue();
        verifyNoInteractions(tenantAdminPort, staffInviteRepository, platformInvoiceRepository);
    }

    @Test
    void should_throwNotFound_when_conversationIdDoesNotMatchAnyRequest() {
        UUID unknownId = UUID.randomUUID();
        when(paymentGatewayPort.retrieveCheckoutResult("tok-4"))
            .thenReturn(new CheckoutResult(true, unknownId.toString(), "pay_123"));
        when(tenantSignupRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("tok-4", LocalDate.of(2026, 8, 30)))
            .isInstanceOf(TenantSignupRequestNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -o test -Dtest=HandleSignupPaymentCallbackUseCaseTest`
Expected: derleme hatası (`HandleSignupPaymentCallbackUseCase`, `TenantSignupRequestNotFoundException` henüz yok).

- [ ] **Step 3: Create the exception**

`backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/TenantSignupRequestNotFoundException.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TenantSignupRequestNotFoundException extends DomainException {
    public TenantSignupRequestNotFoundException(UUID id) {
        super("TENANT_SIGNUP_REQUEST_NOT_FOUND", "Kayit talebi bulunamadi: " + id);
    }
}
```

- [ ] **Step 4: Implement the use case**

`backend/src/main/java/com/vetos/modules/platformadmin/application/HandleSignupPaymentCallbackUseCase.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;
import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import com.vetos.modules.platformadmin.domain.exception.TenantSignupRequestNotFoundException;
import com.vetos.modules.tenant.domain.InviteEmailPort;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantSignupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * iyzico'nun checkout callback'i sonrasi, conversationId bilinen bir
 * PlatformInvoice'a ait DEGILSE (PublicPaymentCallbackController'daki
 * fallback), bu use-case cagrilir -- vetly.com'da yeni bir kayit odemesi.
 * Idempotent: TenantSignupRequest zaten COMPLETED ise tenant tekrar
 * olusturulmaz.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HandleSignupPaymentCallbackUseCase {

    private final PaymentGatewayPort paymentGatewayPort;
    private final TenantSignupRequestRepository tenantSignupRequestRepository;
    private final PlanRepository planRepository;
    private final TenantAdminPort tenantAdminPort;
    private final StaffInviteRepository staffInviteRepository;
    private final InviteEmailPort inviteEmailPort;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public boolean execute(String token, LocalDate today) {
        CheckoutResult result = paymentGatewayPort.retrieveCheckoutResult(token);
        if (!result.success()) {
            log.info("iyzico kayit odemesi basarisiz: conversationId={}", result.conversationId());
            return false;
        }

        UUID requestId = UUID.fromString(result.conversationId());
        TenantSignupRequest request = tenantSignupRequestRepository.findById(requestId)
            .orElseThrow(() -> new TenantSignupRequestNotFoundException(requestId));

        if (request.isCompleted()) {
            log.info("iyzico kayit callback'i tekrarlandi, kayit zaten tamamlanmis: requestId={}", requestId);
            return true;
        }

        Plan plan = planRepository.findByCode(request.getPlanCode())
            .orElseThrow(() -> new PlanNotFoundException(request.getPlanCode()));

        LocalDate renewsAt = today.plusMonths(1);
        TenantSignupResult tenant = tenantAdminPort.createTenantForPaidSignup(
            request.getClinicName(), "-", request.getClinicName(), "-", "-", request.getPlanCode(), renewsAt
        );

        StaffInvite invite = staffInviteRepository.save(StaffInvite.create(
            tenant.tenantId(), tenant.branchId(), request.getAdminEmail(), request.getAdminFullName(), StaffRole.ADMIN, null
        ));
        String acceptUrl = frontendBaseUrl + "/davet/" + invite.getToken();
        inviteEmailPort.sendInvite(invite, request.getClinicName(), acceptUrl);

        PlatformInvoice invoice = platformInvoiceRepository.save(
            PlatformInvoice.issue(tenant.tenantId(), request.getPlanCode(), plan.getMonthlyPrice(), today, renewsAt, today)
        );
        recordPlatformPaymentUseCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), plan.getMonthlyPrice(), PlatformPaymentMethod.CARD_ONLINE,
            today, "iyzico odeme referansi: " + result.paymentId(), null
        ));

        request.complete();
        tenantSignupRequestRepository.save(request);

        return true;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q -o test -Dtest=HandleSignupPaymentCallbackUseCaseTest`
Expected: PASS (4 test, 0 hata).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/domain/exception/TenantSignupRequestNotFoundException.java backend/src/main/java/com/vetos/modules/platformadmin/application/HandleSignupPaymentCallbackUseCase.java backend/src/test/java/com/vetos/modules/platformadmin/application/HandleSignupPaymentCallbackUseCaseTest.java
git commit -m "feat: add HandleSignupPaymentCallbackUseCase"
```

---

### Task 5: Controllers — public signup uç noktaları + callback fallback + CORS + uçtan uca doğrulama

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/InitiateSignupCheckoutRequest.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/api/PublicSignupController.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/PublicPaymentCallbackController.java`
- Modify: `backend/src/test/java/com/vetos/modules/platformadmin/api/PublicPaymentCallbackControllerTest.java`
- Modify: `backend/src/main/java/com/vetos/platform/security/SecurityConfig.java`

**Interfaces:**
- Consumes: `InitiateSignupCheckoutUseCase.execute(...)` (Task 3), `HandleSignupPaymentCallbackUseCase.execute(String, LocalDate): boolean` (Task 4), `ListPlansUseCase.execute()` (mevcut), `CheckoutSessionResponse` (mevcut, sub-proje #3'te oluşturuldu — `record CheckoutSessionResponse(String checkoutFormUrl)`), `PlanResponse.from(Plan)` (mevcut).
- Produces: `GET /api/v1/public/signup/plans`, `POST /api/v1/public/signup/checkout` → `{ checkoutFormUrl }`. `PublicPaymentCallbackController`'ın genişletilmiş davranışı (fallback + vetly-site'a yönlendirme). Task 6 (vetly-site) bu iki yeni uç noktayı kullanacak.

- [ ] **Step 1: Add the request DTO**

`backend/src/main/java/com/vetos/modules/platformadmin/api/dto/InitiateSignupCheckoutRequest.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InitiateSignupCheckoutRequest(
    @NotBlank String clinicName,
    @NotBlank String adminFullName,
    @NotBlank @Email String adminEmail,
    String phone,
    @NotBlank String planCode
) {}
```

- [ ] **Step 2: Create `PublicSignupController`**

`backend/src/main/java/com/vetos/modules/platformadmin/api/PublicSignupController.java` (yeni dosya):

```java
package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CheckoutSessionResponse;
import com.vetos.modules.platformadmin.api.dto.InitiateSignupCheckoutRequest;
import com.vetos.modules.platformadmin.api.dto.PlanResponse;
import com.vetos.modules.platformadmin.application.InitiateSignupCheckoutUseCase;
import com.vetos.modules.platformadmin.application.ListPlansUseCase;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.Plan;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * vetly.com'daki self-servis kayit akisi icin kimlik dogrulama gerektirmeyen
 * uc noktalar -- SecurityConfig'de /api/v1/public/** zaten permitAll
 * (PublicClinicController ile ayni desen).
 */
@RestController
@RequestMapping("/api/v1/public/signup")
@RequiredArgsConstructor
public class PublicSignupController {

    private final ListPlansUseCase listPlansUseCase;
    private final InitiateSignupCheckoutUseCase initiateSignupCheckoutUseCase;

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return listPlansUseCase.execute().stream()
            .filter(Plan::isActive)
            .map(PlanResponse::from)
            .toList();
    }

    @PostMapping("/checkout")
    public CheckoutSessionResponse checkout(@RequestBody @Valid InitiateSignupCheckoutRequest request) {
        CheckoutSession session = initiateSignupCheckoutUseCase.execute(
            request.clinicName(), request.adminFullName(), request.adminEmail(), request.phone(), request.planCode()
        );
        return new CheckoutSessionResponse(session.checkoutFormUrl());
    }
}
```

- [ ] **Step 3: Update `PublicPaymentCallbackController`**

`backend/src/main/java/com/vetos/modules/platformadmin/api/PublicPaymentCallbackController.java` — tüm dosyanın yeni hali:

```java
package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.application.HandlePaymentCallbackUseCase;
import com.vetos.modules.platformadmin.application.HandleSignupPaymentCallbackUseCase;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

/**
 * iyzico'nun Checkout Form callback'i icin kimlik dogrulama gerektirmeyen uc
 * nokta -- SecurityConfig'de /api/v1/public/** zaten permitAll (PublicClinicController
 * ile ayni desen). POST: gercek iyzico callback'i (form param 'token'). GET:
 * sadece IyzicoPaymentGatewayAdapter simule modundayken kullanilir -- gelistirici
 * "Ode" butonuna basinca gercek bir iyzico sayfasi olmadan bu uc noktaya
 * dogrudan yonlendirilir. Gateway gercekten yapilandirilmissa (isConfigured())
 * GET tamamen reddedilir; gercek iyzico her zaman POST eder.
 *
 * Iki farkli odeme senaryosunu ayni callback'te ele alir: (1) mevcut bir
 * tenant'in kendi PlatformInvoice'unu odemesi (HandlePaymentCallbackUseCase),
 * (2) vetly.com'da yeni bir kayit odemesi (HandleSignupPaymentCallbackUseCase).
 * conversationId hangi turden oldugunu kendi basina soylemedigi icin once
 * fatura-odemesi olarak denenir; PlatformInvoiceNotFoundException gelirse
 * kayit-odemesi olarak denenir. Basarili sonucta HANGI akisin isledigine
 * gore FARKLI bir siteye (ana uygulama ya da vetly-site) yonlendirilir.
 */
@RestController
@RequestMapping("/api/v1/public/payments/iyzico/callback")
@Slf4j
public class PublicPaymentCallbackController {

    private final HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    private final HandleSignupPaymentCallbackUseCase handleSignupPaymentCallbackUseCase;
    private final PaymentGatewayPort paymentGatewayPort;
    private final String frontendBaseUrl;
    private final String vetlySiteOrigin;

    PublicPaymentCallbackController(
        HandlePaymentCallbackUseCase handlePaymentCallbackUseCase,
        HandleSignupPaymentCallbackUseCase handleSignupPaymentCallbackUseCase,
        PaymentGatewayPort paymentGatewayPort,
        @Value("${app.frontend-base-url}") String frontendBaseUrl,
        @Value("${app.vetly-site-origin:http://localhost:5175}") String vetlySiteOrigin
    ) {
        this.handlePaymentCallbackUseCase = handlePaymentCallbackUseCase;
        this.handleSignupPaymentCallbackUseCase = handleSignupPaymentCallbackUseCase;
        this.paymentGatewayPort = paymentGatewayPort;
        this.frontendBaseUrl = frontendBaseUrl;
        this.vetlySiteOrigin = vetlySiteOrigin;
    }

    @PostMapping
    public ResponseEntity<Void> handlePost(@RequestParam String token) {
        return handleAndRedirect(token);
    }

    @GetMapping
    public ResponseEntity<Void> handleGet(@RequestParam String token) {
        if (paymentGatewayPort.isConfigured()) {
            log.warn("iyzico callback GET reddedildi -- gateway yapilandirilmis, sadece POST kabul edilir");
            return redirectToApp(false);
        }
        return handleAndRedirect(token);
    }

    private ResponseEntity<Void> handleAndRedirect(String token) {
        LocalDate today = LocalDate.now();
        try {
            boolean success = handlePaymentCallbackUseCase.execute(token, today);
            return redirectToApp(success);
        } catch (PlatformInvoiceNotFoundException notAnInvoicePayment) {
            try {
                boolean success = handleSignupPaymentCallbackUseCase.execute(token, today);
                return redirectToSignup(success);
            } catch (Exception e) {
                log.error("iyzico kayit callback'i islenemedi: token={}", token, e);
                return redirectToSignup(false);
            }
        } catch (Exception e) {
            log.error("iyzico callback islenemedi: token={}", token, e);
            return redirectToApp(false);
        }
    }

    private ResponseEntity<Void> redirectToApp(boolean success) {
        String redirectUrl = frontendBaseUrl + "/ayarlar/abonelik?odeme=" + (success ? "basarili" : "hata");
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    private ResponseEntity<Void> redirectToSignup(boolean success) {
        String redirectUrl = vetlySiteOrigin + "/?kayit=" + (success ? "basarili" : "hata");
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }
}
```

- [ ] **Step 4: Update the existing controller test**

`backend/src/test/java/com/vetos/modules/platformadmin/api/PublicPaymentCallbackControllerTest.java` — tüm dosyanın yeni hali:

```java
package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.application.HandlePaymentCallbackUseCase;
import com.vetos.modules.platformadmin.application.HandleSignupPaymentCallbackUseCase;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicPaymentCallbackControllerTest {

    private static final String FRONTEND = "http://localhost:5173";
    private static final String VETLY_SITE = "http://localhost:5175";

    @Mock private HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    @Mock private HandleSignupPaymentCallbackUseCase handleSignupPaymentCallbackUseCase;
    @Mock private PaymentGatewayPort paymentGatewayPort;

    private PublicPaymentCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicPaymentCallbackController(
            handlePaymentCallbackUseCase, handleSignupPaymentCallbackUseCase, paymentGatewayPort, FRONTEND, VETLY_SITE
        );
    }

    private static String location(ResponseEntity<Void> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        return String.valueOf(response.getHeaders().getLocation());
    }

    @Test
    void should_rejectGetWithoutTouchingToken_when_gatewayIsConfigured() {
        when(paymentGatewayPort.isConfigured()).thenReturn(true);

        ResponseEntity<Void> response = controller.handleGet("SIMULATED-" + UUID.randomUUID());

        assertThat(location(response)).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handlePaymentCallbackUseCase, handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_processGet_when_gatewayIsNotConfigured() {
        when(paymentGatewayPort.isConfigured()).thenReturn(false);
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        ResponseEntity<Void> response = controller.handleGet("SIMULATED-" + UUID.randomUUID());

        assertThat(location(response)).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=basarili");
        verify(handlePaymentCallbackUseCase).execute(anyString(), any(LocalDate.class));
    }

    @Test
    void should_redirectToAppSuccess_when_postCallbackSucceedsAsInvoicePayment() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=basarili");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
        verify(paymentGatewayPort, never()).isConfigured();
    }

    @Test
    void should_redirectToAppFailure_when_postCallbackReturnsFalseAsInvoicePayment() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(false);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_redirectToAppFailure_when_invoiceUseCaseThrowsNonNotFoundDomainException() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceInvalidTransitionException(PlatformInvoiceStatus.PAID, PlatformInvoiceStatus.PAID));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_redirectToAppFailure_when_useCaseThrowsUnexpectedRuntimeException() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new IllegalArgumentException("Invalid UUID string"));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_fallBackToSignupSuccess_when_invoiceUseCaseThrowsNotFound() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceNotFoundException(UUID.randomUUID()));
        when(handleSignupPaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(VETLY_SITE + "/?kayit=basarili");
    }

    @Test
    void should_fallBackToSignupFailure_when_invoiceUseCaseThrowsNotFoundAndSignupReturnsFalse() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceNotFoundException(UUID.randomUUID()));
        when(handleSignupPaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(false);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(VETLY_SITE + "/?kayit=hata");
    }

    @Test
    void should_fallBackToSignupFailure_when_bothUseCasesFindNothing() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceNotFoundException(UUID.randomUUID()));
        when(handleSignupPaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new RuntimeException("bilinmeyen conversationId"));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(VETLY_SITE + "/?kayit=hata");
    }
}
```

- [ ] **Step 5: Add the vetly-site origin to CORS**

`backend/src/main/java/com/vetos/platform/security/SecurityConfig.java` içinde sınıfın alanlarına ekle (mevcut `jwtTokenProvider`/`authenticationEntryPoint`/`accessDeniedHandler` alanlarının altına):

```java
    @org.springframework.beans.factory.annotation.Value("${app.vetly-site-origin:http://localhost:5175}")
    private String vetlySiteOrigin;
```

Sonra `corsConfigurationSource()` metodundaki şu satırı:

```java
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*"));
```

şununla değiştir:

```java
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", vetlySiteOrigin));
```

- [ ] **Step 6: Compile and run the full backend test suite**

Run (from `backend/`): `./mvnw -q -o test-compile`
Expected: derleme hatasız biter.

Run: `./mvnw -q -o test -Dtest=ApplicationModulesTest`
Expected: PASS — bu task'ta yeni bir modül sınırı ihlali beklenmez (`PublicSignupController`/`HandleSignupPaymentCallbackUseCase` zaten izinli `modules.tenant::domain`'i kullanıyor).

Run: `./mvnw -q -o test`
Expected: tüm testler (mevcut + Task 1-5'te eklenenler/güncellenenler) PASS.

- [ ] **Step 7: Uçtan uca doğrulama (simüle mod, gerçek çalışan instance'a karşı)**

Backend'i başlat (`cd backend && ./mvnw -q -o spring-boot:run`, arka planda). Postgres (`backend-postgres-1`, port 5433) zaten çalışıyor olmalı — `docker ps` ile kontrol et.

```bash
# aktif planlari listele (bos gorunmemeli -- #1'de en az bir plan var)
curl -s http://localhost:8080/api/v1/public/signup/plans

# checkout baslat
CHECKOUT=$(curl -s -X POST http://localhost:8080/api/v1/public/signup/checkout \
  -H "Content-Type: application/json" \
  -d '{"clinicName":"Test Kayit Klinigi","adminFullName":"Test Admin","adminEmail":"test-signup-e2e@example.com","phone":"+905551112233","planCode":"PRO"}')
echo "$CHECKOUT"
# beklenen: {"checkoutFormUrl":"http://localhost:8080/api/v1/public/payments/iyzico/callback?token=SIMULATED-<uuid>"}

# checkoutFormUrl'e git (simule callback)
curl -sI "http://localhost:8080/api/v1/public/payments/iyzico/callback?token=SIMULATED-<CHECKOUT'tan alinan token>"
# beklenen: 302, Location: http://localhost:5175/?kayit=basarili

# yeni tenant'in olustugunu platform admin ile dogrula (platform admin token'i almak icin onceki oturumlardaki
# admin@myvet.local / change-me-local-dev-only girisini kullan)
curl -s http://localhost:8080/api/v1/platform-admin/tenants -H "Authorization: Bearer $PA_TOKEN" | grep "Test Kayit Klinigi"

# StaffInvite'in var oldugunu ve /davet/:token ile alinabildigini dogrula -- staff_invites tablosundan token'i cek:
docker exec backend-postgres-1 psql -U myvet -d myvet -c "SELECT token, email, invited_by_staff_user_id FROM staff_invites ORDER BY created_at DESC LIMIT 1;"
# beklenen: invited_by_staff_user_id NULL, email test-signup-e2e@example.com

curl -s http://localhost:8080/api/v1/public/staff-invites/<token>
# beklenen: {"email":"test-signup-e2e@example.com","fullName":"Test Admin","role":"ADMIN","tenantName":"Test Kayit Klinigi"}

# davet kabul edilip sifre belirlensin
curl -s -X POST http://localhost:8080/api/v1/public/staff-invites/<token>/accept \
  -H "Content-Type: application/json" -d '{"password":"TestSignup123!"}'

# yeni admin ile giris yap
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test-signup-e2e@example.com","password":"TestSignup123!"}'
# beklenen: token donuyor -- giris basarili

# tekrarlanan callback idempotent mi?
curl -sI "http://localhost:8080/api/v1/public/payments/iyzico/callback?token=SIMULATED-<ayni token>"
# beklenen: yine 302, 500 DEGIL

# platform_payments'ta mukerrer satir olmadigini dogrula
docker exec backend-postgres-1 psql -U myvet -d myvet -c "SELECT invoice_id, COUNT(*) FROM platform_payments GROUP BY invoice_id HAVING COUNT(*) > 1;"
# beklenen: 0 satir

# artik gercek bir StaffUser var (davet kabul edildi) -- ayni e-posta ile TEKRAR checkout baslatmayi dene, simdi 409 beklenir
curl -s -X POST http://localhost:8080/api/v1/public/signup/checkout \
  -H "Content-Type: application/json" \
  -d '{"clinicName":"Test Kayit Klinigi 2","adminFullName":"Test Admin","adminEmail":"test-signup-e2e@example.com","planCode":"PRO"}'
# beklenen: 409, SIGNUP_EMAIL_ALREADY_REGISTERED (bu adim bilerek en sona birakildi -- StaffUser sadece davet
# kabul edildikten SONRA var oluyor; callback tamamlanmadan hemen once denenirse isEmailRegistered hala false
# doner ve istek yanlislikla basarili gorunur, gercek bir bug degil)
```

Backend'i durdur.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/api/dto/InitiateSignupCheckoutRequest.java backend/src/main/java/com/vetos/modules/platformadmin/api/PublicSignupController.java backend/src/main/java/com/vetos/modules/platformadmin/api/PublicPaymentCallbackController.java backend/src/test/java/com/vetos/modules/platformadmin/api/PublicPaymentCallbackControllerTest.java backend/src/main/java/com/vetos/platform/security/SecurityConfig.java
git commit -m "feat: add public signup endpoints and callback fallback to signup flow"
```

---

### Task 6: vetly-site — gerçek plan verisi + checkout formu

**Files:**
- Create: `C:\Users\Furkan\Desktop\vetly-site\src\api\client.ts`
- Create: `C:\Users\Furkan\Desktop\vetly-site\src\api\signupApi.ts`
- Modify: `C:\Users\Furkan\Desktop\vetly-site\src\components\Pricing.tsx`
- Modify: `C:\Users\Furkan\Desktop\vetly-site\src\components\ContactCta.tsx`
- Modify: `C:\Users\Furkan\Desktop\vetly-site\src\pages\HomePage.tsx`
- Modify: `C:\Users\Furkan\Desktop\vetly-site\src\index.css`
- Create: `C:\Users\Furkan\Desktop\vetly-site\.env.development` (yerel geliştirme için, backend'in gerçek portunu göstermek üzere — genelde gerek yok çünkü varsayılan zaten `http://localhost:8080`, ama açıkça belirtmek netlik sağlar)

**Interfaces:**
- Consumes: `GET /api/v1/public/signup/plans`, `POST /api/v1/public/signup/checkout` (Task 5).
- Produces: `signupApi.getPlans(): Promise<SignupPlan[]>`, `signupApi.initiateCheckout(payload): Promise<{ checkoutFormUrl: string }>`.

- [ ] **Step 1: Create the API client**

`C:\Users\Furkan\Desktop\vetly-site\src\api\client.ts` (yeni dosya):

```ts
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    throw new ApiError(body?.message ?? 'Beklenmeyen bir hata olustu', response.status);
  }

  return body as T;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'POST', body: data !== undefined ? JSON.stringify(data) : undefined }),
};
```

- [ ] **Step 2: Create the signup API**

`C:\Users\Furkan\Desktop\vetly-site\src\api\signupApi.ts` (yeni dosya):

```ts
import { apiClient } from './client';

export interface SignupPlan {
  id: string;
  code: string;
  name: string;
  monthlyPrice: number;
  annualPrice: number | null;
  description: string | null;
  badge: string | null;
  imageUrl: string | null;
  features: string[];
  active: boolean;
}

export interface CheckoutSession {
  checkoutFormUrl: string;
}

export interface SignupCheckoutPayload {
  clinicName: string;
  adminFullName: string;
  adminEmail: string;
  phone?: string;
  planCode: string;
}

export const signupApi = {
  getPlans: () => apiClient.get<SignupPlan[]>('/api/v1/public/signup/plans'),
  initiateCheckout: (payload: SignupCheckoutPayload) =>
    apiClient.post<CheckoutSession>('/api/v1/public/signup/checkout', payload),
};
```

- [ ] **Step 3: Convert `Pricing.tsx` to fetch real plan data**

`C:\Users\Furkan\Desktop\vetly-site\src\components\Pricing.tsx` — tüm dosyanın yeni hali:

```tsx
import { useEffect, useState } from 'react';
import { signupApi, type SignupPlan } from '../api/signupApi';

interface PricingProps {
  onSelectPlan: (planCode: string) => void;
}

export function Pricing({ onSelectPlan }: PricingProps) {
  const [plans, setPlans] = useState<SignupPlan[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    signupApi
      .getPlans()
      .then(setPlans)
      .catch(() => setPlans([]))
      .finally(() => setLoading(false));
  }, []);

  function handleSelect(planCode: string) {
    onSelectPlan(planCode);
    document.getElementById('iletisim')?.scrollIntoView({ behavior: 'smooth' });
  }

  return (
    <section className="section pricing" id="fiyatlandirma">
      <div className="wrap">
        <div className="section-head">
          <span className="eyebrow">Fiyatlandırma</span>
          <h2>Kliniğinizin büyüklüğüne uygun paket</h2>
          <p>İki basit paket, gizli ücret yok. Yıllık ödemede 2 ay ücretsiz kazanın.</p>
        </div>

        {loading ? (
          <p className="pricing-footnote">Planlar yükleniyor...</p>
        ) : plans.length === 0 ? (
          <p className="pricing-footnote">Şu anda satın alınabilir bir plan bulunmuyor.</p>
        ) : (
          <div className="pricing-grid">
            {plans.map((plan) => (
              <div key={plan.code} className={plan.badge ? 'price-card popular' : 'price-card'}>
                {plan.badge && <span className="price-badge">{plan.badge}</span>}
                <div>
                  <div className="price-plan-name">{plan.name}</div>
                  {plan.description && <p className="price-plan-desc">{plan.description}</p>}
                </div>
                <div>
                  <div className="price-amount">
                    <span className="num">{plan.monthlyPrice.toLocaleString('tr-TR')}</span>
                    <span className="unit">₺ / ay</span>
                  </div>
                  {plan.annualPrice !== null && (
                    <p className="price-annual">
                      Yıllık <strong>{plan.annualPrice.toLocaleString('tr-TR')}₺</strong>{' '}
                      <span className="price-save">(2 ay ücretsiz)</span>
                    </p>
                  )}
                </div>
                {plan.features.length > 0 && (
                  <div className="price-feature-list">
                    {plan.features.map((feature) => (
                      <div className="price-feature" key={feature}>
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M4 12l5 5L20 6" />
                        </svg>
                        <span>{feature}</span>
                      </div>
                    ))}
                  </div>
                )}
                <button
                  type="button"
                  className={plan.badge ? 'btn btn-primary btn-block' : 'btn btn-ghost btn-block'}
                  onClick={() => handleSelect(plan.code)}
                >
                  Hemen Başla
                </button>
              </div>
            ))}
          </div>
        )}

        <p className="pricing-footnote">
          Daha büyük bir klinik zinciri misiniz? <a href="#iletisim">Bize ulaşın</a>, size özel bir teklif hazırlayalım.
        </p>
      </div>
    </section>
  );
}
```

- [ ] **Step 4: Convert `ContactCta.tsx` into a real checkout form**

`C:\Users\Furkan\Desktop\vetly-site\src\components\ContactCta.tsx` — tüm dosyanın yeni hali:

```tsx
import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { signupApi } from '../api/signupApi';

type FormStatus = 'idle' | 'submitting';

interface ContactCtaProps {
  selectedPlanCode: string | null;
}

export function ContactCta({ selectedPlanCode }: ContactCtaProps) {
  const [status, setStatus] = useState<FormStatus>('idle');
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const planCode = String(formData.get('planCode') || '');

    if (!planCode) {
      setError('Lütfen yukarıdaki fiyatlandırma bölümünden bir plan seçin.');
      return;
    }

    setStatus('submitting');
    setError(null);

    try {
      const phone = String(formData.get('phone') || '');
      const session = await signupApi.initiateCheckout({
        clinicName: String(formData.get('clinicName') || ''),
        adminFullName: String(formData.get('adminFullName') || ''),
        adminEmail: String(formData.get('adminEmail') || ''),
        phone: phone === '' ? undefined : phone,
        planCode,
      });
      window.location.href = session.checkoutFormUrl;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Bir şeyler ters gitti, lütfen tekrar deneyin.');
      setStatus('idle');
    }
  }

  return (
    <section className="section cta-band" id="iletisim">
      <div className="wrap">
        <div className="cta-band-inner">
          <span className="eyebrow">Hemen Başlayın</span>
          <h2>Kliniğinizi bugün Vetly'ye taşıyın</h2>
          <p>Formu doldurun, ödemenizi yapın, hesabınız hemen oluşsun.</p>

          <form className="real-cta-form" onSubmit={handleSubmit}>
            <input type="hidden" name="planCode" value={selectedPlanCode ?? ''} />

            <div className="cta-form-row">
              <input type="text" name="clinicName" placeholder="Klinik Adı" required />
              <input type="text" name="adminFullName" placeholder="Adınız Soyadınız" required />
            </div>
            <div className="cta-form-row">
              <input type="email" name="adminEmail" placeholder="E-posta" required />
              <input type="tel" name="phone" placeholder="Telefon (opsiyonel)" />
            </div>

            {!selectedPlanCode && (
              <p className="cta-form-status cta-form-error">Lütfen yukarıdaki fiyatlandırma bölümünden bir plan seçin.</p>
            )}

            <button type="submit" className="btn btn-primary btn-block" disabled={status === 'submitting' || !selectedPlanCode}>
              {status === 'submitting' ? 'Yönlendiriliyor...' : 'Ödemeye Geç'}
            </button>

            {error && <p className="cta-form-status cta-form-error">{error}</p>}
          </form>

          <p className="cta-fineprint">Ya da bizi arayın: [TELEFON NUMARANIZ]</p>
        </div>
      </div>
    </section>
  );
}
```

- [ ] **Step 5: Wire selected-plan state and the post-payment result banner in `HomePage.tsx`**

`C:\Users\Furkan\Desktop\vetly-site\src\pages\HomePage.tsx` — tüm dosyanın yeni hali:

```tsx
import { useEffect, useState } from 'react';
import { Header } from '../components/Header';
import { Hero } from '../components/Hero';
import { Features } from '../components/Features';
import { CompareSection } from '../components/CompareSection';
import { LabDeepDive } from '../components/LabDeepDive';
import { AiDeepDive } from '../components/AiDeepDive';
import { HowItWorks } from '../components/HowItWorks';
import { Pricing } from '../components/Pricing';
import { Faq } from '../components/Faq';
import { ContactCta } from '../components/ContactCta';
import { Footer } from '../components/Footer';
import { WhatsAppFloat } from '../components/WhatsAppFloat';
import { CookieBanner } from '../components/CookieBanner';

export function HomePage() {
  const [selectedPlanCode, setSelectedPlanCode] = useState<string | null>(null);
  const [signupResult, setSignupResult] = useState<'basarili' | 'hata' | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const kayit = params.get('kayit');
    if (kayit === 'basarili' || kayit === 'hata') {
      setSignupResult(kayit);
      params.delete('kayit');
      const newSearch = params.toString();
      window.history.replaceState({}, '', window.location.pathname + (newSearch ? `?${newSearch}` : ''));
    }
  }, []);

  return (
    <div id="app">
      <Header />
      {signupResult === 'basarili' && (
        <div className="signup-result-banner signup-result-success">
          Ödemeniz alındı! E-postanızı kontrol edin, hesabınızı etkinleştirme linki gönderdik.
        </div>
      )}
      {signupResult === 'hata' && (
        <div className="signup-result-banner signup-result-error">
          Ödeme tamamlanamadı, lütfen tekrar deneyin ya da bizimle iletişime geçin.
        </div>
      )}
      <Hero />
      <Features />
      <CompareSection />
      <LabDeepDive />
      <AiDeepDive />
      <HowItWorks />
      <Pricing onSelectPlan={setSelectedPlanCode} />
      <Faq />
      <ContactCta selectedPlanCode={selectedPlanCode} />
      <Footer />
      <WhatsAppFloat />
      <CookieBanner />
    </div>
  );
}
```

- [ ] **Step 6: Add the result banner CSS**

`C:\Users\Furkan\Desktop\vetly-site\src\index.css` dosyasının sonuna ekle:

```css
/* signup sonuc banner'i */
.signup-result-banner {
  max-width: 720px;
  margin: 16px auto 0;
  padding: 12px 20px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  text-align: center;
}
.signup-result-success {
  background: var(--color-success-100);
  color: var(--color-success-700);
}
.signup-result-error {
  background: var(--color-gold-100);
  color: var(--color-gold-600);
}
```

- [ ] **Step 7: Create the local dev env file**

`C:\Users\Furkan\Desktop\vetly-site\.env.development` (yeni dosya):

```
VITE_API_BASE_URL=http://localhost:8080
```

- [ ] **Step 8: Build to verify no type errors**

Run (from `C:\Users\Furkan\Desktop\vetly-site`): `npx tsc --noEmit`
Expected: derleme hatasız biter (çıktı yok).

Run: `npm run build`
Expected: hatasız tamamlanır.

- [ ] **Step 9: Visual verification in the browser**

Backend'i (Task 5'teki gibi simüle modda) `cd backend && ./mvnw -q -o spring-boot:run` ile başlat. vetly-site'ı `npm run dev` ile başlat (port 5173/5174 doluysa Vite otomatik başka bir port seçer — hangi porta bağlandığını `npm run dev` çıktısından öğren; backend'in `app.vetly-site-origin` varsayılanı `5175` — eğer Vite farklı bir port seçerse, backend'i `VETLY_SITE_ORIGIN=http://localhost:<gercek-port>` env değişkeniyle yeniden başlat, aksi halde callback yönlendirmesi yanlış porta gider).

Ana sayfada Fiyatlandırma bölümünün gerçek plan verisini gösterdiğini doğrula (statik değil). Bir planda "Hemen Başla"ya tıkla — sayfa `#iletisim`e kaysın ve o planın kodu forma gizlice yüklensin. Formu doldur, "Ödemeye Geç"e bas — tarayıcı simüle callback'e gidip hemen `?kayit=basarili` ile geri dönmeli, yeşil banner görünmeli. Playwright ile ekran görüntüsü al ve doğrula.

- [ ] **Step 10: Commit**

```bash
cd "C:\Users\Furkan\Desktop\vetly-site"
git add src/api/client.ts src/api/signupApi.ts src/components/Pricing.tsx src/components/ContactCta.tsx src/pages/HomePage.tsx src/index.css .env.development
git commit -m "feat: fetch real plan data and wire checkout form to backend signup flow"
```

(Not: bu commit `myvet-claude-code-baslangic-kiti` reposunda DEĞİL, ayrı `vetly-site` reposunda çalıştırılır — `cd` ile dizin değiştirmeyi unutma.)

---

### Task 7: Son doğrulama

**Files:** Yok (sadece doğrulama).

**Interfaces:** Yok.

- [ ] **Step 1: Run the full backend test suite**

Run (from `backend/`): `./mvnw -q -o test`
Expected: tüm testler (mevcut + Task 1-5'te eklenenler/güncellenenler) PASS, 0 hata.

- [ ] **Step 2: Run the frontend builds**

Run (from `frontend/`, ana uygulama — bu plan ana uygulamanın frontend'ini değiştirmedi ama regresyon kontrolü için çalıştır): `npm run build`
Expected: hatasız tamamlanır.

Run (from `C:\Users\Furkan\Desktop\vetly-site`): `npm run build`
Expected: hatasız tamamlanır.

- [ ] **Step 3: Final review**

`git log --oneline -8` (myvet reposunda) ile bu planın backend commit'lerini gözden geçir. `cd "C:\Users\Furkan\Desktop\vetly-site" && git log --oneline -3` ile vetly-site'ın kendi commit'ini gözden geçir. `git status --short` (her iki repoda) ile bu planla ilgisiz, önceden var olan uncommitted değişiklikleri stage etmediğini doğrula.
