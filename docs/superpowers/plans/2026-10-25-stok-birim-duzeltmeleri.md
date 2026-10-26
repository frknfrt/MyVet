# Stok Birimi ve TARBİL Miktar Düzeltmeleri Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vetly'nin TARBİL'e yazdığı Miktar her ürün türünde TARBİL'in düştüğüyle aynı olsun (tablet = tablet sayısı, pipet = ml), geçersiz miktarlar Vetly'de reddedilsin ve açık/parça stok değerlendirmesinden kalan maddeler kapansın.

**Architecture:** `StockUnit`'e TABLET eklenir (açılabilir birim). ML kalemlerine isteğe bağlı "parça" (pipet ml'si + adı) eklenir; stok ve TARBİL hep ml, pipet yalnızca ekranda giriş/gösterim. Birim tahmini tablet/pipet metinlerini tanır. TARBİL gönderim planı kuyruktaki önceki düşümleri hesaba katar; düşüm satır kilidiyle yapılır. Eklenti açık ürün son kullanma tarihini okur ve 0,01 altını yazmaz.

**Tech Stack:** Java 21 / Spring Boot 3.5 / JPA / Flyway, React + vitest, Chrome MV3 eklenti (TypeScript + vitest).

**Spec:** `docs/superpowers/specs/2026-10-25-stok-birim-duzeltmeleri-design.md`

## Global Constraints

- Miktar girişleri: en az 0,01, en çok 2 ondalık, üst sınır 100.000; fazla hane **reddedilir** (yuvarlanmaz); 500 asla.
- TABLET ve parça girişleri tam sayı. Yarım tablet yok.
- TARBİL'e Miktar her zaman virgüllü metin (`"1,5"`); 0,01 altı yazılmaz.
- Açık ürün süresi: geçti = kırmızı, 3 gün içinde = sarı. Açık miktar 0 olunca tarih temizlenir.
- Elle ayarlanmış birim (`unit_source = MANUAL`) ve bekleyen düşümü olan kalemde birim tahmini çalışmaz.
- Eklenti resmi düğmelere (Satır Kaydet, Reçeteyi Onayla, Onayla, çıkış) asla basmaz.
- Main üzerinde, **commit yok**. Yeni migration: `V87__stok_birim_duzeltmeleri.sql`.
- Eklenti sürümü 0.3.1; sunucunun en düşük sürümü (0.3.0) değişmez.

## Review Focus

1. Eski ADET tablet kalemi eşitlemede TABLET'e dönünce kapalı kutu sayısı değişmemeli, açık 0 kalmalı (Task 4 testi `refresh_adetTabletBecomesTablet_keepsClosedBoxes`).
2. Parça tanımlı ML kalemde reçete miktarı ml olarak saklanmalı; ekran 2 pipet → 1,0 ml göndermeli (Task 10 testi `PrescriptionCard › pipet item sends ml`).
3. Kuyrukta açık miktarı aşan iki satır: ikinci satır WHOLE planlanmalı (Task 6 testi `secondLineExceedingRemainingOpened_isWhole`).
4. TARBİL onay satırı (`MarkSubmittedRequest`) TARBİL'in kendi değeridir; Vetly kuralıyla reddedilirse eklenti sonsuz yeniden dener — bu DTO'ya yeni üst/alt sınır konmaz (Task 1 ruling ve testi `markSubmittedAmount_keepsTarbilValueRules`).
5. Eşzamanlı iki düşüm aynı kaleme: biri diğerini ezmemeli (Task 7 gerçek veritabanı testi).

---

