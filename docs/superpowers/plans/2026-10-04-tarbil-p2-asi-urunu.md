# TARBİL P2 — Aşı Ürünü Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hekim aşıyı Vetly'de stoktan seçer (stok 1 düşer, iptalde geri gelir); eklenti TARBİL aşı belgesinde hayvandan sonra "Ürün Ekle" → stok penceresinde seri ile arama → "Seç" → "Ürün Adet = 1" adımlarını yapar; satır Kaydet, Detay ve Onayla hekimde kalır.

**Architecture:** `encounter` aşı kaydına `inventory_item_id` ekler ve `VaccinationRecordedEvent` / yeni `VaccinationCancelledEvent` ile bildirir; `inventory` bu olayları dinleyip 1 adet `OUT`/`IN` yazar (doğrudan bağımlılık döngü yaratacağı için olay). `integration/tarbil` eklentiye stok kaleminin TARBİL ürün adını da gönderir. Eklenti aşı sayfasında yeni `choosingProduct` / `productReady` adımlarını ve stok penceresi (`UcVaccineStockSearchModalPage`) için ayrı bir sayfa modülünü kazanır.

**Tech Stack:** Java 21, Spring Boot 3, Spring Modulith, JPA, Flyway, PostgreSQL, JUnit 5 + Mockito + MockMvc; Chrome MV3, TypeScript, Vitest + jsdom; React 18.

**Spec:** `docs/superpowers/specs/2026-10-04-tarbil-p2-asi-urunu-design.md` (canlı bulgular: `docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md` §3 "Aşı ürünü — canlı bulgular").

## Global Constraints

