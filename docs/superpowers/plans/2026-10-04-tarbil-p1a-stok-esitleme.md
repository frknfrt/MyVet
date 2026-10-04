# TARBİL P1a — Stok Eşitleme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hekim TARBİL'in aşı ve ilaç stok sayfalarında tek tıkla mevcut stoğu Vetly'ye gönderir; Vetly Stok sayfasında farkları görüp seçtiği satırları Vetly stoğuna (lot, son kullanma tarihi, miktar) işler.

**Architecture:** `inventory` modülü stok kalemlerine TARBİL bağlantı alanları ekler ve `TarbilStockSyncPort` açar (listele / TARBİL satırından oluştur / miktarı eşitle). `integration/tarbil` modülü eklentinin gönderdiği anlık görüntüyü (`tarbil_stock_snapshot`) saklar, kalemlerle eşleştirip durum hesaplar ve işleme uçlarını sunar. Eklenti stok sayfalarında "Ara"ya basıp tabloyu tek sayfaya alır, okur ve Vetly'ye gönderir; TARBİL'de başka hiçbir şeye basmaz.

**Tech Stack:** Java 21, Spring Boot 3, Spring Modulith, JPA, Flyway, PostgreSQL, JUnit 5 + Mockito + MockMvc; Chrome MV3, TypeScript, Vitest + jsdom; React 18 (frontend).

**Spec:** `docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md` §13 (P1a), §2 (kurallar), §5.2 (stok alanları).

## Global Constraints

- Doğrudan `main` üzerinde çalışılır (dal açılmaz); push yok.
- TARBİL'de yalnız okuma: eklenti yalnız izin listesindeki "Ara" butonlarına basar ve sayfa boyutunu değiştirir. Onayla/Reddet/Kaydet/çıkış yok. (Spec §2, §13)
- `tarbil_system` değerleri: `HBSAPP_VACCINE`, `VETILAC_MEDICINE`. (Spec §13)
- Satır durumları: `NEW`, `QUANTITY_DIFFERS`, `MATCHED`, `APPLIED`. Eşleşme: normalize lot aynı **ve** ürün adı kalemin `tarbil_product_name`'i ya da adıyla aynı; lotsuz satır `NEW`. (Spec §13)
- Anlık görüntü en çok 500 satır. (Spec §13)
- Web uçları `/api/v1/tarbil/**` → ADMIN, VET (mevcut sınıf düzeyi `@PreAuthorize`). Şube: kullanıcının `branchIds[0]`. (Spec §13)
- `integration/tarbil` → `inventory`'ye yalnız `modules.inventory::domain` (port) üzerinden erişir. (Spec §5.5)
- Kiracı: `tarbil_stock_snapshot*` tabloları `@TenantId` dışında, kiracı elle filtrelenir (diğer `tarbil_*` tablolarıyla aynı karar).
- Backend testleri docker-compose Postgres (localhost:5433) ister; hekimin 8080 backend'ine dokunulmaz.
- Kullanıcıya görünen metinler Türkçe; konsola veri yazılmaz.

## Review Focus

