# TARBİL Stoktan Düşen Reçete Doldurma (Faz 1 / Plan B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eklenti, Vetly'deki stoktan düşen reçete aktarımını TARBİL "Stoktan Düşerek İlaç Reçetesi Ekle" formuna doldursun (tarih, sınıf, tür, teşhis, hayvan, ilaç satırları); hekim satır Kaydet'e ve Reçeteyi Onayla'ya basınca kaydedilen satırlarla Vetly'ye AUTO onay göndersin.

**Architecture:** Aşı akışının (`pages/vaccineReceipt.ts` + `pages/stockPopup.ts`) deseni reçete sayfasına taşınır: yeni sayfa türleri `prescriptionReceipt` ve `medicineStockPopup`, MAIN dünyada yeni sayfa işlemleri (teşhis ağacı, stok satırı adet/miktar), satır satır ilerleyen yeni `pages/prescriptionReceipt.ts` akışı ve ilaç stok penceresi akışı. Onay mesajı satırları (`lines`) arka plandan backend'in mevcut `/submitted` gövdesine gider. Backend'de yalnız seri karşılaştırması yerel ayardan bağımsız yapılır.

**Tech Stack:** Chrome MV3 eklentisi (TypeScript, vitest + jsdom, React yan panel); Spring Boot 3 backend (JUnit 5 + Mockito).

**Spec:** `docs/superpowers/specs/2026-10-09-tarbil-stok-dusum-senkronu-design.md` (§1, §7, §8, §11)

## Global Constraints

- Doğrudan yerel `main` üzerinde çalışılır; branch yok; push yalnız kullanıcı isteyince.
- Eklenti resmi düğmelere asla basmaz: `btnInsert`/`btnInsert2` (Reçeteyi Onayla) ve satır `PerformInsertButton` (Kaydet) `FORBIDDEN_BUTTON_PATTERNS`'ta kalır. Ruling (plan): ilaç satırı Kaydet'i de hekimde — TARBİL'de satır Kaydet'in stok düşüp düşmediği doğrulanmadı; aşıda düşüyor (2026-10-04). Doğrulanırsa sonra eklentiye verilebilir.
- Kullanıcı kararı (2026-10-10): TARBİL stoktan **Adet**i düşer. Eklenti "Kullanılacak Adet" = Vetly satır adedi, "Kullanılacak Miktarı" = aynı sayı yazar; takdim şeklinden (ör. "0,4 ml") hesap yapılmaz.
- TARBİL ViewState okunmaz/saklanmaz; eklenti tablolardan yalnız çip, seri, ürün adı, SKT, adet okur (kişisel veri hücrelerine dokunmaz).
- TARBİL'e özgü tüm id/yol bilgisi `extension/src/tarbil/selectors/` altında.
- Başarı paneli `_UCRECEIPTNotification_pnlNotifiSuccess` **doğrulanmadı** (2026-10-10 sayfada yalnız boş `UCRECEIPTNotification_pnlNotifi` var). Yanlışsa AUTO onay hiç tetiklenmez, hekim karttaki "Kaydedildi olarak işaretle" (MANUAL, planlanan adetler) ile kapatır; yanlış onay üretmez.
- Kullanıcının :8080 backend'ine ve :5173 frontend'ine dokunma; bu planda migration yok.
- UI metinleri Türkçe.

## Review Focus

1. Aşı sayfası açıkken aktif aktarım reçete ise (ya da tersi) form yanlış belgeyle doldurulmamalı → Task 7 `leaves a vaccination alone on the prescription page`, Task 8 `leaves a prescription alone on the vaccine page`.
2. Kart "Formu doldur"/"Formu sıfırla" akışı `PRESCRIPTION` belge türüyle yeniden kurulmalı; aksi halde ilaç stok penceresi akışı (belge türü kontrolü) devralmaz → Task 7 `re-arms as a prescription when the vet presses Formu doldur`.
3. Lot numarası küçük harf "i" içeren Vetly kalemi TARBİL'in büyük harfli serisiyle eşleşmeli (Türkçe İ kuralı) → Task 1 testleri.
4. Satır Kaydet sonrası sayfa yeniden çizilince aynı satır ikinci kez eklenmemeli; sonraki ilaca geçilmeli → Task 7 `moves to the next line once the vet saved the row`.
5. Onay anında okunamayan adet (boş hücre) planlanan adetle tamamlanmalı, ürün adı boş satır backend'e gitmemeli (`@NotBlank`) → Task 4 `fills missing names and quantities from the plan`.

---

### Task 1: Seri karşılaştırması yerel ayardan bağımsız

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/inventory/application/PendingDeductionService.java:35,127`
- Test: `backend/src/test/java/com/vetos/modules/inventory/application/PendingDeductionServiceTest.java`
- Modify: `extension/src/tarbil/steps/productRows.ts` (`normalizeSerial`)
- Test: `extension/src/tarbil/steps/productRows.test.ts`

**Interfaces:**
- Produces: `normalizeSerial(s)` = boşluksuz, `i/ı/İ → I`, `toUpperCase()` (yerel ayarsız). Backend `normalize` aynı kural.

- [ ] **Step 1: Backend testi yaz**

`PendingDeductionServiceTest` içine, `matchesSerialIgnoringCaseAndSpaces` testinin altına:

```java
    @Test
    void matchesSerialRegardlessOfTurkishDottedI() {
        UUID a = item("Synulox", "ki7");
        PendingStockDeduction p = pending(a, 1);

        service.settle(StockReferenceType.ENCOUNTER, ref, List.of(new ReportedDeductionLine("Synulox", "KI7", 1)), DeductionSettlement.TARBIL, staff);

        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
    }
```

- [ ] **Step 2: Çalıştır, kırmızı gör**

Run: `cd backend && ./mvnw test -Dtest=PendingDeductionServiceTest`
Expected: FAIL — `matchesSerialRegardlessOfTurkishDottedI` (status PENDING; "ki7" → "Kİ7").

- [ ] **Step 3: Backend düzelt**

`PendingDeductionService`: `TR` sabitini sil (başka kullanımı yoksa; `grep -n "TR)" ` ile kontrol et), `normalize`:

```java
    /** Bosluksuz, i/ı/İ -> I, buyuk harf (yerel ayarsiz): Vetly'de "ki7", TARBIL'de "KI7" ayni seri. */
    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").replaceAll("[iıİ]", "I").toUpperCase(Locale.ROOT);
    }
```

(Metodun mevcut imzasını/adını koru; yalnız gövde ve yorum değişir.)

- [ ] **Step 4: Çalıştır, yeşil gör**

Run: `cd backend && ./mvnw test -Dtest=PendingDeductionServiceTest`
Expected: PASS (tümü).

- [ ] **Step 5: Eklenti testi yaz**

`extension/src/tarbil/steps/productRows.test.ts` içine `describe` bloğuna:

```ts
  it('normalizes serials without Turkish dotted-I rules', () => {
    expect(normalizeSerial('ki 7')).toBe('KI7');
    expect(normalizeSerial('Kİ7')).toBe('KI7');
    expect(normalizeSerial('kı7')).toBe('KI7');
  });
```

(`normalizeSerial` import'u yoksa `./productRows`'tan ekle.)

- [ ] **Step 6: Çalıştır, kırmızı gör**

Run: `cd extension && npx vitest run src/tarbil/steps/productRows.test.ts`
Expected: FAIL — `'Kİ7'` ≠ `'KI7'`.

- [ ] **Step 7: Eklentiyi düzelt**

```ts
/** Bosluksuz, i/ı/İ -> I, buyuk harf (yerel ayarsiz; backend PendingDeductionService ile ayni kural). */
export function normalizeSerial(s: string | null | undefined): string {
  return clean(s).replace(/\s+/g, '').replace(/[iıİ]/g, 'I').toUpperCase();
}
```

- [ ] **Step 8: Çalıştır, yeşil gör; tüm eklenti testleri**

Run: `cd extension && npx vitest run`
Expected: PASS (tümü).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/inventory/application/PendingDeductionService.java backend/src/test/java/com/vetos/modules/inventory/application/PendingDeductionServiceTest.java extension/src/tarbil/steps/productRows.ts extension/src/tarbil/steps/productRows.test.ts
git commit -m "fix(tarbil): seri karşılaştırması Türkçe İ kuralından bağımsız"
```

---

### Task 2: Reçete sayfası seçicileri, sayfa türleri ve izin listesi

**Files:**
- Create: `extension/src/tarbil/selectors/prescriptionReceipt.ts`
- Modify: `extension/src/tarbil/selectors/stock.ts`, `shared.ts`, `allowlist.ts`, `index.ts`
- Test: `extension/src/tarbil/selectors/allowlist.test.ts`, `shared.test.ts`

**Interfaces:**
- Produces: `PRESCRIPTION_RECEIPT` (aşağıdaki alanlar), `MEDICINE_STOCK_POPUP { serial, search, grid }`, `PRESCRIPTION_PAGE_URL`, `pageUrlFor(documentType: DocumentType): string`, `PageKind` += `'prescriptionReceipt' | 'medicineStockPopup'`, izin listesi `prescriptionReceipt.{petVet, addDisease, addStock, stockSearch}`, `medicineStockPopup.search`. `SEARCH` sonekleri her iki arama penceresinde (`_UCVaccineKKBSAnimalSearch_` ve `_UCKKBSAnimalSearch_`) eşleşir.

- [ ] **Step 1: Testleri yaz**

`shared.test.ts` `describe('pageKind')` içine:

```ts
  it('recognizes the stock prescription page only for type=1', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx', search: '?type=1' })).toBe('prescriptionReceipt');
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx', search: '?type=2' })).toBe('other');
  });
  it('recognizes the prescription animal search and medicine stock popups', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ModalPages/KKBSAnimalSearchModalPage.aspx', search: '?AnimalType=x' })).toBe('search');
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ModalPages/UcPharmacyStockSearchForReceiptModalPage.aspx', search: '?animalid=x' })).toBe('medicineStockPopup');
  });
  it('picks the TARBIL page by document type', () => {
    expect(pageUrlFor('PRESCRIPTION')).toBe('https://hbsapp.tarbil.gov.tr/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx?type=1');
    expect(pageUrlFor('VACCINATION')).toBe(VACCINE_PAGE_URL);
  });
  it('matches the animal search fields of both search windows', () => {
    for (const p of ['ctl00_X_UCVaccineKKBSAnimalSearch_', 'ctl00_X_UCKKBSAnimalSearch_']) {
      document.body.innerHTML = `<input id="${p}txtChipNo">`;
      expect(document.querySelector(bySuffix(SEARCH.chip))).not.toBeNull();
    }
  });
```

Dosyanın başına `// @vitest-environment jsdom` ekle (yoksa) ve import'u genişlet: `import { SEARCH, VACCINE_PAGE_URL, bySuffix, pageKind, pageUrlFor } from './';`