- Doğrudan `main` üzerinde çalışılır (dal açılmaz); push yok.
- Eklenti **satır Kaydet** (`PerformInsertButton`), **Onayla** (`btnInsert`, `btnInsert2`), Reddet ve çıkışa basmaz. `PerformInsertButton` yasak desenlere eklenir ve testle doğrulanır. (Spec §2)
- Seri bilinmiyorsa (aşı serbest yazılmış) ürün adımı tamamen hekimdedir; bugünkü "Ürün Ekle'den seçin" kartı kalır. (Spec §2)
- Eklenti yalnız birebir **tek** eşleşmeyi seçer; belirsizlikte (yok / çok / SKT geçmiş / ürün adı tutmuyor) hekime bırakır. (Spec §2)
- `encounter` → `inventory` doğrudan bağımlılık kurulmaz; stok işlemi `inventory` olay dinleyicisindedir. (Spec §3.3)
- Her aşı 1 adet: Vetly stoğundan 1 düşer, TARBİL "Ürün Adet" = 1. (Spec §5)
- Stok 0 iken kayıt reddedilmez; miktar eksiye düşmez, hareket yazılmaz. (Spec §3.3)
- V67 yalnız sütun ekler (çalışan eski backend'i bozmaz). (Spec §6)
- Konsola veri yazılmaz; TARBİL ViewState okunmaz; kullanıcıya görünen metinler Türkçe.
- Backend testleri docker-compose Postgres (localhost:5433) ister; hekimin 8080 backend'ine dokunulmaz.

## Review Focus

1. **Aynı aşı için iki kez "uygulandı" olayı** (kayıt ADMINISTERED + sonradan "uygulandı olarak işaretle" ya da çift istek) stoğu 2 düşürmemeli → Task 2 testi `should_deductOnlyOnce_when_sameVaccinationReportedTwice`.
2. **Uygulanmamış (SCHEDULED) aşının iptali** stoğa adet eklememeli → Task 2 testi `should_notReturnStock_when_cancelledBeforeAnyDeduction`.
3. **Başka kiracının / silinmiş stok kalemi kimliği** olayda gelirse hiçbir şey yazılmamalı → Task 2 testi `should_ignore_when_itemNotFound`.
4. **Stok penceresinde SKT'si geçmiş tek eşleşme** seçilmemeli, hekim uyarılmalı → Task 4 testi `does not pick an expired serial`.
5. **Formdaki ürün satırının serisi farklıysa** (hekim elle başka seri seçti) Ürün Adet yazılmamalı, "yanlış ürün" uyarısı çıkmalı → Task 6 testi `warns instead of filling quantity when another serial lands on the form`.

---

## Dosya Yapısı

**Backend** (`ENC=backend/src/main/java/com/vetos/modules/encounter`, `INV=…/modules/inventory`, `TB=…/modules/integration/tarbil`, test kökleri `TENC`, `TINV`, `TTB` aynı yollar `src/test` altında)
- `backend/src/main/resources/db/migration/V67__vaccination_inventory_item.sql` (yeni)
- `$ENC/domain/VaccinationRecord.java`, `$ENC/domain/VaccinationTarbilView.java`, `$ENC/domain/event/VaccinationRecordedEvent.java`, `$ENC/domain/event/VaccinationCancelledEvent.java` (yeni), `$ENC/application/dto/RecordVaccinationCommand.java`, `$ENC/api/dto/RecordVaccinationRequest.java`, `$ENC/api/VaccinationRecordsController.java`, `$ENC/application/RecordVaccinationUseCase.java`, `$ENC/application/MarkVaccinationAdministeredUseCase.java`, `$ENC/application/CancelVaccinationUseCase.java`, `$ENC/infrastructure/persistence/VaccinationLookupAdapter.java`
- `$INV/domain/StockReferenceType.java`, `$INV/domain/StockMovementRepository.java`, `$INV/infrastructure/persistence/StockMovementJpaRepository.java`, `$INV/infrastructure/persistence/StockMovementRepositoryAdapter.java`, `$INV/application/ApplyVaccinationStockUseCase.java` (yeni), `$INV/infrastructure/event/VaccinationStockEventListener.java` (yeni), `$INV/domain/InventoryItemLookupPort.java` (yeni), `$INV/infrastructure/InventoryItemLookupAdapter.java` (yeni)
- `$TB/application/TarbilSubmissionAssembler.java`, `$TB/application/dto/TarbilSubmissionView.java`, `$TB/api/dto/TarbilSubmissionResponse.java`

**Eklenti** (`extension/src/`): `shared/types.ts`, `shared/flowStore.ts`, `tarbil/selectors/vaccineReceipt.ts`, `tarbil/selectors/stock.ts`, `tarbil/selectors/allowlist.ts`, `tarbil/selectors/shared.ts`, `tarbil/page/ops.ts`, `tarbil/steps/productRows.ts` (yeni), `tarbil/pages/stockPopup.ts` (yeni), `tarbil/pages/vaccineReceipt.ts`, `tarbil/core/views.ts`, `tarbil/content.ts`

**Frontend**: `frontend/src/api/vaccinationApi.ts`, `frontend/src/pages/vaccinations/NewVaccinationPage.tsx`, `frontend/src/pages/vaccinations/VaccineStockPicker.tsx` (yeni)

---

### Task 1: Encounter — aşı kaydında stok kalemi ve olaylar

**Files:**
- Create: `backend/src/main/resources/db/migration/V67__vaccination_inventory_item.sql`, `$ENC/domain/event/VaccinationCancelledEvent.java`
- Modify: `$ENC/domain/VaccinationRecord.java`, `$ENC/domain/VaccinationTarbilView.java`, `$ENC/domain/event/VaccinationRecordedEvent.java`, `$ENC/application/dto/RecordVaccinationCommand.java`, `$ENC/api/dto/RecordVaccinationRequest.java`, `$ENC/api/VaccinationRecordsController.java`, `$ENC/application/RecordVaccinationUseCase.java`, `$ENC/application/MarkVaccinationAdministeredUseCase.java`, `$ENC/application/CancelVaccinationUseCase.java`, `$ENC/infrastructure/persistence/VaccinationLookupAdapter.java`
- Test: `$TENC/application/VaccinationStockEventsTest.java` (yeni)

**Interfaces:**
- Produces:
  - `VaccinationRecord.record(UUID tenantId, UUID patientId, UUID encounterId, String vaccineName, String lotNumber, LocalDate administeredDate, LocalDate nextDueDate, UUID administeredByStaffId, VaccinationStatus status, String notes, UUID inventoryItemId)` (yeni 11 parametreli; eski 10 parametreli `inventoryItemId=null` ile kalır) ve `getInventoryItemId()`
  - `record VaccinationRecordedEvent(UUID vaccinationRecordId, UUID patientId, String vaccineName, LocalDate administeredDate, UUID inventoryItemId)`
  - `record VaccinationCancelledEvent(UUID vaccinationRecordId, UUID inventoryItemId)`
  - `record VaccinationTarbilView(UUID id, UUID tenantId, UUID patientId, String vaccineName, String lotNumber, LocalDate administeredDate, VaccinationStatus status, UUID inventoryItemId)` (+ eski 7 parametreli kurucu, `inventoryItemId=null`)
  - `RecordVaccinationCommand` ve `RecordVaccinationRequest` sonuna `UUID inventoryItemId`

- [ ] **Step 1: Başarısız testi yaz**

`backend/src/test/java/com/vetos/modules/encounter/application/VaccinationStockEventsTest.java`:

```java
package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.RecordVaccinationCommand;
import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.event.VaccinationCancelledEvent;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.platform.event.DomainEventPublisher;
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
class VaccinationStockEventsTest {

    @Mock private VaccinationRecordRepository repository;
    @Mock private DomainEventPublisher publisher;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    private RecordVaccinationCommand command(VaccinationStatus status) {
        return new RecordVaccinationCommand(tenantId, UUID.randomUUID(), null, "Biocan R", "665932",
            LocalDate.of(2026, 10, 4), null, UUID.randomUUID(), status, null, itemId);
    }

    @Test
    void should_storeItemAndAnnounceIt_when_administeredFromStock() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new RecordVaccinationUseCase(repository, publisher).execute(command(VaccinationStatus.ADMINISTERED));

        ArgumentCaptor<VaccinationRecord> record = ArgumentCaptor.forClass(VaccinationRecord.class);
        verify(repository).save(record.capture());
        assertThat(record.getValue().getInventoryItemId()).isEqualTo(itemId);
        ArgumentCaptor<VaccinationRecordedEvent> event = ArgumentCaptor.forClass(VaccinationRecordedEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().inventoryItemId()).isEqualTo(itemId);
    }

    @Test
    void should_notAnnounce_when_onlyScheduled() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new RecordVaccinationUseCase(repository, publisher).execute(command(VaccinationStatus.SCHEDULED));

        verify(publisher, never()).publish(any());
    }

    @Test
    void should_announceItem_when_scheduledVaccinationMarkedAdministered() {
        UUID id = UUID.randomUUID();
        VaccinationRecord record = VaccinationRecord.record(tenantId, UUID.randomUUID(), null, "Biocan R", "665932",
            LocalDate.of(2026, 10, 4), null, UUID.randomUUID(), VaccinationStatus.SCHEDULED, null, itemId);
        when(repository.findById(id)).thenReturn(Optional.of(record));

        new MarkVaccinationAdministeredUseCase(repository, publisher).execute(id, null);

        ArgumentCaptor<VaccinationRecordedEvent> event = ArgumentCaptor.forClass(VaccinationRecordedEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().inventoryItemId()).isEqualTo(itemId);
    }

    @Test
    void should_announceCancellationWithItem() {
        UUID id = UUID.randomUUID();
        VaccinationRecord record = VaccinationRecord.record(tenantId, UUID.randomUUID(), null, "Biocan R", "665932",
            LocalDate.of(2026, 10, 4), null, UUID.randomUUID(), VaccinationStatus.ADMINISTERED, null, itemId);
        when(repository.findById(id)).thenReturn(Optional.of(record));

        new CancelVaccinationUseCase(repository, publisher).execute(id);

        assertThat(record.getStatus()).isEqualTo(VaccinationStatus.CANCELLED);
        ArgumentCaptor<VaccinationCancelledEvent> event = ArgumentCaptor.forClass(VaccinationCancelledEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().inventoryItemId()).isEqualTo(itemId);
    }
}
```

- [ ] **Step 2: Testi çalıştır, başarısız olduğunu gör**

Run: `cd backend && ./mvnw -q test -Dtest=VaccinationStockEventsTest > /tmp/p2t1.log 2>&1; echo exit=$?; grep -E "symbol:|location:" /tmp/p2t1.log | sort -u | head -6`
Expected: `exit=1`; `VaccinationCancelledEvent`, `getInventoryItemId`, `inventoryItemId()` bulunamıyor / kurucu uyuşmuyor.

- [ ] **Step 3: Migration, varlık, olaylar**

`backend/src/main/resources/db/migration/V67__vaccination_inventory_item.sql`:

```sql
-- TARBIL P2 (spec 2026-10-04 P2 S3.1): asi kaydinin stok kalemi. FK yok (moduller arasi kimlik); yalniz sutun eklenir.
ALTER TABLE vaccination_records ADD COLUMN inventory_item_id UUID;
```

`$ENC/domain/VaccinationRecord.java`: `reminderSent` alanının altına ekle:

```java

    /** Stoktan secilen asi (spec 2026-10-04 P2): inventory modulundeki kalem; serbest yazilmis asida null. */
    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;
```

ve mevcut `record(...)` metodunu şu iki metotla değiştir:

```java
    public static VaccinationRecord record(
        UUID tenantId, UUID patientId, UUID encounterId, String vaccineName, String lotNumber,
        LocalDate administeredDate, LocalDate nextDueDate, UUID administeredByStaffId,
        VaccinationStatus status, String notes
    ) {
        return record(tenantId, patientId, encounterId, vaccineName, lotNumber, administeredDate, nextDueDate,
            administeredByStaffId, status, notes, null);
    }

    public static VaccinationRecord record(
        UUID tenantId, UUID patientId, UUID encounterId, String vaccineName, String lotNumber,
        LocalDate administeredDate, LocalDate nextDueDate, UUID administeredByStaffId,
        VaccinationStatus status, String notes, UUID inventoryItemId
    ) {
        VaccinationRecord record = new VaccinationRecord();
        record.tenantId = tenantId;
        record.patientId = patientId;
        record.encounterId = encounterId;
        record.vaccineName = vaccineName;
        record.lotNumber = lotNumber;
        record.administeredDate = administeredDate;
        record.nextDueDate = nextDueDate;
        record.administeredByStaffId = administeredByStaffId;
        record.status = status;
        record.notes = notes;
        record.reminderSent = false;
        record.inventoryItemId = inventoryItemId;
        return record;
    }
```

`$ENC/domain/event/VaccinationRecordedEvent.java`:

```java
package com.vetos.modules.encounter.domain.event;

import java.time.LocalDate;
import java.util.UUID;

/** Asi uygulandi (ADMINISTERED). inventoryItemId: stoktan secildiyse kalem, aksi halde null (spec 2026-10-04 P2). */
public record VaccinationRecordedEvent(UUID vaccinationRecordId, UUID patientId, String vaccineName, LocalDate administeredDate,
                                       UUID inventoryItemId) {}
```

`$ENC/domain/event/VaccinationCancelledEvent.java`:

```java
package com.vetos.modules.encounter.domain.event;

import java.util.UUID;

/** Asi kaydi iptal edildi; inventory dusulmus adedi geri ekler (spec 2026-10-04 P2 S3.3). */
public record VaccinationCancelledEvent(UUID vaccinationRecordId, UUID inventoryItemId) {}
```

`$ENC/domain/VaccinationTarbilView.java`:

```java
package com.vetos.modules.encounter.domain;

import java.time.LocalDate;
import java.util.UUID;

/** integration/tarbil modulunun asi aktarimi icin ihtiyac duydugu, salt-okunur gorunum. */
public record VaccinationTarbilView(
    UUID id, UUID tenantId, UUID patientId, String vaccineName, String lotNumber,
    LocalDate administeredDate, VaccinationStatus status, UUID inventoryItemId
) {
    public VaccinationTarbilView(UUID id, UUID tenantId, UUID patientId, String vaccineName, String lotNumber,
                                 LocalDate administeredDate, VaccinationStatus status) {
        this(id, tenantId, patientId, vaccineName, lotNumber, administeredDate, status, null);
    }
}
```

`$ENC/infrastructure/persistence/VaccinationLookupAdapter.java` `findForTarbil` içinde `r.getAdministeredDate(), r.getStatus()` satırını `r.getAdministeredDate(), r.getStatus(), r.getInventoryItemId()` yap.

- [ ] **Step 4: Komut, istek, denetleyici, kullanım senaryoları**

`$ENC/application/dto/RecordVaccinationCommand.java` alan listesinin sonuna (`String notes` satırından sonra, virgül ekleyerek) `UUID inventoryItemId` ekle.

`$ENC/api/dto/RecordVaccinationRequest.java` alan listesinin sonuna (`String notes` sonrası) `UUID inventoryItemId` ekle.

`$ENC/api/VaccinationRecordsController.java` `record` metodundaki komut kurulumunu şu hale getir:

```java
        UUID id = recordVaccinationUseCase.execute(new RecordVaccinationCommand(
            TenantContext.current(), request.patientId(), request.encounterId(), request.vaccineName(), request.lotNumber(),
            request.administeredDate(), request.nextDueDate(), principal.staffUserId(), request.status(), request.notes(),
            request.inventoryItemId()
        ));
```

`$ENC/application/RecordVaccinationUseCase.java`: `VaccinationRecord.record(` çağrısının son satırını `command.status(), command.notes(), command.inventoryItemId()` yap ve olay yayınını şuna çevir:

```java
            eventPublisher.publish(new VaccinationRecordedEvent(
                record.getId(), record.getPatientId(), record.getVaccineName(), record.getAdministeredDate(), record.getInventoryItemId()
            ));
```

`$ENC/application/MarkVaccinationAdministeredUseCase.java`: olay yayınını aynı biçimde `record.getInventoryItemId()` ekleyerek güncelle.

`$ENC/application/CancelVaccinationUseCase.java`:

```java
package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.event.VaccinationCancelledEvent;
import com.vetos.modules.encounter.domain.exception.VaccinationRecordNotFoundException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelVaccinationUseCase {

    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID vaccinationRecordId) {
        VaccinationRecord record = vaccinationRecordRepository.findById(vaccinationRecordId)
            .orElseThrow(() -> new VaccinationRecordNotFoundException(vaccinationRecordId));
        record.cancel();
        vaccinationRecordRepository.save(record);
        // Stoktan dusulmus adet varsa inventory geri ekler (dusulmemisse -- SCHEDULED iptali -- hicbir sey yapmaz).
        eventPublisher.publish(new VaccinationCancelledEvent(record.getId(), record.getInventoryItemId()));
    }
}
```

- [ ] **Step 5: Testleri çalıştır, geçtiğini gör**

Run: `cd backend && ./mvnw -q test -Dtest='VaccinationStockEventsTest,VaccinationLookupAdapterTest,ApplicationModulesTest' > /tmp/p2t1.log 2>&1; echo exit=$?; grep -E "Tests run:.*Fail|ERROR\]" /tmp/p2t1.log | head`
Expected: `exit=0`.

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "feat(encounter): asi kaydinda stok kalemi; uygulandi/iptal olaylari kalemi tasir"
```

---

### Task 2: Inventory — aşı olaylarıyla stok düş / geri ekle; TARBİL ürün adı portu

**Files:**
- Create: `$INV/application/ApplyVaccinationStockUseCase.java`, `$INV/infrastructure/event/VaccinationStockEventListener.java`, `$INV/domain/InventoryItemLookupPort.java`, `$INV/infrastructure/InventoryItemLookupAdapter.java`
- Modify: `$INV/domain/StockReferenceType.java`, `$INV/domain/StockMovementRepository.java`, `$INV/infrastructure/persistence/StockMovementJpaRepository.java`, `$INV/infrastructure/persistence/StockMovementRepositoryAdapter.java`
- Test: `$TINV/application/ApplyVaccinationStockUseCaseTest.java`, `$TINV/infrastructure/InventoryItemLookupAdapterTest.java`

**Interfaces:**
- Consumes: `VaccinationRecordedEvent(…, UUID inventoryItemId)`, `VaccinationCancelledEvent(UUID vaccinationRecordId, UUID inventoryItemId)` (Task 1).
- Produces:
  - `StockReferenceType.VACCINATION`
  - `StockMovementRepository.existsByReference(UUID referenceId, StockReferenceType referenceType, StockMovementType movementType): boolean`
  - `ApplyVaccinationStockUseCase.administered(UUID vaccinationRecordId, UUID inventoryItemId)`, `.cancelled(UUID vaccinationRecordId, UUID inventoryItemId)`
  - `interface InventoryItemLookupPort { Optional<String> findTarbilProductName(UUID inventoryItemId); }` — `tarbil_product_name`, yoksa kalem adı.

- [ ] **Step 1: Başarısız testleri yaz**

`backend/src/test/java/com/vetos/modules/inventory/application/ApplyVaccinationStockUseCaseTest.java`:

```java
package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyVaccinationStockUseCaseTest {

    @Mock private InventoryItemRepository items;
    @Mock private StockMovementRepository movements;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID vaccinationId = UUID.randomUUID();

    private ApplyVaccinationStockUseCase useCase() {
        return new ApplyVaccinationStockUseCase(items, movements);
    }

    private InventoryItem item(int qty) {
        return InventoryItem.create(tenantId, UUID.randomUUID(), "Biocan R", "Aşı", null, qty, 0, null, "665932", null);
    }

    @Test
    void should_deductOne_when_vaccinationAdministeredFromStock() {
        InventoryItem item = item(18);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId);

        assertThat(item.getQuantityOnHand()).isEqualTo(17);
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(m.capture());
        assertThat(m.getValue().getMovementType()).isEqualTo(StockMovementType.OUT);
        assertThat(m.getValue().getQuantity()).isEqualTo(1);
        assertThat(m.getValue().getReferenceType()).isEqualTo(StockReferenceType.VACCINATION);
        assertThat(m.getValue().getReferenceId()).isEqualTo(vaccinationId);
        verify(items).save(item);
    }

    @Test
    void should_deductOnlyOnce_when_sameVaccinationReportedTwice() {
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(true);

        useCase().administered(vaccinationId, itemId);

        verify(items, never()).findById(any());
        verify(movements, never()).save(any());
    }

    @Test
    void should_notGoNegative_when_stockIsEmpty() {
        InventoryItem item = item(0);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId);

        assertThat(item.getQuantityOnHand()).isZero();
        verify(movements, never()).save(any());
    }

    @Test
    void should_ignore_when_itemNotFound() {
        when(items.findById(itemId)).thenReturn(Optional.empty());
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().administered(vaccinationId, itemId);

        verify(movements, never()).save(any());
    }

    @Test
    void should_ignore_when_noItem() {
        useCase().administered(vaccinationId, null);
        useCase().cancelled(vaccinationId, null);

        verify(items, never()).findById(any());
        verify(movements, never()).save(any());
    }

    @Test
    void should_returnOne_when_deductedVaccinationCancelled() {
        InventoryItem item = item(17);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(true);
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.IN)).thenReturn(false);

        useCase().cancelled(vaccinationId, itemId);

        assertThat(item.getQuantityOnHand()).isEqualTo(18);
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(m.capture());
        assertThat(m.getValue().getMovementType()).isEqualTo(StockMovementType.IN);
    }

    @Test
    void should_notReturnStock_when_cancelledBeforeAnyDeduction() {
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(false);

        useCase().cancelled(vaccinationId, itemId);

        verify(items, never()).findById(any());
        verify(movements, never()).save(any());
    }

    @Test
    void should_returnOnlyOnce_when_cancelledTwice() {
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.OUT)).thenReturn(true);
        when(movements.existsByReference(vaccinationId, StockReferenceType.VACCINATION, StockMovementType.IN)).thenReturn(true);

        useCase().cancelled(vaccinationId, itemId);

        verify(movements, never()).save(any());
    }
}
```

`backend/src/test/java/com/vetos/modules/inventory/infrastructure/InventoryItemLookupAdapterTest.java`:

```java
package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryItemLookupAdapterTest {

    @Mock private InventoryItemRepository items;

    @Test
    void should_preferTarbilName_andFallBackToItemName() {
        UUID linked = UUID.randomUUID();
        UUID plain = UUID.randomUUID();
        InventoryItem a = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Kuduz aşısı", "Aşı", null, 1, 0, null, "L1", null);
        a.linkTarbil("HBSAPP_VACCINE", "Biocan R", "Flakon");
        InventoryItem b = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Nobivac", "Aşı", null, 1, 0, null, "L2", null);
        when(items.findById(linked)).thenReturn(Optional.of(a));
        when(items.findById(plain)).thenReturn(Optional.of(b));

        InventoryItemLookupAdapter adapter = new InventoryItemLookupAdapter(items);

        assertThat(adapter.findTarbilProductName(linked)).contains("Biocan R");
        assertThat(adapter.findTarbilProductName(plain)).contains("Nobivac");
        assertThat(adapter.findTarbilProductName(null)).isEmpty();
    }
}
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd backend && ./mvnw -q test -Dtest='ApplyVaccinationStockUseCaseTest,InventoryItemLookupAdapterTest' > /tmp/p2t2.log 2>&1; echo exit=$?; grep -E "symbol:" /tmp/p2t2.log | sort -u | head -6`
Expected: `exit=1`; `ApplyVaccinationStockUseCase`, `existsByReference`, `VACCINATION`, `InventoryItemLookupAdapter` bulunamıyor.

- [ ] **Step 3: Uygulama**

`$INV/domain/StockReferenceType.java`:

```java
package com.vetos.modules.inventory.domain;

