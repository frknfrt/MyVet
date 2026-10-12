# Stoktan Verilen Reçete Kalemi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** "Kliniğin stoğundan ver" işaretli reçete kaleminde ilaç kataloğu seçimi kalkar; kalemin ilacı stok ürünüdür ve adı kaleme yazılır.

**Architecture:** `prescription_items.drug_id` boş olabilir, yeni `drug_name` sütunu stoktan verilen kalemin ad anlık görüntüsünü tutar. Reçete modülü stok modülünü göremediği için ad istemciden gelir ve inventory modülündeki mevcut olay dinleyicisinde (aynı işlem) stok kaleminin adıyla doğrulanır. Okuyan yerler (detay, liste, rapor/PDF) adı tek bir yardımcıdan alır.

**Tech Stack:** Spring Boot 3 + Flyway + JPA (JUnit 5, Mockito, MockMvc entegrasyon); React + TypeScript (Vite; birim test altyapısı yok).

**Spec:** `docs/superpowers/specs/2026-10-12-stoktan-verilen-recete-kalemi-design.md`

## Global Constraints

- Doğrudan yerel `main`; branch yok; push yalnız kullanıcı isteyince.
- Migration `V79__prescription_item_drug_name.sql`; geriye uyumlu (sütun ekleme + NOT NULL kaldırma + CHECK).
- Stoktan verilen kalem: `drugName` zorunlu (boş olmayan), `drugId` boş olmalı. Stoktan verilmeyen kalem: `drugId` zorunlu, `drugName` boş olmalı. Aksi `InvalidPrescriptionItemException` (422).
- Ad, inventory'de stok kaleminin `name` alanıyla (kırpılmış, birebir) eşleşmeli; uymazsa `InvalidPrescriptionStockItemException` (422), reçete yazılmaz.
- Kontrollü ilaç ve etkileşim kontrolü yalnız katalog ilacı olan kalemlerle yapılır.
- TARBİL aktarımı ve eklenti değişmez.
- Geliştirme DB'si çalışan :8080 backend ile ortak; testler V79'u uygular. Kullanıcının :8080 backend'ine ve :5173 frontend'ine dokunma; bitince kullanıcıdan backend'i yeniden başlatmasını iste.
- Frontend'de birim test yok: doğrulama `npm run build` (tsc + vite). Ruling (plan): Playwright e2e çalışan sunucu ister, kullanılmaz.
- UI metinleri Türkçe.

## Review Focus

1. Stok kalemi adı baş/son boşluklu gelirse (ör. `" Metacam "`) yine eşleşmeli ve kalemde kırpılmış saklanmalı → Task 1 `acceptsAStockItemNameWithSurroundingSpaces` (entegrasyon).
2. Stoktan verilen kalem okunurken katalog sorgusu `findById(null)` çağrılmamalı (Spring Data `IllegalArgumentException` atar) → Task 2 `PrescriptionItemNamesTest.usesTheStoredNameWithoutTouchingTheCatalog`.
3. Değişiklikten önce yazılmış, hem `drug_id` hem `inventory_item_id` dolu kalem okunmaya devam etmeli (katalog adı) → Task 2 `PrescriptionItemNamesTest.prefersTheCatalogNameWhenBothAreStored`.
4. Formda kutu işaretlenip kaldırılınca kalem tekrar katalog ilacıyla gönderilmeli (`drugId` dolu, `drugName` yok) → Task 3 kod adımı + elle kontrol listesi.
5. Muayene raporu/PDF stoktan verilen kalemde "Bilinmeyen ilac" yerine stok adını yazmalı → Task 2 `PrescriptionItemNamesTest.fallsBackForTheReport`.

---

### Task 1: Yazma yolu — migration, kurallar, olay adı, stok tarafında ad doğrulaması

**Files:**
- Create: `backend/src/main/resources/db/migration/V79__prescription_item_drug_name.sql`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/domain/PrescriptionItem.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/dto/PrescriptionItemInput.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/api/dto/IssuePrescriptionRequest.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/api/PrescriptionsController.java:42`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/IssuePrescriptionUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/domain/event/PrescriptionIssuedEvent.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/infrastructure/event/PrescriptionStockEventListener.java`
- Test: `backend/src/test/java/com/vetos/modules/encounter/application/IssuePrescriptionUseCaseTest.java`
- Test: `backend/src/test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java`