`allowlist.test.ts` `describe` içine (import'a `PRESCRIPTION_RECEIPT` ekle: `import { PRESCRIPTION_RECEIPT } from './prescriptionReceipt';`):

```ts
  it('treats Reçeteyi Onayla and the stock row Kaydet as forbidden', () => {
    for (const suffix of PRESCRIPTION_RECEIPT.insertButtons) {
      expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)), suffix).toBe(true);
    }
    expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test('RadGridStock_ctl00_ctl02_ctl03_PerformInsertButton'))).toBe(true);
  });
  it('allows the prescription form helpers and the medicine stock popup search', () => {
    expect(allowedButtonSuffix('prescriptionReceipt', 'petVet')).toBe('_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00_ctl02_ctl00_bntPetVet');
    expect(allowedButtonSuffix('prescriptionReceipt', 'addDisease')).toBe('_cntRECEIPTBodyContent_imgAddDisease');
    expect(allowedButtonSuffix('prescriptionReceipt', 'addStock')).toBe('_RadGridStock_ctl00_ctl02_ctl00_InitInsertButton');
    expect(allowedButtonSuffix('prescriptionReceipt', 'stockSearch')).toBe('_RadGridStock_ctl00_ctl02_ctl03_imgSearch');
    expect(allowedButtonSuffix('medicineStockPopup', 'search')).toBe('_UcPharmacyStockSearchForReceipt_btnSearch');
    expect(allowedButtonSuffix('prescriptionReceipt', 'insert')).toBeNull();
  });
```

- [ ] **Step 2: Çalıştır, kırmızı gör**

Run: `cd extension && npx vitest run src/tarbil/selectors`
Expected: FAIL — `pageUrlFor` / `./prescriptionReceipt` yok.

- [ ] **Step 3: Seçicileri yaz**

`extension/src/tarbil/selectors/prescriptionReceipt.ts`:

```ts
// "Stoktan Duserek Ilac Recetesi Ekle" sayfasina ozgu id sonekleri (spec 2026-10-09 S11; 2026-10-10 canli okuma).
const B = '_cntRECEIPTBodyContent_';

export const PRESCRIPTION_RECEIPT = {
  date: `${B}dpTreatmentStartDate`,
  /** Muayene/Tedavi Recetesi (varsayilan secili); _1 Tavsiye Recetesi. */
  treatmentClass: `${B}rbtnReceiptClass_0`,
  animalType: `${B}cbxAnimalType`,
  // Teshis: RadComboBox icinde RadTreeView (dugum degeri = tarbil_disease.id), "+" ile RadListBox'a eklenir.
  diseaseTree: `${B}cbxDisease_i0_treeDisease`,
  addDisease: `${B}imgAddDisease`,
  diagnosedList: `${B}lbxDiagnosedDisease`,
  // Hayvan bolumu asi sayfasiyla ayni bilesen (PetVet arama penceresi KKBSAnimalSearchModalPage.aspx).
  petVet: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00_ctl02_ctl00_bntPetVet',
  animalGrid: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00',
  // Urun bolumu: "Stok Ekle" ekleme satiri acar; buyutec stok penceresini window.open ile acar.
  stockGrid: '_RadGridStock_ctl00',
  addStock: '_RadGridStock_ctl00_ctl02_ctl00_InitInsertButton',
  stockSearch: '_RadGridStock_ctl00_ctl02_ctl03_imgSearch',
  editSerial: '_RadGridStock_ctl00_ctl02_ctl03_lblSerialNumber',
  editProductName: '_RadGridStock_ctl00_ctl02_ctl03_txtStockProductName',
  /** Kullanilacak Adet (tam sayi; TARBIL stoktan bunu duser). */
  stockQuantity: '_RadGridStock_ctl00_ctl02_ctl03_txtStockQuantity',
  /** Kullanilacak Miktari (2 ondalik, min 0,01); kullanici karari: Adet ile ayni sayi. */
  stockAmount: '_RadGridStock_ctl00_ctl02_ctl03_txtStockAmount',
  // Receteyi Onayla: eklenti ASLA basmaz, yalniz hekimin tiklamasini fark eder.
  insertButtons: [`${B}btnInsert`, `${B}btnInsert2`],
  // DOGRULANMADI (2026-10-10): sayfada bos UCRECEIPTNotification_pnlNotifi var; asi sayfasindaki desene gore aday.
  successPanel: '_UCRECEIPTNotification_pnlNotifiSuccess',
} as const;
```

`stock.ts` sonuna:

```ts
/** hbsapp ilac recetesi stok penceresi (UcPharmacyStockSearchForReceiptModalPage.aspx; 2026-10-10 canli). "Sec" pencereyi kapatir. */
export const MEDICINE_STOCK_POPUP = {
  serial: '_UcPharmacyStockSearchForReceipt_txtSerialNumber',
  search: '_UcPharmacyStockSearchForReceipt_btnSearch',
  grid: '_UcPharmacyStockSearchForReceipt_radGridStock_ctl00',
} as const;
```

`shared.ts` değişiklikleri:

```ts
import type { DocumentType } from '../../shared/types';
```

`VACCINE_PAGE_URL` altına:

```ts
const PRESCRIPTION_PAGE_PATH = '/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx';
export const PRESCRIPTION_PAGE_URL = `${TARBIL_ORIGIN}${PRESCRIPTION_PAGE_PATH}?type=1`;

/** Aktarimin dolduruldugu TARBIL sayfasi. */
export function pageUrlFor(documentType: DocumentType): string {
  return documentType === 'PRESCRIPTION' ? PRESCRIPTION_PAGE_URL : VACCINE_PAGE_URL;
}
```

`SEARCH_PAGE_FILE` satırını değiştir:

```ts
// Asi sayfasi VaccineKKBS..., recete sayfasi KKBS... penceresini acar; alanlar ayni (2026-10-10 canli).
const SEARCH_PAGE_FILES = ['vaccinekkbsanimalsearchmodalpage.aspx', 'kkbsanimalsearchmodalpage.aspx'];
const MEDICINE_STOCK_POPUP_FILE = 'ucpharmacystocksearchforreceiptmodalpage.aspx';
```

`SEARCH` sonekleri (ön ek `_UCVaccine` / `_UC` iki pencerede farklı; sonek ikisini de yakalar):

```ts
export const SEARCH = {
  chip: 'KKBSAnimalSearch_txtChipNo',
  passport: 'KKBSAnimalSearch_txtPassportNo',
  search: 'KKBSAnimalSearch_btnSearch',
  grid: 'KKBSAnimalSearch_radGridAnimal_ctl00',
  transfer: 'KKBSAnimalSearch_btnAddBulkAnimal',
  // (mevcut adres filtresi yorumu korunur)
  addressFilters: [
    'KKBSAnimalSearch_UCProvinceDistrictNeigbourhood_cbxNeigbourhood',
    'KKBSAnimalSearch_UCProvinceDistrictNeigbourhood_cbxDistrict',
    'KKBSAnimalSearch_UCProvinceDistrictNeigbourhood_cbxProvince',
  ],
} as const;
```

`PageKind` ve `pageKind`:

```ts
export type PageKind =
  | 'receipt' | 'prescriptionReceipt' | 'search' | 'home' | 'vaccineStock' | 'medicineStock'
  | 'vaccineStockPopup' | 'medicineStockPopup' | 'other';

export function pageKind(loc: { pathname: string; search: string }): PageKind {
  const path = loc.pathname.toLowerCase();
  const type1 = new URLSearchParams(loc.search).get('type') === '1';
  if (path === VACCINE_PAGE_PATH.toLowerCase()) return type1 ? 'receipt' : 'other';
  if (path === PRESCRIPTION_PAGE_PATH.toLowerCase()) return type1 ? 'prescriptionReceipt' : 'other';
  if (SEARCH_PAGE_FILES.some((f) => path.endsWith(`/${f}`))) return 'search';
  if (path.endsWith(`/${VACCINE_STOCK_POPUP_FILE}`)) return 'vaccineStockPopup';
  if (path.endsWith(`/${MEDICINE_STOCK_POPUP_FILE}`)) return 'medicineStockPopup';
  if (path === VACCINE_STOCK_PATH) return 'vaccineStock';
  if (path === MEDICINE_STOCK_PATH) return 'medicineStock';
  if (HOME_PATHS.includes(path)) return 'home';
  return 'other';
}
```

`allowlist.ts`:

```ts
import { PRESCRIPTION_RECEIPT } from './prescriptionReceipt';
import { MEDICINE_STOCK, MEDICINE_STOCK_POPUP, VACCINE_STOCK, VACCINE_STOCK_POPUP } from './stock';
```

`ALLOWED_BUTTONS`'a:

```ts
  prescriptionReceipt: {
    petVet: PRESCRIPTION_RECEIPT.petVet,
    addDisease: PRESCRIPTION_RECEIPT.addDisease,
    addStock: PRESCRIPTION_RECEIPT.addStock,
    stockSearch: PRESCRIPTION_RECEIPT.stockSearch,
  },
  medicineStockPopup: { search: MEDICINE_STOCK_POPUP.search },
```

`index.ts`'e: `export * from './prescriptionReceipt';`

- [ ] **Step 4: Çalıştır, yeşil gör; tüm eklenti testleri**

Run: `cd extension && npx vitest run`
Expected: PASS (tümü; `ops.test.ts`/`findAnimal.test.ts` id'leri `_UCVaccineKKBSAnimalSearch_` ön ekiyle yine eşleşir).

- [ ] **Step 5: Commit**

```bash
git add extension/src/tarbil/selectors
git commit -m "feat(eklenti): reçete sayfası seçicileri, sayfa türleri ve izin listesi"
```

---

### Task 3: Reçete sayfası işlemleri (MAIN dünya)

**Files:**
- Modify: `extension/src/tarbil/page/telerik.ts` (yeni `setTextLoose`)
- Modify: `extension/src/tarbil/page/ops.ts`
- Test: `extension/src/tarbil/page/ops.test.ts`

**Interfaces:**
- Consumes: Task 2 `PRESCRIPTION_RECEIPT`, `MEDICINE_STOCK_POPUP`, izin listesi.
- Produces (köprü komutları): `setTreatmentDate({ iso })`, `ensureTreatmentClass() → { changed }`, `selectPrescriptionAnimalType({ value })`, `addDisease({ value }) → { added: boolean; listed: boolean }` (bilinmeyen değer → `OPTION_NOT_FOUND`), `searchMedicineSerial({ serial })`, `selectStockRow({ linkId })` (aşı **ve** ilaç penceresi tablosu), `setStockQuantities({ quantity })` (Adet ve Miktar = quantity).

- [ ] **Step 1: Testleri yaz**

`ops.test.ts` `describe('page ops')` sonuna:

```ts
  const RP = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntRECEIPTBodyContent_';

  it('sets the treatment start date on the prescription form', async () => {
    document.body.innerHTML = `<div id="${RP}dpTreatmentStartDate"></div>`;
    const log: string[] = [];
    const picker = { get_selectedDate: () => null, set_selectedDate: (d: Date) => log.push(d.toDateString()) };
    const env: TelerikEnv = { doc: document, find: (id) => (id === `${RP}dpTreatmentStartDate` ? picker : null), prm: () => null, isReady: () => true };

    await createPageOps(env).setTreatmentDate({ iso: '2026-10-09' });

    expect(log).toEqual([new Date(2026, 9, 9).toDateString()]);
  });

  it('selects Muayene/Tedavi Reçetesi only when it is not selected', async () => {
    document.body.innerHTML = `<input type="radio" name="c" id="${RP}rbtnReceiptClass_0"><input type="radio" name="c" id="${RP}rbtnReceiptClass_1" checked>`;
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => null, isReady: () => true };
    const ops = createPageOps(env);

    expect(await ops.ensureTreatmentClass()).toEqual({ changed: true });
    expect((document.getElementById(`${RP}rbtnReceiptClass_0`) as HTMLInputElement).checked).toBe(true);
    expect(await ops.ensureTreatmentClass()).toEqual({ changed: false });
  });

  it('checks the disease in the tree and presses the add button', async () => {
    document.body.innerHTML = `<div id="${RP}cbxDisease_i0_treeDisease"></div><div id="${RP}lbxDiagnosedDisease"></div>
      <input type="image" id="${RP}imgAddDisease">`;
    const log: string[] = [];
    let listed = false;
    document.getElementById(`${RP}imgAddDisease`)!.addEventListener('click', (e) => { e.preventDefault(); log.push('add'); listed = true; });
    const node = { get_text: () => 'PNÖMONİ', set_checked: (v: boolean) => log.push(`check:${v}`) };
    const comps: Record<string, Record<string, unknown>> = {
      [`${RP}cbxDisease_i0_treeDisease`]: { findNodeByValue: (v: string) => (v === 'd1' ? node : null) },
      [`${RP}lbxDiagnosedDisease`]: { findItemByValue: () => (listed ? {} : null), findItemByText: () => null },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true };

    expect(await createPageOps(env).addDisease({ value: 'd1' })).toEqual({ added: true, listed: true });
    expect(log).toEqual(['check:true', 'add']);
  });

  it('does not add a disease that is already in the diagnosed list', async () => {
    document.body.innerHTML = `<div id="${RP}cbxDisease_i0_treeDisease"></div><div id="${RP}lbxDiagnosedDisease"></div>
      <input type="image" id="${RP}imgAddDisease">`;
    let clicked = 0;
    document.getElementById(`${RP}imgAddDisease`)!.addEventListener('click', (e) => { e.preventDefault(); clicked++; });
    const comps: Record<string, Record<string, unknown>> = {
      [`${RP}cbxDisease_i0_treeDisease`]: { findNodeByValue: () => ({ get_text: () => 'PNÖMONİ', set_checked: () => undefined }) },
      [`${RP}lbxDiagnosedDisease`]: { findItemByValue: () => null, findItemByText: (t: string) => (t === 'PNÖMONİ' ? {} : null) },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true };

    expect(await createPageOps(env).addDisease({ value: 'd1' })).toEqual({ added: false, listed: true });
    expect(clicked).toBe(0);
  });

  it('rejects a disease that is not in the TARBIL tree', async () => {
    document.body.innerHTML = `<div id="${RP}cbxDisease_i0_treeDisease"></div><div id="${RP}lbxDiagnosedDisease"></div>`;
    const comps: Record<string, Record<string, unknown>> = {
      [`${RP}cbxDisease_i0_treeDisease`]: { findNodeByValue: () => null },
      [`${RP}lbxDiagnosedDisease`]: { findItemByValue: () => null, findItemByText: () => null },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true };

    await expect(createPageOps(env).addDisease({ value: 'x' })).rejects.toMatchObject({ code: 'OPTION_NOT_FOUND' });
  });

  it('searches the medicine stock popup by serial (plain input)', async () => {
    const P = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_';
    document.body.innerHTML = `<input id="${P}txtSerialNumber"><a id="${P}btnSearch"></a>`;
    const prm = instantPrm();
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = { [`${P}btnSearch`]: { click: () => { log.push('ara'); prm.fire(); } } };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchMedicineSerial({ serial: 'KV04Z81' });

    expect((document.getElementById(`${P}txtSerialNumber`) as HTMLInputElement).value).toBe('KV04Z81');
    expect(log).toEqual(['ara']);
  });

  it('clicks Seç inside the medicine stock popup grid', async () => {
    const G = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_radGridStock_ctl00';
    document.body.innerHTML = `<table id="${G}"><tbody><tr><td><a id="${G}_ctl04_SelectlinkButton">Seç</a></td></tr></tbody></table>`;
    let clicked = 0;
    document.getElementById(`${G}_ctl04_SelectlinkButton`)!.addEventListener('click', () => clicked++);
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => null, isReady: () => true };

    await createPageOps(env).selectStockRow({ linkId: `${G}_ctl04_SelectlinkButton` });

    expect(clicked).toBe(1);
  });

  it('writes the same number into Kullanılacak Adet and Kullanılacak Miktarı', async () => {
    const R = `${RP}RadGridStock_ctl00_ctl02_ctl03_`;
    document.body.innerHTML = `<input id="${R}txtStockQuantity"><input id="${R}txtStockAmount">`;
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [`${R}txtStockQuantity`]: { set_value: (v: string) => log.push(`adet:${v}`) },
      [`${R}txtStockAmount`]: { set_value: (v: string) => log.push(`miktar:${v}`) },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true };
    const ops = createPageOps(env);

    await ops.setStockQuantities({ quantity: 2 });
    await expect(ops.setStockQuantities({ quantity: 0 })).rejects.toMatchObject({ code: 'BAD_INPUT' });

    expect(log).toEqual(['adet:2', 'miktar:2']);
  });
```

- [ ] **Step 2: Çalıştır, kırmızı gör**

Run: `cd extension && npx vitest run src/tarbil/page/ops.test.ts`
Expected: FAIL — `setTreatmentDate is not a function` vb.

- [ ] **Step 3: `setTextLoose` ekle (`telerik.ts`, `setText` altına)**

```ts
/** Telerik kutusu varsa onun uzerinden, yoksa duz input (ilac stok penceresinin seri kutusu Telerik olmayabilir). */
export function setTextLoose(env: TelerikEnv, suffix: string, value: string): void {
  const id = clientId(env, suffix);
  const c = env.find(id);
  if (c && typeof c.set_value === 'function') {
    c.set_value(value);
    return;
  }
  (env.doc.getElementById(id) as HTMLInputElement).value = value;
}
```

- [ ] **Step 4: `ops.ts` komutlarını ekle**

Import'lar:

```ts
import { MEDICINE_STOCK, MEDICINE_STOCK_POPUP, PRESCRIPTION_RECEIPT, RECEIPT, SEARCH, VACCINE_STOCK, VACCINE_STOCK_POPUP, allowedButtonSuffix, bySuffix } from '../selectors';
import { PageError, clearCombo, clickButton, clickElement, clientId, selectComboValue, setDate, setText, setTextLoose, showAllRows, waitUntil, type TelerikEnv } from './telerik';
```

`selectStockRow`'u iki pencere tablosunu kabul edecek şekilde değiştir:

```ts
    // Yalniz stok pencerelerindeki (asi ya da ilac) "Sec" baglantilari; tiklama pencereyi kapatir, sonuc beklenmez.
    selectStockRow: ({ linkId }: { linkId: string }) => {
      const link = env.doc.getElementById(linkId);
      const inGrid = [VACCINE_STOCK_POPUP.grid, MEDICINE_STOCK_POPUP.grid]
        .map((g) => `table${bySuffix(g)} a[id$="SelectlinkButton"]`)
        .join(', ');
      if (!link || !link.matches(inGrid)) {
        return Promise.reject(new PageError('NOT_FOUND', 'Stok tablosunda böyle bir Seç bağlantısı yok'));
      }
      (link as HTMLElement).click();
      return Promise.resolve();
    },
```

Dönen nesneye ekle:

```ts
    // Stoktan Duserek Ilac Recetesi Ekle (spec 2026-10-09 S11).
    setTreatmentDate: ({ iso }: { iso: string }) => setDate(env, PRESCRIPTION_RECEIPT.date, iso),
    ensureTreatmentClass: async () => {
      const radio = env.doc.querySelector<HTMLInputElement>(bySuffix(PRESCRIPTION_RECEIPT.treatmentClass));
      if (!radio) throw new PageError('NOT_FOUND', 'Reçete sınıfı bulunamadı');
      if (radio.checked) return { changed: false };
      await clickElement(env, radio.id);
      return { changed: true };
    },
    selectPrescriptionAnimalType: ({ value }: { value: string }) => selectComboValue(env, PRESCRIPTION_RECEIPT.animalType, value),
    // Teshis agacindan dugum isaretlenir, "+" ile eklenen listesine alinir; listede (deger ya da adla) varsa dokunulmaz.
    addDisease: async ({ value }: { value: string }) => {
      const tree = env.find(clientId(env, PRESCRIPTION_RECEIPT.diseaseTree));
      const node = tree?.findNodeByValue?.(value);
      if (!node) throw new PageError('OPTION_NOT_FOUND', 'Teşhis TARBİL listesinde yok');
      const text = String(node.get_text());
      const listed = () => {
        const list = env.find(clientId(env, PRESCRIPTION_RECEIPT.diagnosedList));
        return Boolean(list && (list.findItemByValue(value) || list.findItemByText(text)));
      };
      if (listed()) return { added: false, listed: true };
      node.set_checked(true);
      await clickButton(env, allowed('prescriptionReceipt', 'addDisease'));
      return { added: true, listed: listed() };
    },
    searchMedicineSerial: async ({ serial }: { serial: string }) => {
      setTextLoose(env, MEDICINE_STOCK_POPUP.serial, serial);
      await clickButton(env, allowed('medicineStockPopup', 'search'));
    },
    // Kullanici karari (2026-10-10): TARBIL Adet'i duser; Miktar ayni sayi.
    setStockQuantities: async ({ quantity }: { quantity: number }) => {
      if (!Number.isInteger(quantity) || quantity < 1) throw new PageError('BAD_INPUT', `Geçersiz adet: ${quantity}`);
      setText(env, PRESCRIPTION_RECEIPT.stockQuantity, String(quantity));
      setText(env, PRESCRIPTION_RECEIPT.stockAmount, String(quantity));
    },
```

`clientId` `telerik.ts`'te zaten `export`; değilse export et.

- [ ] **Step 5: Çalıştır, yeşil gör; tüm eklenti testleri**

Run: `cd extension && npx vitest run`
Expected: PASS (tümü).

- [ ] **Step 6: Commit**

```bash
git add extension/src/tarbil/page
git commit -m "feat(eklenti): reçete formu sayfa işlemleri (teşhis, stok satırı adet/miktar)"
```

---

### Task 4: Reçete satırlarını okuma

**Files:**
- Modify: `extension/src/tarbil/steps/productRows.ts` (`headerIndex` ve yeni `readPopupRows` export)
- Modify: `extension/src/shared/types.ts` (`ConfirmedLine`, `PlannedLine`)
- Create: `extension/src/tarbil/steps/stockLines.ts`
- Test: `extension/src/tarbil/steps/stockLines.test.ts`

**Interfaces:**
- Consumes: Task 2 seçiciler, Task 1 `normalizeSerial`.
- Produces:
  - `shared/types.ts`: `export interface ConfirmedLine { productName: string; serialNumber: string; quantity: number }`, `export type PlannedLine = PrescriptionPayload['lines'][number]`.
  - `readMedicinePopupRows(doc): StockPopupRow[]`
  - `readStockEditRow(doc): { serial: string; productName: string } | null` (satır açık değilse null; açık ama ürün seçilmemişse `serial: ''`)
  - `readSavedStockLines(doc): SavedStockLine[]` (`{ productName; serial; quantity: number | null }`)
  - `nextLine(planned: PlannedLine[], saved: SavedStockLine[]): number` (kaydedilmemiş ilk satırın sırası; yoksa -1)
  - `confirmedLines(saved, planned): ConfirmedLine[]`

- [ ] **Step 1: Testleri yaz**

`extension/src/tarbil/steps/stockLines.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import type { PlannedLine } from '../../shared/types';
import { confirmedLines, nextLine, readMedicinePopupRows, readSavedStockLines, readStockEditRow } from './stockLines';

const P = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntRECEIPTBodyContent_';
const T = `${P}RadGridStock_ctl00`;
const HEAD = ['Detay', 'Detay', 'Ürün', 'Takdim Şekli', 'Seri Numarası', 'Son Kullanım Tarihi', 'Stok Miktarı', 'Stok Tipi', 'Kullanılacak Adet', 'Kullanılacak Miktarı', 'Sil'];

function stockGrid(saved: [string, string, string][]) {
  document.body.innerHTML = `<table id="${T}"><thead><tr>${HEAD.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${
    saved.length === 0 ? '<tr class="rgNoRecords"><td colspan="11">Kayıt Bulunamadı.</td></tr>'
      : saved.map(([name, serial, qty], i) => `<tr id="${T}__${i}" class="rgRow"><td></td><td></td><td>${name}</td><td>Kutu</td>
          <td>${serial}</td><td>01.01.2027</td><td>3 Adet</td><td>Bütün Stok</td><td>${qty}</td><td>${qty}</td><td></td></tr>`).join('')
  }</tbody></table>`;
}

const plan: PlannedLine[] = [
  { itemName: 'Advantage', tarbilProductName: 'Advantage % 10', lotNumber: 'KV04Z81', quantity: 1 },
  { itemName: 'Synulox', tarbilProductName: 'SYNULOX', lotNumber: 's1', quantity: 2 },
];

describe('stock lines', () => {
  it('reads saved rows of the prescription stock grid', () => {
    stockGrid([['Advantage % 10', 'KV04Z81', '1'], ['SYNULOX', 'S1', '']]);

    expect(readSavedStockLines(document)).toEqual([
      { productName: 'Advantage % 10', serial: 'KV04Z81', quantity: 1 },
      { productName: 'SYNULOX', serial: 'S1', quantity: null },
    ]);
  });

  it('reads nothing from an empty grid', () => {
    stockGrid([]);
    expect(readSavedStockLines(document)).toEqual([]);
  });

  it('reads the open insert row', () => {
    document.body.innerHTML = `<input id="${T}_ctl02_ctl03_txtStockProductName" value="Advantage % 10"><span id="${T}_ctl02_ctl03_lblSerialNumber"> kv04z81 </span>`;
    expect(readStockEditRow(document)).toEqual({ serial: 'KV04Z81', productName: 'Advantage % 10' });
    document.body.innerHTML = '';
    expect(readStockEditRow(document)).toBeNull();
  });

  it('finds the first planned line that is not saved yet', () => {
    expect(nextLine(plan, [])).toBe(0);
    expect(nextLine(plan, [{ productName: 'x', serial: 'KV04Z81', quantity: 1 }])).toBe(1);
    expect(nextLine(plan, [{ productName: 'x', serial: 'KV04Z81', quantity: 1 }, { productName: 'y', serial: 'S1', quantity: 2 }])).toBe(-1);
  });

  it('fills missing names and quantities from the plan', () => {
    expect(confirmedLines([
      { productName: '', serial: 'KV04Z81', quantity: 1 },
      { productName: 'SYNULOX', serial: 'S1', quantity: null },
      { productName: '', serial: 'ZZ9', quantity: null },
    ], plan)).toEqual([
      { productName: 'Advantage % 10', serialNumber: 'KV04Z81', quantity: 1 },
      { productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 },
    ]);
  });

  it('reads the medicine stock popup rows', () => {
    const G = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_radGridStock_ctl00';
    document.body.innerHTML = `<table id="${G}"><thead><tr><th></th><th>Ürün Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
      <th>Son Kullanma Tarihi</th><th>Ürün Miktarı</th></tr></thead><tbody><tr id="${G}__0"><td><a id="${G}_ctl04_SelectlinkButton">Seç</a></td>
      <td>Advantage % 10</td><td>Kutu</td><td>KV04Z81</td><td>31.01.2027</td><td>3</td></tr></tbody></table>`;

    expect(readMedicinePopupRows(document)).toEqual([
      { productName: 'Advantage % 10', serial: 'KV04Z81', expiryDate: '2027-01-31', linkId: `${G}_ctl04_SelectlinkButton` },
    ]);
  });
});
```

- [ ] **Step 2: Çalıştır, kırmızı gör**

Run: `cd extension && npx vitest run src/tarbil/steps/stockLines.test.ts`
Expected: FAIL — `./stockLines` yok.

- [ ] **Step 3: Tipler (`shared/types.ts`, `PrescriptionPayload` altına)**

```ts
export type PlannedLine = PrescriptionPayload['lines'][number];

/** TARBIL'de kaydedilmis recete satiri; onayla birlikte Vetly'ye gider (POST .../submitted lines). */
export interface ConfirmedLine {
  productName: string;
  serialNumber: string;
  quantity: number;
}
```

- [ ] **Step 4: `productRows.ts` paylaşımı**

`headerIndex`'i `export function headerIndex(...)` yap. `readStockPopupRows`'u genelleştir:

```ts
/** Stok penceresi tablosu (asi ya da ilac): urun adi basligi pencereye gore degisir. */
export function readPopupRows(doc: Document, gridSuffix: string, nameHeader: string): StockPopupRow[] {
  const table = doc.querySelector<HTMLTableElement>(`table${bySuffix(gridSuffix)}`);
  if (!table) return [];
  const name = headerIndex(table, nameHeader, 1);
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

export function readStockPopupRows(doc: Document): StockPopupRow[] {
  return readPopupRows(doc, VACCINE_STOCK_POPUP.grid, 'Aşı Adı');
}
```

- [ ] **Step 5: `stockLines.ts`**

```ts
import type { ConfirmedLine, PlannedLine } from '../../shared/types';
import { MEDICINE_STOCK_POPUP, PRESCRIPTION_RECEIPT, bySuffix } from '../selectors';
import { headerIndex, normalizeSerial, readPopupRows, type StockPopupRow } from './productRows';

// Gizlilik: yalniz urun adi, seri ve adet okunur (urun verisi, kisisel veri yok).

export interface SavedStockLine {
  productName: string;
  serial: string;
  quantity: number | null;
}

const clean = (s: string | null | undefined) => (s ?? '').replace(/\s+/g, ' ').trim();

export function readMedicinePopupRows(doc: Document): StockPopupRow[] {
  return readPopupRows(doc, MEDICINE_STOCK_POPUP.grid, 'Ürün Adı');
}

/** "Stok Ekle" ekleme satiri: urun secilmeden seri bos gelir (2026-10-10 canli). */
export function readStockEditRow(doc: Document): { serial: string; productName: string } | null {
  const serial = doc.querySelector(bySuffix(PRESCRIPTION_RECEIPT.editSerial));
  if (!serial) return null;
  const name = doc.querySelector<HTMLInputElement>(bySuffix(PRESCRIPTION_RECEIPT.editProductName));
  return { serial: normalizeSerial(serial.textContent), productName: clean(name?.value) };
}

/** Satir Kaydet'e basilmis ilac satirlari. Kayitli satir yapisi canlidan dogrulanmadi: sutunlar basliktan bulunur. */
export function readSavedStockLines(doc: Document): SavedStockLine[] {
  const table = doc.querySelector<HTMLTableElement>(`table${bySuffix(PRESCRIPTION_RECEIPT.stockGrid)}`);
  if (!table) return [];
  const name = headerIndex(table, 'Ürün', 2);
  const serial = headerIndex(table, 'Seri Numarası', 4);
  const quantity = headerIndex(table, 'Kullanılacak Adet', 8);
  return Array.from(table.tBodies[0]?.rows ?? [])
    .filter((r) => r.id.startsWith(`${table.id}__`) && !r.classList.contains('rgEditRow'))
    .map((r) => {
      const n = Number.parseInt(clean(r.cells[quantity]?.textContent).replace(/\D/g, ''), 10);
      return {
        productName: clean(r.cells[name]?.textContent),
        serial: normalizeSerial(r.cells[serial]?.textContent),
        quantity: Number.isInteger(n) && n > 0 ? n : null,
      };
    })
    .filter((l) => l.serial.length > 0);
}

/** Kaydedilmemis ilk planlanan satirin sirasi; hepsi kaydedildiyse -1. */
export function nextLine(planned: PlannedLine[], saved: SavedStockLine[]): number {
  const done = new Set(saved.map((l) => l.serial));
  return planned.findIndex((l) => !done.has(normalizeSerial(l.lotNumber)));
}

/** Onaylanan satirlar: okunamayan ad/adet plandan (seriyle) tamamlanir; tamamlanamayan satir gonderilmez. */
export function confirmedLines(saved: SavedStockLine[], planned: PlannedLine[]): ConfirmedLine[] {
  return saved.flatMap((l) => {
    const plan = planned.find((p) => normalizeSerial(p.lotNumber) === l.serial);
    const productName = l.productName || plan?.tarbilProductName || plan?.itemName || '';
    const quantity = l.quantity ?? plan?.quantity ?? null;
    return productName && quantity ? [{ productName, serialNumber: l.serial, quantity }] : [];
  });
}
```

- [ ] **Step 6: Çalıştır, yeşil gör; tüm eklenti testleri**

Run: `cd extension && npx vitest run`
Expected: PASS (tümü).

- [ ] **Step 7: Commit**

```bash
git add extension/src/shared/types.ts extension/src/tarbil/steps
git commit -m "feat(eklenti): reçete stok satırlarını okuma ve onay satırları"
```

---

### Task 5: Arka plan — onay satırları ve belge türüne göre sayfa

**Files:**
- Modify: `extension/src/shared/messages.ts`, `extension/src/background/vetlyApi.ts`, `confirmationOutbox.ts`, `router.ts`, `tarbilTab.ts`, `index.ts`
- Test: `extension/src/background/vetlyApi.test.ts`, `confirmationOutbox.test.ts`, `router.test.ts`, `tarbilTab.test.ts`

**Interfaces:**
- Consumes: Task 4 `ConfirmedLine`, Task 2 `pageUrlFor`.
- Produces:
  - `BackgroundRequest` `MARK_SUBMITTED` += `lines?: ConfirmedLine[]`; `SET_ACTIVE` += `documentType?: DocumentType`.
  - `api.markSubmitted(id, method, tarbilReference, lines?)` — gövde `{ method, tarbilReference }` (+ `lines` doluysa).
  - `OutboxEntry.lines?: ConfirmedLine[]`.
  - Router dep `openTarbilPage?: (documentType: DocumentType) => Promise<void>` (eski `openVaccinePage` yerine).
  - `openTarbilPage(tabs, focusWindow, documentType = 'VACCINATION')`; `openVaccinePage` aynı imzayla kalır (sarmalayıcı).

- [ ] **Step 1: Testleri yaz**

`vetlyApi.test.ts`:

```ts
  it('sends confirmed prescription lines with the submitted call', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const { fn, calls } = fakeFetch(200, {});
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fn });

    await api.markSubmitted('s1', 'AUTO', null, [{ productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 }]);
    await api.markSubmitted('s2', 'MANUAL', null);

    expect(JSON.parse(calls[0].init.body as string)).toEqual({
      method: 'AUTO', tarbilReference: null, lines: [{ productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 }],
    });
    expect(JSON.parse(calls[1].init.body as string)).toEqual({ method: 'MANUAL', tarbilReference: null });
  });
```

`confirmationOutbox.test.ts`:

```ts
  it('replays the confirmed lines of a queued prescription', async () => {
    const seen: unknown[] = [];
    const api = { markSubmitted: async (...args: unknown[]) => { seen.push(args); return {} as never; } };
    const outbox = createConfirmationOutbox(memoryStore(), api);
    const lines = [{ productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 }];
    await outbox.enqueue({ id: 's1', method: 'AUTO', tarbilReference: null, lines });

    await outbox.flush();

    expect(seen).toEqual([['s1', 'AUTO', null, lines]]);
  });
```

`router.test.ts` — mevcut `'opens the TARBIL vaccine page when the side panel starts a transfer'` testindeki `openVaccinePage: async () => { opened++; }` satırını `openTarbilPage: async () => { opened++; }` yap ve şu testleri ekle:

```ts
  it('arms a prescription flow and opens the prescription page', async () => {
    const tokens = createTokenStore(memoryStore());
    const api = {} as never;
    const session = memoryStore();
    const opened: string[] = [];
    const router = createRouter({
      api, tokens, outbox: createConfirmationOutbox(memoryStore(), api), session,
      openTarbilPage: async (documentType) => { opened.push(documentType); },
    });

    await router.handle({ type: 'SET_ACTIVE', id: 's9', documentType: 'PRESCRIPTION' });

    expect(await session.get('tarbilFlow')).toMatchObject({ submissionId: 's9', documentType: 'PRESCRIPTION', step: 'armed' });
    expect(opened).toEqual(['PRESCRIPTION']);
  });

  it('passes confirmed lines to the API', async () => {
    const seen: unknown[] = [];
    const lines = [{ productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 }];
    const { router } = setup(async (...args: unknown[]) => { seen.push(args); return {}; });

    await router.handle({ type: 'MARK_SUBMITTED', id: 's1', method: 'AUTO', tarbilReference: null, lines });

    expect(seen).toEqual([['s1', 'AUTO', null, lines]]);
  });

  it('queues confirmed lines when offline', async () => {
    const lines = [{ productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 }];
    const enqueued: unknown[] = [];
    const outbox = { enqueue: async (e: unknown) => { enqueued.push(e); }, size: async () => enqueued.length, flush: async () => 0 };
    const api = { markSubmitted: async () => { throw new ApiError('OFFLINE', 'offline'); } } as never;
    const router = createRouter({ api, tokens: createTokenStore(memoryStore()), outbox: outbox as never, session: memoryStore() });

    await router.handle({ type: 'MARK_SUBMITTED', id: 's1', method: 'AUTO', tarbilReference: null, lines });

    expect(enqueued).toEqual([{ id: 's1', method: 'AUTO', tarbilReference: null, lines }]);
  });
```

(`setup`'ın `markSubmitted` parametresi `(...args: unknown[]) => Promise<unknown>` olarak genişletilir.)

`tarbilTab.test.ts` — import'a `openTarbilPage` ve `PRESCRIPTION_PAGE_URL` ekle:

```ts
describe('openTarbilPage', () => {
  it('opens the stock prescription page for a prescription', async () => {
    const { tabs, focusWindow, log } = fakeTabs([{ id: 2, url: RECEIPT, windowId: 7 }]);

    await openTarbilPage(tabs, focusWindow, 'PRESCRIPTION');

    expect(log).toEqual([`create:${PRESCRIPTION_PAGE_URL}`]);
  });

  it('switches to an open stock prescription tab', async () => {
    const { tabs, focusWindow, log } = fakeTabs([{ id: 4, url: PRESCRIPTION_PAGE_URL, windowId: 8 }]);

    await openTarbilPage(tabs, focusWindow, 'PRESCRIPTION');

    expect(log).toEqual(['update:4:{"active":true}', 'focus:8']);
  });
});
```

- [ ] **Step 2: Çalıştır, kırmızı gör**

Run: `cd extension && npx vitest run src/background`
Expected: FAIL — `lines` gövdede yok, `openTarbilPage` yok, `documentType` arm edilmiyor.

- [ ] **Step 3: Uygula**

`messages.ts`:

```ts
import type { ConfirmationMethod, ConfirmedLine, DocumentType, ExtensionProfile, StockSnapshotLine, StockSystem, Submission } from './types';
...
  | { type: 'SET_ACTIVE'; id: string; documentType?: DocumentType }
...
  | { type: 'MARK_SUBMITTED'; id: string; method: ConfirmationMethod; tarbilReference: string | null; lines?: ConfirmedLine[] }
```

`vetlyApi.ts`:

```ts
    markSubmitted: (id: string, method: ConfirmationMethod, tarbilReference: string | null, lines?: ConfirmedLine[]) =>
      request<Submission>(`/api/v1/tarbil-extension/submissions/${id}/submitted`, {
        method: 'POST',
        body: JSON.stringify(lines && lines.length > 0 ? { method, tarbilReference, lines } : { method, tarbilReference }),
      }),
```

(`ConfirmedLine` import'unu ekle.)

`confirmationOutbox.ts`: `OutboxEntry`'ye `lines?: ConfirmedLine[];` ekle; flush'ta `await api.markSubmitted(entry.id, entry.method, entry.tarbilReference, entry.lines);`.

`router.ts`:

```ts
import type { DocumentType } from '../shared/types';
...
  /** Yan panelden aktarim baslatilinca belge turunun TARBIL sayfasini one getirir (yoksa acar). */
  openTarbilPage?: (documentType: DocumentType) => Promise<void>;
...
export function createRouter({ api, tokens, outbox, session, openTarbilPage }: Deps) {
...
          case 'SET_ACTIVE': {
            const documentType = req.documentType ?? 'VACCINATION';
            await flow.arm(req.id, documentType);
            await session.set(ACTIVE_KEY, req.id);
            // Sekme acilamazsa aktarim yine hazir: hekim sayfayi kendisi acinca form doldurulur.
            await openTarbilPage?.(documentType).catch(() => undefined);
            return { ok: true, data: null };
          }
...
          case 'MARK_SUBMITTED':
            try {
              return { ok: true, data: await api.markSubmitted(req.id, req.method, req.tarbilReference, req.lines) };
            } catch (e) {
              if (e instanceof ApiError && (e.code === 'OFFLINE' || e.code === 'UNKNOWN')) {
                await outbox.enqueue({ id: req.id, method: req.method, tarbilReference: req.tarbilReference, lines: req.lines });
                return { ok: true, data: { queued: true } };
              }
              throw e;
            }
```

`tarbilTab.ts`:

```ts
import type { DocumentType } from '../shared/types';
import { pageKind, pageUrlFor } from '../tarbil/selectors';
...
/**
 * Belge turunun TARBIL sayfasini one getirir: acik bir sekmesi varsa ona gecer, yoksa yeni sekmede acar. Oturum kapaliysa
 * TARBIL giris ekranini gosterir; e-Devlet donusunde icerik betigi sayfaya gecer (spec S12.2). Baska bir TARBIL sekmesinin
 * adresi DEGISTIRILMEZ -- orada kaydedilmemis is olabilir.
 */
export async function openTarbilPage(
  tabs: TabsApi,
  focusWindow: (windowId: number) => Promise<unknown>,
  documentType: DocumentType = 'VACCINATION',
): Promise<void> {
  const kind = documentType === 'PRESCRIPTION' ? 'prescriptionReceipt' : 'receipt';
  const open = await tabs.query({ url: 'https://hbsapp.tarbil.gov.tr/*' });
  const page = open.find((t) => {
    if (t.id === undefined || !t.url) return false;
    const u = new URL(t.url);
    return pageKind({ pathname: u.pathname, search: u.search }) === kind;
  });
  if (page?.id !== undefined) {
    await tabs.update(page.id, { active: true });
    if (page.windowId !== undefined) await focusWindow(page.windowId);
    return;
  }
  await tabs.create({ url: pageUrlFor(documentType), active: true });
}

export const openVaccinePage = (tabs: TabsApi, focusWindow: (windowId: number) => Promise<unknown>) =>
  openTarbilPage(tabs, focusWindow, 'VACCINATION');
```

`index.ts`:

```ts
import { openTarbilPage } from './tarbilTab';
...
  openTarbilPage: (documentType) =>
    openTarbilPage(chrome.tabs, (windowId) => chrome.windows.update(windowId, { focused: true }), documentType),
```

- [ ] **Step 4: Çalıştır, yeşil gör; tüm eklenti testleri ve tip kontrolü**

Run: `cd extension && npx vitest run && npx tsc --noEmit`
Expected: PASS; tsc hatasız.

- [ ] **Step 5: Commit**

```bash
git add extension/src/shared/messages.ts extension/src/background
git commit -m "feat(eklenti): onay satırları backend'e; aktarım belge türünün sayfasını açar"
```

---

### Task 6: İlaç stok penceresi akışı

**Files:**
- Create: `extension/src/tarbil/pages/medicineStockPopup.ts`
- Test: `extension/src/tarbil/pages/medicineStockPopup.test.ts`

**Interfaces:**
- Consumes: Task 3 `searchMedicineSerial`, `selectStockRow`; Task 4 `readMedicinePopupRows`; `pickStockRow`, `PRODUCT_HANDOFF_MS`, `StockPopupDeps` (`pages/stockPopup.ts`).
- Produces: `runMedicineStockPopupFlow(d: StockPopupDeps): Promise<void>`. Akış durumu: `documentType === 'PRESCRIPTION'`, `step === 'choosingProduct'`, `stepData.lineIndex` = `s.prescription.lines` sırası. Teslim (hekime bırakma) → `step: 'awaitingConfirm', message`.

- [ ] **Step 1: Testleri yaz**

`medicineStockPopup.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import type { BackgroundRequest } from '../../shared/messages';
import { createFlowStore } from '../../shared/flowStore';
import type { DocumentType, Submission } from '../../shared/types';
import type { CardView } from '../core/card';
import type { Send } from '../steps/findAnimal';
import { runMedicineStockPopupFlow } from './medicineStockPopup';

const G = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_radGridStock_ctl00';

function popup(rows: [string, string, string][]) {
  document.body.innerHTML = `<table id="${G}"><thead><tr><th></th><th>Ürün Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
    <th>Son Kullanma Tarihi</th><th>Ürün Miktarı</th></tr></thead><tbody>${rows
      .map(([name, serial, skt], i) => `<tr id="${G}__${i}"><td><a id="${G}_ctl0${4 + 2 * i}_SelectlinkButton">Seç</a></td>
        <td>${name}</td><td>Kutu</td><td>${serial}</td><td>${skt}</td><td>3</td></tr>`)
      .join('')}</tbody></table>`;
}

const submission = {
  id: 's1', status: 'PENDING', documentType: 'PRESCRIPTION', lotNumber: null,
  prescription: {
    sourceKind: 'ENCOUNTER', tarbilDiseaseId: 'd1', tarbilDiseaseName: 'Pnömoni', treatmentStartDate: '2026-10-09',
    lines: [
      { itemName: 'Advantage', tarbilProductName: 'Advantage % 10', lotNumber: 'KV04Z81', quantity: 1 },
      { itemName: 'Synulox', tarbilProductName: 'SYNULOX', lotNumber: 'S1', quantity: 2 },
    ],
  },
} as Submission;

async function setup(documentType: DocumentType = 'PRESCRIPTION', lineIndex = 1) {
  const flow = createFlowStore(memoryStore(), () => 1000);
  await flow.arm('s1', documentType);
  await flow.update('s1', { step: 'choosingProduct', stepData: { lineIndex } });
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const send = (async (req: BackgroundRequest) => (req.type === 'GET_ACTIVE' ? { ok: true, data: submission } : { ok: true, data: null })) as Send;
  const shown: CardView[] = [];
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined };
  const deps = { bridge, flow, send, doc: document, card, now: () => 6000, today: () => '2026-10-10' };
  return { deps, flow, calls };
}

describe('runMedicineStockPopupFlow', () => {
  it('searches the serial of the current line and selects its single row', async () => {
    popup([['SYNULOX', 'S1', '31.01.2027'], ['Advantage % 10', 'KV04Z81', '31.01.2027']]);
    const { deps, calls } = await setup();

    await runMedicineStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchMedicineSerial', 'selectStockRow']);
    expect(calls[1].args).toEqual({ serial: 'S1' });
    expect(calls[2].args).toEqual({ linkId: `${G}_ctl04_SelectlinkButton` });
  });

  it('hands the choice to the vet when the serial is not in stock', async () => {
    popup([['Advantage % 10', 'KV04Z81', '31.01.2027']]);
    const { deps, calls, flow } = await setup();

    await runMedicineStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchMedicineSerial']);
    expect(await flow.get()).toMatchObject({ step: 'awaitingConfirm', message: expect.stringContaining('bulunamadı') });
  });

  it('does nothing for a vaccination flow', async () => {
    popup([['SYNULOX', 'S1', '31.01.2027']]);
    const { deps, calls } = await setup('VACCINATION');

    await runMedicineStockPopupFlow(deps);

    expect(calls).toEqual([]);
  });
});
```

- [ ] **Step 2: Çalıştır, kırmızı gör**

Run: `cd extension && npx vitest run src/tarbil/pages/medicineStockPopup.test.ts`
Expected: FAIL — modül yok.

- [ ] **Step 3: Uygula**

`medicineStockPopup.ts`:

```ts
import type { Submission } from '../../shared/types';
import { views } from '../core/views';
import { pickStockRow } from '../steps/productRows';
import { readMedicinePopupRows } from '../steps/stockLines';
import { PRODUCT_HANDOFF_MS, type StockPopupDeps } from './stockPopup';

const code = (e: unknown) => (e as { code?: string })?.code ?? 'UNKNOWN';

/**
 * Ilac recetesi stok penceresi (spec 2026-10-09 S11): recetenin siradaki satirinin serisi aranir; seri + TARBIL urun adi
 * birebir TEK ve SKT'si gecmemis satir varsa "Sec"e basilir (pencere kapanir, urun ana sayfadaki ekleme satirina gelir).
 * Aksi halde secim hekime birakilir; nedeni akisa yazilir. Pencereyi hekim kendisi actiysa hicbir sey yapilmaz.
 */
export async function runMedicineStockPopupFlow(d: StockPopupDeps): Promise<void> {
  const state = await d.flow.get();
  if (!state || state.documentType !== 'PRESCRIPTION' || state.step !== 'choosingProduct') return;
  if (d.now() - state.updatedAt > PRODUCT_HANDOFF_MS) return;
  const res = await d.send<Submission | null>({ type: 'GET_ACTIVE' });
  const s = res.ok ? res.data : null;
  const line = s?.prescription?.lines[Number(state.stepData?.lineIndex)];
  if (!s || s.id !== state.submissionId || !line?.lotNumber) return;
  const serial = line.lotNumber;

  const handOver = async (message: string) => {
    await d.flow.update(s.id, { step: 'awaitingConfirm', message });
    d.card.show(views.stockPopup(`${message} Satırı kendiniz seçin.`, 'warn'));
  };

  d.card.show(views.stockPopup(`Seri ${serial} aranıyor…`, 'muted'));
  try {
    await d.bridge.call('ready');
    await d.bridge.call('searchMedicineSerial', { serial });
  } catch (e) {
    await handOver(`Stokta arama yapılamadı (${code(e)}).`);
    return;
  }

  const pick = pickStockRow(readMedicinePopupRows(d.doc), { serial, productName: line.tarbilProductName, today: d.today() });
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
    case 'unknownExpiry':
      await handOver(`Seri ${serial} için son kullanma tarihi okunamadı.`);
      return;
    case 'nameMismatch':
      await handOver(`Seri ${serial} TARBİL'de "${pick.row.productName}" olarak görünüyor; Vetly'deki ilaçla aynı değil.`);
      return;
    case 'one':
      d.card.show(views.stockPopup('İlaç seçiliyor…', 'muted'));
      // "Sec" pencereyi kapatir; yanit gelmeyebilir. Ana sayfa ekleme satirini serisinden dogrular.
      await d.bridge.call('selectStockRow', { linkId: pick.row.linkId }, 5000).catch(() => undefined);
  }
}
```

- [ ] **Step 4: Çalıştır, yeşil gör**

Run: `cd extension && npx vitest run src/tarbil/pages`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add extension/src/tarbil/pages/medicineStockPopup.ts extension/src/tarbil/pages/medicineStockPopup.test.ts
git commit -m "feat(eklenti): ilaç stok penceresinde satırın serisini seçme"
```

---

### Task 7: Reçete sayfası akışı

**Files:**
- Create: `extension/src/tarbil/core/prescriptionViews.ts`
- Create: `extension/src/tarbil/pages/prescriptionReceipt.ts`
- Test: `extension/src/tarbil/pages/prescriptionReceipt.test.ts`

**Interfaces:**
- Consumes: Task 3 köprü komutları; Task 4 `readStockEditRow`, `readSavedStockLines`, `nextLine`, `confirmedLines`; `ReceiptDeps`, `POPUP_WAIT_MS`, `SUCCESS_WINDOW_MS` (`pages/vaccineReceipt.ts`); `countReceiptAnimals`, `readReceiptChips`, `normalizeChip`, `normalizePassport`; `resolveAnimalType`; `hasFreshSuccess`, `isConfirmClick`, `markStaleSuccess`.
- Produces: `createPrescriptionFlow(d: ReceiptDeps): { start(): Promise<void>; flowChanged(state: FlowState | null): void }`. Adımlar: `armed → filling → searching/transferred/needsVet → choosingProduct(lineIndex) → productReady(lineIndex) → … → awaitingConfirm → done`; `stepData.lineIndex`, onay tıklamasında `stepData.confirmedLines` (anlık görüntü). Card eylemleri: `fill`, `petvet`, `resetForm`, `stockWindow`, `manual`, `dismiss`.

- [ ] **Step 1: Testleri yaz**

`prescriptionReceipt.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import { createFlowStore, type FlowStep } from '../../shared/flowStore';
import type { BackgroundRequest } from '../../shared/messages';
import type { Submission } from '../../shared/types';
import type { CardView } from '../core/card';
import { ANIMAL_TYPE } from '../selectors';
import type { Send } from '../steps/findAnimal';
import { createPrescriptionFlow } from './prescriptionReceipt';

const P = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntRECEIPTBodyContent_';
const GRID = `${P}ReceiptAddOtherAnimal_RadOtherAnimal_ctl00`;
const T = `${P}RadGridStock_ctl00`;
const CHIP = '900000000000001';
const HEAD = ['Detay', 'Detay', 'Ürün', 'Takdim Şekli', 'Seri Numarası', 'Son Kullanım Tarihi', 'Stok Miktarı', 'Stok Tipi', 'Kullanılacak Adet', 'Kullanılacak Miktarı', 'Sil'];

function page() {
  document.body.innerHTML = `
    <input id="${P}dpTreatmentStartDate">
    <a id="${P}btnInsert"><input id="${P}btnInsert_input" type="button" value="Reçeteyi Onayla"></a>
    <table id="${GRID}"><thead><tr><th></th><th style="display:none"></th><th>Sistem Küpe/Çip No</th></tr></thead><tbody></tbody></table>
    <div id="stock"></div>`;
}

function addAnimalRow(chip: string) {
  document.querySelector(`#${GRID} tbody`)!.insertAdjacentHTML('beforeend', `<tr id="${GRID}__0"><td></td><td></td><td>${chip}</td></tr>`);
}

/** Ilac tablosu: kaydedilmis satirlar tbody'de; acik ekleme satirinin alanlari ctl02_ctl03 id'leriyle. */
function stockGrid({ saved = [] as [string, string, string][], edit = null as string | null } = {}) {
  document.getElementById('stock')!.innerHTML = `<table id="${T}"><thead><tr>${HEAD.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${
    saved.map(([name, serial, qty], i) => `<tr id="${T}__${i}" class="rgRow"><td></td><td></td><td>${name}</td><td>Kutu</td><td>${serial}</td>
      <td>01.01.2027</td><td>3 Adet</td><td>Bütün Stok</td><td>${qty}</td><td>${qty}</td><td></td></tr>`).join('')
  }</tbody></table>${edit !== null ? `<input id="${T}_ctl02_ctl03_txtStockProductName" value="x"><span id="${T}_ctl02_ctl03_lblSerialNumber">${edit}</span>` : ''}`;
}

function showSuccess() {
  document.body.insertAdjacentHTML('beforeend', '<div id="bodyCPH_ContentPlaceHolder1_UCRECEIPTNotification_pnlNotifiSuccess">Kaydedildi</div>');
}

const LINES = [
  { itemName: 'Advantage', tarbilProductName: 'Advantage % 10', lotNumber: 'KV04Z81', quantity: 1 },
  { itemName: 'Synulox', tarbilProductName: 'SYNULOX', lotNumber: 'S1', quantity: 2 },
];

const base = {
  id: 's1', vaccinationRecordId: 'r1', status: 'PENDING', patientName: 'Pamuk', microchipNumber: CHIP, passportNumber: null,
  speciesId: null, speciesName: 'Kedi', breedName: null, sex: null, birthDate: null, vaccineName: 'Reçete',
  lotNumber: null, tarbilProductName: null, administeredDate: null, submittedAt: null, confirmationMethod: null, tarbilReference: null,
  vaccineKey: '', vaccineMapping: null, speciesMapping: null, documentType: 'PRESCRIPTION',
  prescription: { sourceKind: 'ENCOUNTER', tarbilDiseaseId: 'd1', tarbilDiseaseName: 'Pnömoni', treatmentStartDate: '2026-10-09', lines: LINES },
} satisfies Submission;

async function setup(sub: Partial<Submission> = {}, step: FlowStep | null = 'armed', stepData?: Record<string, unknown>,
  results: Record<string, unknown> = { addDisease: { added: true, listed: true } }) {
  let t = 100_000;
  const now = () => t;
  const flow = createFlowStore(memoryStore(), now);
  if (step) {
    await flow.arm('s1', 'PRESCRIPTION');
    if (step !== 'armed' || stepData) await flow.update('s1', { step, ...(stepData ? { stepData } : {}) });
  }
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return results[op] as never; } };
  const sent: BackgroundRequest[] = [];
  const submission = { ...base, ...sub };
  const send = (async (req: BackgroundRequest) => { sent.push(req); return { ok: true, data: submission }; }) as Send;
  const shown: CardView[] = [];
  let action: (id: string) => void = () => undefined;
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: (h: (id: string) => void) => { action = h; } };
  let observer: () => void = () => undefined;
  const flowUnderTest = createPrescriptionFlow({
    bridge, flow, send, doc: document, card, now,
    setTimer: () => undefined,
    observe: (cb) => { observer = cb; return () => undefined; },
    reload: () => undefined,
  });
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  return { prescription: flowUnderTest, flow, calls, sent, text, mutate: () => observer(), act: (id: string) => action(id) };
}

describe('prescriptionFlow', () => {
  it('fills date, class, species and disease, then opens the PetVet search', async () => {
    page();
    const { prescription, calls, flow } = await setup();

    await prescription.start();

    expect(calls.map((c) => c.op)).toEqual(['ready', 'setTreatmentDate', 'ensureTreatmentClass', 'selectPrescriptionAnimalType', 'addDisease', 'clickAllowed']);
    expect(calls[1].args).toEqual({ iso: '2026-10-09' });
    expect(calls[3].args).toEqual({ value: ANIMAL_TYPE.CAT });
    expect(calls[4].args).toEqual({ value: 'd1' });
    expect(calls[5].args).toEqual({ page: 'prescriptionReceipt', button: 'petVet' });
    expect((await flow.get())?.step).toBe('searching');
  });

  it('stops when the disease did not reach the diagnosed list', async () => {
    page();
    const { prescription, calls, flow, text } = await setup({}, 'armed', undefined, { addDisease: { added: true, listed: false } });

    await prescription.start();

    expect(calls.map((c) => c.op)).not.toContain('clickAllowed');
    expect((await flow.get())?.step).toBe('error');
    expect(text()).toContain('DISEASE_NOT_ADDED');
  });

  it('does nothing on the page when the prescription has no TARBIL diagnosis', async () => {
    page();
    const { prescription, calls, text } = await setup({ prescription: { ...base.prescription, tarbilDiseaseId: null, tarbilDiseaseName: null } });

    await prescription.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('teşhis');
  });

  it('leaves a vaccination alone on the prescription page', async () => {
    page();
    const { prescription, calls, text } = await setup({ documentType: 'VACCINATION', prescription: null });

    await prescription.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('Aşı Uygulama Belgesi Ekle');
  });

  it('opens Stok Ekle and the stock window for the first line once the animal is on the form', async () => {
    page();
    const { prescription, flow, calls, mutate } = await setup({}, 'transferred');
    await prescription.start();

    addAnimalRow(CHIP);
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('choosingProduct'));
    expect((await flow.get())?.stepData).toMatchObject({ lineIndex: 0 });
    expect(calls.filter((c) => c.op === 'clickAllowed').map((c) => c.args)).toEqual([
      { page: 'prescriptionReceipt', button: 'addStock' },
      { page: 'prescriptionReceipt', button: 'stockSearch' },
    ]);
  });

  it('writes Adet and Miktar when the selected row has the planned serial', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, calls, mutate, text } = await setup({}, 'choosingProduct', { lineIndex: 0 });
    await prescription.start();

    stockGrid({ edit: 'KV04Z81' });
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('productReady'));
    expect(calls.find((c) => c.op === 'setStockQuantities')?.args).toEqual({ quantity: 1 });
    expect(text()).toContain('Kaydet');
  });

  it('refuses a row with a different serial', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, calls, mutate, text } = await setup({}, 'choosingProduct', { lineIndex: 0 });
    await prescription.start();

    stockGrid({ edit: 'XX1' });
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('awaitingConfirm'));
    expect(calls.map((c) => c.op)).not.toContain('setStockQuantities');
    expect(text()).toContain('Yanlış ürün');
  });

  it('moves to the next line once the vet saved the row', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, calls, mutate } = await setup({}, 'productReady', { lineIndex: 0 });
    await prescription.start();

    stockGrid({ saved: [['Advantage % 10', 'KV04Z81', '1']] });
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.stepData).toMatchObject({ lineIndex: 1 }));
    expect((await flow.get())?.step).toBe('choosingProduct');
    expect(calls.filter((c) => c.op === 'clickAllowed')).toHaveLength(2);
  });

  it('asks for Reçeteyi Onayla once every line is saved', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, mutate, text } = await setup({}, 'productReady', { lineIndex: 1 });
    await prescription.start();

    stockGrid({ saved: [['Advantage % 10', 'KV04Z81', '1'], ['SYNULOX', 'S1', '2']] });
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('awaitingConfirm'));
    expect(text()).toContain('Reçeteyi Onayla');
  });

  it('reports the saved lines when TARBIL confirms the prescription', async () => {
    page();
    addAnimalRow(CHIP);
    stockGrid({ saved: [['Advantage % 10', 'KV04Z81', '1'], ['SYNULOX', 'S1', '2']] });
    const { prescription, flow, sent, mutate } = await setup({}, 'awaitingConfirm');
    await prescription.start();

    document.getElementById(`${P}btnInsert_input`)!.click();
    await vi.waitFor(async () => expect((await flow.get())?.insertClickedAt).toBeDefined());
    showSuccess();
    mutate();

    await vi.waitFor(() => expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(true));
    expect(sent.find((r) => r.type === 'MARK_SUBMITTED')).toEqual({
      type: 'MARK_SUBMITTED', id: 's1', method: 'AUTO', tarbilReference: null,
      lines: [
        { productName: 'Advantage % 10', serialNumber: 'KV04Z81', quantity: 1 },
        { productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 },
      ],
    });
    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('done'));
  });

  it('ignores a success panel that was on the page before Reçeteyi Onayla', async () => {
    page();
    addAnimalRow(CHIP);
    showSuccess();
    const { prescription, sent, mutate } = await setup({}, 'awaitingConfirm');
    await prescription.start();

    document.getElementById(`${P}btnInsert_input`)!.click();
    mutate();
    await new Promise((r) => setTimeout(r, 20));

    expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(false);
  });

  it('refuses a form that holds another animal', async () => {
    page();
    addAnimalRow('900000000000999');
    const { prescription, calls, flow, text } = await setup();

    await prescription.start();

    expect(calls).toEqual([]);
    expect((await flow.get())?.message).toBe('DIRTY_FORM');
    expect(text()).toContain('yarım kalmış');
  });

  it('resumes on a form with its own animal and a saved line', async () => {
    page();
    addAnimalRow(CHIP);
    stockGrid({ saved: [['Advantage % 10', 'KV04Z81', '1']] });
    const { prescription, calls, flow } = await setup();

    await prescription.start();

    await vi.waitFor(async () => expect((await flow.get())?.stepData).toMatchObject({ lineIndex: 1 }));
    expect(calls.map((c) => c.op)).not.toContain('setTreatmentDate');
    expect(calls.map((c) => c.op)).toContain('addDisease');
  });

  it('re-arms as a prescription when the vet presses Formu doldur', async () => {
    page();
    const { prescription, flow, act } = await setup({}, null);
    await prescription.start();

    act('fill');

    await vi.waitFor(async () => expect((await flow.get())?.documentType).toBe('PRESCRIPTION'));
  });
});
```

- [ ] **Step 2: Çalıştır, kırmızı gör**

Run: `cd extension && npx vitest run src/tarbil/pages/prescriptionReceipt.test.ts`
Expected: FAIL — modül yok.

- [ ] **Step 3: Görünümler — `core/prescriptionViews.ts`**

```ts
import type { PlannedLine, Submission } from '../../shared/types';
import type { CardAction, CardLine, CardView, Tone } from './card';