### Task 1: Miktar doğrulaması (sunucu)

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/validation/StockAmount.java` (bileşik anotasyon)
- Modify: `encounter/api/dto/IssuePrescriptionRequest.java` (`stockQuantity`), `encounter/api/dto/RecordInventoryUsageRequest.java` (`quantity`), `inventory/api/dto/UpdateInventoryItemRequest.java` (`packageSize`)
- Test: `backend/src/test/java/com/vetos/platform/validation/StockAmountTest.java`

(Bu planda yol verilmeyen sınıflar `backend/src/main/java/com/vetos/modules/` altındadır.)

**Interfaces:**
- Produces: `@StockAmount` = `@DecimalMin("0.01") @DecimalMax("100000") @Digits(integer = 6, fraction = 2)`, null'a izin verir (zorunluluk alanın kendi `@NotNull`'ı ile). Task 2 `pieceSize`, Task 8 `openedDelta` için de kullanılır (openedDelta işaretli olduğu için orada yalnız `@Digits` + mutlak değer kontrolü — Task 8).

- [ ] **Step 1: Test yaz** — `jakarta.validation.Validation.buildDefaultValidatorFactory()` ile:
  - `rejects`: `0.004`, `0`, `-1`, `1.234`, `100000.01` → ihlal var.
  - `accepts`: `0.01`, `1.25`, `100000`, `null` → ihlal yok.
  - `markSubmittedAmount_keepsTarbilValueRules`: `MarkSubmittedRequest.Line` `amount = 0.5` ve `amount = 250000` → ihlal yok (yalnız `@Positive`).
- [ ] **Step 2: Kırmızı.** `cd backend && ./mvnw -q test -Dtest=StockAmountTest` → derleme hatası.
- [ ] **Step 3: Uygula** (`@Constraint(validatedBy = {})` + `@ReportAsSingleViolation`, mesaj: `"Miktar en az 0,01, en fazla 100.000 ve en çok 2 ondalık olmalı"`). Üç DTO alanına ekle.
- [ ] **Step 4: Yeşil** + mevcut controller testleri: `./mvnw -q test -Dtest='StockAmountTest,*Prescription*Test,*InventoryUsage*Test,*InventoryItem*Test'`.

### Task 2: TABLET birimi, parça alanları, açık ürün tarihi, düşük stok (alan modeli)

**Files:**
- Create: `backend/src/main/resources/db/migration/V87__stok_birim_duzeltmeleri.sql`
- Modify: `inventory/domain/StockUnit.java`, `inventory/domain/InventoryItem.java`, `inventory/application/UpdateInventoryItemUseCase.java` (+ command), `inventory/api/dto/UpdateInventoryItemRequest.java`, `InventoryItemResponse`/`InventoryItemDetail`/`ListInventoryItemsUseCase` (yeni alanları döndür)
- Test: `InventoryItemConsumeTest`, `UpdateInventoryItemUnitTest` (mevcut), yeni `InventoryItemLowStockTest`

**Interfaces:**
- Produces:
  - `enum StockUnit { ADET, ML, G, FLAKON, TABLET }`
  - `InventoryItem`: `BigDecimal pieceSize` (`piece_size NUMERIC(10,2)`), `String pieceLabel` (`piece_label TEXT`), `LocalDate openedExpiryDate` (`opened_expiry_date DATE`).
  - `setStockUnit(StockUnit unit, BigDecimal packageSize, BigDecimal pieceSize, String pieceLabel, boolean manual)` — parça yalnız ML'de saklanır, diğer birimlerde null. Eski 3 parametreli imza kaldırılır, çağıranlar güncellenir.
  - `setOpenedExpiry(LocalDate)`; `consume` ve `syncTo` sonrası `openedQuantity == 0` ise `openedExpiryDate = null`.
  - `isBelowReorderThreshold()`: açılabilir kalemde `quantityOnHand + openedQuantity / packageSize <= reorderThreshold` (BigDecimal, ölçek 2, HALF_UP); diğerlerinde bugünkü gibi.
  - Güncelleme komutu/DTO: `pieceSize` (`@StockAmount`), `pieceLabel` (`@Size(max = 30)`). TABLET'te `packageSize` tam sayı olmalı, değilse `InvalidStockUnitException`.
  - Yanıt DTO'ları: `pieceSize`, `pieceLabel`, `openedExpiryDate`.
- V87 ayrıca: `tarbil_stock_snapshot_line.opened_expiry_date DATE`.

- [ ] **Step 1: Testler**
  - `tablet_consumesFromOpenedThenOpensBox`: TABLET, paket 10, kapalı 2, açık 3 → `consume(5)` → kapalı 1, açık 8, `StockChange(-1, +5)`.
  - `openedExpiryClearedWhenOpenedReachesZero`: açık 2, tarih dolu → `consume(2)` → tarih null.
  - `setStockUnit_keepsPieceOnlyForMl`: ML + parça 0,5 "pipet" → saklanır; TABLET + parça → null.
  - `lowStock_countsOpenedAsBoxFraction`: FLAKON paket 20, kapalı 0, açık 19, eşik 0 → false (0,95 > 0); eşik 1 → true.
  - `update_rejectsFractionalTabletPackage`: TABLET, packageSize 10,5 → `InvalidStockUnitException`.
- [ ] **Step 2: Kırmızı.** `./mvnw -q test -Dtest='InventoryItem*Test,UpdateInventoryItemUnitTest'`.
- [ ] **Step 3: Uygula** (migration + alan + use case + DTO).
- [ ] **Step 4: Yeşil** + `./mvnw -q test -Dtest='*Inventory*Test'`.

### Task 3: Birim tahmini (tablet, pipet, "ml'lik" önceliği)

**Files:**
- Modify: `inventory/domain/PresentationUnitGuesser.java`
- Test: `PresentationUnitGuesserTest.java` (mevcut; yeni vakalar eklenir)

**Interfaces:**
- Produces: `record UnitGuess(StockUnit unit, BigDecimal packageSize, BigDecimal pieceSize, String pieceLabel)`; `guess(String tarbilSystem, String presentation, Integer dosesPerPackage)` imzası aynı.

- [ ] **Step 1: Test vakaları** (canlı 2026-10-25 metinleri; `VETILAC_MEDICINE` aksi belirtilmedikçe):

| Takdim şekli | Beklenen (birim, paket, parça, ad) |
|---|---|
| `0.5 ml’lik pipetler 3 adetlik karton kutuda` | ML, 1.5, 0.5, "pipet" |
| `Karton kutu içinde 0,8 ml'lik 4'er adet beyaz polipropilen tüp içeren blisterlerde` | ML, 3.2, 0.8, "tüp" |
| `0.25 ml polipropilen pipet içeren karton kutuda 4 pipet` | ML, 1.0, 0.25, "pipet" |
| `karton kutu içerisinde 3 adetlik 2.68 ml’lik pipetlerde` | ML, 8.04, 2.68, "pipet" |
| `Karton kutu içerisinde 10 tabletlik blisterlerde` | TABLET, 10, null, null |
| `Her tablet alüminyum folyo poşet ile kaplı, 50 tabletlik karton kutularda` | TABLET, 50, null, null |
| `tablet` (sayı yok) | ADET, null |
| `50 ml'lik renksiz cam şişeler` | ML, 50, null |
| `1 ml'de 50 mg içerir, 100 ml'lik amber şişe` | ML, 100, null |
| `10 g ’lık alüminyum tüplerde karton kutu içerisinde` | G, 10, null (tek tüp: parça yok) |
| `200 ml'lik beyaz aluminyum tüplerde` | ML, 200, null |
| `10 ml beyaz LLDPE enjektörlerde karton kutuda` | ML, 10, null |
| `5 ml sırınga` | ML, 5, null |
| HBSAPP_VACCINE `Plastik kutusunda 20 adet flakon`, doz null | FLAKON, 20 |
| HBSAPP_VACCINE `1 dozluk aşı içeren şişe ... plastik kutusunda 25 adet` | FLAKON, 25 |