1. **Aynı lot iki kez seçilip işlendiğinde** (TARBİL'de aynı ürün+lot iki satırda) ikinci satır yeni kalem açmamalı, ilkini güncellemeli → Task 2 testi `should_notCreateDuplicate_when_sameLotAppliedTwiceInOneCall`.
2. **Başka kiracının görüntüsünü işleme** denemesi 404 olmalı, hiçbir stok değişmemeli → Task 2 testi `should_throwNotFound_when_snapshotBelongsToAnotherTenant`.
3. **Zaten işlenmiş satırın tekrar işlenmesi** (çift tıklama) stoğu ikinci kez artırmamalı → Task 2 testi `should_skipAlreadyAppliedLines`.
4. **TARBİL tablosunun başlıkları değişirse** eklenti boş/yanlış veri göndermemeli, kart "tablo tanınmadı" demeli → Task 3 testi `returns no rows when a required column is missing` ve Task 4 testi `warns instead of uploading when the table is not recognized`.
5. **Ondalıklı açılmış miktar ve gg.aa.yyyy tarihleri** doğru çevrilmeli (`9,99` → 9.99; `31.01.2027` → `2027-01-31`) → Task 3 testi `parses Turkish dates and numbers`.

---

## Dosya Yapısı

**Backend**
- `backend/src/main/resources/db/migration/V64__inventory_tarbil_fields.sql`, `V65__tarbil_stock_snapshot.sql`
- `modules/inventory/domain/`: `InventoryItem.java` (alanlar), `StockReferenceType.java` (+`TARBIL_SYNC`), `TarbilStockSyncPort.java`, `InventoryStockView.java`, `NewTarbilStockItem.java`, `TarbilStockLink.java` (yeni)
- `modules/inventory/infrastructure/TarbilStockSyncAdapter.java` (yeni)
- `modules/integration/tarbil/domain/`: `TarbilStockSystem.java`, `TarbilStockSyncStatus.java`, `TarbilStockSnapshot.java`, `TarbilStockSnapshotLine.java`, `TarbilStockSnapshotRepository.java`, `StockMatchKey.java`, `exception/TarbilStockSnapshotNotFoundException.java`, `exception/InvalidTarbilStockSnapshotException.java`, `exception/TarbilStockBranchMissingException.java` (yeni)
- `modules/integration/tarbil/infrastructure/persistence/`: `TarbilStockSnapshotJpaRepository.java`, `TarbilStockSnapshotLineJpaRepository.java`, `TarbilStockSnapshotRepositoryAdapter.java` (yeni)
- `modules/integration/tarbil/application/`: `StockSyncMatcher.java`, `RecordStockSnapshotUseCase.java`, `GetStockSyncViewUseCase.java`, `ApplyStockSyncUseCase.java`, `dto/StockSnapshotLineInput.java`, `dto/StockSyncLineView.java`, `dto/StockSyncView.java` (yeni)
- `modules/integration/tarbil/api/`: `TarbilExtensionController.java`, `TarbilController.java` (değişir), `dto/StockSnapshotRequest.java`, `dto/StockSnapshotLineRequest.java`, `dto/StockSnapshotResponse.java`, `dto/StockSyncResponse.java`, `dto/StockSyncLineResponse.java`, `dto/ApplyStockSyncRequest.java`, `dto/ApplyStockSyncResponse.java` (yeni)
- `modules/integration/tarbil/package-info.java` (bağımlılık)

**Eklenti (`extension/src/`)**: `tarbil/selectors/stock.ts`, `tarbil/selectors/shared.ts` (pageKind), `tarbil/selectors/allowlist.ts`, `tarbil/selectors/index.ts`, `tarbil/page/telerik.ts` (`showAllRows`), `tarbil/page/ops.ts` (`loadStockTable`), `tarbil/steps/stockRows.ts`, `tarbil/pages/stockSync.ts`, `tarbil/core/views.ts`, `tarbil/content.ts`, `shared/types.ts`, `shared/messages.ts`, `background/vetlyApi.ts`, `background/router.ts`, `public/manifest.json`

**Frontend**: `frontend/src/api/tarbilApi.ts`, `frontend/src/pages/inventory/TarbilStockSyncPanel.tsx` (yeni), `frontend/src/pages/inventory/InventoryPage.tsx`

Kısaltmalar: `INV=backend/src/main/java/com/vetos/modules/inventory`, `TB=backend/src/main/java/com/vetos/modules/integration/tarbil`, `TINV=backend/src/test/java/com/vetos/modules/inventory`, `TTB=backend/src/test/java/com/vetos/modules/integration/tarbil`.

---

### Task 1: Inventory — TARBİL alanları ve `TarbilStockSyncPort`

**Files:**
- Create: `backend/src/main/resources/db/migration/V64__inventory_tarbil_fields.sql`, `$INV/domain/TarbilStockSyncPort.java`, `$INV/domain/InventoryStockView.java`, `$INV/domain/NewTarbilStockItem.java`, `$INV/domain/TarbilStockLink.java`, `$INV/infrastructure/TarbilStockSyncAdapter.java`
- Modify: `$INV/domain/InventoryItem.java`, `$INV/domain/StockReferenceType.java`
- Test: `$TINV/infrastructure/TarbilStockSyncAdapterTest.java`

**Interfaces:**
- Produces:
  - `InventoryItem.linkTarbil(String system, String productName, String presentation)`; getter'lar `getTarbilSystem/getTarbilProductName/getTarbilPresentation/getUnit`
  - `StockReferenceType.TARBIL_SYNC`
  - `record InventoryStockView(UUID id, String name, String lotNumber, LocalDate expiryDate, int quantityOnHand, String tarbilProductName)`
  - `record NewTarbilStockItem(String tarbilSystem, String productName, String presentation, String category, String lotNumber, LocalDate expiryDate, int quantity)`
  - `record TarbilStockLink(String tarbilSystem, String productName, String presentation)`
  - `interface TarbilStockSyncPort { List<InventoryStockView> listForBranch(UUID branchId); UUID createFromTarbil(UUID branchId, NewTarbilStockItem item, UUID referenceId); void syncFromTarbil(UUID inventoryItemId, int targetQuantity, TarbilStockLink link, UUID referenceId); }`

- [ ] **Step 1: Başarısız testi yaz**

`$TINV/infrastructure/TarbilStockSyncAdapterTest.java`:

```java
package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockLink;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TarbilStockSyncAdapterTest {

    @Mock private InventoryItemRepository items;
    @Mock private StockMovementRepository movements;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID ref = UUID.randomUUID();

    @BeforeEach
    void setTenant() {
        TenantContext.set(tenantId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private TarbilStockSyncAdapter adapter() {
        return new TarbilStockSyncAdapter(items, movements);
    }

    @Test
    void should_createLinkedItemWithInMovement_when_newTarbilLine() {
        when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter().createFromTarbil(branchId,
            new NewTarbilStockItem("VETILAC_MEDICINE", "Test İlaç", "Kutu", "İlaç", "L-1", LocalDate.of(2027, 1, 31), 5), ref);

        ArgumentCaptor<InventoryItem> item = ArgumentCaptor.forClass(InventoryItem.class);
        verify(items).save(item.capture());
        assertThat(item.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(item.getValue().getBranchId()).isEqualTo(branchId);
        assertThat(item.getValue().getName()).isEqualTo("Test İlaç");
        assertThat(item.getValue().getLotNumber()).isEqualTo("L-1");
        assertThat(item.getValue().getQuantityOnHand()).isEqualTo(5);
        assertThat(item.getValue().getTarbilSystem()).isEqualTo("VETILAC_MEDICINE");
        assertThat(item.getValue().getTarbilProductName()).isEqualTo("Test İlaç");
        assertThat(item.getValue().getTarbilPresentation()).isEqualTo("Kutu");
        assertThat(item.getValue().getUnit()).isEqualTo("ADET");
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(movement.capture());
        assertThat(movement.getValue().getMovementType()).isEqualTo(StockMovementType.IN);
        assertThat(movement.getValue().getQuantity()).isEqualTo(5);
        assertThat(movement.getValue().getReferenceType()).isEqualTo(StockReferenceType.TARBIL_SYNC);
        assertThat(movement.getValue().getReferenceId()).isEqualTo(ref);
    }

    @Test
    void should_recordOutMovement_when_tarbilHasLessThanVetly() {
        UUID id = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(tenantId, branchId, "Aşı", "Aşı", null, 8, 0, null, "L-2", null);
        when(items.findById(id)).thenReturn(Optional.of(item));

        adapter().syncFromTarbil(id, 5, new TarbilStockLink("HBSAPP_VACCINE", "Aşı X", "Flakon"), ref);

        assertThat(item.getQuantityOnHand()).isEqualTo(5);
        assertThat(item.getTarbilProductName()).isEqualTo("Aşı X");
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(movement.capture());
        assertThat(movement.getValue().getMovementType()).isEqualTo(StockMovementType.OUT);
        assertThat(movement.getValue().getQuantity()).isEqualTo(3);
        verify(items).save(item);
    }

    @Test
    void should_onlyLink_when_quantitiesAlreadyEqual() {
        UUID id = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(tenantId, branchId, "Aşı", "Aşı", null, 5, 0, null, "L-3", null);
        when(items.findById(id)).thenReturn(Optional.of(item));

        adapter().syncFromTarbil(id, 5, new TarbilStockLink("HBSAPP_VACCINE", "Aşı Y", null), ref);

        verify(movements, never()).save(any());
        assertThat(item.getTarbilSystem()).isEqualTo("HBSAPP_VACCINE");
    }
}
```

- [ ] **Step 2: Testi çalıştır, başarısız olduğunu gör**

Run: `cd backend && ./mvnw -q test -Dtest=TarbilStockSyncAdapterTest > /tmp/p1a1.log 2>&1; echo exit=$?; grep -E "symbol:" /tmp/p1a1.log | sort -u | head -5`
Expected: `exit=1`; `TarbilStockSyncAdapter`, `NewTarbilStockItem`, `TarbilStockLink`, `getTarbilSystem` bulunamıyor.

- [ ] **Step 3: Migration, alanlar ve port**

`backend/src/main/resources/db/migration/V64__inventory_tarbil_fields.sql`:

```sql
-- TARBIL P1a (spec 2026-10-04 S13): stok kaleminin TARBIL'deki karsiligi ve birimi.
ALTER TABLE inventory_items ADD COLUMN tarbil_system TEXT;
ALTER TABLE inventory_items ADD COLUMN tarbil_product_name TEXT;
ALTER TABLE inventory_items ADD COLUMN tarbil_presentation TEXT;
ALTER TABLE inventory_items ADD COLUMN unit TEXT NOT NULL DEFAULT 'ADET';
```

`$INV/domain/InventoryItem.java` içinde `unitCost` alanının hemen altına ekle:

```java

    /** TARBIL eslesmesi (spec 2026-10-04 S13): HBSAPP_VACCINE | VETILAC_MEDICINE; TARBIL'den gelmeyen kalemde null. */
    @Column(name = "tarbil_system")
    private String tarbilSystem;

    @Column(name = "tarbil_product_name")
    private String tarbilProductName;

    @Column(name = "tarbil_presentation")
    private String tarbilPresentation;

    @Column(nullable = false)
    private String unit = "ADET";
```

ve `updateDetails` metodundan sonra ekle:

```java

    public void linkTarbil(String system, String productName, String presentation) {
        this.tarbilSystem = system;
        this.tarbilProductName = productName;
        this.tarbilPresentation = presentation;
    }
```

`$INV/domain/StockReferenceType.java`:

```java
package com.vetos.modules.inventory.domain;

public enum StockReferenceType { ENCOUNTER, PURCHASE_ORDER, MANUAL, TARBIL_SYNC }
```

`$INV/domain/InventoryStockView.java`:

```java
package com.vetos.modules.inventory.domain;

import java.time.LocalDate;
import java.util.UUID;

/** TARBIL stok esitlemesi icin salt-okunur stok kalemi gorunumu. */
public record InventoryStockView(UUID id, String name, String lotNumber, LocalDate expiryDate, int quantityOnHand, String tarbilProductName) {}
```

`$INV/domain/NewTarbilStockItem.java`:

```java
package com.vetos.modules.inventory.domain;

import java.time.LocalDate;

public record NewTarbilStockItem(
    String tarbilSystem, String productName, String presentation, String category,
    String lotNumber, LocalDate expiryDate, int quantity
) {}
```

`$INV/domain/TarbilStockLink.java`:

```java
package com.vetos.modules.inventory.domain;

public record TarbilStockLink(String tarbilSystem, String productName, String presentation) {}
```

`$INV/domain/TarbilStockSyncPort.java`:

```java
package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.UUID;

/**
 * integration/tarbil icin (spec 2026-10-04 S13): TARBIL stok anlik goruntusunu Vetly stoguna esitler.
 * Kiraci TenantContext'ten gelir (cagiran kimligi dogrulanmis bir istek icindedir).
 */
public interface TarbilStockSyncPort {
    List<InventoryStockView> listForBranch(UUID branchId);

    /** Yeni kalem + IN hareketi (TARBIL_SYNC). */
    UUID createFromTarbil(UUID branchId, NewTarbilStockItem item, UUID referenceId);

    /** Miktari TARBIL'deki degere getirir (fark kadar IN/OUT, TARBIL_SYNC) ve TARBIL baglantisini yazar. */
    void syncFromTarbil(UUID inventoryItemId, int targetQuantity, TarbilStockLink link, UUID referenceId);
}
```

`$INV/infrastructure/TarbilStockSyncAdapter.java`:

```java
package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockLink;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import com.vetos.modules.inventory.domain.exception.InventoryItemNotFoundException;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilStockSyncAdapter implements TarbilStockSyncPort {

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryStockView> listForBranch(UUID branchId) {
        return items.findByBranchId(branchId).stream()
            .map(i -> new InventoryStockView(i.getId(), i.getName(), i.getLotNumber(), i.getExpiryDate(), i.getQuantityOnHand(), i.getTarbilProductName()))
            .toList();
    }

    @Override
    @Transactional
    public UUID createFromTarbil(UUID branchId, NewTarbilStockItem n, UUID referenceId) {
        InventoryItem item = InventoryItem.create(
            TenantContext.current(), branchId, n.productName(), n.category(), null,
            n.quantity(), 0, n.expiryDate(), n.lotNumber(), null
        );
        item.linkTarbil(n.tarbilSystem(), n.productName(), n.presentation());
        InventoryItem saved = items.save(item);
        if (n.quantity() > 0) {
            movements.save(StockMovement.record(saved.getTenantId(), saved.getId(), StockMovementType.IN, n.quantity(),
                StockReferenceType.TARBIL_SYNC, referenceId));
        }
        return saved.getId();
    }

    @Override
    @Transactional
    public void syncFromTarbil(UUID inventoryItemId, int targetQuantity, TarbilStockLink link, UUID referenceId) {
        InventoryItem item = items.findById(inventoryItemId).orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));
        item.linkTarbil(link.tarbilSystem(), link.productName(), link.presentation());
        int delta = targetQuantity - item.getQuantityOnHand();
        if (delta != 0) {
            item.adjustQuantity(delta);
            movements.save(StockMovement.record(item.getTenantId(), inventoryItemId,
                delta > 0 ? StockMovementType.IN : StockMovementType.OUT, Math.abs(delta), StockReferenceType.TARBIL_SYNC, referenceId));
        }
        items.save(item);
    }
}
```

**Not:** `InventoryItemNotFoundException`'ın paketini `grep -rn "class InventoryItemNotFoundException" backend/src/main` ile doğrula; farklıysa import'u ona göre düzelt.

- [ ] **Step 4: Testi çalıştır, geçtiğini gör**

Run: `cd backend && ./mvnw -q test -Dtest='TarbilStockSyncAdapterTest,StockDeductionAdapterTest' > /tmp/p1a1.log 2>&1; echo exit=$?`
Expected: `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src
git commit -m "feat(inventory): TARBIL baglanti alanlari ve TarbilStockSyncPort (olustur / miktari esitle)"
```

---

### Task 2: TARBİL — stok anlık görüntüsü, eşleşme ve işleme uçları

**Files:**
- Create: `backend/src/main/resources/db/migration/V65__tarbil_stock_snapshot.sql`; `$TB/domain/{TarbilStockSystem,TarbilStockSyncStatus,TarbilStockSnapshot,TarbilStockSnapshotLine,TarbilStockSnapshotRepository,StockMatchKey}.java`; `$TB/domain/exception/{TarbilStockSnapshotNotFoundException,InvalidTarbilStockSnapshotException,TarbilStockBranchMissingException}.java`; `$TB/infrastructure/persistence/{TarbilStockSnapshotJpaRepository,TarbilStockSnapshotLineJpaRepository,TarbilStockSnapshotRepositoryAdapter}.java`; `$TB/application/{StockSyncMatcher,RecordStockSnapshotUseCase,GetStockSyncViewUseCase,ApplyStockSyncUseCase}.java`; `$TB/application/dto/{StockSnapshotLineInput,StockSyncLineView,StockSyncView}.java`; `$TB/api/dto/{StockSnapshotRequest,StockSnapshotLineRequest,StockSnapshotResponse,StockSyncResponse,StockSyncLineResponse,ApplyStockSyncRequest,ApplyStockSyncResponse}.java`
- Modify: `$TB/package-info.java`, `$TB/api/TarbilExtensionController.java`, `$TB/api/TarbilController.java`
- Test: `$TTB/application/{StockSyncMatcherTest,RecordStockSnapshotUseCaseTest,ApplyStockSyncUseCaseTest}.java`; `backend/src/test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java` (ekleme)

**Interfaces:**
- Consumes: `TarbilStockSyncPort`, `InventoryStockView`, `NewTarbilStockItem`, `TarbilStockLink` (Task 1).
- Produces:
  - `enum TarbilStockSystem { HBSAPP_VACCINE, VETILAC_MEDICINE }`, `enum TarbilStockSyncStatus { NEW, QUANTITY_DIFFERS, MATCHED, APPLIED }`
  - `POST /api/v1/tarbil-extension/stock-snapshots` `{system, lines[{productName, presentation, lotNumber, expiryDate, quantity, openedQuantity}]}` → 201 `{snapshotId}`
  - `GET /api/v1/tarbil/stock-sync?system=` → `{snapshotId|null, system, takenAt|null, lines[{lineId, productName, presentation, lotNumber, expiryDate, tarbilQuantity, openedQuantity, status, inventoryItemId|null, vetlyQuantity|null}]}`
  - `POST /api/v1/tarbil/stock-sync/{snapshotId}/apply` `{lineIds:[uuid]}` → `{applied}`

- [ ] **Step 1: Başarısız testleri yaz**

`$TTB/application/StockSyncMatcherTest.java`:

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;
import com.vetos.modules.inventory.domain.InventoryStockView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockSyncMatcherTest {

    private static TarbilStockSnapshotLine line(String product, String lot, int qty) {
        return TarbilStockSnapshotLine.of(UUID.randomUUID(), UUID.randomUUID(), 1, product, null, lot, null, qty, null);
    }

    private static InventoryStockView item(String name, String lot, int qty, String tarbilName) {
        return new InventoryStockView(UUID.randomUUID(), name, lot, null, qty, tarbilName);
    }

    @Test
    void should_beNew_when_noItemHasThatLot() {
        assertThat(StockSyncMatcher.match(line("Drontal", "L1", 3), List.of(item("Drontal", "L2", 3, null))).status())
            .isEqualTo(TarbilStockSyncStatus.NEW);
    }

    @Test
    void should_match_when_lotAndNameEqualIgnoringCaseAndSpaces() {
        var m = StockSyncMatcher.match(line(" drontal  plus ", "l1", 3), List.of(item("DRONTAL PLUS", "L1", 3, null)));
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.MATCHED);
        assertThat(m.item()).isNotNull();
    }

    @Test
    void should_reportQuantityDifference_when_matchedByTarbilProductName() {
        var m = StockSyncMatcher.match(line("Rabisin", "R9", 10), List.of(item("Kuduz aşısı", "R9", 4, "Rabisin")));
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.QUANTITY_DIFFERS);
    }

    @Test
    void should_beNew_when_lineHasNoLot() {
        assertThat(StockSyncMatcher.match(line("Drontal", null, 3), List.of(item("Drontal", null, 3, null))).status())
            .isEqualTo(TarbilStockSyncStatus.NEW);
    }

    @Test
    void should_beApplied_when_lineWasAlreadyApplied() {
        TarbilStockSnapshotLine l = line("Drontal", "L1", 3);
        l.markApplied(UUID.randomUUID(), Instant.now());
        assertThat(StockSyncMatcher.match(l, List.of()).status()).isEqualTo(TarbilStockSyncStatus.APPLIED);
    }
}
```

`$TTB/application/RecordStockSnapshotUseCaseTest.java`:

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.StockSnapshotLineInput;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilStockSnapshotException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordStockSnapshotUseCaseTest {

    @Mock private TarbilStockSnapshotRepository repository;
    private final UUID tenantId = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void should_saveNumberedTrimmedLines() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new RecordStockSnapshotUseCase(repository).execute(tenantId, UUID.randomUUID(), TarbilStockSystem.VETILAC_MEDICINE, List.of(
            new StockSnapshotLineInput("  Drontal ", " Kutu ", " L1 ", LocalDate.of(2027, 1, 31), 3, new BigDecimal("0.5")),
            new StockSnapshotLineInput("Rabisin", null, "", null, 0, null)));

        ArgumentCaptor<TarbilStockSnapshot> snapshot = ArgumentCaptor.forClass(TarbilStockSnapshot.class);
        verify(repository).save(snapshot.capture());
        assertThat(snapshot.getValue().getTenantId()).isEqualTo(tenantId);
        ArgumentCaptor<List<TarbilStockSnapshotLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(repository).saveLines(lines.capture());
        assertThat(lines.getValue()).extracting(TarbilStockSnapshotLine::getLineNo).containsExactly(1, 2);
        assertThat(lines.getValue().get(0).getProductName()).isEqualTo("Drontal");
        assertThat(lines.getValue().get(0).getLotNumber()).isEqualTo("L1");
        assertThat(lines.getValue().get(1).getLotNumber()).isNull();
    }

    @Test
    void should_reject_when_tooManyLinesOrBlankName() {
        var useCase = new RecordStockSnapshotUseCase(repository);
        List<StockSnapshotLineInput> tooMany = Collections.nCopies(501, new StockSnapshotLineInput("A", null, "L", null, 1, null));

        assertThatThrownBy(() -> useCase.execute(tenantId, UUID.randomUUID(), TarbilStockSystem.HBSAPP_VACCINE, tooMany))
            .isInstanceOf(InvalidTarbilStockSnapshotException.class);
        assertThatThrownBy(() -> useCase.execute(tenantId, UUID.randomUUID(), TarbilStockSystem.HBSAPP_VACCINE,
            List.of(new StockSnapshotLineInput(" ", null, "L", null, 1, null))))
            .isInstanceOf(InvalidTarbilStockSnapshotException.class);
    }
}
```