function header(s: Submission): CardLine[] {
  const p = s.prescription;
  return [
    { text: s.patientName, tone: 'strong' },
    { text: `Reçete · ${p?.tarbilDiseaseName ?? 'teşhis yok'} · ${p?.lines.length ?? 0} ilaç`, tone: 'muted' },
  ];
}

export const lineText = (l: PlannedLine) => `${l.tarbilProductName ?? l.itemName} · Seri ${l.lotNumber ?? '—'} · ${l.quantity} adet`;

const FALLBACK: CardAction[] = [
  { id: 'manual', label: 'Kaydedildi olarak işaretle' },
  { id: 'dismiss', label: 'Bildirilmeyecek' },
];

const view = (s: Submission, text: string, tone: Tone | undefined, actions: CardAction[] = FALLBACK): CardView => ({
  lines: [...header(s), { text, tone }],
  actions,
});

/** "Stoktan Duserek Ilac Recetesi Ekle" sayfasindaki kart metinleri (spec 2026-10-09 S7). */
export const prescriptionViews = {
  submitted: (s: Submission): CardView =>
    view(s, `✓ Bu reçete ${new Date(s.submittedAt!).toLocaleDateString('tr-TR')} tarihinde TARBİL'e kaydedilmiş. Tekrar girmeyin.`, 'ok', []),
  login: (s: Submission): CardView =>
    view(s, 'Reçete formu bulunamadı. TARBİL oturumunuz kapanmış olabilir: e-Devlet ile giriş yapın; girişten sonra reçete sayfasına kendimiz geçeceğiz.', 'warn', []),
  idle: (s: Submission): CardView =>
    view(s, 'Tedavi tarihini, teşhisi ve türü girip hayvanı çip (yoksa pasaport) numarasıyla bulacağız; ilaçları stoktan satır satır ekleyeceğiz.', 'muted',
      [{ id: 'fill', label: 'Formu doldur' }, ...FALLBACK]),
  wrongPage: (s: Submission): CardView =>
    ({ lines: [{ text: s.patientName, tone: 'strong' }, { text: 'Bu bir aşı aktarımı; "Aşı Uygulama Belgesi Ekle" sayfasında doldurulur.', tone: 'muted' }], actions: [] }),
  noChip: (s: Submission): CardView =>
    view(s, "Çip ve pasaport numarası yok. Vetly'de hastaya birini girin; hayvan TARBİL'de kayıtlı değilse önce kimliklendirme gerekir.", 'warn'),
  unsupportedSpecies: (s: Submission): CardView =>
    view(s, `Otomatik doldurma yalnız kedi ve köpek için (${s.speciesName ?? 'tür bilinmiyor'}). Formu kendiniz doldurun.`, 'warn'),
  noDisease: (s: Submission): CardView =>
    view(s, "Reçetede TARBİL teşhisi yok. Vetly'de teşhis seçin ya da formu kendiniz doldurun.", 'warn'),
  nothingToDeduct: (s: Submission): CardView =>
    view(s, 'TARBİL düşümü bekleyen ilaç kalmadı.', 'muted'),
  progress: (s: Submission, text: string): CardView => view(s, text, 'muted'),
  searching: (s: Submission): CardView =>
    view(s, 'PetVet arama penceresinde hayvan aranıyor… Pencere açılmadıysa aşağıdaki butona basın.', 'muted', [
      { id: 'petvet', label: 'Arama penceresini aç' },
      ...FALLBACK,
    ]),
  popupBlocked: (s: Submission): CardView =>
    view(s, 'Hayvan arama penceresi açılmadı. TARBİL için açılır pencerelere izin verin ya da aşağıdaki butona basın.', 'warn', [
      { id: 'petvet', label: 'Arama penceresini aç' },
      ...FALLBACK,
    ]),
  needsVet: (s: Submission, message: string): CardView => view(s, `${message} Hayvan forma eklenince devam edeceğiz.`, 'warn'),
  wrongAnimal: (s: Submission): CardView =>
    view(s, `Forma eklenen hayvan Vetly'deki hastayla (${s.microchipNumber ? `çip ${s.microchipNumber}` : `pasaport ${s.passportNumber ?? '—'}`}) aynı değil. Yanlış satırı silip doğru hayvanı ekleyin.`, 'warn'),
  dirtyForm: (s: Submission): CardView =>
    view(s, "Bu formda başka bir hastaya ya da başka ilaçlara ait yarım kalmış bir reçete var. Onu tamamlayıp Reçeteyi Onayla'ya basın ya da formu sıfırlayın (TARBİL'de hiçbir şey kaydedilmez).", 'warn',
      [{ id: 'resetForm', label: 'Formu sıfırla' }, ...FALLBACK]),
  choosingLine: (s: Submission, line: PlannedLine, index: number): CardView =>
    view(s, `✓ Hayvan formda. İlaç ${index + 1}/${s.prescription?.lines.length ?? 0}: ${lineText(line)} stoktan seçiliyor…`, 'muted'),
  stockWindowBlocked: (s: Submission): CardView =>
    view(s, 'Stok penceresi açılmadı. TARBİL için açılır pencerelere izin verin ya da aşağıdaki butona basın.', 'warn', [
      { id: 'stockWindow', label: 'Stok penceresini aç' },
      ...FALLBACK,
    ]),
  lineReady: (s: Submission, line: PlannedLine): CardView =>
    view(s, `✓ Satır hazır: ${lineText(line)} (Adet ve Miktar ${line.quantity}). Satırdaki Kaydet'e siz basın; sonraki ilaca geçeceğiz.`, 'ok'),
  wrongLine: (s: Submission, line: PlannedLine | undefined): CardView =>
    view(s, `Yanlış ürün seçildi: satırın serisi ${line?.lotNumber ?? '—'} olmalı. Satırı İptal edip doğru seriyi seçin.`, 'warn'),
  lineNeedsVet: (s: Submission, message: string): CardView =>
    view(s, `${message} İlacı "Stok Ekle"den kendiniz ekleyin; tüm ilaçlar eklenince Reçeteyi Onayla'ya basın.`, 'warn'),
  allLinesReady: (s: Submission): CardView =>
    view(s, "✓ Tüm ilaçlar eklendi. Teşhis, hayvan ve satırları kontrol edip Reçeteyi Onayla'ya basın. Kaydı yakalayıp Vetly'ye işleyeceğiz.", 'ok'),
  done: (s: Submission, queued: boolean): CardView =>
    view(s, queued
      ? "✓ TARBİL'e kaydedildi. Vetly'ye şu an ulaşılamıyor; bağlantı gelince otomatik işlenecek."
      : "✓ TARBİL'e kaydedildi; Vetly stoğu düşüldü.", 'ok', []),
  failed: (s: Submission, code: string): CardView =>
    view(s, `Otomatik doldurma durdu (${code}). Kalan adımları TARBİL'de kendiniz tamamlayabilirsiniz.`, 'warn', [
      { id: 'fill', label: 'Yeniden dene' },
      ...FALLBACK,
    ]),
  elsewhere: (s: Submission): CardView =>
    ({ lines: [...header(s), { text: 'Bu reçeteyi "Stoktan Düşerek İlaç Reçetesi Ekle" sayfasında dolduracağız.', tone: 'muted' }],
      actions: [{ id: 'open', label: 'Reçete sayfasını aç' }] }),
};
```

- [ ] **Step 4: Akış — `pages/prescriptionReceipt.ts`**

```ts
import type { FlowState } from '../../shared/flowStore';
import type { ConfirmedLine, Submission } from '../../shared/types';
import { prescriptionViews as views } from '../core/prescriptionViews';
import { hasFreshSuccess, isConfirmClick, markStaleSuccess } from '../core/success';
import { PRESCRIPTION_RECEIPT, bySuffix } from '../selectors';
import { countReceiptAnimals, normalizeChip, normalizePassport, readReceiptChips } from '../steps/animalRows';
import { normalizeSerial } from '../steps/productRows';
import { resolveAnimalType } from '../steps/species';
import { confirmedLines, nextLine, readSavedStockLines, readStockEditRow } from '../steps/stockLines';
import { POPUP_WAIT_MS, SUCCESS_WINDOW_MS, type ReceiptDeps } from './vaccineReceipt';

