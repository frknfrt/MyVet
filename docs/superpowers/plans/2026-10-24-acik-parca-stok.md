# Açık stok (ilaç) ve parça stok (aşı) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vetly stok kalemi TARBİL gibi iki parçalı olsun (kapalı adet + açık miktar). ml ile kullanılan ilaç ml ile girilsin ve düşülsün. Aşı flakonla düşülsün. Eklenti doğru listeden seçsin. Eşitleme iki parçayı ayrı karşılaştırsın.

**Architecture:**
- Kurallar `inventory` modülünde, `InventoryItem.consume(amount, source)` içinde.
- Tüm kullanım miktarları (`reçete`, `muayene`, `bekleyen düşüm`) ondalığa geçer.
- `integration/tarbil` eşitlemesi ürün+seri başına kapalı/açık toplamlarını karşılaştırır.
- Eklenti, Vetly'nin verdiği plan (`source`, `amount`) ile TARBİL penceresinde Bütün/Açık/Parça listesini seçer. Onaylanan satırı liste ve miktarıyla geri bildirir.

**Tech Stack:** Spring Boot 3.5 / Java 21 / Flyway / JPA · React + Vite + vitest · Chrome MV3 eklentisi (TypeScript, vitest + jsdom)

**Spec:** `docs/superpowers/specs/2026-10-24-acik-parca-stok-design.md`

## Global Constraints

- Çalışma doğrudan `main`'de; **commit yok, push yok**. Değişiklikler IntelliJ changelist'inde kalır, commit kullanıcıda.
- Yeni Flyway migration'ı tek dosya: `V85__acik_parca_stok.sql`.
  - Yazmadan önce `ls backend/src/main/resources/db/migration | sort -V | tail -3` ile son numarayı kontrol et.
  - Depoda V85 ya da üstü varsa bir üst numarayı al; ledger'a yaz.
  - Canlıda V84'e kadar uygulanmış durumda (`docs/deployment.md` "Her çıkıştan önce").
- Eklenti TARBİL'de resmi butonlara (Satır Kaydet, Reçeteyi Onayla, belge kaydı) **asla** basmaz. Yalnız arama/listeleme butonları ve "Seç" bağlantısı kullanılır.
- TARBİL tarayıcı sekmesinde asla çıkış yapılmaz; sekme açık bırakılır.
- Bekleyen düşüm modeli ve TARBİL onayı korunur: TARBİL'e bağlı kalemde stok TARBİL onayından sonra düşer. Eşitleme kilidi de korunur: bekleyen işi olan kalem eşitlenemez.
- **TARBİL esas** (spec §8): TARBİL'e bağlı kalemde düşüm, eklentinin bildirdiği onaylı satırın listesine (`WHOLE`/`OPEN`) ve miktarına göre yazılır.
- Fiyatlandırma ve fatura değişmez (spec §4). Fatura, muayene malzeme miktarını kullanmıyor; dokunma.
- Ekran metinleri sade Türkçe. Birim gösterimi: `adet`, `ml`, `g`, `flakon`, `kutu`.
- Eski eklentiden gelen veride açık miktar bilinmiyorsa Vetly'deki açık miktar değiştirilmez (spec §5).
- Testler yerel Docker Postgres'i (`backend-postgres-1`) çalışan backend ile paylaşır. Migration'dan sonra yerel backend'i `run-local.sh` ile yeniden başlat.

## Review Focus

1. **Eski eklentiden gelen aşı görüntüsü** (parça stok okunmadı bilgisi yok) → açık miktar değişmemeli, yalnız kapalı adet eşitlenmeli. Test: Task 5 `vaccineSnapshotWithoutPartStockFlag_keepsOpenedQuantity`.
2. **Takdim şekli tahmin edilemeyen ilaç** ("Karton kutuda", boş, null) → `ADET`, paket büyüklüğü null. Kalem bugünkü gibi çalışmalı. Test: Task 3 `unknownPresentation_isAdet`.
3. **Pipet ve tablet içeren ml yazısı** ("3 adetlik 1,34 ml'lik pipetlerde") → `ADET`, ml değil. Test: Task 3 `pipette_isAdet_evenWithMl`.
4. **Açık miktarı aşan kullanım ve kapalı adet 0 iken kullanım** → yeni paket açılır; kapalı negatife inebilir (bugünkü adet davranışı gibi), hata atılmaz. Test: Task 2 `consumeBeyondStock_opensPackageEvenWhenNoneClosed`.
5. **Kullanıcının elle ayarladığı birim** → sonraki TARBİL eşitlemesi tahminle ezmemeli. Test: Task 3 `manualUnit_isNotOverwrittenBySync`.

---

### Task 1: TARBİL'de açık/parça stok satırının canlı doğrulaması (kullanıcıyla)

Yalnız okuma. Kod yazılmaz; bulgular spec'e eklenir. Task 8 ve 9 buna dayanır.

