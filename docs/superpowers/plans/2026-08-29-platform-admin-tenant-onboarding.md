# Platform Admin Tenant Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove self-service clinic registration (`/kayit`) from the public login page and replace it with a platform-admin-only "create tenant" flow inside the Platform Admin Panel.

**Architecture:** `TenantAdminPort` (the single sanctioned bridge from `modules.platformadmin` into `modules.tenant`) gets a new `createTenant(...)` method whose adapter implementation reuses the exact same domain factory methods (`Tenant.register`, `Branch.create`, `Subscription.startTrial`, `StaffUser.register`) that the deleted self-service flow used — only the caller changes, from an anonymous public endpoint to an authenticated `PLATFORM_ADMIN`-only one.

**Tech Stack:** Spring Boot 3.5.16 / Java 21 backend, React + TypeScript frontend, Spring Modulith module boundaries, Playwright e2e.

**Spec:** docs/superpowers/specs/2026-08-29-platform-admin-tenant-onboarding-design.md

## Global Constraints

- `TenantAdminPort` is the ONLY sanctioned cross-module bridge from `platformadmin` into `tenant` domain — no other module may import `tenant.application`/`tenant.infrastructure` classes directly.
- Exception naming → HTTP status (`GlobalExceptionHandler.resolveStatus`, suffix-based): `*NotFoundException`→404, `*ConflictException`→409, `*ForbiddenException`→403, `*InvalidCredentialsException`/`*UnauthorizedException`→401, else→422. `EmailAlreadyRegisteredConflictException` (existing, reused) → 409.
- Infrastructure adapter classes (`*Adapter`) in this codebase carry no dedicated unit tests — verified via full-suite regression + manual e2e curl instead (established precedent: `TenantAdminPortAdapter`, `PlanRepositoryAdapter`).
- Thin pass-through use cases (single line delegating to a port) carry no dedicated unit test in this codebase (established precedent: `SuspendTenantUseCase`, `ActivateTenantUseCase`, `UpdateTenantSubscriptionUseCase`).
- Package template: ports/domain in `domain/`, use cases in `application/` (+`application/dto/`), REST DTOs in `api/dto/`, controllers in `api/`.
- Reuse existing UI primitives verbatim: `Modal`, `Button`, `FieldWrap`, `Input` from `components/ui/*`; the platform admin panel's own `PlatformAdminPages.module.css` classes (`.actionsRow`, `.tableCard`, `.modalTitle`, `.modalActions`) — no new CSS classes needed anywhere in this plan.

---

### Task 1: `TenantAdminPort.createTenant` + adapter implementation

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java`
- Modify: `docs/architecture.md:171-173`

**Interfaces:**
- Produces: `TenantAdminPort.createTenant(String tenantName, String taxNumber, String branchName, String adminFullName, String adminEmail, String adminPassword): UUID`

This is pure infrastructure logic (multi-entity creation delegating to existing domain factory methods) — no dedicated unit test per the Global Constraints precedent. Verified in Task 3 (full backend suite + module boundary check) and Task 7 (manual curl e2e).

- [ ] **Step 1: Extend `TenantAdminPort` — full file**

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
 * kiraciyi goruntuleyip DEGISTIREBILMESI ve YENI bir kiraci YARATABILMESI
 * icin yazma da icerir. Sadece modules.platformadmin bu portu kullanir
 * (architecture.md'ye not dusulmustur). Diger hicbir modul bu portu
 * import ETMEMELIDIR.
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

    /**
     * Yeni bir kiraci + ilk sube + TRIAL abonelik + ADMIN rolunde ilk personeli
     * tek islemde olusturur -- platform admin panelinden tetiklenir (self-servis
     * kayit kaldirildi). AuthSession/JWT URETMEZ; platform admin baskasinin
     * klinigini olusturuyor, kendi adina giris yapmiyor.
     */
    UUID createTenant(
        String tenantName, String taxNumber, String branchName,
        String adminFullName, String adminEmail, String adminPassword
    );
}
```