public enum StockReferenceType { ENCOUNTER, PURCHASE_ORDER, MANUAL, TARBIL_SYNC, VACCINATION }
```

`$INV/domain/StockMovementRepository.java` arayüzüne ekle:

```java
    /** Ayni kaynak (ornegin asi kaydi) icin bu turde hareket zaten yazildi mi -- cift olaya karsi. */
    boolean existsByReference(UUID referenceId, StockReferenceType referenceType, StockMovementType movementType);
```

`$INV/infrastructure/persistence/StockMovementJpaRepository.java` arayüzüne ekle (import'lar: `com.vetos.modules.inventory.domain.StockMovementType`, `com.vetos.modules.inventory.domain.StockReferenceType`):

```java
    boolean existsByReferenceIdAndReferenceTypeAndMovementType(UUID referenceId, StockReferenceType referenceType, StockMovementType movementType);
```

`$INV/infrastructure/persistence/StockMovementRepositoryAdapter.java` sınıfına ekle (aynı import'lar):

```java
    @Override
    public boolean existsByReference(UUID referenceId, StockReferenceType referenceType, StockMovementType movementType) {
        return jpaRepository.existsByReferenceIdAndReferenceTypeAndMovementType(referenceId, referenceType, movementType);
    }
```

`$INV/application/ApplyVaccinationStockUseCase.java`:

```java
package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stoktan secilen asi (spec 2026-10-04 P2 S3.3): uygulaninca 1 OUT, iptalde (yalniz dusulmusse) 1 IN.
 * Ayni asi kaydi icin her yonde en fazla bir hareket; stok 0'da eksiye dusmez ve kayit yine olusur.
 */
@Service
@RequiredArgsConstructor
public class ApplyVaccinationStockUseCase {

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;

    @Transactional
    public void administered(UUID vaccinationRecordId, UUID inventoryItemId) {
        if (inventoryItemId == null
            || movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)) {
            return;
        }
        items.findById(inventoryItemId).filter(i -> i.getQuantityOnHand() > 0).ifPresent(item -> {
            item.adjustQuantity(-1);
            items.save(item);
            movements.save(StockMovement.record(item.getTenantId(), item.getId(), StockMovementType.OUT, 1,
                StockReferenceType.VACCINATION, vaccinationRecordId));
        });
    }

    @Transactional
    public void cancelled(UUID vaccinationRecordId, UUID inventoryItemId) {
        if (inventoryItemId == null
            || !movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)
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
}
```

`$INV/infrastructure/event/VaccinationStockEventListener.java`:

```java
package com.vetos.modules.inventory.infrastructure.event;

import com.vetos.modules.encounter.domain.event.VaccinationCancelledEvent;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.modules.inventory.application.ApplyVaccinationStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Asi uygulandi/iptal edildi -> stok (spec 2026-10-04 P2 S3.3). Ayni islem icinde; kiraci istekten gelir. */
@Component
@RequiredArgsConstructor
class VaccinationStockEventListener {

    private final ApplyVaccinationStockUseCase applyVaccinationStockUseCase;

    @EventListener
    void onVaccinationRecorded(VaccinationRecordedEvent event) {
        applyVaccinationStockUseCase.administered(event.vaccinationRecordId(), event.inventoryItemId());
    }

    @EventListener
    void onVaccinationCancelled(VaccinationCancelledEvent event) {
        applyVaccinationStockUseCase.cancelled(event.vaccinationRecordId(), event.inventoryItemId());
    }
}
```

`$INV/domain/InventoryItemLookupPort.java`:

```java
package com.vetos.modules.inventory.domain;

import java.util.Optional;
import java.util.UUID;

/** integration/tarbil icin: asinin stok kaleminin TARBIL'deki urun adi (yoksa kalem adi). */
public interface InventoryItemLookupPort {
    Optional<String> findTarbilProductName(UUID inventoryItemId);
}
```

`$INV/infrastructure/InventoryItemLookupAdapter.java`:

```java
package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItemLookupPort;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class InventoryItemLookupAdapter implements InventoryItemLookupPort {

    private final InventoryItemRepository items;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findTarbilProductName(UUID inventoryItemId) {
        if (inventoryItemId == null) return Optional.empty();
        return items.findById(inventoryItemId)
            .map(i -> i.getTarbilProductName() != null ? i.getTarbilProductName() : i.getName());
    }
}
```

- [ ] **Step 4: Testleri çalıştır, geçtiğini gör**

Run: `cd backend && ./mvnw -q test -Dtest='ApplyVaccinationStockUseCaseTest,InventoryItemLookupAdapterTest,TarbilStockSyncAdapterTest,ApplicationModulesTest' > /tmp/p2t2.log 2>&1; echo exit=$?`
Expected: `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src
git commit -m "feat(inventory): asi olaylariyla stoktan 1 dus / iptalde geri ekle; TARBIL urun adi portu"
```

---

### Task 3: TARBİL — eklentiye ürün adı + uçtan uca stok testi

**Files:**
- Modify: `$TB/application/TarbilSubmissionAssembler.java`, `$TB/application/dto/TarbilSubmissionView.java`, `$TB/api/dto/TarbilSubmissionResponse.java`; testler `$TTB/application/{GetSubmissionUseCaseTest,GetTarbilStatusSummaryUseCaseTest,ListPendingSubmissionsUseCaseTest,ListTarbilSyncLogsUseCaseTest,MarkSubmittedUseCaseTest}.java`, `backend/src/test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java`

**Interfaces:**
- Consumes: `InventoryItemLookupPort.findTarbilProductName(UUID)` (Task 2), `VaccinationTarbilView.inventoryItemId()` (Task 1).
- Produces: `TarbilSubmissionView` ve `TarbilSubmissionResponse`'ta `passportNumber`'dan sonra değil, `lotNumber`'dan hemen sonra `String tarbilProductName`. JSON alanı `tarbilProductName`.

- [ ] **Step 1: Başarısız testleri yaz**

Beş test dosyasında `new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository)` ifadesini `new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository, inventoryItemLookupPort)` yap ve her birinin `@Mock` alanlarına ekle (import `com.vetos.modules.inventory.domain.InventoryItemLookupPort`):

```java
    @Mock private InventoryItemLookupPort inventoryItemLookupPort;
```

(Mockito `Optional` dönen metotlar için varsayılan olarak `Optional.empty()` döner; ek `when` gerekmez.)

`ListPendingSubmissionsUseCaseTest.should_includeMappingsAndPatientData_when_pendingVaccinationExists` içinde: `VaccinationTarbilView` kurulumunu son argümana bir kalem ekleyerek değiştir:

```java
        UUID itemId = UUID.randomUUID();
        when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, patientId, "Kuduz Aşısı", "L-1", LocalDate.of(2026, 10, 1), VaccinationStatus.ADMINISTERED, itemId)));
        when(inventoryItemLookupPort.findTarbilProductName(itemId)).thenReturn(Optional.of("Biocan R"));
```

ve sonundaki doğrulamalara ekle:

```java
        assertThat(view.tarbilProductName()).isEqualTo("Biocan R");