`$TTB/application/ApplyStockSyncUseCaseTest.java`:

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilStockSnapshotNotFoundException;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.TarbilStockLink;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyStockSyncUseCaseTest {

    @Mock private TarbilStockSnapshotRepository repository;
    @Mock private TarbilStockSyncPort port;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    private TarbilStockSnapshot snapshot(TarbilStockSystem system) {
        return TarbilStockSnapshot.take(tenantId, system, UUID.randomUUID(), Instant.now());
    }

    private TarbilStockSnapshotLine line(int no, String product, String lot, int qty) {
        return TarbilStockSnapshotLine.of(null, tenantId, no, product, "Kutu", lot, null, qty, null);
    }

    private ApplyStockSyncUseCase useCase() {
        return new ApplyStockSyncUseCase(repository, port);
    }

    @Test
    void should_createNewItemsAndSyncDifferentOnes() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshot s = snapshot(TarbilStockSystem.VETILAC_MEDICINE);
        TarbilStockSnapshotLine fresh = line(1, "Drontal", "L1", 3);
        TarbilStockSnapshotLine differs = line(2, "Rabisin", "R9", 10);
        UUID existing = UUID.randomUUID();
        when(repository.findById(snapshotId)).thenReturn(Optional.of(s));
        when(repository.findLines(snapshotId)).thenReturn(List.of(fresh, differs));
        when(port.listForBranch(branchId)).thenReturn(List.of(new InventoryStockView(existing, "Rabisin", "R9", null, 4, null)));
        UUID created = UUID.randomUUID();
        when(port.createFromTarbil(eq(branchId), any(), eq(snapshotId))).thenReturn(created);

        int applied = useCase().execute(tenantId, branchId, snapshotId, List.of(fresh.getId(), differs.getId()));

        assertThat(applied).isEqualTo(2);
        ArgumentCaptor<NewTarbilStockItem> item = ArgumentCaptor.forClass(NewTarbilStockItem.class);
        verify(port).createFromTarbil(eq(branchId), item.capture(), eq(snapshotId));
        assertThat(item.getValue().category()).isEqualTo("İlaç");
        assertThat(item.getValue().tarbilSystem()).isEqualTo("VETILAC_MEDICINE");
        verify(port).syncFromTarbil(eq(existing), eq(10), any(TarbilStockLink.class), eq(snapshotId));
        assertThat(fresh.getAppliedInventoryItemId()).isEqualTo(created);
        assertThat(differs.getAppliedInventoryItemId()).isEqualTo(existing);
    }

    @Test
    void should_notCreateDuplicate_when_sameLotAppliedTwiceInOneCall() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshotLine first = line(1, "Drontal", "L1", 3);
        TarbilStockSnapshotLine second = line(2, "Drontal", "L1", 5);
        when(repository.findById(snapshotId)).thenReturn(Optional.of(snapshot(TarbilStockSystem.VETILAC_MEDICINE)));
        when(repository.findLines(snapshotId)).thenReturn(List.of(first, second));
        when(port.listForBranch(branchId)).thenReturn(List.of());
        UUID created = UUID.randomUUID();
        when(port.createFromTarbil(eq(branchId), any(), eq(snapshotId))).thenReturn(created);

        useCase().execute(tenantId, branchId, snapshotId, List.of(first.getId(), second.getId()));

        verify(port, times(1)).createFromTarbil(any(), any(), any());
        verify(port).syncFromTarbil(eq(created), eq(5), any(TarbilStockLink.class), eq(snapshotId));
    }

    @Test
    void should_skipAlreadyAppliedLines() {
        UUID snapshotId = UUID.randomUUID();
        TarbilStockSnapshotLine done = line(1, "Drontal", "L1", 3);
        done.markApplied(UUID.randomUUID(), Instant.now());
        when(repository.findById(snapshotId)).thenReturn(Optional.of(snapshot(TarbilStockSystem.HBSAPP_VACCINE)));
        when(repository.findLines(snapshotId)).thenReturn(List.of(done));
        when(port.listForBranch(branchId)).thenReturn(List.of());

        assertThat(useCase().execute(tenantId, branchId, snapshotId, List.of(done.getId()))).isZero();
        verify(port, never()).createFromTarbil(any(), any(), any());
        verify(port, never()).syncFromTarbil(any(), any(Integer.class), any(), any());
    }

    @Test
    void should_throwNotFound_when_snapshotBelongsToAnotherTenant() {
        UUID snapshotId = UUID.randomUUID();
        when(repository.findById(snapshotId)).thenReturn(Optional.of(
            TarbilStockSnapshot.take(UUID.randomUUID(), TarbilStockSystem.HBSAPP_VACCINE, UUID.randomUUID(), Instant.now())));

        assertThatThrownBy(() -> useCase().execute(tenantId, branchId, snapshotId, List.of(UUID.randomUUID())))
            .isInstanceOf(TarbilStockSnapshotNotFoundException.class);
        verify(port, never()).listForBranch(any());
    }
}
```

**Not:** Mockito `syncFromTarbil(any(), any(Integer.class), ...)` ilkel `int` parametrede `anyInt()` gerektirir; derleme hatası verirse `org.mockito.ArgumentMatchers.anyInt()` kullan.

`backend/src/test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java`: `tearDown` döngüsünde `tarbil_value_mapping` silme satırından hemen sonra ekle:

```java
            jdbcTemplate.update("DELETE FROM tarbil_stock_snapshot_line WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM tarbil_stock_snapshot WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM stock_movements WHERE tenant_id = ?", t);
            jdbcTemplate.update("DELETE FROM inventory_items WHERE tenant_id = ?", t);