- [ ] **Step 2: Kırmızı.** `./mvnw -q test -Dtest=PresentationUnitGuesserTest`.
- [ ] **Step 3: Uygula.** Kurallar (sırayla, ilacda):
  1. `(\d+)\s*(tablet|kapsül|kapsul)(lik|lük|luk|lık)?` → TABLET, paket = sayı. "tablet/kapsül" var ama sayı yoksa → ADET.
  2. Pipet/tüp (`pipet|tüp|tup`) **ve** ml birlikte: parça = pipet ml'si (`N ml`), adet = `(\d+)\s*(adet|pipet|'er|'ar)` ya da `(\d+)\s*adetlik` (ml sayısı olmayan sayı); paket = adet × parça; adet yoksa paket = parça ve parça null (tek tüp/şişe). Ad: metindeki ilk "pipet" ya da "tüp".
  3. Enjektör/şırınga (`enjektör|enjektor|şırınga|siringa|sırınga`) + `N ml` → ML, paket N, parça yok.
  4. Şişe/flakon/tüp ml/g: `(\d+(?:[.,]\d+)?)\s*(ml|g|gr)\s*['’.]?\s*(lik|lık|luk|lük|li|lı)` öncelikli, yoksa düz `N ml|g`.
  5. Aksi → ADET. Aşı kuralları değişmez.
