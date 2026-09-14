# Hızlı Satış (Retail POS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bir kliniğin kayıtlı veya kayıtsız (anonim) bir müşteriye, ürün seçip ödeme alarak tek ekranda hızlı satış yapabilmesini sağlamak; bu satış anasayfadaki "Bugünkü Satış" rakamına yansımalı ve stoktan otomatik düşmeli.

**Architecture:** Mevcut `billing` modülündeki Invoice/InvoiceLine/Payment altyapısı yeniden kullanılır (yeni bir "sale" kavramı icat edilmez). Yeni bir `CompleteQuickSaleUseCase`, mevcut `CreateManualInvoiceUseCase` → `AddInvoiceLineUseCase` → `IssueInvoiceUseCase` → `RecordPaymentUseCase` zincirini tek transaction'da orkestre eder. Anonim müşteri, `owners` tablosunda tenant başına tek bir sentinel kayıt olarak modellenir. Modüller arası iletişim (billing→inventory, billing→patient) mevcut `*LookupPort` deseniyle (domain paketi üzerinden) yapılır, `application`/`infrastructure` katmanlarına asla doğrudan erişilmez.

**Tech Stack:** Spring Boot 3.5 / Java 21 (backend), React + TypeScript + Vite (frontend), PostgreSQL + Flyway, JUnit 5 + Mockito + AssertJ, Playwright (e2e).

**Spec:** `docs/superpowers/specs/2026-09-14-hizli-satis-design.md`

## Global Constraints

- Modüller arası erişim SADECE hedef modülün `domain` paketindeki `*Port`/`*LookupPort` arayüzleri üzerinden yapılır — `application`/`infrastructure` paketleri asla başka modülden import edilmez (bkz. `modules/patient/domain/package-info.java`).
- Yeni exception sınıfları `com.vetos.platform.exception.DomainException`'ı extend eder; isim soneki HTTP durumunu belirler (`*NotFoundException`→404, `*ConflictException`→409, diğerleri→422) — bkz. `GlobalExceptionHandler.resolveStatus`.
- Backend testleri Mockito ile use-case seviyesinde yazılır (bu projede repository/entity için ayrı `@DataJpaTest` yok) — `UpdateOwnerUseCaseTest` bu deseni örnekler.
- Her yeni Flyway migration'ı sıradaki numarayı kullanır (şu an en yüksek: `V34`) ve mevcut satırları bozmayacak şekilde nullable/varsayılan değerli kolonlar ekler.
- Frontend'de yeni modallar mevcut `Modal`/`FieldWrap`/`Input`/`Select`/`Button` bileşenlerini kullanır, mevcut owner-picker deseninde `onMouseDown={(e) => e.preventDefault()}` dropdown'da MUTLAKA olmalı (bkz. `NewInvoiceModal.tsx`).

---

## Task 1: `Owner` — anonim müşteri sentinel kaydı (domain)

**Files:**
- Modify: `backend/src/main/resources/db/migration/` → Create: `V35__owner_anonymous_placeholder.sql`
- Modify: `backend/src/main/java/com/vetos/modules/patient/domain/Owner.java`
- Test: `backend/src/test/java/com/vetos/modules/patient/domain/OwnerTest.java` (yeni dosya)

**Interfaces:**
- Produces: `Owner.isAnonymousPlaceholder(): boolean` (getter, Lombok `@Getter` otomatik üretir), `Owner.createAnonymousPlaceholder(UUID tenantId): Owner` (static factory)

- [ ] **Step 1: Migration dosyasını oluştur**

`backend/src/main/resources/db/migration/V35__owner_anonymous_placeholder.sql`:
```sql
-- Kayitsiz/yoldan gecen musteriler icin tenant basina tek bir sentinel
-- owner kaydi (Hizli Satis ozelligi, bkz. docs/superpowers/specs/2026-09-14-hizli-satis-design.md).
ALTER TABLE owners ADD COLUMN is_anonymous_placeholder BOOLEAN NOT NULL DEFAULT FALSE;
```

- [ ] **Step 2: Migration'ın uygulandığını doğrula**

Backend'i yeniden başlat (`./run-local.sh` veya mevcut çalışan süreci yeniden başlat) ve loglarda şunu gör:
```
Migrating schema "public" to version "35 - owner anonymous placeholder"
```

- [ ] **Step 3: Başarısız testi yaz**

`backend/src/test/java/com/vetos/modules/patient/domain/OwnerTest.java` (yeni dosya):
```java
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
```

- [ ] **Step 4: Testi çalıştırıp derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q -Dtest=OwnerTest test`
Expected: derleme hatası (`createAnonymousPlaceholder` ve `isAnonymousPlaceholder` henüz yok)

- [ ] **Step 5: `Owner.java`'ya alanı ve factory metodunu ekle**

`backend/src/main/java/com/vetos/modules/patient/domain/Owner.java` — mevcut `national_id`/`birth_date` alanlarının hemen altına yeni kolon ekle:
```java
    @Column(name = "national_id")
    private String nationalId;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "is_anonymous_placeholder", nullable = false)
    private boolean anonymousPlaceholder;

    private String address;
```

`register(...)` factory metodundan hemen sonra yeni bir factory ekle:
```java
    public static Owner createAnonymousPlaceholder(UUID tenantId) {
        Owner owner = register(tenantId, "Anonim Müşteri", "0000000000", null, null);
        owner.anonymousPlaceholder = true;
        return owner;
    }
```

- [ ] **Step 6: Testi çalıştırıp geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -Dtest=OwnerTest test`
Expected: PASS (2 test — mevcut olsaydı; burada 1 test, PASS)

- [ ] **Step 7: Commit**

```bash
cd backend && git add src/main/resources/db/migration/V35__owner_anonymous_placeholder.sql src/main/java/com/vetos/modules/patient/domain/Owner.java src/test/java/com/vetos/modules/patient/domain/OwnerTest.java
git commit -m "feat: Owner icin anonim musteri sentinel kaydi destegi"
```

---

## Task 2: `OwnerRepository` — anonim placeholder sorgusu

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/patient/domain/OwnerRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/OwnerJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/OwnerRepositoryAdapter.java`

**Interfaces:**
- Consumes: `Owner` (Task 1)
- Produces: `OwnerRepository.findAnonymousPlaceholder(UUID tenantId): Optional<Owner>`

- [ ] **Step 1: `OwnerRepository` arayüzüne metodu ekle**

`backend/src/main/java/com/vetos/modules/patient/domain/OwnerRepository.java` — mevcut son satırdan önce ekle:
```java
public interface OwnerRepository {
    Owner save(Owner owner);
    Optional<Owner> findById(UUID id);
    List<Owner> searchByTenantAndQuery(UUID tenantId, String query);
    List<Owner> findByTenantIdWithFilters(UUID tenantId, String nameContains, Instant registeredFrom, Instant registeredTo);
    Optional<Owner> findAnonymousPlaceholder(UUID tenantId);
}
```

- [ ] **Step 2: `OwnerJpaRepository`'e native query ekle**

`backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/OwnerJpaRepository.java` — mevcut `findByTenantIdWithFilters` metodundan sonra, kapanış `}`'dan önce ekle:
```java
    @Query(
        value = "SELECT * FROM owners o WHERE o.tenant_id = :tenantId AND o.is_anonymous_placeholder = true LIMIT 1",
        nativeQuery = true
    )
    Optional<Owner> findAnonymousPlaceholder(@Param("tenantId") UUID tenantId);
```

(Dosyanın üstünde zaten `import java.util.Optional;` yoksa ekle — mevcut importlara bak, `UUID` zaten import edilmiş.)

- [ ] **Step 3: `OwnerRepositoryAdapter`'a delegasyonu ekle**

`backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/OwnerRepositoryAdapter.java` — mevcut son `@Override` metodundan sonra, kapanış `}`'dan önce ekle:
```java
    @Override
    public Optional<Owner> findAnonymousPlaceholder(UUID tenantId) {
        return jpaRepository.findAnonymousPlaceholder(tenantId);
    }