const WAITING_FOR_ANIMAL = ['searching', 'transferred', 'needsVet'];
/** Receteyi Onayla + basari yakalamasinin gecerli oldugu adimlar. */
const CONFIRM_STEPS = ['awaitingConfirm', 'choosingProduct', 'productReady'];
const code = (e: unknown) => (e as { code?: string })?.code ?? 'UNKNOWN';

/**
 * "Stoktan Duserek Ilac Recetesi Ekle" sayfasi (spec 2026-10-09 S7, S11): tarih -> sinif -> tur -> teshis -> PetVet;
 * hayvan dogrulaninca ilaclar satir satir: "Stok Ekle" + stok penceresi (seri ile secim) + Adet/Miktar. Satir Kaydet'e ve
 * Receteyi Onayla'ya hekim basar; basari yakalaninca kaydedilen satirlar Vetly'ye gider. Resmi butonlara ASLA basmaz.
 */
export function createPrescriptionFlow(d: ReceiptDeps) {
  let sub: Submission | null = null;
  let stopObserving: (() => void) | null = null;
  let finishing = false;
  let starting: Promise<void> | null = null;
  let evaluating: Promise<void> = Promise.resolve();
  const staleSuccess = new WeakSet<Element>();

  async function current(): Promise<FlowState | null> {
    const st = await d.flow.get();
    return st && sub && st.submissionId === sub.id ? st : null;
  }

  function start(): Promise<void> {
    starting ??= run().finally(() => {
      starting = null;
    });
    return starting;
  }

  async function run(): Promise<void> {
    stopObserving?.();
    stopObserving = null;
    finishing = false;
    const res = await d.send<Submission | null>({ type: 'GET_ACTIVE' });
    if (!res.ok || !res.data || res.data.status === 'DISMISSED') {
      sub = null;
      d.card.hide();
      return;
    }
    sub = res.data;
    if (sub.documentType !== 'PRESCRIPTION') {
      d.card.show(views.wrongPage(sub));
      return;
    }
    if (sub.status === 'SUBMITTED') {
      d.card.show(views.submitted(sub));
      return;
    }
    if (!d.doc.querySelector(bySuffix(PRESCRIPTION_RECEIPT.date))) {
      d.card.show(views.login(sub));
      return;
    }
    const st = await current();
    if (st?.step === 'armed') return fill();
    if (st && (WAITING_FOR_ANIMAL.includes(st.step) || CONFIRM_STEPS.includes(st.step))) {
      d.card.show(views.progress(sub, 'Kaldığı yerden devam ediliyor…'));
      watch();
      return;
    }
    d.card.show(views.idle(sub));
  }

  async function fill(): Promise<void> {
    const s = sub!;
    const p = s.prescription;
    const chip = normalizeChip(s.microchipNumber) || normalizePassport(s.passportNumber);
    const animalType = resolveAnimalType(s);
    const stop = async (message: string, v: ReturnType<typeof views.noChip>) => {
      await d.flow.update(s.id, { step: 'error', message });
      d.card.show(v);
    };
    if (!p || p.lines.length === 0) return stop('NO_LINES', views.nothingToDeduct(s));
    if (!p.tarbilDiseaseId) return stop('NO_DISEASE', views.noDisease(s));
    if (!chip) return stop('NO_CHIP', views.noChip(s));
    if (!animalType) return stop('UNSUPPORTED_SPECIES', views.unsupportedSpecies(s));
    const form = inspectForm(s, await current());
    if (form === 'dirty') return stop('DIRTY_FORM', views.dirtyForm(s));
    if (form === 'resume') {
      try {
        await ensureDisease(p.tarbilDiseaseId);
        await continueLines(s);
      } catch (e) {
        await failed(e);
        return;
      }
      watch();
      return;
    }
    await d.flow.update(s.id, { step: 'filling' });
    try {
      d.card.show(views.progress(s, 'Tedavi tarihi, reçete sınıfı, tür ve teşhis giriliyor…'));
      await d.bridge.call('ready');
      await d.bridge.call('setTreatmentDate', { iso: p.treatmentStartDate });
      await d.bridge.call('ensureTreatmentClass');
      await d.bridge.call('selectPrescriptionAnimalType', { value: animalType });
      await ensureDisease(p.tarbilDiseaseId);
      await openSearch();
    } catch (e) {
      await failed(e);
      return;
    }
    watch();
  }

  async function ensureDisease(value: string): Promise<void> {
    const r = await d.bridge.call<{ listed: boolean }>('addDisease', { value });
    if (!r?.listed) throw Object.assign(new Error('Teşhis eklenemedi'), { code: 'DISEASE_NOT_ADDED' });
  }

  /** empty: bos form; resume: yalniz bu hastanin hayvani ve bu recetenin ilaclari; dirty: baska hayvan/ilac var. */
  function inspectForm(s: Submission, st: FlowState | null): 'empty' | 'resume' | 'dirty' {
    const animals = countReceiptAnimals(d.doc);
    const saved = readSavedStockLines(d.doc);
    const edit = readStockEditRow(d.doc);
    if (animals === 0 && saved.length === 0 && !edit?.serial) return 'empty';
    const chips = readReceiptChips(d.doc);
    const expected = normalizeChip(s.microchipNumber) || normalizeChip(st?.stepData?.matchedChip as string | undefined);
    const ownAnimal = animals === 1 && expected.length > 0 && chips.length === 1 && chips[0] === expected;
    const planned = new Set((s.prescription?.lines ?? []).map((l) => normalizeSerial(l.lotNumber)));
    const ownLines = saved.every((l) => planned.has(l.serial)) && (!edit?.serial || planned.has(edit.serial));
    return ownAnimal && ownLines ? 'resume' : 'dirty';
  }

  async function openSearch(): Promise<void> {
    const s = sub!;
    if (!(await current())) await d.flow.arm(s.id, 'PRESCRIPTION');
    await d.flow.update(s.id, { step: 'searching' });
    d.card.show(views.progress(s, 'PetVet arama penceresinde hayvan aranıyor…'));
    await d.bridge.call('clickAllowed', { page: 'prescriptionReceipt', button: 'petVet' });
    d.card.show(views.searching(s));
    d.setTimer(() => {
      void (async () => {
        const st = await current();
        if (st?.step === 'searching' && d.now() - st.updatedAt >= POPUP_WAIT_MS) d.card.show(views.popupBlocked(s));
      })();
    }, POPUP_WAIT_MS);
  }

  /** Hayvan dogrulandi ya da bir satir kaydedildi: siradaki kaydedilmemis ilac icin "Stok Ekle" + stok penceresi. */
  async function continueLines(s: Submission): Promise<void> {
    const lines = s.prescription?.lines ?? [];
    const i = nextLine(lines, readSavedStockLines(d.doc));
    if (i < 0) {
      await d.flow.update(s.id, { step: 'awaitingConfirm', message: undefined });
      d.card.show(views.allLinesReady(s));
      return;
    }
    const line = lines[i];
    const st = await current();
    await d.flow.update(s.id, { step: 'choosingProduct', message: undefined, stepData: { ...st?.stepData, lineIndex: i } });
    const edit = readStockEditRow(d.doc);
    if (edit?.serial) {
      await checkEditRow(s);
      return;
    }
    if (!line.lotNumber) {
      const message = `${line.tarbilProductName ?? line.itemName} için Vetly'de seri numarası yok.`;
      await d.flow.update(s.id, { step: 'awaitingConfirm', message });
      d.card.show(views.lineNeedsVet(s, message));
      return;
    }
    d.card.show(views.choosingLine(s, line, i));
    try {
      if (!edit) await d.bridge.call('clickAllowed', { page: 'prescriptionReceipt', button: 'addStock' });
      await d.bridge.call('clickAllowed', { page: 'prescriptionReceipt', button: 'stockSearch' });
    } catch (e) {
      const message = `Stok satırı açılamadı (${code(e)}).`;
      await d.flow.update(s.id, { step: 'awaitingConfirm', message });
      d.card.show(views.lineNeedsVet(s, message));
      return;
    }
    d.setTimer(() => {
      void (async () => {
        const later = await current();
        if (later?.step === 'choosingProduct' && d.now() - later.updatedAt >= POPUP_WAIT_MS && !readStockEditRow(d.doc)?.serial) {
          d.card.show(views.stockWindowBlocked(s));
        }
      })();
    }, POPUP_WAIT_MS);
  }

  /** Stok penceresinden secilen ilac ekleme satirina geldi mi: seri planlanan seriyse Adet ve Miktar yazilir. */
  async function checkEditRow(s: Submission): Promise<void> {
    const st = await current();
    const line = s.prescription?.lines[Number(st?.stepData?.lineIndex)];
    const edit = readStockEditRow(d.doc);
    if (!line || !edit?.serial) return;
    if (edit.serial !== normalizeSerial(line.lotNumber)) {
      await d.flow.update(s.id, { step: 'awaitingConfirm' });
      d.card.show(views.wrongLine(s, line));
      return;
    }
    try {
      await d.bridge.call('setStockQuantities', { quantity: line.quantity });
    } catch (e) {
      const message = `Adet yazılamadı (${code(e)}).`;
      await d.flow.update(s.id, { step: 'awaitingConfirm', message });
      d.card.show(views.lineNeedsVet(s, message));
      return;
    }
    await d.flow.update(s.id, { step: 'productReady' });
    d.card.show(views.lineReady(s, line));
  }

  async function failed(e: unknown): Promise<void> {
    const s = sub!;
    await d.flow.update(s.id, { step: 'error', message: code(e) });
    d.card.show(views.failed(s, code(e)));
  }

  function watch(): void {
    if (stopObserving) return;
    stopObserving = d.observe(() => void scheduleEvaluate());
    void scheduleEvaluate();
  }

  function scheduleEvaluate(): Promise<void> {
    evaluating = evaluating.then(evaluate, evaluate);
    return evaluating;
  }

  async function evaluate(): Promise<void> {
    const s = sub;
    if (!s || finishing) return;
    const st = await current();
    if (!st) return;
    if (CONFIRM_STEPS.includes(st.step)) {
      const clickedRecently = st.insertClickedAt !== undefined && d.now() - st.insertClickedAt <= SUCCESS_WINDOW_MS;
      if (clickedRecently && hasFreshSuccess(d.doc, PRESCRIPTION_RECEIPT.successPanel, staleSuccess)) {
        await confirm(s, st);
        return;
      }
      if (st.step === 'choosingProduct') {
        await checkEditRow(s);
      } else if (st.step === 'productReady') {
        const line = s.prescription?.lines[Number(st.stepData?.lineIndex)];
        const saved = readSavedStockLines(d.doc).map((l) => l.serial);
        if (line && saved.includes(normalizeSerial(line.lotNumber))) await continueLines(s);
      }
      return;
    }
    if (!WAITING_FOR_ANIMAL.includes(st.step)) return;
    const chips = readReceiptChips(d.doc);
    const expected = normalizeChip(s.microchipNumber) || normalizeChip(st.stepData?.matchedChip as string | undefined);
    if (expected && chips.includes(expected)) {
      await continueLines(s);
    } else if (expected && chips.length > 0) {
      d.card.show(views.wrongAnimal(s));
    }
  }

  async function confirm(s: Submission, st: FlowState): Promise<void> {
    finishing = true;
    const lines = (st.stepData?.confirmedLines as ConfirmedLine[] | undefined)
      ?? confirmedLines(readSavedStockLines(d.doc), s.prescription?.lines ?? []);
    const r = await d.send<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'AUTO', tarbilReference: null, lines });
    if (!r.ok) {
      finishing = false;
      d.card.show(views.failed(s, r.code));
      return;
    }
    await d.flow.update(s.id, { step: 'done' });
    stopObserving?.();
    stopObserving = null;
    d.card.show(views.done(s, (r.data as { queued?: boolean } | undefined)?.queued === true));
  }

  // Hekimin Receteyi Onayla tiklamasi: kaydedilmis satirlar tiklama aninda okunur (onaydan sonra form temizlenebilir).
  d.doc.addEventListener(
    'click',
    (e) => {
      if (!isConfirmClick(e.target, PRESCRIPTION_RECEIPT.insertButtons)) return;
      markStaleSuccess(d.doc, PRESCRIPTION_RECEIPT.successPanel, staleSuccess);
      const snapshot = confirmedLines(readSavedStockLines(d.doc), sub?.prescription?.lines ?? []);
      void (async () => {
        const st = await current();
        if (st && CONFIRM_STEPS.includes(st.step)) {
          await d.flow.update(st.submissionId, { insertClickedAt: d.now(), stepData: { ...st.stepData, confirmedLines: snapshot } });
        }
      })();
    },
    true,
  );

  d.card.onAction((id) => {
    const s = sub;
    if (!s) return;
    void (async () => {
      switch (id) {
        case 'fill':
          await d.flow.arm(s.id, 'PRESCRIPTION');
          await start();
          return;
        case 'petvet':
          try {
            await openSearch();
            watch();
          } catch (e) {
            await failed(e);
          }
          return;
        case 'resetForm':
          await d.flow.arm(s.id, 'PRESCRIPTION');
          d.reload();
          return;
        case 'stockWindow':
          try {
            await continueLines(s);
          } catch (e) {
            await failed(e);
          }
          return;
        case 'manual': {
          const r = await d.send<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'MANUAL', tarbilReference: null });
          if (!r.ok) return d.card.show(views.failed(s, r.code));
          await d.flow.update(s.id, { step: 'done' });
          d.card.show(views.done(s, (r.data as { queued?: boolean } | undefined)?.queued === true));
          return;
        }
        case 'dismiss': {
          const r = await d.send({ type: 'DISMISS', id: s.id, reason: 'Bildirim gerekmiyor' });
          if (r.ok) d.card.hide();
          else d.card.show(views.failed(s, r.code));
          return;
        }
      }
    })();
  });

  return {
    start,
    /** Arama/stok penceresi akis durumunu degistirdiginde (content.ts storage dinleyicisinden). */
    flowChanged(state: FlowState | null): void {
      const s = sub;
      if (!s || !state || state.submissionId !== s.id) return;
      if (state.step === 'armed') {
        void start();
        return;
      }
      if (state.step === 'awaitingConfirm' && state.message) {
        d.card.show(views.lineNeedsVet(s, state.message));
        return;
      }
      if (state.step === 'needsVet') d.card.show(views.needsVet(s, state.message ?? ''));
      else if (state.step === 'transferred') d.card.show(views.progress(s, 'Hayvan forma aktarılıyor…'));
      if (WAITING_FOR_ANIMAL.includes(state.step)) watch();
    },
  };
}
```

`PageBridge.call`'ın jenerik imzası yoksa (`core/bridge.ts`'e bak) `ensureDisease`'te `(await d.bridge.call('addDisease', { value })) as { listed?: boolean } | undefined` kullan.

- [ ] **Step 5: Çalıştır, yeşil gör; tüm eklenti testleri ve tip kontrolü**

Run: `cd extension && npx vitest run && npx tsc --noEmit`
Expected: PASS; tsc hatasız.

- [ ] **Step 6: Commit**

```bash
git add extension/src/tarbil/core/prescriptionViews.ts extension/src/tarbil/pages/prescriptionReceipt.ts extension/src/tarbil/pages/prescriptionReceipt.test.ts
git commit -m "feat(eklenti): stoktan düşen reçete formunu satır satır doldurma ve onay yakalama"
```

---

### Task 8: Yönlendirme, aşı sayfası koruması ve yan panel

**Files:**
- Modify: `extension/src/tarbil/pages/vaccineReceipt.ts` (reçeteyi doldurmaz)
- Modify: `extension/src/tarbil/core/views.ts` (`prescriptionElsewhere`)
- Modify: `extension/src/tarbil/content.ts`
- Modify: `extension/src/sidepanel/SubmissionCard.tsx`
- Test: `extension/src/tarbil/pages/vaccineReceipt.test.ts`, `extension/src/sidepanel/SubmissionCard.test.tsx`

**Interfaces:**
- Consumes: Task 2 `pageKind`/`pageUrlFor`, Task 5 `SET_ACTIVE.documentType`, Task 6 `runMedicineStockPopupFlow`, Task 7 `createPrescriptionFlow`, `prescriptionViews.elsewhere`.
- Produces: `content.ts` yönlendirmesi: `prescriptionReceipt` → reçete akışı, `medicineStockPopup` → ilaç stok penceresi akışı; e-Devlet dönüşü ve "başka sayfa" kartı belge türünün sayfasına gider. Aşı AUTO onayı, formda kaydedilmiş ürün satırı varsa `lines` (seri, adet 1) taşır (spec §7); yoksa gövde eskisi gibi.

- [ ] **Step 1: Testleri yaz**

`vaccineReceipt.test.ts` `describe('receiptFlow')` içine:

```ts
  it('leaves a prescription alone on the vaccine page', async () => {
    page();
    const { receipt, calls, text } = await setup({ documentType: 'PRESCRIPTION' });

    await receipt.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('Stoktan Düşerek İlaç Reçetesi Ekle');
  });

  it('reports the saved vaccine serial with the confirmation', async () => {
    page();
    productGrid({ saved: ['665932'] });
    const { receipt, sent, flow, mutate } = await setup({ lotNumber: '665932', tarbilProductName: 'Biocan R' }, 'productReady');
    await receipt.start();

    (document.getElementById(`${P}btnInsert_input`) as HTMLInputElement).click();
    await vi.waitFor(async () => expect((await flow.get())?.insertClickedAt).toBeDefined());
    showSuccess();
    mutate();

    await vi.waitFor(() =>
      expect(sent).toContainEqual({
        type: 'MARK_SUBMITTED', id: 's1', method: 'AUTO', tarbilReference: null,
        lines: [{ productName: 'Biocan R', serialNumber: '665932', quantity: 1 }],
      }),
    );
  });