**Files:**
- Modify: `docs/superpowers/specs/2026-10-24-acik-parca-stok-design.md` (§2'ye "2026-10-2x canlı doğrulama" alt bölümü)

- [ ] **Step 1:** Kullanıcıdan TARBİL'de **Stoktan Düşerek İlaç Reçetesi Ekle** sayfasında bir hayvanı doğrulamasını iste. Hayvan doğrulanınca "Stok Ekle" satırı açılır. Kullanıcıya **Satır Kaydet'e basmamasını** söyle.
- [ ] **Step 2:** Tarayıcı aracıyla (JS ile okuma; ekran görüntüsü bu ortamda çalışmıyor) "Stok Ekle"yi aç. Stok penceresinde `rbtnStockType_1` "Açık Stok"u seç, Ara'ya bas, Clavon 63016'yı **Seç**. Ana sayfada `txtStockQuantity` ve `txtStockAmount` için şunları kaydet:
  - görünür mü, etkin mi,
  - varsayılan değer,
  - üst sınır doğrulaması var mı,
  - etiket metni.
- [ ] **Step 3:** Aynısını "Bütün Stok"tan seçilen ml'li bir ilaçla (Tenidur 251227) yap. Adet=1, Miktar=1 girilince TARBİL'in ne anlama yorumladığını not et: ekrandaki açıklama ya da doğrulama mesajı. **Kaydetme.**
- [ ] **Step 4:** Kayıtlı satırlar tablosunun (`RadGridStock`) başlık kolonlarını oku: "Kullanılacak Miktarı" ve stok tipi kolonu var mı, adları ne? Kaydedilmiş satır yoksa kullanıcıdan izin alarak **test reçetesi kaydetme**; başlık satırı boş tabloda da okunur.
- [ ] **Step 5:** Aşı belgesi stok penceresinde (`UcVaccineStockSearchModalPage.aspx`) `rbtnStockType` "Parça Stok" değerini (beklenen `2`) ve Parça listesinin tablo kimliğini (`radGridStock_ctl00` mı, ayrı mı) doğrula.
- [ ] **Step 6:** Bulguları spec §2'ye yaz. Açık sekmeleri kapat, TARBİL sekmesini açık bırak.
- [ ] **Step 7:** Bulgular aşağıdaki varsayımlarla çelişiyorsa Task 8/9'u buna göre düzeltip ledger'a `Ruling:` yaz:
  - (a) Açık stoktan seçilen satırda Adet gizli ya da 0, Miktar = ml.
  - (b) Bütün stoktan seçilen ml'li satırda Adet=1, Miktar = kullanılan ml.
  - (c) Parça Stok değeri `2`.

### Task 2: Veri modeli ve stok düşüm kuralı (`InventoryItem.consume`)

**Files:**
- Create: `backend/src/main/resources/db/migration/V85__acik_parca_stok.sql`
- Create: `backend/src/main/java/com/vetos/modules/inventory/domain/StockUnit.java` — `enum StockUnit { ADET, ML, G, FLAKON }`
- Create: `backend/src/main/java/com/vetos/modules/inventory/domain/StockSource.java` — `enum StockSource { WHOLE, OPEN }` (aşıda parça stok = `OPEN`)
- Create: `backend/src/main/java/com/vetos/modules/inventory/domain/StockChange.java` — `record StockChange(int closedDelta, BigDecimal openedDelta)`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/domain/InventoryItem.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/domain/StockMovement.java`
- Test: `backend/src/test/java/com/vetos/modules/inventory/domain/InventoryItemConsumeTest.java`

**Migration içeriği (tamamı burada; sonraki task'lar yeni migration açmaz):**
```sql
ALTER TABLE inventory_items ADD COLUMN package_size NUMERIC(10,2);
ALTER TABLE inventory_items ADD COLUMN opened_quantity NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE inventory_items ADD COLUMN unit_source TEXT NOT NULL DEFAULT 'AUTO';   -- AUTO | MANUAL
-- mevcut 'unit' kolonu (V73, varsayilan 'ADET') kullanim birimi olur: ADET | ML | G | FLAKON
ALTER TABLE stock_movements ADD COLUMN opened_delta NUMERIC(12,2);
ALTER TABLE pending_stock_deduction ALTER COLUMN quantity TYPE NUMERIC(12,2);
ALTER TABLE pending_stock_deduction ALTER COLUMN settled_quantity TYPE NUMERIC(12,2);
ALTER TABLE pending_stock_deduction ADD COLUMN settled_source TEXT;               -- WHOLE | OPEN
ALTER TABLE prescription_items ALTER COLUMN stock_quantity TYPE NUMERIC(12,2);
ALTER TABLE encounter_inventory_usage ALTER COLUMN quantity TYPE NUMERIC(12,2);
ALTER TABLE tarbil_stock_snapshot_line ADD COLUMN doses_per_package INTEGER;
ALTER TABLE tarbil_stock_snapshot ADD COLUMN part_stock_read BOOLEAN NOT NULL DEFAULT FALSE;
```
(Mevcut kalemler: `unit`='ADET', açık 0 → spec §3.8.)

**Interfaces:**
- Produces, `InventoryItem`:
  - alanlar: `StockUnit getUnit()`, `BigDecimal getPackageSize()`, `BigDecimal getOpenedQuantity()`, `boolean isUnitManual()`
  - `StockChange consume(BigDecimal amount, StockSource source)` (`source` null olabilir = Vetly kuralı)
  - `void setStockUnit(StockUnit unit, BigDecimal packageSize, boolean manual)`
  - `StockChange syncTo(int closed, BigDecimal openedOrNull)`
- Produces, `StockMovement`: `static StockMovement record(UUID tenantId, UUID itemId, StockMovementType type, int quantity, BigDecimal openedDelta, StockReferenceType refType, UUID refId)`. Mevcut 6 argümanlı `record` aynen kalır; `openedDelta` null olur.

**`consume` kuralı (spec §3.3):**
- `ADET` ya da `packageSize == null`:
  - `amount` tam sayı olmalı, değilse `IllegalArgumentException`.
  - `closed -= amount`; açık miktar değişmez.
- Diğer birimler:
  - `source == WHOLE` ise önce bir paket aç.
  - Ardından `opened < amount` olduğu sürece paket aç.
  - Sonra `opened -= amount`.
- "Paket aç": `closed -= 1; opened += packageSize`. Kapalı adet 0 olsa da açılır; kapalı negatife iner (bugünkü adet davranışı).
- Dönüş: toplam `closedDelta` ve `openedDelta`.

- [ ] **Step 1: Write the failing tests** (`InventoryItemConsumeTest`; Clavon = ML, paket 50, kapalı 1, açık 0):
```java
@Test void ml_firstUse_opensBottle()        { var c = clavon(1, "0").consume(bd("1"), null);   assertThat(c).isEqualTo(new StockChange(-1, bd("49"))); /* item: closed 0, opened 49.00 */ }
@Test void ml_usesOpenBeforeClosed()        { var i = clavon(2, "49"); i.consume(bd("1.5"), null); assertThat(i.getQuantityOnHand()).isEqualTo(2); assertThat(i.getOpenedQuantity()).isEqualByComparingTo("47.5"); }
@Test void ml_moreThanOpen_opensNext()      { var i = clavon(2, "10"); i.consume(bd("30"), null); assertThat(i.getQuantityOnHand()).isEqualTo(1); assertThat(i.getOpenedQuantity()).isEqualByComparingTo("30"); }
@Test void ml_sourceWhole_opensNewEvenIfOpenExists() { var i = clavon(2, "49"); i.consume(bd("1"), StockSource.WHOLE); assertThat(i.getQuantityOnHand()).isEqualTo(1); assertThat(i.getOpenedQuantity()).isEqualByComparingTo("98"); }
@Test void vaccine_flakon_opensBox()        { var i = vaccine(1, "0", 20); i.consume(BigDecimal.ONE, null); assertThat(i.getQuantityOnHand()).isEqualTo(0); assertThat(i.getOpenedQuantity()).isEqualByComparingTo("19"); }
@Test void adet_behavesAsToday()            { var i = adet(3); assertThat(i.consume(bd("2"), null)).isEqualTo(new StockChange(-2, BigDecimal.ZERO)); assertThat(i.getQuantityOnHand()).isEqualTo(1); }
@Test void adet_rejectsFraction()           { assertThatThrownBy(() -> adet(3).consume(bd("0.5"), null)).isInstanceOf(IllegalArgumentException.class); }
@Test void consumeBeyondStock_opensPackageEvenWhenNoneClosed() { var i = clavon(0, "2"); i.consume(bd("5"), null); assertThat(i.getQuantityOnHand()).isEqualTo(-1); assertThat(i.getOpenedQuantity()).isEqualByComparingTo("47"); }
@Test void syncTo_unknownOpened_keepsOpened() { var i = clavon(1, "49"); i.syncTo(3, null); assertThat(i.getQuantityOnHand()).isEqualTo(3); assertThat(i.getOpenedQuantity()).isEqualByComparingTo("49"); }
```
- [ ] **Step 2:** `cd backend && ./mvnw -q test -Dtest=InventoryItemConsumeTest` → FAIL (derleme hatası: `consume`/`StockUnit` yok).
- [ ] **Step 3:** Migration'ı ve enum/record dosyalarını ekle. `InventoryItem`'a alanları, `consume`, `setStockUnit`, `syncTo` metotlarını, `StockMovement`'a `openedDelta`'yı ekle. `unit` String kolonu `@Enumerated(EnumType.STRING) StockUnit unit = StockUnit.ADET` olur.
- [ ] **Step 4:** Aynı komut → PASS. Ardından `./mvnw -q test -Dtest='ApplicationModulesTest,InventoryItemLookupAdapterTest,TarbilStockSyncAdapterTest'` → PASS. Şema doğrulaması (`ddl-auto: validate`) yeni kolonlarla açılıyor demektir.

### Task 3: Birim tahmini, TARBİL'den kalem açma/eşitleme ve elle düzeltme

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/inventory/domain/PresentationUnitGuesser.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/domain/NewTarbilStockItem.java` — `Integer dosesPerPackage` ve `BigDecimal openedQuantity` ekle
- Modify: `backend/src/main/java/com/vetos/modules/inventory/domain/TarbilStockSyncPort.java` ve adapter'ı (`inventory/infrastructure/TarbilStockSyncAdapter.java`)
- Modify: `backend/src/main/java/com/vetos/modules/inventory/domain/InventoryStockView.java` — `BigDecimal openedQuantity, String unit` ekle
- Modify: `UpdateInventoryItemUseCase.java` + ilgili controller/DTO'lar — `unit`, `packageSize` alanları
- Modify: `ListInventoryItemsUseCase.java` + yanıt DTO'su — `unit`, `packageSize`, `openedQuantity`, `unitSource`
- Test: `backend/src/test/java/com/vetos/modules/inventory/domain/PresentationUnitGuesserTest.java`, `TarbilStockSyncAdapterTest.java` (genişlet)

**Interfaces:**
- Produces:
  - `record UnitGuess(StockUnit unit, BigDecimal packageSize)`
  - `static UnitGuess PresentationUnitGuesser.guess(String tarbilSystem, String presentation, Integer dosesPerPackage)`
- Produces (port, değişen imza):
  - `void syncFromTarbil(UUID itemId, int targetClosed, BigDecimal targetOpenedOrNull, Integer dosesPerPackage, TarbilStockLink link, UUID referenceId)`
  - `createFromTarbil` aynı imza; açık miktar ve dozlar `NewTarbilStockItem` içinde.

**Tahmin kuralları:**
- `HBSAPP_VACCINE`:
  - paket = `dosesPerPackage`;
  - yoksa takdimdeki `(\d+)\s*adet` (ör. "Kutusunda 20 adet flakon", "plastik kutusunda 25 adet");
  - bulunursa `FLAKON`, yoksa `ADET`.
- `VETILAC_MEDICINE`:
  - takdim `pipet|tablet|kapsül|blister|enjektör|şırınga` içeriyorsa `ADET`;
  - değilse `(\d+(?:[.,]\d+)?)\s*(ml|g|gr)\b` ilk eşleşmesi + `şişe|flakon|tüp` geçiyorsa `ML` (ya da `G`), paket = sayı;
  - aksi halde `ADET`.
  - Büyük/küçük harf ve Türkçe karakter duyarsız.
- `unit_source = MANUAL` olan kalemde eşitleme birimi **değiştirmez**.
- Elle güncelleme `MANUAL` yapar. `ML/G/FLAKON` için `packageSize > 0` zorunlu, değilse 400 (`InvalidStockUnitException`, mevcut adlandırmayla 4xx eşlemesi).

- [ ] **Step 1: Write the failing tests:**
```java
@Test void clavon_isMl50()          { assertThat(guess("VETILAC_MEDICINE", "50 ml'lik renksiz cam şişeler", null)).isEqualTo(new UnitGuess(ML, bd("50"))); }
@Test void tenidur_isMl20()         { assertThat(guess("VETILAC_MEDICINE", "kutu içerisinde 20 ml amber renkli cam flakonlarda", null)).isEqualTo(new UnitGuess(ML, bd("20"))); }
@Test void oridermyl_isG10()        { assertThat(guess("VETILAC_MEDICINE", "10 g ’lık alüminyum tüplerde karton kutu içerisinde", null)).isEqualTo(new UnitGuess(G, bd("10"))); }
@Test void pipette_isAdet_evenWithMl() { assertThat(guess("VETILAC_MEDICINE", "karton kutu içerisinde 3 adetlik 1,34 ml’lik pipetlerde", null).unit()).isEqualTo(ADET); }
@Test void tablet_isAdet()          { assertThat(guess("VETILAC_MEDICINE", "Karton kutu içerisinde 10 tabletlik blisterlerde", null).unit()).isEqualTo(ADET); }
@Test void unknownPresentation_isAdet() { assertThat(guess("VETILAC_MEDICINE", null, null)).isEqualTo(new UnitGuess(ADET, null)); }
@Test void vaccine_usesDoseColumn() { assertThat(guess("HBSAPP_VACCINE", "1 ml flakonda sulandırıcısı ile birlikte, Kutusunda 20 adet flakon", 20)).isEqualTo(new UnitGuess(FLAKON, bd("20"))); }
@Test void vaccine_fallsBackToText(){ assertThat(guess("HBSAPP_VACCINE", "1 dozluk aşı içeren şişe ve 1 ml sulandırıcısı ile birlikte; plastik kutusunda 25 adet", null)).isEqualTo(new UnitGuess(FLAKON, bd("25"))); }
```
`TarbilStockSyncAdapterTest`'e eklenecekler:
  - `createFromTarbil_setsGuessedUnitAndOpened`: Clavon, kapalı 0, açık 49 → `unit ML`, paket 50, açık 49.
  - `manualUnit_isNotOverwrittenBySync`: `setStockUnit(ADET, null, true)` sonrası `syncFromTarbil` birimi `ADET` bırakır.
  - `syncFromTarbil_writesMovementWithOpenedDelta`: kapalı 1→0, açık 0→49 → bir `TARBIL_SYNC` hareketi, `quantity` 1 (OUT), `openedDelta` 49.
- [ ] **Step 2:** `./mvnw -q test -Dtest='PresentationUnitGuesserTest,TarbilStockSyncAdapterTest'` → FAIL.
- [ ] **Step 3:** Uygula. `ApplyStockSyncUseCase`'in mevcut çağrılarını yeni imzaya geçir: Task 5'e kadar `targetOpened = null`, `doses = null` ver.
- [ ] **Step 4:** Aynı komut + `ApplyStockSyncUseCaseTest` → PASS.

### Task 4: Ondalıklı kullanım miktarı, bekleyen düşüm ve TARBİL bildirimi

**Files:**
- Modify (encounter):
  - `PrescriptionItem.java` (`BigDecimal stockQuantity`)
  - `IssuePrescriptionUseCase.java` (doğrulama `> 0`)
  - `PrescriptionsController` DTO'ları
  - `EncounterInventoryUsage.java` (`BigDecimal quantity`)
  - malzeme ekleme uç noktası ve DTO
  - `PrescriptionIssuedEvent.StockItem` ve `EncounterFinalizedEvent.UsedItem` (`BigDecimal quantity`)
- Modify (inventory):
  - `PendingStockDeduction.java` (`BigDecimal quantity/settledQuantity`, `StockSource settledSource`)
  - `PendingDeductionService.java`
  - `AutoDeductStockForEncounterUseCase.java`
  - `ApplyVaccinationStockUseCase.java`
  - `ReportedDeductionLine.java`
  - `PendingDeductionLine.java` (+ `unit`)
- Modify (tarbil):
  - `MarkSubmittedRequest.java`
  - `TarbilSubmissionView.PrescriptionPayload.Line` ve aşı yükü
  - `TarbilSubmissionAssembler.java`
- Test:
  - `PendingDeductionServiceTest.java`
  - `AutoDeductStockForEncounterUseCaseTest.java`
  - `ApplyVaccinationStockUseCaseTest.java`
  - `IssuePrescription…Test` (varsa)
  - `TarbilExtensionSecurityIntegrationTest.java` (uçtan uca)

**Interfaces:**
- Produces:
  - `record ReportedDeductionLine(String productName, String serialNumber, BigDecimal amount, StockSource source)` (`source` null = Vetly kuralı)
  - `MarkSubmittedRequest.Line(@NotBlank productName, serialNumber, @PositiveOrZero int quantity, @Positive BigDecimal amount /* null olabilir */, String source /* "WHOLE"|"OPEN"|null */)`
  - `quantity` artık 0 olabilir: açık stoktan düşülen ml satırında adet yok. Bunun için `amount` dolu olmalı; ikisi birden 0/null ise 400.
  - `toReported()`: `amount` null ise `quantity`; `source` geçersizse null.
  - Eski eklentiler (`amount` göndermeyen, `quantity ≥ 1`) aynen çalışır.
- Produces (eklentiye giden plan):
  - `PrescriptionPayload.Line(String itemName, String tarbilProductName, String lotNumber, int quantity, BigDecimal amount, String unit, String source)`
  - `quantity` = bütün stoktan alınacak adet (`ADET` kalemde miktar; ml kalemde `source==WHOLE` ise 1, `OPEN` ise 0)
  - `amount` = kullanılan miktar
  - `source` = `item.getOpenedQuantity() >= amount ? "OPEN" : "WHOLE"` (`ADET` kalemde her zaman `"WHOLE"`)
- Produces (aşı yükü): `stockSource` = açık flakon ≥ 1 ise `"OPEN"`, değilse `"WHOLE"`.
- `PendingDeductionService.deduct` → `item.consume(amount, reportedSource)` ve `StockMovement.record(..., -closedDelta, openedDelta, ...)`. Hareket türü OUT; kapalı değişmediyse `quantity` 0.

- [ ] **Step 1: Write the failing tests:**
  - `PendingDeductionServiceTest.settle_mlFromOpen_deductsOpenOnly`: Clavon kapalı 1, açık 49; bekleyen 1 ml; bildirilen `(…, "63016", 1, OPEN)` → kapalı 1, açık 48, `settledSource OPEN`.
  - `…settle_reportedWholeWins_overVetlyRule` (TARBİL esas): açık 49 varken bildirilen `WHOLE` 1 ml → kapalı 0, açık 98.
  - `…settle_withoutReport_usesVetlyRule`: `reported == null` (VETLY_ONLY) → `consume(amount, null)`.
  - `ApplyVaccinationStockUseCaseTest.administered_opensPendingOfOneFlakon` (mevcut testi `BigDecimal.ONE` ile güncelle) + `settle_vaccineOpensBox`: kutu 1, paket 20 → kapalı 0, açık 19.
  - `AutoDeductStockForEncounterUseCaseTest.unlinkedMlItem_consumesImmediately`: TARBİL'e bağlı olmayan ML kalem, 2.5 ml → hemen düşer, açık −2.5 değil, yeni şişe açılır.
  - `TarbilExtensionSecurityIntegrationTest.mlPrescriptionTravelsWithOpenSourceAndSettlesOpenStock`:
    - reçete 1.5 ml Clavon (açık 49) → eklentinin GET'inde satır `amount 1.5, unit "ML", source "OPEN", quantity 0`;
    - `MARK_SUBMITTED` `{quantity:1, amount:1.5, source:"OPEN"}` → kalem açık 47.5, kapalı değişmez.
  - `IssuePrescription…`: `stockQuantity 0.5` kabul, `0` ve negatif 400.
- [ ] **Step 2:** `./mvnw -q test -Dtest='PendingDeductionServiceTest,ApplyVaccinationStockUseCaseTest,AutoDeductStockForEncounterUseCaseTest,TarbilExtensionSecurityIntegrationTest'` → FAIL.
- [ ] **Step 3:** Uygula. `PendingTarbilStockWork` ve `GetEncounterTarbilDeductionUseCase` gibi miktar okuyan yerleri `BigDecimal`'a geçir; derleyici hepsini gösterir.
- [ ] **Step 4:** Aynı komut → PASS, ardından tam backend paketi: `./mvnw -q test` → hata 0.

### Task 5: Eşitleme iki parçalı

**Files:**
- Modify:
  - `integration/tarbil/api/dto/StockSnapshotRequest.java` ve `StockSnapshotLineRequest.java` (`Integer dosesPerPackage`; snapshot düzeyinde `Boolean partStockRead`)
  - `TarbilStockSnapshot.java`, `TarbilStockSnapshotLine.java`
  - `RecordStockSnapshotUseCase.java`
  - `StockSyncMatcher.java`
  - `ApplyStockSyncUseCase.java`
  - `GetStockSyncViewUseCase.java`
  - `StockSyncLineView.java`, `StockSyncLineResponse.java`
- Test: `StockSyncMatcherTest.java`, `ApplyStockSyncUseCaseTest.java`, `TarbilExtensionSecurityIntegrationTest.java`

**Interfaces:**
- Produces:
  - `record StockSyncMatcher.Target(int closed, BigDecimal opened /* null = bilinmiyor */, Integer dosesPerPackage)`
  - `static Map<String, Target> targets(TarbilStockSnapshot s, List<TarbilStockSnapshotLine> lines)`
- **İlaç:**
  - `closed = Σ quantity`, `opened = Σ openedQuantity` (null → 0; vetilac tablosu bu kolonu hep içerir).
- **Aşı:**
  - `closed = Σ quantity` (parça olmayan satırlar);
  - `opened = s.partStockRead ? Σ quantity (parça satırları) : null`;
  - `doses` = parça olmayan satırların ilk dolu `dosesPerPackage` değeri.
- `MATCHED` ⇔ `item.closed == closed && (opened == null || item.opened.compareTo(opened) == 0)`.
- Görünüm **ürün+seri başına tek satır** döner:
  - `lineId` = o anahtarın ilk satırı;
  - `partStock` = anahtarda parça satırı var mı.
  - Yanıt alanları:
    - `tarbilQuantity` (kapalı toplam)
    - `tarbilOpenedQuantity` (BigDecimal, null = bilinmiyor)
    - `vetlyQuantity`, `vetlyOpenedQuantity`
    - `unit` (kalemin birimi; NEW satırda tahmin)
    - `pendingTarbilCount`
- `ApplyStockSyncUseCase` → `syncFromTarbil(itemId, t.closed(), t.opened(), t.dosesPerPackage(), …)`.

- [ ] **Step 1: Write the failing tests:**
  - `StockSyncMatcherTest.vaccinePartStock_isOpenedNotClosed`: Biocan R yalnız parça 18, `partStockRead=true` → `Target(0, 18, null)`.
  - `…vaccineWholeAndPart_areNotSummed`: aynı seri bütün 1 + parça 19 → `Target(1, 19, 20)`.
  - `…medicineOpened_isCompared`: Clavon kapalı 0 / açık 49 → Vetly (1, 0) ile `QUANTITY_DIFFERS`, Vetly (0, 49) ile `MATCHED`.
  - `ApplyStockSyncUseCaseTest.vaccineSnapshotWithoutPartStockFlag_keepsOpenedQuantity`: `partStockRead=false` → `syncFromTarbil(…, 1, null, 20, …)`.
  - `…clavonApply_keeps49ml`: Vetly 1 adet → işlenince kapalı 0, açık 49 (açık miktar silinmez; spec §4 istenmeyen).
  - `TarbilExtensionSecurityIntegrationTest.partStockAndDosesTravelToSyncScreen`: snapshot `{partStockRead:true, lines:[{Biocan DHPPI,145932,qty 1,doses 20},{Biocan R,665932,qty 18,partStock true}]}` → görünümde iki satır:
    - DHPPI `tarbilQuantity 1, tarbilOpenedQuantity 0, unit FLAKON`;
    - Biocan R `0 / 18`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='StockSyncMatcherTest,ApplyStockSyncUseCaseTest,TarbilExtensionSecurityIntegrationTest'` → FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** Aynı komut → PASS; `./mvnw -q test` → hata 0.

### Task 6: Web ekranları

**Files:**
- Modify:
  - `frontend/src/api/inventoryApi.ts` (`unit`, `packageSize`, `openedQuantity`, `unitSource`)
  - `frontend/src/api/tarbilApi.ts` (Task 5 alanları)
  - `frontend/src/api/clinicalApi.ts` (`stockQuantity` ondalık)
- Modify:
  - `frontend/src/pages/inventory/InventoryPage.tsx`: "Miktar" yerine iki kolon **"Kapalı"** ve **"Açık"**
  - `frontend/src/pages/inventory/ItemDetailModal.tsx`: "Kullanım birimi" (Adet / ml / g / Flakon) ve "Paket büyüklüğü" alanları; tahminse "TARBİL bilgisinden tahmin edildi" notu
- Modify:
  - `frontend/src/pages/encounter/PrescriptionCard.tsx` ve `StockGivePicker.tsx`
  - `frontend/src/pages/encounter/MaterialsUsedCard.tsx`
  - Birim `ML`/`G` ise miktar girişi `step="0.01" min="0.01"` ve etiket "ml"/"g"; aksi halde bugünkü tam sayı adet.
  - Listede "Clavon (0 şişe + 49 ml mevcut)".
- Modify:
  - `frontend/src/pages/inventory/TarbilStockSyncPanel.tsx`: TARBİL ve Vetly hücreleri "0 + 49 ml" biçimi; açık bilinmiyorsa yalnız kapalı
  - `PendingDeductionsPanel.tsx`: miktar + birim
- Create: `frontend/src/pages/inventory/stockFormat.ts`
  - `formatStock(closed: number, opened: number | null, unit: StockUnit, system?: string): string`
  - Gösterim:
    - `ADET` → `"3 adet"`
    - `ML` → `"0 şişe + 49 ml"`
    - `G` → `"1 tüp + 4 g"`
    - `FLAKON` → `"1 kutu + 19 flakon"`
    - `opened` null → yalnız kapalı kısım
- Test:
  - `stockFormat.test.ts`
  - `TarbilStockSyncPanel.test.tsx` (genişlet)
  - `MaterialsUsedCard.test.tsx` (yeni)

- [ ] **Step 1: Write the failing tests:**
```ts
expect(formatStock(0, 49, 'ML')).toBe('0 şişe + 49 ml');
expect(formatStock(1, 19, 'FLAKON')).toBe('1 kutu + 19 flakon');
expect(formatStock(3, 0, 'ADET')).toBe('3 adet');
expect(formatStock(1, null, 'FLAKON')).toBe('1 kutu');
expect(formatStock(0, 47.5, 'ML')).toBe('0 şişe + 47,5 ml');   // tr-TR ondalık
```
  - `TarbilStockSyncPanel` satırında `"0 şişe + 49 ml"` ve `"1 şişe"` (Vetly) görünür.
  - `MaterialsUsedCard`: ML kalem seçilince giriş `step` = `"0.01"` ve `encounterApi.addMaterial` `quantity: 1.5` ile çağrılır.
- [ ] **Step 2:** `cd frontend && npx vitest run src/pages/inventory src/pages/encounter` → FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** `npx vitest run` + `npx tsc -b --noEmit` → hepsi yeşil.

### Task 7: Eklenti — stok görüntüsü (doz kolonu, parça okundu bayrağı)

**Files:**
- Modify:
  - `extension/src/tarbil/steps/stockRows.ts`: aşı bütün stok tablosunda "Üretilen Doz-Küçük Hayvan" kolonu → `StockRow.dosesPerPackage: number | null` (opsiyonel kolon; yoksa null)
  - `extension/src/tarbil/pages/stockSync.ts`: parça tablosu başarıyla okunduysa yükte `partStockRead: true`
  - `extension/src/shared/types.ts` (`StockSnapshotLine.dosesPerPackage?`, snapshot `partStockRead?`)
- Test: `stockRows.test.ts`, `stockSync.test.ts`

- [ ] **Step 1: Write the failing tests:**
  - `stockRows.test.ts › reads doses per package from the vaccine whole-stock grid` → Biocan DHPPI satırında `dosesPerPackage 20`.
  - `stockSync.test.ts › sends partStockRead only when the part grid was read` → okundu: `true`; okunamadı (mevcut "Parça stok okunamadı" yolu): alan yok ya da `false`.
- [ ] **Step 2:** `cd extension && npx vitest run src/tarbil/steps/stockRows.test.ts src/tarbil/pages/stockSync.test.ts` → FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** Aynı komut → PASS.

### Task 8: Eklenti — ilaç reçetesinde Açık Stok seçimi ve ml miktarı

Task 1 bulgularına bağlı. Varsayımlar Task 1 Step 7'de; çelişirse ledger'daki ruling geçerli.

**Files:**
- Modify:
  - `extension/src/tarbil/selectors/stock.ts`: `MEDICINE_STOCK_POPUP.openStockRadio: '_UcPharmacyStockSearchForReceipt_rbtnStockType_1'`, `openGrid: '_UcPharmacyStockSearchForReceipt_radGridOpenStock_ctl00'`
  - `selectors/allowlist.ts`: radyo düğmesi tıklaması izinli listeye
- Modify `extension/src/tarbil/page/ops.ts`:
  - `searchMedicineSerial({ serial, open }: { serial: string; open: boolean })`: `open` ise önce açık stok radyosunu seçer.
  - `setStockQuantities({ quantity, amount }: { quantity: number; amount: number })`:
    - `quantity` 0'dan büyük tam sayı ise Adet alanına yazar; 0 ise Adet alanına dokunmaz.
    - `amount` 2 ondalığa yuvarlanıp tr-TR biçimiyle Miktar alanına yazılır (`1,50`).
    - `amount ≤ 0` → `BAD_INPUT`.
- Modify:
  - `extension/src/tarbil/steps/stockLines.ts`: `readMedicinePopupRows(doc, open)` (açık tabloda son kolon "Açık Stok Miktarı");
  - `readSavedStockLines` "Kullanılacak Miktarı" kolonunu da okur (`amount: number | null`, ondalık);
  - `confirmedLines` → `{ productName, serialNumber, quantity, amount, source }` (`source` plandan).
- Modify:
  - `extension/src/tarbil/pages/medicineStockPopup.ts`: `line.source === 'OPEN'` ise açık listede arar;
  - `prescriptionReceipt.ts`: `setStockQuantities({ quantity: line.quantity, amount: line.amount })`.
- Modify: `extension/src/shared/types.ts` — `PrescriptionPayload.lines[]` + `amount: number; unit: string; source: 'WHOLE' | 'OPEN'`; `ConfirmedLine` + `amount`, `source`.
- Test: `ops.test.ts`, `stockLines.test.ts` (varsa, yoksa oluştur), `medicineStockPopup.test.ts`, `prescriptionReceipt.test.ts`, `allowlist.test.ts`

- [ ] **Step 1: Write the failing tests:**
  - `ops.test.ts › setStockQuantities writes 1,50 to Miktar and leaves Adet when quantity is 0`
  - `ops.test.ts › setStockQuantities rejects amount 0`
  - `medicineStockPopup.test.ts › selects from the open-stock grid when the line source is OPEN` (fikstürde `radGridOpenStock_ctl00`: `Clavon | 50 ml'lik… | 63016 | 27.04.2027 | 49,00`)
  - `stockLines.test.ts › reads Kullanılacak Miktarı as a decimal` (`"1,50"` → 1.5)
  - `prescriptionReceipt.test.ts › reports amount and source for an ml line` (`MARK_SUBMITTED` satırı `{quantity: 0 ya da okunan, amount: 1.5, source: 'OPEN'}`)
  - `allowlist.test.ts`: açık stok radyosu listede; resmi kaydetme butonları **değil**.
- [ ] **Step 2:** `cd extension && npx vitest run` → yeni testler FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** `npx vitest run` + `npx tsc --noEmit` → hepsi yeşil.

### Task 9: Eklenti — aşı belgesinde Parça Stok seçimi

**Files:**
- Modify:
  - `extension/src/tarbil/selectors/stock.ts`: `VACCINE_STOCK_POPUP.partStockRadio` (Task 1 Step 5'teki değer; beklenen `…_rbtnStockType_2`)
  - `allowlist.ts`
  - `page/ops.ts`: `searchSerial({ serial, part }: { serial: string; part: boolean })`
  - `pages/stockPopup.ts`
  - `shared/types.ts`: aşı yükünde `stockSource: 'WHOLE' | 'OPEN'`
- Test: `stockPopup.test.ts`, `ops.test.ts`

- [ ] **Step 1: Write the failing test:** `stockPopup.test.ts › searches the part-stock list when the vaccination stockSource is OPEN` (köprüye `searchSerial` `{ serial: '665932', part: true }` ile gider). Mevcut test `part: false` ile güncellenir.
- [ ] **Step 2:** `npx vitest run src/tarbil/pages/stockPopup.test.ts` → FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** `npx vitest run` → yeşil.

### Task 10: Sürüm, paket, belge ve tam doğrulama

**Files:**
- Modify:
  - `extension/package.json`, `extension/public/manifest.json`, `extension/src/shared/version.test.ts` → **0.3.0**
  - `docs/deployment.md` "TARBİL eklentisi" bölümü: 0.3.0 notu (açık/parça stok için eklenti güncellemesi gerekir; eski eklenti çalışır ama açık stok eşitlenmez)

- [ ] **Step 1:** Sürümü 0.3.0 yap. `cd extension && npx vitest run` → yeşil.
- [ ] **Step 2:** `npm run package`, ardından `npm run build:dev` (yerel test için `dist` localhost'a döner). `frontend/public/downloads/vetly-tarbil-eklenti.json` → `{"version":"0.3.0"}`.
- [ ] **Step 3:** `cd backend && ./mvnw -q test` → hata 0 (`ApplicationModulesTest` dahil); `cd frontend && npx vitest run && npx tsc -b --noEmit` → yeşil.
- [ ] **Step 4:** Yerel backend'i yeniden başlat (`backend/run-local.sh`; çalışan görevi önce durdur). Kullanıcıya elle deneme adımlarını yaz:
  1. TARBİL İlaç Stok Ara → Vetly'ye gönder → Eşitleme'de Clavon "0 şişe + 49 ml".
  2. Stok ekranında Clavon "Kapalı 0 / Açık 49 ml".
  3. Muayenede Clavon 1 ml reçete → eklenti TARBİL'de Açık Stok'tan seçip Miktar 1,00 yazar (kaydetmeyi hekim yapar).
  4. Aşı Stok Ara → Biocan R "0 kutu + 18 flakon".