- [ ] **Step 4: Yeşil.**

### Task 4: Eşitleme bağlantıları (parça, açık ürün tarihi)

**Files:**
- Modify: `integration/tarbil/api/dto/StockSnapshotLineRequest.java` (+`LocalDate openedExpiryDate`), `StockSnapshotRequest.toInputs`, `application/dto/StockSnapshotLineInput.java`, `domain/TarbilStockSnapshotLine.java` (+kolon), `RecordStockSnapshotUseCase`, `inventory/domain/NewTarbilStockItem.java` (+`openedExpiryDate`), `ApplyStockSyncUseCase`, `inventory/domain/TarbilStockSyncPort.java`, `inventory/infrastructure/TarbilStockSyncAdapter.java`, `integration/tarbil/application/RefreshStockUnitGuessesUseCase.java`
- Test: `TarbilStockSyncAdapterTest`, `RefreshStockUnitGuessesUseCaseTest`, `StockSnapshotRequestTest` (mevcut)

**Interfaces:**
- Consumes: Task 2 `setStockUnit(…5 parametre)`, `setOpenedExpiry`; Task 3 `UnitGuess`.
- Produces: `TarbilStockSyncPort.refreshFromSnapshot(UUID itemId, String system, String presentation, Integer doses, LocalDate openedExpiryDate)` — `refreshUnitGuess`'in yerini alır: birim tahmini (eski kurallarla atlama) + açık ürün tarihi (her zaman, birim elle olsa da; açık 0 ise null). Hareket yazmaz.

- [ ] **Step 1: Testler**
  - `refresh_adetTabletBecomesTablet_keepsClosedBoxes`: ADET, kapalı 3, takdim "10 tabletlik" → TABLET 10, kapalı 3, açık 0.
  - `refresh_setsPieceForPipetBox`: FİPROES metni → ML 1.5, parça 0.5 "pipet".
  - `refresh_writesOpenedExpiry_evenWhenUnitManual`: MANUAL birim, tarih 2026-11-01, açık 0,5 → tarih yazılır, birim değişmez.
  - `createFromTarbil_carriesOpenedExpiry`.
  - `snapshotRequest_mapsOpenedExpiryDate`.
- [ ] **Step 2: Kırmızı** `./mvnw -q test -Dtest='TarbilStockSyncAdapterTest,RefreshStockUnitGuessesUseCaseTest,StockSnapshotRequestTest'`.
- [ ] **Step 3: Uygula.**
- [ ] **Step 4: Yeşil** + `./mvnw -q test -Dtest='*Tarbil*Test,*StockSync*Test'`.

### Task 5: Tablet ve parça miktar kuralları (reçete, kullanım)

**Files:**
- Modify: `encounter/application/IssuePrescriptionUseCase.java`, `encounter/application/RecordInventoryUsageUseCase.java` (veya kalem birimini okuyan yer: `InventoryItemLookupPort`)
- Test: `IssuePrescriptionUseCaseTest`, `RecordInventoryUsageUseCaseTest` (mevcut)