**Interfaces:**
- Produces: `PrescriptionItemInput(UUID drugId, String drugName, String dosage, String frequency, int durationDays, DrugRoute route, UUID inventoryItemId, Integer stockQuantity)`; `IssuePrescriptionRequest.Item` aynı sırayla `drugName` alır; `PrescriptionItem.getDrugName()`; `PrescriptionItem.add(tenantId, prescriptionId, drugId, drugName, dosage, frequency, durationDays, route, inventoryItemId, stockQuantity)` (10 argüman; 7 argümanlı eski aşırı yükleme kalır, `drugName` null); `PrescriptionIssuedEvent.StockItem(UUID inventoryItemId, int quantity, String name)`.

- [ ] **Step 1: Birim testlerini güncelle ve ekle**

`IssuePrescriptionUseCaseTest` içinde yardımcıları değiştir:

```java
    private PrescriptionItemInput stockItem(UUID inventoryItemId, Integer qty) {
        return new PrescriptionItemInput(null, "Metacam", "1 tb", "2x1", 5, DrugRoute.ORAL, inventoryItemId, qty);
    }

    private PrescriptionItemInput plainItem() {
        return new PrescriptionItemInput(drug, null, "1 tb", "1x1", 3, DrugRoute.ORAL, null, null);
    }
```

`issuesWithoutEncounterAndAnnouncesStockItems` içindeki beklentiyi `new PrescriptionIssuedEvent.StockItem(inv, 2, "Metacam")` yap. Sona ekle:

```java
    @Test
    void stockItemTakesItsNameInsteadOfACatalogDrug() {
        UUID inv = UUID.randomUUID();
        PrescriptionItemInput withCatalogDrug = new PrescriptionItemInput(drug, "Metacam", "1", "1", 1, DrugRoute.ORAL, inv, 1);
        PrescriptionItemInput withoutName = new PrescriptionItemInput(null, "  ", "1", "1", 1, DrugRoute.ORAL, inv, 1);

        for (PrescriptionItemInput item : List.of(withCatalogDrug, withoutName)) {
            assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, UUID.randomUUID(), List.of(item))))
                .isInstanceOf(InvalidPrescriptionItemException.class);
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void plainItemNeedsACatalogDrugAndNoName() {
        PrescriptionItemInput noDrug = new PrescriptionItemInput(null, null, "1", "1", 1, DrugRoute.ORAL, null, null);
        PrescriptionItemInput named = new PrescriptionItemInput(drug, "Metacam", "1", "1", 1, DrugRoute.ORAL, null, null);

        for (PrescriptionItemInput item : List.of(noDrug, named)) {
            assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, null, List.of(item))))
                .isInstanceOf(InvalidPrescriptionItemException.class);
        }
    }

    @Test
    void storesTheTrimmedStockNameOnTheItem() {
        UUID inv = UUID.randomUUID();
        ArgumentCaptor<PrescriptionItem> saved = ArgumentCaptor.forClass(PrescriptionItem.class);

        UUID id = useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, UUID.randomUUID(),
            List.of(new PrescriptionItemInput(null, " Metacam ", "1", "1", 1, DrugRoute.ORAL, inv, 1))));

        verify(items).save(saved.capture());
        assertThat(saved.getValue().getDrugId()).isNull();
        assertThat(saved.getValue().getDrugName()).isEqualTo("Metacam");
        ArgumentCaptor<PrescriptionIssuedEvent> event = ArgumentCaptor.forClass(PrescriptionIssuedEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().prescriptionId()).isEqualTo(id);
        assertThat(event.getValue().stockItems()).containsExactly(new PrescriptionIssuedEvent.StockItem(inv, 1, "Metacam"));
    }
```

Import'lar: `org.mockito.ArgumentCaptor`, `com.vetos.modules.encounter.domain.PrescriptionItem`, `static org.assertj.core.api.Assertions.assertThat`.