```

ve sınıfa ekle (import'lara `com.jayway.jsonpath.JsonPath` ekle):

```java
    @Test
    void stockSnapshotUploadedByExtensionCanBeAppliedFromWeb() throws Exception {
        UUID branchA = inRootSession(() -> branchRepository.save(Branch.create(tenantA, "Stok Şubesi")).getId());
        String jwt = jwtTokenProvider.generateToken(staffA, tenantA, List.of(branchA), "VET");
        String body = "{\"system\":\"VETILAC_MEDICINE\",\"lines\":[{\"productName\":\"Test İlaç\",\"presentation\":\"Kutu\","
            + "\"lotNumber\":\"LOT-1\",\"expiryDate\":\"2027-01-31\",\"quantity\":3,\"openedQuantity\":null}]}";

        mockMvc.perform(post("/api/v1/tarbil-extension/stock-snapshots")
                .header("Authorization", "Bearer " + tokenA).contentType("application/json").content(body))
            .andExpect(status().isCreated());

        String view = mockMvc.perform(get("/api/v1/tarbil/stock-sync?system=VETILAC_MEDICINE").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[0].status").value("NEW"))
            .andReturn().getResponse().getContentAsString();
        String snapshotId = JsonPath.read(view, "$.snapshotId");
        String lineId = JsonPath.read(view, "$.lines[0].lineId");

        mockMvc.perform(post("/api/v1/tarbil/stock-sync/" + snapshotId + "/apply").header("Authorization", "Bearer " + jwt)
                .contentType("application/json").content("{\"lineIds\":[\"" + lineId + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied").value(1));
        mockMvc.perform(get("/api/v1/tarbil/stock-sync?system=VETILAC_MEDICINE").header("Authorization", "Bearer " + jwt))
            .andExpect(jsonPath("$.lines[0].status").value("APPLIED"))
            .andExpect(jsonPath("$.lines[0].vetlyQuantity").value(3));
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM inventory_items WHERE tenant_id = ? AND lot_number = 'LOT-1' AND tarbil_system = 'VETILAC_MEDICINE'",
            Integer.class, tenantA);
        assertThat(count).isEqualTo(1);
    }
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd backend && ./mvnw -q test -Dtest='StockSyncMatcherTest,RecordStockSnapshotUseCaseTest,ApplyStockSyncUseCaseTest' > /tmp/p1a2.log 2>&1; echo exit=$?; grep -E "symbol:" /tmp/p1a2.log | sort -u | head -6`
Expected: `exit=1`; `TarbilStockSnapshotLine`, `StockSyncMatcher`, `RecordStockSnapshotUseCase` vb. bulunamıyor.

- [ ] **Step 3: Migration ve domain**

`backend/src/main/resources/db/migration/V65__tarbil_stock_snapshot.sql`:

```sql
-- TARBIL P1a (spec 2026-10-04 S13): eklentinin TARBIL stok sayfasindan okudugu anlik goruntu.
-- @TenantId DISINDA (diger tarbil_* tablolariyla ayni karar) -- tenant_id elle filtrelenir.
CREATE TABLE tarbil_stock_snapshot (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    tarbil_system      TEXT NOT NULL,
    taken_at           TIMESTAMPTZ NOT NULL,
    taken_by_staff_id  UUID NOT NULL
);
CREATE INDEX idx_tarbil_stock_snapshot_latest ON tarbil_stock_snapshot (tenant_id, tarbil_system, taken_at DESC);

CREATE TABLE tarbil_stock_snapshot_line (
    id                         UUID PRIMARY KEY,
    snapshot_id                UUID NOT NULL REFERENCES tarbil_stock_snapshot (id) ON DELETE CASCADE,
    tenant_id                  UUID NOT NULL,
    line_no                    INT NOT NULL,
    product_name               TEXT NOT NULL,
    presentation               TEXT,
    lot_number                 TEXT,
    expiry_date                DATE,
    quantity                   INT NOT NULL,
    opened_quantity            NUMERIC(12, 3),
    applied_inventory_item_id  UUID,
    applied_at                 TIMESTAMPTZ
);
CREATE INDEX idx_tarbil_stock_snapshot_line_snapshot ON tarbil_stock_snapshot_line (snapshot_id, line_no);
```

`$TB/domain/TarbilStockSystem.java`:

```java
package com.vetos.modules.integration.tarbil.domain;

/** TARBIL'de stok tutan sistem: hbsapp (asi) ya da vetilac (ilac). */
public enum TarbilStockSystem { HBSAPP_VACCINE, VETILAC_MEDICINE }
```

`$TB/domain/TarbilStockSyncStatus.java`:

```java
package com.vetos.modules.integration.tarbil.domain;

public enum TarbilStockSyncStatus { NEW, QUANTITY_DIFFERS, MATCHED, APPLIED }
```

`$TB/domain/StockMatchKey.java`:

```java
package com.vetos.modules.integration.tarbil.domain;

import java.util.Locale;

/** Lot ve urun adini karsilastirma icin normalize eder: bosluklar sadelesir, Turkce buyuk harf. */
public final class StockMatchKey {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private StockMatchKey() {}

    public static String of(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(TR);
    }
}
```

`$TB/domain/TarbilStockSnapshot.java`:

```java
package com.vetos.modules.integration.tarbil.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tarbil_stock_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilStockSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tarbil_system", nullable = false)
    private TarbilStockSystem tarbilSystem;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(name = "taken_by_staff_id", nullable = false)
    private UUID takenByStaffId;

    public static TarbilStockSnapshot take(UUID tenantId, TarbilStockSystem system, UUID staffId, Instant now) {
        TarbilStockSnapshot snapshot = new TarbilStockSnapshot();
        snapshot.tenantId = tenantId;
        snapshot.tarbilSystem = system;
        snapshot.takenByStaffId = staffId;
        snapshot.takenAt = now;
        return snapshot;
    }
}
```

`$TB/domain/TarbilStockSnapshotLine.java`:

```java
package com.vetos.modules.integration.tarbil.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tarbil_stock_snapshot_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilStockSnapshotLine {

    @Id
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "product_name", nullable = false)
    private String productName;

    private String presentation;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "opened_quantity")
    private BigDecimal openedQuantity;

    @Column(name = "applied_inventory_item_id")
    private UUID appliedInventoryItemId;

    @Column(name = "applied_at")
    private Instant appliedAt;

    /** id hemen atanir: satirlar kaydedilmeden once de kimlikleriyle secilebilsin (testler ve eslesme). */
    public static TarbilStockSnapshotLine of(UUID snapshotId, UUID tenantId, int lineNo, String productName, String presentation,
                                             String lotNumber, LocalDate expiryDate, int quantity, BigDecimal openedQuantity) {
        TarbilStockSnapshotLine line = new TarbilStockSnapshotLine();
        line.id = UUID.randomUUID();
        line.snapshotId = snapshotId;
        line.tenantId = tenantId;
        line.lineNo = lineNo;
        line.productName = productName;
        line.presentation = presentation;
        line.lotNumber = lotNumber;
        line.expiryDate = expiryDate;
        line.quantity = quantity;
        line.openedQuantity = openedQuantity;
        return line;
    }

    public boolean isApplied() {
        return appliedInventoryItemId != null;
    }

    public void markApplied(UUID inventoryItemId, Instant now) {
        this.appliedInventoryItemId = inventoryItemId;
        this.appliedAt = now;
    }
}
```

`$TB/domain/TarbilStockSnapshotRepository.java`:

```java
package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilStockSnapshotRepository {
    TarbilStockSnapshot save(TarbilStockSnapshot snapshot);
    void saveLines(List<TarbilStockSnapshotLine> lines);
    TarbilStockSnapshotLine saveLine(TarbilStockSnapshotLine line);
    Optional<TarbilStockSnapshot> findById(UUID id);
    Optional<TarbilStockSnapshot> findLatest(UUID tenantId, TarbilStockSystem system);
    List<TarbilStockSnapshotLine> findLines(UUID snapshotId);
}
```

`$TB/domain/exception/TarbilStockSnapshotNotFoundException.java`:

```java
package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TarbilStockSnapshotNotFoundException extends DomainException {
    public TarbilStockSnapshotNotFoundException(UUID id) {
        super("TARBIL_STOCK_SNAPSHOT_NOT_FOUND", "TARBIL stok goruntusu bulunamadi: " + id);
    }
}
```

`$TB/domain/exception/InvalidTarbilStockSnapshotException.java`:

```java
package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidTarbilStockSnapshotException extends DomainException {
    public InvalidTarbilStockSnapshotException(String reason) {
        super("INVALID_TARBIL_STOCK_SNAPSHOT", "Gecersiz TARBIL stok goruntusu: " + reason);
    }
}
```

`$TB/domain/exception/TarbilStockBranchMissingException.java`:

```java
package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TarbilStockBranchMissingException extends DomainException {
    public TarbilStockBranchMissingException() {
        super("TARBIL_STOCK_BRANCH_MISSING", "Kullanicinin subesi yok; TARBIL stogu esitlenemez");
    }
}
```

**Not:** `DomainException` kurucusunun `(String code, String message)` olduğunu `TarbilSubmissionNotFoundException` ile doğrula (aynı biçim).

- [ ] **Step 4: Kalıcılık**

`$TB/infrastructure/persistence/TarbilStockSnapshotJpaRepository.java`:

```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface TarbilStockSnapshotJpaRepository extends JpaRepository<TarbilStockSnapshot, UUID> {
    Optional<TarbilStockSnapshot> findFirstByTenantIdAndTarbilSystemOrderByTakenAtDesc(UUID tenantId, TarbilStockSystem system);
}
```

`$TB/infrastructure/persistence/TarbilStockSnapshotLineJpaRepository.java`:

```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TarbilStockSnapshotLineJpaRepository extends JpaRepository<TarbilStockSnapshotLine, UUID> {
    List<TarbilStockSnapshotLine> findBySnapshotIdOrderByLineNoAsc(UUID snapshotId);
}
```

`$TB/infrastructure/persistence/TarbilStockSnapshotRepositoryAdapter.java`:

```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilStockSnapshotRepositoryAdapter implements TarbilStockSnapshotRepository {

    private final TarbilStockSnapshotJpaRepository snapshots;
    private final TarbilStockSnapshotLineJpaRepository lines;

    @Override public TarbilStockSnapshot save(TarbilStockSnapshot snapshot) { return snapshots.save(snapshot); }

    @Override public void saveLines(List<TarbilStockSnapshotLine> rows) { lines.saveAll(rows); }

    @Override public TarbilStockSnapshotLine saveLine(TarbilStockSnapshotLine line) { return lines.save(line); }

    @Override public Optional<TarbilStockSnapshot> findById(UUID id) { return snapshots.findById(id); }

    @Override
    public Optional<TarbilStockSnapshot> findLatest(UUID tenantId, TarbilStockSystem system) {
        return snapshots.findFirstByTenantIdAndTarbilSystemOrderByTakenAtDesc(tenantId, system);
    }

    @Override public List<TarbilStockSnapshotLine> findLines(UUID snapshotId) { return lines.findBySnapshotIdOrderByLineNoAsc(snapshotId); }
}
```

**Not:** `TarbilStockSnapshotLine.id` elle atandığı için Spring Data `save()` onu "var olan" sanıp `merge` yapar; bu yeni satırda fazladan bir SELECT demektir, doğruluk sorunu yoktur.

- [ ] **Step 5: Uygulama katmanı**

`$TB/application/dto/StockSnapshotLineInput.java`:

```java
package com.vetos.modules.integration.tarbil.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockSnapshotLineInput(String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                     int quantity, BigDecimal openedQuantity) {}
```

`$TB/application/dto/StockSyncLineView.java`:

```java
package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockSyncLineView(UUID lineId, String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                int tarbilQuantity, BigDecimal openedQuantity, TarbilStockSyncStatus status,
                                UUID inventoryItemId, Integer vetlyQuantity) {}
```

`$TB/application/dto/StockSyncView.java`:

```java
package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** snapshotId null ise bu sistem icin henuz goruntu yok. */
public record StockSyncView(UUID snapshotId, TarbilStockSystem system, Instant takenAt, List<StockSyncLineView> lines) {}
```

`$TB/application/StockSyncMatcher.java`:

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.StockMatchKey;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;
import com.vetos.modules.inventory.domain.InventoryStockView;

import java.util.List;

/** Spec 2026-10-04 S13: lot (normalize) ayni VE urun adi kalemin TARBIL adi ya da adiyla ayni. Lotsuz satir NEW. */
final class StockSyncMatcher {

    record Match(TarbilStockSyncStatus status, InventoryStockView item) {}

    private StockSyncMatcher() {}

    static Match match(TarbilStockSnapshotLine line, List<InventoryStockView> stock) {
        if (line.isApplied()) {
            return new Match(TarbilStockSyncStatus.APPLIED,
                stock.stream().filter(i -> i.id().equals(line.getAppliedInventoryItemId())).findFirst().orElse(null));
        }
        String lot = StockMatchKey.of(line.getLotNumber());
        if (lot.isEmpty()) {
            return new Match(TarbilStockSyncStatus.NEW, null);
        }
        String product = StockMatchKey.of(line.getProductName());
        return stock.stream()
            .filter(i -> lot.equals(StockMatchKey.of(i.lotNumber())))
            .filter(i -> product.equals(StockMatchKey.of(i.tarbilProductName())) || product.equals(StockMatchKey.of(i.name())))
            .findFirst()
            .map(i -> new Match(i.quantityOnHand() == line.getQuantity() ? TarbilStockSyncStatus.MATCHED : TarbilStockSyncStatus.QUANTITY_DIFFERS, i))
            .orElse(new Match(TarbilStockSyncStatus.NEW, null));
    }
}
```