**Interfaces:**
- Consumes: `InventoryItemLookupPort` — kalemin birimi; yoksa `Optional<String> findUnit(UUID itemId)` eklenir.
- Kural: birim TABLET ise miktar tam sayı olmalı; değilse `InvalidPrescriptionStockItemException` (422, mevcut). ADET kalemler bugünkü gibi (tam sayı kuralı mevcutsa korunur).

- [ ] **Step 1: Testler** `tabletItem_rejectsHalfTablet` (1,5 → istisna), `tabletItem_acceptsWholeTablets` (2 → kabul), kullanım için aynı ikili.
- [ ] **Step 2: Kırmızı.** **Step 3: Uygula.** **Step 4: Yeşil** `./mvnw -q test -Dtest='IssuePrescriptionUseCaseTest,RecordInventoryUsageUseCaseTest'`.

### Task 6: TARBİL gönderim planı (aşı ADET, sıralı plan, ADET yukarı yuvarlama)

**Files:**
- Create: `inventory/domain/OpenedQueuePlanner.java`
- Modify: `inventory/domain/PendingDeductionLine.java` (`itemOpenedQuantity` → `plannedOpenedQuantity`), `inventory/application/PendingDeductionService.java` (`toLine`), `inventory/domain/PendingStockDeductionRepository.java` + adapter + JPA (`findPendingForItem(UUID)` createdAt artan), `integration/tarbil/application/TarbilSubmissionAssembler.java` (`planLine`, aşı kaynağı), `InventoryItemLookupPort` (aşı için birim)
- Test: `OpenedQueuePlannerTest`, `TarbilSubmissionAssemblerTest` (mevcut), `PendingDeductionServiceTest` (mevcut)

**Interfaces:**
- Produces: `static BigDecimal OpenedQueuePlanner.openedBefore(BigDecimal opened, BigDecimal packageSize, List<BigDecimal> earlierQuantities)` — önceki düşümleri `consume` kuralıyla (açık yetmezse paket aç) uygulayıp kalan açık miktarı döner; packageSize null/0 ise `opened`.
- `PendingDeductionLine.plannedOpenedQuantity`: bu satırdan önce (createdAt) bekleyen aynı kalem düşümleri uygulanmış açık miktar.

- [ ] **Step 1: Testler**
  - `openedBefore`: açık 49, paket 50, önceki [30] → 19; önceki [30, 30] → 39 (ikincisi yeni şişe açar: 19 → 69 − 30); önceki [] → 49.
  - `secondLineExceedingRemainingOpened_isWhole`: açık 49; satır A 30 (önce) → OPEN; satır B 30 → WHOLE.
  - `adetLine_roundsFractionalUp`: ADET, miktar 1,5 → Adet 2, Miktar 2 (Line `quantity` int = 2, `amount` = 2).
  - `vaccine_adetUnit_isWhole_evenWithOpened`: aşı kalemi ADET, açık 3 → `"WHOLE"`; FLAKON açık 3 → `"OPEN"`.
- [ ] **Step 2: Kırmızı** `./mvnw -q test -Dtest='OpenedQueuePlannerTest,TarbilSubmissionAssemblerTest,PendingDeductionServiceTest'`.
- [ ] **Step 3: Uygula.**
- [ ] **Step 4: Yeşil** + `./mvnw -q test -Dtest='*Tarbil*Test,*Pending*Test'`.

### Task 7: Düşümde satır kilidi

**Files:**
- Modify: `inventory/domain/InventoryItemRepository.java` (+`Optional<InventoryItem> findByIdForUpdate(UUID id)`), `inventory/infrastructure/persistence/InventoryItemJpaRepository.java` (`@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from InventoryItem i where i.id = :id")`), adapter, `inventory/application/RecordStockMovementUseCase.java` (`execute` ve `consume` kilitli okur)
- Test: `RecordStockMovementUseCaseTest` (mevcut; mock `findByIdForUpdate`), yeni `backend/src/test/java/com/vetos/StockLockingTest.java` (`@SpringBootTest`, `TenantScopedTestSupport`, `PatientHistoryQueriesTest` deseni)