- [ ] **Step 2: Entegrasyon gövdelerini güncelle ve testleri ekle**

`TarbilExtensionSecurityIntegrationTest`:

`prescriptionGivingFromStock` gövdesini (kalemde `drugId` yok, `drugName` stok adı) şu hale getir ve `drug` satırını sil:

```java
        String body = "{\"patientId\":\"" + patient + "\",\"controlledSubstance\":false,\"tarbilDiseaseId\":\"" + selectableDisease() + "\","
            + "\"items\":[{\"drugName\":\"Metacam\",\"dosage\":\"1 ml\",\"frequency\":\"1x1\",\"durationDays\":3,\"route\":\"ORAL\","
            + "\"inventoryItemId\":\"" + item + "\",\"stockQuantity\":" + given + "}]}";
```

`stockGivingPrescriptionWithoutDiagnosisIsRejected` gövdesinde `\"drugId\":\"" + drug + "\"` yerine `\"drugName\":\"Metacam\"` yaz ve `drug` satırını sil.

`stockPrescriptionBody` imzasını `stockPrescriptionBody(UUID inventoryItemId, UUID diseaseId, String drugName)` yap, `drug` satırını sil, kalemi:

```java
            + "\"items\":[{\"drugName\":\"" + drugName + "\",\"dosage\":\"1\",\"frequency\":\"1\",\"durationDays\":1,\"route\":\"ORAL\","
```