`$TB/application/RecordStockSnapshotUseCase.java`:

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.StockSnapshotLineInput;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilStockSnapshotException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Eklentinin TARBIL stok sayfasindan okudugu tabloyu saklar; Vetly stoguna HENUZ dokunmaz (hekim web'den isler). */
@Service
@RequiredArgsConstructor
public class RecordStockSnapshotUseCase {

    static final int MAX_LINES = 500;

    private final TarbilStockSnapshotRepository repository;

    @Transactional
    public UUID execute(UUID tenantId, UUID staffId, TarbilStockSystem system, List<StockSnapshotLineInput> lines) {
        if (system == null) {
            throw new InvalidTarbilStockSnapshotException("sistem bos");
        }
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new InvalidTarbilStockSnapshotException("satir sayisi 1-" + MAX_LINES + " olmali");
        }
        for (StockSnapshotLineInput l : lines) {
            if (l.productName() == null || l.productName().isBlank()) {
                throw new InvalidTarbilStockSnapshotException("urun adi bos");
            }
            if (l.quantity() < 0) {
                throw new InvalidTarbilStockSnapshotException("miktar negatif");
            }
        }
        TarbilStockSnapshot snapshot = repository.save(TarbilStockSnapshot.take(tenantId, system, staffId, Instant.now()));
        List<TarbilStockSnapshotLine> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            StockSnapshotLineInput l = lines.get(i);
            rows.add(TarbilStockSnapshotLine.of(snapshot.getId(), tenantId, i + 1, l.productName().trim(), trimToNull(l.presentation()),
                trimToNull(l.lotNumber()), l.expiryDate(), l.quantity(), l.openedQuantity()));
        }
        repository.saveLines(rows);
        return snapshot.getId();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
```

`$TB/application/GetStockSyncViewUseCase.java`:

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.StockSyncLineView;
import com.vetos.modules.integration.tarbil.application.dto.StockSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetStockSyncViewUseCase {

    private final TarbilStockSnapshotRepository repository;
    private final TarbilStockSyncPort stockSyncPort;

    @Transactional(readOnly = true)
    public StockSyncView execute(UUID tenantId, UUID branchId, TarbilStockSystem system) {
        Optional<TarbilStockSnapshot> snapshot = repository.findLatest(tenantId, system);
        if (snapshot.isEmpty()) {
            return new StockSyncView(null, system, null, List.of());
        }
        List<InventoryStockView> stock = stockSyncPort.listForBranch(branchId);
        List<StockSyncLineView> lines = repository.findLines(snapshot.get().getId()).stream().map(l -> {
            StockSyncMatcher.Match m = StockSyncMatcher.match(l, stock);
            return new StockSyncLineView(l.getId(), l.getProductName(), l.getPresentation(), l.getLotNumber(), l.getExpiryDate(),
                l.getQuantity(), l.getOpenedQuantity(), m.status(),
                m.item() == null ? null : m.item().id(), m.item() == null ? null : m.item().quantityOnHand());
        }).toList();
        return new StockSyncView(snapshot.get().getId(), system, snapshot.get().getTakenAt(), lines);
    }
}
```

`$TB/application/ApplyStockSyncUseCase.java`:

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilStockSnapshotNotFoundException;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.TarbilStockLink;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Hekimin sectigi goruntu satirlarini Vetly stoguna isler (yeni kalem ya da miktar esitleme). Islenmis satir tekrar islenmez. */
@Service
@RequiredArgsConstructor
public class ApplyStockSyncUseCase {

    private final TarbilStockSnapshotRepository repository;
    private final TarbilStockSyncPort stockSyncPort;

    @Transactional
    public int execute(UUID tenantId, UUID branchId, UUID snapshotId, List<UUID> lineIds) {
        TarbilStockSnapshot snapshot = repository.findById(snapshotId)
            .filter(s -> s.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilStockSnapshotNotFoundException(snapshotId));
        Set<UUID> wanted = new HashSet<>(lineIds == null ? List.of() : lineIds);
        String system = snapshot.getTarbilSystem().name();
        String category = snapshot.getTarbilSystem() == TarbilStockSystem.HBSAPP_VACCINE ? "Aşı" : "İlaç";
        // Ayni cagrida ayni lot iki kez gelirse ikincisi yeni kalem acmasin: yerel liste her islemde guncellenir.
        List<InventoryStockView> stock = new ArrayList<>(stockSyncPort.listForBranch(branchId));
        Instant now = Instant.now();
        int applied = 0;
        for (TarbilStockSnapshotLine line : repository.findLines(snapshotId)) {
            if (!wanted.contains(line.getId()) || line.isApplied()) {
                continue;
            }
            StockSyncMatcher.Match match = StockSyncMatcher.match(line, stock);
            UUID itemId;
            if (match.status() == TarbilStockSyncStatus.NEW) {
                itemId = stockSyncPort.createFromTarbil(branchId, new NewTarbilStockItem(system, line.getProductName(),
                    line.getPresentation(), category, line.getLotNumber(), line.getExpiryDate(), line.getQuantity()), snapshotId);
            } else {
                itemId = match.item().id();
                stockSyncPort.syncFromTarbil(itemId, line.getQuantity(),
                    new TarbilStockLink(system, line.getProductName(), line.getPresentation()), snapshotId);
                stock.remove(match.item());
            }
            stock.add(new InventoryStockView(itemId, line.getProductName(), line.getLotNumber(), line.getExpiryDate(),
                line.getQuantity(), line.getProductName()));
            line.markApplied(itemId, now);
            repository.saveLine(line);
            applied++;
        }
        return applied;
    }
}
```

`$TB/package-info.java`: `allowedDependencies` listesine `"modules.inventory::domain",` ekle (ör. `"modules.tenant::domain",` satırının altına).

- [ ] **Step 6: API**

`$TB/api/dto/StockSnapshotLineRequest.java`:

```java
package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockSnapshotLineRequest(
    @NotBlank @Size(max = 300) String productName,
    @Size(max = 200) String presentation,
    @Size(max = 100) String lotNumber,
    LocalDate expiryDate,
    @Min(0) int quantity,
    BigDecimal openedQuantity
) {}
```

`$TB/api/dto/StockSnapshotRequest.java`:

```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.StockSnapshotLineInput;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StockSnapshotRequest(@NotNull TarbilStockSystem system, @NotNull @Size(min = 1, max = 500) List<@Valid StockSnapshotLineRequest> lines) {
    public List<StockSnapshotLineInput> toInputs() {
        return lines.stream().map(l -> new StockSnapshotLineInput(l.productName(), l.presentation(), l.lotNumber(),
            l.expiryDate(), l.quantity(), l.openedQuantity())).toList();
    }
}
```

`$TB/api/dto/StockSnapshotResponse.java`:

```java
package com.vetos.modules.integration.tarbil.api.dto;

import java.util.UUID;

public record StockSnapshotResponse(UUID snapshotId) {}
```

`$TB/api/dto/StockSyncLineResponse.java`:

```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.StockSyncLineView;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockSyncLineResponse(UUID lineId, String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                    int tarbilQuantity, BigDecimal openedQuantity, TarbilStockSyncStatus status,
                                    UUID inventoryItemId, Integer vetlyQuantity) {
    public static StockSyncLineResponse from(StockSyncLineView v) {
        return new StockSyncLineResponse(v.lineId(), v.productName(), v.presentation(), v.lotNumber(), v.expiryDate(),
            v.tarbilQuantity(), v.openedQuantity(), v.status(), v.inventoryItemId(), v.vetlyQuantity());
    }
}
```

`$TB/api/dto/StockSyncResponse.java`:

```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.StockSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockSyncResponse(UUID snapshotId, TarbilStockSystem system, Instant takenAt, List<StockSyncLineResponse> lines) {
    public static StockSyncResponse from(StockSyncView v) {
        return new StockSyncResponse(v.snapshotId(), v.system(), v.takenAt(), v.lines().stream().map(StockSyncLineResponse::from).toList());
    }
}
```

`$TB/api/dto/ApplyStockSyncRequest.java`:

```java
package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ApplyStockSyncRequest(@NotNull @Size(min = 1, max = 500) List<UUID> lineIds) {}
```

`$TB/api/dto/ApplyStockSyncResponse.java`:

```java
package com.vetos.modules.integration.tarbil.api.dto;

public record ApplyStockSyncResponse(int applied) {}
```

`$TB/api/TarbilExtensionController.java`: alan listesine `private final RecordStockSnapshotUseCase recordStockSnapshotUseCase;` ekle; import'lara `com.vetos.modules.integration.tarbil.api.dto.StockSnapshotRequest`, `com.vetos.modules.integration.tarbil.api.dto.StockSnapshotResponse`, `com.vetos.modules.integration.tarbil.application.RecordStockSnapshotUseCase` ekle; sınıfın sonuna ekle:

```java
    @PostMapping("/stock-snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    public StockSnapshotResponse uploadStockSnapshot(@Valid @RequestBody StockSnapshotRequest request,
                                                     @AuthenticationPrincipal AuthenticatedStaffUser user) {
        return new StockSnapshotResponse(recordStockSnapshotUseCase.execute(
            TenantContext.current(), user.staffUserId(), request.system(), request.toInputs()));
    }
```

`$TB/api/TarbilController.java`: alan listesine `private final GetStockSyncViewUseCase getStockSyncViewUseCase;` ve `private final ApplyStockSyncUseCase applyStockSyncUseCase;` ekle; import'lara `ApplyStockSyncRequest`, `ApplyStockSyncResponse`, `StockSyncResponse` (api.dto), `ApplyStockSyncUseCase`, `GetStockSyncViewUseCase` (application), `com.vetos.modules.integration.tarbil.domain.TarbilStockSystem`, `com.vetos.modules.integration.tarbil.domain.exception.TarbilStockBranchMissingException` ekle; sınıfın sonuna ekle:

```java
    @GetMapping("/stock-sync")
    public StockSyncResponse stockSync(@RequestParam TarbilStockSystem system, @AuthenticationPrincipal AuthenticatedStaffUser user) {
        return StockSyncResponse.from(getStockSyncViewUseCase.execute(TenantContext.current(), firstBranch(user), system));
    }

    @PostMapping("/stock-sync/{snapshotId}/apply")
    public ApplyStockSyncResponse applyStockSync(@PathVariable UUID snapshotId, @Valid @RequestBody ApplyStockSyncRequest request,
                                                 @AuthenticationPrincipal AuthenticatedStaffUser user) {
        return new ApplyStockSyncResponse(applyStockSyncUseCase.execute(TenantContext.current(), firstBranch(user), snapshotId, request.lineIds()));
    }

    /** Spec 2026-10-04 S13: stok kullanicinin ilk subesine islenir (cok subeli secim sonraki is). */
    private static UUID firstBranch(AuthenticatedStaffUser user) {
        if (user.branchIds() == null || user.branchIds().isEmpty()) {
            throw new TarbilStockBranchMissingException();
        }
        return user.branchIds().get(0);
    }
```

- [ ] **Step 7: Testleri çalıştır, geçtiğini gör**

Run: `cd backend && ./mvnw -q test -Dtest='StockSyncMatcherTest,RecordStockSnapshotUseCaseTest,ApplyStockSyncUseCaseTest,TarbilExtensionSecurityIntegrationTest' > /tmp/p1a2.log 2>&1; echo exit=$?; grep -E "ERROR\]|Caused by" /tmp/p1a2.log | head -5`
Expected: `exit=0`.

- [ ] **Step 8: Tüm backend testleri (ApplicationModulesTest dahil)**

Run: `cd backend && ./mvnw -q test > /tmp/p1a2b.log 2>&1; echo exit=$?; grep -lE "<(failure|error)" target/surefire-reports/TEST-*.xml | head`
Expected: `exit=0`; ikinci komut boş.

- [ ] **Step 9: Commit**

```bash
git add backend/src
git commit -m "feat(tarbil): stok anlik goruntusu, esleme ve Vetly stoguna isleme uclari (P1a)"
```

---

### Task 3: Eklenti — stok tablosu okuma ve MAIN komutu

**Files:**
- Create: `extension/src/tarbil/selectors/stock.ts`, `extension/src/tarbil/steps/stockRows.ts`, `extension/src/tarbil/steps/stockRows.test.ts`
- Modify: `extension/src/tarbil/selectors/shared.ts` (+test), `extension/src/tarbil/selectors/index.ts`, `extension/src/tarbil/selectors/allowlist.ts`, `extension/src/tarbil/page/telerik.ts`, `extension/src/tarbil/page/ops.ts`, `extension/src/tarbil/page/ops.test.ts`

**Interfaces:**
- Produces:
  - `VACCINE_STOCK = { search: 'BodyContent_btnSearch', grid: '_radGridStock_ctl00', gridComponent: '_radGridStock' }`, `MEDICINE_STOCK = { search: 'ContentHolder_btnSearch', grid: '_radGridStockSearch_ctl00', gridComponent: '_radGridStockSearch' }`
  - `PageKind` + `'vaccineStock' | 'medicineStock'`
  - `ALLOWED_BUTTONS.vaccineStock.search`, `ALLOWED_BUTTONS.medicineStock.search`
  - `showAllRows(env, gridSuffix: string, pageSize: number): Promise<{ postback: boolean }>`
  - MAIN komutu `loadStockTable({ page: 'vaccineStock' | 'medicineStock' })`
  - `type StockKind = 'vaccineStock' | 'medicineStock'`; `interface StockRow { productName: string; presentation: string | null; lotNumber: string | null; expiryDate: string | null; quantity: number; openedQuantity: number | null }`; `parseTrDate(s): string | null`; `parseTrNumber(s): number | null`; `readStockRows(doc, kind): StockRow[]`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/steps/stockRows.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { parseTrDate, parseTrNumber, readStockRows } from './stockRows';

const VP = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_radGridStock_ctl00';
const MP = 'ctl00_ContentHolder_radGridStockSearch_ctl00';

function vaccineTable(rows: string, headers = ['', '', '', 'Ruhsat Tipi', 'Aşı Adı', 'Takdim Şekli', 'Seri Numarası', 'Son Kullanma Tarihi', 'Ürün Miktarı', 'Stok Tipi']) {
  document.body.innerHTML = `<table id="${VP}"><thead><tr>${headers.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${rows}</tbody></table>`;
}

describe('stock rows', () => {
  it('parses Turkish dates and numbers', () => {
    expect(parseTrDate('31.01.2027')).toBe('2027-01-31');
    expect(parseTrDate(' ')).toBeNull();
    expect(parseTrNumber('9,99')).toBe(9.99);
    expect(parseTrNumber('1.234,5')).toBe(1234.5);
    expect(parseTrNumber('')).toBeNull();
  });

  it('reads the vaccine stock table by header names', () => {
    vaccineTable(`<tr id="${VP}__0"><td></td><td></td><td></td><td>R</td><td> Aşı X </td><td>Flakon</td><td>123456</td><td>31.01.2027</td><td>4</td><td>Ana Stok</td></tr>`);

    expect(readStockRows(document, 'vaccineStock')).toEqual([
      { productName: 'Aşı X', presentation: 'Flakon', lotNumber: '123456', expiryDate: '2027-01-31', quantity: 4, openedQuantity: null },
    ]);
  });

  it('reads the medicine table whose header lives in a separate table', () => {
    document.body.innerHTML = `
      <table id="${MP}_Header"><thead><tr><th></th><th>Satış Yeri</th><th>Ürün</th><th>Takdim Şekli</th><th>Miktar</th>
        <th>Son Kullanma Tarihi</th><th>Açılmış Kalan Miktar</th><th>Açılmış Kalan Miktar Son Kullanma Tarihi</th><th>Seri Numarası</th><th>Kaydeden</th></tr></thead></table>
      <table id="${MP}"><tbody><tr id="${MP}__0"><td></td><td>Klinik</td><td>İlaç Y</td><td>Kutu</td><td>2</td><td>01.02.2027</td><td>0,5</td><td>01.03.2027</td><td>A1B2</td><td>X</td></tr></tbody></table>`;

    expect(readStockRows(document, 'medicineStock')).toEqual([
      { productName: 'İlaç Y', presentation: 'Kutu', lotNumber: 'A1B2', expiryDate: '2027-02-01', quantity: 2, openedQuantity: 0.5 },
    ]);
  });

  it('returns no rows when a required column is missing', () => {
    vaccineTable(`<tr id="${VP}__0"><td>Aşı X</td></tr>`, ['Ad']);
    expect(readStockRows(document, 'vaccineStock')).toEqual([]);
  });
});
```

`extension/src/tarbil/selectors/shared.test.ts` içindeki `describe('pageKind', ...)` bloğuna ekle:

```ts
  it('recognizes the vaccine and medicine stock pages', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ATS/VaccineStock/VaccineStockSearch.aspx', search: '' })).toBe('vaccineStock');
    expect(pageKind({ pathname: '/Pages/StockSearch.aspx', search: '' })).toBe('medicineStock');
  });
```

`extension/src/tarbil/page/ops.test.ts` `describe('page ops', ...)` bloğunun sonuna ekle:

```ts
  it('loads the whole medicine stock table: presses Ara, then shows all rows', async () => {
    const BTN = 'ctl00_ContentHolder_btnSearch';
    const GRID = 'ctl00_ContentHolder_radGridStockSearch';
    document.body.innerHTML = `<a id="${BTN}"></a><div id="${GRID}"></div>`;
    const prm = instantPrm();
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [BTN]: { click: () => { log.push('ara'); prm.fire(); } },
      [GRID]: { get_masterTableView: () => ({ get_pageSize: () => 10, set_pageSize: (n: number) => { log.push(`size:${n}`); prm.fire(); } }) },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).loadStockTable({ page: 'medicineStock' });

    expect(log).toEqual(['ara', 'size:500']);
  });

  it('rejects an unknown stock page', async () => {
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => instantPrm(), isReady: () => true };
    await expect(createPageOps(env).loadStockTable({ page: 'vaccineReceipt' })).rejects.toMatchObject({ code: 'BAD_INPUT' });
  });
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/steps/stockRows.test.ts src/tarbil/selectors/shared.test.ts src/tarbil/page/ops.test.ts 2>&1 | grep -E "resolve|×|Tests "`
Expected: `./stockRows` çözümlenemiyor; pageKind ve `loadStockTable` testleri başarısız.

- [ ] **Step 3: Seçiciler, izin listesi, sayfa türü**

`extension/src/tarbil/selectors/stock.ts`:

```ts
// TARBIL stok sayfalari (spec 2026-10-04 S13; 2026-10-04 canli okuma). Eklenti burada yalniz "Ara"ya basar ve
// tabloyu tek sayfaya alir (sayfa boyutu); baska hicbir sey yapmaz.

/** hbsapp: Asi > Stok > Ara (ATS/VaccineStock/VaccineStockSearch.aspx). */
export const VACCINE_STOCK = {
  search: 'BodyContent_btnSearch',
  grid: '_radGridStock_ctl00',
  gridComponent: '_radGridStock',
} as const;

/** vetilac: Ilac Takip Sistemi > Stok Ara (/Pages/StockSearch.aspx). Baslik ayri `<grid>_Header` tablosunda. */
export const MEDICINE_STOCK = {
  search: 'ContentHolder_btnSearch',
  grid: '_radGridStockSearch_ctl00',
  gridComponent: '_radGridStockSearch',
} as const;
```

`extension/src/tarbil/selectors/index.ts` sonuna ekle:

```ts
export * from './stock';
```

`extension/src/tarbil/selectors/allowlist.ts` içinde import'a `import { MEDICINE_STOCK, VACCINE_STOCK } from './stock';` ekle ve `ALLOWED_BUTTONS`'ı şununla değiştir:

```ts
export const ALLOWED_BUTTONS = {
  vaccineReceipt: { petVet: RECEIPT.petVet },
  animalSearch: { search: SEARCH.search, transfer: SEARCH.transfer },
  vaccineStock: { search: VACCINE_STOCK.search },
  medicineStock: { search: MEDICINE_STOCK.search },
} as const;
```

`extension/src/tarbil/selectors/shared.ts` içinde:
1. `const HOME_PATHS = ['/', '/default.aspx'];` satırının altına ekle:
```ts
const VACCINE_STOCK_PATH = '/modules/receipt/pages/ats/vaccinestock/vaccinestocksearch.aspx';
const MEDICINE_STOCK_PATH = '/pages/stocksearch.aspx';
```
2. `export type PageKind = 'receipt' | 'search' | 'home' | 'other';` satırını şununla değiştir:
```ts
export type PageKind = 'receipt' | 'search' | 'home' | 'vaccineStock' | 'medicineStock' | 'other';
```
3. `pageKind` içinde `if (HOME_PATHS.includes(path)) return 'home';` satırının hemen üstüne ekle:
```ts
  if (path === VACCINE_STOCK_PATH) return 'vaccineStock';
  if (path === MEDICINE_STOCK_PATH) return 'medicineStock';
```

- [ ] **Step 4: Tablo okuyucu**

`extension/src/tarbil/steps/stockRows.ts`:

```ts
import { MEDICINE_STOCK, VACCINE_STOCK, bySuffix } from '../selectors';

export type StockKind = 'vaccineStock' | 'medicineStock';

export interface StockRow {
  productName: string;
  presentation: string | null;
  lotNumber: string | null;
  expiryDate: string | null;
  quantity: number;
  openedQuantity: number | null;
}

interface Columns { product: string; presentation: string; lot: string; expiry: string; quantity: string; opened?: string }

// Basliklar 2026-10-04 canli okumasindan; tam esitlikle aranir ("Acilmis Kalan Miktar Son Kullanma Tarihi" karismasin).
const COLUMNS: Record<StockKind, Columns> = {
  vaccineStock: { product: 'Aşı Adı', presentation: 'Takdim Şekli', lot: 'Seri Numarası', expiry: 'Son Kullanma Tarihi', quantity: 'Ürün Miktarı' },
  medicineStock: { product: 'Ürün', presentation: 'Takdim Şekli', lot: 'Seri Numarası', expiry: 'Son Kullanma Tarihi', quantity: 'Miktar', opened: 'Açılmış Kalan Miktar' },
};

const clean = (s: string | null | undefined) => (s ?? '').replace(/\s+/g, ' ').trim();

export function parseTrDate(s: string | null | undefined): string | null {
  const m = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(clean(s));
  return m ? `${m[3]}-${m[2]}-${m[1]}` : null;
}

export function parseTrNumber(s: string | null | undefined): number | null {
  const t = clean(s).replace(/\./g, '').replace(',', '.');
  if (!t) return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

function headerCells(doc: Document, table: HTMLTableElement): string[] {
  const separate = doc.getElementById(`${table.id}_Header`) as HTMLTableElement | null;
  const head = (separate ?? table).tHead?.rows;
  return head && head.length > 0 ? Array.from(head[head.length - 1].cells).map((c) => clean(c.textContent)) : [];
}

/** Bilinen sutunlardan biri yoksa (TARBIL ekrani degismis) bos liste doner; yanlis veri gonderilmez. */
export function readStockRows(doc: Document, kind: StockKind): StockRow[] {
  const gridSuffix = kind === 'vaccineStock' ? VACCINE_STOCK.grid : MEDICINE_STOCK.grid;
  const table = doc.querySelector<HTMLTableElement>(`table${bySuffix(gridSuffix)}`);
  if (!table) return [];
  const headers = headerCells(doc, table);
  const cols = COLUMNS[kind];
  const idx = (name: string | undefined) => (name ? headers.indexOf(name) : -1);
  const product = idx(cols.product);
  const presentation = idx(cols.presentation);
  const lot = idx(cols.lot);
  const expiry = idx(cols.expiry);
  const quantity = idx(cols.quantity);
  const opened = idx(cols.opened);
  if ([product, presentation, lot, expiry, quantity].some((i) => i < 0) || (cols.opened && opened < 0)) return [];
  return Array.from(table.tBodies[0]?.rows ?? [])
    .filter((r) => r.id.startsWith(`${table.id}__`))
    .map((r) => ({
      productName: clean(r.cells[product]?.textContent),
      presentation: clean(r.cells[presentation]?.textContent) || null,
      lotNumber: clean(r.cells[lot]?.textContent) || null,
      expiryDate: parseTrDate(r.cells[expiry]?.textContent),
      quantity: Math.max(0, Math.round(parseTrNumber(r.cells[quantity]?.textContent) ?? 0)),
      openedQuantity: opened >= 0 ? parseTrNumber(r.cells[opened]?.textContent) : null,
    }))
    .filter((row) => row.productName.length > 0);
}
```

- [ ] **Step 5: MAIN komutu**

`extension/src/tarbil/page/telerik.ts` sonuna ekle:

```ts
/** RadGrid'i tek sayfada gosterir (sayfa boyutu pageSize); zaten o kadar buyukse postback yapmaz. */
export async function showAllRows(env: TelerikEnv, gridSuffix: string, pageSize: number): Promise<{ postback: boolean }> {
  const view = component(env, gridSuffix).get_masterTableView();
  if (typeof view.get_pageSize === 'function' && view.get_pageSize() >= pageSize) return { postback: false };
  return withPostback(env, () => view.set_pageSize(pageSize));
}
```

`extension/src/tarbil/page/ops.ts` içinde:
1. import satırlarını şununla değiştir:
```ts
import type { PageHandler } from '../core/bridge';
import { MEDICINE_STOCK, RECEIPT, SEARCH, VACCINE_STOCK, allowedButtonSuffix, bySuffix } from '../selectors';
import { PageError, clickButton, clickElement, selectComboValue, setDate, setText, showAllRows, waitUntil, type TelerikEnv } from './telerik';
```
2. dönen nesneye `checkRow`'dan sonra ekle:
```ts
    // Stok sayfalari: "Ara" + tum satirlari tek sayfaya al. Yalniz okuma icin (spec 2026-10-04 S13).
    loadStockTable: async ({ page }: { page: string }) => {
      const grid = page === 'vaccineStock' ? VACCINE_STOCK.gridComponent : page === 'medicineStock' ? MEDICINE_STOCK.gridComponent : null;
      if (!grid) throw new PageError('BAD_INPUT', `Stok sayfası değil: ${page}`);
      await clickButton(env, allowed(page, 'search'));
      await showAllRows(env, grid, 500);
    },
```

- [ ] **Step 6: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "×|Test Files|Tests "`
Expected: tip hatası yok; tüm testler PASS (106 + stockRows 4 + pageKind 1 + ops 2 = 113).

- [ ] **Step 7: Commit**

```bash
git add extension/src
git commit -m "feat(tarbil-ext): TARBIL asi/ilac stok tablosunu okuma ve tek sayfaya alma (salt okuma)"
```

---

### Task 4: Eklenti — stok sayfası kartı, Vetly'ye gönderme, bağlama

**Files:**
- Create: `extension/src/tarbil/pages/stockSync.ts`, `extension/src/tarbil/pages/stockSync.test.ts`
- Modify: `extension/src/shared/types.ts`, `extension/src/shared/messages.ts`, `extension/src/background/vetlyApi.ts`, `extension/src/background/router.ts`, `extension/src/background/router.test.ts`, `extension/src/tarbil/core/views.ts`, `extension/src/tarbil/content.ts`, `extension/public/manifest.json`

**Interfaces:**
- Consumes: `readStockRows`, `StockKind`, `StockRow` (Task 3); MAIN `loadStockTable`.
- Produces:
  - `type StockSystem = 'HBSAPP_VACCINE' | 'VETILAC_MEDICINE'`; `BackgroundRequest` + `{ type: 'UPLOAD_STOCK_SNAPSHOT'; system: StockSystem; lines: StockRow[] }`
  - `vetlyApi.uploadStockSnapshot(system, lines): Promise<{ snapshotId: string }>`
  - `createStockSync(d: StockSyncDeps): { start(): void }`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/pages/stockSync.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import type { BackgroundRequest } from '../../shared/messages';
import type { CardView } from '../core/card';
import type { Send } from '../steps/findAnimal';
import { createStockSync } from './stockSync';

const MP = 'ctl00_ContentHolder_radGridStockSearch_ctl00';

function medicineTable() {
  document.body.innerHTML = `
    <table id="${MP}_Header"><thead><tr><th></th><th>Satış Yeri</th><th>Ürün</th><th>Takdim Şekli</th><th>Miktar</th>
      <th>Son Kullanma Tarihi</th><th>Açılmış Kalan Miktar</th><th>Seri Numarası</th></tr></thead></table>
    <table id="${MP}"><tbody><tr id="${MP}__0"><td></td><td>K</td><td>İlaç Y</td><td>Kutu</td><td>2</td><td>01.02.2027</td><td></td><td>A1</td></tr></tbody></table>`;
}

function setup(sendResult: unknown = { ok: true, data: { snapshotId: 's1' } }) {
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const sent: BackgroundRequest[] = [];
  const send = (async (req: BackgroundRequest) => { sent.push(req); return sendResult; }) as Send;
  const shown: CardView[] = [];
  let handler: (id: string) => void = () => undefined;
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: (h: (id: string) => void) => { handler = h; } };
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  return { calls, sent, shown, text, card, bridge, send, click: (id: string) => handler(id) };
}

describe('stock sync page', () => {
  it('offers a button and does nothing on TARBIL until the vet clicks it', () => {
    medicineTable();
    const s = setup();
    createStockSync({ bridge: s.bridge, send: s.send, doc: document, card: s.card, kind: 'medicineStock' }).start();

    expect(s.calls).toEqual([]);
    expect(s.shown.at(-1)?.actions.map((a) => a.id)).toEqual(['sendStock']);
  });

  it('loads the table, reads it and uploads it to Vetly', async () => {
    medicineTable();
    const s = setup();
    createStockSync({ bridge: s.bridge, send: s.send, doc: document, card: s.card, kind: 'medicineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.sent).toHaveLength(1));
    expect(s.calls.map((c) => c.op)).toEqual(['ready', 'loadStockTable']);
    expect(s.calls[1].args).toEqual({ page: 'medicineStock' });
    expect(s.sent[0]).toEqual({
      type: 'UPLOAD_STOCK_SNAPSHOT', system: 'VETILAC_MEDICINE',
      lines: [{ productName: 'İlaç Y', presentation: 'Kutu', lotNumber: 'A1', expiryDate: '2027-02-01', quantity: 2, openedQuantity: null }],
    });
    await vi.waitFor(() => expect(s.text()).toContain('1 satır'));
  });

  it('warns instead of uploading when the table is not recognized', async () => {
    document.body.innerHTML = '<div></div>';
    const s = setup();
    createStockSync({ bridge: s.bridge, send: s.send, doc: document, card: s.card, kind: 'vaccineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.text()).toContain('tanınmadı'));
    expect(s.sent).toEqual([]);
  });

  it('reports a Vetly error', async () => {
    medicineTable();
    const s = setup({ ok: false, error: 'Eklenti bağlı değil', code: 'UNAUTHORIZED' });
    createStockSync({ bridge: s.bridge, send: s.send, doc: document, card: s.card, kind: 'medicineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.text()).toContain('Eklenti bağlı değil'));
  });
});
```

`extension/src/background/router.test.ts` `describe('router', ...)` bloğunun sonuna ekle:

```ts
  it('uploads a TARBIL stock snapshot through the API', async () => {
    const tokens = createTokenStore(memoryStore());
    const uploaded: unknown[] = [];
    const api = { uploadStockSnapshot: async (system: string, lines: unknown[]) => { uploaded.push({ system, lines }); return { snapshotId: 's1' }; } } as never;
    const router = createRouter({ api, tokens, outbox: createConfirmationOutbox(memoryStore(), api), session: memoryStore() });

    const res = await router.handle({ type: 'UPLOAD_STOCK_SNAPSHOT', system: 'HBSAPP_VACCINE', lines: [] });

    expect(res).toEqual({ ok: true, data: { snapshotId: 's1' } });
    expect(uploaded).toEqual([{ system: 'HBSAPP_VACCINE', lines: [] }]);
  });
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/pages/stockSync.test.ts src/background/router.test.ts 2>&1 | grep -E "resolve|×|Tests "`
Expected: `./stockSync` çözümlenemiyor; router testi başarısız.

- [ ] **Step 3: Tipler, mesaj, API, yönlendirici**

`extension/src/shared/types.ts` sonuna ekle:

```ts
export type StockSystem = 'HBSAPP_VACCINE' | 'VETILAC_MEDICINE';

/** TARBIL stok tablosundan okunan satir (spec 2026-10-04 S13). */
export interface StockSnapshotLine {
  productName: string;
  presentation: string | null;
  lotNumber: string | null;
  expiryDate: string | null;
  quantity: number;
  openedQuantity: number | null;
}
```

`extension/src/shared/messages.ts`:
1. ilk import satırını `import type { ConfirmationMethod, ExtensionProfile, StockSnapshotLine, StockSystem, Submission } from './types';` yap.
2. `BackgroundRequest` birleşiminin son üyesinden (`| { type: 'DISMISS'; id: string; reason: string };`) önce ekle:
```ts
  | { type: 'UPLOAD_STOCK_SNAPSHOT'; system: StockSystem; lines: StockSnapshotLine[] }
```
(son üyenin sonundaki `;` yerinde kalmalı).

`extension/src/background/vetlyApi.ts`:
1. import'u `import type { ConfirmationMethod, ExtensionProfile, StockSnapshotLine, StockSystem, Submission } from '../shared/types';` yap.
2. dönen nesneye `dismiss`'ten sonra ekle:
```ts
    uploadStockSnapshot: (system: StockSystem, lines: StockSnapshotLine[]) =>
      request<{ snapshotId: string }>('/api/v1/tarbil-extension/stock-snapshots', {
        method: 'POST',
        body: JSON.stringify({ system, lines }),
      }),
```

`extension/src/background/router.ts` içinde `case 'DISMISS':` bloğundan önce ekle:

```ts
          case 'UPLOAD_STOCK_SNAPSHOT':
            return { ok: true, data: await api.uploadStockSnapshot(req.system, req.lines) };
```

- [ ] **Step 4: Kart içerikleri ve sayfa modülü**

`extension/src/tarbil/core/views.ts` içinde `popup:` satırından önce ekle:

```ts
  stock: (text: string, tone?: Tone, actions: CardAction[] = []): CardView => ({ lines: [{ text: 'TARBİL stoğu', tone: 'strong' }, { text, tone }], actions }),
```

`extension/src/tarbil/pages/stockSync.ts`:

```ts
import type { StockSystem } from '../../shared/types';
import type { PageBridge } from '../core/bridge';
import type { Card } from '../core/card';
import { views } from '../core/views';
import type { Send } from '../steps/findAnimal';
import { readStockRows, type StockKind } from '../steps/stockRows';

export interface StockSyncDeps {
  bridge: PageBridge;
  send: Send;
  doc: Document;
  card: Card;
  kind: StockKind;
}

const SYSTEM: Record<StockKind, StockSystem> = { vaccineStock: 'HBSAPP_VACCINE', medicineStock: 'VETILAC_MEDICINE' };
const SEND_ACTION = [{ id: 'sendStock', label: "TARBİL stoğunu Vetly'ye gönder" }];

/**
 * TARBIL asi/ilac stok sayfasi (spec 2026-10-04 S13): hekim tiklayinca "Ara" + tum satirlar, tablo okunur, Vetly'ye
 * gonderilir. Vetly stoguna isleme hekimin Vetly Stok sayfasindaki onayiyla olur; TARBIL'de baska hicbir sey yapilmaz.
 */
export function createStockSync(d: StockSyncDeps) {
  let busy = false;

  async function sendStock(): Promise<void> {
    if (busy) return;
    busy = true;
    try {
      d.card.show(views.stock('TARBİL stoğu okunuyor…', 'muted'));
      await d.bridge.call('ready');
      await d.bridge.call('loadStockTable', { page: d.kind });
      const lines = readStockRows(d.doc, d.kind);
      if (lines.length === 0) {
        d.card.show(views.stock('Stok tablosu tanınmadı ya da boş. TARBİL ekranı değişmiş olabilir; Vetly ekibine haber verin.', 'warn', SEND_ACTION));
        return;
      }
      const res = await d.send<{ snapshotId: string }>({ type: 'UPLOAD_STOCK_SNAPSHOT', system: SYSTEM[d.kind], lines });
      if (!res.ok) {
        d.card.show(views.stock(res.error, 'warn', SEND_ACTION));
        return;
      }
      d.card.show(views.stock(`${lines.length} satır Vetly'ye gönderildi. Vetly > Stok > TARBİL Eşitleme bölümünden kontrol edip onaylayın.`, 'ok', SEND_ACTION));
    } catch (e) {
      d.card.show(views.stock(`Stok okunamadı (${(e as { code?: string })?.code ?? 'UNKNOWN'}).`, 'warn', SEND_ACTION));
    } finally {
      busy = false;
    }
  }

  d.card.onAction((id) => {
    if (id === 'sendStock') void sendStock();
  });

  return {
    start(): void {
      d.card.show(views.stock("Bu sayfadaki stoğu Vetly'ye aktarabilirsiniz (TARBİL'de yalnız arama yapılır).", 'muted', SEND_ACTION));
    },
  };
}
```

- [ ] **Step 5: İçerik betiği ve manifest**

`extension/src/tarbil/content.ts`:
1. import'lara ekle: `import { createStockSync } from './pages/stockSync';`
2. `if (location.origin !== 'https://vetilac.tarbil.gov.tr') routeTarbilPage();` satırını şununla değiştir:
```ts
if (location.origin === 'https://vetilac.tarbil.gov.tr') {
  // vetilac'ta yalniz ilac stok sayfasi; digerlerinde yalniz oturum canli tutulur.
  if (pageKind(location) === 'medicineStock') startStockSync('medicineStock');
} else {
  routeTarbilPage();
}

