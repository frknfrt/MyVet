# TARBİL Stok Düşüm Senkronu (Faz 1 / Plan A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TARBİL'e giden stok kullanımları (muayenede kullanılan TARBİL'e bağlı ilaç, stoktan aşı, reçetede "kliniğin stoğundan ver") Vetly stoğundan ancak TARBİL'de düşüm onaylanınca düşsün; muayenesiz reçete yazılabilsin.

**Architecture:** inventory modülünde `pending_stock_deduction` (bekleyen düşüm) tablosu; kaynak olaylar (muayene kapanışı, aşı kaydı, reçete) bekleyen satır açar, integration modülü TARBİL aktarımı onaylanınca inventory'deki `TarbilStockDeductionPort.settle(...)` ile düşer. "Yalnız Vetly'den düş" inventory'de yapılır ve olayla integration'daki aktarımı reddeder. Eklentinin reçete formunu doldurması **Plan B**'dedir (canlı inceleme sonrası); bu planda eklenti reçete aktarımlarını gösterir ve yalnız "TARBİL'de yaptım" (MANUAL) ile onaylatır.

**Tech Stack:** Spring Boot 3 + Spring Modulith + JPA/Hibernate (@TenantId) + Flyway (Postgres); React + Vite (frontend); Chrome MV3 eklentisi (TypeScript, vitest).

**Spec:** `docs/superpowers/specs/2026-10-09-tarbil-stok-dusum-senkronu-design.md`

## Global Constraints

- Doğrudan yerel `main` üzerinde çalışılır; branch yok; push yalnız kullanıcı isteyince.
- Yeni migration numarası **V78** (son: V77). Migration'ı yeniden adlandırırsan `./mvnw clean` çalıştır.
- Entegrasyon testleri kullanıcının :8080 backend'iyle aynı dev DB'yi (localhost:5433 `myvet`) migrate eder; V78 eklemeli olmalı (kolon silme/yeniden adlandırma yok). Kullanıcının :8080 / :5173 süreçlerine dokunma.
- Modül yönü: `inventory` → `encounter::domain.event` dinler; `integration` → `inventory` domain portlarını çağırır ve `encounter`/`inventory` olaylarını dinler. `inventory` ASLA `integration`'a, `encounter` ASLA `inventory`/`integration`'a bağımlı olmaz. `ApplicationModulesTest` yeşil kalmalı.
- Yeni kiracılı tablolar `@org.hibernate.annotations.TenantId` + `updatable=false` kullanır.
- `DomainException` HTTP durumu sınıf adı sonekinden: `...NotFoundException`→404, `...ConflictException`→409, diğer→422.
- "TARBİL'e bağlı kalem" = `inventory_items.tarbil_product_name IS NOT NULL`.
- Aşı istisnası (Ruling, spec §5.2 ile tutarlı): stoktan seçilen her aşı TARBİL'e gittiği için **bağlı olsun olmasın** bekleyen düşüm açar; muayene kalemlerinde yalnız bağlı olanlar bekler.
- Eski modelle düşülmüş aşı kayıtlarına (OUT hareketi var, bekleyen satır yok) dokunulmaz; iptallerinde eski davranış (1 IN) sürer.
- Eklenti resmi düğmelere (Kaydet/Onayla/Reçeteyi Onayla) asla basmaz; TARBİL ViewState okunmaz/saklanmaz.
- UI metinleri Türkçe.

## Review Focus

1. Aynı aktarım iki kez onaylanırsa (eklenti AUTO + hekim MANUAL) stok iki kez düşmemeli → Task 5 `secondConfirmationDoesNotDeductAgain`.
2. "Yalnız Vetly'den düş" sonrası hekim yine TARBİL'de onaylarsa çift düşüm olmamalı → Task 6 `tarbilConfirmationAfterVetlyOnlyDoesNotDeductAgain`.
3. Başka kiracının muayenesi/reçetesi için düşüm kuyruğa alınamamalı / görülememeli → Task 7 `foreignEncounterDeductionIsNotFound`.
4. Muayenede TARBİL'e bağlı kalem yokken "TARBİL'den stok düş" → 409, boş aktarım açılmamalı → Task 7 `queueWithoutPendingLinesConflicts`.
5. Bildirilen seri no boşluk/küçük harf farkıyla gelirse yine eşleşmeli → Task 2 `matchesSerialIgnoringCaseAndSpaces`.

---

## Dosya haritası

**Backend — inventory**
- Create `inventory/domain/PendingStockDeduction.java`, `PendingDeductionStatus.java`, `DeductionSettlement.java`, `PendingStockDeductionRepository.java`, `TarbilStockDeductionPort.java`, `PendingDeductionLine.java`, `ReportedDeductionLine.java`, `DeductionSettlementResult.java`
- Create `inventory/domain/event/PendingDeductionSettledVetlyOnlyEvent.java` (+ `package-info.java` NamedInterface `domain.event`)
- Create `inventory/infrastructure/persistence/PendingStockDeductionJpaRepository.java`, `PendingStockDeductionRepositoryAdapter.java`
- Create `inventory/application/PendingDeductionService.java` (açma/düşme/iptal kuralları), `ListPendingDeductionsUseCase.java`, `SettleVetlyOnlyUseCase.java`
- Create `inventory/infrastructure/TarbilStockDeductionAdapter.java`, `inventory/infrastructure/event/PrescriptionStockEventListener.java`
- Create `inventory/api/PendingDeductionsController.java`, `inventory/api/dto/PendingDeductionResponse.java`
- Modify `StockReferenceType.java` (+`PRESCRIPTION`), `AutoDeductStockForEncounterUseCase.java`, `ApplyVaccinationStockUseCase.java`, `InventoryItemResponse.java`/`InventoryItemDetail` (+`tarbilProductName`)

**Backend — encounter**
- Modify `Prescription.java`, `PrescriptionItem.java`, `IssuePrescriptionUseCase.java`, `IssuePrescriptionCommand.java`, `PrescriptionItemInput.java`, `IssuePrescriptionRequest.java`, `PrescriptionDetail.java`, `PrescriptionResponse.java`, `PrescriptionsController.java`, `GetPrescriptionUseCase`/`ListPrescriptionsByPatientUseCase` eşlemesi
- Create `encounter/domain/event/PrescriptionIssuedEvent.java`, `PrescriptionCancelledEvent.java`, `encounter/application/CancelPrescriptionUseCase.java`, `encounter/domain/exception/PrescriptionTarbilDiseaseRequiredException.java`, `PrescriptionEncounterMismatchException.java`, `PrescriptionNotCancellableConflictException.java`

**Backend — integration/tarbil**
- Modify `TarbilSubmission.java` (+`sourceKind`, `tarbilDiseaseId`, `settlementNote`), `MarkSubmittedUseCase.java`, `MarkSubmittedRequest.java`, `TarbilExtensionController.java`, `QueueTarbilSyncUseCase.java`, `TarbilSubmissionAssembler.java`, `TarbilSubmissionView`/`TarbilSubmissionResponse`, `TarbilController.java`
- Create `TarbilSourceKind.java`, `GetEncounterTarbilDeductionUseCase.java`, `QueueEncounterDeductionUseCase.java`, `infrastructure/event/PrescriptionTarbilEventListener.java`, `infrastructure/event/VetlyOnlyDeductionListener.java`, `domain/exception/TarbilNothingToDeductConflictException.java`

**DB:** `V78__pending_stock_deduction.sql`

**Frontend:** `api/tarbilApi.ts`, `api/inventoryApi.ts`, `api/clinicalApi.ts`, `pages/encounter/TarbilDeductionCard.tsx`(+css), `pages/inventory/PendingDeductionsPanel.tsx`(+css), `pages/encounter/PrescriptionCard.tsx`, `pages/encounter/StockGivePicker.tsx`, `pages/encounter/TarbilDiseaseSelect.tsx`, `pages/patients/PatientDetailPage.tsx`, `pages/encounter/EncounterPage.tsx`, `pages/inventory/InventoryPage.tsx`

**Eklenti:** `shared/types.ts`, `sidepanel/SubmissionCard.tsx`, `background/vetlyApi.ts`, `shared/messages.ts`, `background/router.ts`

---

### Task 1: V78 + bekleyen düşüm varlığı

**Files:**
- Create: `backend/src/main/resources/db/migration/V78__pending_stock_deduction.sql`
- Create: `backend/src/main/java/com/vetos/modules/inventory/domain/{PendingStockDeduction,PendingDeductionStatus,DeductionSettlement,PendingStockDeductionRepository}.java`
- Create: `backend/src/main/java/com/vetos/modules/inventory/infrastructure/persistence/{PendingStockDeductionJpaRepository,PendingStockDeductionRepositoryAdapter}.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/domain/StockReferenceType.java`
- Test: `backend/src/test/java/com/vetos/modules/inventory/domain/PendingStockDeductionTest.java`