```

- [ ] **Step 4: Backend'in derlendiğini doğrula**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: hatasız derleme (bu görevde birim testi yok — repository adaptörleri bu kod tabanında ayrıca test edilmiyor, bkz. Global Constraints)

- [ ] **Step 5: Commit**

```bash
cd backend && git add src/main/java/com/vetos/modules/patient/domain/OwnerRepository.java src/main/java/com/vetos/modules/patient/infrastructure/persistence/OwnerJpaRepository.java src/main/java/com/vetos/modules/patient/infrastructure/persistence/OwnerRepositoryAdapter.java
git commit -m "feat: OwnerRepository.findAnonymousPlaceholder sorgusu"
```

---

## Task 3: `GetOrCreateAnonymousOwnerUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/patient/application/GetOrCreateAnonymousOwnerUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/patient/application/GetOrCreateAnonymousOwnerUseCaseTest.java`

**Interfaces:**
- Consumes: `OwnerRepository.findAnonymousPlaceholder`/`save` (Task 2), `Owner.createAnonymousPlaceholder` (Task 1)
- Produces: `GetOrCreateAnonymousOwnerUseCase.execute(UUID tenantId): UUID`

- [ ] **Step 1: Başarısız testi yaz**

`backend/src/test/java/com/vetos/modules/patient/application/GetOrCreateAnonymousOwnerUseCaseTest.java`:
```java
package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrCreateAnonymousOwnerUseCaseTest {

    @Mock private OwnerRepository ownerRepository;

    private GetOrCreateAnonymousOwnerUseCase useCase;

    @Test
    void should_returnExistingId_when_placeholderAlreadyExists() {
        useCase = new GetOrCreateAnonymousOwnerUseCase(ownerRepository);
        UUID tenantId = UUID.randomUUID();
        Owner existing = Owner.createAnonymousPlaceholder(tenantId);
        when(ownerRepository.findAnonymousPlaceholder(tenantId)).thenReturn(Optional.of(existing));

        UUID result = useCase.execute(tenantId);

        assertThat(result).isEqualTo(existing.getId());
        verify(ownerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_createAndReturnNewId_when_placeholderMissing() {
        useCase = new GetOrCreateAnonymousOwnerUseCase(ownerRepository);
        UUID tenantId = UUID.randomUUID();
        when(ownerRepository.findAnonymousPlaceholder(tenantId)).thenReturn(Optional.empty());
        when(ownerRepository.save(org.mockito.ArgumentMatchers.any(Owner.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UUID result = useCase.execute(tenantId);

        assertThat(result).isNotNull();
        verify(ownerRepository).save(org.mockito.ArgumentMatchers.any(Owner.class));
    }
}
```

- [ ] **Step 2: Testi çalıştırıp derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q -Dtest=GetOrCreateAnonymousOwnerUseCaseTest test`
Expected: derleme hatası (`GetOrCreateAnonymousOwnerUseCase` henüz yok)

- [ ] **Step 3: Use case'i implemente et**

`backend/src/main/java/com/vetos/modules/patient/application/GetOrCreateAnonymousOwnerUseCase.java`:
```java
package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Hizli Satis akisinda kayitli olmayan/kayit gerekmeyen musteriler icin
 * kullanilir -- bkz. docs/superpowers/specs/2026-09-14-hizli-satis-design.md
 * S4.1. Nadir bir race durumunda (ayni anda iki ilk-satis) iki sentinel
 * owner olusabilir; veri butunlugunu bozmadigi icin ekstra kilitleme
 * eklenmedi (YAGNI).
 */
@Service
@RequiredArgsConstructor
public class GetOrCreateAnonymousOwnerUseCase {

    private final OwnerRepository ownerRepository;

    @Transactional
    public UUID execute(UUID tenantId) {
        return ownerRepository.findAnonymousPlaceholder(tenantId)
            .map(Owner::getId)
            .orElseGet(() -> ownerRepository.save(Owner.createAnonymousPlaceholder(tenantId)).getId());
    }
}
```

- [ ] **Step 4: Testi çalıştırıp geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -Dtest=GetOrCreateAnonymousOwnerUseCaseTest test`
Expected: PASS (2 test)

- [ ] **Step 5: Commit**

```bash
cd backend && git add src/main/java/com/vetos/modules/patient/application/GetOrCreateAnonymousOwnerUseCase.java src/test/java/com/vetos/modules/patient/application/GetOrCreateAnonymousOwnerUseCaseTest.java
git commit -m "feat: GetOrCreateAnonymousOwnerUseCase"
```

---

## Task 4: `OwnerLookupPort` genişletmesi (billing → patient köprüsü)

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/patient/domain/OwnerLookupPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/OwnerLookupAdapter.java`

**Interfaces:**
- Consumes: `GetOrCreateAnonymousOwnerUseCase.execute` (Task 3)
- Produces: `OwnerLookupPort.getOrCreateAnonymousOwnerId(UUID tenantId): UUID` — Task 7 (`CompleteQuickSaleUseCase`) bunu kullanacak

- [ ] **Step 1: Port arayüzüne metodu ekle**

`backend/src/main/java/com/vetos/modules/patient/domain/OwnerLookupPort.java`:
```java
package com.vetos.modules.patient.domain;

import java.util.UUID;

/**
 * Diger moduller (billing, appointment...) sahip bilgisine SADECE bu port
 * uzerinden erisir. OwnerRepository'yi ASLA import etmezler.
 */
public interface OwnerLookupPort {
    OwnerSummary findSummaryById(UUID ownerId);

    /** Hizli Satis icin: tenant'in anonim musteri kaydini bulur/olusturur. */
    UUID getOrCreateAnonymousOwnerId(UUID tenantId);
}
```

- [ ] **Step 2: Adaptöre implementasyonu ekle**

`backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/OwnerLookupAdapter.java` — tam dosya:
```java
package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.application.GetOrCreateAnonymousOwnerUseCase;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.OwnerSummary;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class OwnerLookupAdapter implements OwnerLookupPort {

    private final OwnerJpaRepository jpaRepository;
    private final GetOrCreateAnonymousOwnerUseCase getOrCreateAnonymousOwnerUseCase;

    @Override
    public OwnerSummary findSummaryById(UUID ownerId) {
        Owner owner = jpaRepository.findById(ownerId).orElseThrow(() -> new OwnerNotFoundException(ownerId));
        return new OwnerSummary(
            owner.getId(), owner.getFullName(), owner.getPhone(), owner.getAddress(), owner.getCity(), owner.getDistrict(),
            owner.getNationalId(), owner.isSmsConsent(), owner.isWhatsappConsent()
        );
    }

    @Override
    public UUID getOrCreateAnonymousOwnerId(UUID tenantId) {
        return getOrCreateAnonymousOwnerUseCase.execute(tenantId);
    }
}
```

- [ ] **Step 3: Derlemeyi ve Modulith testini doğrula**

Run: `cd backend && ./mvnw -q -DskipTests compile && ./mvnw -q -Dtest=ApplicationModulesTest test`
Expected: ikisi de hatasız/PASS (bu adaptör `patient` modülü içinde kalıyor — `application` katmanına kendi modülü içinde erişiyor, sorun olmamalı)

- [ ] **Step 4: Commit**

```bash
cd backend && git add src/main/java/com/vetos/modules/patient/domain/OwnerLookupPort.java src/main/java/com/vetos/modules/patient/infrastructure/persistence/OwnerLookupAdapter.java
git commit -m "feat: OwnerLookupPort.getOrCreateAnonymousOwnerId"
```

---

## Task 5: Stok düşümü portu (`inventory` → `billing` köprüsü)

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/inventory/domain/exception/InsufficientStockException.java`
- Create: `backend/src/main/java/com/vetos/modules/inventory/domain/StockDeductionPort.java`
- Create: `backend/src/main/java/com/vetos/modules/inventory/infrastructure/StockDeductionAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/inventory/infrastructure/StockDeductionAdapterTest.java`

**Interfaces:**
- Consumes: `InventoryItemRepository.findById` (mevcut), `RecordStockMovementUseCase.execute` (mevcut, aynı modül içi)
- Produces: `StockDeductionPort.deductForSale(UUID inventoryItemId, int quantity, UUID invoiceId): void` — Task 6 bunu kullanacak

- [ ] **Step 1: `InsufficientStockException`'ı oluştur**

`backend/src/main/java/com/vetos/modules/inventory/domain/exception/InsufficientStockException.java`:
```java
package com.vetos.modules.inventory.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class InsufficientStockException extends DomainException {
    public InsufficientStockException(UUID inventoryItemId, int requested, int available) {
        super(
            "INSUFFICIENT_STOCK",
            "Stok yetersiz: kalem=" + inventoryItemId + ", istenen=" + requested + ", mevcut=" + available
        );
    }
}
```

- [ ] **Step 2: Port arayüzünü oluştur**

`backend/src/main/java/com/vetos/modules/inventory/domain/StockDeductionPort.java`:
```java
package com.vetos.modules.inventory.domain;

import java.util.UUID;

/**
 * Diger moduller (billing) stok dusumune SADECE bu port uzerinden erisir --
 * RecordStockMovementUseCase'i (application katmani) ASLA import etmezler.
 */
public interface StockDeductionPort {
    /** quantityOnHand yetersizse InsufficientStockException firlatir, aksi halde OUT hareketi kaydeder. */
    void deductForSale(UUID inventoryItemId, int quantity, UUID invoiceId);
}
```

- [ ] **Step 3: Başarısız testi yaz**

`backend/src/test/java/com/vetos/modules/inventory/infrastructure/StockDeductionAdapterTest.java`:
```java
package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.application.RecordStockMovementUseCase;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

@ExtendWith(MockitoExtension.class)
class StockDeductionAdapterTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private RecordStockMovementUseCase recordStockMovementUseCase;

    private StockDeductionAdapter adapter;

    @Test
    void should_throwInsufficientStock_when_quantityExceedsOnHand() {
        adapter = new StockDeductionAdapter(inventoryItemRepository, recordStockMovementUseCase);
        UUID itemId = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(
            UUID.randomUUID(), "Mama", "Gida", null, 2, 1, null, null, BigDecimal.TEN
        );
        when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adapter.deductForSale(itemId, 5, UUID.randomUUID()))
            .isInstanceOf(InsufficientStockException.class);

        verify(recordStockMovementUseCase, never()).execute(any(), any(), anyInt(), any(), any());
    }

    @Test
    void should_recordOutMovement_when_stockSufficient() {
        adapter = new StockDeductionAdapter(inventoryItemRepository, recordStockMovementUseCase);
        UUID itemId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(
            UUID.randomUUID(), "Mama", "Gida", null, 10, 1, null, null, BigDecimal.TEN
        );
        when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        adapter.deductForSale(itemId, 3, invoiceId);

        verify(recordStockMovementUseCase).execute(
            itemId, com.vetos.modules.inventory.domain.StockMovementType.OUT, 3,
            com.vetos.modules.inventory.domain.StockReferenceType.MANUAL, invoiceId
        );
    }
}
```

- [ ] **Step 4: Testi çalıştırıp derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q -Dtest=StockDeductionAdapterTest test`
Expected: derleme hatası (`StockDeductionAdapter` henüz yok)

- [ ] **Step 5: Adaptörü implemente et**

`backend/src/main/java/com/vetos/modules/inventory/infrastructure/StockDeductionAdapter.java`:
```java
package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.application.RecordStockMovementUseCase;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockDeductionPort;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.exception.InsufficientStockException;
import com.vetos.modules.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class StockDeductionAdapter implements StockDeductionPort {

    private final InventoryItemRepository inventoryItemRepository;
    private final RecordStockMovementUseCase recordStockMovementUseCase;

    @Override
    public void deductForSale(UUID inventoryItemId, int quantity, UUID invoiceId) {
        InventoryItem item = inventoryItemRepository.findById(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));

        if (item.getQuantityOnHand() < quantity) {
            throw new InsufficientStockException(inventoryItemId, quantity, item.getQuantityOnHand());
        }

        recordStockMovementUseCase.execute(inventoryItemId, StockMovementType.OUT, quantity, StockReferenceType.MANUAL, invoiceId);
    }
}
```

**Not:** Paket-private sınıf olduğu için test dosyasının `com.vetos.modules.inventory.infrastructure` paketinde olması gerekiyor (yukarıdaki gibi) — `StockDeductionAdapterTest` aynı pakette.

- [ ] **Step 6: Testi çalıştırıp geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -Dtest=StockDeductionAdapterTest test`
Expected: PASS (2 test)

- [ ] **Step 7: Commit**

```bash
cd backend && git add src/main/java/com/vetos/modules/inventory/domain/exception/InsufficientStockException.java src/main/java/com/vetos/modules/inventory/domain/StockDeductionPort.java src/main/java/com/vetos/modules/inventory/infrastructure/StockDeductionAdapter.java src/test/java/com/vetos/modules/inventory/infrastructure/StockDeductionAdapterTest.java
git commit -m "feat: stok dusumu icin StockDeductionPort"
```

---

## Task 6: `AddInvoiceLineUseCase` — ürün (inventory item) satırı desteği

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/dto/AddInvoiceLineCommand.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/api/dto/AddInvoiceLineRequest.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/AddInvoiceLineUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/api/InvoicesController.java:241-247` (mevcut `addLine` metodu)
- Test: `backend/src/test/java/com/vetos/modules/billing/application/AddInvoiceLineUseCaseTest.java`

**Interfaces:**
- Consumes: `StockDeductionPort.deductForSale` (Task 5)
- Produces: `AddInvoiceLineCommand` artık 8. alan olarak `UUID inventoryItemId` taşır — Task 7 bunu kullanacak

- [ ] **Step 1: Başarısız testi yaz**

`backend/src/test/java/com/vetos/modules/billing/application/AddInvoiceLineUseCaseTest.java`:
```java
package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.inventory.domain.StockDeductionPort;
import com.vetos.modules.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddInvoiceLineUseCaseTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private StockDeductionPort stockDeductionPort;

    private AddInvoiceLineUseCase useCase;

    private AddInvoiceLineCommand aCommand(UUID invoiceId, UUID inventoryItemId) {
        return new AddInvoiceLineCommand(
            invoiceId, "Kedi Maması", 2, BigDecimal.valueOf(150),
            BigDecimal.ZERO, BigDecimal.ZERO, null, inventoryItemId
        );
    }

    @Test
    void should_deductStock_when_inventoryItemIdProvided() {
        useCase = new AddInvoiceLineUseCase(invoiceRepository, invoiceLineRepository, stockDeductionPort);
        UUID invoiceId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceLineRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());

        useCase.execute(aCommand(invoiceId, itemId));

        verify(stockDeductionPort).deductForSale(itemId, 2, invoiceId);
    }

    @Test
    void should_notCallStockDeduction_when_inventoryItemIdNull() {
        useCase = new AddInvoiceLineUseCase(invoiceRepository, invoiceLineRepository, stockDeductionPort);
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceLineRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());

        useCase.execute(aCommand(invoiceId, null));

        verifyNoInteractions(stockDeductionPort);
    }

    @Test
    void should_propagateInsufficientStock_and_notAddLine() {
        useCase = new AddInvoiceLineUseCase(invoiceRepository, invoiceLineRepository, stockDeductionPort);
        UUID invoiceId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        doThrow(new InsufficientStockException(itemId, 2, 0)).when(stockDeductionPort).deductForSale(itemId, 2, invoiceId);

        assertThatThrownBy(() -> useCase.execute(aCommand(invoiceId, itemId)))
            .isInstanceOf(InsufficientStockException.class);

        verify(invoiceLineRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Testi çalıştırıp derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q -Dtest=AddInvoiceLineUseCaseTest test`
Expected: derleme hatası (constructor 3 parametre bekliyor, command 8. alanı yok)

- [ ] **Step 3: `AddInvoiceLineCommand`'a alanı ekle**

`backend/src/main/java/com/vetos/modules/billing/application/dto/AddInvoiceLineCommand.java` — tam dosya:
```java
package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AddInvoiceLineCommand(
    UUID invoiceId, String description, int quantity, BigDecimal unitPrice,
    BigDecimal discountAmount, BigDecimal vatRate, UUID serviceTypeId, UUID inventoryItemId
) {}
```

- [ ] **Step 4: `AddInvoiceLineRequest`'e alanı ekle**

`backend/src/main/java/com/vetos/modules/billing/api/dto/AddInvoiceLineRequest.java` — tam dosya:
```java
package com.vetos.modules.billing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record AddInvoiceLineRequest(
    @NotBlank String description,
    @Positive int quantity,
    @NotNull BigDecimal unitPrice,
    @PositiveOrZero BigDecimal discountAmount,
    @PositiveOrZero BigDecimal vatRate,
    UUID serviceTypeId,
    UUID inventoryItemId
) {}
```

- [ ] **Step 5: `AddInvoiceLineUseCase`'i güncelle**

`backend/src/main/java/com/vetos/modules/billing/application/AddInvoiceLineUseCase.java` — tam dosya:
```java
package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.domain.*;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.modules.inventory.domain.StockDeductionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddInvoiceLineUseCase {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final StockDeductionPort stockDeductionPort;

    @Transactional
    public UUID execute(AddInvoiceLineCommand command) {
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
            .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));

        if (command.inventoryItemId() != null) {
            stockDeductionPort.deductForSale(command.inventoryItemId(), command.quantity(), invoice.getId());
        }

        InvoiceLine line = invoiceLineRepository.save(InvoiceLine.create(
            invoice.getId(), command.description(), command.quantity(), command.unitPrice(),
            command.discountAmount(), command.vatRate(),
            command.serviceTypeId(), command.inventoryItemId(), InvoiceLineSource.MANUAL
        ));

        BigDecimal newTotal = invoiceLineRepository.findByInvoiceId(invoice.getId()).stream()
            .map(InvoiceLine::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.recalculateTotal(newTotal);
        invoiceRepository.save(invoice);

        return line.getId();
    }
}
```

- [ ] **Step 6: `InvoicesController.addLine`'ı güncelle**

`backend/src/main/java/com/vetos/modules/billing/api/InvoicesController.java` — mevcut `addLine` metodunu değiştir:
```java
    @PostMapping("/{id}/lines")
    public void addLine(@PathVariable UUID id, @RequestBody @Valid AddInvoiceLineRequest request) {
        addInvoiceLineUseCase.execute(new AddInvoiceLineCommand(
            id, request.description(), request.quantity(), request.unitPrice(),
            request.discountAmount(), request.vatRate(), request.serviceTypeId(), request.inventoryItemId()
        ));
    }
```

- [ ] **Step 7: Testi ve Modulith testini çalıştırıp geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -Dtest=AddInvoiceLineUseCaseTest,ApplicationModulesTest test`
Expected: PASS (3 + Modulith testleri)

- [ ] **Step 8: Commit**

```bash
cd backend && git add src/main/java/com/vetos/modules/billing/application/dto/AddInvoiceLineCommand.java src/main/java/com/vetos/modules/billing/api/dto/AddInvoiceLineRequest.java src/main/java/com/vetos/modules/billing/application/AddInvoiceLineUseCase.java src/main/java/com/vetos/modules/billing/api/InvoicesController.java src/test/java/com/vetos/modules/billing/application/AddInvoiceLineUseCaseTest.java
git commit -m "feat: fatura kalemine stok kalemi (urun) ekleme + otomatik stok dusumu"
```

---

## Task 7: `CompleteQuickSaleUseCase` + `/quick-sale` uç noktası

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/billing/application/dto/QuickSaleLineCommand.java`
- Create: `backend/src/main/java/com/vetos/modules/billing/application/dto/CompleteQuickSaleCommand.java`
- Create: `backend/src/main/java/com/vetos/modules/billing/application/CompleteQuickSaleUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/billing/api/dto/QuickSaleLineRequest.java`
- Create: `backend/src/main/java/com/vetos/modules/billing/api/dto/CompleteQuickSaleRequest.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/api/InvoicesController.java`
- Test: `backend/src/test/java/com/vetos/modules/billing/application/CompleteQuickSaleUseCaseTest.java`

**Interfaces:**
- Consumes: `CreateManualInvoiceUseCase.execute`, `AddInvoiceLineUseCase.execute` (Task 6), `IssueInvoiceUseCase.execute`, `RecordPaymentUseCase.execute`, `InvoiceRepository.findById`, `OwnerLookupPort.getOrCreateAnonymousOwnerId` (Task 4) — hepsi mevcut/önceki task'larda tanımlı
- Produces: `POST /api/v1/invoices/quick-sale` → `201 { id }`

- [ ] **Step 1: Command DTO'larını oluştur**

`backend/src/main/java/com/vetos/modules/billing/application/dto/QuickSaleLineCommand.java`:
```java
package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record QuickSaleLineCommand(UUID inventoryItemId, String description, int quantity, BigDecimal unitPrice) {}
```

`backend/src/main/java/com/vetos/modules/billing/application/dto/CompleteQuickSaleCommand.java`:
```java
package com.vetos.modules.billing.application.dto;

import com.vetos.modules.billing.domain.PaymentMethod;

import java.util.List;
import java.util.UUID;

public record CompleteQuickSaleCommand(
    UUID branchId, UUID ownerId, UUID staffUserId, List<QuickSaleLineCommand> lines, PaymentMethod paymentMethod
) {}
```

- [ ] **Step 2: Başarısız testi yaz**

`backend/src/test/java/com/vetos/modules/billing/application/CompleteQuickSaleUseCaseTest.java`:
```java
package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.application.dto.CompleteQuickSaleCommand;
import com.vetos.modules.billing.application.dto.QuickSaleLineCommand;
import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.PaymentMethod;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompleteQuickSaleUseCaseTest {

    @Mock private CreateManualInvoiceUseCase createManualInvoiceUseCase;
    @Mock private AddInvoiceLineUseCase addInvoiceLineUseCase;
    @Mock private IssueInvoiceUseCase issueInvoiceUseCase;
    @Mock private RecordPaymentUseCase recordPaymentUseCase;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private OwnerLookupPort ownerLookupPort;

    private CompleteQuickSaleUseCase useCase;

    private QuickSaleLineCommand aLine() {
        return new QuickSaleLineCommand(UUID.randomUUID(), "Kedi Maması", 1, BigDecimal.valueOf(200));
    }

    @Test
    void should_useProvidedOwner_when_ownerIdGiven() {
        useCase = new CompleteQuickSaleUseCase(
            createManualInvoiceUseCase, addInvoiceLineUseCase, issueInvoiceUseCase,
            recordPaymentUseCase, invoiceRepository, ownerLookupPort
        );
        UUID ownerId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(createManualInvoiceUseCase.execute(branchId, ownerId, staffId)).thenReturn(invoiceId);
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), branchId, ownerId, null, staffId);
        invoice.recalculateTotal(BigDecimal.valueOf(200));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        UUID result = useCase.execute(new CompleteQuickSaleCommand(
            branchId, ownerId, staffId, List.of(aLine()), PaymentMethod.CASH
        ));

        assertThat(result).isEqualTo(invoiceId);
        verifyNoInteractions(ownerLookupPort);
        verify(issueInvoiceUseCase).execute(invoiceId);
        verify(recordPaymentUseCase).execute(new RecordPaymentCommand(invoiceId, PaymentMethod.CASH, BigDecimal.valueOf(200), null));
    }

    @Test
    void should_resolveAnonymousOwner_when_ownerIdNull() {
        useCase = new CompleteQuickSaleUseCase(
            createManualInvoiceUseCase, addInvoiceLineUseCase, issueInvoiceUseCase,
            recordPaymentUseCase, invoiceRepository, ownerLookupPort
        );
        UUID branchId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID anonymousOwnerId = UUID.randomUUID();
        when(ownerLookupPort.getOrCreateAnonymousOwnerId(any())).thenReturn(anonymousOwnerId);
        when(createManualInvoiceUseCase.execute(branchId, anonymousOwnerId, staffId)).thenReturn(invoiceId);
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), branchId, anonymousOwnerId, null, staffId);
        invoice.recalculateTotal(BigDecimal.valueOf(200));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        useCase.execute(new CompleteQuickSaleCommand(branchId, null, staffId, List.of(aLine()), PaymentMethod.CASH));

        verify(createManualInvoiceUseCase).execute(branchId, anonymousOwnerId, staffId);
    }

    @Test
    void should_addEachLine_beforeIssuing() {
        useCase = new CompleteQuickSaleUseCase(
            createManualInvoiceUseCase, addInvoiceLineUseCase, issueInvoiceUseCase,
            recordPaymentUseCase, invoiceRepository, ownerLookupPort
        );
        UUID ownerId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        QuickSaleLineCommand line1 = aLine();
        QuickSaleLineCommand line2 = aLine();
        when(createManualInvoiceUseCase.execute(branchId, ownerId, staffId)).thenReturn(invoiceId);
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), branchId, ownerId, null, staffId);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        useCase.execute(new CompleteQuickSaleCommand(branchId, ownerId, staffId, List.of(line1, line2), PaymentMethod.CARD));

        verify(addInvoiceLineUseCase).execute(new AddInvoiceLineCommand(
            invoiceId, line1.description(), line1.quantity(), line1.unitPrice(),
            BigDecimal.ZERO, BigDecimal.ZERO, null, line1.inventoryItemId()
        ));
        verify(addInvoiceLineUseCase).execute(new AddInvoiceLineCommand(
            invoiceId, line2.description(), line2.quantity(), line2.unitPrice(),
            BigDecimal.ZERO, BigDecimal.ZERO, null, line2.inventoryItemId()
        ));
    }
}
```

- [ ] **Step 3: Testi çalıştırıp derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q -Dtest=CompleteQuickSaleUseCaseTest test`
Expected: derleme hatası (`CompleteQuickSaleUseCase` henüz yok)

- [ ] **Step 4: Use case'i implemente et**

`backend/src/main/java/com/vetos/modules/billing/application/CompleteQuickSaleUseCase.java`:
```java
package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.application.dto.CompleteQuickSaleCommand;
import com.vetos.modules.billing.application.dto.QuickSaleLineCommand;
import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tek ekranli "Hizli Satis" akisi -- kayitli ya da anonim bir musteriye,
 * tek transaction'da fatura acar, kalemleri ekler (stok duser), keser ve
 * tam odemeyi alir. Herhangi bir adim (orn. stok yetersiz) hata verirse
 * tum islem geri alinir. Bkz. docs/superpowers/specs/2026-09-14-hizli-satis-design.md S4.4.
 */
@Service
@RequiredArgsConstructor
public class CompleteQuickSaleUseCase {

    private final CreateManualInvoiceUseCase createManualInvoiceUseCase;
    private final AddInvoiceLineUseCase addInvoiceLineUseCase;
    private final IssueInvoiceUseCase issueInvoiceUseCase;
    private final RecordPaymentUseCase recordPaymentUseCase;
    private final InvoiceRepository invoiceRepository;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional
    public UUID execute(CompleteQuickSaleCommand command) {
        UUID resolvedOwnerId = command.ownerId() != null
            ? command.ownerId()
            : ownerLookupPort.getOrCreateAnonymousOwnerId(com.vetos.platform.tenancy.TenantContext.current());

        UUID invoiceId = createManualInvoiceUseCase.execute(command.branchId(), resolvedOwnerId, command.staffUserId());

        for (QuickSaleLineCommand line : command.lines()) {
            addInvoiceLineUseCase.execute(new AddInvoiceLineCommand(
                invoiceId, line.description(), line.quantity(), line.unitPrice(),
                BigDecimal.ZERO, BigDecimal.ZERO, null, line.inventoryItemId()
            ));
        }

        issueInvoiceUseCase.execute(invoiceId);

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        recordPaymentUseCase.execute(new RecordPaymentCommand(invoiceId, command.paymentMethod(), invoice.getTotalAmount(), null));

        return invoiceId;
    }
}
```

- [ ] **Step 5: Testi çalıştırıp geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -Dtest=CompleteQuickSaleUseCaseTest test`
Expected: PASS (3 test)

- [ ] **Step 6: API DTO'larını oluştur**

`backend/src/main/java/com/vetos/modules/billing/api/dto/QuickSaleLineRequest.java`:
```java
package com.vetos.modules.billing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record QuickSaleLineRequest(
    UUID inventoryItemId, @NotBlank String description, @Positive int quantity, @NotNull BigDecimal unitPrice
) {}
```

`backend/src/main/java/com/vetos/modules/billing/api/dto/CompleteQuickSaleRequest.java`:
```java
package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CompleteQuickSaleRequest(
    UUID ownerId, @NotEmpty @Valid List<QuickSaleLineRequest> lines, @NotNull PaymentMethod paymentMethod
) {}
```

(`ownerId` kasıtlı olarak `@NotNull` DEĞİL — null ise anonim müşteri anlamına gelir.)

- [ ] **Step 7: Controller'a uç noktayı ekle**

`backend/src/main/java/com/vetos/modules/billing/api/InvoicesController.java` — üstteki `import` bloğuna `CompleteQuickSaleUseCase`, `CompleteQuickSaleCommand`, `QuickSaleLineCommand` ekle (zaten `import com.vetos.modules.billing.application.*;` ve `com.vetos.modules.billing.api.dto.*;` var, ek import gerekmeyebilir — `application.dto.*` için ayrı importlar var, `QuickSaleLineCommand`/`CompleteQuickSaleCommand` için de ekle):
```java
import com.vetos.modules.billing.application.dto.CompleteQuickSaleCommand;
import com.vetos.modules.billing.application.dto.QuickSaleLineCommand;
```

`private final CompleteQuickSaleUseCase completeQuickSaleUseCase;` alanını diğer use case alanlarının yanına ekle, sonra `create` metodundan hemen sonra yeni endpoint'i ekle — mevcut `create` metoduyla AYNI desen (Location header, JSON body yok — `patient` modülünün `OwnerResponse`'unu ASLA import etme, bu modül sınırı ihlali olurdu):
```java
    @PostMapping("/quick-sale")
    public ResponseEntity<Void> quickSale(
        @AuthenticationPrincipal AuthenticatedStaffUser principal, @RequestBody @Valid CompleteQuickSaleRequest request
    ) {
        List<QuickSaleLineCommand> lines = request.lines().stream()
            .map(l -> new QuickSaleLineCommand(l.inventoryItemId(), l.description(), l.quantity(), l.unitPrice()))
            .toList();
        UUID id = completeQuickSaleUseCase.execute(new CompleteQuickSaleCommand(
            principal.branchIds().get(0), request.ownerId(), principal.staffUserId(), lines, request.paymentMethod()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/invoices/" + id)).build();
    }
```

Bu, `billingApi.quickSale`'in (Task 9) `apiClient.postForId(...)` kullanmasıyla eşleşir — `postForId` gövdeyi değil `Location` header'ını okur.

- [ ] **Step 8: Backend'in derlendiğini ve Modulith testinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -DskipTests compile && ./mvnw -q -Dtest=ApplicationModulesTest test`
Expected: ikisi de hatasız

- [ ] **Step 9: Commit**

```bash
cd backend && git add src/main/java/com/vetos/modules/billing/application/dto/QuickSaleLineCommand.java src/main/java/com/vetos/modules/billing/application/dto/CompleteQuickSaleCommand.java src/main/java/com/vetos/modules/billing/application/CompleteQuickSaleUseCase.java src/main/java/com/vetos/modules/billing/api/dto/QuickSaleLineRequest.java src/main/java/com/vetos/modules/billing/api/dto/CompleteQuickSaleRequest.java src/main/java/com/vetos/modules/billing/api/InvoicesController.java src/test/java/com/vetos/modules/billing/application/CompleteQuickSaleUseCaseTest.java
git commit -m "feat: CompleteQuickSaleUseCase + POST /api/v1/invoices/quick-sale"
```

---

## Task 8: `GetTodaySalesSummaryUseCase` + `/today-summary` uç noktası

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/billing/application/dto/TodaySalesSummary.java`
- Create: `backend/src/main/java/com/vetos/modules/billing/application/GetTodaySalesSummaryUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/billing/api/dto/TodaySalesSummaryResponse.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/api/InvoicesController.java`
- Test: `backend/src/test/java/com/vetos/modules/billing/application/GetTodaySalesSummaryUseCaseTest.java`

**Interfaces:**
- Consumes: `InvoiceRepository.findByTenantId` (mevcut)
- Produces: `GET /api/v1/invoices/today-summary` → `{ totalAmount, saleCount }`, tüm rollere açık

- [ ] **Step 1: Başarısız testi yaz**

`backend/src/test/java/com/vetos/modules/billing/application/GetTodaySalesSummaryUseCaseTest.java`:
```java
package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.TodaySalesSummary;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTodaySalesSummaryUseCaseTest {

    @Mock private InvoiceRepository invoiceRepository;

    private GetTodaySalesSummaryUseCase useCase;

    private Invoice issuedInvoiceAt(Instant issuedAt, BigDecimal total) throws Exception {
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        invoice.recalculateTotal(total);
        invoice.issue();
        setPrivateField(invoice, "issuedAt", issuedAt);
        return invoice;
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void should_sumOnlyTodaysIssuedInvoices() throws Exception {
        useCase = new GetTodaySalesSummaryUseCase(invoiceRepository);
        UUID tenantId = UUID.randomUUID();
        Invoice today1 = issuedInvoiceAt(Instant.now(), BigDecimal.valueOf(100));
        Invoice today2 = issuedInvoiceAt(Instant.now().minus(1, ChronoUnit.HOURS), BigDecimal.valueOf(50));
        Invoice yesterday = issuedInvoiceAt(Instant.now().minus(2, ChronoUnit.DAYS), BigDecimal.valueOf(999));
        when(invoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(today1, today2, yesterday));

        TodaySalesSummary result = useCase.execute(tenantId);

        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(result.saleCount()).isEqualTo(2);
    }

    @Test
    void should_returnZero_when_noInvoicesToday() {
        useCase = new GetTodaySalesSummaryUseCase(invoiceRepository);
        UUID tenantId = UUID.randomUUID();
        when(invoiceRepository.findByTenantId(tenantId)).thenReturn(List.of());

        TodaySalesSummary result = useCase.execute(tenantId);

        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.saleCount()).isZero();
    }
}
```

- [ ] **Step 2: Testi çalıştırıp derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q -Dtest=GetTodaySalesSummaryUseCaseTest test`
Expected: derleme hatası (`GetTodaySalesSummaryUseCase`/`TodaySalesSummary` henüz yok)

- [ ] **Step 3: DTO ve use case'i implemente et**

`backend/src/main/java/com/vetos/modules/billing/application/dto/TodaySalesSummary.java`:
```java
package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;

public record TodaySalesSummary(BigDecimal totalAmount, int saleCount) {}
```

`backend/src/main/java/com/vetos/modules/billing/application/GetTodaySalesSummaryUseCase.java`:
```java
package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.TodaySalesSummary;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dashboard'daki "Bugunku Satis" KPI karti icin -- GetRevenueSummaryUseCase
 * ile ayni desen ama SADECE bugunun toplamini dondurur ve tum rollere acik
 * (bkz. docs/superpowers/specs/2026-09-14-hizli-satis-design.md S4.5).
 */
@Service
@RequiredArgsConstructor
public class GetTodaySalesSummaryUseCase {

    private static final Set<InvoiceStatus> REVENUE_STATUSES = EnumSet.of(
        InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID
    );

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public TodaySalesSummary execute(UUID tenantId) {
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfTomorrow = startOfToday.plusSeconds(86400);

        List<Invoice> todayInvoices = invoiceRepository.findByTenantId(tenantId).stream()
            .filter(inv -> REVENUE_STATUSES.contains(inv.getStatus()) && inv.getIssuedAt() != null)
            .filter(inv -> !inv.getIssuedAt().isBefore(startOfToday) && inv.getIssuedAt().isBefore(startOfTomorrow))
            .toList();

        BigDecimal total = todayInvoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TodaySalesSummary(total, todayInvoices.size());
    }
}
```

- [ ] **Step 4: Testi çalıştırıp geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -Dtest=GetTodaySalesSummaryUseCaseTest test`
Expected: PASS (2 test)

- [ ] **Step 5: Response DTO'sunu oluştur ve controller'a ekle**

`backend/src/main/java/com/vetos/modules/billing/api/dto/TodaySalesSummaryResponse.java`:
```java
package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.TodaySalesSummary;

import java.math.BigDecimal;

public record TodaySalesSummaryResponse(BigDecimal totalAmount, int saleCount) {
    public static TodaySalesSummaryResponse from(TodaySalesSummary summary) {
        return new TodaySalesSummaryResponse(summary.totalAmount(), summary.saleCount());
    }
}
```

`backend/src/main/java/com/vetos/modules/billing/api/InvoicesController.java` — `private final GetTodaySalesSummaryUseCase getTodaySalesSummaryUseCase;` alanını ekle, `revenueSummary()` metodundan hemen sonra:
```java
    @GetMapping("/today-summary")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public TodaySalesSummaryResponse todaySummary() {
        return TodaySalesSummaryResponse.from(getTodaySalesSummaryUseCase.execute(TenantContext.current()));
    }
```

(Bu metod-seviyesi `@PreAuthorize`, sınıf seviyesindeki `RECEPTIONIST/ADMIN` kısıtlamasını SADECE bu uç nokta için geçersiz kılar — Spring Security metod-seviyesi anotasyonu sınıf seviyesininkinin yerine geçer, birleştirmez.)

- [ ] **Step 6: Derlemeyi doğrula**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: hatasız

- [ ] **Step 7: Commit**

```bash
cd backend && git add src/main/java/com/vetos/modules/billing/application/dto/TodaySalesSummary.java src/main/java/com/vetos/modules/billing/application/GetTodaySalesSummaryUseCase.java src/main/java/com/vetos/modules/billing/api/dto/TodaySalesSummaryResponse.java src/main/java/com/vetos/modules/billing/api/InvoicesController.java src/test/java/com/vetos/modules/billing/application/GetTodaySalesSummaryUseCaseTest.java
git commit -m "feat: GetTodaySalesSummaryUseCase + herkese acik GET /invoices/today-summary"
```

---

## Task 9: Frontend — `billingApi.ts` ve `inventoryApi.ts` genişletmesi

**Files:**
- Modify: `frontend/src/api/billingApi.ts`

**Interfaces:**
- Consumes: `POST /api/v1/invoices/quick-sale` (Task 7), `GET /api/v1/invoices/today-summary` (Task 8)
- Produces: `billingApi.quickSale(...)`, `billingApi.todaySalesSummary()` — Task 10, 11 bunları kullanacak

- [ ] **Step 1: Tipleri ve metotları ekle**

`frontend/src/api/billingApi.ts` — `CashRegisterSession` interface'inden sonra, `export const billingApi` bloğundan önce ekle:
```typescript
export interface QuickSaleLine {
  inventoryItemId?: string;
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface TodaySalesSummary {
  totalAmount: number;
  saleCount: number;
}
```

`export const billingApi = { ... }` bloğunun içine, `cashRegisterHistory` satırından sonra ekle:
```typescript
  quickSale: (payload: { ownerId?: string; lines: QuickSaleLine[]; paymentMethod: PaymentMethod }) =>
    apiClient.postForId('/api/v1/invoices/quick-sale', payload),
  todaySalesSummary: () => apiClient.get<TodaySalesSummary>('/api/v1/invoices/today-summary'),
```

- [ ] **Step 2: TypeScript derlemesini doğrula**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: hatasız (bu dosyada başka hiçbir yer bu yeni alanları henüz kullanmıyor)

- [ ] **Step 3: Commit**

```bash
cd frontend && git add src/api/billingApi.ts
git commit -m "feat: billingApi.quickSale ve todaySalesSummary"
```

---

## Task 10: Frontend — `QuickSaleModal.tsx`

**Files:**
- Create: `frontend/src/pages/finance/QuickSaleModal.tsx`
- Create: `frontend/src/pages/finance/QuickSaleModal.module.css`

**Interfaces:**
- Consumes: `billingApi.quickSale` (Task 9), `inventoryApi.list` (mevcut), `patientApi.searchOwners` (mevcut)
- Produces: `<QuickSaleModal open onClose onCompleted />` — Task 11, 12 bunu mount edecek

- [ ] **Step 1: CSS modülünü oluştur**

`frontend/src/pages/finance/QuickSaleModal.module.css`:
```css
.title {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 18px;
  margin-bottom: 4px;
}
.sub {
  font-size: 12.5px;
  color: var(--color-text-muted);
  margin-bottom: 18px;
}
.anonymousBtn {
  width: 100%;
  height: 40px;
  border: 1.5px dashed var(--color-border-strong, var(--color-border));
  border-radius: 9px;
  background: none;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-text-muted);
  cursor: pointer;
  margin-bottom: 8px;
}
.anonymousBtn:hover {
  border-color: var(--color-brand-500);
  color: var(--color-brand-500);
}
.ownerPicker {
  position: relative;
}
.ownerDropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  box-shadow: var(--shadow-sm);
  max-height: 200px;
  overflow-y: auto;
  z-index: 10;
}
.ownerOption {
  display: flex;
  justify-content: space-between;
  padding: 9px 12px;
  font-size: 13px;
  cursor: pointer;
}
.ownerOption:hover {
  background: var(--color-neutral-100, #f5f3ee);
}
.ownerOptionName {
  font-weight: 600;
}
.ownerOptionPhone {
  color: var(--color-text-muted);
}
.selectedChip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-neutral-100, #f5f3ee);
  border: 1.5px solid var(--color-border);
  border-radius: 9px;
  padding: 0 12px;
  height: 40px;
  font-size: 13px;
  font-weight: 600;
}
.changeBtn {
  background: none;
  border: none;
  padding: 0;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-brand-500);
  cursor: pointer;
}
.sectionLabel {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-brand-500);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  margin: 16px 0 8px;
}
.addLineForm {
  display: grid;
  grid-template-columns: 1.6fr 0.6fr 0.8fr auto;
  gap: 8px;
  align-items: end;
}
.cartRow {
  display: grid;
  grid-template-columns: 2fr 0.6fr 0.9fr 0.9fr auto;
  gap: 8px;
  padding: 7px 0;
  font-size: 12.5px;
  border-bottom: 1px solid var(--color-border);
  align-items: center;
}
.cartRow:last-child {
  border-bottom: none;
}
.emptyNote {
  font-size: 12px;
  color: var(--color-text-muted);
  padding: 10px 0;
}
.removeBtn {
  background: none;
  border: none;
  color: var(--color-text-muted);
  font-size: 15px;
  cursor: pointer;
}
.removeBtn:hover {
  color: var(--color-danger-700);
}
.totalRow {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1.5px solid var(--color-border);
  font-weight: 700;
}
.totalValue {
  font-family: var(--font-heading);
  font-size: 18px;
}
.errorBanner {
  background: var(--color-danger-100);
  color: var(--color-danger-700);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  margin-bottom: 14px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}