function startStockSync(kind: 'vaccineStock' | 'medicineStock'): void {
  createStockSync({ bridge: createPageBridge(window), send, doc: document, card, kind }).start();
}
```
3. `routeTarbilPage` içindeki `switch`'te `case 'home':` satırından önce ekle:
```ts
  case 'vaccineStock':
    startStockSync('vaccineStock');
    break;
```

`extension/public/manifest.json`: `"js": ["page.js"]` olan içerik betiğinin `matches` dizisine `"https://vetilac.tarbil.gov.tr/*"` ekle (MAIN dünya betiği `vetilac`'ta da çalışsın).

- [ ] **Step 6: Testler, tip kontrolü, derleme**

Run: `cd extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "×|Test Files|Tests " && npm run build:dev > /tmp/p1a4.log 2>&1; echo build=$?; grep -c "console\." dist/content.js dist/page.js`
Expected: tip hatası yok; `Tests 118 passed (118)` (113 + stockSync 4 + router 1); `build=0`; `console.` sayıları `0`.

- [ ] **Step 7: Commit**

```bash
git add extension/src extension/public/manifest.json
git commit -m "feat(tarbil-ext): stok sayfalarinda 'TARBIL stogunu Vetly'ye gonder' karti ve yukleme"
```

---

### Task 5: Frontend — Stok sayfasında "TARBİL Eşitleme" bölümü

**Files:**
- Modify: `frontend/src/api/tarbilApi.ts`, `frontend/src/pages/inventory/InventoryPage.tsx`
- Create: `frontend/src/pages/inventory/TarbilStockSyncPanel.tsx`

**Interfaces:**
- Consumes: Task 2 uçları.
- Produces: `tarbilApi.stockSync(system)`, `tarbilApi.applyStockSync(snapshotId, lineIds)`; `<TarbilStockSyncPanel onApplied={() => void} />`

- [ ] **Step 1: API istemcisi**

`frontend/src/api/tarbilApi.ts` içinde `export const tarbilApi = {` satırından önce ekle:

```ts
export type TarbilStockSystem = 'HBSAPP_VACCINE' | 'VETILAC_MEDICINE';
export type TarbilStockSyncStatus = 'NEW' | 'QUANTITY_DIFFERS' | 'MATCHED' | 'APPLIED';

export interface TarbilStockSyncLine {
  lineId: string;
  productName: string;
  presentation: string | null;
  lotNumber: string | null;
  expiryDate: string | null;
  tarbilQuantity: number;
  openedQuantity: number | null;
  status: TarbilStockSyncStatus;
  inventoryItemId: string | null;
  vetlyQuantity: number | null;
}

export interface TarbilStockSync {
  snapshotId: string | null;
  system: TarbilStockSystem;
  takenAt: string | null;
  lines: TarbilStockSyncLine[];
}
```

ve `tarbilApi` nesnesine (`mappings` satırından sonra) ekle:

```ts
  stockSync: (system: TarbilStockSystem) => apiClient.get<TarbilStockSync>(`/api/v1/tarbil/stock-sync?system=${system}`),
  applyStockSync: (snapshotId: string, lineIds: string[]) =>
    apiClient.post<{ applied: number }>(`/api/v1/tarbil/stock-sync/${snapshotId}/apply`, { lineIds }),
```

**Not:** `apiClient.post`'un imzasını (`post: <T>(path, data?) => ...`) `frontend/src/api/client.ts`'ten doğrula; farklıysa aynı dosyadaki `dismiss` kullanımını örnek al.

- [ ] **Step 2: Panel bileşeni**

`frontend/src/pages/inventory/TarbilStockSyncPanel.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { tarbilApi, TarbilStockSync, TarbilStockSyncStatus, TarbilStockSystem } from '../../api/tarbilApi';
import styles from './InventoryPage.module.css';

const STATUS: Record<TarbilStockSyncStatus, { label: string; tone: 'success' | 'warning' | 'neutral' | 'gold' }> = {
  NEW: { label: "Vetly'de yok", tone: 'gold' },
  QUANTITY_DIFFERS: { label: 'Miktar farklı', tone: 'warning' },
  MATCHED: { label: 'Eşleşti', tone: 'success' },
  APPLIED: { label: 'İşlendi', tone: 'neutral' },
};

/**
 * TARBIL stogu (eklentinin gonderdigi son goruntu) ile Vetly stogunu karsilastirir; secilen satirlari Vetly stoguna isler.
 * Spec 2026-10-04 S13. Yalniz ADMIN ve VET (/tarbil/** kurali).
 */
export function TarbilStockSyncPanel({ onApplied }: { onApplied: () => void }) {
  const [system, setSystem] = useState<TarbilStockSystem>('VETILAC_MEDICINE');
  const [data, setData] = useState<TarbilStockSync | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  function load(s: TarbilStockSystem) {
    setData(null);
    setSelected(new Set());
    tarbilApi.stockSync(s).then(setData).catch((e: Error) => setMessage(e.message));
  }

  useEffect(() => {
    load(system);
  }, [system]);

  const selectable = (data?.lines ?? []).filter((l) => l.status === 'NEW' || l.status === 'QUANTITY_DIFFERS');

  async function apply() {
    if (!data?.snapshotId || selected.size === 0) return;
    setBusy(true);
    setMessage(null);
    try {
      const res = await tarbilApi.applyStockSync(data.snapshotId, Array.from(selected));
      setMessage(`${res.applied} satır Vetly stoğuna işlendi.`);
      load(system);
      onApplied();
    } catch (e) {
      setMessage((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.tableCard} style={{ marginTop: 24, padding: 16 }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <strong>TARBİL Eşitleme</strong>
        <Button variant={system === 'VETILAC_MEDICINE' ? 'primary' : 'secondary'} onClick={() => setSystem('VETILAC_MEDICINE')}>İlaç</Button>
        <Button variant={system === 'HBSAPP_VACCINE' ? 'primary' : 'secondary'} onClick={() => setSystem('HBSAPP_VACCINE')}>Aşı</Button>
        <span className={styles.muted}>
          {data?.takenAt ? `TARBİL'den alındı: ${new Date(data.takenAt).toLocaleString('tr-TR')}` : ''}
        </span>
      </div>
      {!data ? (
        <div className={styles.empty}>Yükleniyor...</div>
      ) : !data.snapshotId ? (
        <div className={styles.empty}>
          Henüz TARBİL stoğu gönderilmedi. TARBİL'de {system === 'VETILAC_MEDICINE' ? 'İlaç Takip Sistemi > Stok Ara' : 'Aşı > Stok > Ara'} sayfasında Vetly kartındaki "TARBİL stoğunu Vetly'ye gönder" butonuna basın.
        </div>
      ) : (
        <>
          <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: 12 }}>
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    aria-label="Hepsini seç"
                    checked={selectable.length > 0 && selected.size === selectable.length}
                    onChange={(e) => setSelected(e.target.checked ? new Set(selectable.map((l) => l.lineId)) : new Set())}
                  />
                </th>
                <th style={{ textAlign: 'left' }}>Ürün</th>
                <th style={{ textAlign: 'left' }}>Seri No</th>
                <th style={{ textAlign: 'left' }}>Son Kullanma</th>
                <th>TARBİL</th>
                <th>Vetly</th>
                <th>Durum</th>
              </tr>
            </thead>
            <tbody>
              {data.lines.map((l) => {
                const canSelect = l.status === 'NEW' || l.status === 'QUANTITY_DIFFERS';
                return (
                  <tr key={l.lineId}>
                    <td>
                      <input
                        type="checkbox"
                        disabled={!canSelect}
                        checked={selected.has(l.lineId)}
                        onChange={(e) => {
                          const next = new Set(selected);
                          if (e.target.checked) next.add(l.lineId);
                          else next.delete(l.lineId);
                          setSelected(next);
                        }}
                      />
                    </td>
                    <td>
                      {l.productName}
                      {l.presentation && <span className={styles.muted}> · {l.presentation}</span>}
                    </td>
                    <td>{l.lotNumber ?? '—'}</td>
                    <td>{l.expiryDate ? new Date(l.expiryDate).toLocaleDateString('tr-TR') : '—'}</td>
                    <td style={{ textAlign: 'center' }}>{l.tarbilQuantity}</td>
                    <td style={{ textAlign: 'center' }}>{l.vetlyQuantity ?? '—'}</td>
                    <td><Badge tone={STATUS[l.status].tone}>{STATUS[l.status].label}</Badge></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 12 }}>
            <Button variant="primary" disabled={busy || selected.size === 0} onClick={apply}>
              Seçilenleri Vetly stoğuna işle ({selected.size})
            </Button>
          </div>
        </>
      )}
      {message && <div className={styles.muted} style={{ marginTop: 8 }}>{message}</div>}
    </div>
  );
}
```

**Not:** `Badge`'in `tone` tipinde `'gold'` ve `'neutral'` var (`components/ui/Badge.tsx`). `InventoryPage.module.css`'te `tableCard`, `empty`, `muted` sınıfları mevcut.

- [ ] **Step 3: Stok sayfasına ekle**

`frontend/src/pages/inventory/InventoryPage.tsx`:
1. import'lara ekle: `import { TarbilStockSyncPanel } from './TarbilStockSyncPanel';`
2. `const canWrite = ...` satırının altına ekle:
```tsx
  // /tarbil/** uclari ADMIN ve VET'e acik (TARBIL'de stok isini hekim yapar).
  const canTarbilSync = session ? ['ADMIN', 'VET'].includes(session.role) : false;
```
3. `<ItemDetailModal` satırından hemen önce ekle:
```tsx
      {canTarbilSync && <TarbilStockSyncPanel onApplied={load} />}
```

- [ ] **Step 4: Tip kontrolü ve derleme**

Run: `cd frontend && npx tsc -b --noEmit; echo tsc=$?; npm run build > /tmp/p1a5.log 2>&1; echo build=$?`
Expected: `tsc=0`, `build=0`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat(frontend): Stok sayfasinda TARBIL Esitleme bolumu (fark gor, secileni isle)"
```

---

### Task 6: Dokümantasyon ve son doğrulama

**Files:**
- Modify: `docs/api-conventions.md`, `docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md`, `extension/README.md`

- [ ] **Step 1: API belgesi**

`docs/api-conventions.md` içinde "**TARBİL uçları (P0 çekirdek, 2026-10-04):**" listesinin sonuna ekle:

```markdown
- `POST /api/v1/tarbil-extension/stock-snapshots` (eklenti) — `{system: HBSAPP_VACCINE|VETILAC_MEDICINE, lines[{productName, presentation, lotNumber, expiryDate, quantity, openedQuantity}]}` (en çok 500) → 201 `{snapshotId}`.
- `GET /api/v1/tarbil/stock-sync?system=` (ADMIN, VET) — son TARBİL stok görüntüsü, Vetly stoğuyla karşılaştırmalı (`NEW`, `QUANTITY_DIFFERS`, `MATCHED`, `APPLIED`); görüntü yoksa `snapshotId: null`.
- `POST /api/v1/tarbil/stock-sync/{snapshotId}/apply` (ADMIN, VET) — `{lineIds}` → `{applied}`; kullanıcının ilk şubesine işlenir.
```

- [ ] **Step 2: README ve spec notu**

`extension/README.md` sonuna ekle:

```markdown

## TARBİL stoğunu Vetly'ye aktarma (P1a)

TARBİL'de **Aşı > Stok > Ara** (`hbsapp`) ya da **İlaç Takip Sistemi > Stok Ara** (`vetilac`) sayfasını açın; Vetly kartındaki **TARBİL stoğunu Vetly'ye gönder** butonuna basın. Eklenti yalnız "Ara"ya basar ve tabloyu tek sayfaya alır, okur ve Vetly'ye gönderir. Ardından Vetly'de **Stok** sayfasının altındaki **TARBİL Eşitleme** bölümünden farkları görüp seçtiğiniz satırları Vetly stoğuna işleyin.
```

`docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md` Bölüm 13'ün sonuna ekle:

```markdown
- **Uygulandı:** `docs/superpowers/plans/2026-10-04-tarbil-p1a-stok-esitleme.md` (V64 stok alanları, V65 `tarbil_stock_snapshot`).
```

- [ ] **Step 3: Son doğrulama**

Run:
```bash
cd backend && ./mvnw -q test > /tmp/p1a6.log 2>&1; echo backend=$?; grep -lE "<(failure|error)" target/surefire-reports/TEST-*.xml | head
cd ../extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "Tests " && npm run build:dev > /tmp/p1a6e.log 2>&1; echo ext=$?
cd ../frontend && npx tsc -b --noEmit; echo frontend=$?
```
Expected: `backend=0` ve rapor listesi boş; `Tests 118 passed (118)`; `ext=0`; `frontend=0`.

- [ ] **Step 4: Commit**

```bash
git add docs/api-conventions.md docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md extension/README.md
git commit -m "docs(tarbil): P1a stok esitleme - API, README, spec notu"
```

- [ ] **Step 5: Canlı kabul (hekimle; kod değişikliği yok)**

1. `chrome://extensions` → Vetly eklentisini **Yeniden yükle** (yeni izin: `vetilac`; Chrome izin isteyebilir).
2. TARBİL'de İlaç Takip Sistemi > Stok Ara sayfasını aç → Vetly kartı → **TARBİL stoğunu Vetly'ye gönder** → kart "N satır gönderildi" der (N = TARBİL'deki satır sayısı; 2026-10-04'te 40).
3. Aşı > Stok > Ara için aynısı (2026-10-04'te 2 satır).
4. Vetly > Stok > TARBİL Eşitleme: İlaç/Aşı sekmelerinde satırlar `Vetly'de yok` olarak görünür; birkaçını seçip işle → durum `İşlendi`, Stok listesinde yeni kalemler lot ve son kullanma tarihiyle görünür.