- [ ] **Step 1: Testler**
  - Birim: `consume_readsItemWithRowLock` → `verify(repo).findByIdForUpdate(id)`.
  - Gerçek DB: ML kalem paket 50, açık 49; iki iş parçacığı aynı anda `consume(10)` (her biri `TransactionTemplate` içinde, `CountDownLatch` ile aynı anda başlar) → sonunda açık 29, iki hareket.
- [ ] **Step 2: Kırmızı** `./mvnw -q test -Dtest='RecordStockMovementUseCaseTest,StockLockingTest'` (kilitsiz hâlde gerçek DB testi 39 bulur ya da kararsız; en az birim testi kırmızı).
- [ ] **Step 3: Uygula.** **Step 4: Yeşil** (gerçek DB testi 3 kez arka arkaya çalıştırılır).

### Task 8: Açık miktarı elle düzeltme

**Files:**
- Modify: `inventory/api/dto/StockMovementRequest.java` (`quantity` `@PositiveOrZero`, + `BigDecimal openedDelta` `@Digits(integer = 6, fraction = 2)`), `InventoryItemsController.recordMovement`, `RecordStockMovementUseCase` (+`adjustOpened`), `InventoryItem` (+`StockChange adjustOpened(BigDecimal delta)`)
- Test: `InventoryItemConsumeTest`, `RecordStockMovementUseCaseTest`

**Interfaces:**
- `InventoryItem.adjustOpened(delta)`: yalnız `isOpenable()`; sonuç açık < 0 olamaz; 0 olursa açık tarih temizlenir; aksi `InvalidStockUnitException`. `|delta| ≤ 100000`.
- Endpoint: `quantity = 0` ve `openedDelta` dolu ise ADJUSTMENT hareketi `openedDelta` ile; ikisi de 0/null ise 400.

- [ ] **Step 1: Testler** `adjustOpened_minus3ml` (açık 49 → 46, hareket ADJUSTMENT −3), `adjustOpened_belowZero_rejected`, `adjustOpened_onAdet_rejected`, `movement_requiresSomeChange`.
- [ ] **Step 2–4:** kırmızı → uygula → yeşil `./mvnw -q test -Dtest='InventoryItemConsumeTest,RecordStockMovementUseCaseTest,*InventoryItemsController*Test'`.

### Task 9: Eklenti (0,01 altı, virgül, açık ürün tarihi, sürüm)

**Files:**
- Modify: `extension/src/tarbil/page/ops.ts` (`setStockQuantities`), `extension/src/tarbil/steps/stockRows.ts` (yeni sütun), stok gönderen istek tipi (`openedExpiryDate`), `extension/package.json` + `extension/public/manifest.json` → `0.3.1`
- Test: `ops.test.ts` (ya da `setStockQuantities` testinin olduğu dosya), `stockRows.test.ts`

**Interfaces:**
- `StockRow.openedExpiryDate?: string | null` — başlık tam eşitlikle `Açılmış Kalan Miktar Son Kullanma Tarihi`; yalnız ilaç tablosunda, sütun yoksa alan yok; tarih okunamazsa satır şüpheli (mevcut kural gibi tablo `null`).
- `setStockQuantities`: önce `Math.round(value * 100) / 100`; sonuç `< 0.01` ise `PageError('BAD_INPUT', 'Miktar 0,01\'den küçük: …')`; yazılan metin her zaman virgüllü ve noktasız.

- [ ] **Step 1: Testler** `amountBelowMinimumAfterRounding_isNotWritten` (0.004 → hata, alana yazılmaz), `amountIsWrittenWithComma` (1.5 → `"1,5"`, 1234.5 → `"1234,5"`, metinde `.` yok), `readsOpenedExpiryDate` (`30.11.2027` → `2027-11-30`), `missingOpenedExpiryColumn_omitsField`.
- [ ] **Step 2: Kırmızı** `cd extension && npx vitest run src/tarbil`.
- [ ] **Step 3: Uygula** + sürüm 0.3.1. **Step 4: Yeşil** + `npx tsc --noEmit`.

### Task 10: Ekran