```

- [ ] **Step 2: Bileşeni oluştur**

`frontend/src/pages/finance/QuickSaleModal.tsx`:
```tsx
import { useEffect, useState } from 'react';
import { billingApi, PaymentMethod, QuickSaleLine } from '../../api/billingApi';
import { ApiError } from '../../api/client';
import { InventoryItem, inventoryApi } from '../../api/inventoryApi';
import { OwnerSearchResult, patientApi } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './QuickSaleModal.module.css';

const PAYMENT_LABELS: Record<PaymentMethod, string> = {
  CARD: 'Kart',
  CASH: 'Nakit',
  TEXT_TO_PAY: 'Text-to-Pay',
  INSTALLMENT: 'Taksit',
};

interface CartLine extends QuickSaleLine {
  key: string;
}

interface QuickSaleModalProps {
  open: boolean;
  onClose: () => void;
  onCompleted: (invoiceId: string) => void;
}

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function QuickSaleModal({ open, onClose, onCompleted }: QuickSaleModalProps) {
  const [ownerId, setOwnerId] = useState<string | null>(null);
  const [ownerLabel, setOwnerLabel] = useState<string | null>(null);
  const [isAnonymous, setIsAnonymous] = useState(false);
  const [ownerQuery, setOwnerQuery] = useState('');
  const [ownerResults, setOwnerResults] = useState<OwnerSearchResult[]>([]);
  const [ownerSearchOpen, setOwnerSearchOpen] = useState(false);

  const [items, setItems] = useState<InventoryItem[]>([]);
  const [selectedItemId, setSelectedItemId] = useState('');
  const [lineQty, setLineQty] = useState('1');
  const [linePrice, setLinePrice] = useState('');
  const [cart, setCart] = useState<CartLine[]>([]);

  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setOwnerId(null);
      setOwnerLabel(null);
      setIsAnonymous(false);
      setOwnerQuery('');
      setOwnerResults([]);
      setSelectedItemId('');
      setLineQty('1');
      setLinePrice('');
      setCart([]);
      setPaymentMethod('CASH');
      setError(null);
      return;
    }
    inventoryApi.list().then(setItems);
  }, [open]);

  useEffect(() => {
    if (!ownerSearchOpen) return;
    const handle = setTimeout(() => {
      patientApi.searchOwners(ownerQuery).then(setOwnerResults);
    }, 250);
    return () => clearTimeout(handle);
  }, [ownerQuery, ownerSearchOpen]);

  function selectOwner(o: OwnerSearchResult) {
    setOwnerId(o.id);
    setOwnerLabel(o.fullName);
    setOwnerSearchOpen(false);
    setOwnerQuery('');
  }

  function handleItemSelect(id: string) {
    setSelectedItemId(id);
    setLinePrice('');
  }

  function addToCart() {
    const item = items.find((i) => i.id === selectedItemId);
    const qty = Number(lineQty);
    const price = Number(linePrice);
    if (!item || qty <= 0 || price < 0 || linePrice === '') return;
    setCart((prev) => [
      ...prev,
      { key: `${item.id}-${Date.now()}`, inventoryItemId: item.id, description: item.name, quantity: qty, unitPrice: price },
    ]);
    setSelectedItemId('');
    setLineQty('1');
    setLinePrice('');
  }

  function removeFromCart(key: string) {
    setCart((prev) => prev.filter((l) => l.key !== key));
  }

  const total = cart.reduce((sum, l) => sum + l.quantity * l.unitPrice, 0);
  const canComplete = (ownerId !== null || isAnonymous) && cart.length > 0 && !busy;

  async function handleComplete() {
    if (!canComplete) return;
    setBusy(true);
    setError(null);
    try {
      const invoiceId = await billingApi.quickSale({
        ownerId: ownerId ?? undefined,
        lines: cart.map(({ key: _key, ...line }) => line),
        paymentMethod,
      });
      onCompleted(invoiceId);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} width={640}>
      <div className={styles.title}>Hızlı Satış</div>
      <div className={styles.sub}>Kayıtlı bir müşteri seçin veya anonim satış yapın, ürünleri ekleyip ödemeyi alın.</div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <FieldWrap label="Müşteri">
        {isAnonymous ? (
          <div className={styles.selectedChip}>
            <span>Anonim / Günlük Müşteri</span>
            <button type="button" className={styles.changeBtn} onClick={() => setIsAnonymous(false)}>
              Değiştir
            </button>
          </div>
        ) : ownerId ? (
          <div className={styles.selectedChip}>
            <span>{ownerLabel}</span>
            <button
              type="button"
              className={styles.changeBtn}
              onClick={() => {
                setOwnerId(null);
                setOwnerLabel(null);
              }}
            >
              Değiştir
            </button>
          </div>
        ) : (
          <>
            <button type="button" className={styles.anonymousBtn} onClick={() => setIsAnonymous(true)}>
              Anonim / Günlük Müşteri Olarak Devam Et
            </button>
            <div className={styles.ownerPicker}>
              <Input
                placeholder="veya kayıtlı müşteri ara (ad veya telefon)"
                value={ownerQuery}
                onFocus={() => setOwnerSearchOpen(true)}
                onBlur={() => setTimeout(() => setOwnerSearchOpen(false), 150)}
                onChange={(e) => {
                  setOwnerQuery(e.target.value);
                  setOwnerSearchOpen(true);
                }}
              />
              {ownerSearchOpen && ownerResults.length > 0 && (
                <div className={styles.ownerDropdown} onMouseDown={(e) => e.preventDefault()}>
                  {ownerResults.map((o) => (
                    <div key={o.id} className={styles.ownerOption} onClick={() => selectOwner(o)}>
                      <span className={styles.ownerOptionName}>{o.fullName}</span>
                      <span className={styles.ownerOptionPhone}>{o.phone}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </>
        )}
      </FieldWrap>

      <div className={styles.sectionLabel}>Ürün Ekle</div>
      <div className={styles.addLineForm}>
        <FieldWrap label="Ürün">
          <Select value={selectedItemId} onChange={(e) => handleItemSelect(e.target.value)}>
            <option value="">Seçiniz</option>
            {items.map((i) => (
              <option key={i.id} value={i.id}>
                {i.name} ({i.quantityOnHand} adet stokta)
              </option>
            ))}
          </Select>
        </FieldWrap>
        <FieldWrap label="Adet">
          <Input type="number" min={1} value={lineQty} onChange={(e) => setLineQty(e.target.value)} />
        </FieldWrap>
        <FieldWrap label="Birim Fiyat">
          <Input type="number" step="0.01" min={0} value={linePrice} onChange={(e) => setLinePrice(e.target.value)} />
        </FieldWrap>
        <Button type="button" variant="secondary" onClick={addToCart} disabled={!selectedItemId || linePrice === ''}>
          Sepete Ekle
        </Button>
      </div>

      <div className={styles.sectionLabel}>Sepet</div>
      {cart.length === 0 ? (
        <div className={styles.emptyNote}>Henüz ürün eklenmedi</div>
      ) : (
        cart.map((l) => (
          <div key={l.key} className={styles.cartRow}>
            <span>{l.description}</span>
            <span>{l.quantity}x</span>
            <span>{l.unitPrice.toFixed(2)} ₺</span>
            <span>{(l.quantity * l.unitPrice).toFixed(2)} ₺</span>
            <button type="button" className={styles.removeBtn} onClick={() => removeFromCart(l.key)}>
              ×
            </button>
          </div>
        ))
      )}

      <div className={styles.totalRow}>
        <span>Toplam</span>
        <span className={styles.totalValue}>{total.toFixed(2)} ₺</span>
      </div>

      <FieldWrap label="Ödeme Yöntemi">
        <Select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}>
          {Object.entries(PAYMENT_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </FieldWrap>

      <div className={styles.actions}>
        <Button variant="secondary" onClick={onClose} disabled={busy}>
          Vazgeç
        </Button>
        <Button variant="primary" onClick={handleComplete} disabled={!canComplete}>
          {busy ? 'Tamamlanıyor...' : 'Satışı Tamamla'}
        </Button>
      </div>
    </Modal>
  );
}
```

**Not:** `handleItemSelect` fiyatı her zaman boşa çeker (satış fiyatı alanı olmadığı için doldurulacak bir varsayılan yok — kullanıcı kararı: fiyat her zaman elle girilir). Bu, sadece seçim değiştiğinde önceki ürünün fiyatının yanlışlıkla kalmasını önlüyor.

- [ ] **Step 3: TypeScript derlemesini doğrula**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: hatasız

- [ ] **Step 4: Commit**

```bash
cd frontend && git add src/pages/finance/QuickSaleModal.tsx src/pages/finance/QuickSaleModal.module.css
git commit -m "feat: QuickSaleModal bileseni"
```

---

## Task 11: Frontend — giriş noktaları (Finans sayfası + Hızlı Ekle menüsü)

**Files:**
- Modify: `frontend/src/pages/finance/FinancePage.tsx`
- Modify: `frontend/src/pages/dashboard/QuickAddMenu.tsx`
- Modify: `frontend/src/pages/dashboard/DashboardPage.tsx`

**Interfaces:**
- Consumes: `<QuickSaleModal />` (Task 10)

- [ ] **Step 1: `FinancePage.tsx`'e Hızlı Satış butonunu ve modalı ekle**

`frontend/src/pages/finance/FinancePage.tsx` — importlara ekle:
```typescript
import { QuickSaleModal } from './QuickSaleModal';
```

`newInvoiceOpen` state'inin yanına yeni bir state ekle:
```typescript
  const [newInvoiceOpen, setNewInvoiceOpen] = useState(false);
  const [quickSaleOpen, setQuickSaleOpen] = useState(false);
```

`topbar` içindeki mevcut "Yeni Fatura" butonunun yanına ekle:
```tsx
        {tab === 'invoices' && (
          <div className={styles.actions}>
            <Button variant="secondary" onClick={() => setQuickSaleOpen(true)}>
              Hızlı Satış
            </Button>
            <Button variant="primary" onClick={() => setNewInvoiceOpen(true)}>
              Yeni Fatura
            </Button>
          </div>
        )}
```

(`styles.actions` yoksa `FinancePage.module.css`'e ekle: `.actions { display: flex; gap: 10px; }` — mevcut dosyayı kontrol et, muhtemelen zaten benzer bir class vardır, yoksa bu şekilde ekle.)

`NewInvoiceModal`'ın hemen altına modalı mount et:
```tsx
      <QuickSaleModal
        open={quickSaleOpen}
        onClose={() => setQuickSaleOpen(false)}
        onCompleted={(invoiceId) => {
          setQuickSaleOpen(false);
          loadInvoices();
          setSelectedInvoiceId(invoiceId);
        }}
      />
```

- [ ] **Step 2: `QuickAddMenu.tsx`'i güncelle — "Yeni satış" seçeneğini geri ekle**

`frontend/src/pages/dashboard/QuickAddMenu.tsx` — tam dosya (yeni `onQuickSale` prop'u ekleniyor, yorum satırı kaldırılıyor, yeni bir ikon ekleniyor):
```tsx
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './QuickAddMenu.module.css';

interface QuickAddOption {
  label: string;
  icon: JSX.Element;
  onSelect: () => void;
}

const PLUS_ICON = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round">
    <path d="M12 5v14M5 12h14" />
  </svg>
);
const CHEVRON_ICON = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round">
    <path d="M6 9l6 6 6-6" />
  </svg>
);

const ICON_APPT = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="5" width="18" height="16" rx="2" />
    <path d="M8 3v4M16 3v4M3 10h18" />
  </svg>
);
const ICON_PATIENT = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <circle cx="9" cy="8" r="3.5" />
    <path d="M2.5 20v-1a6.5 6.5 0 0 1 13 0v1" />
  </svg>
);
const ICON_SALE = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 3h2l2.4 12.4a2 2 0 0 0 2 1.6h7.2a2 2 0 0 0 2-1.6L21 8H6" />
    <circle cx="9" cy="20" r="1" />
    <circle cx="17" cy="20" r="1" />
  </svg>
);