Çağıranlar: `prescriptionStockItemMustBeAnExistingTarbilLinkedItemOfThisClinic` → `stockPrescriptionBody(item, selectableDisease(), "Metacam")` (yabancı kalem ve rastgele id için de; `unlinkedItem`'ın adı "Gazlı bez" — onda da `"Gazlı bez"` geçir ki red nedeni yalnız bağsızlık olsun: döngüyü `Map.of(UUID.randomUUID(), "Metacam", foreignItem, "Metacam", unlinkedItem, "Gazlı bez")` üzerinden kur). `prescriptionTarbilDiseaseMustExist` → `stockPrescriptionBody(item, UUID.randomUUID(), "Metacam")`.

Yeni testler (`prescriptionTarbilDiseaseMustExist` altına):

```java
    @Test
    void prescriptionStockItemNameMustMatchTheStockItem() throws Exception {
        UUID branchA = jdbcTemplate.queryForObject("SELECT branch_id FROM staff_users WHERE id = ?", UUID.class, staffA);
        UUID item = asTenant(tenantA, () -> {
            InventoryItem i = InventoryItem.create(tenantA, branchA, "Metacam", "İlaç", null, 5, 0, null, "MC3", null);
            i.linkTarbil("VETILAC_MEDICINE", "METACAM", "Şişe");
            return inventoryItemRepository.save(i).getId();
        });

        mockMvc.perform(post("/api/v1/prescriptions").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content(stockPrescriptionBody(item, selectableDisease(), "Amoksisilin")))
            .andExpect(status().isUnprocessableEntity());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM prescriptions WHERE tenant_id = ?", Integer.class, tenantA)).isZero();
    }

    @Test
    void acceptsAStockItemNameWithSurroundingSpaces() throws Exception {
        UUID branchA = jdbcTemplate.queryForObject("SELECT branch_id FROM staff_users WHERE id = ?", UUID.class, staffA);
        UUID item = asTenant(tenantA, () -> {
            InventoryItem i = InventoryItem.create(tenantA, branchA, "Metacam", "İlaç", null, 5, 0, null, "MC4", null);
            i.linkTarbil("VETILAC_MEDICINE", "METACAM", "Şişe");
            return inventoryItemRepository.save(i).getId();
        });

        mockMvc.perform(post("/api/v1/prescriptions").header("Authorization", "Bearer " + jwtA)
                .contentType("application/json").content(stockPrescriptionBody(item, selectableDisease(), " Metacam ")))
            .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT drug_name FROM prescription_items WHERE inventory_item_id = ?", String.class, item))
            .isEqualTo("Metacam");
    }
```

(`java.util.Map` import'unu ekle.)

- [ ] **Step 3: Çalıştır, kırmızı gör**

Run: `cd backend && ./mvnw test -Dtest=IssuePrescriptionUseCaseTest`
Expected: derleme hatası — `PrescriptionItemInput` 8 argüman almıyor, `StockItem` 3 argüman almıyor.

- [ ] **Step 4: Migration**

`V79__prescription_item_drug_name.sql`:

```sql
-- Stoktan verilen recete kaleminin ilaci stok urunudur; katalog ilaci yok, ad anlik goruntusu (spec 2026-10-12).
ALTER TABLE prescription_items ALTER COLUMN drug_id DROP NOT NULL;
ALTER TABLE prescription_items ADD COLUMN drug_name TEXT;
ALTER TABLE prescription_items ADD CONSTRAINT chk_prescription_items_drug
    CHECK (drug_id IS NOT NULL OR drug_name IS NOT NULL);
```

- [ ] **Step 5: Entity, DTO'lar, controller**

`PrescriptionItem`:

```java
    /** Katalog ilaci; stoktan verilen kalemde bos (ilac stok urunudur, spec 2026-10-12). */
    @Column(name = "drug_id")
    private UUID drugId;

    /** Stoktan verilen kalemin stok urunu adi (anlik goruntu); katalog ilacli kalemde bos. */
    @Column(name = "drug_name")
    private String drugName;
```

Aşırı yüklemeler:

```java
    public static PrescriptionItem add(UUID tenantId, UUID prescriptionId, UUID drugId, String dosage, String frequency, int durationDays, DrugRoute route) {
        return add(tenantId, prescriptionId, drugId, null, dosage, frequency, durationDays, route, null, null);
    }

    public static PrescriptionItem add(UUID tenantId, UUID prescriptionId, UUID drugId, String drugName, String dosage, String frequency,
                                       int durationDays, DrugRoute route, UUID inventoryItemId, Integer stockQuantity) {
        PrescriptionItem item = new PrescriptionItem();
        item.tenantId = tenantId;
        item.prescriptionId = prescriptionId;
        item.drugId = drugId;
        item.drugName = drugName;
        item.dosage = dosage;
        item.frequency = frequency;
        item.durationDays = durationDays;
        item.route = route;
        item.inventoryItemId = inventoryItemId;
        item.stockQuantity = stockQuantity;
        return item;
    }
```

`PrescriptionItemInput`:

```java
/**
 * inventoryItemId + stockQuantity: "klinigin stogundan ver" (birlikte dolu ya da birlikte bos). Stoktan verilen kalemde ilac
 * stok urunudur: drugName dolu, drugId bos; digerlerinde drugId dolu, drugName bos (spec 2026-10-12).
 */
public record PrescriptionItemInput(UUID drugId, String drugName, String dosage, String frequency, int durationDays, DrugRoute route,
                                    UUID inventoryItemId, Integer stockQuantity) {}
```

`IssuePrescriptionRequest.Item`:

```java
    public record Item(UUID drugId, String drugName, String dosage, String frequency, int durationDays, DrugRoute route,
                       UUID inventoryItemId, @Positive Integer stockQuantity) {}
```

Controller eşlemesi:

```java
                .map(i -> new PrescriptionItemInput(i.drugId(), i.drugName(), i.dosage(), i.frequency(), i.durationDays(), i.route(),
                    i.inventoryItemId(), i.stockQuantity()))
```

- [ ] **Step 6: Olay ve use case**

`PrescriptionIssuedEvent`:

```java
    /** name: recetede yazilan stok urunu adi; inventory kalemin adiyla dogrular (spec 2026-10-12). */
    public record StockItem(UUID inventoryItemId, int quantity, String name) {}
```

`IssuePrescriptionUseCase.execute` başındaki döngüyü değiştir:

```java
        for (PrescriptionItemInput item : command.items()) {
            if (!validItem(item)) {
                throw new InvalidPrescriptionItemException();
            }
        }
        List<PrescriptionIssuedEvent.StockItem> stockItems = command.items().stream()
            .filter(i -> i.inventoryItemId() != null)
            .map(i -> new PrescriptionIssuedEvent.StockItem(i.inventoryItemId(), i.stockQuantity(), i.drugName().trim()))
            .toList();
```

Kalem kaydı:

```java
        command.items().forEach(item -> prescriptionItemRepository.save(PrescriptionItem.add(
            prescription.getTenantId(), prescription.getId(), item.drugId(), item.drugName() == null ? null : item.drugName().trim(),
            item.dosage(), item.frequency(), item.durationDays(), item.route(), item.inventoryItemId(), item.stockQuantity()
        )));
```

Sınıfa:

```java
    /**
     * Stok kalemi ve adedi birlikte; stoktan verilen kalemde ilac stok urunudur (ad dolu, katalog ilaci yok), digerlerinde
     * katalog ilaci zorunlu ve ad yok (spec 2026-10-12).
     */
    private static boolean validItem(PrescriptionItemInput item) {
        if ((item.inventoryItemId() == null) != (item.stockQuantity() == null)) return false;
        if (item.stockQuantity() != null && item.stockQuantity() <= 0) return false;
        boolean named = item.drugName() != null && !item.drugName().isBlank();
        return item.inventoryItemId() != null
            ? item.drugId() == null && named
            : item.drugId() != null && item.drugName() == null;
    }
```

- [ ] **Step 7: Stok tarafında ad doğrulaması**

`PrescriptionStockEventListener.onIssued` döngüsü:

```java
        for (PrescriptionIssuedEvent.StockItem stockItem : event.stockItems()) {
            // Bu kiracida (filtreli oturum), TARBIL'e bagli ve recetede yazilan adla ayni kalem (spec 2026-10-12).
            boolean valid = items.findById(stockItem.inventoryItemId())
                .map(i -> i.getTarbilProductName() != null && i.getName().trim().equals(stockItem.name()))
                .orElse(false);
            if (!valid) {
                throw new InvalidPrescriptionStockItemException();
            }
        }
```

- [ ] **Step 8: Çalıştır, yeşil gör**

Run: `cd backend && ./mvnw test -Dtest=IssuePrescriptionUseCaseTest,TarbilExtensionSecurityIntegrationTest,TenantIsolationTest`
Expected: PASS (tümü).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/db/migration/V79__prescription_item_drug_name.sql backend/src/main/java/com/vetos/modules/encounter backend/src/main/java/com/vetos/modules/inventory/infrastructure/event/PrescriptionStockEventListener.java backend/src/test/java/com/vetos/modules/encounter/application/IssuePrescriptionUseCaseTest.java backend/src/test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java
git commit -m "feat(reçete): stoktan verilen kalemin ilacı stok ürünü; ad stokta doğrulanır"
```

---

### Task 2: Okuma — kalem adı tek yardımcıdan

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/encounter/application/PrescriptionItemNames.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/GetPrescriptionUseCase.java:30-33`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/ListPrescriptionsByPatientUseCase.java:33-36`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/GenerateEncounterReportUseCase.java:58-60`
- Test: `backend/src/test/java/com/vetos/modules/encounter/application/PrescriptionItemNamesTest.java`
- Test: `backend/src/test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 `PrescriptionItem.getDrugName()`, 10 argümanlı `PrescriptionItem.add`.
- Produces: `PrescriptionItemNames.of(PrescriptionItem item, DrugCatalogRepository catalog): String` (null olabilir).

- [ ] **Step 1: Birim testi**

`PrescriptionItemNamesTest.java`:

```java
package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.DrugRoute;
import com.vetos.modules.encounter.domain.PrescriptionItem;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrescriptionItemNamesTest {

    private final DrugCatalogRepository catalog = mock(DrugCatalogRepository.class);
    private final UUID drug = UUID.randomUUID();

    private PrescriptionItem item(UUID drugId, String drugName) {
        return PrescriptionItem.add(UUID.randomUUID(), UUID.randomUUID(), drugId, drugName, "1", "1", 1, DrugRoute.ORAL, null, null);
    }

    @Test
    void usesTheStoredNameWithoutTouchingTheCatalog() {
        assertThat(PrescriptionItemNames.of(item(null, "Metacam"), catalog)).isEqualTo("Metacam");
        verify(catalog, never()).findById(any());
    }

    @Test
    void prefersTheCatalogNameWhenBothAreStored() {
        DrugCatalog amox = mock(DrugCatalog.class);
        when(amox.getName()).thenReturn("Amoksisilin");
        when(catalog.findById(drug)).thenReturn(Optional.of(amox));

        assertThat(PrescriptionItemNames.of(item(drug, "Metacam"), catalog)).isEqualTo("Amoksisilin");
    }

    @Test
    void fallsBackForTheReport() {
        when(catalog.findById(drug)).thenReturn(Optional.empty());

        assertThat(PrescriptionItemNames.of(item(drug, null), catalog)).isNull();
        assertThat(PrescriptionItemNames.of(item(null, "Metacam"), catalog)).isEqualTo("Metacam");
    }
}
```

`TarbilExtensionSecurityIntegrationTest`'e (`prescriptionWithoutEncounterGivesStockAfterTarbilConfirmation` altına):

```java
    @Test
    void stockGivenItemIsListedUnderItsStockName() throws Exception {
        UUID[] rx = prescriptionGivingFromStock(10, 1);

        mockMvc.perform(get("/api/v1/prescriptions/" + rx[0]).header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].drugId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.items[0].drugName").value("Metacam"));
    }
```

- [ ] **Step 2: Çalıştır, kırmızı gör**

Run: `cd backend && ./mvnw test -Dtest=PrescriptionItemNamesTest`
Expected: derleme hatası — `PrescriptionItemNames` yok.

- [ ] **Step 3: Yardımcı ve okuyucular**

`PrescriptionItemNames.java`:

```java
package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.PrescriptionItem;

/** Recete kaleminin gosterilen adi: katalog ilaci varsa onun adi, yoksa stoktan verilen urunun adi (spec 2026-10-12). */
final class PrescriptionItemNames {

    private PrescriptionItemNames() {}

    static String of(PrescriptionItem item, DrugCatalogRepository catalog) {
        if (item.getDrugId() == null) return item.getDrugName();
        return catalog.findById(item.getDrugId()).map(DrugCatalog::getName).orElse(null);
    }
}
```

`GetPrescriptionUseCase` ve `ListPrescriptionsByPatientUseCase`'te:

```java
                drugCatalogRepository.findById(item.getDrugId()).map(DrugCatalog::getName).orElse(null),
```

→

```java
                PrescriptionItemNames.of(item, drugCatalogRepository),
```

(Kullanılmayan `DrugCatalog` import'unu kaldır.)

`GenerateEncounterReportUseCase`:

```java
                String drugName = Optional.ofNullable(PrescriptionItemNames.of(item, drugCatalogRepository)).orElse("Bilinmeyen ilac");
```

(`java.util.Optional` import'u yoksa ekle.)

- [ ] **Step 4: Çalıştır, yeşil gör**

Run: `cd backend && ./mvnw test -Dtest=PrescriptionItemNamesTest,TarbilExtensionSecurityIntegrationTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/encounter/application backend/src/test/java/com/vetos/modules/encounter/application/PrescriptionItemNamesTest.java backend/src/test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java
git commit -m "feat(reçete): stoktan verilen kalem listede, raporda ve PDF'te stok adıyla"
```

---

### Task 3: Reçete formu

**Files:**
- Modify: `frontend/src/api/clinicalApi.ts` (`PrescriptionItem`, `PrescriptionItemPayload`)
- Modify: `frontend/src/pages/encounter/StockGivePicker.tsx`
- Modify: `frontend/src/pages/encounter/PrescriptionCard.tsx`

**Interfaces:**
- Consumes: Task 1 istek gövdesi (`drugName`), Task 2 yanıtı (`drugId` boş olabilir, `drugName` dolu).
- Produces: `StockGivePicker` `onChange(id: string | null, name: string | null)`.

- [ ] **Step 1: API tipleri**

```ts
export interface PrescriptionItem {
  /** Stoktan verilen kalemde null: ilaç stok ürünüdür (spec 2026-10-12). */
  drugId: string | null;
  drugName: string | null;
  ...
}

export interface PrescriptionItemPayload {
  /** Stoktan verilen kalemde null; ad drugName'de. */
  drugId: string | null;
  drugName?: string | null;
  ...
}
```

- [ ] **Step 2: `StockGivePicker`**

İmza ve seçim:

```tsx
export function StockGivePicker({ value, onChange }: { value: string | null; onChange: (id: string | null, name: string | null) => void }) {
  ...
      <Select
        aria-label="Stok ürünü"
        value={value ?? ''}
        onChange={(e) => {
          const picked = items.find((i) => i.id === e.target.value);
          onChange(picked?.id ?? null, picked?.name ?? null);
        }}
      >
```

- [ ] **Step 3: `PrescriptionCard`**

`emptyItem`'a `drugName: null,` ekle.

Etkileşim kontrolü yalnız katalog ilaçlarıyla:

```tsx
    const drugIds = [...new Set(items.filter((it) => !it.giveFromStock && it.drugId).map((it) => it.drugId as string))];
```

`drugName(id)` yardımcısının imzası `drugName(id: string | null)`, gövdesi `return (id && drugs.find((d) => d.id === id)?.name) ?? '—';`.

Gönderim:

```tsx
      const controlledSubstance = items.some((it) => !it.giveFromStock && drugs.find((d) => d.id === it.drugId)?.isControlled);
      const payloadItems: PrescriptionItemPayload[] = items.map(({ giveFromStock, ...it }) =>
        giveFromStock
          ? { ...it, drugId: null }
          : { ...it, drugName: null, inventoryItemId: null, stockQuantity: null },
      );
```

Form satırında `<FieldWrap label="İlaç">…</FieldWrap>` bloğunu `{!item.giveFromStock && ( … )}` içine al. `<Select value={item.drugId} …>` → `value={item.drugId ?? ''}`.

Kutu:

```tsx
                  onChange={(e) =>
                    updateItem(
                      index,
                      e.target.checked
                        ? { giveFromStock: true, stockQuantity: 1, drugId: null }
                        : { giveFromStock: false, inventoryItemId: null, stockQuantity: null, drugName: null, drugId: drugs[0]?.id ?? '' },
                    )
                  }
```

Stok seçimi:

```tsx
                  <StockGivePicker
                    value={item.inventoryItemId ?? null}
                    onChange={(id, name) => updateItem(index, { inventoryItemId: id, drugName: name })}
                  />
```

Liste satırı: `{item.drugName ?? drugName(item.drugId)} — …` aynen kalır (yeni imzayla derlenir).

- [ ] **Step 4: Derle**

Run: `cd frontend && npm run build`
Expected: tsc ve vite hatasız.

- [ ] **Step 5: Elle kontrol listesini ledger'a yaz (kullanıcı canlıda doğrular)**

1) Kutu işaretlenince "İlaç" alanı kaybolur, "Stok ürünü" çıkar. 2) Kutu kaldırılınca "İlaç" alanı ilk katalog ilacıyla geri gelir. 3) Stoktan reçete listede stok adıyla görünür. 4) İki katalog ilaçlı kalemde etkileşim uyarısı eskisi gibi çalışır.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/clinicalApi.ts frontend/src/pages/encounter/StockGivePicker.tsx frontend/src/pages/encounter/PrescriptionCard.tsx
git commit -m "feat(reçete): stoktan verilen kalemde ilaç seçimi yerine stok ürünü"
```

---

### Task 4: Belgeler ve tam doğrulama

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-stoktan-verilen-recete-kalemi-design.md` (Durum satırı)

- [ ] **Step 1: Spec durumu**

Başlıktaki `Durum: taslak (kullanıcı onayı bekliyor)` → `Durum: uygulandı (docs/superpowers/plans/2026-10-12-stoktan-verilen-recete-kalemi.md)`.

- [ ] **Step 2: Tam doğrulama**

Run: `cd backend && ./mvnw test`
Expected: tümü PASS (önceki 544 + yeni testler).

Run: `cd frontend && npm run build`
Expected: hatasız.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-10-12-stoktan-verilen-recete-kalemi-design.md
git commit -m "docs: stoktan verilen reçete kalemi uygulandı"
```

Kullanıcıya: V79 migration'ı uygulandı; :8080 backend'i bir kez yeniden başlatması gerekir.