```

`SubmissionCard.test.tsx` — ilk testi şu hâle getir (adı ve beklentiler değişir):

```ts
  it('shows a stock prescription with its lines and lets the extension fill it', () => {
    const { text, buttons } = render({
      ...base, documentType: 'PRESCRIPTION', vaccineName: 'Reçete', lotNumber: null, administeredDate: null, vaccineKey: '',
      prescription: {
        sourceKind: 'ENCOUNTER', tarbilDiseaseId: 'd1', tarbilDiseaseName: 'Pnömoni', treatmentStartDate: '2026-10-09',
        lines: [{ itemName: 'Synulox', tarbilProductName: 'SYNULOX', lotNumber: 'S1', quantity: 2 }],
      },
    });

    expect(text).toContain('Reçete — stoktan düşüm');
    expect(text).toContain('Pnömoni');
    expect(text).toContain('SYNULOX · Seri S1 · 2 adet');
    expect(text).not.toContain('yakında');
    expect(buttons).toContain("TARBİL'de doldur");
    expect(buttons).toContain('Gönderildi olarak işaretle');
  });
```

- [ ] **Step 2: Çalıştır, kırmızı gör**

Run: `cd extension && npx vitest run src/tarbil/pages/vaccineReceipt.test.ts src/sidepanel`
Expected: FAIL — aşı akışı reçeteyi doldurmaya çalışır (`calls` boş değil); aşı onayında `lines` yok; yan panelde düğme yok.

- [ ] **Step 3: Aşı akışı koruması ve onay satırları**

`vaccineReceipt.ts` tıklama dinleyicisinde seriler tıklama anında okunur (onaydan sonra form temizlenebilir):

```ts
      markStaleSuccess(d.doc, RECEIPT.successPanel, staleSuccess);
      const savedSerials = readSavedProductSerials(d.doc);
      void (async () => {
        const st = await current();
        if (st && CONFIRM_STEPS.includes(st.step)) {
          await d.flow.update(st.submissionId, { insertClickedAt: d.now(), stepData: { ...st.stepData, savedSerials } });
        }
      })();