```

`TarbilExtensionSecurityIntegrationTest`'e ekle (import'lar: `com.vetos.modules.inventory.domain.InventoryItem`, `com.vetos.modules.inventory.domain.InventoryItemRepository`; alan `@Autowired private InventoryItemRepository inventoryItemRepository;`):

```java
    @Test
    void vaccinationFromStockDeductsOneAndCancellationReturnsIt() throws Exception {
        UUID branchA = inRootSession(() -> branchRepository.save(Branch.create(tenantA, "Aşı Şubesi")).getId());
        String jwt = jwtTokenProvider.generateToken(staffA, tenantA, List.of(branchA), "VET");
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID owner = asTenant(tenantA, () -> ownerRepository.save(Owner.register(tenantA, "A Sahip", "05550000000", null, null)).getId());
        UUID patient = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, owner, speciesId, null, "Pamuk", Sex.FEMALE, null)).getId());
        UUID item = asTenant(tenantA, () -> inventoryItemRepository.save(
            InventoryItem.create(tenantA, branchA, "Biocan R", "Aşı", null, 18, 0, LocalDate.of(2027, 1, 31), "665932", null)).getId());
        String body = "{\"patientId\":\"" + patient + "\",\"vaccineName\":\"Biocan R\",\"lotNumber\":\"665932\","
            + "\"administeredDate\":\"" + LocalDate.now() + "\",\"status\":\"ADMINISTERED\",\"inventoryItemId\":\"" + item + "\"}";

        String location = mockMvc.perform(post("/api/v1/vaccination-records").header("Authorization", "Bearer " + jwt)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");
        Integer afterRecord = jdbcTemplate.queryForObject("SELECT quantity_on_hand FROM inventory_items WHERE id = ?", Integer.class, item);
        assertThat(afterRecord).isEqualTo(17);

        mockMvc.perform(post(location + "/cancel").header("Authorization", "Bearer " + jwt))
            .andExpect(status().is2xxSuccessful());
        Integer afterCancel = jdbcTemplate.queryForObject("SELECT quantity_on_hand FROM inventory_items WHERE id = ?", Integer.class, item);
        assertThat(afterCancel).isEqualTo(18);
    }
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd backend && ./mvnw -q test -Dtest='ListPendingSubmissionsUseCaseTest' > /tmp/p2t3.log 2>&1; echo exit=$?; grep -E "symbol:|constructor" /tmp/p2t3.log | sort -u | head -4`
Expected: `exit=1`; `TarbilSubmissionAssembler` kurucusu / `tarbilProductName()` uyuşmuyor.

- [ ] **Step 3: Uygulama**

`$TB/application/dto/TarbilSubmissionView.java`: `String vaccineName, String lotNumber, LocalDate administeredDate,` satırını `String vaccineName, String lotNumber, String tarbilProductName, LocalDate administeredDate,` yap.

`$TB/api/dto/TarbilSubmissionResponse.java`: kayıt bileşenlerinde `String vaccineName, String lotNumber, LocalDate administeredDate,` → `String vaccineName, String lotNumber, String tarbilProductName, LocalDate administeredDate,`; `from` içinde `v.vaccineName(), v.lotNumber(),` → `v.vaccineName(), v.lotNumber(), v.tarbilProductName(),`.

`$TB/application/TarbilSubmissionAssembler.java`:
1. import ekle: `com.vetos.modules.inventory.domain.InventoryItemLookupPort`.
2. alanlara (`mappingRepository`'den sonra) ekle: `private final InventoryItemLookupPort inventoryItemLookupPort;`
3. `assemble` içinde `v.vaccineName(), v.lotNumber(), v.administeredDate(),` satırını şuna çevir:
```java
            v.vaccineName(), v.lotNumber(), inventoryItemLookupPort.findTarbilProductName(v.inventoryItemId()).orElse(null),
            v.administeredDate(),
```

- [ ] **Step 4: Tüm backend testleri**

Docker'daki Postgres (5433) açık olmalı. V67 yalnız sütun eklediği için hekimin çalışan backend'i etkilenmez.

Run: `cd backend && ./mvnw -q test > /tmp/p2t3b.log 2>&1; echo exit=$?; grep -lE "<(failure|error)" target/surefire-reports/TEST-*.xml | head`
Expected: `exit=0`; ikinci komut boş.

- [ ] **Step 5: Commit**

```bash
git add backend/src
git commit -m "feat(tarbil): eklentiye asinin TARBIL urun adi; asi-stok uctan uca testi"
```

---

### Task 4: Eklenti — seçiciler, izin/yasak listesi, stok penceresi ve ürün satırı okuyucuları, MAIN komutları

**Files:**
- Create: `extension/src/tarbil/steps/productRows.ts`, `extension/src/tarbil/steps/productRows.test.ts`
- Modify: `extension/src/tarbil/selectors/vaccineReceipt.ts`, `extension/src/tarbil/selectors/stock.ts`, `extension/src/tarbil/selectors/allowlist.ts`, `extension/src/tarbil/selectors/allowlist.test.ts`, `extension/src/tarbil/selectors/shared.ts`, `extension/src/tarbil/selectors/shared.test.ts`, `extension/src/tarbil/page/ops.ts`, `extension/src/tarbil/page/ops.test.ts`, `extension/src/shared/types.ts`

**Interfaces:**
- Produces:
  - `Submission.tarbilProductName: string | null`
  - `RECEIPT.addProduct`, `RECEIPT.productGrid`, `RECEIPT.productQuantity`; `VACCINE_STOCK_POPUP = { serial, search, grid }`
  - `PageKind` + `'vaccineStockPopup'`
  - `ALLOWED_BUTTONS.vaccineReceipt.addProduct`, `ALLOWED_BUTTONS.vaccineStockPopup.search`; `FORBIDDEN_BUTTON_PATTERNS` + `/PerformInsertButton$/i`
  - MAIN: `searchSerial({ serial })`, `selectStockRow({ linkId })`, `setProductQuantity({ quantity })`
  - `normalizeSerial(s): string`; `interface StockPopupRow { productName: string; serial: string; expiryDate: string | null; linkId: string }`; `readStockPopupRows(doc): StockPopupRow[]`; `type StockPick = { kind: 'one'; row: StockPopupRow } | { kind: 'none' } | { kind: 'many' } | { kind: 'expired'; row: StockPopupRow } | { kind: 'nameMismatch'; row: StockPopupRow }`; `pickStockRow(rows, { serial, productName, today }): StockPick`; `readProductEditRow(doc): { serial: string } | null`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/steps/productRows.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { normalizeSerial, pickStockRow, readProductEditRow, readStockPopupRows } from './productRows';

const SP = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_radGridStock_ctl00';
const PG = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_RadGridProduct_ctl00';

function popup(rows: [string, string, string][]) {
  document.body.innerHTML = `<table id="${SP}"><thead><tr><th></th><th>Aşı Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
    <th>Son Kullanma Tarihi</th><th>Ürün Miktarı</th><th>Ruhsat Sahibi Tip</th><th>Ruhsat Sahibi İsim</th></tr></thead><tbody>${rows
      .map(([name, serial, skt], i) => `<tr id="${SP}__${i}"><td><a id="${SP}_ctl0${4 + 2 * i}_SelectlinkButton">Seç</a></td>
        <td>${name}</td><td>Flakon</td><td>${serial}</td><td>${skt}</td><td>3</td><td>Firma</td><td>X</td></tr>`)
      .join('')}</tbody></table>`;
}

describe('stock popup rows', () => {
  it('reads name, serial, expiry and the Seç link of each row', () => {
    popup([['Biocan R', ' 665932 ', '31.01.2027']]);

    expect(readStockPopupRows(document)).toEqual([
      { productName: 'Biocan R', serial: '665932', expiryDate: '2027-01-31', linkId: `${SP}_ctl04_SelectlinkButton` },
    ]);
  });

  it('picks the single row with the same serial and product name', () => {
    popup([['Biocan R', '665932', '31.01.2027'], ['Biocan DHPPI', '145932', '31.01.2027']]);

    const pick = pickStockRow(readStockPopupRows(document), { serial: '665932', productName: 'biocan  r', today: '2026-10-04' });

    expect(pick).toMatchObject({ kind: 'one', row: { serial: '665932' } });
  });

  it('accepts any name when Vetly has no TARBIL product name', () => {
    popup([['Biocan R', '665932', '31.01.2027']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '665932', productName: null, today: '2026-10-04' }).kind).toBe('one');
  });

  it('refuses a serial whose product name differs', () => {
    popup([['Nobivac', '665932', '31.01.2027']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '665932', productName: 'Biocan R', today: '2026-10-04' }).kind).toBe('nameMismatch');
  });

  it('refuses to choose between several rows', () => {
    popup([['Biocan R', '665932', '31.01.2027'], ['Biocan R', '665932', '31.01.2027']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '665932', productName: 'Biocan R', today: '2026-10-04' }).kind).toBe('many');
  });

  it('does not pick an expired serial', () => {
    popup([['Biocan R', '665932', '01.10.2026']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '665932', productName: 'Biocan R', today: '2026-10-04' }).kind).toBe('expired');
  });

  it('finds nothing for an unknown serial', () => {
    popup([['Biocan R', '665932', '31.01.2027']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '000', productName: null, today: '2026-10-04' })).toEqual({ kind: 'none' });
  });

  it('normalizes serials', () => {
    expect(normalizeSerial(' ab 12-3 ')).toBe('AB12-3');
  });
});

describe('product edit row', () => {
  it('reads the serial of the product row TARBIL draws in the grid header', () => {
    document.body.innerHTML = `<table id="${PG}"><thead><tr class="rgCommandRow"><td>Ürün Ekle</td></tr>
      <tr><th>Detay</th><th>Detay</th><th>Ürün</th><th>Takdim Şekli</th><th>Seri Numarası</th><th>Son Kullanım Tarihi</th>
      <th>Stok Miktarı</th><th>Stok Tipi</th><th>Ürün Adet</th><th></th><th>Sil</th></tr>
      <tr class="rgEditRow"><td></td><td></td><td><input></td><td>Flakon</td><td> 665932 </td><td>31.01.2027</td>
      <td>18 Adet</td><td>Parça Stok</td><td><input></td><td></td><td></td></tr></thead>
      <tbody><tr class="rgNoRecords"><td colspan="11">Kayıt Bulunamadı.</td></tr></tbody></table>`;

    expect(readProductEditRow(document)).toEqual({ serial: '665932' });
  });

  it('returns null while no product row is open', () => {
    document.body.innerHTML = `<table id="${PG}"><thead><tr><th>Seri Numarası</th></tr></thead><tbody></tbody></table>`;
    expect(readProductEditRow(document)).toBeNull();
  });
});
```