interface QuickAddMenuProps {
  onQuickSale: () => void;
}

/**
 * Tasarım sistemi kuralı korunuyor: sayfada tek bir primary buton.
 * Kolayvet'teki 5-6 ayrı "Hızlı X" butonu yerine, tek buton + açılır menü.
 */
export function QuickAddMenu({ onQuickSale }: QuickAddMenuProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const options: QuickAddOption[] = [
    { label: 'Yeni randevu', icon: ICON_APPT, onSelect: () => navigate('/randevu') },
    { label: 'Yeni hasta', icon: ICON_PATIENT, onSelect: () => navigate('/hastalar') },
    { label: 'Yeni satış', icon: ICON_SALE, onSelect: onQuickSale },
  ];

  return (
    <div className={styles.wrap} ref={ref}>
      <button className={styles.trigger} onClick={() => setOpen((v) => !v)}>
        {PLUS_ICON}
        Hızlı ekle
        <span className={`${styles.chevron} ${open ? styles.chevronOpen : ''}`}>{CHEVRON_ICON}</span>
      </button>
      {open && (
        <div className={styles.menu}>
          {options.map((opt) => (
            <div
              key={opt.label}
              className={styles.menuItem}
              onClick={() => {
                opt.onSelect();
                setOpen(false);
              }}
            >
              {opt.icon}
              {opt.label}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: `DashboardPage.tsx`'i güncelle — modalı mount et ve `QuickAddMenu`'ye prop geç**

`frontend/src/pages/dashboard/DashboardPage.tsx` — importlara ekle:
```typescript
import { QuickSaleModal } from '../finance/QuickSaleModal';
```

`loading` state'inin yanına ekle:
```typescript
  const [quickSaleOpen, setQuickSaleOpen] = useState(false);
```

`<QuickAddMenu />` kullanımını şuna değiştir:
```tsx
            <QuickAddMenu onQuickSale={() => setQuickSaleOpen(true)} />
```

`</AppShell>` kapanışından hemen önce (return bloğunun sonunda) modalı mount et:
```tsx
      <QuickSaleModal
        open={quickSaleOpen}
        onClose={() => setQuickSaleOpen(false)}
        onCompleted={() => {
          setQuickSaleOpen(false);
          load();
        }}
      />
    </AppShell>
```

- [ ] **Step 4: Frontend'in derlendiğini doğrula**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: hatasız

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/pages/finance/FinancePage.tsx src/pages/dashboard/QuickAddMenu.tsx src/pages/dashboard/DashboardPage.tsx
git commit -m "feat: Hizli Satis giris noktalari (Finans sayfasi + Hizli ekle menusu)"
```

---

## Task 12: Frontend — "Bugünkü Satış" KPI kartı

**Files:**
- Modify: `frontend/src/pages/dashboard/DashboardPage.tsx`

**Interfaces:**
- Consumes: `billingApi.todaySalesSummary` (Task 9)

- [ ] **Step 1: State ve veri çekmeyi ekle**

`frontend/src/pages/dashboard/DashboardPage.tsx` — importlara ekle:
```typescript
import { billingApi, TodaySalesSummary } from '../../api/billingApi';
```

`appointments`/`loading` state'lerinin yanına ekle:
```typescript
  const [todaySales, setTodaySales] = useState<TodaySalesSummary | null>(null);
```

Mevcut `load()` fonksiyonunu değiştir (randevularla birlikte satış özetini de çeksin):
```typescript
  function load() {
    setLoading(true);
    appointmentApi
      .weeklyCalendar(todayIso)
      .then((list) => setAppointments(list.filter((a) => a.scheduledStart.slice(0, 10) === todayIso)))
      .finally(() => setLoading(false));
    billingApi.todaySalesSummary().then(setTodaySales).catch(() => setTodaySales(null));
  }
```

- [ ] **Step 2: KPI kartını ekle**

`frontend/src/pages/dashboard/DashboardPage.tsx` — mevcut `kpiStrip` içindeki 4 karttan sonra (`"No-show riski yüksek"` kartından sonra) ekle:
```tsx
            <div className={styles.kpiCard}>
              <div className={styles.kpiLabel}>Bugünkü satış</div>
              <div className={styles.kpiValue}>
                {todaySales ? `${todaySales.totalAmount.toFixed(0)} ₺` : '—'}
              </div>
            </div>
```

- [ ] **Step 3: Frontend'in derlendiğini doğrula**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: hatasız

- [ ] **Step 4: Backend'i (Task 1-8 değişiklikleriyle) yeniden başlat, frontend'i tarayıcıda aç, Anasayfa'da "Bugünkü satış" kartının göründüğünü doğrula**

Backend'i yeniden başlat (`./run-local.sh`), frontend zaten Vite ile hot-reload olur. `/panel` sayfasına git, KPI şeridinde 5. kart olarak "Bugünkü satış" göründüğünü doğrula (değer `0 ₺` olabilir, henüz satış yoksa).

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/pages/dashboard/DashboardPage.tsx
git commit -m "feat: Anasayfa'ya Bugunku Satis KPI karti"
```

---

## Task 13: Uçtan uca doğrulama (e2e test)

**Files:**
- Create: `frontend/e2e/quick-sale.spec.ts`

**Interfaces:**
- Consumes: Task 1-12'nin tamamı (backend + frontend)

- [ ] **Step 1: e2e testini yaz**

`frontend/e2e/quick-sale.spec.ts`:
```typescript
import { test, expect, Page } from '@playwright/test';

function field(page: Page, label: string) {
  return page.locator(`xpath=//label[normalize-space(.)="${label}"]/following-sibling::*[1]`);
}

test('Hızlı Satış: anonim müşteriyle ürün satışı ve günlük ciroya yansıması', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());

  const runId = Date.now();
  const clinicName = `E2E Klinik ${runId}`;
  const adminEmail = `e2e-admin-${runId}@example.com`;
  const password = 'password123';
  const productName = `E2E Ürün ${runId}`;

  await test.step('Klinik oluştur ve giriş yap', async () => {
    await page.goto('/platform-admin/login');
    await field(page, 'E-posta').fill('admin@myvet.local');
    await field(page, 'Şifre').fill('change-me-local-dev-only');
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/tenants/);

    await page.getByRole('button', { name: '+ Yeni Klinik' }).click();
    await field(page, 'Klinik adı').fill(clinicName);
    await field(page, 'Vergi numarası').fill('1234567890');
    await field(page, 'Şube adı').fill('Merkez');
    await field(page, 'Adres').fill('Test Cad. No:1');
    await field(page, 'Şehir').fill('İstanbul');
    await field(page, 'Yetkili adı soyadı').fill('E2E Admin');
    await field(page, 'Yetkili e-postası').fill(adminEmail);
    await field(page, 'Geçici şifre').fill(password);
    await page.getByRole('button', { name: 'Klinik Oluştur' }).click();
    await expect(page).toHaveURL(/\/platform-admin\/tenants\//);

    await page.goto('/login');
    await field(page, 'E-posta').fill(adminEmail);
    await field(page, 'Şifre').fill(password);
    await page.getByRole('button', { name: 'Giriş yap' }).click();
    await expect(page).toHaveURL(/\/panel/);
  });

  await test.step('Stok kalemi oluştur', async () => {
    await page.goto('/stok');
    await page.getByRole('button', { name: 'Yeni Ürün' }).click();
    await field(page, 'Ürün adı').fill(productName);
    await field(page, 'Başlangıç miktarı').fill('20');
    await field(page, 'Kritik stok eşiği').fill('2');
    await page.getByRole('button', { name: 'Kaydet' }).click();
    await expect(page.getByText(productName).first()).toBeVisible();
  });

  await test.step('Dashboard\'dan Hızlı Satış aç, anonim müşteriyle ürün sat', async () => {
    await page.goto('/panel');
    await page.getByRole('button', { name: 'Hızlı ekle' }).click();
    await page.getByText('Yeni satış').click();

    await page.getByRole('button', { name: 'Anonim / Günlük Müşteri Olarak Devam Et' }).click();

    await field(page, 'Ürün').selectOption({ label: `${productName} (20 adet stokta)` });
    await field(page, 'Adet').fill('2');
    await field(page, 'Birim Fiyat').fill('150');
    await page.getByRole('button', { name: 'Sepete Ekle' }).click();

    await expect(page.getByText('300.00 ₺').first()).toBeVisible();

    await page.getByRole('button', { name: 'Satışı Tamamla' }).click();
    await expect(page.getByRole('button', { name: 'Satışı Tamamla' })).not.toBeVisible();
  });

  await test.step('Bugünkü satış KPI\'ının arttığını doğrula', async () => {
    await page.reload();
    await expect(page.getByText('Bugünkü satış')).toBeVisible();
    await expect(page.getByText('300 ₺')).toBeVisible();
  });

  await test.step('Stok düşümünü doğrula (20 - 2 = 18)', async () => {
    await page.goto('/stok');
    const nameCell = page.getByText(productName, { exact: true });
    const row = nameCell.locator('xpath=..');
    await expect(row).toContainText('18');
  });
});
```

- [ ] **Step 2: Backend ve frontend'in ayakta olduğundan emin ol, testi çalıştır**

Run: `cd frontend && npx playwright test e2e/quick-sale.spec.ts --workers=1 --reporter=line`
Expected: PASS. Başarısız olursa hata mesajındaki gerçek seçici farkını düzelt ve tekrar çalıştır.

- [ ] **Step 3: Golden path ve diğer mevcut e2e testlerin bozulmadığını doğrula**

Run: `cd frontend && npx playwright test e2e/ --workers=1 --reporter=line`
Expected: tüm testler PASS

- [ ] **Step 4: Commit**

```bash
cd frontend && git add e2e/quick-sale.spec.ts
git commit -m "test: Hizli Satis icin e2e dogrulama"
```

---

## Son Kontrol

Tüm task'lar bittikten sonra:

```bash
cd backend && ./mvnw -q test
```
Expected: tüm backend testleri (mevcut 188 + bu planda eklenen ~15 yeni test) PASS.

```bash
cd frontend && npx tsc -b --noEmit && npx playwright test e2e/ --workers=1 --reporter=line
```
Expected: derleme temiz, tüm e2e testler PASS.