**Files:**
- Modify: `frontend/src/api/inventoryApi.ts` (`StockUnit` + `'TABLET'`, `pieceSize`, `pieceLabel`, `openedExpiryDate`, `recordMovement` payload `openedDelta?`), `frontend/src/pages/inventory/stockFormat.ts`, `ItemDetailModal.tsx` (birim düzenleme: TABLET + parça; elle hareket: açık miktar alanı; açık ürün tarihi), `InventoryPage.tsx` (açık ürün süresi rozeti), `pages/encounter/PrescriptionCard.tsx`, `MaterialsUsedCard.tsx`, `StockGivePicker.tsx`, `TarbilDeductionCard.tsx`
- Test: `stockFormat.test.ts`, `ItemDetailModal.test.tsx`, `PrescriptionCard.test.tsx`, yeni `TarbilDeductionCard.test.tsx`

**Interfaces (stockFormat.ts):**
- `type StockUnit = 'ADET' | 'ML' | 'G' | 'FLAKON' | 'TABLET'`; TABLET adları: kapalı "kutu", açık "tablet".
- `formatStock(closed, opened, unit)`: ADET'te `opened > 0` ise açık da gösterilir (`"2 adet, 0,5 açık"`).
- `formatAmount(amount: number, unit, pieceSize?: number|null, pieceLabel?: string|null): string` — parça varsa `"2 pipet (1 ml)"`, TABLET `"2 tablet"`, ML `"1,5 ml"`, ADET `"2 adet"`.
- `openedExpiryStatus(date: string|null, today: Date): 'expired'|'soon'|null` — geçti → expired, 3 gün içinde → soon.
- Giriş: `inputModeFor(unit, pieceSize)` → `{ label, step, min, toBase(n) }`; parça: label `"Miktar (pipet)"`, step 1, min 1, `toBase = n * pieceSize`; TABLET: `"Miktar (tablet)"`, step 1; ML/G/FLAKON: step 0.01, min 0.01; ADET: step 1.

- [ ] **Step 1: Testler**
  - stockFormat: TABLET biçimleri; ADET açık gösterimi; `formatAmount` dört durum; `openedExpiryStatus` (dün → expired, 2 gün sonra → soon, 10 gün → null).
  - `PrescriptionCard › pipet item sends ml`: kalem ML, parça 0,5 "pipet"; 2 girilir → API `stockQuantity: 1`.
  - `PrescriptionCard › tablet item has step 1 and label tablet`.
  - `TarbilDeductionCard › shows unit`: ML 1,5 → "1,5 ml"; TABLET 2 → "2 tablet".
  - `ItemDetailModal › opened adjustment sends openedDelta` (−3 → `recordMovement(id, {movementType: 'ADJUSTMENT', quantity: 0, openedDelta: -3})`).
  - `ItemDetailModal › shows expired opened badge`.
- [ ] **Step 2: Kırmızı** `cd frontend && npx vitest run src/pages/inventory src/pages/encounter`.
- [ ] **Step 3: Uygula.** **Step 4: Yeşil** + `npx tsc --noEmit -p .` + `npx vitest run`.

### Task 11: Notlar ve tam doğrulama

- [ ] **Step 1:** `ApplyVaccinationStockUseCase.cancelled` Javadoc: V77 öncesi kayıtta bütün kutu geri eklenir, sonraki eşitleme düzeltir (spec 4.5/16). `PendingDeductionService.deduct` (source null): "açık varsa önce oradan" kuralı ATS 9.2.4.4 ile aynı (spec 4.5/17).
- [ ] **Step 2:** `cd backend && ./mvnw test` → tümü geçer. `cd frontend && npx tsc --noEmit -p . && npx vitest run`. `cd extension && npx tsc --noEmit && npx vitest run && npm run build` (0.3.1 zip).
- [ ] **Step 3:** `docs/deployment.md`: 0.3.1 notu (V87; eklenti zip; en düşük sürüm değişmez).
- [ ] **Step 4:** Yerel backend'i yeniden başlat, "Started VetosApplication" gör.