`extension/src/tarbil/selectors/allowlist.test.ts` dosyasının sonuna (son `});`'den önce, en dıştaki `describe` içine) ekle:

```ts
  it('allows Ürün Ekle and the stock popup search but never the product row Kaydet', () => {
    expect(allowedButtonSuffix('vaccineReceipt', 'addProduct')).toBe('_RadGridProduct_ctl00_ctl02_ctl00_InitInsertButton');
    expect(allowedButtonSuffix('vaccineStockPopup', 'search')).toBe('_UcVaccineStockSearch_btnSearch');
    expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test('RadGridProduct_ctl00_ctl02_ctl03_PerformInsertButton'))).toBe(true);
  });
```

(`FORBIDDEN_BUTTON_PATTERNS` ve `allowedButtonSuffix` dosyada zaten import ediliyor; değilse import satırına ekle.)

`extension/src/tarbil/pages/vaccineReceipt.test.ts` içindeki `base` nesnesinde (`satisfies Submission`) `lotNumber: 'L1',` satırını `lotNumber: 'L1', tarbilProductName: null,` yap (yeni zorunlu alan; aksi halde tsc hata verir).

`extension/src/tarbil/selectors/shared.test.ts` `describe('pageKind', ...)` bloğuna ekle:

```ts
  it('recognizes the vaccine stock popup', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ModalPages/UcVaccineStockSearchModalPage.aspx', search: '?animalid=x' })).toBe('vaccineStockPopup');
  });
```

`extension/src/tarbil/page/ops.test.ts` son `});`'den önce ekle:

```ts
  it('searches the stock popup by serial', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_';
    document.body.innerHTML = `<input id="${P}txtSerialNo"><a id="${P}btnSearch"></a>`;
    const prm = instantPrm();
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [`${P}txtSerialNo`]: { set_value: (v: string) => log.push(`serial:${v}`) },
      [`${P}btnSearch`]: { click: () => { log.push('ara'); prm.fire(); } },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchSerial({ serial: '665932' });

    expect(log).toEqual(['serial:665932', 'ara']);
  });

  it('clicks Seç only inside the stock popup grid', async () => {
    const G = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_radGridStock_ctl00';
    document.body.innerHTML = `<table id="${G}"><tbody><tr><td><a id="${G}_ctl04_SelectlinkButton">Seç</a></td></tr></tbody></table>
      <a id="other_SelectlinkButton">Seç</a>`;
    let clicked = 0;
    document.getElementById(`${G}_ctl04_SelectlinkButton`)!.addEventListener('click', () => clicked++);
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => null, isReady: () => true };
    const ops = createPageOps(env);

    await ops.selectStockRow({ linkId: `${G}_ctl04_SelectlinkButton` });
    await expect(ops.selectStockRow({ linkId: 'other_SelectlinkButton' })).rejects.toMatchObject({ code: 'NOT_FOUND' });

    expect(clicked).toBe(1);
  });

  it('types the dose count into the product row quantity box', async () => {
    const Q = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_RadGridProduct_ctl00_ctl02_ctl03_txtQuantity';
    document.body.innerHTML = `<input id="${Q}">`;
    const log: string[] = [];
    const env: TelerikEnv = { doc: document, find: (id) => (id === Q ? { set_value: (v: string) => log.push(v) } : null), prm: () => null, isReady: () => true };

    await createPageOps(env).setProductQuantity({ quantity: 1 });

    expect(log).toEqual(['1']);
  });
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/steps/productRows.test.ts src/tarbil/selectors src/tarbil/page/ops.test.ts 2>&1 | grep -E "resolve|×|Tests "`
Expected: `./productRows` çözümlenemiyor; allowlist/pageKind/ops yeni testleri başarısız.

- [ ] **Step 3: Seçiciler, tipler, izin listesi, sayfa türü**

`extension/src/shared/types.ts` `Submission` içinde `lotNumber: string | null;` satırının altına ekle:

```ts
  /** Stoktan secilen asinin TARBIL urun adi (spec 2026-10-04 P2); stok baglantisi yoksa null. */
  tarbilProductName: string | null;
```

`extension/src/tarbil/selectors/vaccineReceipt.ts` `RECEIPT` nesnesine `animalGrid` satırından sonra ekle:

```ts
  // Urun bolumu (2026-10-04 canli): "Urun Ekle" postback'i stok penceresini acar; secilen urun thead'de rgEditRow olur.
  addProduct: '_RadGridProduct_ctl00_ctl02_ctl00_InitInsertButton',
  productGrid: '_RadGridProduct_ctl00',
  productQuantity: '_RadGridProduct_ctl00_ctl02_ctl03_txtQuantity',
```

`extension/src/tarbil/selectors/stock.ts` sonuna ekle:

```ts

/** hbsapp asi belgesi stok penceresi (UcVaccineStockSearchModalPage.aspx). "Sec" baglantisi pencereyi kapatir. */
export const VACCINE_STOCK_POPUP = {
  serial: '_UcVaccineStockSearch_txtSerialNo',
  search: '_UcVaccineStockSearch_btnSearch',
  grid: '_UcVaccineStockSearch_radGridStock_ctl00',
} as const;
```

`extension/src/tarbil/selectors/allowlist.ts`:
1. import satırını `import { MEDICINE_STOCK, VACCINE_STOCK, VACCINE_STOCK_POPUP } from './stock';` yap.
2. `vaccineReceipt: { petVet: RECEIPT.petVet },` satırını şununla değiştir:
```ts
  vaccineReceipt: { petVet: RECEIPT.petVet, addProduct: RECEIPT.addProduct },
  vaccineStockPopup: { search: VACCINE_STOCK_POPUP.search },
```
3. `FORBIDDEN_BUTTON_PATTERNS` satırını şununla değiştir:
```ts
// PerformInsertButton: asi urun satiri "Kaydet" -- TARBIL stogundan duser (2026-10-04 hekim bildirdi), resmi islem sayilir.
export const FORBIDDEN_BUTTON_PATTERNS: readonly RegExp[] = [/btnInsert2?$/i, /btnApprove$/i, /btnReject$/i, /exit/i, /PerformInsertButton$/i];
```

`extension/src/tarbil/selectors/shared.ts`:
1. `const MEDICINE_STOCK_PATH = '/pages/stocksearch.aspx';` satırının altına ekle:
```ts
const VACCINE_STOCK_POPUP_FILE = 'ucvaccinestocksearchmodalpage.aspx';
```
2. `PageKind` tipine `'vaccineStockPopup'` ekle:
```ts
export type PageKind = 'receipt' | 'search' | 'home' | 'vaccineStock' | 'medicineStock' | 'vaccineStockPopup' | 'other';
```
3. `pageKind` içinde `if (path.endsWith(`/${SEARCH_PAGE_FILE}`)) return 'search';` satırının altına ekle:
```ts
  if (path.endsWith(`/${VACCINE_STOCK_POPUP_FILE}`)) return 'vaccineStockPopup';
```

- [ ] **Step 4: Okuyucular**

`extension/src/tarbil/steps/productRows.ts`:

```ts
import { RECEIPT, VACCINE_STOCK_POPUP, bySuffix } from '../selectors';

// Gizlilik: yalniz urun adi, seri, son kullanma tarihi ve "Sec" baglantisinin id'si okunur (urun verisi, kisisel veri yok).

export interface StockPopupRow {
  productName: string;
  serial: string;
  expiryDate: string | null;
  linkId: string;
}

export type StockPick =
  | { kind: 'one'; row: StockPopupRow }
  | { kind: 'none' }
  | { kind: 'many' }
  | { kind: 'expired'; row: StockPopupRow }
  | { kind: 'nameMismatch'; row: StockPopupRow };

const clean = (s: string | null | undefined) => (s ?? '').replace(/\s+/g, ' ').trim();

export function normalizeSerial(s: string | null | undefined): string {
  return clean(s).replace(/\s+/g, '').toLocaleUpperCase('tr-TR');
}

const normalizeName = (s: string | null | undefined) => clean(s).toLocaleUpperCase('tr-TR');

/** "31.01.2027" ya da "31.01.2027 00:00:00" -> "2027-01-31". */
function isoDate(s: string | null | undefined): string | null {
  const m = /(\d{2})\.(\d{2})\.(\d{4})/.exec(clean(s));
  return m ? `${m[3]}-${m[2]}-${m[1]}` : null;
}

function headerIndex(table: HTMLTableElement, name: string, fallback: number): number {
  const rows = table.tHead?.rows;
  if (!rows) return fallback;
  for (const row of Array.from(rows)) {
    const i = Array.from(row.cells).findIndex((c) => clean(c.textContent) === name);
    if (i >= 0) return i;
  }
  return fallback;
}

export function readStockPopupRows(doc: Document): StockPopupRow[] {
  const table = doc.querySelector<HTMLTableElement>(`table${bySuffix(VACCINE_STOCK_POPUP.grid)}`);
  if (!table) return [];
  const name = headerIndex(table, 'Aşı Adı', 1);
  const serial = headerIndex(table, 'Seri Numarası', 3);
  const expiry = headerIndex(table, 'Son Kullanma Tarihi', 4);
  return Array.from(table.tBodies[0]?.rows ?? [])
    .filter((r) => r.id.startsWith(`${table.id}__`))
    .map((r) => ({
      productName: clean(r.cells[name]?.textContent),
      serial: normalizeSerial(r.cells[serial]?.textContent),
      expiryDate: isoDate(r.cells[expiry]?.textContent),
      linkId: r.querySelector<HTMLAnchorElement>('a[id$="SelectlinkButton"]')?.id ?? '',
    }))
    .filter((r) => r.serial && r.linkId);
}

/** Seri birebir; Vetly'de TARBIL urun adi varsa o da birebir (normalize). Tek eslesme ve SKT >= bugun ise secilir. */
export function pickStockRow(rows: StockPopupRow[], want: { serial: string; productName: string | null; today: string }): StockPick {
  const serial = normalizeSerial(want.serial);
  const matches = rows.filter((r) => r.serial === serial);
  if (matches.length === 0) return { kind: 'none' };
  if (matches.length > 1) return { kind: 'many' };
  const row = matches[0];
  if (want.productName && normalizeName(row.productName) !== normalizeName(want.productName)) return { kind: 'nameMismatch', row };
  if (row.expiryDate && row.expiryDate < want.today) return { kind: 'expired', row };
  return { kind: 'one', row };
}

/** Stoktan secilen urun TARBIL'de urun tablosunun thead'ine rgEditRow olarak cizilir (2026-10-04 canli). */
export function readProductEditRow(doc: Document): { serial: string } | null {
  const table = doc.querySelector<HTMLTableElement>(`table${bySuffix(RECEIPT.productGrid)}`);
  const edit = table?.tHead?.querySelector<HTMLTableRowElement>('tr.rgEditRow');
  if (!table || !edit) return null;
  const serial = headerIndex(table, 'Seri Numarası', 4);
  return { serial: normalizeSerial(edit.cells[serial]?.textContent) };
}
```

`normalizeName` iki boşluğu teke indirir (`clean`) — testteki `'biocan  r'` bu yüzden eşleşir.

- [ ] **Step 5: MAIN komutları**

`extension/src/tarbil/page/ops.ts`:
1. selectors import'unu `import { MEDICINE_STOCK, RECEIPT, SEARCH, VACCINE_STOCK, VACCINE_STOCK_POPUP, allowedButtonSuffix, bySuffix } from '../selectors';` yap.
2. dönen nesneye `loadStockTable`'dan sonra ekle:
```ts
    // Asi stok penceresi (spec 2026-10-04 P2): seri ile ara, tek satirin "Sec" baglantisi, urun satirina adet.
    searchSerial: async ({ serial }: { serial: string }) => {
      setText(env, VACCINE_STOCK_POPUP.serial, serial);
      await clickButton(env, allowed('vaccineStockPopup', 'search'));
    },
    // Yalniz stok tablosundaki "Sec" baglantilari; tiklama pencereyi kapatir, sonuc beklenmez.
    selectStockRow: ({ linkId }: { linkId: string }) => {
      const link = env.doc.getElementById(linkId);
      if (!link || !link.matches(`table${bySuffix(VACCINE_STOCK_POPUP.grid)} a[id$="SelectlinkButton"]`)) {
        return Promise.reject(new PageError('NOT_FOUND', 'Stok tablosunda böyle bir Seç bağlantısı yok'));
      }
      (link as HTMLElement).click();
      return Promise.resolve();
    },
    setProductQuantity: async ({ quantity }: { quantity: number }) => {
      if (!Number.isInteger(quantity) || quantity < 1) throw new PageError('BAD_INPUT', `Geçersiz adet: ${quantity}`);
      setText(env, RECEIPT.productQuantity, String(quantity));
    },
```

- [ ] **Step 6: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "×|Test Files|Tests "`
Expected: tip hatası yok; tüm testler PASS.

- [ ] **Step 7: Commit**

```bash
git add extension/src
git commit -m "feat(tarbil-ext): asi stok penceresi/urun satiri okuyuculari, Urun Ekle izni, satir Kaydet yasagi"
```

---

### Task 5: Eklenti — stok penceresi akışı

**Files:**
- Create: `extension/src/tarbil/pages/stockPopup.ts`, `extension/src/tarbil/pages/stockPopup.test.ts`
- Modify: `extension/src/shared/flowStore.ts`, `extension/src/tarbil/core/views.ts`, `extension/src/tarbil/content.ts`

**Interfaces:**
- Consumes: `readStockPopupRows`, `pickStockRow` (Task 4); MAIN `searchSerial`, `selectStockRow`.
- Produces:
  - `FlowStep` + `'choosingProduct' | 'productReady'`
  - `PRODUCT_HANDOFF_MS = 120_000`; `runStockPopupFlow(d: StockPopupDeps): Promise<void>` (`StockPopupDeps = { bridge, flow, send, doc, card, now, today: () => string }`)
  - Başarısızlıkta akış: `step: 'awaitingConfirm'`, `message: <metin>` (ana sayfa bunu kartta gösterir, Task 6)
  - `views.stockPopup(text, tone?)`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/pages/stockPopup.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import type { BackgroundRequest } from '../../shared/messages';
import { createFlowStore, type FlowStep } from '../../shared/flowStore';
import type { Submission } from '../../shared/types';
import type { CardView } from '../core/card';
import type { Send } from '../steps/findAnimal';
import { runStockPopupFlow } from './stockPopup';

const SP = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_radGridStock_ctl00';

function popup(rows: [string, string, string][]) {
  document.body.innerHTML = `<table id="${SP}"><thead><tr><th></th><th>Aşı Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
    <th>Son Kullanma Tarihi</th><th>Ürün Miktarı</th></tr></thead><tbody>${rows
      .map(([name, serial, skt], i) => `<tr id="${SP}__${i}"><td><a id="${SP}_ctl0${4 + 2 * i}_SelectlinkButton">Seç</a></td>
        <td>${name}</td><td>Flakon</td><td>${serial}</td><td>${skt}</td><td>3</td></tr>`)
      .join('')}</tbody></table>`;
}

const submission = { id: 's1', status: 'PENDING', lotNumber: '665932', tarbilProductName: 'Biocan R' } as Submission;

async function setup(step: FlowStep = 'choosingProduct', sub: Submission = submission, age = 5000) {
  const flow = createFlowStore(memoryStore(), () => 1000);
  await flow.arm('s1');
  await flow.update('s1', { step });
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const send = (async (req: BackgroundRequest) => (req.type === 'GET_ACTIVE' ? { ok: true, data: sub } : { ok: true, data: null })) as Send;
  const shown: CardView[] = [];
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined };
  const deps = { bridge, flow, send, doc: document, card, now: () => 1000 + age, today: () => '2026-10-04' };
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  return { deps, flow, calls, text };
}

describe('runStockPopupFlow', () => {
  it('searches by the Vetly serial and selects the single matching row', async () => {
    popup([['Biocan R', '665932', '31.01.2027'], ['Biocan DHPPI', '145932', '31.01.2027']]);
    const { deps, calls } = await setup();

    await runStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchSerial', 'selectStockRow']);
    expect(calls[1].args).toEqual({ serial: '665932' });
    expect(calls[2].args).toEqual({ linkId: `${SP}_ctl04_SelectlinkButton` });
  });

  it('does nothing when the vet opened the window without Vetly', async () => {
    popup([['Biocan R', '665932', '31.01.2027']]);
    const { deps, calls } = await setup('awaitingConfirm');

    await runStockPopupFlow(deps);

    expect(calls).toEqual([]);
  });

  it('does nothing when the product step is stale', async () => {
    popup([['Biocan R', '665932', '31.01.2027']]);
    const { deps, calls } = await setup('choosingProduct', submission, 200_000);

    await runStockPopupFlow(deps);

    expect(calls).toEqual([]);
  });

  it('hands over to the vet when the serial is not in the clinic stock', async () => {
    popup([['Biocan R', '111111', '31.01.2027']]);
    const { deps, flow, calls, text } = await setup();

    await runStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchSerial']);
    expect(await flow.get()).toMatchObject({ step: 'awaitingConfirm' });
    expect((await flow.get())?.message).toContain('665932');
    expect(text()).toContain('bulunamadı');
  });

  it('does not pick an expired serial', async () => {
    popup([['Biocan R', '665932', '01.10.2026']]);
    const { deps, flow, calls } = await setup();

    await runStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchSerial']);
    expect((await flow.get())?.message).toContain('son kullanma');
  });

  it('hands over when the product name in TARBIL differs', async () => {
    popup([['Nobivac', '665932', '31.01.2027']]);
    const { deps, flow } = await setup();

    await runStockPopupFlow(deps);

    expect((await flow.get())?.message).toContain('Nobivac');
  });
});
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/pages/stockPopup.test.ts 2>&1 | grep -E "resolve|×|Tests "`
Expected: `./stockPopup` çözümlenemiyor.

- [ ] **Step 3: Akış adımları ve kart**

`extension/src/shared/flowStore.ts` `FlowStep` tipini şununla değiştir:

```ts
export type FlowStep =
  | 'armed' | 'filling' | 'searching' | 'transferred' | 'needsVet' | 'awaitingConfirm'
  | 'choosingProduct' | 'productReady' | 'done' | 'error';
```

`extension/src/tarbil/core/views.ts` `popup:` satırından önce ekle:

```ts
  stockPopup: (text: string, tone?: Tone): CardView => ({ lines: [{ text: 'Vetly', tone: 'strong' }, { text, tone }], actions: [] }),
```

- [ ] **Step 4: Stok penceresi akışı**

`extension/src/tarbil/pages/stockPopup.ts`:

```ts
import type { FlowStore } from '../../shared/flowStore';
import type { Submission } from '../../shared/types';
import type { PageBridge } from '../core/bridge';
import type { Card } from '../core/card';
import { views } from '../core/views';
import type { Send } from '../steps/findAnimal';
import { pickStockRow, readStockPopupRows } from '../steps/productRows';

export interface StockPopupDeps {
  bridge: PageBridge;
  flow: FlowStore;
  send: Send;
  doc: Document;
  card: Card;
  now: () => number;
  /** "yyyy-MM-dd" (SKT karsilastirmasi icin). */
  today: () => string;
}

/** Ana sayfa "Urun Ekle"ye bastiktan sonra pencerenin bu sure icinde acilmasi beklenir. */
export const PRODUCT_HANDOFF_MS = 120_000;

const code = (e: unknown) => (e as { code?: string })?.code ?? 'UNKNOWN';

/**
 * Asi stok penceresi (spec 2026-10-04 P2 S4.1): Vetly serisi ile aranir; seri + urun adi birebir TEK ve SKT'si
 * gecmemis satir varsa "Sec"e basilir (pencere kapanir, urun ana sayfaya gelir). Aksi halde secim hekime birakilir;
 * nedeni akisa yazilir, ana sayfa kartta gosterir. Pencereyi hekim kendisi actiysa hicbir sey yapilmaz.
 */
export async function runStockPopupFlow(d: StockPopupDeps): Promise<void> {
  const state = await d.flow.get();
  if (!state || state.step !== 'choosingProduct' || d.now() - state.updatedAt > PRODUCT_HANDOFF_MS) return;
  const res = await d.send<Submission | null>({ type: 'GET_ACTIVE' });
  const s = res.ok ? res.data : null;
  if (!s || s.id !== state.submissionId || !s.lotNumber) return;
  const serial = s.lotNumber;

  const handOver = async (message: string) => {
    await d.flow.update(s.id, { step: 'awaitingConfirm', message });
    d.card.show(views.stockPopup(`${message} Satırı kendiniz seçin.`, 'warn'));
  };

  d.card.show(views.stockPopup(`Seri ${serial} aranıyor…`, 'muted'));
  try {
    await d.bridge.call('ready');
    await d.bridge.call('searchSerial', { serial });
  } catch (e) {
    await handOver(`Stokta arama yapılamadı (${code(e)}).`);
    return;
  }

  const pick = pickStockRow(readStockPopupRows(d.doc), { serial, productName: s.tarbilProductName, today: d.today() });
  switch (pick.kind) {
    case 'none':
      await handOver(`Seri ${serial} TARBİL stoğunuzda bulunamadı.`);
      return;
    case 'many':
      await handOver(`Seri ${serial} stokta birden fazla satırda.`);
      return;
    case 'expired':
      await handOver(`Seri ${serial} son kullanma tarihi geçmiş.`);
      return;
    case 'nameMismatch':
      await handOver(`Seri ${serial} TARBİL'de "${pick.row.productName}" olarak görünüyor; Vetly'deki aşıyla aynı değil.`);
      return;
    case 'one':
      d.card.show(views.stockPopup('Aşı seçiliyor…', 'muted'));
      // "Sec" pencereyi kapatir; yanit gelmeyebilir. Ana sayfa urun satirini serisinden dogrular.
      await d.bridge.call('selectStockRow', { linkId: pick.row.linkId }, 5000).catch(() => undefined);
  }
}
```

- [ ] **Step 5: İçerik betiğine bağla**

`extension/src/tarbil/content.ts`:
1. import ekle: `import { runStockPopupFlow } from './pages/stockPopup';`
2. `routeTarbilPage` `switch`'inde `case 'search':` bloğundan sonra ekle:
```ts
  case 'vaccineStockPopup':
    void runStockPopupFlow({
      bridge: createPageBridge(window),
      flow,
      send,
      doc: document,
      card,
      now: Date.now,
      today: () => {
        const d = new Date();
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      },
    });
    break;
```

- [ ] **Step 6: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "×|Test Files|Tests "`
Expected: tip hatası yok; tüm testler PASS.

- [ ] **Step 7: Commit**

```bash
git add extension/src
git commit -m "feat(tarbil-ext): asi stok penceresinde seri ile arama ve tek eslesmeyi secme"
```

---

### Task 6: Eklenti — aşı sayfasında ürün adımı

**Files:**
- Modify: `extension/src/tarbil/pages/vaccineReceipt.ts`, `extension/src/tarbil/pages/vaccineReceipt.test.ts`, `extension/src/tarbil/core/views.ts`

**Interfaces:**
- Consumes: `readProductEditRow`, `normalizeSerial` (Task 4); MAIN `clickAllowed({page:'vaccineReceipt', button:'addProduct'})`, `setProductQuantity` (Task 4); akış `choosingProduct`/`productReady`, başarısızlık mesajı `awaitingConfirm` + `message` (Task 5).
- Produces: `views.choosingProduct(s)`, `views.stockWindowBlocked(s)`, `views.productReady(s)`, `views.productNeedsVet(s, message)`, `views.wrongProduct(s)`; kart eylemi `'stockWindow'`.

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/pages/vaccineReceipt.test.ts`:
1. (`base` nesnesine `tarbilProductName: null` Task 4'te eklendi.)
2. `it('moves to awaiting confirmation when the matching animal lands on the form'` testindeki `setup({}, 'transferred')` çağrısını `setup({ lotNumber: null }, 'transferred')` yap (seri yoksa eski davranış).
3. Dosyanın en üstündeki sabitlerin altına ekle:
```ts
function openProductRow(serial: string) {
  document.body.insertAdjacentHTML('beforeend', `<table id="${P}RadGridProduct_ctl00"><thead>
    <tr><th>Detay</th><th>Detay</th><th>Ürün</th><th>Takdim Şekli</th><th>Seri Numarası</th></tr>
    <tr class="rgEditRow"><td></td><td></td><td><input></td><td>Flakon</td><td>${serial}</td></tr></thead><tbody></tbody></table>`);
}
```
4. `describe('receiptFlow', ...)` bloğunun sonuna ekle:
```ts
  it('presses Ürün Ekle once the animal lands when the vaccine came from stock', async () => {
    page();
    const { receipt, flow, calls, mutate, text } = await setup({ lotNumber: '665932' }, 'transferred');
    await receipt.start();

    addAnimalRow(CHIP);
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('choosingProduct'));
    expect(calls).toContainEqual({ op: 'clickAllowed', args: { page: 'vaccineReceipt', button: 'addProduct' } });
    expect(text()).toContain('665932');
  });

  it('fills Ürün Adet when the product row with the Vetly serial lands on the form', async () => {
    page();
    const { receipt, flow, calls, mutate, text } = await setup({ lotNumber: '665932' }, 'choosingProduct');
    await receipt.start();

    openProductRow('665932');
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('productReady'));
    expect(calls).toContainEqual({ op: 'setProductQuantity', args: { quantity: 1 } });
    expect(text()).toContain('Kaydet');
    expect(calls.map((c) => c.op)).not.toContain('clickAllowed');
  });

  it('warns instead of filling quantity when another serial lands on the form', async () => {
    page();
    const { receipt, flow, calls, mutate, text } = await setup({ lotNumber: '665932' }, 'choosingProduct');
    await receipt.start();

    openProductRow('999999');
    mutate();

    await vi.waitFor(() => expect(text()).toContain('Yanlış ürün'));
    expect(calls.map((c) => c.op)).not.toContain('setProductQuantity');
    expect((await flow.get())?.step).toBe('awaitingConfirm');
  });

  it('shows why the stock window could not pick the vaccine', async () => {
    page();
    const { receipt, flow, text } = await setup({ lotNumber: '665932' }, 'choosingProduct');
    await receipt.start();

    const next = await flow.update('s1', { step: 'awaitingConfirm', message: 'Seri 665932 TARBİL stoğunuzda bulunamadı.' });
    receipt.flowChanged(next);

    await vi.waitFor(() => expect(text()).toContain('bulunamadı'));
  });

  it('offers to reopen the stock window when it never picks up', async () => {
    page();
    const { receipt, timers, advance, text, mutate } = await setup({ lotNumber: '665932' }, 'transferred');
    await receipt.start();
    addAnimalRow(CHIP);
    mutate();
    await vi.waitFor(() => expect(text()).toContain('665932'));

    advance(POPUP_WAIT_MS);
    timers.forEach((t) => t());

    await vi.waitFor(() => expect(text()).toContain('Stok penceresi'));
  });

  it('marks submitted when Onayla succeeds after the product row is ready', async () => {
    page();
    const { receipt, sent, flow, mutate } = await setup({ lotNumber: '665932' }, 'productReady');
    await receipt.start();

    (document.getElementById(`${P}btnInsert_input`) as HTMLInputElement).click();
    await vi.waitFor(async () => expect((await flow.get())?.insertClickedAt).toBeDefined());
    showSuccess();
    mutate();

    await vi.waitFor(() =>
      expect(sent).toContainEqual({ type: 'MARK_SUBMITTED', id: 's1', method: 'AUTO', tarbilReference: null }),
    );
  });
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/pages/vaccineReceipt.test.ts 2>&1 | grep -E "×|Tests "`
Expected: yeni 6 test başarısız; diğerleri geçer.

- [ ] **Step 3: Kartlar**

`extension/src/tarbil/core/views.ts` `addProduct:` girdisinden önce ekle:

```ts
  choosingProduct: (s: Submission): CardView =>
    view(s, `✓ Hayvan forma eklendi. Stok penceresinde seri ${s.lotNumber ?? '—'} aranıyor…`, 'muted'),
  stockWindowBlocked: (s: Submission): CardView =>
    view(
      s,
      'Stok penceresi açılmadı. Tarayıcı açılır pencereyi engellemiş olabilir: TARBİL için açılır pencerelere izin verin ya da aşağıdaki butona basın.',
      'warn',
      [{ id: 'stockWindow', label: 'Stok penceresini aç' }, ...FALLBACK],
    ),
  productReady: (s: Submission): CardView => ({
    lines: [
      ...header(s),
      { text: `✓ Ürün satırı hazır: ${s.tarbilProductName ?? s.vaccineName} · Seri ${s.lotNumber ?? '—'} · 1 adet.`, tone: 'ok' },
      { text: "Satırdaki Kaydet'e siz basın (TARBİL stoğundan düşer), Detay alanlarını kontrol edin, sonra Onayla'ya basın. Kaydı yakalayıp Vetly'ye işleyeceğiz." },
    ],
    actions: FALLBACK,
  }),
  productNeedsVet: (s: Submission, message: string): CardView =>
    view(s, `${message} "Ürün Ekle"den aşıyı stoktan kendiniz seçin, kontrol edip Onayla'ya basın.`, 'warn'),
  wrongProduct: (s: Submission): CardView =>
    view(s, `Yanlış ürün seçildi: formdaki serinin Vetly'deki seriyle (${s.lotNumber ?? '—'}) aynı olması gerekir. Satırı İptal edip doğru seriyi seçin.`, 'warn'),
```

- [ ] **Step 4: Akış**

`extension/src/tarbil/pages/vaccineReceipt.ts`:
1. import'lara ekle: `import { normalizeSerial, readProductEditRow } from '../steps/productRows';`
2. `const WAITING_FOR_ANIMAL = …` satırının altına ekle:
```ts
/** Onayla + basari yakalamasinin gecerli oldugu adimlar (urun adimi otomatik ya da hekimde). */
const CONFIRM_STEPS = ['awaitingConfirm', 'choosingProduct', 'productReady'];
```
3. `run()` içindeki şu bloğu:
```ts
    if (st && (WAITING_FOR_ANIMAL.includes(st.step) || st.step === 'awaitingConfirm')) {
      if (st.step !== 'awaitingConfirm') d.card.show(views.progress(sub, 'Hayvanın forma eklenmesi bekleniyor…'));
      else d.card.show(views.addProduct(sub));
      watch();
      return;
    }
```
şununla değiştir:
```ts
    if (st && (WAITING_FOR_ANIMAL.includes(st.step) || CONFIRM_STEPS.includes(st.step))) {
      d.card.show(stepView(sub, st));
      watch();
      return;
    }
```
4. `fill()` fonksiyonundan önce ekle:
```ts
  function stepView(s: Submission, st: FlowState) {
    switch (st.step) {
      case 'choosingProduct':
        return views.choosingProduct(s);
      case 'productReady':
        return views.productReady(s);
      case 'awaitingConfirm':
        return st.message ? views.productNeedsVet(s, st.message) : views.addProduct(s);
      default:
        return views.progress(s, 'Hayvanın forma eklenmesi bekleniyor…');
    }
  }

  /** Hayvan dogrulandi: seri biliniyorsa "Urun Ekle" (stok penceresi), bilinmiyorsa urun adimi hekimde. */
  async function startProductStep(s: Submission): Promise<void> {
    if (!s.lotNumber) {
      await d.flow.update(s.id, { step: 'awaitingConfirm', message: undefined });
      d.card.show(views.addProduct(s));
      return;
    }
    await d.flow.update(s.id, { step: 'choosingProduct', message: undefined });
    d.card.show(views.choosingProduct(s));
    try {
      await d.bridge.call('clickAllowed', { page: 'vaccineReceipt', button: 'addProduct' });
    } catch (e) {
      await d.flow.update(s.id, { step: 'awaitingConfirm', message: `Ürün Ekle'ye basılamadı (${code(e)}).` });
      d.card.show(views.productNeedsVet(s, `Ürün Ekle'ye basılamadı (${code(e)}).`));
      return;
    }
    d.setTimer(() => {
      void (async () => {
        const st = await current();
        if (st?.step === 'choosingProduct' && d.now() - st.updatedAt >= POPUP_WAIT_MS && !readProductEditRow(d.doc)) {
          d.card.show(views.stockWindowBlocked(s));
        }
      })();
    }, POPUP_WAIT_MS);
  }
```
5. `evaluate()` içindeki şu bloğu:
```ts
    if (st.step === 'awaitingConfirm') {
      const clickedRecently = st.insertClickedAt !== undefined && d.now() - st.insertClickedAt <= SUCCESS_WINDOW_MS;
      const freshSuccess = hasFreshSuccess(d.doc, RECEIPT.successPanel, staleSuccess);
      if (clickedRecently && freshSuccess) await confirm(s);
      return;
    }
```
şununla değiştir:
```ts
    if (CONFIRM_STEPS.includes(st.step)) {
      const clickedRecently = st.insertClickedAt !== undefined && d.now() - st.insertClickedAt <= SUCCESS_WINDOW_MS;
      const freshSuccess = hasFreshSuccess(d.doc, RECEIPT.successPanel, staleSuccess);
      if (clickedRecently && freshSuccess) {
        await confirm(s);
        return;
      }
      if (st.step === 'choosingProduct') await checkProductRow(s);
      return;
    }
```
ve aynı fonksiyondaki
```ts
      await d.flow.update(s.id, { step: 'awaitingConfirm' });
      d.card.show(views.addProduct(s));
```
satırlarını şununla değiştir:
```ts
      await startProductStep(s);
```
6. `confirm` fonksiyonundan önce ekle:
```ts
  /** Stok penceresinden secilen urun forma geldi mi: seri Vetly serisiyse Urun Adet = 1 (satir Kaydet hekimde). */
  async function checkProductRow(s: Submission): Promise<void> {
    const row = readProductEditRow(d.doc);
    if (!row || !row.serial) return;
    if (row.serial !== normalizeSerial(s.lotNumber)) {
      await d.flow.update(s.id, { step: 'awaitingConfirm' });
      d.card.show(views.wrongProduct(s));
      return;
    }
    try {
      await d.bridge.call('setProductQuantity', { quantity: 1 });
    } catch (e) {
      const message = `Ürün Adet yazılamadı (${code(e)}).`;
      await d.flow.update(s.id, { step: 'awaitingConfirm', message });
      d.card.show(views.productNeedsVet(s, message));
      return;
    }
    await d.flow.update(s.id, { step: 'productReady' });
    d.card.show(views.productReady(s));
  }
```
7. Onayla tıklama dinleyicisindeki `if (st?.step === 'awaitingConfirm') await d.flow.update(…)` satırını `if (st && CONFIRM_STEPS.includes(st.step)) await d.flow.update(st.submissionId, { insertClickedAt: d.now() });` yap.
8. `d.card.onAction` `switch`'ine `case 'petvet':` bloğundan sonra ekle:
```ts
        case 'stockWindow':
          try {
            await d.bridge.call('clickAllowed', { page: 'vaccineReceipt', button: 'addProduct' });
          } catch (e) {
            await failed(e);
          }
          return;
```
9. `flowChanged` içinde `if (state.step === 'needsVet') …` satırının üstüne ekle:
```ts
      // Stok penceresi secimi hekime biraktiysa nedenini goster.
      if (state.step === 'awaitingConfirm' && state.message) {
        d.card.show(views.productNeedsVet(s, state.message));
        return;
      }
```

- [ ] **Step 5: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "×|Test Files|Tests " && npm run build:dev > /tmp/p2t6.log 2>&1; echo build=$?; grep -c "console\." dist/content.js dist/page.js`
Expected: tip hatası yok; tüm testler PASS; `build=0`; `console.` sayıları `0`.

- [ ] **Step 6: Commit**

```bash
git add extension/src
git commit -m "feat(tarbil-ext): asi belgesinde Urun Ekle, seri dogrulamasi ve Urun Adet=1; satir Kaydet hekimde"
```

---

### Task 7: Frontend — Yeni Aşı formunda stoktan seçim

**Files:**
- Create: `frontend/src/pages/vaccinations/VaccineStockPicker.tsx`
- Modify: `frontend/src/api/vaccinationApi.ts`, `frontend/src/pages/vaccinations/NewVaccinationPage.tsx`

**Interfaces:**
- Consumes: `inventoryApi.list()` (`InventoryItem`: `id, name, category, quantityOnHand, expiryDate, lotNumber`), `POST /api/v1/vaccination-records` + `inventoryItemId` (Task 1).
- Produces: `<VaccineStockPicker value={string|null} onChange={(item: InventoryItem | null) => void} />`

- [ ] **Step 1: API tipi**

`frontend/src/api/vaccinationApi.ts` `RecordVaccinationPayload` içine `notes?: string;` satırından sonra ekle:

```ts
  /** Stoktan secilen asi (spec 2026-10-04 P2); verilirse vaccineName/lotNumber kalemden doldurulur. */
  inventoryItemId?: string;
```

- [ ] **Step 2: Stok seçici**

`frontend/src/pages/vaccinations/VaccineStockPicker.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { Select } from '../../components/ui/Field';

/** "Aşı", "aşı", "Asi" ... (P1a TARBİL'den gelen kalemler "Aşı" kategorisiyle açılır). */
const isVaccine = (i: InventoryItem) =>
  (i.category ?? '').toLocaleLowerCase('tr-TR').replace(/ş/g, 's').replace(/ı/g, 'i').includes('asi');

function trDate(iso: string | null): string {
  const m = iso ? /^(\d{4})-(\d{2})-(\d{2})/.exec(iso) : null;
  return m ? `${m[3]}.${m[2]}.${m[1]}` : '—';
}

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * Yeni Asi formu (spec 2026-10-04 P2 S3.2): subenin asi stogundan seri secimi. "Stokta yok" secilirse asi adi elle
 * yazilir (stok baglantisi olmaz, eklenti urun adimini hekime birakir). SKT'si gecmis seri secilemez.
 */
export function VaccineStockPicker({ value, onChange }: { value: string | null; onChange: (item: InventoryItem | null) => void }) {
  const [items, setItems] = useState<InventoryItem[]>([]);

  useEffect(() => {
    inventoryApi
      .list()
      .then((all) => setItems(all.filter((i) => isVaccine(i) && i.quantityOnHand > 0)))
      .catch(() => setItems([]));
  }, []);

  const today = todayIso();

  return (
    <Select
      value={value ?? ''}
      onChange={(e) => onChange(items.find((i) => i.id === e.target.value) ?? null)}
    >
      <option value="">Stokta yok — aşı adını elle yaz</option>
      {items.map((i) => {
        const expired = !!i.expiryDate && i.expiryDate < today;
        return (
          <option key={i.id} value={i.id} disabled={expired} style={expired ? { color: 'var(--color-danger-700)' } : undefined}>
            {`${i.name} · Seri ${i.lotNumber ?? '—'} · SKT ${trDate(i.expiryDate)} · ${i.quantityOnHand} adet${expired ? ' (SKT geçmiş)' : ''}`}
          </option>
        );
      })}
    </Select>
  );
}
```

**Not:** `Select`'in `components/ui/Field.tsx`'ten bu adla dışa aktarıldığını doğrula (`NewVaccinationPage.tsx` zaten `import { FieldWrap, Input, Select, Textarea } from '../../components/ui/Field';` kullanıyor).

- [ ] **Step 3: Forma bağla**

`frontend/src/pages/vaccinations/NewVaccinationPage.tsx`:
1. import'lara ekle: `import { InventoryItem } from '../../api/inventoryApi';` ve `import { VaccineStockPicker } from './VaccineStockPicker';`
2. `const [vaccineName, setVaccineName] = useState('');` satırının altına ekle:
```tsx
  const [stockItem, setStockItem] = useState<InventoryItem | null>(null);
```
3. `resetVaccineFields` içinde `setVaccineName('');` satırının altına `setStockItem(null);` ekle.
4. `submit` içindeki `vaccinationApi.record({ … })` nesnesinde `vaccineName: vaccineName.trim(),` satırını şununla değiştir:
```tsx
        vaccineName: stockItem ? stockItem.name : vaccineName.trim(),
        lotNumber: stockItem?.lotNumber ?? undefined,
        inventoryItemId: stockItem?.id,
```
5. `<FieldWrap label="Aşı*">` bloğunun tamamını şununla değiştir:
```tsx
            <FieldWrap label="Aşı*">
              <VaccineStockPicker
                value={stockItem?.id ?? null}
                onChange={(item) => {
                  setStockItem(item);
                  if (item) setVaccineName(item.name);
                }}
              />
              {!stockItem && (
                <>
                  <Input
                    list="vaccine-names"
                    value={vaccineName}
                    onChange={(e) => setVaccineName(e.target.value)}
                    placeholder="Aşı adı (stokta yoksa)"
                    required
                    style={{ marginTop: 8 }}
                  />
                  <datalist id="vaccine-names">
                    {COMMON_VACCINE_NAMES.map((n) => (
                      <option key={n} value={n} />
                    ))}
                  </datalist>
                </>
              )}
            </FieldWrap>
```

- [ ] **Step 4: Tip kontrolü ve derleme**

Run: `cd frontend && npx tsc -b --noEmit; echo tsc=$?; npm run build > /tmp/p2t7.log 2>&1; echo build=$?`
Expected: `tsc=0`, `build=0`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat(frontend): Yeni Asi formunda asiyi stoktan secme (seri, SKT, adet)"
```

---

### Task 8: Dokümantasyon ve son doğrulama

**Files:**
- Modify: `docs/api-conventions.md`, `docs/superpowers/specs/2026-10-04-tarbil-p2-asi-urunu-design.md`, `extension/README.md`

- [ ] **Step 1: Belgeler**

`docs/api-conventions.md` "**TARBİL uçları (P0 çekirdek, 2026-10-04):**" listesinin sonuna ekle:

```markdown
- `POST /api/v1/vaccination-records` — isteğe `inventoryItemId` (opsiyonel) eklendi: stoktan seçilen aşı; `ADMINISTERED` olunca Vetly stoğundan 1 düşer, iptalde geri eklenir (olayla, `inventory` modülü). Eklentiye giden aşı verisinde `tarbilProductName` alanı var.
```

`extension/README.md` sonuna ekle:

```markdown

## Aşı ürünü (P2)

Aşı Vetly'de stoktan seçildiyse eklenti hayvandan sonra **Ürün Ekle**'ye basar; açılan stok penceresinde Vetly serisini arar ve tek eşleşen satırı **Seç**er; formdaki **Ürün Adet**'i 1 yapar. Satırdaki **Kaydet** (TARBİL stoğundan düşer), Detay alanları ve **Onayla** hekimdedir. Stok penceresi açılmıyorsa TARBİL için açılır pencerelere izin verin.
```

`docs/superpowers/specs/2026-10-04-tarbil-p2-asi-urunu-design.md` sonuna ekle:

```markdown

**Uygulandı:** `docs/superpowers/plans/2026-10-04-tarbil-p2-asi-urunu.md` (V67).
```

- [ ] **Step 2: Son doğrulama**

Run:
```bash
cd backend && ./mvnw -q test > /tmp/p2t8.log 2>&1; echo backend=$?; grep -lE "<(failure|error)" target/surefire-reports/TEST-*.xml | head
cd ../extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "Tests " && npm run build:dev > /tmp/p2t8e.log 2>&1; echo ext=$?
cd ../frontend && npx tsc -b --noEmit; echo frontend=$?
```
Expected: `backend=0` ve rapor listesi boş; eklenti testleri hepsi PASS; `ext=0`; `frontend=0`.

- [ ] **Step 3: Commit**

```bash
git add docs/api-conventions.md docs/superpowers/specs/2026-10-04-tarbil-p2-asi-urunu-design.md extension/README.md
git commit -m "docs(tarbil): P2 asi urunu - API, README, spec notu"
```

- [ ] **Step 4: Canlı kabul (hekimle; kod değişikliği yok)**

1. Backend'i yeniden başlat (V67 sütunu eklenir; zorunlu değil ama yeni alanlar için gerekli), eklentiyi `chrome://extensions`'tan yeniden yükle.
2. Vetly > Yeni Aşı: aşıyı stoktan seç (ör. Biocan R · Seri …), "Uygulandı" ile kaydet → Stok sayfasında adet 1 düşmüş olmalı.
3. Yan panel → "TARBİL'de doldur": hayvan eklendikten sonra stok penceresi açılır, seri seçilir, ürün satırında Ürün Adet = 1.
4. Hekim satır **Kaydet**'e basar, Detay alanlarını kontrol eder, **Onayla** → kart "TARBİL'e kaydedildi".
5. Detay alanlarının yapısı bu ilk gerçek aşıda okunur (sonraki tur: öğrenme).