```

`evaluate()` içindeki `await confirm(s);` → `await confirm(s, st);`; `confirm`:

```ts
  async function confirm(s: Submission, st: FlowState): Promise<void> {
    finishing = true;
    // Formda kaydedilmis urun satirlari (asi: satir basina 1 doz); yoksa backend planlanan adedi duser.
    const serials = (st.stepData?.savedSerials as string[] | undefined) ?? [];
    const lines = serials.map((serialNumber) => ({ productName: s.tarbilProductName ?? s.vaccineName, serialNumber, quantity: 1 }));
    const r = await d.send<{ queued?: boolean }>({
      type: 'MARK_SUBMITTED', id: s.id, method: 'AUTO', tarbilReference: null, ...(lines.length > 0 ? { lines } : {}),
    });
```

(Gövdenin geri kalanı aynı.)

`views.ts`'e (`elsewhere` altına):

```ts
  prescriptionElsewhere: (s: Submission): CardView => ({
    lines: [
      { text: s.patientName, tone: 'strong' },
      { text: 'Bu bir reçete aktarımı; "Stoktan Düşerek İlaç Reçetesi Ekle" sayfasında doldurulur.', tone: 'muted' },
    ],
    actions: [],
  }),
```

`vaccineReceipt.ts` `run()` içinde `sub = res.data;` satırının hemen altına:

```ts
    if (sub.documentType === 'PRESCRIPTION') {
      d.card.show(views.prescriptionElsewhere(sub));
      return;
    }
```

- [ ] **Step 4: Yan panel**

`SubmissionCard.tsx`:

```tsx
  async function setActive() {
    await sendToBackground({ type: 'SET_ACTIVE', id: s.id, documentType: s.documentType ?? 'VACCINATION' });
    setNote(
      isPrescription
        ? 'TARBİL reçete sayfası açılıyor. Oturum kapalıysa e-Devlet ile giriş yapın; giriş sonrası form kendiliğinden doldurulur.'
        : 'TARBİL aşı sayfası açılıyor. Oturum kapalıysa e-Devlet ile giriş yapın; giriş sonrası form kendiliğinden doldurulur.',
    );
  }
```

(`const isPrescription = ...` satırını `setActive`'ten önceye taşı.) Reçete bölümündeki "Otomatik doldurma yakında — …" notunu şununla değiştir:

```tsx
          <div className="muted">
            Formu ve ilaç satırlarını biz doldururuz; satırdaki Kaydet'e ve Reçeteyi Onayla'ya siz basarsınız.
          </div>
```

Düğmeyi koşulsuz göster:

```tsx
        <button onClick={setActive} disabled={busy}>TARBİL'de doldur</button>
```

- [ ] **Step 5: İçerik betiği yönlendirmesi (`content.ts`)**

Import'lar:

```ts
import { runMedicineStockPopupFlow } from './pages/medicineStockPopup';
import { createPrescriptionFlow } from './pages/prescriptionReceipt';
import { prescriptionViews } from './core/prescriptionViews';
import { pageKind, pageUrlFor } from './selectors';
```

(`VACCINE_PAGE_URL` import'u artık kullanılmıyorsa kaldır.)

`showElsewhere`:

```ts
async function showElsewhere(): Promise<void> {
  const res = await send<Submission | null>({ type: 'GET_ACTIVE' });
  if (!res.ok || !res.data || res.data.status !== 'PENDING') return;
  const s = res.data;
  const documentType = s.documentType ?? 'VACCINATION';
  card.onAction((id) => {
    if (id === 'open') location.assign(pageUrlFor(documentType));
  });
  card.show(documentType === 'PRESCRIPTION' ? prescriptionViews.elsewhere(s) : views.elsewhere(s));
}
```

`home()` içinde `location.assign(VACCINE_PAGE_URL);` → `location.assign(pageUrlFor(state.documentType));`

`routeTarbilPage()` — `case 'receipt'` bloğundaki kurulumu bir yardımcıya çıkar ve iki sayfada kullan:

```ts
function receiptDeps() {
  return {
    bridge: createPageBridge(window),
    flow,
    send,
    doc: document,
    card,
    now: Date.now,
    setTimer: (fn: () => void, ms: number) => {
      setTimeout(fn, ms);
    },
    observe: (cb: () => void) => {
      const mo = new MutationObserver(cb);
      mo.observe(document.body, { childList: true, subtree: true });
      return () => mo.disconnect();
    },
    reload: () => location.reload(),
  };
}

function runReceiptPage(page: { start(): Promise<void>; flowChanged(state: FlowState | null): void }): void {
  void page.start();
  chrome.storage.session.onChanged.addListener((changes) => {
    if ('activeSubmissionId' in changes) void page.start();
    else if (FLOW_KEY in changes) page.flowChanged((changes[FLOW_KEY].newValue as FlowState | undefined) ?? null);
  });
}

function popupDeps() {
  return {
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
  };
}
```

`switch`:

```ts
  case 'receipt':
    runReceiptPage(createReceiptFlow(receiptDeps()));
    break;
  case 'prescriptionReceipt':
    runReceiptPage(createPrescriptionFlow(receiptDeps()));
    break;
  case 'search':
    void runSearchFlow({ bridge: createPageBridge(window), flow, send, doc: document, card, now: Date.now });
    break;
  case 'vaccineStockPopup':
    void runStockPopupFlow(popupDeps());
    break;
  case 'medicineStockPopup':
    void runMedicineStockPopupFlow(popupDeps());
    break;
```

(Diğer `case`'ler aynen kalır. Dosya başındaki sayfa listesi yorumuna `prescriptionReceipt` ve `medicineStockPopup` satırlarını ekle.)

- [ ] **Step 6: Çalıştır, yeşil gör; tüm eklenti testleri ve derleme**

Run: `cd extension && npx vitest run && npm run build`
Expected: PASS; derleme hatasız (content.ts birim testi yok — Ruling: yönlendirme derleme + canlı denemeyle doğrulanır).

- [ ] **Step 7: Commit**

```bash
git add extension/src/tarbil/pages/vaccineReceipt.ts extension/src/tarbil/pages/vaccineReceipt.test.ts extension/src/tarbil/core/views.ts extension/src/tarbil/content.ts extension/src/sidepanel
git commit -m "feat(eklenti): reçete sayfası ve ilaç stok penceresi yönlendirmesi; yan panelden reçete doldurma"
```

---

### Task 9: Belgeler ve tam doğrulama

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-tarbil-stok-dusum-senkronu-design.md` (§8 durum, §11 canlı doğrulama listesi)

- [ ] **Step 1: Spec güncelle**

§8 "Durum" paragrafının sonuna ekle:

```markdown
Durum (2026-10-10): **Plan B uygulandı** (`docs/superpowers/plans/2026-10-10-tarbil-recete-doldurma.md`) — eklenti reçete
formunu doldurur (tarih, sınıf, tür, teşhis, hayvan, ilaç satırları: seri ile stok penceresi, Adet = Miktar = Vetly adedi);
satır Kaydet ve Reçeteyi Onayla hekimde; başarı yakalanınca kaydedilen satırlar AUTO onayla Vetly'ye gider.
```

§11 sonuna ekle:

```markdown
- **İlk gerçek reçetede canlı doğrulanacaklar (Plan B):** (1) başarı paneli `_UCRECEIPTNotification_pnlNotifiSuccess`
  (yanlışsa AUTO hiç tetiklenmez; hekim MANUAL ile kapatır); (2) tür/teşhis postback'lerinin birbirini sıfırlayıp
  sıfırlamadığı (sıra: tarih → sınıf → tür → teşhis → PetVet); (3) satır Kaydet sonrası kaydedilmiş satırın yapısı
  (`Ürün`, `Seri Numarası`, `Kullanılacak Adet` sütunları); (4) teşhis listesinin (`lbxDiagnosedDisease`) öğe değerinin
  hastalık id'si mi ad mı olduğu; (5) satır Kaydet'in TARBİL stoğunu düşüp düşmediği (düşmüyorsa eklentiye verilebilir).
```

- [ ] **Step 2: Tam doğrulama**

Run: `cd backend && ./mvnw test`
Expected: tümü PASS (önceki 543 + 1).

Run: `cd extension && npx vitest run && npm run build`
Expected: tümü PASS; derleme hatasız.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-10-09-tarbil-stok-dusum-senkronu-design.md
git commit -m "docs(tarbil): Plan B durumu ve ilk reçetede canlı doğrulama listesi"
```