**Interfaces:**
- Produces: `PendingStockDeduction.open(UUID tenantId, UUID itemId, int qty, StockReferenceType type, UUID refId)`, `boolean settle(int qty, DeductionSettlement via, UUID staffId, Instant now)`, `boolean cancel()`, getters; `PendingStockDeductionRepository { save, findByReference(StockReferenceType, UUID): List, findAllPending(): List }`; `StockReferenceType.PRESCRIPTION`.

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.inventory.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class PendingStockDeductionTest {

    private final UUID staff = UUID.randomUUID();
    private PendingStockDeduction pending() {
        return PendingStockDeduction.open(UUID.randomUUID(), UUID.randomUUID(), 2, StockReferenceType.ENCOUNTER, UUID.randomUUID());
    }

    @Test
    void settlesOnceWithTheGivenQuantity() {
        PendingStockDeduction p = pending();
        assertThat(p.settle(3, DeductionSettlement.TARBIL, staff, Instant.EPOCH)).isTrue();
        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
        assertThat(p.getSettledQuantity()).isEqualTo(3);
        assertThat(p.getSettledVia()).isEqualTo(DeductionSettlement.TARBIL);
        assertThat(p.settle(1, DeductionSettlement.VETLY_ONLY, staff, Instant.EPOCH)).isFalse();
        assertThat(p.getSettledQuantity()).isEqualTo(3);
    }

    @Test
    void cancelsOnlyWhilePending() {
        PendingStockDeduction p = pending();
        assertThat(p.cancel()).isTrue();
        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.CANCELLED);
        assertThat(p.settle(2, DeductionSettlement.TARBIL, staff, Instant.EPOCH)).isFalse();

        PendingStockDeduction d = pending();
        d.settle(2, DeductionSettlement.TARBIL, staff, Instant.EPOCH);
        assertThat(d.cancel()).isFalse();
        assertThat(d.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=PendingStockDeductionTest`
Expected: FAIL — derleme hatası, `PendingStockDeduction` yok.

- [ ] **Step 3: Write migration and implementation**

`V78__pending_stock_deduction.sql`:

```sql
-- TARBIL stok dusum senkronu (spec 2026-10-09): TARBIL'de dusum onaylanana kadar Vetly stogu dusmez.
CREATE TABLE pending_stock_deduction (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    inventory_item_id  UUID NOT NULL REFERENCES inventory_items (id),
    quantity           INT  NOT NULL CHECK (quantity > 0),
    reference_type     TEXT NOT NULL,
    reference_id       UUID NOT NULL,
    status             TEXT NOT NULL,
    settled_via        TEXT,
    settled_quantity   INT,
    settled_at         TIMESTAMPTZ,
    settled_by_staff_id UUID,
    created_at         TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_pending_stock_deduction_ref ON pending_stock_deduction (reference_type, reference_id, inventory_item_id);
CREATE INDEX idx_pending_stock_deduction_tenant_status ON pending_stock_deduction (tenant_id, status);

ALTER TABLE prescriptions ALTER COLUMN encounter_id DROP NOT NULL;
ALTER TABLE prescriptions ADD COLUMN tarbil_disease_id UUID REFERENCES tarbil_disease (id);
ALTER TABLE prescription_items ADD COLUMN inventory_item_id UUID REFERENCES inventory_items (id);
ALTER TABLE prescription_items ADD COLUMN stock_quantity INT CHECK (stock_quantity IS NULL OR stock_quantity > 0);

ALTER TABLE tarbil_submission ADD COLUMN source_kind TEXT;
UPDATE tarbil_submission SET source_kind = document_type WHERE document_type IN ('VACCINATION', 'STOCK_RECEIPT');
UPDATE tarbil_submission SET source_kind = 'PRESCRIPTION' WHERE document_type = 'PRESCRIPTION';
ALTER TABLE tarbil_submission ADD COLUMN tarbil_disease_id UUID REFERENCES tarbil_disease (id);
ALTER TABLE tarbil_submission ADD COLUMN settlement_note TEXT;
```

`StockReferenceType.java`: `public enum StockReferenceType { ENCOUNTER, PURCHASE_ORDER, MANUAL, TARBIL_SYNC, VACCINATION, PRESCRIPTION }`

`PendingDeductionStatus.java`: `public enum PendingDeductionStatus { PENDING, DEDUCTED, CANCELLED }`
`DeductionSettlement.java`: `public enum DeductionSettlement { TARBIL, VETLY_ONLY }`

`PendingStockDeduction.java`:

```java
package com.vetos.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** TARBIL'de dusum onaylanana kadar bekleyen stok dusumu (spec 2026-10-09 S4). */
@Entity
@Table(name = "pending_stock_deduction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingStockDeduction {

    @Id
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private StockReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PendingDeductionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "settled_via")
    private DeductionSettlement settledVia;

    @Column(name = "settled_quantity")
    private Integer settledQuantity;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settled_by_staff_id")
    private UUID settledByStaffId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PendingStockDeduction open(UUID tenantId, UUID inventoryItemId, int quantity, StockReferenceType type, UUID referenceId) {
        PendingStockDeduction p = new PendingStockDeduction();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.inventoryItemId = inventoryItemId;
        p.quantity = quantity;
        p.referenceType = type;
        p.referenceId = referenceId;
        p.status = PendingDeductionStatus.PENDING;
        p.createdAt = Instant.now();
        return p;
    }

    /** @return false ise zaten dusulmus/iptal (idempotent). */
    public boolean settle(int settledQuantity, DeductionSettlement via, UUID staffId, Instant now) {
        if (status != PendingDeductionStatus.PENDING) {
            return false;
        }
        this.status = PendingDeductionStatus.DEDUCTED;
        this.settledQuantity = settledQuantity;
        this.settledVia = via;
        this.settledByStaffId = staffId;
        this.settledAt = now;
        return true;
    }

    public boolean cancel() {
        if (status != PendingDeductionStatus.PENDING) {
            return false;
        }
        this.status = PendingDeductionStatus.CANCELLED;
        return true;
    }
}
```

`PendingStockDeductionRepository.java`:

```java
package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.UUID;

public interface PendingStockDeductionRepository {
    PendingStockDeduction save(PendingStockDeduction deduction);
    List<PendingStockDeduction> findByReference(StockReferenceType type, UUID referenceId);
    /** Kiraci filtresi @TenantId ile gelir. */
    List<PendingStockDeduction> findAllPending();
}
```

`PendingStockDeductionJpaRepository.java`:

```java
package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.PendingStockDeduction;
import com.vetos.modules.inventory.domain.StockReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface PendingStockDeductionJpaRepository extends JpaRepository<PendingStockDeduction, UUID> {
    List<PendingStockDeduction> findByReferenceTypeAndReferenceIdOrderByCreatedAt(StockReferenceType type, UUID referenceId);
    List<PendingStockDeduction> findByStatusOrderByCreatedAt(PendingDeductionStatus status);
}
```

`PendingStockDeductionRepositoryAdapter.java`:

```java
package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PendingStockDeductionRepositoryAdapter implements PendingStockDeductionRepository {
    private final PendingStockDeductionJpaRepository jpa;

    @Override public PendingStockDeduction save(PendingStockDeduction d) { return jpa.save(d); }
    @Override public List<PendingStockDeduction> findByReference(StockReferenceType type, UUID referenceId) {
        return jpa.findByReferenceTypeAndReferenceIdOrderByCreatedAt(type, referenceId);
    }
    @Override public List<PendingStockDeduction> findAllPending() { return jpa.findByStatusOrderByCreatedAt(PendingDeductionStatus.PENDING); }
}
```

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -q test -Dtest='PendingStockDeductionTest,ApplicationModulesTest'`
Expected: PASS (Docker açık olmalı; ApplicationModulesTest V78'i de migrate eder).

- [ ] **Step 5: Commit**

```bash
git add backend/src && git commit -m "feat(stok): bekleyen TARBIL dusumu tablosu ve varligi (V78)"
```

---

### Task 2: Düşüm servisi ve `TarbilStockDeductionPort`

**Files:**
- Create: `inventory/domain/{TarbilStockDeductionPort,PendingDeductionLine,ReportedDeductionLine,DeductionSettlementResult}.java`
- Create: `inventory/application/PendingDeductionService.java`
- Create: `inventory/infrastructure/TarbilStockDeductionAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/inventory/application/PendingDeductionServiceTest.java`

**Interfaces:**
- Consumes: Task 1 varlık/depo; `RecordStockMovementUseCase.execute(itemId, OUT, qty, refType, refId)`; `InventoryItemRepository.findById`.
- Produces:
  - `record PendingDeductionLine(UUID id, UUID inventoryItemId, String itemName, String tarbilProductName, String lotNumber, int quantity, PendingDeductionStatus status, Integer settledQuantity)`
  - `record ReportedDeductionLine(String productName, String serialNumber, int quantity)`
  - `record DeductionSettlementResult(int settledCount, List<ReportedDeductionLine> unmatched)`
  - `interface TarbilStockDeductionPort { List<PendingDeductionLine> listForReference(StockReferenceType, UUID); DeductionSettlementResult settleFromTarbil(StockReferenceType, UUID, List<ReportedDeductionLine> reportedOrNull, UUID staffId); }`
  - `PendingDeductionService { void open(UUID itemId, int qty, StockReferenceType, UUID refId); List<PendingDeductionLine> lines(StockReferenceType, UUID); DeductionSettlementResult settle(StockReferenceType, UUID, List<ReportedDeductionLine> reportedOrNull, DeductionSettlement via, UUID staffId); boolean cancel(StockReferenceType, UUID) /* true: en az bir PENDING iptal edildi */; boolean hasDeducted(StockReferenceType, UUID); }`

Eşleştirme kuralı (spec §5.3): `reported == null` → tüm PENDING satırlar planlanan adetle; değilse her bildirilen satır, PENDING satırların kalemi ile **seri no** (`lotNumber`, boşluksuz + `Locale tr` büyük harf) üzerinden eşleşir; eşleşen PENDING satır bildirilen adetle düşer; eşleşmeyen bildirilen satır `unmatched`'e; bildirilmeyen PENDING satır PENDING kalır. Bir PENDING satır en fazla bir bildirilen satırla eşleşir.

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingDeductionServiceTest {

    @Mock private PendingStockDeductionRepository repository;
    @Mock private InventoryItemRepository items;
    @Mock private RecordStockMovementUseCase movements;

    private final UUID tenant = UUID.randomUUID();
    private final UUID ref = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private final List<PendingStockDeduction> rows = new ArrayList<>();
    private PendingDeductionService service;

    private UUID item(String name, String lot) {
        InventoryItem i = InventoryItem.create(tenant, UUID.randomUUID(), name, "İlaç", null, 10, 0, LocalDate.of(2027, 1, 1), lot, BigDecimal.ONE);
        ReflectionTestUtils.setField(i, "id", UUID.randomUUID()); // id @GeneratedValue
        lenient().when(items.findById(i.getId())).thenReturn(Optional.of(i));
        return i.getId();
    }

    private PendingStockDeduction pending(UUID itemId, int qty) {
        PendingStockDeduction p = PendingStockDeduction.open(tenant, itemId, qty, StockReferenceType.ENCOUNTER, ref);
        rows.add(p);
        return p;
    }

    @BeforeEach
    void setUp() {
        service = new PendingDeductionService(repository, items, movements);
        lenient().when(repository.findByReference(StockReferenceType.ENCOUNTER, ref)).thenReturn(rows);
        lenient().when(repository.save(any())).thenAnswer(a -> a.getArgument(0));
    }

    @Test
    void settlesAllPlannedQuantitiesWhenNothingIsReported() {
        UUID a = item("Synulox", "S1");
        PendingStockDeduction p = pending(a, 2);

        DeductionSettlementResult r = service.settle(StockReferenceType.ENCOUNTER, ref, null, DeductionSettlement.TARBIL, staff);

        assertThat(r.settledCount()).isEqualTo(1);
        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
        verify(movements).execute(a, StockMovementType.OUT, 2, StockReferenceType.ENCOUNTER, ref);
    }

    @Test
    void deductsReportedQuantityAndKeepsUnreportedLinesPending() {
        UUID a = item("Synulox", "S1");
        UUID b = item("Metacam", "M7");
        PendingStockDeduction pa = pending(a, 2);
        PendingStockDeduction pb = pending(b, 1);

        DeductionSettlementResult r = service.settle(StockReferenceType.ENCOUNTER, ref,
            List.of(new ReportedDeductionLine("Synulox", "S1", 3), new ReportedDeductionLine("Baytril", "B9", 1)),
            DeductionSettlement.TARBIL, staff);

        assertThat(pa.getSettledQuantity()).isEqualTo(3);
        assertThat(pb.getStatus()).isEqualTo(PendingDeductionStatus.PENDING);
        assertThat(r.unmatched()).containsExactly(new ReportedDeductionLine("Baytril", "B9", 1));
        verify(movements).execute(a, StockMovementType.OUT, 3, StockReferenceType.ENCOUNTER, ref);
        verify(movements, never()).execute(eq(b), any(), anyInt(), any(), any());
    }

    @Test
    void matchesSerialIgnoringCaseAndSpaces() {
        UUID a = item("Synulox", "ab 12");
        PendingStockDeduction p = pending(a, 1);

        service.settle(StockReferenceType.ENCOUNTER, ref, List.of(new ReportedDeductionLine("Synulox", " AB12 ", 1)), DeductionSettlement.TARBIL, staff);

        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
    }

    @Test
    void doesNotDeductAlreadySettledLinesAgain() {
        UUID a = item("Synulox", "S1");
        PendingStockDeduction p = pending(a, 2);
        p.settle(2, DeductionSettlement.VETLY_ONLY, staff, java.time.Instant.EPOCH);

        DeductionSettlementResult r = service.settle(StockReferenceType.ENCOUNTER, ref, null, DeductionSettlement.TARBIL, staff);

        assertThat(r.settledCount()).isZero();
        verifyNoInteractions(movements);
    }

    @Test
    void cancelLeavesDeductedLinesAlone() {
        UUID a = item("Synulox", "S1");
        UUID b = item("Metacam", "M7");
        PendingStockDeduction pa = pending(a, 1);
        PendingStockDeduction pb = pending(b, 1);
        pb.settle(1, DeductionSettlement.TARBIL, staff, java.time.Instant.EPOCH);

        assertThat(service.cancel(StockReferenceType.ENCOUNTER, ref)).isTrue();
        assertThat(pa.getStatus()).isEqualTo(PendingDeductionStatus.CANCELLED);
        assertThat(pb.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
        assertThat(service.hasDeducted(StockReferenceType.ENCOUNTER, ref)).isTrue();
    }

    @Test
    void openIsIdempotentPerItem() {
        UUID a = item("Synulox", "S1");
        pending(a, 2);

        service.open(a, 2, StockReferenceType.ENCOUNTER, ref);

        verify(repository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=PendingDeductionServiceTest`
Expected: FAIL — `PendingDeductionService`/`ReportedDeductionLine` yok.

- [ ] **Step 3: Write implementation**

`PendingDeductionLine.java`, `ReportedDeductionLine.java`, `DeductionSettlementResult.java` (paket `inventory.domain`):

```java
public record PendingDeductionLine(UUID id, UUID inventoryItemId, String itemName, String tarbilProductName,
                                   String lotNumber, int quantity, PendingDeductionStatus status, Integer settledQuantity) {}
```
```java
/** Eklentinin TARBIL'de onaylanmis olarak bildirdigi satir (spec 2026-10-09 S5.3). */
public record ReportedDeductionLine(String productName, String serialNumber, int quantity) {}
```
```java
public record DeductionSettlementResult(int settledCount, List<ReportedDeductionLine> unmatched) {}
```

`TarbilStockDeductionPort.java`:

```java
package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.UUID;

/**
 * integration/tarbil icin (spec 2026-10-09 S6): bekleyen TARBIL dusumlerini okur ve TARBIL onayi gelince duser.
 * Kiraci TenantContext'ten gelir. reported == null -> planlanan adetler (MANUAL onay ya da satir bildirmeyen eklenti).
 */
public interface TarbilStockDeductionPort {
    List<PendingDeductionLine> listForReference(StockReferenceType type, UUID referenceId);
    DeductionSettlementResult settleFromTarbil(StockReferenceType type, UUID referenceId, List<ReportedDeductionLine> reported, UUID staffId);
}
```

`PendingDeductionService.java`:

```java
package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.*;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** Bekleyen TARBIL dusumu kurallari (spec 2026-10-09 S4-S5). Tek yazma noktasi. */
@Service
@RequiredArgsConstructor
public class PendingDeductionService {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private final PendingStockDeductionRepository repository;
    private final InventoryItemRepository items;
    private final RecordStockMovementUseCase movements;

    @Transactional
    public void open(UUID inventoryItemId, int quantity, StockReferenceType type, UUID referenceId) {
        boolean exists = repository.findByReference(type, referenceId).stream()
            .anyMatch(p -> p.getInventoryItemId().equals(inventoryItemId));
        if (exists || quantity <= 0) {
            return;
        }
        repository.save(PendingStockDeduction.open(TenantContext.current(), inventoryItemId, quantity, type, referenceId));
    }

    @Transactional(readOnly = true)
    public List<PendingDeductionLine> lines(StockReferenceType type, UUID referenceId) {
        return repository.findByReference(type, referenceId).stream().map(this::toLine).toList();
    }

    @Transactional
    public DeductionSettlementResult settle(StockReferenceType type, UUID referenceId, List<ReportedDeductionLine> reported,
                                            DeductionSettlement via, UUID staffId) {
        List<PendingStockDeduction> pending = repository.findByReference(type, referenceId).stream()
            .filter(p -> p.getStatus() == PendingDeductionStatus.PENDING)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Instant now = Instant.now();
        int settled = 0;
        if (reported == null) {
            for (PendingStockDeduction p : pending) {
                settled += deduct(p, p.getQuantity(), via, staffId, now);
            }
            return new DeductionSettlementResult(settled, List.of());
        }
        List<ReportedDeductionLine> unmatched = new ArrayList<>();
        for (ReportedDeductionLine line : reported) {
            Optional<PendingStockDeduction> match = pending.stream()
                .filter(p -> serial(lotOf(p)).equals(serial(line.serialNumber())) && !serial(line.serialNumber()).isEmpty())
                .findFirst();
            if (match.isPresent() && line.quantity() > 0) {
                settled += deduct(match.get(), line.quantity(), via, staffId, now);
                pending.remove(match.get());
            } else {
                unmatched.add(line);
            }
        }
        return new DeductionSettlementResult(settled, unmatched);
    }

    @Transactional
    public boolean cancel(StockReferenceType type, UUID referenceId) {
        boolean any = false;
        for (PendingStockDeduction p : repository.findByReference(type, referenceId)) {
            if (p.cancel()) {
                repository.save(p);
                any = true;
            }
        }
        return any;
    }

    @Transactional(readOnly = true)
    public boolean hasDeducted(StockReferenceType type, UUID referenceId) {
        return repository.findByReference(type, referenceId).stream().anyMatch(p -> p.getStatus() == PendingDeductionStatus.DEDUCTED);
    }

    private int deduct(PendingStockDeduction p, int quantity, DeductionSettlement via, UUID staffId, Instant now) {
        if (!p.settle(quantity, via, staffId, now)) {
            return 0;
        }
        repository.save(p);
        movements.execute(p.getInventoryItemId(), StockMovementType.OUT, quantity, p.getReferenceType(), p.getReferenceId());
        return 1;
    }

    private String lotOf(PendingStockDeduction p) {
        return items.findById(p.getInventoryItemId()).map(InventoryItem::getLotNumber).orElse(null);
    }

    private PendingDeductionLine toLine(PendingStockDeduction p) {
        Optional<InventoryItem> item = items.findById(p.getInventoryItemId());
        return new PendingDeductionLine(p.getId(), p.getInventoryItemId(),
            item.map(InventoryItem::getName).orElse("—"), item.map(InventoryItem::getTarbilProductName).orElse(null),
            item.map(InventoryItem::getLotNumber).orElse(null), p.getQuantity(), p.getStatus(), p.getSettledQuantity());
    }

    static String serial(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase(TR);
    }
}
```

`TarbilStockDeductionAdapter.java` (`inventory.infrastructure`):

```java
package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.application.PendingDeductionService;
import com.vetos.modules.inventory.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilStockDeductionAdapter implements TarbilStockDeductionPort {
    private final PendingDeductionService service;

    @Override
    public List<PendingDeductionLine> listForReference(StockReferenceType type, UUID referenceId) {
        return service.lines(type, referenceId);
    }

    @Override
    public DeductionSettlementResult settleFromTarbil(StockReferenceType type, UUID referenceId, List<ReportedDeductionLine> reported, UUID staffId) {
        return service.settle(type, referenceId, reported, DeductionSettlement.TARBIL, staffId);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -q test -Dtest=PendingDeductionServiceTest`
Expected: PASS (6/6).

- [ ] **Step 5: Commit**

```bash
git add backend/src && git commit -m "feat(stok): bekleyen dusum servisi ve TarbilStockDeductionPort"
```

---

### Task 3: Muayene kapanışı — TARBİL'e bağlı kalemler bekler

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/inventory/application/AutoDeductStockForEncounterUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/inventory/application/AutoDeductStockForEncounterUseCaseTest.java`

**Interfaces:**
- Consumes: `PendingDeductionService.open(...)` (Task 2), `InventoryItemRepository.findById`.

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoDeductStockForEncounterUseCaseTest {

    @Mock private RecordStockMovementUseCase movements;
    @Mock private PendingDeductionService pending;
    @Mock private InventoryItemRepository items;

    private UUID item(boolean linked) {
        InventoryItem i = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "X", null, null, 5, 0, null, "L1", null);
        ReflectionTestUtils.setField(i, "id", UUID.randomUUID()); // id @GeneratedValue
        if (linked) i.linkTarbil("VETILAC_MEDICINE", "X", "Kutu");
        when(items.findById(i.getId())).thenReturn(Optional.of(i));
        return i.getId();
    }

    @Test
    void deductsUnlinkedItemsNowAndParksTarbilLinkedOnes() {
        UUID encounter = UUID.randomUUID();
        UUID plain = item(false);
        UUID linked = item(true);

        new AutoDeductStockForEncounterUseCase(movements, pending, items).execute(encounter, List.of(
            new AutoDeductStockForEncounterUseCase.UsedItem(plain, 2),
            new AutoDeductStockForEncounterUseCase.UsedItem(linked, 1)));

        verify(movements).execute(plain, StockMovementType.OUT, 2, StockReferenceType.ENCOUNTER, encounter);
        verify(movements, never()).execute(eq(linked), any(), anyInt(), any(), any());
        verify(pending).open(linked, 1, StockReferenceType.ENCOUNTER, encounter);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=AutoDeductStockForEncounterUseCaseTest`
Expected: FAIL — yapıcı 1 argüman alıyor.

- [ ] **Step 3: Implement**

```java
@Service
@RequiredArgsConstructor
public class AutoDeductStockForEncounterUseCase {

    private final RecordStockMovementUseCase recordStockMovementUseCase;
    private final PendingDeductionService pendingDeductionService;
    private final InventoryItemRepository inventoryItemRepository;

    public record UsedItem(UUID inventoryItemId, int quantity) {}

    /** TARBIL'e bagli kalem TARBIL dusumunu bekler (spec 2026-10-09 S5.1); digerleri hemen duser. */
    @Transactional
    public void execute(UUID encounterId, List<UsedItem> usedItems) {
        for (UsedItem used : usedItems) {
            boolean linked = inventoryItemRepository.findById(used.inventoryItemId())
                .map(i -> i.getTarbilProductName() != null).orElse(false);
            if (linked) {
                pendingDeductionService.open(used.inventoryItemId(), used.quantity(), StockReferenceType.ENCOUNTER, encounterId);
            } else {
                recordStockMovementUseCase.execute(
                    used.inventoryItemId(), StockMovementType.OUT, used.quantity(), StockReferenceType.ENCOUNTER, encounterId);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -q test -Dtest='AutoDeductStockForEncounterUseCaseTest,*Encounter*Test'`
Expected: PASS. Muayene kapanışını uçtan uca test eden mevcut bir test bağlı olmayan kalem kullanıyorsa değişmeden geçer.

- [ ] **Step 5: Commit**

```bash
git add backend/src && git commit -m "feat(stok): muayenede TARBIL'e bagli ilac TARBIL dusumunu bekler"
```

---

### Task 4: Aşı — kayıtta bekler, iptalde bekleyen iptal olur

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/inventory/application/ApplyVaccinationStockUseCase.java`
- Modify: `backend/src/test/java/com/vetos/modules/inventory/application/ApplyVaccinationStockUseCaseTest.java`
- Modify: `backend/src/test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java` (`vaccinationFromStockDeductsOneAndCancellationReturnsIt` → yeni davranış; `tearDown`'a `DELETE FROM pending_stock_deduction WHERE tenant_id = ?` ilk satır olarak)

**Interfaces:**
- Consumes: `PendingDeductionService.open/cancel/hasDeducted` (Task 2).

Yeni davranış:
- `administered`: kalem geçerli aşı ve seri eşleşiyorsa → `pending.open(item, 1, VACCINATION, vid)`; OUT yazılmaz. Stok 0 kontrolü kalkar (düşüm TARBİL onayında yapılır).
- `cancelled`: bekleyen satır varsa → `pending.cancel(VACCINATION, vid)`, IN yazılmaz (düşülmüşse TARBİL esastır, iade yok). Bekleyen satır yoksa (eski model) → eski IN mantığı.

- [ ] **Step 1: Update/write failing tests**

`ApplyVaccinationStockUseCaseTest` içinde yapıcıyı `new ApplyVaccinationStockUseCase(items, movements, pending)` yap (`@Mock PendingDeductionService pending;`), "uygulaninca 1 OUT" beklentili testleri şu testlerle değiştir:

```java
@Test
void administeredVaccineWaitsForTarbilInsteadOfDeducting() {
    InventoryItem item = vaccine("665932", 18);
    useCase.administered(vid, item.getId(), "665932");

    verify(pending).open(item.getId(), 1, StockReferenceType.VACCINATION, vid);
    verify(movements, never()).save(any());
    assertThat(item.getQuantityOnHand()).isEqualTo(18);
}

@Test
void cancellationBeforeTarbilCancelsThePendingDeduction() {
    InventoryItem item = vaccine("665932", 18);
    when(pending.lines(StockReferenceType.VACCINATION, vid)).thenReturn(List.of(
        new PendingDeductionLine(UUID.randomUUID(), item.getId(), "Biocan R", null, "665932", 1, PendingDeductionStatus.PENDING, null)));

    useCase.cancelled(vid, item.getId());

    verify(pending).cancel(StockReferenceType.VACCINATION, vid);
    verify(movements, never()).save(any());
}

@Test
void cancellationAfterTarbilDeductionDoesNotReturnStock() {
    InventoryItem item = vaccine("665932", 17);
    when(pending.lines(StockReferenceType.VACCINATION, vid)).thenReturn(List.of(
        new PendingDeductionLine(UUID.randomUUID(), item.getId(), "Biocan R", null, "665932", 1, PendingDeductionStatus.DEDUCTED, 1)));

    useCase.cancelled(vid, item.getId());

    verify(movements, never()).save(any());
    assertThat(item.getQuantityOnHand()).isEqualTo(17);
}

@Test
void legacyDeductedVaccinationIsStillReturnedOnCancel() {
    InventoryItem item = vaccine("665932", 17);
    when(pending.lines(StockReferenceType.VACCINATION, vid)).thenReturn(List.of());
    when(movements.existsByReference(vid, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(true);
    when(movements.existsByReference(vid, StockReferenceType.VACCINATION, StockMovementType.IN)).thenReturn(false);

    useCase.cancelled(vid, item.getId());

    assertThat(item.getQuantityOnHand()).isEqualTo(18);
}
```

(`vaccine(lot, qty)` yardımcısı dosyada yoksa ekle: `InventoryItem.create(tenant, branch, "Biocan R", "Aşı", null, qty, 0, null, lot, null)` + `ReflectionTestUtils.setField(i, "id", UUID.randomUUID())` (id `@GeneratedValue`) + `when(items.findById(i.getId())).thenReturn(Optional.of(i))`.) Seri uyuşmazlığı / aşı olmayan kalem testleri kalır; beklentileri `verify(pending, never()).open(...)` olur.

Entegrasyon testi `vaccinationFromStockDeductsOneAndCancellationReturnsIt` → adını `vaccinationFromStockWaitsForTarbilAndDeductsOnConfirmation` yap, gövdeyi Task 5'te bitireceğiz; bu adımda kaydın ardından `afterRecord == 18` ve `SELECT status FROM pending_stock_deduction WHERE reference_id = <vid>` = `PENDING` iddiası; iptal sonrası `CANCELLED` ve stok 18. `vid` = `location`'ın son segmenti.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest='ApplyVaccinationStockUseCaseTest'`
Expected: FAIL — yapıcı/davranış eski.

- [ ] **Step 3: Implement**

```java
@Service
@RequiredArgsConstructor
public class ApplyVaccinationStockUseCase {

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;
    private final PendingDeductionService pending;

    /** Stoktan secilen asi TARBIL dusumunu bekler (spec 2026-10-09 S5.2). Kalem asi degilse / seri farkliysa bekleme acilmaz. */
    @Transactional
    public void administered(UUID vaccinationRecordId, UUID inventoryItemId, String lotNumber) {
        if (inventoryItemId == null
            || movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)) {
            return;
        }
        items.findById(inventoryItemId)
            .filter(i -> isVaccine(i) && serial(i.getLotNumber()).equals(serial(lotNumber)))
            .ifPresent(item -> pending.open(item.getId(), 1, StockReferenceType.VACCINATION, vaccinationRecordId));
    }

    /** Bekleyen varsa iptal edilir (iade yok: ya hic dusmedi ya da TARBIL'de dustu ve TARBIL esastir). Eski kayitlarda 1 IN. */
    @Transactional
    public void cancelled(UUID vaccinationRecordId, UUID inventoryItemId) {
        if (inventoryItemId == null) {
            return;
        }
        if (!pending.lines(StockReferenceType.VACCINATION, vaccinationRecordId).isEmpty()) {
            pending.cancel(StockReferenceType.VACCINATION, vaccinationRecordId);
            return;
        }
        if (!movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)
            || movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.IN)) {
            return;
        }
        items.findById(inventoryItemId).ifPresent(item -> {
            item.adjustQuantity(1);
            items.save(item);
            movements.save(StockMovement.record(item.getTenantId(), item.getId(), StockMovementType.IN, 1,
                StockReferenceType.VACCINATION, vaccinationRecordId));
        });
    }
    // isVaccine / serial / TR aynen kalir
}
```

Not: TARBİL onayında yazılan OUT `RecordStockMovementUseCase` üzerinden `reference_type = VACCINATION` ile gider; V77 tekil indeksi (bir aşıya bir OUT) korunur.

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -q test -Dtest='ApplyVaccinationStockUseCaseTest,TarbilExtensionSecurityIntegrationTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src && git commit -m "feat(stok): stoktan asi TARBIL dusumunu bekler, iptal bekleyeni kapatir"
```

---

### Task 5: Onay (submitted) bekleyenleri düşer + bildirilen satırlar

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSourceKind.java`
- Modify: `integration/tarbil/domain/TarbilSubmission.java`, `application/MarkSubmittedUseCase.java`, `api/dto/MarkSubmittedRequest.java`, `api/TarbilExtensionController.java`, `application/QueueTarbilSyncUseCase.java` (aşı kuyruğu `sourceKind = VACCINATION`)
- Test: `backend/src/test/java/com/vetos/modules/integration/tarbil/application/MarkSubmittedUseCaseTest.java` (yoksa oluştur), entegrasyon testi

**Interfaces:**
- Consumes: `TarbilStockDeductionPort.settleFromTarbil` (Task 2).
- Produces:
  - `enum TarbilSourceKind { VACCINATION, ENCOUNTER, PRESCRIPTION, STOCK_RECEIPT }` + `StockReferenceType stockReference()` (VACCINATION→VACCINATION, ENCOUNTER→ENCOUNTER, PRESCRIPTION→PRESCRIPTION, STOCK_RECEIPT→null)
  - `TarbilSubmission.queuePrescription(UUID tenantId, UUID patientId, TarbilSourceKind kind, UUID sourceId, UUID tarbilDiseaseId)`; getter `getSourceKind()` (null ise `documentType`'tan türetilen `sourceKindOrDefault()`), `getTarbilDiseaseId()`, `noteSettlement(String)`
  - `MarkSubmittedUseCase.execute(UUID tenantId, UUID staffId, UUID submissionId, TarbilConfirmationMethod method, String reference, List<ReportedDeductionLine> lines)`
  - `MarkSubmittedRequest(method, tarbilReference, List<Line> lines)` — `record Line(@NotBlank @Size(max=300) String productName, @Size(max=100) String serialNumber, @Positive int quantity)`, `lines` en çok 50 (`@Size(max = 50)`).

- [ ] **Step 1: Write the failing tests**

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.*;
import com.vetos.modules.inventory.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarkSubmittedUseCaseTest {

    @Mock private TarbilSubmissionRepository repository;
    @Mock private TarbilSubmissionAssembler assembler;
    @Mock private TarbilStockDeductionPort deductions;

    private final UUID tenant = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();

    private TarbilSubmission encounterSubmission() {
        TarbilSubmission s = TarbilSubmission.queuePrescription(tenant, UUID.randomUUID(), TarbilSourceKind.ENCOUNTER, UUID.randomUUID(), UUID.randomUUID());
        when(repository.findById(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private MarkSubmittedUseCase useCase() {
        return new MarkSubmittedUseCase(repository, assembler, deductions);
    }

    @Test
    void settlesReportedLinesOnFirstConfirmation() {
        TarbilSubmission s = encounterSubmission();
        List<ReportedDeductionLine> lines = List.of(new ReportedDeductionLine("Synulox", "S1", 2));
        when(deductions.settleFromTarbil(StockReferenceType.ENCOUNTER, s.getSourceId(), lines, staff))
            .thenReturn(new DeductionSettlementResult(1, List.of()));

        useCase().execute(tenant, staff, s.getId(), TarbilConfirmationMethod.AUTO, null, lines);

        verify(deductions).settleFromTarbil(StockReferenceType.ENCOUNTER, s.getSourceId(), lines, staff);
        assertThat(s.getSettlementNote()).isNull();
    }

    @Test
    void manualConfirmationSettlesPlannedQuantities() {
        TarbilSubmission s = encounterSubmission();
        when(deductions.settleFromTarbil(any(), any(), isNull(), any())).thenReturn(new DeductionSettlementResult(1, List.of()));

        useCase().execute(tenant, staff, s.getId(), TarbilConfirmationMethod.MANUAL, null, List.of(new ReportedDeductionLine("X", "Y", 1)));

        verify(deductions).settleFromTarbil(StockReferenceType.ENCOUNTER, s.getSourceId(), null, staff);
    }

    @Test
    void secondConfirmationDoesNotDeductAgain() {
        TarbilSubmission s = encounterSubmission();
        when(deductions.settleFromTarbil(any(), any(), any(), any())).thenReturn(new DeductionSettlementResult(1, List.of()));
        useCase().execute(tenant, staff, s.getId(), TarbilConfirmationMethod.AUTO, null, null);

        useCase().execute(tenant, staff, s.getId(), TarbilConfirmationMethod.MANUAL, null, null);

        verify(deductions, times(1)).settleFromTarbil(any(), any(), any(), any());
    }

    @Test
    void notesReportedLinesThatMatchedNothing() {
        TarbilSubmission s = encounterSubmission();
        when(deductions.settleFromTarbil(any(), any(), any(), any()))
            .thenReturn(new DeductionSettlementResult(0, List.of(new ReportedDeductionLine("Baytril", "B9", 1))));

        useCase().execute(tenant, staff, s.getId(), TarbilConfirmationMethod.AUTO, null, List.of(new ReportedDeductionLine("Baytril", "B9", 1)));

        assertThat(s.getSettlementNote()).contains("Baytril").contains("B9");
    }
}
```

Entegrasyon testine (Task 4'te yeniden adlandırılan test) ekle: kayıttan sonra aktarım id'sini `SELECT id FROM tarbil_submission WHERE source_id = ?` ile al, `POST /api/v1/tarbil-extension/submissions/{id}/submitted` (eklenti token'ı `tokenA` ile, gövde `{"method":"AUTO"}`) → 200, stok 17, bekleyen `DEDUCTED`; aynı isteği tekrar → stok yine 17.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest=MarkSubmittedUseCaseTest`
Expected: FAIL — `queuePrescription`/`TarbilSourceKind`/yeni yapıcı yok.

- [ ] **Step 3: Implement**

`TarbilSourceKind.java`:

```java
package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.inventory.domain.StockReferenceType;

/** Aktarimin Vetly kaynagi (spec 2026-10-09 S5.6). PRESCRIPTION belgesi muayeneden ya da receteden gelebilir. */
public enum TarbilSourceKind {
    VACCINATION, ENCOUNTER, PRESCRIPTION, STOCK_RECEIPT;

    public StockReferenceType stockReference() {
        return switch (this) {
            case VACCINATION -> StockReferenceType.VACCINATION;
            case ENCOUNTER -> StockReferenceType.ENCOUNTER;
            case PRESCRIPTION -> StockReferenceType.PRESCRIPTION;
            case STOCK_RECEIPT -> null;
        };
    }
}
```

`TarbilSubmission.java` ekleri (javadoc'taki "PRESCRIPTION -> prescriptions.id" satırını "PRESCRIPTION -> source_kind'e gore encounters.id ya da prescriptions.id" yap):

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind")
    private TarbilSourceKind sourceKind;

    @Column(name = "tarbil_disease_id")
    private UUID tarbilDiseaseId;

    @Column(name = "settlement_note")
    private String settlementNote;

    public static TarbilSubmission queuePrescription(UUID tenantId, UUID patientId, TarbilSourceKind kind, UUID sourceId, UUID tarbilDiseaseId) {
        TarbilSubmission s = queue(tenantId, TarbilDocumentType.PRESCRIPTION, patientId, sourceId);
        s.sourceKind = kind;
        s.tarbilDiseaseId = tarbilDiseaseId;
        return s;
    }

    /** V78 oncesi satirlarda null olabilir. */
    public TarbilSourceKind sourceKindOrDefault() {
        if (sourceKind != null) return sourceKind;
        return switch (documentType) {
            case VACCINATION -> TarbilSourceKind.VACCINATION;
            case PRESCRIPTION -> TarbilSourceKind.PRESCRIPTION;
            case STOCK_RECEIPT -> TarbilSourceKind.STOCK_RECEIPT;
        };
    }

    public void noteSettlement(String note) {
        this.settlementNote = note;
    }
```

`TarbilSubmission.id` `@GeneratedValue` — `queue(...)` içinde `submission.id = UUID.randomUUID();` ata ve `@GeneratedValue`'yu kaldır (testler `getId()`'yi kayıttan önce kullanıyor; `TarbilStockSnapshotLine`'daki kalıp). `queueVaccination` içinde `s.sourceKind = TarbilSourceKind.VACCINATION`.

`MarkSubmittedUseCase.java`:

```java
@Service
@RequiredArgsConstructor
public class MarkSubmittedUseCase {

    private final TarbilSubmissionRepository syncLogRepository;
    private final TarbilSubmissionAssembler assembler;
    private final TarbilStockDeductionPort stockDeductionPort;

    /** Ilk onayda bekleyen stok dusulur (spec 2026-10-09 S5); MANUAL -> planlanan adetler, AUTO -> bildirilen satirlar (yoksa planlanan). */
    @Transactional
    public Optional<TarbilSubmissionView> execute(UUID tenantId, UUID staffId, UUID submissionId,
                                                  TarbilConfirmationMethod method, String tarbilReference,
                                                  List<ReportedDeductionLine> lines) {
        TarbilSubmission log = syncLogRepository.findById(submissionId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
        String reference = tarbilReference == null || tarbilReference.isBlank() ? null : tarbilReference.trim();
        if (log.markSubmitted(staffId, method, reference, Instant.now())) {
            StockReferenceType stockRef = log.sourceKindOrDefault().stockReference();
            if (stockRef != null) {
                List<ReportedDeductionLine> reported = method == TarbilConfirmationMethod.MANUAL || lines == null || lines.isEmpty() ? null : lines;
                DeductionSettlementResult result = stockDeductionPort.settleFromTarbil(stockRef, log.getSourceId(), reported, staffId);
                if (!result.unmatched().isEmpty()) {
                    log.noteSettlement("Vetly'de eslesmeyen TARBIL satirlari: " + result.unmatched().stream()
                        .map(l -> l.productName() + " / " + l.serialNumber() + " x" + l.quantity())
                        .collect(java.util.stream.Collectors.joining(", ")));
                }
            }
            syncLogRepository.save(log);
        }
        return assembler.assemble(log);
    }
}
```

`MarkSubmittedRequest.java`:

```java
public record MarkSubmittedRequest(@NotNull TarbilConfirmationMethod method, @Size(max = 100) String tarbilReference,
                                   @Valid @Size(max = 50) List<Line> lines) {
    public record Line(@NotBlank @Size(max = 300) String productName, @Size(max = 100) String serialNumber, @Positive int quantity) {}

    public List<ReportedDeductionLine> toReported() {
        return lines == null ? null : lines.stream().map(l -> new ReportedDeductionLine(l.productName(), l.serialNumber(), l.quantity())).toList();
    }
}
```

`TarbilExtensionController.submitted`: `markSubmittedUseCase.execute(TenantContext.current(), user.staffUserId(), id, request.method(), request.tarbilReference(), request.toReported())`. Diğer `markSubmittedUseCase.execute(` çağrıları varsa (`grep -rn "markSubmittedUseCase.execute" backend/src`) son argüman `null`.

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -q test -Dtest='MarkSubmittedUseCaseTest,TarbilExtensionSecurityIntegrationTest,ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src && git commit -m "feat(tarbil): onay bildirimi bekleyen stogu duser, eslesmeyen satirlari not eder"
```

---

### Task 6: Bekleyen listesi + "Yalnız Vetly'den düş"

**Files:**
- Create: `inventory/application/ListPendingDeductionsUseCase.java`, `inventory/application/SettleVetlyOnlyUseCase.java`
- Create: `inventory/domain/event/PendingDeductionSettledVetlyOnlyEvent.java`, `inventory/domain/event/package-info.java`
- Create: `inventory/api/PendingDeductionsController.java`, `inventory/api/dto/PendingDeductionResponse.java`
- Create: `integration/tarbil/infrastructure/event/VetlyOnlyDeductionListener.java`
- Test: `backend/src/test/java/com/vetos/modules/inventory/application/SettleVetlyOnlyUseCaseTest.java`, entegrasyon testi

**Interfaces:**
- Produces:
  - `GET /api/v1/inventory/pending-deductions` (VET, TECHNICIAN, ADMIN) → `[{id, inventoryItemId, itemName, lotNumber, quantity, referenceType, referenceId, createdAt}]` yalnız `PENDING`
  - `POST /api/v1/inventory/pending-deductions/{referenceType}/{referenceId}/settle-vetly-only` (VET, ADMIN) → 204
  - `record PendingDeductionSettledVetlyOnlyEvent(StockReferenceType referenceType, UUID referenceId, UUID staffId)`
- Consumes: `PendingDeductionService.settle(..., VETLY_ONLY, ...)`, `DomainEventPublisher.publish`, integration tarafında `TarbilSubmissionRepository.findByDocumentTypeAndSourceId`.

- [ ] **Step 1: Write the failing tests**

```java
package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.*;
import com.vetos.modules.inventory.domain.event.PendingDeductionSettledVetlyOnlyEvent;
import com.vetos.platform.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettleVetlyOnlyUseCaseTest {

    @Mock private PendingDeductionService service;
    @Mock private DomainEventPublisher publisher;

    @Test
    void settlesPendingLinesAndAnnouncesIt() {
        UUID ref = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        when(service.settle(StockReferenceType.ENCOUNTER, ref, null, DeductionSettlement.VETLY_ONLY, staff))
            .thenReturn(new DeductionSettlementResult(2, List.of()));

        new SettleVetlyOnlyUseCase(service, publisher).execute(StockReferenceType.ENCOUNTER, ref, staff);

        verify(publisher).publish(new PendingDeductionSettledVetlyOnlyEvent(StockReferenceType.ENCOUNTER, ref, staff));
    }

    @Test
    void doesNotAnnounceWhenNothingWasPending() {
        UUID ref = UUID.randomUUID();
        when(service.settle(any(), any(), any(), any(), any())).thenReturn(new DeductionSettlementResult(0, List.of()));

        new SettleVetlyOnlyUseCase(service, publisher).execute(StockReferenceType.ENCOUNTER, ref, UUID.randomUUID());

        verifyNoInteractions(publisher);
    }
}
```

Entegrasyon testi (`TarbilExtensionSecurityIntegrationTest`): `tarbilConfirmationAfterVetlyOnlyDoesNotDeductAgain` — aşı stoktan kaydedilir (stok 18) → `POST /api/v1/inventory/pending-deductions/VACCINATION/{vid}/settle-vetly-only` (jwt) → 204, stok 17, aktarım `DISMISSED` (`dismissed_reason` = `Yalnız Vetly'den düşüldü`) → eklenti `submitted` (AUTO) → 200, stok yine 17. Ayrıca `GET /api/v1/inventory/pending-deductions` kayıttan hemen sonra 1 satır döner.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest=SettleVetlyOnlyUseCaseTest`
Expected: FAIL — sınıflar yok.

- [ ] **Step 3: Implement**

`inventory/domain/event/package-info.java`:

```java
@org.springframework.modulith.NamedInterface("domain.event")
package com.vetos.modules.inventory.domain.event;
```

`PendingDeductionSettledVetlyOnlyEvent.java`:

```java
package com.vetos.modules.inventory.domain.event;

import com.vetos.modules.inventory.domain.StockReferenceType;
import java.util.UUID;

/** Hekim TARBIL'e gitmeden yalniz Vetly'den dustu; integration bekleyen aktarimi reddeder (spec 2026-10-09 S5.1). */
public record PendingDeductionSettledVetlyOnlyEvent(StockReferenceType referenceType, UUID referenceId, UUID staffId) {}
```

`SettleVetlyOnlyUseCase.java`:

```java
@Service
@RequiredArgsConstructor
public class SettleVetlyOnlyUseCase {
    private final PendingDeductionService service;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void execute(StockReferenceType type, UUID referenceId, UUID staffId) {
        if (service.settle(type, referenceId, null, DeductionSettlement.VETLY_ONLY, staffId).settledCount() > 0) {
            eventPublisher.publish(new PendingDeductionSettledVetlyOnlyEvent(type, referenceId, staffId));
        }
    }
}
```

`ListPendingDeductionsUseCase.java`:

```java
@Service
@RequiredArgsConstructor
public class ListPendingDeductionsUseCase {
    private final PendingStockDeductionRepository repository;
    private final InventoryItemRepository items;

    public record PendingDeductionView(UUID id, UUID inventoryItemId, String itemName, String lotNumber, int quantity,
                                       StockReferenceType referenceType, UUID referenceId, Instant createdAt) {}

    @Transactional(readOnly = true)
    public List<PendingDeductionView> execute() {
        return repository.findAllPending().stream().map(p -> {
            Optional<InventoryItem> item = items.findById(p.getInventoryItemId());
            return new PendingDeductionView(p.getId(), p.getInventoryItemId(), item.map(InventoryItem::getName).orElse("—"),
                item.map(InventoryItem::getLotNumber).orElse(null), p.getQuantity(), p.getReferenceType(), p.getReferenceId(), p.getCreatedAt());
        }).toList();
    }
}
```

`PendingDeductionResponse.java`: `record PendingDeductionResponse(UUID id, UUID inventoryItemId, String itemName, String lotNumber, int quantity, String referenceType, UUID referenceId, Instant createdAt)` + `static from(PendingDeductionView v)`.

`PendingDeductionsController.java`:

```java
@RestController
@RequestMapping("/api/v1/inventory/pending-deductions")
@RequiredArgsConstructor
public class PendingDeductionsController {
    private final ListPendingDeductionsUseCase listUseCase;
    private final SettleVetlyOnlyUseCase settleVetlyOnlyUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public List<PendingDeductionResponse> list() {
        return listUseCase.execute().stream().map(PendingDeductionResponse::from).toList();
    }

    @PostMapping("/{referenceType}/{referenceId}/settle-vetly-only")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public ResponseEntity<Void> settleVetlyOnly(@PathVariable StockReferenceType referenceType, @PathVariable UUID referenceId,
                                                @AuthenticationPrincipal AuthenticatedStaffUser user) {
        settleVetlyOnlyUseCase.execute(referenceType, referenceId, user.staffUserId());
        return ResponseEntity.noContent().build();
    }
}
```

`VetlyOnlyDeductionListener.java` (integration):

```java
@Component
@RequiredArgsConstructor
class VetlyOnlyDeductionListener {
    static final String REASON = "Yalnız Vetly'den düşüldü";
    private final TarbilSubmissionRepository repository;

    @EventListener
    void on(PendingDeductionSettledVetlyOnlyEvent event) {
        TarbilDocumentType doc = event.referenceType() == StockReferenceType.VACCINATION ? TarbilDocumentType.VACCINATION : TarbilDocumentType.PRESCRIPTION;
        repository.findByDocumentTypeAndSourceId(doc, event.referenceId())
            .filter(s -> s.getStatus() == TarbilSyncStatus.PENDING)
            .ifPresent(s -> {
                s.dismiss(event.staffId(), REASON, Instant.now());
                repository.save(s);
            });
    }
}
```

`findByDocumentTypeAndSourceId` kiracı filtresiz bir yerel sorguysa, `.filter(s -> s.getTenantId().equals(TenantContext.current()))` ekle.

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -q test -Dtest='SettleVetlyOnlyUseCaseTest,TarbilExtensionSecurityIntegrationTest,ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src && git commit -m "feat(stok): bekleyen TARBIL dusumleri listesi ve yalniz Vetly'den dus"
```

---

### Task 7: Muayene — "TARBİL'den stok düş" kuyruğu ve durum

**Files:**
- Create: `integration/tarbil/application/QueueEncounterDeductionUseCase.java`, `integration/tarbil/application/GetEncounterTarbilDeductionUseCase.java`, `integration/tarbil/domain/exception/TarbilNothingToDeductConflictException.java`, `integration/tarbil/api/dto/{QueueEncounterDeductionRequest,EncounterTarbilDeductionResponse}.java`
- Modify: `integration/tarbil/api/TarbilController.java`
- Test: `backend/src/test/java/com/vetos/modules/integration/tarbil/application/QueueEncounterDeductionUseCaseTest.java`, entegrasyon testi

**Interfaces:**
- Consumes: `EncounterLookupPort.findSummaryById(UUID): EncounterSummary(id, patientId, staffUserId, status)`, `TarbilStockDeductionPort.listForReference`, `TarbilSubmission.queuePrescription`.
- Produces:
  - `POST /api/v1/tarbil/encounters/{encounterId}/deduction` body `{tarbilDiseaseId}` (VET, ADMIN) → 201 `{submissionId}`; mevcut aktarım varsa aynı id (idempotent).
  - `GET /api/v1/tarbil/encounters/{encounterId}/deduction` (VET, TECHNICIAN, ADMIN) → `{lines:[{itemName, lotNumber, quantity, status, settledQuantity}], submissionId, submissionStatus}`; TARBİL'e bağlı satır yoksa `lines: []`.
  - `QueueEncounterDeductionUseCase.execute(UUID encounterId, UUID tarbilDiseaseId): UUID`

Kurallar: muayene `FINALIZED`/`AMENDED` değilse → `TarbilNothingToDeductConflictException` ("Muayene tamamlanmadı"); `PENDING` satır yoksa → aynı istisna ("TARBİL düşümü bekleyen ilaç yok"); `tarbilDiseaseId` zorunlu (`@NotNull`). Başka kiracının muayenesi: `EncounterLookupPort` filtreli oturumda `EncounterNotFoundException` fırlatır → 404.

- [ ] **Step 1: Write the failing test**

```java
@ExtendWith(MockitoExtension.class)
class QueueEncounterDeductionUseCaseTest {

    @Mock private EncounterLookupPort encounters;
    @Mock private TarbilStockDeductionPort deductions;
    @Mock private TarbilSubmissionRepository submissions;

    private final UUID encounter = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final UUID disease = UUID.randomUUID();

    private QueueEncounterDeductionUseCase useCase() {
        return new QueueEncounterDeductionUseCase(encounters, deductions, submissions);
    }

    private PendingDeductionLine line(PendingDeductionStatus status) {
        return new PendingDeductionLine(UUID.randomUUID(), UUID.randomUUID(), "Synulox", "Synulox", "S1", 1, status, null);
    }

    @Test
    void queuesAPrescriptionSubmissionForTheEncounter() {
        when(encounters.findSummaryById(encounter)).thenReturn(new EncounterSummary(encounter, patient, UUID.randomUUID(), EncounterStatus.FINALIZED));
        when(deductions.listForReference(StockReferenceType.ENCOUNTER, encounter)).thenReturn(List.of(line(PendingDeductionStatus.PENDING)));
        when(submissions.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, encounter)).thenReturn(Optional.empty());
        when(submissions.save(any())).thenAnswer(a -> a.getArgument(0));

        TenantContext.set(UUID.randomUUID());
        try {
            useCase().execute(encounter, disease);
        } finally {
            TenantContext.clear();
        }

        ArgumentCaptor<TarbilSubmission> saved = ArgumentCaptor.forClass(TarbilSubmission.class);
        verify(submissions).save(saved.capture());
        assertThat(saved.getValue().getSourceKind()).isEqualTo(TarbilSourceKind.ENCOUNTER);
        assertThat(saved.getValue().getTarbilDiseaseId()).isEqualTo(disease);
        assertThat(saved.getValue().getPatientId()).isEqualTo(patient);
    }

    @Test
    void queueWithoutPendingLinesConflicts() {
        when(encounters.findSummaryById(encounter)).thenReturn(new EncounterSummary(encounter, patient, UUID.randomUUID(), EncounterStatus.FINALIZED));
        when(deductions.listForReference(StockReferenceType.ENCOUNTER, encounter)).thenReturn(List.of(line(PendingDeductionStatus.DEDUCTED)));

        assertThatThrownBy(() -> useCase().execute(encounter, disease)).isInstanceOf(TarbilNothingToDeductConflictException.class);
        verify(submissions, never()).save(any());
    }

    @Test
    void draftEncounterCannotBeQueued() {
        when(encounters.findSummaryById(encounter)).thenReturn(new EncounterSummary(encounter, patient, UUID.randomUUID(), EncounterStatus.DRAFT));

        assertThatThrownBy(() -> useCase().execute(encounter, disease)).isInstanceOf(TarbilNothingToDeductConflictException.class);
    }
}
```

Kiracı kalıbı `QueueTarbilSyncUseCaseTest`/`TarbilStockSyncAdapterTest` ile aynı: `TenantContext.set(...)`; temizleme metodunun adını o testlerden al (`clear()` değilse ona göre düzelt).

Entegrasyon testleri: `foreignEncounterDeductionIsNotFound` — tenantB'de oluşturulmuş muayene id'siyle tenantA jwt'si `GET/POST .../encounters/{id}/deduction` → 404. `encounterMedicineIsDeductedAfterTarbilConfirmation` — tenantA'da hasta + TARBİL'e bağlı kalem (`linkTarbil`) + muayene, kullanım (`POST /api/v1/encounters/{id}/inventory-usages` mevcut uç; adını `grep -rn "inventory-usage\|InventoryUsage" backend/src/main/java/com/vetos/modules/encounter/api` ile doğrula) + finalize → stok değişmez, `GET .../deduction` 1 PENDING satır → `POST .../deduction {tarbilDiseaseId: <tarbil_disease'ten seçilebilir ilk id>}` 201 → eklenti `submitted` MANUAL → stok düşer.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest=QueueEncounterDeductionUseCaseTest`
Expected: FAIL — sınıf yok.

- [ ] **Step 3: Implement**

```java
@Service
@RequiredArgsConstructor
public class QueueEncounterDeductionUseCase {
    private final EncounterLookupPort encounterLookupPort;
    private final TarbilStockDeductionPort stockDeductionPort;
    private final TarbilSubmissionRepository submissions;

    /** Muayene karti "TARBIL'den stok dus" (spec 2026-10-09 S5.1). */
    @Transactional
    public UUID execute(UUID encounterId, UUID tarbilDiseaseId) {
        EncounterSummary encounter = encounterLookupPort.findSummaryById(encounterId);
        if (encounter.status() == EncounterStatus.DRAFT) {
            throw new TarbilNothingToDeductConflictException("Muayene tamamlanmadı");
        }
        boolean anyPending = stockDeductionPort.listForReference(StockReferenceType.ENCOUNTER, encounterId).stream()
            .anyMatch(l -> l.status() == PendingDeductionStatus.PENDING);
        if (!anyPending) {
            throw new TarbilNothingToDeductConflictException("TARBİL düşümü bekleyen ilaç yok");
        }
        return submissions.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, encounterId)
            .map(TarbilSubmission::getId)
            .orElseGet(() -> submissions.save(TarbilSubmission.queuePrescription(
                TenantContext.current(), encounter.patientId(), TarbilSourceKind.ENCOUNTER, encounterId, tarbilDiseaseId)).getId());
    }
}
```

`TarbilNothingToDeductConflictException extends DomainException` → `super("TARBIL_NOTHING_TO_DEDUCT", message)`.

`GetEncounterTarbilDeductionUseCase`:

```java
@Service
@RequiredArgsConstructor
public class GetEncounterTarbilDeductionUseCase {
    private final EncounterLookupPort encounterLookupPort;
    private final TarbilStockDeductionPort stockDeductionPort;
    private final TarbilSubmissionRepository submissions;

    public record View(List<PendingDeductionLine> lines, UUID submissionId, TarbilSyncStatus submissionStatus) {}

    @Transactional(readOnly = true)
    public View execute(UUID encounterId) {
        encounterLookupPort.findSummaryById(encounterId); // kiraci disi -> 404
        Optional<TarbilSubmission> s = submissions.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, encounterId);
        return new View(stockDeductionPort.listForReference(StockReferenceType.ENCOUNTER, encounterId),
            s.map(TarbilSubmission::getId).orElse(null), s.map(TarbilSubmission::getStatus).orElse(null));
    }
}
```

`TarbilController` ekleri:

```java
    @PostMapping("/encounters/{encounterId}/deduction")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public ResponseEntity<Map<String, UUID>> queueEncounterDeduction(@PathVariable UUID encounterId,
                                                                    @Valid @RequestBody QueueEncounterDeductionRequest request) {
        UUID id = queueEncounterDeductionUseCase.execute(encounterId, request.tarbilDiseaseId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("submissionId", id));
    }

    @GetMapping("/encounters/{encounterId}/deduction")
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public EncounterTarbilDeductionResponse encounterDeduction(@PathVariable UUID encounterId) {
        return EncounterTarbilDeductionResponse.from(getEncounterTarbilDeductionUseCase.execute(encounterId));
    }
```

`QueueEncounterDeductionRequest(@NotNull UUID tarbilDiseaseId)`; `EncounterTarbilDeductionResponse(List<Line> lines, UUID submissionId, String submissionStatus)`, `record Line(String itemName, String lotNumber, int quantity, String status, Integer settledQuantity)`.

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -q test -Dtest='QueueEncounterDeductionUseCaseTest,TarbilExtensionSecurityIntegrationTest,ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src && git commit -m "feat(tarbil): muayene kartindan TARBIL'den stok dus kuyrugu ve durum ucu"
```

---

### Task 8: Reçete — muayenesiz, teşhis, "kliniğin stoğundan ver", iptal

**Files:**
- Modify: `encounter/domain/Prescription.java`, `PrescriptionItem.java`, `application/IssuePrescriptionUseCase.java`, `application/dto/{IssuePrescriptionCommand,PrescriptionItemInput,PrescriptionDetail}.java`, `api/dto/{IssuePrescriptionRequest,PrescriptionResponse}.java`, `api/PrescriptionsController.java`, `PrescriptionDetail`'i kuran use case'ler (`grep -rn "new PrescriptionDetail(" backend/src/main`)
- Create: `encounter/domain/event/{PrescriptionIssuedEvent,PrescriptionCancelledEvent}.java`, `encounter/application/CancelPrescriptionUseCase.java`, `encounter/domain/exception/{PrescriptionTarbilDiseaseRequiredException,PrescriptionEncounterMismatchException,PrescriptionNotCancellableConflictException}.java`
- Create: `inventory/infrastructure/event/PrescriptionStockEventListener.java`, `integration/tarbil/infrastructure/event/PrescriptionTarbilEventListener.java`
- Modify: `integration/tarbil/application/QueueTarbilSyncUseCase.java` (+`queuePrescription`, +`dismissPrescription`)
- Test: `backend/src/test/java/com/vetos/modules/encounter/application/IssuePrescriptionUseCaseTest.java`, `CancelPrescriptionUseCaseTest.java`, entegrasyon testi

**Interfaces:**
- Produces:
  - `IssuePrescriptionCommand(UUID patientId, UUID encounterIdOrNull, UUID staffId, boolean controlledSubstance, UUID tarbilDiseaseId, List<PrescriptionItemInput> items)`
  - `PrescriptionItemInput(UUID drugId, String dosage, String frequency, int durationDays, DrugRoute route, UUID inventoryItemId, Integer stockQuantity)` — ikisi birlikte dolu ya da birlikte boş
  - `record PrescriptionIssuedEvent(UUID prescriptionId, UUID patientId, UUID tarbilDiseaseId, List<StockItem> stockItems)`, `record StockItem(UUID inventoryItemId, int quantity)`
  - `record PrescriptionCancelledEvent(UUID prescriptionId)`
  - `POST /api/v1/prescriptions` gövdesi: `encounterId` isteğe bağlı, `tarbilDiseaseId` isteğe bağlı, kalemde `inventoryItemId`, `stockQuantity`
  - `POST /api/v1/prescriptions/{id}/cancel` (VET, ADMIN) → 204; `ACTIVE` değilse 409
  - `PrescriptionResponse` + `tarbilDiseaseId`; kalem detayı + `inventoryItemId`, `stockQuantity`
  - `QueueTarbilSyncUseCase.queuePrescription(UUID prescriptionId, UUID patientId, UUID tarbilDiseaseId): UUID`, `dismissPrescription(UUID prescriptionId, String reason)`

Kurallar: stoktan verilen kalem varsa `tarbilDiseaseId` zorunlu → `PrescriptionTarbilDiseaseRequiredException` (422). `encounterId` verilmişse muayenenin hastası `patientId` olmalı → `PrescriptionEncounterMismatchException` (422). Kalemde `inventoryItemId` ile `stockQuantity`'den yalnız biri doluysa 422 (`PrescriptionTarbilDiseaseRequiredException` değil; aynı sınıf ailesinde `InvalidPrescriptionItemException` oluştur — Files listesine ekle).

- [ ] **Step 1: Write the failing tests**

```java
@ExtendWith(MockitoExtension.class)
class IssuePrescriptionUseCaseTest {

    @Mock private PrescriptionRepository prescriptions;
    @Mock private PrescriptionItemRepository items;
    @Mock private EncounterRepository encounters;
    @Mock private DomainEventPublisher publisher;

    private final UUID patient = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private final UUID drug = UUID.randomUUID();

    private IssuePrescriptionUseCase useCase() {
        lenient().when(prescriptions.save(any())).thenAnswer(a -> a.getArgument(0));
        return new IssuePrescriptionUseCase(prescriptions, items, encounters, publisher);
    }

    private PrescriptionItemInput stockItem(UUID inv, int qty) {
        return new PrescriptionItemInput(drug, "1 tb", "2x1", 5, DrugRoute.ORAL, inv, qty);
    }

    @Test
    void issuesWithoutEncounterAndAnnouncesStockItems() {
        UUID inv = UUID.randomUUID();
        UUID disease = UUID.randomUUID();
        TenantContext.set(UUID.randomUUID());
        try {
            UUID id = useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, disease,
                List.of(stockItem(inv, 2), new PrescriptionItemInput(drug, "1 tb", "1x1", 3, DrugRoute.ORAL, null, null))));

            verify(publisher).publish(new PrescriptionIssuedEvent(id, patient, disease, List.of(new PrescriptionIssuedEvent.StockItem(inv, 2))));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void stockItemRequiresTarbilDisease() {
        assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, null,
            List.of(stockItem(UUID.randomUUID(), 1))))).isInstanceOf(PrescriptionTarbilDiseaseRequiredException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void encounterMustBelongToThePatient() {
        UUID encounterId = UUID.randomUUID();
        Encounter other = mock(Encounter.class);
        when(other.getPatientId()).thenReturn(UUID.randomUUID());
        when(encounters.findById(encounterId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, encounterId, staff, false, null,
            List.of(new PrescriptionItemInput(drug, "1", "1", 1, DrugRoute.ORAL, null, null)))))
            .isInstanceOf(PrescriptionEncounterMismatchException.class);
    }
}
```

Kiracı kalıbı Task 7 notundaki gibi. `EncounterRepository.findById` filtreli; yoksa `EncounterNotFoundException`.

`CancelPrescriptionUseCaseTest`: `ACTIVE` reçete → `CANCELLED` + `PrescriptionCancelledEvent(id)` yayınlanır; `CANCELLED` reçete → `PrescriptionNotCancellableConflictException`, olay yok.

Entegrasyon testi `prescriptionWithoutEncounterGivesStockAfterTarbilConfirmation`: tenantA'da hasta + bağlı kalem (stok 10) → `POST /api/v1/prescriptions` (encounterId yok, `tarbilDiseaseId`, kalemde `inventoryItemId`, `stockQuantity: 2`) → 201; stok 10; `tarbil_submission` satırı `document_type = PRESCRIPTION`, `source_kind = PRESCRIPTION`; eklenti listesi (`GET /api/v1/tarbil-extension/submissions`) bu aktarımı içerir → `submitted` MANUAL → stok 8. İkinci reçete aynı şekilde → `POST /{id}/cancel` → 204, bekleyen `CANCELLED`, aktarım `DISMISSED`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest='IssuePrescriptionUseCaseTest,CancelPrescriptionUseCaseTest'`
Expected: FAIL — yeni yapıcılar/kayıtlar yok.

- [ ] **Step 3: Implement**

`Prescription.java`: `encounterId` kolonundan `nullable = false` kaldır; alan ekle `@Column(name = "tarbil_disease_id") private UUID tarbilDiseaseId;`; `issue(...)` imzasına `UUID tarbilDiseaseId` son parametre ekle ve ata; `cancel()` → `public boolean cancel() { if (status != PrescriptionStatus.ACTIVE) return false; status = PrescriptionStatus.CANCELLED; return true; }`.

`PrescriptionItem.java`: alanlar `@Column(name = "inventory_item_id") private UUID inventoryItemId; @Column(name = "stock_quantity") private Integer stockQuantity;`; `add(...)` sonuna `UUID inventoryItemId, Integer stockQuantity` ekle.

`IssuePrescriptionUseCase.java`:

```java
@Service
@RequiredArgsConstructor
public class IssuePrescriptionUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final EncounterRepository encounterRepository;
    private final DomainEventPublisher eventPublisher;

    /** Muayeneli ya da muayenesiz recete (spec 2026-10-09 S5.6); stoktan verilen kalemler TARBIL dusumunu bekler. */
    @Transactional
    public UUID execute(IssuePrescriptionCommand command) {
        for (PrescriptionItemInput item : command.items()) {
            if ((item.inventoryItemId() == null) != (item.stockQuantity() == null)
                || (item.stockQuantity() != null && item.stockQuantity() <= 0)) {
                throw new InvalidPrescriptionItemException();
            }
        }
        List<PrescriptionIssuedEvent.StockItem> stockItems = command.items().stream()
            .filter(i -> i.inventoryItemId() != null)
            .map(i -> new PrescriptionIssuedEvent.StockItem(i.inventoryItemId(), i.stockQuantity()))
            .toList();
        if (!stockItems.isEmpty() && command.tarbilDiseaseId() == null) {
            throw new PrescriptionTarbilDiseaseRequiredException();
        }
        if (command.encounterId() != null) {
            Encounter encounter = encounterRepository.findById(command.encounterId())
                .orElseThrow(() -> new EncounterNotFoundException(command.encounterId()));
            if (!encounter.getPatientId().equals(command.patientId())) {
                throw new PrescriptionEncounterMismatchException();
            }
        }
        Prescription prescription = prescriptionRepository.save(Prescription.issue(
            TenantContext.current(), command.patientId(), command.encounterId(),
            command.prescribingStaffId(), command.controlledSubstance(), command.tarbilDiseaseId()
        ));
        command.items().forEach(item -> prescriptionItemRepository.save(PrescriptionItem.add(
            prescription.getTenantId(), prescription.getId(), item.drugId(), item.dosage(),
            item.frequency(), item.durationDays(), item.route(), item.inventoryItemId(), item.stockQuantity()
        )));
        eventPublisher.publish(new PrescriptionIssuedEvent(prescription.getId(), command.patientId(), command.tarbilDiseaseId(), stockItems));
        return prescription.getId();
    }
}
```

`Prescription.id` `@GeneratedValue` — test olaydaki id'yi karşılaştırdığı için `Prescription.issue` içinde `prescription.id = UUID.randomUUID()` ata ve `@GeneratedValue`'yu kaldır (tablo `id UUID PRIMARY KEY`, varsayılansız; kayıt yolları `save` ile değişmez). `EncounterNotFoundException` adını `ls encounter/domain/exception` ile doğrula.

Hata sınıfları (`encounter/domain/exception`):

```java
public class PrescriptionTarbilDiseaseRequiredException extends DomainException {
    public PrescriptionTarbilDiseaseRequiredException() {
        super("PRESCRIPTION_TARBIL_DISEASE_REQUIRED", "Stoktan verilen ilaç için TARBİL teşhisi seçilmeli");
    }
}
public class PrescriptionEncounterMismatchException extends DomainException {
    public PrescriptionEncounterMismatchException() {
        super("PRESCRIPTION_ENCOUNTER_MISMATCH", "Muayene bu hastaya ait değil");
    }
}
public class InvalidPrescriptionItemException extends DomainException {
    public InvalidPrescriptionItemException() {
        super("INVALID_PRESCRIPTION_ITEM", "Stoktan verilen kalemde stok ürünü ve adet birlikte girilmeli");
    }
}
public class PrescriptionNotCancellableConflictException extends DomainException {
    public PrescriptionNotCancellableConflictException() {
        super("PRESCRIPTION_NOT_CANCELLABLE", "Yalnız aktif reçete iptal edilebilir");
    }
}
```

`CancelPrescriptionUseCase`:

```java
@Service
@RequiredArgsConstructor
public class CancelPrescriptionUseCase {
    private final PrescriptionRepository prescriptionRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID prescriptionId) {
        Prescription p = prescriptionRepository.findById(prescriptionId).orElseThrow(() -> new PrescriptionNotFoundException(prescriptionId));
        if (!p.cancel()) {
            throw new PrescriptionNotCancellableConflictException();
        }
        prescriptionRepository.save(p);
        eventPublisher.publish(new PrescriptionCancelledEvent(prescriptionId));
    }
}
```

`PrescriptionsController`: `issue` → `encounterId` `@NotNull` kaldır; `IssuePrescriptionRequest(@NotNull UUID patientId, UUID encounterId, boolean controlledSubstance, UUID tarbilDiseaseId, @NotEmpty @Valid List<Item> items)`, `Item(... DrugRoute route, UUID inventoryItemId, @Positive Integer stockQuantity)`; `issue` metoduna `@PreAuthorize("hasAnyRole('VET', 'ADMIN')")` (yoksa). Yeni:

```java
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        cancelPrescriptionUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
```

`PrescriptionDetail` → `+ UUID tarbilDiseaseId`; `PrescriptionItemDetail` → `+ UUID inventoryItemId, Integer stockQuantity`; `PrescriptionResponse.from` buna göre.

`PrescriptionStockEventListener` (inventory):

```java
@Component
@RequiredArgsConstructor
class PrescriptionStockEventListener {
    private final PendingDeductionService pending;

    @EventListener
    void onIssued(PrescriptionIssuedEvent event) {
        event.stockItems().forEach(i -> pending.open(i.inventoryItemId(), i.quantity(), StockReferenceType.PRESCRIPTION, event.prescriptionId()));
    }

    @EventListener
    void onCancelled(PrescriptionCancelledEvent event) {
        pending.cancel(StockReferenceType.PRESCRIPTION, event.prescriptionId());
    }
}
```

`PrescriptionTarbilEventListener` (integration):

```java
@Component
@RequiredArgsConstructor
class PrescriptionTarbilEventListener {
    private final QueueTarbilSyncUseCase queue;

    @EventListener
    void onIssued(PrescriptionIssuedEvent event) {
        if (!event.stockItems().isEmpty()) {
            queue.queuePrescription(event.prescriptionId(), event.patientId(), event.tarbilDiseaseId());
        }
    }

    @EventListener
    void onCancelled(PrescriptionCancelledEvent event) {
        queue.dismissPrescription(event.prescriptionId(), "Reçete iptal edildi");
    }
}
```

`QueueTarbilSyncUseCase` ekleri:

```java
    @Transactional
    public UUID queuePrescription(UUID prescriptionId, UUID patientId, UUID tarbilDiseaseId) {
        return tarbilSyncLogRepository.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, prescriptionId)
            .map(TarbilSubmission::getId)
            .orElseGet(() -> tarbilSyncLogRepository.save(TarbilSubmission.queuePrescription(
                TenantContext.current(), patientId, TarbilSourceKind.PRESCRIPTION, prescriptionId, tarbilDiseaseId)).getId());
    }

    /** Iptal edilen recetenin bekleyen aktarimi; gonderilmis aktarima dokunulmaz (TARBIL'de iptal hekimde). */
    @Transactional
    public void dismissPrescription(UUID prescriptionId, String reason) {
        tarbilSyncLogRepository.findByDocumentTypeAndSourceId(TarbilDocumentType.PRESCRIPTION, prescriptionId)
            .filter(s -> s.getStatus() == TarbilSyncStatus.PENDING)
            .ifPresent(s -> {
                s.dismiss(null, reason, Instant.now());
                tarbilSyncLogRepository.save(s);
            });
    }
```

(`dismissed_by_staff_id` nullable — V68 — sistem iptali `null` personelle yazılır.)

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -q test -Dtest='IssuePrescriptionUseCaseTest,CancelPrescriptionUseCaseTest,*Prescription*Test,TarbilExtensionSecurityIntegrationTest,ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src && git commit -m "feat(recete): muayenesiz recete, TARBIL teshisi, stoktan ver ve iptal"
```

---

### Task 9: Eklenti görünümü — reçete aktarımları

**Files:**
- Modify: `integration/tarbil/application/TarbilSubmissionAssembler.java`, `TarbilSubmissionView` (`grep -rn "record TarbilSubmissionView" backend/src/main`), `api/dto/TarbilSubmissionResponse.java`
- Modify: `integration/tarbil/application/ListPendingSubmissionsUseCase.java` yalnız `isVisible` kullanıyorsa değişmez
- Test: `backend/src/test/java/com/vetos/modules/integration/tarbil/application/TarbilSubmissionAssemblerTest.java` (yoksa oluştur)

**Interfaces:**
- Consumes: `TarbilStockDeductionPort.listForReference`, `PatientLookupPort.findTarbilProfile`, `TarbilDiseaseRepository.findById` (`grep -rn "interface TarbilDiseaseRepository" backend/src/main`).
- Produces: `TarbilSubmissionView`'ın SONUNA `PrescriptionPayload prescription` alanı; `record PrescriptionPayload(String sourceKind, UUID tarbilDiseaseId, String tarbilDiseaseName, LocalDate treatmentStartDate, List<Line> lines)`, `record Line(String itemName, String tarbilProductName, String lotNumber, int quantity)`. Aşıda `prescription = null`; reçetede aşı alanları `vaccineName = "Reçete"`, `vaccineKey = ""`, diğer aşı alanları `null`.

Kurallar: `PRESCRIPTION` aktarımı `PENDING` iken ancak en az bir `PENDING` bekleyen satırı varsa görünür (`isVisible`); `lines` yalnız `PENDING` satırlar. `treatmentStartDate` = aktarımın `queuedAt` tarihi (Europe/Istanbul).

- [ ] **Step 1: Write the failing test**

```java
@ExtendWith(MockitoExtension.class)
class TarbilSubmissionAssemblerTest {
    @Mock private VaccinationLookupPort vaccinations;
    @Mock private PatientLookupPort patients;
    @Mock private TarbilValueMappingRepository mappings;
    @Mock private InventoryItemLookupPort inventoryItems;
    @Mock private TarbilStockDeductionPort deductions;
    @Mock private TarbilDiseaseRepository diseases;

    private TarbilSubmissionAssembler assembler() {
        return new TarbilSubmissionAssembler(vaccinations, patients, mappings, inventoryItems, deductions, diseases);
    }

    @Test
    void assemblesAPrescriptionWithItsPendingLines() {
        UUID tenant = UUID.randomUUID();
        UUID disease = UUID.randomUUID();
        TarbilSubmission s = TarbilSubmission.queuePrescription(tenant, UUID.randomUUID(), TarbilSourceKind.ENCOUNTER, UUID.randomUUID(), disease);
        when(deductions.listForReference(StockReferenceType.ENCOUNTER, s.getSourceId())).thenReturn(List.of(
            new PendingDeductionLine(UUID.randomUUID(), UUID.randomUUID(), "Synulox", "SYNULOX 250 MG", "S1", 2, PendingDeductionStatus.PENDING, null),
            new PendingDeductionLine(UUID.randomUUID(), UUID.randomUUID(), "Metacam", "METACAM", "M7", 1, PendingDeductionStatus.DEDUCTED, 1)));
        when(diseases.findById(disease)).thenReturn(Optional.of(TarbilDisease.of(disease, null, "Pnömoni", true, 1)));
        when(patients.findTarbilProfile(s.getPatientId())).thenReturn(Optional.empty());

        TarbilSubmissionView v = assembler().assemble(s).orElseThrow();

        assertThat(v.documentType()).isEqualTo(TarbilDocumentType.PRESCRIPTION);
        assertThat(v.prescription().tarbilDiseaseName()).isEqualTo("Pnömoni");
        assertThat(v.prescription().lines()).containsExactly(new TarbilSubmissionView.PrescriptionPayload.Line("Synulox", "SYNULOX 250 MG", "S1", 2));
        assertThat(assembler().isVisible(s)).isTrue();
    }

    @Test
    void prescriptionWithNothingPendingIsHidden() {
        TarbilSubmission s = TarbilSubmission.queuePrescription(UUID.randomUUID(), UUID.randomUUID(), TarbilSourceKind.PRESCRIPTION, UUID.randomUUID(), null);
        when(deductions.listForReference(StockReferenceType.PRESCRIPTION, s.getSourceId())).thenReturn(List.of());

        assertThat(assembler().isVisible(s)).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=TarbilSubmissionAssemblerTest`
Expected: FAIL — yapıcı/alan yok.

- [ ] **Step 3: Implement**

`TarbilSubmissionAssembler`:

```java
    private final TarbilStockDeductionPort stockDeductionPort;
    private final TarbilDiseaseRepository diseaseRepository;

    private List<PendingDeductionLine> pendingLines(TarbilSubmission log) {
        StockReferenceType ref = log.sourceKindOrDefault().stockReference();
        if (log.getDocumentType() != TarbilDocumentType.PRESCRIPTION || ref == null) return List.of();
        return stockDeductionPort.listForReference(ref, log.getSourceId()).stream()
            .filter(l -> l.status() == PendingDeductionStatus.PENDING).toList();
    }

    boolean isVisible(TarbilSubmission log) {
        if (log.getStatus() != TarbilSyncStatus.PENDING) return true;
        if (log.getDocumentType() == TarbilDocumentType.PRESCRIPTION) return !pendingLines(log).isEmpty();
        return liveVaccination(log).isPresent();
    }

    Optional<TarbilSubmissionView> assemble(TarbilSubmission log) {
        if (log.getDocumentType() == TarbilDocumentType.PRESCRIPTION) {
            return Optional.of(assemblePrescription(log));
        }
        // ... mevcut asi govdesi; yapici cagrisinin sonuna `null` (prescription) eklenir
    }

    private TarbilSubmissionView assemblePrescription(TarbilSubmission log) {
        Optional<PatientTarbilProfile> patient = patientLookupPort.findTarbilProfile(log.getPatientId());
        String speciesMapping = patient.map(PatientTarbilProfile::speciesId)
            .flatMap(sid -> mappingRepository.findByTenantIdAndKindAndVetlyKey(log.getTenantId(), TarbilMappingKind.SPECIES, sid.toString()))
            .map(TarbilValueMapping::getTarbilFields).orElse(null);
        var payload = new TarbilSubmissionView.PrescriptionPayload(
            log.sourceKindOrDefault().name(), log.getTarbilDiseaseId(),
            log.getTarbilDiseaseId() == null ? null : diseaseRepository.findById(log.getTarbilDiseaseId()).map(TarbilDisease::getName).orElse(null),
            LocalDate.ofInstant(log.getQueuedAt(), ZoneId.of("Europe/Istanbul")),
            pendingLines(log).stream().map(l -> new TarbilSubmissionView.PrescriptionPayload.Line(l.itemName(), l.tarbilProductName(), l.lotNumber(), l.quantity())).toList());
        return new TarbilSubmissionView(
            log.getId(), log.getSourceId(), log.getStatus(),
            patient.map(PatientTarbilProfile::name).orElse("—"),
            patient.map(PatientTarbilProfile::microchipNumber).orElse(null),
            patient.map(PatientTarbilProfile::passportNumber).orElse(null),
            patient.map(PatientTarbilProfile::speciesId).orElse(null),
            patient.map(PatientTarbilProfile::speciesName).orElse(null),
            patient.map(PatientTarbilProfile::breedName).orElse(null),
            patient.map(PatientTarbilProfile::sex).map(Enum::name).orElse(null),
            patient.map(PatientTarbilProfile::birthDate).orElse(null),
            "Reçete", null, null, null,
            log.getSubmittedAt(), log.getConfirmationMethod(), log.getTarbilReference(),
            "", null, speciesMapping, log.getDocumentType(), payload);
    }
```

`TarbilSubmissionView`'a sonda `PrescriptionPayload prescription` ve iç kayıtları ekle; `TarbilSubmissionResponse`'a aynı alanı (`prescription`) geçir. `administeredDate` alanı reçetede `null` olur — `TarbilSubmissionResponse`/eklenti bunu kabul etmeli (Task 12).

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -q test -Dtest='TarbilSubmissionAssemblerTest,TarbilExtensionSecurityIntegrationTest,ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./mvnw -q test > "$TEMP/plan-a-backend.log" 2>&1; echo exit=$?; grep -E "Tests run:.*Fail|ERROR\]   " "$TEMP/plan-a-backend.log" | head`
Expected: `exit=0`.

- [ ] **Step 6: Commit**

```bash
git add backend/src && git commit -m "feat(tarbil): eklenti icin recete aktarimi gorunumu"
```

---

### Task 10: Frontend — muayene kartı ve stok sayfası bekleyen listesi

**Files:**
- Modify: `backend/.../inventory/api/dto/InventoryItemResponse.java` + `InventoryItemDetail` (`+ String tarbilProductName`) — `grep -rn "new InventoryItemDetail(" backend/src/main` ile kuran yerleri güncelle
- Modify: `frontend/src/api/inventoryApi.ts`, `frontend/src/api/tarbilApi.ts`
- Create: `frontend/src/pages/encounter/TarbilDeductionCard.tsx`, `TarbilDeductionCard.module.css`, `frontend/src/pages/encounter/TarbilDiseaseSelect.tsx`
- Create: `frontend/src/pages/inventory/PendingDeductionsPanel.tsx`, `PendingDeductionsPanel.module.css`
- Modify: `frontend/src/pages/encounter/EncounterPage.tsx` (MaterialsUsedCard'ın altına, muayene `FINALIZED`/`AMENDED` iken), `frontend/src/pages/inventory/InventoryPage.tsx` (TarbilStockSyncPanel'in üstüne)
- Test: `frontend/src/pages/encounter/TarbilDeductionCard.test.tsx`, `frontend/src/pages/inventory/PendingDeductionsPanel.test.tsx` (mevcut test altyapısıyla — `ls frontend/src/pages/inventory/*.test.tsx` ile örnek al; yoksa `npm run build` + `npm run lint` doğrulaması)

**Interfaces:**
- Consumes: Task 6/7 uçları.
- Produces (`tarbilApi`):

```ts
export interface TarbilDiseaseOption { id: string; parentId: string | null; name: string; path: string; selectable: boolean }
export interface EncounterDeductionLine { itemName: string; lotNumber: string | null; quantity: number; status: 'PENDING' | 'DEDUCTED' | 'CANCELLED'; settledQuantity: number | null }
export interface EncounterDeduction { lines: EncounterDeductionLine[]; submissionId: string | null; submissionStatus: 'PENDING' | 'SUBMITTED' | 'DISMISSED' | null }
listDiseases: () => apiClient.get<TarbilDiseaseOption[]>('/api/v1/tarbil/diseases'),
getEncounterDeduction: (encounterId: string) => apiClient.get<EncounterDeduction>(`/api/v1/tarbil/encounters/${encounterId}/deduction`),
queueEncounterDeduction: (encounterId: string, tarbilDiseaseId: string) =>
  apiClient.post<{ submissionId: string }>(`/api/v1/tarbil/encounters/${encounterId}/deduction`, { tarbilDiseaseId }),
```

(`inventoryApi`):

```ts
export interface PendingDeduction { id: string; inventoryItemId: string; itemName: string; lotNumber: string | null; quantity: number; referenceType: 'ENCOUNTER' | 'VACCINATION' | 'PRESCRIPTION'; referenceId: string; createdAt: string }
listPendingDeductions: () => apiClient.get<PendingDeduction[]>('/api/v1/inventory/pending-deductions'),
settleVetlyOnly: (referenceType: string, referenceId: string) =>
  apiClient.post<void>(`/api/v1/inventory/pending-deductions/${referenceType}/${referenceId}/settle-vetly-only`),
```

`InventoryItem` arayüzüne `tarbilProductName: string | null`.

Kart durumları (`TarbilDeductionCard`):
- `lines.length === 0` → kart hiç çizilmez.
- Bekleyen var, aktarım yok → "Bu muayenede TARBİL'e bağlı N ilaç kullanıldı — TARBİL düşümü bekliyor." + `TarbilDiseaseSelect` (yalnız `selectable` olanlar, `path` metniyle) + **TARBİL'den stok düş** (teşhis seçilmeden pasif) + **Yalnız Vetly'den düş** (`window.confirm` YOK; ikinci tıklamayla onaylayan iki adımlı düğme: "Emin misiniz? Yalnız Vetly'den düş").
- Bekleyen var, aktarım `PENDING` → "TARBİL'e aktarılmayı bekliyor — eklentiden TARBİL'e aktarın." + Yalnız Vetly'den düş.
- Bekleyen yok, en az bir `DEDUCTED` → "TARBİL'den düşüldü ✓" (satırlar: ad · seri · adet).
- Her işlemden sonra kart yeniden yüklenir.

`PendingDeductionsPanel`: başlık "TARBİL düşümü bekleyen N kalem"; satır: kalem adı, seri, adet, kaynak (`ENCOUNTER`→"Muayene", `VACCINATION`→"Aşı", `PRESCRIPTION`→"Reçete"), tarih (`tr-TR`), **Yalnız Vetly'den düş** (iki adımlı). Liste boşsa panel çizilmez. Yalnız `VET`/`ADMIN` düğmeyi görür.

- [ ] **Step 1: Write the failing test(s)** — örnek (`TarbilDeductionCard.test.tsx`, vitest + Testing Library; altyapı yoksa bu adımı `npm run build` RED/GREEN'ine çevir ve ledger'a Ruling yaz):

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { tarbilApi } from '../../api/tarbilApi';
import { TarbilDeductionCard } from './TarbilDeductionCard';

vi.mock('../../api/tarbilApi');

it('queues the encounter after a disease is chosen', async () => {
  vi.mocked(tarbilApi.getEncounterDeduction).mockResolvedValue({
    lines: [{ itemName: 'Synulox', lotNumber: 'S1', quantity: 2, status: 'PENDING', settledQuantity: null }],
    submissionId: null, submissionStatus: null,
  });
  vi.mocked(tarbilApi.listDiseases).mockResolvedValue([{ id: 'd1', parentId: null, name: 'Pnömoni', path: 'Solunum › Pnömoni', selectable: true }]);
  vi.mocked(tarbilApi.queueEncounterDeduction).mockResolvedValue({ submissionId: 's1' });

  render(<TarbilDeductionCard encounterId="e1" canAct />);

  const button = await screen.findByRole('button', { name: "TARBİL'den stok düş" });
  expect(button).toBeDisabled();
  await userEvent.selectOptions(screen.getByLabelText('TARBİL teşhisi'), 'd1');
  await userEvent.click(button);
  await waitFor(() => expect(tarbilApi.queueEncounterDeduction).toHaveBeenCalledWith('e1', 'd1'));
});

it('renders nothing without TARBIL-linked lines', async () => {
  vi.mocked(tarbilApi.getEncounterDeduction).mockResolvedValue({ lines: [], submissionId: null, submissionStatus: null });
  const { container } = render(<TarbilDeductionCard encounterId="e1" canAct />);
  await waitFor(() => expect(tarbilApi.getEncounterDeduction).toHaveBeenCalled());
  expect(container).toBeEmptyDOMElement();
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/pages/encounter/TarbilDeductionCard.test.tsx`
Expected: FAIL — bileşen yok.

- [ ] **Step 3: Implement** — `TarbilDiseaseSelect`:

```tsx
import { useEffect, useState } from 'react';
import { tarbilApi, TarbilDiseaseOption } from '../../api/tarbilApi';
import { FieldWrap, Select } from '../../components/ui/Field';

export function TarbilDiseaseSelect({ value, onChange }: { value: string; onChange: (id: string) => void }) {
  const [options, setOptions] = useState<TarbilDiseaseOption[]>([]);
  useEffect(() => {
    tarbilApi.listDiseases().then((all) => setOptions(all.filter((d) => d.selectable))).catch(() => setOptions([]));
  }, []);
  return (
    <FieldWrap label="TARBİL teşhisi">
      <Select aria-label="TARBİL teşhisi" value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Seçiniz</option>
        {options.map((d) => (
          <option key={d.id} value={d.id}>{d.path}</option>
        ))}
      </Select>
    </FieldWrap>
  );
}
```

`TarbilDeductionCard`:

```tsx
import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { inventoryApi } from '../../api/inventoryApi';
import { EncounterDeduction, tarbilApi } from '../../api/tarbilApi';
import { Button } from '../../components/ui/Button';
import encounterStyles from './EncounterPage.module.css';
import styles from './TarbilDeductionCard.module.css';
import { TarbilDiseaseSelect } from './TarbilDiseaseSelect';

/** Muayene tamamlaninca TARBIL'e bagli ilaclarin dusumu (spec 2026-10-09 S5.1). */
export function TarbilDeductionCard({ encounterId, canAct }: { encounterId: string; canAct: boolean }) {
  const [data, setData] = useState<EncounterDeduction | null>(null);
  const [disease, setDisease] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmVetlyOnly, setConfirmVetlyOnly] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => tarbilApi.getEncounterDeduction(encounterId).then(setData).catch(() => setData(null));
  useEffect(() => { void load(); }, [encounterId]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!data || data.lines.length === 0) return null;
  const pending = data.lines.filter((l) => l.status === 'PENDING');
  const deducted = data.lines.filter((l) => l.status === 'DEDUCTED');

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      setConfirmVetlyOnly(false);
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'İşlem yapılamadı');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={encounterStyles.card}>
      <div className={encounterStyles.cardTitle}>TARBİL stok düşümü</div>
      {error && <div className={encounterStyles.aiError}>{error}</div>}
      {pending.length === 0 ? (
        <div className={styles.done}>TARBİL'den düşüldü ✓</div>
      ) : data.submissionStatus === 'PENDING' ? (
        <div className={styles.note}>TARBİL'e aktarılmayı bekliyor — eklentiden "TARBİL'e aktar" deyin.</div>
      ) : (
        <div className={styles.note}>Bu muayenede TARBİL'e bağlı {pending.length} ilaç kullanıldı — TARBİL düşümü bekliyor.</div>
      )}
      <ul className={styles.lines}>
        {(pending.length > 0 ? pending : deducted).map((l, i) => (
          <li key={i}>{l.itemName} · Seri {l.lotNumber ?? '—'} · {l.settledQuantity ?? l.quantity} adet</li>
        ))}
      </ul>
      {canAct && pending.length > 0 && (
        <div className={styles.actions}>
          {data.submissionStatus !== 'PENDING' && (
            <>
              <TarbilDiseaseSelect value={disease} onChange={setDisease} />
              <Button variant="primary" disabled={!disease || busy} onClick={() => run(() => tarbilApi.queueEncounterDeduction(encounterId, disease))}>
                TARBİL'den stok düş
              </Button>
            </>
          )}
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => (confirmVetlyOnly ? run(() => inventoryApi.settleVetlyOnly('ENCOUNTER', encounterId)) : setConfirmVetlyOnly(true))}
          >
            {confirmVetlyOnly ? "Emin misiniz? Yalnız Vetly'den düş" : "Yalnız Vetly'den düş"}
          </Button>
        </div>
      )}
    </div>
  );
}
```

`TarbilDeductionCard.module.css`: `.note`, `.done` (yeşil ton `var(--color-success-700)`), `.lines` (liste boşluk), `.actions` (flex, gap 8px, wrap) — mevcut `MaterialsUsedCard.module.css`/token adlarıyla.

`EncounterPage.tsx` 527. satır civarı:

```tsx
          <MaterialsUsedCard encounterId={encounter.id} readOnly={isReadOnly} />
          {(encounter.status === 'FINALIZED' || encounter.status === 'AMENDED') && (
            <TarbilDeductionCard encounterId={encounter.id} canAct={session?.role === 'VET' || session?.role === 'ADMIN'} />
          )}
```

`PendingDeductionsPanel` aynı kalıpla (`inventoryApi.listPendingDeductions`, iki adımlı düğme, `onSettled` → `InventoryPage`'in `load`'u). `InventoryPage.tsx`: `{canTarbilSync && <TarbilStockSyncPanel onApplied={load} />}` satırının üstüne `<PendingDeductionsPanel canAct={role === 'VET' || role === 'ADMIN'} onSettled={load} />` (rol değişkeninin adını dosyadan al).

- [ ] **Step 4: Run tests and build**

Run: `cd frontend && npx vitest run src/pages/encounter src/pages/inventory && npm run build && npm run lint`
Expected: PASS, build ve lint temiz. Backend: `./mvnw -q test -Dtest='*InventoryItem*Test'` PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src backend/src && git commit -m "feat(web): muayene TARBIL dusum karti ve stok sayfasinda bekleyen dusumler"
```

---

### Task 11: Frontend — reçete kartı (muayenesiz, stoktan ver, teşhis, iptal)

**Files:**
- Modify: `frontend/src/api/clinicalApi.ts`
- Create: `frontend/src/pages/encounter/StockGivePicker.tsx`
- Modify: `frontend/src/pages/encounter/PrescriptionCard.tsx`, `PrescriptionCard.module.css`
- Modify: `frontend/src/pages/patients/PatientDetailPage.tsx`
- Test: `frontend/src/pages/encounter/PrescriptionCard.test.tsx`

**Interfaces:**
- Produces (`clinicalApi`):
  - `Prescription.encounterId: string | null`, `+ tarbilDiseaseId: string | null`; `PrescriptionItem + inventoryItemId: string | null, stockQuantity: number | null`
  - `PrescriptionItemPayload + inventoryItemId?: string | null, stockQuantity?: number | null`
  - `IssuePrescriptionPayload.encounterId: string | null`, `+ tarbilDiseaseId: string | null`
  - `cancelPrescription: (id: string) => apiClient.post<void>(`/api/v1/prescriptions/${id}/cancel`)`
- `PrescriptionCard` props: `{ encounterId: string | null; patientId: string; readOnly: boolean }` — `encounterId === null` → hastanın muayenesiz reçeteleri (`p.encounterId === null`) listelenir, başlık "Muayenesiz reçeteler".

Davranış:
- Her kalem satırında "Kliniğin stoğundan ver" onay kutusu; işaretlenince `StockGivePicker` (TARBİL'e bağlı, stokta > 0, SKT'si geçmemiş kalemler: `i.tarbilProductName && i.quantityOnHand > 0`) + "Adet" (min 1, varsayılan 1).
- En az bir kalem stoktan veriliyorsa formda `TarbilDiseaseSelect` görünür ve zorunludur (seçilmeden "Reçete Oluştur" pasif).
- Listede stoktan verilen kalem: "· Stoktan: <ad> × <adet>".
- `ACTIVE` reçetede (salt-okunur değilse) "İptal et" — iki adımlı; ikinci adım metni: "Emin misiniz? Stoktan verilenler TARBİL'de düşüldüyse reçeteyi TARBİL'de de iptal edin."

- [ ] **Step 1: Write the failing test**

```tsx
it('requires a TARBIL diagnosis once an item is given from stock', async () => {
  vi.mocked(clinicalApi.listDrugs).mockResolvedValue([{ id: 'dr1', name: 'Amoksisilin', activeIngredient: null, isControlled: false, interactingDrugIds: [] }]);
  vi.mocked(clinicalApi.listPrescriptionsByPatient).mockResolvedValue([]);
  vi.mocked(inventoryApi.list).mockResolvedValue([{ id: 'i1', name: 'Synulox', category: 'İlaç', skuBarcode: null, quantityOnHand: 5, reorderThreshold: 0, belowReorderThreshold: false, expiryDate: null, lotNumber: 'S1', unitCost: null, tarbilProductName: 'SYNULOX' }]);
  vi.mocked(tarbilApi.listDiseases).mockResolvedValue([{ id: 'd1', parentId: null, name: 'Pnömoni', path: 'Pnömoni', selectable: true }]);
  vi.mocked(clinicalApi.issuePrescription).mockResolvedValue('p1');

  render(<PrescriptionCard encounterId={null} patientId="pt1" readOnly={false} />);
  await userEvent.click(await screen.findByRole('button', { name: '+ Yeni Reçete' }));
  await userEvent.type(screen.getByPlaceholderText('Örn. 10 mg/kg'), '1 tb');
  await userEvent.type(screen.getByPlaceholderText('Örn. Günde 2 kez'), '2x1');
  await userEvent.click(screen.getByLabelText('Kliniğin stoğundan ver'));
  await userEvent.selectOptions(await screen.findByLabelText('Stok ürünü'), 'i1');

  const submit = screen.getByRole('button', { name: 'Reçete Oluştur' });
  expect(submit).toBeDisabled();
  await userEvent.selectOptions(screen.getByLabelText('TARBİL teşhisi'), 'd1');
  await userEvent.click(submit);

  await waitFor(() => expect(clinicalApi.issuePrescription).toHaveBeenCalledWith(expect.objectContaining({
    encounterId: null, tarbilDiseaseId: 'd1',
    items: [expect.objectContaining({ inventoryItemId: 'i1', stockQuantity: 1 })],
  })));
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/pages/encounter/PrescriptionCard.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implement** — `StockGivePicker`:

```tsx
import { useEffect, useState } from 'react';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { FieldWrap, Select } from '../../components/ui/Field';

const todayIso = () => new Date().toISOString().slice(0, 10);

/** Receteden stoktan verilecek TARBIL'e bagli kalem (spec 2026-10-09 S5.6). */
export function StockGivePicker({ value, onChange }: { value: string | null; onChange: (id: string | null) => void }) {
  const [items, setItems] = useState<InventoryItem[]>([]);
  useEffect(() => {
    const today = todayIso();
    inventoryApi.list()
      .then((all) => setItems(all.filter((i) => i.tarbilProductName && i.quantityOnHand > 0 && !(i.expiryDate && i.expiryDate < today))))
      .catch(() => setItems([]));
  }, []);
  return (
    <FieldWrap label="Stok ürünü">
      <Select aria-label="Stok ürünü" value={value ?? ''} onChange={(e) => onChange(e.target.value || null)}>
        <option value="">Seçiniz</option>
        {items.map((i) => (
          <option key={i.id} value={i.id}>{`${i.name} · Seri ${i.lotNumber ?? '—'} · ${i.quantityOnHand} adet`}</option>
        ))}
      </Select>
    </FieldWrap>
  );
}
```

`PrescriptionCard.tsx` değişiklikleri:
- Props `encounterId: string | null`; `reload` filtresi `p.encounterId === encounterId`.
- Durum: `const [tarbilDiseaseId, setTarbilDiseaseId] = useState('');`; `type ItemDraft = PrescriptionItemPayload & { giveFromStock: boolean }`; `emptyItem` → `{ ..., inventoryItemId: null, stockQuantity: null, giveFromStock: false }`.
- Kalem satırına:

```tsx
<label className={styles.stockToggle}>
  <input
    type="checkbox"
    aria-label="Kliniğin stoğundan ver"
    checked={item.giveFromStock}
    onChange={(e) => updateItem(index, e.target.checked ? { giveFromStock: true, stockQuantity: 1 } : { giveFromStock: false, inventoryItemId: null, stockQuantity: null })}
  />
  Kliniğin stoğundan ver
</label>
{item.giveFromStock && (
  <>
    <StockGivePicker value={item.inventoryItemId ?? null} onChange={(id) => updateItem(index, { inventoryItemId: id })} />
    <FieldWrap label="Adet">
      <Input type="number" min={1} value={item.stockQuantity ?? 1} onChange={(e) => updateItem(index, { stockQuantity: Number(e.target.value) })} required />
    </FieldWrap>
  </>
)}
```

- Formda: `const givesStock = items.some((it) => it.giveFromStock);` → `{givesStock && <TarbilDiseaseSelect value={tarbilDiseaseId} onChange={setTarbilDiseaseId} />}`; gönder düğmesi `disabled={busy || (givesStock && (!tarbilDiseaseId || items.some((it) => it.giveFromStock && !it.inventoryItemId)))}`.
- `handleSubmit`: `items.map(({ giveFromStock, ...it }) => giveFromStock ? it : { ...it, inventoryItemId: null, stockQuantity: null })`, payload `{ patientId, encounterId, controlledSubstance, tarbilDiseaseId: givesStock ? tarbilDiseaseId : null, items }`.
- Liste satırına stoktan verilen kalem ve iki adımlı "İptal et" (`clinicalApi.cancelPrescription(p.id)` → `reload()`).

`PatientDetailPage.tsx`: reçeteler bölümünde (VET/ADMIN için) `<PrescriptionCard encounterId={null} patientId={patient.id} readOnly={!canStartEncounter} />` — mevcut reçete listesinin üstüne; muayeneli reçeteler mevcut listede kalır.

- [ ] **Step 4: Run tests and build**

Run: `cd frontend && npx vitest run src/pages/encounter src/pages/patients && npm run build && npm run lint`
Expected: PASS, build ve lint temiz.

- [ ] **Step 5: Commit**

```bash
git add frontend/src && git commit -m "feat(web): muayenesiz recete, kliniğin stogundan ver, TARBIL teshisi ve iptal"
```

---

### Task 12: Eklenti — reçete aktarımlarını göster, elle onay

**Files:**
- Modify: `extension/src/shared/types.ts`, `extension/src/sidepanel/SubmissionCard.tsx`, `extension/src/sidepanel/PendingList.tsx` (gerekirse)
- Test: `extension/src/sidepanel/SubmissionCard.test.tsx` (yoksa oluştur; mevcut sidepanel test kalıbına bak: `ls extension/src/sidepanel/*.test.tsx`)

**Interfaces:**
- Consumes: Task 9 `prescription` alanı.
- Produces (`types.ts`):

```ts
export interface PrescriptionPayload {
  sourceKind: 'ENCOUNTER' | 'PRESCRIPTION';
  tarbilDiseaseId: string | null;
  tarbilDiseaseName: string | null;
  treatmentStartDate: string;
  lines: { itemName: string; tarbilProductName: string | null; lotNumber: string | null; quantity: number }[];
}
// Submission: vaccineName: string; administeredDate: string | null; prescription?: PrescriptionPayload | null;
```

Davranış: `documentType === 'PRESCRIPTION'` kartı başlığı "Reçete — stoktan düşüm"; teşhis adı ve satırlar (ürün · seri · adet) listelenir; **TARBİL'e aktar** düğmesi yerine bilgi satırı "Otomatik doldurma yakında — TARBİL'de 'Stoktan Düşerek İlaç Reçetesi Ekle' ile girip onaylayın." ve mevcut **TARBİL'de yaptım** (MANUAL) düğmesi. Aşı kartı değişmez.

- [ ] **Step 1: Write the failing test**

```tsx
it('shows a prescription with its lines and only manual confirmation', () => {
  render(<SubmissionCard submission={{ ...baseSubmission, documentType: 'PRESCRIPTION', vaccineName: 'Reçete', administeredDate: null,
    prescription: { sourceKind: 'ENCOUNTER', tarbilDiseaseId: 'd1', tarbilDiseaseName: 'Pnömoni', treatmentStartDate: '2026-10-09',
      lines: [{ itemName: 'Synulox', tarbilProductName: 'SYNULOX', lotNumber: 'S1', quantity: 2 }] } }} {...handlers} />);

  expect(screen.getByText(/Pnömoni/)).toBeInTheDocument();
  expect(screen.getByText(/SYNULOX · Seri S1 · 2 adet/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /TARBİL'e aktar/ })).toBeNull();
  expect(screen.getByRole('button', { name: /TARBİL'de yaptım/ })).toBeInTheDocument();
});
```

(`baseSubmission` ve `handlers` dosyadaki mevcut kalıptan; SubmissionCard'ın gerçek prop adlarını dosyadan al.)

- [ ] **Step 2: Run to verify it fails**

Run: `cd extension && npx vitest run src/sidepanel/SubmissionCard.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implement** — `SubmissionCard.tsx` içinde `const isPrescription = submission.documentType === 'PRESCRIPTION';` ile dallan; reçete gövdesi:

```tsx
{isPrescription && submission.prescription && (
  <div className="submission-prescription">
    <div className="muted">Teşhis: {submission.prescription.tarbilDiseaseName ?? '—'}</div>
    <ul>
      {submission.prescription.lines.map((l, i) => (
        <li key={i}>{`${l.tarbilProductName ?? l.itemName} · Seri ${l.lotNumber ?? '—'} · ${l.quantity} adet`}</li>
      ))}
    </ul>
    <div className="muted">Otomatik doldurma yakında — TARBİL'de "Stoktan Düşerek İlaç Reçetesi Ekle" ile girip onaylayın.</div>
  </div>
)}
```

ve aktar düğmesini `{!isPrescription && (...)}` ile sar. Aşıya özgü alanları (`administeredDate`, `vaccineName`) okuyan yerlerde `null` güvenliği.

- [ ] **Step 4: Run tests and build**

Run: `cd extension && npx tsc --noEmit -p . && npx vitest run && npm run build:dev`
Expected: tsc temiz, tüm testler PASS, build `exit 0`.

- [ ] **Step 5: Commit**

```bash
git add extension/src && git commit -m "feat(eklenti): recete aktarimlarini goster, elle onay"
```

---

### Task 13: Dokümanlar ve tam doğrulama

**Files:**
- Modify: `docs/api-conventions.md` (yeni uçlar: pending-deductions list/settle-vetly-only, tarbil encounters deduction GET/POST, prescriptions cancel, prescriptions POST değişen gövde, submitted gövdesine `lines`)
- Modify: `docs/superpowers/specs/2026-10-09-tarbil-stok-dusum-senkronu-design.md` §8'e "Plan A tamamlandı; Plan B = eklenti reçete formu doldurma + başarı satırları" notu

- [ ] **Step 1: Docs** — `docs/api-conventions.md`'de TARBİL bölümüne (105. satır civarı) şu satırları ekle:

```markdown
- `POST /api/v1/tarbil-extension/submissions/{id}/submitted` — gövde `{method, tarbilReference?, lines?[{productName, serialNumber, quantity}]}` (en çok 50). İlk onayda bekleyen stok düşer: MANUAL ya da `lines` yoksa planlanan adetler, aksi halde seri no eşleşen satırlar bildirilen adetle (spec 2026-10-09 §5.3).
- `GET /api/v1/tarbil/encounters/{id}/deduction` — `{lines[{itemName, lotNumber, quantity, status, settledQuantity}], submissionId, submissionStatus}`.
- `POST /api/v1/tarbil/encounters/{id}/deduction` (VET/ADMIN) — `{tarbilDiseaseId}` → 201 `{submissionId}`; bekleyen yoksa ya da muayene taslaksa 409.
- `GET /api/v1/inventory/pending-deductions` — bekleyen TARBİL düşümleri.
- `POST /api/v1/inventory/pending-deductions/{ENCOUNTER|VACCINATION|PRESCRIPTION}/{referenceId}/settle-vetly-only` (VET/ADMIN) → 204; bekleyen aktarım "Yalnız Vetly'den düşüldü" ile reddedilir.
- `POST /api/v1/prescriptions` — `encounterId` isteğe bağlı; `tarbilDiseaseId`; kalemde `inventoryItemId` + `stockQuantity` (birlikte). Stoktan verilen kalem varsa teşhis zorunlu (422).
- `POST /api/v1/prescriptions/{id}/cancel` (VET/ADMIN) → 204; aktif değilse 409.
```

- [ ] **Step 2: Full verification**

Run: `cd backend && ./mvnw -q test > "$TEMP/plan-a-final.log" 2>&1; echo exit=$?` ; `cd frontend && npx vitest run && npm run build && npm run lint` ; `cd extension && npx vitest run && npm run build:dev`
Expected: hepsi `exit 0` / PASS.

- [ ] **Step 3: Commit**

```bash
git add docs && git commit -m "docs: TARBIL stok dusum senkronu uclari"
```

---

## Plan B (bu planın dışında, canlı inceleme sonrası yazılacak)

1. Canlı: "Stoktan Düşerek İlaç Reçetesi Ekle" formunda hayvan seçildikten sonra ürün bölümünün yapısı; "Reçeteyi Onayla" sonrası başarı ekranı.
2. Eklenti: `prescriptionStockReceipt` sayfa türü, doldurma adımları (sınıf, tarih, teşhis, hayvan, stok satırları), başarı yakalama + `lines` ile AUTO onay; aşı akışında Onayla başarısına kaydedilmiş ürün satırlarını (`readSavedProductSerials`) `lines` olarak ekleme.