- [ ] **Step 2: Implement in `TenantAdminPortAdapter` — full file**

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
import com.vetos.modules.tenant.domain.event.ClinicRegisteredEvent;
import com.vetos.modules.tenant.domain.exception.EmailAlreadyRegisteredConflictException;
import com.vetos.modules.tenant.domain.exception.SubscriptionNotFoundException;
import com.vetos.modules.tenant.domain.exception.TenantNotFoundException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;
    private final DomainEventPublisher eventPublisher;

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
            .filter(s -> s.getBillingStatus() != BillingStatus.CANCELED)
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

    @Override
    public UUID createTenant(
        String tenantName, String taxNumber, String branchName,
        String adminFullName, String adminEmail, String adminPassword
    ) {
        if (staffUserJpaRepository.existsByEmail(adminEmail)) {
            throw new EmailAlreadyRegisteredConflictException(adminEmail);
        }

        Tenant tenant = tenantJpaRepository.save(Tenant.register(tenantName, taxNumber));
        Branch branch = branchJpaRepository.save(Branch.create(tenant.getId(), branchName));
        subscriptionJpaRepository.save(Subscription.startTrial(tenant.getId()));

        String passwordHash = passwordEncoder.encode(adminPassword);
        StaffUser admin = staffUserJpaRepository.save(
            StaffUser.register(branch.getId(), adminFullName, adminEmail, passwordHash, StaffRole.ADMIN)
        );

        eventPublisher.publish(new ClinicRegisteredEvent(tenant.getId(), branch.getId(), admin.getId()));

        return tenant.getId();
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

Note: this reintroduces the `listSubscriptionsDueOnOrBefore` `CANCELED` filter from the platform billing plan's final-review fix — copy the method exactly as shown above (do not regress that fix).

- [ ] **Step 3: Update `docs/architecture.md` §6.1**

Find this paragraph (currently lines 171-173):

```markdown
### 6.1 Kenar durum: Platform Admin'in yazma erişimi (Faz 2)

`LookupPort` deseni ("diğer modüller sadece okur") tek bir bilinçli istisnayla genişletildi: `modules/tenant/domain/TenantAdminPort.java`, `modules.platformadmin` modülünün herhangi bir kiracıyı görüntüleyip **değiştirebilmesi** için okuma yanında yazma metotları da içerir (abonelik/durum güncelleme). Bu, platform admin'in tanımı gereği (SaaS operatörü, tüm kiracıları yönetir) gerekli — normal bir iş modülü (örn. `billing`) için asla bu deseni kullanma, sadece `modules.platformadmin` bu tür bir port'a sahip olabilir. Yeni bir "admin tarafı" ihtiyaç doğarsa aynı isimlendirme deseni (`XxxAdminPort`) izlenir.
```

Replace with:

```markdown
### 6.1 Kenar durum: Platform Admin'in yazma erişimi (Faz 2)

`LookupPort` deseni ("diğer modüller sadece okur") tek bir bilinçli istisnayla genişletildi: `modules/tenant/domain/TenantAdminPort.java`, `modules.platformadmin` modülünün herhangi bir kiracıyı görüntüleyip **değiştirebilmesi** için okuma yanında yazma metotları da içerir (abonelik/durum güncelleme). Bu, platform admin'in tanımı gereği (SaaS operatörü, tüm kiracıları yönetir) gerekli — normal bir iş modülü (örn. `billing`) için asla bu deseni kullanma, sadece `modules.platformadmin` bu tür bir port'a sahip olabilir. Yeni bir "admin tarafı" ihtiyaç doğarsa aynı isimlendirme deseni (`XxxAdminPort`) izlenir.

**Kiracı yaratma (2026-08):** Self-servis klinik kaydı (`/kayit`) kaldırıldı — yeni klinikler artık SADECE platform admin panelinden, `TenantAdminPort.createTenant(...)` üzerinden oluşturulur. Bu, port'un "değiştirebilme" yetkisinin doğal bir uzantısı: platform admin zaten bir kiracıyı askıya alabiliyor/planını değiştirebiliyorsa, kiracıyı ilk baştan yaratabilmesi de aynı sorumluluk alanına girer.
```

- [ ] **Step 4: Compile check**

Run: `cd backend && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java docs/architecture.md
git commit -m "feat: add TenantAdminPort.createTenant (platform-admin tenant creation)"
```

---

### Task 2: `CreatePlatformTenantUseCase` + DTOs + controller endpoint

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/dto/CreatePlatformTenantCommand.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/CreatePlatformTenantUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/CreatePlatformTenantRequest.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/PlatformAdminTenantsController.java`

**Interfaces:**
- Consumes: `TenantAdminPort.createTenant(...)` (Task 1), `TenantAdminPort.getOverview(UUID)` (existing), `TenantAdminOverviewResponse.from(TenantAdminOverview)` (existing)
- Produces: `CreatePlatformTenantUseCase.execute(CreatePlatformTenantCommand): UUID`, `POST /api/v1/platform-admin/tenants`

Thin pass-through use case — no dedicated unit test per the Global Constraints precedent. Verified in Task 3 (full suite) and Task 7 (manual curl e2e).

- [ ] **Step 1: `application/dto/CreatePlatformTenantCommand.java`**

```java
package com.vetos.modules.platformadmin.application.dto;

public record CreatePlatformTenantCommand(
    String tenantName, String taxNumber, String branchName,
    String adminFullName, String adminEmail, String adminPassword
) {}
```

- [ ] **Step 2: `application/CreatePlatformTenantUseCase.java`**

```java
package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreatePlatformTenantCommand;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatePlatformTenantUseCase {

    private final TenantAdminPort tenantAdminPort;

    @Transactional
    public UUID execute(CreatePlatformTenantCommand command) {
        return tenantAdminPort.createTenant(
            command.tenantName(), command.taxNumber(), command.branchName(),
            command.adminFullName(), command.adminEmail(), command.adminPassword()
        );
    }
}
```

- [ ] **Step 3: `api/dto/CreatePlatformTenantRequest.java`**

```java
package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlatformTenantRequest(
    @NotBlank String tenantName,
    String taxNumber,
    @NotBlank String branchName,
    @NotBlank String adminFullName,
    @NotBlank @Email String adminEmail,
    @NotBlank @Size(min = 8) String adminPassword
) {}
```

- [ ] **Step 4: Add the endpoint to `PlatformAdminTenantsController.java` — full file**

```java
package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CreatePlatformTenantRequest;
import com.vetos.modules.platformadmin.api.dto.TenantAdminOverviewResponse;
import com.vetos.modules.platformadmin.api.dto.UpdateTenantSubscriptionRequest;
import com.vetos.modules.platformadmin.application.ActivateTenantUseCase;
import com.vetos.modules.platformadmin.application.CreatePlatformTenantUseCase;
import com.vetos.modules.platformadmin.application.GetTenantAdminOverviewUseCase;
import com.vetos.modules.platformadmin.application.ListTenantsForAdminUseCase;
import com.vetos.modules.platformadmin.application.SuspendTenantUseCase;
import com.vetos.modules.platformadmin.application.UpdateTenantSubscriptionUseCase;
import com.vetos.modules.platformadmin.application.dto.CreatePlatformTenantCommand;
import com.vetos.modules.platformadmin.application.dto.UpdateTenantSubscriptionCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform-admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminTenantsController {

    private final ListTenantsForAdminUseCase listTenantsForAdminUseCase;
    private final GetTenantAdminOverviewUseCase getTenantAdminOverviewUseCase;
    private final CreatePlatformTenantUseCase createPlatformTenantUseCase;
    private final UpdateTenantSubscriptionUseCase updateTenantSubscriptionUseCase;
    private final SuspendTenantUseCase suspendTenantUseCase;
    private final ActivateTenantUseCase activateTenantUseCase;

    @GetMapping
    public List<TenantAdminOverviewResponse> list() {
        return listTenantsForAdminUseCase.execute().stream()
            .map(TenantAdminOverviewResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public TenantAdminOverviewResponse get(@PathVariable UUID id) {
        return TenantAdminOverviewResponse.from(getTenantAdminOverviewUseCase.execute(id));
    }

    @PostMapping
    public ResponseEntity<TenantAdminOverviewResponse> create(@RequestBody @Valid CreatePlatformTenantRequest request) {
        UUID tenantId = createPlatformTenantUseCase.execute(new CreatePlatformTenantCommand(
            request.tenantName(), request.taxNumber(), request.branchName(),
            request.adminFullName(), request.adminEmail(), request.adminPassword()
        ));
        var overview = getTenantAdminOverviewUseCase.execute(tenantId);
        return ResponseEntity.status(201).body(TenantAdminOverviewResponse.from(overview));
    }

    @PutMapping("/{id}/subscription")
    public void updateSubscription(@PathVariable UUID id, @RequestBody @Valid UpdateTenantSubscriptionRequest request) {
        updateTenantSubscriptionUseCase.execute(new UpdateTenantSubscriptionCommand(
            id, request.planCode(), request.billingStatus(), request.renewsAt()
        ));
    }

    @PostMapping("/{id}/suspend")
    public void suspend(@PathVariable UUID id) {
        suspendTenantUseCase.execute(id);
    }

    @PostMapping("/{id}/activate")
    public void activate(@PathVariable UUID id) {
        activateTenantUseCase.execute(id);
    }
}
```

- [ ] **Step 5: Compile check**

Run: `cd backend && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/application/dto/CreatePlatformTenantCommand.java backend/src/main/java/com/vetos/modules/platformadmin/application/CreatePlatformTenantUseCase.java backend/src/main/java/com/vetos/modules/platformadmin/api/dto/CreatePlatformTenantRequest.java backend/src/main/java/com/vetos/modules/platformadmin/api/PlatformAdminTenantsController.java
git commit -m "feat: add POST /api/v1/platform-admin/tenants (create clinic)"
```

---

### Task 3: Remove self-service clinic registration (backend)

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/tenant/api/AuthController.java`
- Delete: `backend/src/main/java/com/vetos/modules/tenant/application/RegisterClinicUseCase.java`
- Delete: `backend/src/main/java/com/vetos/modules/tenant/application/dto/RegisterClinicCommand.java`
- Delete: `backend/src/main/java/com/vetos/modules/tenant/api/dto/RegisterClinicRequest.java`

**Interfaces:**
- Consumes: nothing new
- Produces: nothing new (pure removal) — `POST /api/v1/auth/register-clinic` no longer exists after this task

This task must run AFTER Task 1/2 land (platform admin can already create tenants before the old path is removed — never a window with neither).

- [ ] **Step 1: Rewrite `AuthController.java` — full file**

```java
package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.AuthSessionResponse;
import com.vetos.modules.tenant.api.dto.LoginRequest;
import com.vetos.modules.tenant.application.LoginUseCase;
import com.vetos.modules.tenant.application.dto.LoginCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@RequestBody @Valid LoginRequest request) {
        var session = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(AuthSessionResponse.from(session));
    }
}
```

- [ ] **Step 2: Delete the three files**

```bash
git rm backend/src/main/java/com/vetos/modules/tenant/application/RegisterClinicUseCase.java
git rm backend/src/main/java/com/vetos/modules/tenant/application/dto/RegisterClinicCommand.java
git rm backend/src/main/java/com/vetos/modules/tenant/api/dto/RegisterClinicRequest.java
```

- [ ] **Step 3: Full backend test suite + module boundary check**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS — all existing tests still green (none reference the deleted classes; `ApplicationModulesTest` still passes since no module-boundary rules changed, only a port grew one method).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/tenant/api/AuthController.java
git commit -m "refactor: remove self-service clinic registration (backend)"
```

---

### Task 4: Frontend — "Yeni Klinik Oluştur" in Platform Admin Panel

**Files:**
- Modify: `frontend/src/api/platformAdminApi.ts`
- Modify: `frontend/src/pages/platform-admin/TenantListPage.tsx`

**Interfaces:**
- Consumes: `TenantAdminOverview` (existing type)
- Produces: `platformAdminApi.createTenant(payload): Promise<TenantAdminOverview>`, `CreateTenantPayload` TS type

- [ ] **Step 1: Add to `platformAdminApi.ts`**

Add this interface right after the existing `UpdateTenantSubscriptionPayload` interface (after line 30):

```typescript
export interface CreateTenantPayload {
  tenantName: string;
  taxNumber: string;
  branchName: string;
  adminFullName: string;
  adminEmail: string;
  adminPassword: string;
}
```

Add this method inside the `platformAdminApi` object, right after the existing `getTenant` entry:

```typescript
  createTenant: (payload: CreateTenantPayload) =>
    platformAdminClient.post<TenantAdminOverview>('/api/v1/platform-admin/tenants', payload),
```

- [ ] **Step 2: Rewrite `TenantListPage.tsx` — full file**

```tsx
import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { CreateTenantPayload, platformAdminApi, TenantAdminOverview } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './PlatformAdminPages.module.css';
import { BILLING_STATUS_LABELS, BILLING_STATUS_TONES, TENANT_STATUS_LABELS, TENANT_STATUS_TONES } from './tenantBadges';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const EMPTY_FORM: CreateTenantPayload = {
  tenantName: '',
  taxNumber: '',
  branchName: '',
  adminFullName: '',
  adminEmail: '',
  adminPassword: '',
};

export function TenantListPage() {
  const navigate = useNavigate();
  const [tenants, setTenants] = useState<TenantAdminOverview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<CreateTenantPayload>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    platformAdminApi
      .listTenants()
      .then(setTenants)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }, []);

  const filtered = tenants.filter((t) => t.name.toLowerCase().includes(query.trim().toLowerCase()));

  function openCreate() {
    setForm(EMPTY_FORM);
    setError(null);
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const created = await platformAdminApi.createTenant(form);
      setModalOpen(false);
      navigate(`/platform-admin/tenants/${created.tenantId}`);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className={styles.title}>Kiracılar</div>

      <div className={styles.actionsRow}>
        <Button variant="primary" onClick={openCreate}>
          + Yeni Klinik
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.searchRow}>
        <FieldWrap label="Ara">
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Klinik adı..." />
        </FieldWrap>
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Klinik Adı</div>
          <div>Plan</div>
          <div>Faturalama</div>
          <div>Durum</div>
          <div>Şube</div>
          <div>Personel</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className={styles.empty}>Kiracı bulunamadı</div>
        ) : (
          filtered.map((t) => (
            <div key={t.tenantId} className={styles.row} onClick={() => navigate(`/platform-admin/tenants/${t.tenantId}`)}>
              <div>{t.name}</div>
              <div className={styles.muted}>{t.planCode}</div>
              <div>
                <Badge tone={BILLING_STATUS_TONES[t.billingStatus]}>{BILLING_STATUS_LABELS[t.billingStatus]}</Badge>
              </div>
              <div>
                <Badge tone={TENANT_STATUS_TONES[t.status]}>{TENANT_STATUS_LABELS[t.status]}</Badge>
              </div>
              <div className={styles.muted}>{t.branchCount}</div>
              <div className={styles.muted}>{t.staffUserCount}</div>
            </div>
          ))
        )}
      </div>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} width={480}>
        <form onSubmit={handleSubmit}>
          <div className={styles.modalTitle}>Yeni Klinik Oluştur</div>

          <FieldWrap label="Klinik adı">
            <Input
              value={form.tenantName}
              onChange={(e) => setForm((f) => ({ ...f, tenantName: e.target.value }))}
              required
            />
          </FieldWrap>
          <FieldWrap label="Vergi numarası">
            <Input value={form.taxNumber} onChange={(e) => setForm((f) => ({ ...f, taxNumber: e.target.value }))} />
          </FieldWrap>
          <FieldWrap label="Şube adı">
            <Input
              placeholder="Merkez Şube"
              value={form.branchName}
              onChange={(e) => setForm((f) => ({ ...f, branchName: e.target.value }))}
              required
            />
          </FieldWrap>
          <FieldWrap label="Yetkili adı soyadı">
            <Input
              value={form.adminFullName}
              onChange={(e) => setForm((f) => ({ ...f, adminFullName: e.target.value }))}
              required
            />
          </FieldWrap>
          <FieldWrap label="Yetkili e-postası">
            <Input
              type="email"
              value={form.adminEmail}
              onChange={(e) => setForm((f) => ({ ...f, adminEmail: e.target.value }))}
              required
            />
          </FieldWrap>
          <FieldWrap label="Geçici şifre">
            <Input
              type="password"
              minLength={8}
              placeholder="En az 8 karakter"
              value={form.adminPassword}
              onChange={(e) => setForm((f) => ({ ...f, adminPassword: e.target.value }))}
              required
            />
          </FieldWrap>

          <div className={styles.modalActions}>
            <Button type="button" variant="secondary" onClick={() => setModalOpen(false)}>
              Vazgeç
            </Button>
            <Button type="submit" variant="primary" disabled={saving}>
              {saving ? 'Oluşturuluyor...' : 'Klinik Oluştur'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
```

- [ ] **Step 3: TypeScript compile check**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/platformAdminApi.ts frontend/src/pages/platform-admin/TenantListPage.tsx
git commit -m "feat: add \"Yeni Klinik Oluştur\" to Platform Admin Panel"
```

---

### Task 5: Remove self-service clinic registration (frontend)

**Files:**
- Modify: `frontend/src/pages/LoginPage.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/auth/AuthContext.tsx`
- Modify: `frontend/src/api/authApi.ts`
- Delete: `frontend/src/pages/auth/RegisterClinicPage.tsx`

**Interfaces:** none — pure removal.

This task must run AFTER Task 4 lands (platform admin already has a working replacement before the public UI path disappears).

- [ ] **Step 1: Remove the footnote link from `LoginPage.tsx`**

Delete this block (currently lines 66-69, right before the closing `</form>`):

```tsx
        <p className={styles.footNote}>
          Kliniğiniz için ilk kez mi kayıt oluyorsunuz? <Link to="/kayit">Klinik kaydı oluştur</Link>
        </p>
```

The `Link` import from `react-router-dom` (line 2: `import { Link, useNavigate } from 'react-router-dom';`) becomes unused after this deletion — change it to:

```tsx
import { useNavigate } from 'react-router-dom';
```

- [ ] **Step 2: Remove the `/kayit` route from `App.tsx`**

Delete the import (currently line 4):
```tsx
import { RegisterClinicPage } from './pages/auth/RegisterClinicPage';
```

Delete the route (currently line 33):
```tsx
      <Route path="/kayit" element={<RegisterClinicPage />} />
```

- [ ] **Step 3: Remove `registerClinic` from `AuthContext.tsx` — full file**

```tsx
import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { setAuthToken, setUnauthorizedHandler } from '../api/client';
import { authApi, LoginPayload } from '../api/authApi';
import { AuthSession, clearStoredSession, loadStoredSession, storeSession } from './session';

interface AuthContextValue {
  session: AuthSession | null;
  login: (payload: LoginPayload) => Promise<AuthSession>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => {
    const initial = loadStoredSession();
    setAuthToken(initial?.token ?? null);
    return initial;
  });

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearStoredSession();
      setAuthToken(null);
      setSession(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  async function login(payload: LoginPayload) {
    const result = await authApi.login(payload);
    storeSession(result);
    setAuthToken(result.token);
    setSession(result);
    return result;
  }

  function logout() {
    clearStoredSession();
    setAuthToken(null);
    setSession(null);
  }

  return (
    <AuthContext.Provider value={{ session, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth, AuthProvider disinda cagrildi');
  }
  return context;
}
```

- [ ] **Step 4: Remove `registerClinic`/`RegisterClinicPayload` from `authApi.ts` — full file**

```typescript
import { apiClient } from './client';
import { AuthSession } from '../auth/session';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface BranchOverview {
  branchId: string;
  tenantName: string;
  branchName: string;
  address: string | null;
  city: string | null;
  timezone: string | null;
  tarbilBranchCode: string | null;
}

export interface UpdateBranchDetailsPayload {
  address: string;
  city: string;
  timezone: string;
  tarbilBranchCode?: string;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export const authApi = {
  login: (payload: LoginPayload) => apiClient.post<AuthSession>('/api/v1/auth/login', payload),
  getCurrentBranch: () => apiClient.get<BranchOverview>('/api/v1/branches/current'),
  updateCurrentBranch: (payload: UpdateBranchDetailsPayload) =>
    apiClient.put<void>('/api/v1/branches/current', payload),
  changePassword: (payload: ChangePasswordPayload) =>
    apiClient.put<void>('/api/v1/staff-users/me/password', payload),
};
```

- [ ] **Step 5: Delete `RegisterClinicPage.tsx`**

```bash
git rm frontend/src/pages/auth/RegisterClinicPage.tsx
```

(`frontend/src/pages/auth/AuthForm.module.css` is shared with `AcceptInvitePage.tsx`/`SetupWizardPage.tsx` — do NOT delete it.)

- [ ] **Step 6: TypeScript compile check + production build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS, no errors

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/LoginPage.tsx frontend/src/App.tsx frontend/src/auth/AuthContext.tsx frontend/src/api/authApi.ts
git commit -m "refactor: remove self-service clinic registration (frontend)"
```

---

### Task 6: Update golden-path e2e test for the new onboarding flow

**Files:**
- Modify: `frontend/e2e/golden-path.spec.ts`

**Interfaces:**
- Consumes: `/platform-admin/login` (existing page), "+ Yeni Klinik" button + modal fields (Task 4), `/login` (existing page)

This task must run AFTER Tasks 4-5 land (the new UI must exist and the old one must be gone for this test to be meaningful).

- [ ] **Step 1: Replace step 1 with two new steps, renumber the rest — full file**

```ts
import { test, expect, Page } from '@playwright/test';

/**
 * Backend Field.tsx'teki FieldWrap, <label> ile <input>/<select>/<textarea>'yı
 * htmlFor/id ile eşlemiyor (bkz. components/ui/Field.tsx) -- bu yüzden
 * getByLabel() güvenilir çalışmıyor. Bunun yerine label metninin hemen
 * bir sonraki kardeş elemanını (FieldWrap'in gerçek DOM yapısı: <div><label/>
 * {children}</div>) buluyoruz. Checkbox'lar (KVKK, saldırgan vb.) <label>
 * içine SARILI olduğu için onlarda getByLabel() sorunsuz çalışıyor.
 */
function field(page: Page, label: string) {
  return page.locator(`xpath=//label[normalize-space(.)="${label}"]/following-sibling::*[1]`);
}

test('Platform admin → Randevu → Muayene → Fatura altın yolu', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  const runId = Date.now();
  const clinicName = `E2E Klinik ${runId}`;
  const adminEmail = `e2e-admin-${runId}@example.com`;
  const password = 'password123';
  const ownerName = `E2E Sahip ${runId}`;
  const ownerPhone = `555${String(runId).slice(-7)}`;
  const patientName = `E2E Pati ${runId}`;
  const speciesName = `E2E Tür ${runId}`;
  const serviceName = `E2E Hizmet ${runId}`;
  const serviceOptionLabel = `${serviceName} (30 dk)`;

  await test.step('1. Platform admin yeni klinik oluşturur', async () => {
    await page.goto('/platform-admin/login');
    await field(page, 'E-posta').fill('admin@myvet.local');
    await field(page, 'Şifre').fill('change-me-local-dev-only');
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/tenants/);

    await page.getByRole('button', { name: '+ Yeni Klinik' }).click();
    await field(page, 'Klinik adı').fill(clinicName);
    await field(page, 'Vergi numarası').fill('1234567890');
    await field(page, 'Şube adı').fill('Merkez');
    await field(page, 'Yetkili adı soyadı').fill('E2E Admin');
    await field(page, 'Yetkili e-postası').fill(adminEmail);
    await field(page, 'Geçici şifre').fill(password);
    await page.getByRole('button', { name: 'Klinik Oluştur' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/tenants\//);
  });

  await test.step('2. Klinik yöneticisi ilk girişini yapar', async () => {
    await page.goto('/login');
    await field(page, 'E-posta').fill(adminEmail);
    await field(page, 'Şifre').fill(password);
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    // LoginPage her zaman /panel'e yönlendiriyor (RegisterClinicPage'in eskiden
    // yaptığı gibi otomatik /kurulum'a değil) -- token'ın gerçekten saklandığından
    // emin olmak icin once /panel'e geçişi bekliyoruz, sonra /kurulum'a gidiyoruz.
    await expect(page).toHaveURL(/\/panel/);
    await page.goto('/kurulum');
  });

  await test.step('3. Kurulum sihirbazını tamamla', async () => {
    // SetupWizardPage mount olduğunda mevcut şube bilgisini çekip formu
    // üzerine yazıyor (setForm) -- bu bitmeden dolduruyorsak değerler silinir.
    await expect(field(page, 'Adres')).toBeEnabled();
    await field(page, 'Adres').fill('Test Cad. No:1');
    await field(page, 'Şehir').fill('İstanbul');
    await page.getByRole('button', { name: 'Kurulumu tamamla' }).click();
    await expect(page).toHaveURL(/\/panel/);
  });

  await test.step('4. Tür ekle (Ayarlar > Tür & Irk)', async () => {
    await page.goto('/ayarlar/tur-irk');
    const speciesForm = page.locator('form', { has: page.getByPlaceholder('Yeni tür adı (örn. Kemirgen)') });
    await speciesForm.getByPlaceholder('Yeni tür adı (örn. Kemirgen)').fill(speciesName);
    await speciesForm.getByRole('button', { name: 'Ekle' }).click();
    await expect(page.getByText(speciesName, { exact: true }).first()).toBeVisible();
  });

  await test.step('5. Hizmet tipi ekle (Ayarlar > Hizmetler)', async () => {
    await page.goto('/ayarlar/hizmetler');
    await field(page, 'Hizmet adı').fill(serviceName);
    await field(page, 'Fiyat (₺)').fill('500');
    await page.getByRole('button', { name: 'Ekle' }).click();
    await expect(page.getByText(serviceName)).toBeVisible();
  });

  await test.step('6. Yeni müşteri oluştur (KVKK onayı dahil)', async () => {
    await page.goto('/musteriler/yeni');
    await field(page, 'Ad Soyad').fill(ownerName);
    await field(page, 'Telefon').fill(ownerPhone);
    await page.getByLabel(/KVKK Aydınlatma Metni/).check();
    await page.getByRole('button', { name: 'Müşteriyi Kaydet' }).click();
    await expect(page).toHaveURL(/\/musteriler\//);
    await expect(page.getByText(ownerName)).toBeVisible();
  });

  await test.step('7. Yeni hasta oluştur', async () => {
    await page.goto('/hastalar/yeni');
    await page.getByPlaceholder('Müşteri adı veya telefonuyla arayın...').fill(ownerName);
    await page.getByText(ownerName).first().click();
    await field(page, 'Adı').fill(patientName);
    await field(page, 'Tür').selectOption({ label: speciesName });
    await page.getByRole('button', { name: 'Hastayı Kaydet' }).click();
    await expect(page).toHaveURL(/\/hastalar\//);
    await expect(page.getByText(patientName)).toBeVisible();
  });

  await test.step('8. Randevu oluştur', async () => {
    await page.goto('/randevu');
    await page.getByRole('button', { name: 'Yeni Randevu' }).click();
    await field(page, 'Hasta veya sahip ara').fill(patientName);
    await page.getByText(patientName, { exact: false }).first().click();
    await field(page, 'Hizmet').selectOption({ label: serviceOptionLabel });
    await page.getByRole('button', { name: 'Randevu Oluştur' }).click();
    await expect(page.getByText(patientName)).toBeVisible();
  });

  await test.step('9. Check-in yap → muayeneye başla', async () => {
    // ScheduleAppointmentModal source varsayılanı WALK_IN -- Appointment.schedule()
    // WIDGET olmayan kaynaklarda randevuyu doğrudan CONFIRMED açıyor, "Onayla"
    // adımı yalnızca web sitesi widget'ından gelen REQUESTED randevular için var.
    await page.getByText(patientName).first().click();
    await page.getByRole('button', { name: 'Check-in yap' }).click();

    await page.getByText(patientName).first().click();
    await page.getByRole('button', { name: "Muayeneyi Başlat (SOAP'a Git)" }).click();

    await expect(page).toHaveURL(/\/muayene\//);
  });

  await test.step('10. Vital bulguları gir', async () => {
    await field(page, 'Ağırlık (kg)').fill('12.5');
    await field(page, 'Ateş (°C)').fill('38.5');
    await field(page, 'Nabız (bpm)').fill('90');
    await field(page, 'Solunum (/dk)').fill('20');
    await page.getByRole('button', { name: 'Vitalleri Kaydet' }).click();
    // React StrictMode (dev) bazen aynı flash mesajını iki kez render ediyor -- .first() ile toleranslı.
    await expect(page.getByText('Vital bulgular kaydedildi').first()).toBeVisible();
  });

  await test.step('11. Fiziksel Muayene — bir sistemi Anormal işaretle', async () => {
    const cardioRow = page.locator('xpath=//div[normalize-space(text())="Kardiyovasküler"]/parent::div');
    await cardioRow.getByRole('button', { name: 'Anormal' }).click();
    await cardioRow.locator('textarea').fill('Üfürüm duyuldu (E2E test notu)');
    await page.getByRole('button', { name: 'Fiziksel Muayeneyi Kaydet' }).click();
    await expect(page.getByText('Fiziksel muayene kaydedildi').first()).toBeVisible();
  });

  await test.step('12. SOAP notunu doldur ve muayeneyi tamamla', async () => {
    await field(page, 'Subjective (S)').fill('Sahip, hastanın son 2 gündür iştahsız olduğunu belirtti.');
    await field(page, 'Objective (O)').fill('Genel durum iyi, hafif letarji mevcut.');
    await field(page, 'Assessment (A)').fill('Hafif gastroenterit şüphesi.');
    await field(page, 'Plan (P)').fill('Destekleyici tedavi, 3 gün sonra kontrol.');
    await page.getByRole('button', { name: 'SOAP Kaydet' }).click();
    await expect(page.getByText('SOAP kaydedildi').first()).toBeVisible();

    await page.getByRole('button', { name: 'Muayeneyi Tamamla' }).click();
    await expect(field(page, 'Subjective (S)')).toBeDisabled();
  });

  await test.step('13. Finans — otomatik taslak faturayı bul, kalem ekle, fatura kes', async () => {
    await page.goto('/finans');
    await page.getByText(ownerName).first().click();

    await field(page, 'Hizmet/Ürün (opsiyonel)').selectOption({ label: serviceName });
    await page.getByRole('button', { name: 'Ekle' }).click();
    // Aynı metin, sıfırlanan "Hizmet/Ürün" select'inde gizli <option> olarak da
    // eşleşiyor (strict-mode collision) -- .first() görünür kalem satırını alır.
    await expect(page.getByText(serviceName).first()).toBeVisible();

    await page.getByRole('button', { name: 'Faturayı Kes' }).click();
    await expect(page.getByText(/e-Fatura/)).toBeVisible();
  });

  await test.step('14. Ödeme al', async () => {
    await field(page, 'Tutar').fill('500');
    await page.getByRole('button', { name: 'Ödeme Al' }).click();
    // "Yöntem" select'indeki "Nakit" option'ıyla strict-mode collision -- .first() ödeme satırını alır.
    await expect(page.getByText('Nakit').first()).toBeVisible();
  });
});
```

- [ ] **Step 2: Run the golden-path test**

Prerequisite: backend running on `http://localhost:8080` against real Postgres (`docker compose up -d` in `backend/`, then `./mvnw spring-boot:run`), with the default platform admin bootstrap credentials (`admin@myvet.local` / `change-me-local-dev-only` unless overridden by `PLATFORM_ADMIN_EMAIL`/`PLATFORM_ADMIN_PASSWORD`).

Run: `cd frontend && npx playwright test`
Expected: 1 passed

- [ ] **Step 3: Commit**

```bash
git add frontend/e2e/golden-path.spec.ts
git commit -m "test: update golden-path e2e for platform-admin tenant onboarding"
```

---

### Task 7: Final verification (backend suite + frontend build + e2e + manual curl)

**Files:** (none — verification only)

- [ ] **Step 1: Full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS, all tests green including `ApplicationModulesTest`

- [ ] **Step 2: Frontend production build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 3: Golden-path e2e regression**

Run: `cd frontend && npx playwright test`
Expected: 1 passed

- [ ] **Step 4: Manual curl verification — old endpoint gone, new endpoint works**

```bash
cd backend && ./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--server.port=8081 > /tmp/tenant-onboarding-verify.log 2>&1 &
```

Wait for `grep -q "Started VetosApplication" /tmp/tenant-onboarding-verify.log`, then:

```bash
BASE="http://localhost:8081"
RUNID=$(date +%s)

echo "--- eski register-clinic endpoint'i artik yok (404 beklenir) ---"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST $BASE/api/v1/auth/register-clinic -H "Content-Type: application/json" \
  -d '{"tenantName":"Ghost","taxNumber":"1","branchName":"Merkez","adminFullName":"X","adminEmail":"ghost@example.com","adminPassword":"password123"}'

echo "--- platform admin login ---"
PA_LOGIN=$(curl -s -X POST $BASE/api/v1/platform-admin/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@myvet.local","password":"change-me-local-dev-only"}')
PA_TOKEN=$(echo "$PA_LOGIN" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

echo "--- platform admin yeni klinik olusturuyor (201 beklenir) ---"
CREATE=$(curl -s -i -X POST $BASE/api/v1/platform-admin/tenants -H "Authorization: Bearer $PA_TOKEN" -H "Content-Type: application/json" \
  -d '{"tenantName":"Onboarding Verify Klinik","taxNumber":"9998887771","branchName":"Merkez","adminFullName":"Admin Verify","adminEmail":"onboarding-admin-'"$RUNID"'@example.com","adminPassword":"password123"}')
echo "$CREATE" | head -1

echo "--- yeni klinik yoneticisi giris yapabiliyor (200 beklenir) ---"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"onboarding-admin-'"$RUNID"'@example.com","password":"password123"}'
```

Beklenen: sırasıyla `404`, sonra `201` (fatura oluşturma), sonra `200` (yeni yönetici girişi).

- [ ] **Step 5: Shut down the isolated instance**

```bash
PID=$(netstat -ano | grep ":8081" | grep LISTENING | awk '{print $5}' | head -1)
[ -n "$PID" ] && taskkill //F //PID $PID
```

- [ ] **Step 6: Update spec status**

In `docs/superpowers/specs/2026-08-29-platform-admin-tenant-onboarding-design.md`, change:
```
**Durum:** Onaylandı — implementasyon planı bekleniyor
```
to:
```
**Durum:** Implementasyon tamamlandı
```

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/specs/2026-08-29-platform-admin-tenant-onboarding-design.md
git commit -m "docs: mark platform-admin tenant onboarding spec as implemented"
```

---

## Spec Coverage Checklist (self-review)

- Login sayfasından self-servis kayıt kaldırılması → Task 5
- Backend `/register-clinic` endpoint'i + `RegisterClinicUseCase` kaldırılması → Task 3
- Platform admin panelinden klinik oluşturma (`TenantAdminPort.createTenant`) → Task 1
- Platform admin API endpoint'i (`POST /api/v1/platform-admin/tenants`) → Task 2
- Platform admin UI (buton + modal) → Task 4
- `architecture.md` §6.1 güncellemesi → Task 1
- e2e golden-path güncellemesi → Task 6
- Uçtan uca doğrulama (eski endpoint 404, yeni akış çalışıyor) → Task 7
