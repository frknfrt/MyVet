# TARBİL Eklentisi Faz 2a — Aşı Formunu Doldurma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eklenti, TARBİL "Aşı Uygulama Belgesi Ekle" sayfasında uygulama tarihini ve türü girer, PetVet arama penceresinde çiple hayvanı bulup forma aktarır, aktarılan hayvanın çipini doğrular; hekim aşıyı stoktan ekleyip Onayla'ya basınca başarıyı yakalayıp Vetly'de "gönderildi (AUTO)" olarak işaretler.

**Architecture:** TARBİL sayfası Telerik bileşenleri kullandığı için iki içerik betiği olur: izole dünyada çalışan `content.js` (Vetly arka planıyla konuşur, kartı gösterir, DOM'u okur, akışı yönetir) ve sayfanın kendi dünyasında (`world: "MAIN"`) çalışan `page.js` (yalnız `$find(...)` / `PageRequestManager` ile tarih, tür, buton, onay kutusu işlemlerini yapan aptal bir yürütücü). İkisi `window.postMessage` köprüsüyle konuşur. Ana sayfa ile arama penceresi (ayrı `window.open` penceresi) arasındaki koordinasyon `chrome.storage.session`'daki `tarbilFlow` durumu üzerinden yürür.

**Tech Stack:** Chrome MV3, TypeScript 5.5, Vite 5 (IIFE içerik betikleri), Vitest 2 (+ jsdom ortamı DOM testleri için), React 18 (yalnız yan panel).

**Spec:** `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md` — özellikle Bölüm 3 (TARBİL ekranı), 12.2 (giriş ve dönüş), 12.4 (Transfer Et'e eklenti basar), 12.6 (Faz 2a kapsamı).

## Global Constraints

- Eklenti **Onayla'ya (`btnInsert` / `btnInsert2`) asla basmaz**; `Exit_Click` postback'ini asla tetiklemez. (Spec §2, §12.4, §3 son madde)
- Eklenti **Ürün Ekle / stok seçimine dokunmaz** (Faz 2a, spec §12.6).
- Sahip alanları (`txtPersonIdNo`, `txtAnimalOwnerName`, `txtAnimalOwnerAddress`, `txtHoldingNo`) **okunmaz, yazılmaz**. ViewState **okunmaz, saklanmaz, gönderilmez**. Arama tablosundan yalnız çip, durum ve satır onay kutusunun id'si okunur. (Spec §3 gizlilik uyarısı)
- Eklenti TARBİL'e kendisi HTTP isteği atmaz; yalnız sayfanın kendi bileşenlerini tetikler. (Spec §2)
- Otomatik satır seçimi yalnız **çipi birebir eşleşen tek satır** ve durumu `CANLI` ise yapılır; aksi hâlde karar hekime kalır. (Spec §3, §12.4)
- Otomatik doldurma yalnız kedi ve köpek için; Kedi = `245f5f71-2cfd-4a04-9072-640069e3268e`, Köpek = `3eea84e6-b2d8-494f-a01c-89e739bb004d`. (Spec §3)
- Aşı sayfası adresi: `https://hbsapp.tarbil.gov.tr/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx?type=1` (`type=2` rapel belgesidir, ona dokunulmaz). e-Devlet dönüşü: `https://hbsapp.tarbil.gov.tr/`. (Spec §3, §11 madde 7)
- Üretim derlemesinde konsola hasta/çip bilgisi yazılmaz (`console.log` eklenmez). (Spec §6)
- **Test için TARBİL'de kayıt oluşturulmaz.** Testlerdeki HTML'ler sentetiktir; gerçek kişi/hayvan verisi içermez (çip `900000000000001` gibi uydurma). (Spec §9)
- Kullanıcıya görünen tüm metinler Türkçe.
- Kart içeriği yalnız `textContent` ile yazılır (`innerHTML`'e veri girmez).

## Bilinen varsayımlar (gerçek TARBİL'de elle kabul testinde doğrulanacak)

- Başarı paneli id'si `..._UCVACCINENotification_pnlNotifiSuccess` (hata panelinin `pnlNotifiError` biçiminden çıkarıldı; spec §3). Yanlışsa yalnız `selectors.ts`'teki `RECEIPT.successPanel` değişir; elle işaretleme yedeği her zaman kartta.
- Arama penceresi bileşen id sonekleri `_UCVaccineKKBSAnimalSearch_txtChipNo`, `_btnSearch`, `_btnAddBulkAnimal` (spec §3). Yanlışsa yalnız `selectors.ts` değişir.
- `bntPetVet` tıklaması pencereyi açar. Chrome kullanıcı hareketi olmadan açılan pencereyi engelleyebilir; bu durumda kart 15 sn sonra "Arama penceresini aç" butonu (kullanıcı hareketi) ve site izni yönergesi gösterir.

## Review Focus

1. **Anne çip numarası tuzağı:** arama sonucunda çip yalnız "Anne Çip No" sütununda geçiyorsa satır seçilmemeli → Task 2 testi `does not match on the mother chip column`.
2. **Yanlış zamanda başarı:** Onayla dışındaki bir postback'ten (ör. aktarım) sonra görünen başarı paneli aşıyı "gönderildi" yapmamalı → Task 6 testi `does not mark submitted when success appears without an Onayla click`.
3. **Engellenen açılır pencere:** arama penceresi hiç açılmazsa akış sessizce takılmamalı, kart hekime buton sunmalı → Task 6 testi `offers to reopen the search window when the popup never picks up`.
4. **Bayat/döngüsel yönlendirme:** eski ya da başka adımdaki bir akış durumu TARBİL ana sayfasını aşı sayfasına zorla yönlendirmemeli; yönlendirme döngüye girmemeli → Task 4 testleri `shouldRedirectHome`.
5. **Kedi/köpek dışı tür veya çipsiz hayvan:** sayfada hiçbir işlem yapılmamalı, kart nedenini söylemeli → Task 6 testleri `does nothing on the page when the patient has no chip` ve `does nothing on the page for unsupported species`.

---

## Dosya Yapısı

```
extension/
├── package.json                     (Değiştir) jsdom, page derlemesi
├── tsconfig.json                    (Değiştir) vite.page.config.ts
├── vite.page.config.ts              (Yeni) MAIN dünya betiği → dist/page.js
├── public/manifest.json             (Değiştir) page.js içerik betiği, world MAIN
├── README.md                        (Değiştir) açılır pencere izni, akış
└── src/
    ├── shared/flowStore.ts          (Yeni) tarbilFlow durumu (arka plan + içerik betikleri)
    ├── background/router.ts         (Değiştir) SET_ACTIVE / SELECT_SUBMISSION akışı "armed" yapar
    ├── sidepanel/SubmissionCard.tsx (Değiştir) buton metni
    └── tarbil/
        ├── selectors.ts             (Yeni) TARBİL'e özgü TÜM id/yol/değerler + pageKind
        ├── species.ts               (Yeni) Vetly türü → TARBİL cbxAnimalType değeri
        ├── animalRows.ts            (Yeni) arama ve form tablolarından çip/durum okuma, satır seçimi
        ├── bridge.ts                (Yeni) izole ↔ MAIN postMessage köprüsü
        ├── home.ts                  (Yeni) e-Devlet dönüşünde yönlendirme kararı
        ├── card.ts                  (Yeni) Shadow DOM kartı (textContent ile)
        ├── views.ts                 (Yeni) kart içerikleri (Türkçe metinler)
        ├── searchFlow.ts            (Yeni) arama penceresi akışı
        ├── receiptFlow.ts           (Yeni) aşı sayfası akışı
        ├── content.ts               (Yeniden yaz) sayfa türüne göre yönlendirme
        └── page/
            ├── telerik.ts           (Yeni) $find, postback bekleme, tarih/combobox/buton
            ├── ops.ts               (Yeni) köprüden çağrılan komutlar
            └── main.ts              (Yeni) MAIN dünya giriş noktası
```

---

### Task 1: Altyapı — MAIN dünya derlemesi ve postMessage köprüsü

**Files:**
- Modify: `extension/package.json`
- Modify: `extension/tsconfig.json`
- Create: `extension/vite.page.config.ts`
- Modify: `extension/public/manifest.json`
- Create: `extension/src/tarbil/bridge.ts`
- Create: `extension/src/tarbil/page/main.ts` (bu taskta yalnız iskelet)
- Test: `extension/src/tarbil/bridge.test.ts`

**Interfaces:**
- Produces:
  - `installPageHandler(win: Window, handlers: Record<string, PageHandler>): () => void` — MAIN dünyada komutları dinler; dönen fonksiyon dinleyiciyi kaldırır.
  - `type PageHandler = (args: any) => unknown | Promise<unknown>`
  - `createPageBridge(win: Window): PageBridge`
  - `interface PageBridge { call<T = unknown>(op: string, args?: unknown, timeoutMs?: number): Promise<T> }`
  - `class BridgeError extends Error { code: string }` — sayfa hatasının kodunu (`NOT_FOUND`, `AJAX_TIMEOUT`…) ya da `BRIDGE_TIMEOUT` / `UNKNOWN_OP` taşır.

- [ ] **Step 1: jsdom'u kur**

Run: `cd extension && npm install -D jsdom@^25.0.1`
Expected: `package.json` devDependencies'e `"jsdom": "^25.0.1"` eklenir, hata yok.

- [ ] **Step 2: Köprü için başarısız testleri yaz**

`extension/src/tarbil/bridge.test.ts`:

```ts
// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { createPageBridge, installPageHandler } from './bridge';

const uninstallers: (() => void)[] = [];
afterEach(() => uninstallers.splice(0).forEach((u) => u()));

describe('page bridge', () => {
  it('round-trips a command to the page handler', async () => {
    uninstallers.push(installPageHandler(window, { echo: async (a: unknown) => ({ got: a }) }));
    const bridge = createPageBridge(window);

    await expect(bridge.call('echo', { x: 1 })).resolves.toEqual({ got: { x: 1 } });
  });

  it('rejects with the code thrown by the page handler', async () => {
    uninstallers.push(
      installPageHandler(window, {
        boom: () => {
          throw Object.assign(new Error('yok'), { code: 'NOT_FOUND' });
        },
      }),
    );
    const bridge = createPageBridge(window);

    await expect(bridge.call('boom')).rejects.toMatchObject({ code: 'NOT_FOUND', message: 'yok' });
  });

  it('reports unknown operations', async () => {
    uninstallers.push(installPageHandler(window, {}));
    const bridge = createPageBridge(window);

    await expect(bridge.call('nope')).rejects.toMatchObject({ code: 'UNKNOWN_OP' });
  });

  it('times out when no page handler answers', async () => {
    const bridge = createPageBridge(window);

    await expect(bridge.call('nothing', undefined, 50)).rejects.toMatchObject({ code: 'BRIDGE_TIMEOUT' });
  });
});
```

- [ ] **Step 3: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/bridge.test.ts`
Expected: FAIL — `Failed to resolve import "./bridge"`.

- [ ] **Step 4: Köprüyü yaz**

`extension/src/tarbil/bridge.ts`:

```ts
// Izole dunya (content.js) ile sayfanin kendi dunyasi (page.js) arasinda komut koprusu.
// Iki dunya ayni window'u paylasir; mesajlar sekilden (__vetly) taninir. Sayfa (TARBIL) bu
// mesajlari gorebilir: koprude yalniz TARBIL'e zaten girilecek degerler (tarih, tur, cip) tasinir.

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type PageHandler = (args: any) => unknown | Promise<unknown>;

export class BridgeError extends Error {
  constructor(public readonly code: string, message: string) {
    super(message);
  }
}

interface CommandMessage {
  __vetly: 'cmd';
  id: string;
  op: string;
  args?: unknown;
}

interface ResponseMessage {
  __vetly: 'res';
  id: string;
  ok: boolean;
  data?: unknown;
  code?: string;
  error?: string;
}

export interface PageBridge {
  call<T = unknown>(op: string, args?: unknown, timeoutMs?: number): Promise<T>;
}

export function installPageHandler(win: Window, handlers: Record<string, PageHandler>): () => void {
  const listener = async (event: MessageEvent) => {
    const m = event.data as CommandMessage | null;
    if (!m || m.__vetly !== 'cmd' || typeof m.id !== 'string') return;
    const reply = (r: Omit<ResponseMessage, '__vetly' | 'id'>) => win.postMessage({ __vetly: 'res', id: m.id, ...r }, '*');
    const handler = Object.prototype.hasOwnProperty.call(handlers, m.op) ? handlers[m.op] : undefined;
    if (!handler) {
      reply({ ok: false, code: 'UNKNOWN_OP', error: `Bilinmeyen komut: ${m.op}` });
      return;
    }
    try {
      reply({ ok: true, data: await handler(m.args) });
    } catch (e) {
      const err = e as { code?: string; message?: string };
      reply({ ok: false, code: err?.code ?? 'UNKNOWN', error: err?.message ?? String(e) });
    }
  };
  win.addEventListener('message', listener);
  return () => win.removeEventListener('message', listener);
}

export function createPageBridge(win: Window): PageBridge {
  const pending = new Map<string, { resolve: (v: unknown) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  win.addEventListener('message', (event: MessageEvent) => {
    const m = event.data as ResponseMessage | null;
    if (!m || m.__vetly !== 'res') return;
    const p = pending.get(m.id);
    if (!p) return;
    pending.delete(m.id);
    clearTimeout(p.timer);
    if (m.ok) p.resolve(m.data);
    else p.reject(new BridgeError(m.code ?? 'UNKNOWN', m.error ?? 'Sayfa işlemi başarısız'));
  });

  let seq = 0;
  return {
    call<T>(op: string, args?: unknown, timeoutMs = 20_000): Promise<T> {
      const id = `${Date.now()}-${++seq}`;
      return new Promise<T>((resolve, reject) => {
        const timer = setTimeout(() => {
          pending.delete(id);
          reject(new BridgeError('BRIDGE_TIMEOUT', `Sayfa yanıt vermedi: ${op}`));
        }, timeoutMs);
        pending.set(id, { resolve: resolve as (v: unknown) => void, reject, timer });
        win.postMessage({ __vetly: 'cmd', id, op, args } satisfies CommandMessage, '*');
      });
    },
  };
}
```

- [ ] **Step 5: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx vitest run src/tarbil/bridge.test.ts`
Expected: PASS (4 test).

- [ ] **Step 6: MAIN dünya iskeleti, derleme ve manifest**

`extension/src/tarbil/page/main.ts` (Task 3'te gerçek komutlarla değişecek):

```ts
// Sayfanin kendi dunyasinda (world: MAIN) calisir: $find ve PageRequestManager yalniz burada erisilebilir.
// Vetly API'sine, eklenti anahtarina ya da chrome.* API'lerine erisimi YOKTUR; yalniz komut yurutur.
import { installPageHandler } from '../bridge';

installPageHandler(window, {
  ping: () => 'pong',
});
```

`extension/vite.page.config.ts`:

```ts
import { defineConfig } from 'vite';
import { resolve } from 'node:path';

// MAIN dunya betigi: ayri IIFE paket (content.js ile ayni dosyada olamaz, farkli dunyada calisir).
export default defineConfig({
  build: {
    outDir: 'dist',
    emptyOutDir: false,
    lib: {
      entry: resolve(__dirname, 'src/tarbil/page/main.ts'),
      formats: ['iife'],
      name: 'VetlyTarbilPage',
      fileName: () => 'page.js',
    },
  },
});
```

`extension/package.json` `scripts` bölümünde `build` ve `build:dev` satırlarını şununla değiştir:

```json
    "build": "tsc --noEmit && vite build && vite build --config vite.content.config.ts && vite build --config vite.page.config.ts",
    "build:dev": "tsc --noEmit && vite build --mode development && vite build --mode development --config vite.content.config.ts && vite build --mode development --config vite.page.config.ts",
```

`extension/tsconfig.json` `include` dizisine `"vite.page.config.ts"` ekle:

```json
  "include": ["src", "vite.config.ts", "vite.content.config.ts", "vite.page.config.ts", "vitest.config.ts"]
```

`extension/public/manifest.json` `content_scripts` dizisini şununla değiştir (MAIN dünya betiği önce, `world: "MAIN"` Chrome 111+):

```json
  "content_scripts": [
    {
      "matches": ["https://hbsapp.tarbil.gov.tr/*"],
      "js": ["page.js"],
      "run_at": "document_idle",
      "world": "MAIN"
    },
    {
      "matches": ["https://hbsapp.tarbil.gov.tr/*"],
      "js": ["content.js"],
      "run_at": "document_idle"
    }
  ]
```

- [ ] **Step 7: Derle ve tüm testleri çalıştır**

Run: `cd extension && npm run build:dev && ls dist && npm test`
Expected: derleme hatasız; `dist` içinde `background.js`, `content.js`, `page.js`, `sidepanel.html`, `manifest.json` var; Vitest tüm testler PASS (önceki 15 + 4 = 19).

- [ ] **Step 8: Commit**

```bash
git add extension/package.json extension/package-lock.json extension/tsconfig.json extension/vite.page.config.ts extension/public/manifest.json extension/src/tarbil/bridge.ts extension/src/tarbil/bridge.test.ts extension/src/tarbil/page/main.ts
git commit -m "feat(tarbil-ext): MAIN dunya betigi ve postMessage koprusu"
```

---

### Task 2: Saf mantık — seçiciler, tür eşlemesi, tablo okuma

**Files:**
- Create: `extension/src/tarbil/selectors.ts`
- Create: `extension/src/tarbil/species.ts`
- Create: `extension/src/tarbil/animalRows.ts`
- Test: `extension/src/tarbil/selectors.test.ts`
- Test: `extension/src/tarbil/species.test.ts`
- Test: `extension/src/tarbil/animalRows.test.ts`

**Interfaces:**
- Produces (`selectors.ts`):
  - `VACCINE_PAGE_URL: string`, `ANIMAL_TYPE: { CAT: string; DOG: string }`
  - `RECEIPT: { date; animalType; petVet; animalGrid; insertButtons: string[]; successPanel }` (id sonekleri)
  - `SEARCH: { chip; search; grid; transfer }` (id sonekleri)
  - `bySuffix(suffix: string): string` → `[id$="<suffix>"]`
  - `type PageKind = 'receipt' | 'search' | 'home' | 'other'`; `pageKind(loc: { pathname: string; search: string }): PageKind`
- Produces (`species.ts`): `resolveAnimalType(s: Pick<Submission, 'speciesName' | 'speciesMapping'>): string | null`
- Produces (`animalRows.ts`):
  - `normalizeChip(s: string | null | undefined): string` (yalnız rakamlar)
  - `interface AnimalRow { rowId: string; chip: string; status: string | null; checkboxId: string | null }`
  - `readSearchRows(doc: Document): AnimalRow[]`
  - `readReceiptChips(doc: Document): string[]`
  - `type PickResult = { kind: 'one'; row: AnimalRow } | { kind: 'none' } | { kind: 'many' } | { kind: 'notAlive'; row: AnimalRow }`
  - `pickAnimal(rows: AnimalRow[], chip: string): PickResult`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/selectors.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { bySuffix, pageKind } from './selectors';

describe('pageKind', () => {
  it('recognizes the vaccine receipt page only for type=1', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx', search: '?type=1' })).toBe('receipt');
    expect(pageKind({ pathname: '/modules/receipt/pages/ats/vaccinereceipt/vaccinereceiptpage.aspx', search: '?type=1' })).toBe('receipt');
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx', search: '?type=2' })).toBe('other');
  });

  it('recognizes the animal search popup', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ModalPages/VaccineKKBSAnimalSearchModalPage.aspx', search: '?AnimalType=C' })).toBe('search');
  });

  it('recognizes the TARBIL home page', () => {
    expect(pageKind({ pathname: '/', search: '' })).toBe('home');
    expect(pageKind({ pathname: '/Default.aspx', search: '' })).toBe('home');
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/VaccineDefault.aspx', search: '' })).toBe('other');
  });

  it('builds an id-suffix selector', () => {
    expect(bySuffix('_btnInsert')).toBe('[id$="_btnInsert"]');
  });
});
```

`extension/src/tarbil/species.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { ANIMAL_TYPE } from './selectors';
import { resolveAnimalType } from './species';

describe('resolveAnimalType', () => {
  it('maps Vetly cat and dog names to TARBIL values', () => {
    expect(resolveAnimalType({ speciesName: 'Kedi', speciesMapping: null })).toBe(ANIMAL_TYPE.CAT);
    expect(resolveAnimalType({ speciesName: 'Kopek', speciesMapping: null })).toBe(ANIMAL_TYPE.DOG);
    expect(resolveAnimalType({ speciesName: ' KÖPEK ', speciesMapping: null })).toBe(ANIMAL_TYPE.DOG);
  });

  it('returns null for unsupported or missing species', () => {
    expect(resolveAnimalType({ speciesName: 'Tavşan', speciesMapping: null })).toBeNull();
    expect(resolveAnimalType({ speciesName: null, speciesMapping: null })).toBeNull();
  });

  it('prefers a learned species mapping', () => {
    expect(resolveAnimalType({ speciesName: 'Kedi', speciesMapping: { animalType: { value: 'guid-x', text: 'Kedi' } } })).toBe('guid-x');
  });
});
```

`extension/src/tarbil/animalRows.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { normalizeChip, pickAnimal, readReceiptChips, readSearchRows } from './animalRows';

const SP = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_radGridAnimal_ctl00';

function searchTable(rows: string) {
  document.body.innerHTML = `<table id="${SP}"><thead><tr><th></th><th>Adı</th><th>Çip No</th><th>Pasaport No</th>
    <th>Tür</th><th>Irk</th><th>Cinsiyet</th><th>Renk</th><th>Doğum Tarihi</th><th>Durumu</th><th>Hayvan Sahibi</th>
    <th>Anne Çip No</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function srow(i: number, chip: string, status = 'CANLI', mother = '') {
  return `<tr id="${SP}__${i}"><td><input type="checkbox" id="${SP}_ctl0${4 + 2 * i}_gridchkBoxAnimalIscheckedTempColumn"></td>
    <td>Ad</td><td>${chip}</td><td>TR00000000</td><td>Kedi</td><td>Irk</td><td>Erkek</td><td>Gri</td><td>01/01/23</td>
    <td> ${status} </td><td></td><td>${mother}</td></tr>`;
}

const RP = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00';

function receiptTable(rows: string) {
  document.body.innerHTML = `<table id="${RP}"><thead><tr class="rgCommandRow"><td colspan="7">butonlar</td></tr>
    <tr><th></th><th style="display:none">&nbsp;</th><th>Sistem Küpe/Çip No</th><th>Hayvan Doğum Tarihi</th>
    <th>Hayvan Cinsiyeti</th><th>Hayvan Irkı</th><th>Hayvan Eşgali</th><th>Sil</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function rrow(i: number, chip: string) {
  return `<tr id="${RP}__${i}"><td><input type="checkbox"></td><td style="display:none"></td><td>
    ${chip}
    </td><td>1.1.2023 00:00:00</td><td>Erkek</td><td>Irk</td><td></td><td></td></tr>`;
}

describe('normalizeChip', () => {
  it('keeps digits only', () => {
    expect(normalizeChip(' 900 000-000000001 ')).toBe('900000000000001');
    expect(normalizeChip(null)).toBe('');
  });
});

describe('readSearchRows', () => {
  it('reads chip, status and checkbox id of each result row', () => {
    searchTable(srow(0, '900000000000001'));

    expect(readSearchRows(document)).toEqual([
      { rowId: `${SP}__0`, chip: '900000000000001', status: 'CANLI', checkboxId: `${SP}_ctl04_gridchkBoxAnimalIscheckedTempColumn` },
    ]);
  });

  it('returns no rows when the grid is absent', () => {
    document.body.innerHTML = '<div></div>';
    expect(readSearchRows(document)).toEqual([]);
  });
});

describe('pickAnimal', () => {
  it('picks the single alive exact match', () => {
    searchTable(srow(0, '900000000000001') + srow(1, '900000000000002'));

    const result = pickAnimal(readSearchRows(document), '900000000000002');

    expect(result).toMatchObject({ kind: 'one', row: { rowId: `${SP}__1` } });
  });

  it('does not match on the mother chip column', () => {
    searchTable(srow(0, '900000000000009', 'CANLI', '900000000000001'));

    expect(pickAnimal(readSearchRows(document), '900000000000001')).toEqual({ kind: 'none' });
  });

  it('refuses to choose between several matches', () => {
    searchTable(srow(0, '900000000000001') + srow(1, '900000000000001'));

    expect(pickAnimal(readSearchRows(document), '900000000000001')).toEqual({ kind: 'many' });
  });

  it('does not auto-select an animal that is not alive', () => {
    searchTable(srow(0, '900000000000001', 'ÖLÜ'));

    expect(pickAnimal(readSearchRows(document), '900000000000001')).toMatchObject({ kind: 'notAlive', row: { status: 'ÖLÜ' } });
  });
});

describe('readReceiptChips', () => {
  it('reads chips of animals added to the receipt form', () => {
    receiptTable(rrow(0, '900000000000001'));

    expect(readReceiptChips(document)).toEqual(['900000000000001']);
  });

  it('ignores the empty-grid row', () => {
    receiptTable('<tr class="rgNoRecords"><td colspan="8">Kayıt Bulunamadı.</td></tr>');

    expect(readReceiptChips(document)).toEqual([]);
  });
});
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/selectors.test.ts src/tarbil/species.test.ts src/tarbil/animalRows.test.ts`
Expected: FAIL — `./selectors`, `./species`, `./animalRows` çözümlenemiyor.

- [ ] **Step 3: Seçicileri yaz**

`extension/src/tarbil/selectors.ts`:

```ts
// TARBIL'e ozgu TUM id/yol/deger bilgisi burada (spec S3). TARBIL ekrani degisirse yalniz bu dosya degisir.
// Id'ler on ek degil SONEK ile aranir: Telerik istemci id'leri (ctl00_ctl00_...) ile duz id'ler (bodyCPH_...)
// ayni bilesende farkli on ekler tasiyor.

export const TARBIL_ORIGIN = 'https://hbsapp.tarbil.gov.tr';
const VACCINE_PAGE_PATH = '/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx';
export const VACCINE_PAGE_URL = `${TARBIL_ORIGIN}${VACCINE_PAGE_PATH}?type=1`;
const SEARCH_PAGE_FILE = 'vaccinekkbsanimalsearchmodalpage.aspx';
const HOME_PATHS = ['/', '/default.aspx'];

/** cbxAnimalType degerleri (spec S3). */
export const ANIMAL_TYPE = {
  CAT: '245f5f71-2cfd-4a04-9072-640069e3268e',
  DOG: '3eea84e6-b2d8-494f-a01c-89e739bb004d',
} as const;

/** Asi Uygulama Belgesi Ekle sayfasi. */
export const RECEIPT = {
  date: '_cntVACCINEBodyContent_dpApplicationDate',
  animalType: '_cntVACCINEBodyContent_cbxAnimalType',
  petVet: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00_ctl02_ctl00_bntPetVet',
  animalGrid: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00',
  // Onayla butonlari: eklenti bunlara ASLA basmaz, yalniz hekimin tiklamasini fark eder.
  insertButtons: ['_cntVACCINEBodyContent_btnInsert', '_cntVACCINEBodyContent_btnInsert2'],
  // Varsayim: hata paneli pnlNotifiError, basari paneli ayni kalipla (elle kabul testinde dogrulanacak).
  successPanel: '_UCVACCINENotification_pnlNotifiSuccess',
} as const;

/** PetVet hayvan arama penceresi (VaccineKKBSAnimalSearchModalPage.aspx). */
export const SEARCH = {
  chip: '_UCVaccineKKBSAnimalSearch_txtChipNo',
  search: '_UCVaccineKKBSAnimalSearch_btnSearch',
  grid: '_UCVaccineKKBSAnimalSearch_radGridAnimal_ctl00',
  transfer: '_UCVaccineKKBSAnimalSearch_btnAddBulkAnimal',
} as const;

export const bySuffix = (suffix: string): string => `[id$="${suffix}"]`;

export type PageKind = 'receipt' | 'search' | 'home' | 'other';

export function pageKind(loc: { pathname: string; search: string }): PageKind {
  const path = loc.pathname.toLowerCase();
  if (path === VACCINE_PAGE_PATH.toLowerCase()) {
    return new URLSearchParams(loc.search).get('type') === '1' ? 'receipt' : 'other';
  }
  if (path.endsWith(`/${SEARCH_PAGE_FILE}`)) return 'search';
  if (HOME_PATHS.includes(path)) return 'home';
  return 'other';
}
```

- [ ] **Step 4: Tür eşlemesini yaz**

`extension/src/tarbil/species.ts`:

```ts
import type { Submission } from '../shared/types';
import { ANIMAL_TYPE } from './selectors';

/** Vetly turu -> TARBIL cbxAnimalType degeri. Ogrenilmis eslestirme varsa o kazanir; yoksa yalniz kedi/kopek. */
export function resolveAnimalType(s: Pick<Submission, 'speciesName' | 'speciesMapping'>): string | null {
  const mapped = (s.speciesMapping as { animalType?: { value?: unknown } } | null)?.animalType?.value;
  if (typeof mapped === 'string' && mapped.length > 0) return mapped;
  const name = (s.speciesName ?? '').trim().toLocaleLowerCase('tr-TR').replace(/ö/g, 'o');
  if (name === 'kedi') return ANIMAL_TYPE.CAT;
  if (name === 'kopek') return ANIMAL_TYPE.DOG;
  return null;
}
```

- [ ] **Step 5: Tablo okumayı yaz**

`extension/src/tarbil/animalRows.ts`:

```ts
import { RECEIPT, SEARCH, bySuffix } from './selectors';

// Gizlilik: tablolardan YALNIZ cip, durum ve satir onay kutusunun id'si okunur; ad, sahip vb. hucrelere dokunulmaz.

export interface AnimalRow {
  rowId: string;
  chip: string;
  status: string | null;
  checkboxId: string | null;
}

export type PickResult =
  | { kind: 'one'; row: AnimalRow }
  | { kind: 'none' }
  | { kind: 'many' }
  | { kind: 'notAlive'; row: AnimalRow };

export function normalizeChip(s: string | null | undefined): string {
  return (s ?? '').replace(/\D/g, '');
}

const headerText = (s: string | null) => (s ?? '').replace(/\s+/g, ' ').trim().toLocaleLowerCase('tr-TR');

/** Sutun sirasi baslik metninden bulunur (gizli sutunlar baslikta da var); bulunamazsa bilinen sira. */
function columnIndex(table: HTMLTableElement, test: (header: string) => boolean, fallback: number): number {
  const head = table.tHead?.rows;
  const cells = head && head.length > 0 ? Array.from(head[head.length - 1].cells) : [];
  const i = cells.findIndex((c) => test(headerText(c.textContent)));
  return i >= 0 ? i : fallback;
}

function dataRows(table: HTMLTableElement): HTMLTableRowElement[] {
  return Array.from(table.tBodies[0]?.rows ?? []).filter((r) => r.id.startsWith(`${table.id}__`));
}

function findTable(doc: Document, suffix: string): HTMLTableElement | null {
  return doc.querySelector<HTMLTableElement>(`table${bySuffix(suffix)}`);
}

export function readSearchRows(doc: Document): AnimalRow[] {
  const table = findTable(doc, SEARCH.grid);
  if (!table) return [];
  const chipCol = columnIndex(table, (h) => h.includes('çip') && !h.includes('anne'), 2);
  const statusCol = columnIndex(table, (h) => h.startsWith('durum'), 9);
  return dataRows(table).map((r) => ({
    rowId: r.id,
    chip: normalizeChip(r.cells[chipCol]?.textContent),
    status: r.cells[statusCol]?.textContent?.trim() || null,
    checkboxId: r.querySelector<HTMLInputElement>('input[type="checkbox"]')?.id || null,
  }));
}

export function readReceiptChips(doc: Document): string[] {
  const table = findTable(doc, RECEIPT.animalGrid);
  if (!table) return [];
  const chipCol = columnIndex(table, (h) => h.includes('çip'), 2);
  return dataRows(table)
    .map((r) => normalizeChip(r.cells[chipCol]?.textContent))
    .filter((c) => c.length > 0);
}

export function pickAnimal(rows: AnimalRow[], chip: string): PickResult {
  const target = normalizeChip(chip);
  const matches = target ? rows.filter((r) => r.chip === target) : [];
  if (matches.length === 0) return { kind: 'none' };
  if (matches.length > 1) return { kind: 'many' };
  const row = matches[0];
  return (row.status ?? '').toLocaleUpperCase('tr-TR') === 'CANLI' ? { kind: 'one', row } : { kind: 'notAlive', row };
}
```

- [ ] **Step 6: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx vitest run src/tarbil/selectors.test.ts src/tarbil/species.test.ts src/tarbil/animalRows.test.ts`
Expected: PASS (4 + 3 + 10 = 17 test).

- [ ] **Step 7: Commit**

```bash
git add extension/src/tarbil/selectors.ts extension/src/tarbil/species.ts extension/src/tarbil/animalRows.ts extension/src/tarbil/selectors.test.ts extension/src/tarbil/species.test.ts extension/src/tarbil/animalRows.test.ts
git commit -m "feat(tarbil-ext): TARBIL seciciler, tur eslemesi, cip ile satir secimi"
```

---

### Task 3: MAIN dünya — Telerik yardımcıları ve sayfa komutları

**Files:**
- Create: `extension/src/tarbil/page/telerik.ts`
- Create: `extension/src/tarbil/page/ops.ts`
- Modify: `extension/src/tarbil/page/main.ts`
- Test: `extension/src/tarbil/page/telerik.test.ts`
- Test: `extension/src/tarbil/page/ops.test.ts`

**Interfaces:**
- Consumes: `installPageHandler` (Task 1); `RECEIPT`, `SEARCH` (Task 2).
- Produces (`telerik.ts`):
  - `interface Prm { add_beginRequest(h: Function): void; remove_beginRequest(h: Function): void; add_endRequest(h: Function): void; remove_endRequest(h: Function): void }`
  - `interface TelerikEnv { doc: Document; find: (id: string) => TelerikComponent | null; prm: () => Prm | null; isReady: () => boolean }`
  - `type TelerikComponent = Record<string, any>`
  - `class PageError extends Error { code: 'NOT_FOUND' | 'OPTION_NOT_FOUND' | 'AJAX_TIMEOUT' | 'AJAX_ERROR' | 'NOT_READY' | 'BAD_INPUT' }`
  - `withPostback(env, action: () => void, opts?: { startGraceMs?: number; timeoutMs?: number }): Promise<{ postback: boolean }>`
  - `waitUntil(test: () => boolean, timeoutMs: number, stepMs?: number): Promise<void>`
  - `setDate(env, suffix, iso: string): Promise<{ changed: boolean }>`
  - `selectComboValue(env, suffix, value: string): Promise<{ changed: boolean }>`
  - `setText(env, suffix, value: string): void`
  - `clickButton(env, suffix): Promise<{ postback: boolean }>`
  - `clickElement(env, id: string): Promise<{ postback: boolean }>`
- Produces (`ops.ts`): `createPageOps(env: TelerikEnv): Record<string, PageHandler>` — komut adları ve argümanları:
  - `ready()` → sayfa Telerik'i başlatana kadar bekler (10 sn)
  - `setDate({ iso: 'YYYY-MM-DD' })`, `selectAnimalType({ value })`, `clickPetVet()`
  - `searchChip({ chip })`, `checkRow({ checkboxId })`, `transfer()`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/page/telerik.test.ts`:

```ts
// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { clickButton, selectComboValue, setDate, withPostback, type Prm, type TelerikComponent, type TelerikEnv } from './telerik';

type Handler = (...a: unknown[]) => void;

function fakePrm() {
  const begin = new Set<Handler>();
  const end = new Set<Handler>();
  return {
    add_beginRequest: (h: Handler) => begin.add(h),
    remove_beginRequest: (h: Handler) => begin.delete(h),
    add_endRequest: (h: Handler) => end.add(h),
    remove_endRequest: (h: Handler) => end.delete(h),
    begin: () => begin.forEach((h) => h()),
    end: (error: unknown = null) => end.forEach((h) => h(null, { get_error: () => error })),
  };
}

function env(prm: ReturnType<typeof fakePrm>, comps: Record<string, TelerikComponent> = {}): TelerikEnv {
  return { doc: document, find: (id) => comps[id] ?? null, prm: () => prm as unknown as Prm, isReady: () => true };
}

afterEach(() => vi.useRealTimers());

describe('withPostback', () => {
  it('resolves after the triggered postback ends', async () => {
    const prm = fakePrm();
    await expect(withPostback(env(prm), () => { prm.begin(); prm.end(); })).resolves.toEqual({ postback: true });
  });

  it('resolves without postback when none starts within the grace period', async () => {
    vi.useFakeTimers();
    const prm = fakePrm();
    const p = withPostback(env(prm), () => undefined);
    vi.advanceTimersByTime(1500);
    await expect(p).resolves.toEqual({ postback: false });
  });

  it('rejects when the postback never ends', async () => {
    vi.useFakeTimers();
    const prm = fakePrm();
    const p = withPostback(env(prm), () => prm.begin());
    vi.advanceTimersByTime(10_000);
    await expect(p).rejects.toMatchObject({ code: 'AJAX_TIMEOUT' });
  });

  it('rejects when the postback reports an error', async () => {
    const prm = fakePrm();
    await expect(withPostback(env(prm), () => { prm.begin(); prm.end(new Error('500')); })).rejects.toMatchObject({ code: 'AJAX_ERROR' });
  });
});

describe('Telerik controls', () => {
  const ID = 'ctl00_X_cntVACCINEBodyContent_cbxAnimalType';

  it('selects a combobox value and waits for its postback', async () => {
    document.body.innerHTML = `<div id="${ID}"></div>`;
    const prm = fakePrm();
    const selected: string[] = [];
    const combo = {
      get_value: () => '',
      findItemByValue: (v: string) => ({ select: () => { selected.push(v); prm.begin(); prm.end(); } }),
    };

    await expect(selectComboValue(env(prm, { [ID]: combo }), '_cntVACCINEBodyContent_cbxAnimalType', 'cat')).resolves.toEqual({ changed: true });
    expect(selected).toEqual(['cat']);
  });

  it('leaves an already selected combobox value alone', async () => {
    document.body.innerHTML = `<div id="${ID}"></div>`;
    const combo = { get_value: () => 'cat', findItemByValue: () => { throw new Error('should not select'); } };

    await expect(selectComboValue(env(fakePrm(), { [ID]: combo }), '_cntVACCINEBodyContent_cbxAnimalType', 'cat')).resolves.toEqual({ changed: false });
  });

  it('fails clearly when a combobox option is missing', async () => {
    document.body.innerHTML = `<div id="${ID}"></div>`;
    const combo = { get_value: () => '', findItemByValue: () => null };

    await expect(selectComboValue(env(fakePrm(), { [ID]: combo }), '_cntVACCINEBodyContent_cbxAnimalType', 'x')).rejects.toMatchObject({ code: 'OPTION_NOT_FOUND' });
  });

  it('fails clearly when the control is not on the page', async () => {
    document.body.innerHTML = '';
    await expect(selectComboValue(env(fakePrm()), '_cntVACCINEBodyContent_cbxAnimalType', 'x')).rejects.toMatchObject({ code: 'NOT_FOUND' });
  });

  it('sets the date picker to the given local date', async () => {
    const DID = 'ctl00_X_cntVACCINEBodyContent_dpApplicationDate';
    document.body.innerHTML = `<input id="${DID}">`;
    const prm = fakePrm();
    let set: Date | null = null;
    const picker = { get_selectedDate: () => new Date(2026, 9, 1), set_selectedDate: (d: Date) => { set = d; prm.begin(); prm.end(); } };

    await expect(setDate(env(prm, { [DID]: picker }), '_cntVACCINEBodyContent_dpApplicationDate', '2026-10-03')).resolves.toEqual({ changed: true });
    expect([set!.getFullYear(), set!.getMonth(), set!.getDate()]).toEqual([2026, 9, 3]);
  });

  it('clicks the inner input when the button has no Telerik component', async () => {
    document.body.innerHTML = '<a id="ctl00_X_btnSearch"><input id="ctl00_X_btnSearch_input" type="button"></a>';
    const clicked = vi.fn();
    document.getElementById('ctl00_X_btnSearch_input')!.addEventListener('click', clicked);
    vi.useFakeTimers();
    const p = clickButton(env(fakePrm()), '_btnSearch');
    vi.advanceTimersByTime(1500);
    await p;
    expect(clicked).toHaveBeenCalledOnce();
  });
});
```

`extension/src/tarbil/page/ops.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { createPageOps } from './ops';
import type { Prm, TelerikEnv } from './telerik';

function instantPrm(): Prm & { fire: () => void } {
  const begin = new Set<Function>();
  const end = new Set<Function>();
  return {
    add_beginRequest: (h) => begin.add(h),
    remove_beginRequest: (h) => begin.delete(h),
    add_endRequest: (h) => end.add(h),
    remove_endRequest: (h) => end.delete(h),
    fire: () => { begin.forEach((h) => h()); end.forEach((h) => h(null, { get_error: () => null })); },
  };
}

describe('page ops', () => {
  it('types the chip into the search box and presses Ara', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_';
    document.body.innerHTML = `<input id="${P}txtChipNo"><a id="${P}btnSearch"></a>`;
    const prm = instantPrm();
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [`${P}txtChipNo`]: { set_value: (v: string) => log.push(`chip:${v}`) },
      [`${P}btnSearch`]: { click: () => { log.push('search'); prm.fire(); } },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchChip({ chip: '900000000000001' });

    expect(log).toEqual(['chip:900000000000001', 'search']);
  });

  it('checks a result row by clicking its checkbox and waits for the postback', async () => {
    document.body.innerHTML = '<input type="checkbox" id="cb1">';
    const prm = instantPrm();
    document.getElementById('cb1')!.addEventListener('click', () => prm.fire());
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => prm, isReady: () => true };

    await expect(createPageOps(env).checkRow({ checkboxId: 'cb1' })).resolves.toEqual({ postback: true });
    expect((document.getElementById('cb1') as HTMLInputElement).checked).toBe(true);
  });
});
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/page`
Expected: FAIL — `./telerik`, `./ops` çözümlenemiyor.

- [ ] **Step 3: Telerik yardımcılarını yaz**

`extension/src/tarbil/page/telerik.ts`:

```ts
// Telerik RadControls 2012.3 + ASP.NET AJAX (spec S3). Bilesenler her komutta YENIDEN aranir:
// async postback PageContentPanel'i yeniden cizer ve eski nesneler gecersiz kalir.

/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types */
export type TelerikComponent = Record<string, any>;

export interface Prm {
  add_beginRequest(h: Function): void;
  remove_beginRequest(h: Function): void;
  add_endRequest(h: Function): void;
  remove_endRequest(h: Function): void;
}

export interface TelerikEnv {
  doc: Document;
  find: (id: string) => TelerikComponent | null;
  prm: () => Prm | null;
  isReady: () => boolean;
}

export type PageErrorCode = 'NOT_FOUND' | 'OPTION_NOT_FOUND' | 'AJAX_TIMEOUT' | 'AJAX_ERROR' | 'NOT_READY' | 'BAD_INPUT';

export class PageError extends Error {
  constructor(public readonly code: PageErrorCode, message: string) {
    super(message);
  }
}

export function clientId(env: TelerikEnv, suffix: string): string {
  const el = env.doc.querySelector(`[id$="${suffix}"]`);
  if (!el) throw new PageError('NOT_FOUND', `Sayfada bulunamadı: ${suffix}`);
  return el.id;
}

function component(env: TelerikEnv, suffix: string): TelerikComponent {
  const c = env.find(clientId(env, suffix));
  if (!c) throw new PageError('NOT_FOUND', `Bileşen hazır değil: ${suffix}`);
  return c;
}

/**
 * action'i calistirir ve tetikledigi async postback'in bitmesini bekler. startGraceMs icinde postback
 * baslamazsa postback olmadigi kabul edilir. Hata TARBIL'e birakilir (kendi mesajini gosterir), biz yalniz durururuz.
 */
export function withPostback(
  env: TelerikEnv,
  action: () => void,
  { startGraceMs = 1500, timeoutMs = 10_000 }: { startGraceMs?: number; timeoutMs?: number } = {},
): Promise<{ postback: boolean }> {
  return new Promise((resolve, reject) => {
    const prm = env.prm();
    if (!prm) {
      try {
        action();
        resolve({ postback: false });
      } catch (e) {
        reject(e);
      }
      return;
    }
    let started = false;
    const cleanup = () => {
      prm.remove_beginRequest(onBegin);
      prm.remove_endRequest(onEnd);
      clearTimeout(grace);
      clearTimeout(timeout);
    };
    const onBegin = () => {
      started = true;
    };
    const onEnd = (_sender: unknown, args: { get_error?: () => unknown } | undefined) => {
      cleanup();
      const error = args?.get_error?.();
      if (error) reject(new PageError('AJAX_ERROR', `TARBİL isteği hata verdi: ${String((error as Error).message ?? error)}`));
      else resolve({ postback: true });
    };
    prm.add_beginRequest(onBegin);
    prm.add_endRequest(onEnd);
    const grace = setTimeout(() => {
      if (!started) {
        cleanup();
        resolve({ postback: false });
      }
    }, startGraceMs);
    const timeout = setTimeout(() => {
      cleanup();
      reject(new PageError('AJAX_TIMEOUT', 'TARBİL yanıt vermedi'));
    }, timeoutMs);
    try {
      action();
    } catch (e) {
      cleanup();
      reject(e);
    }
  });
}

export async function waitUntil(test: () => boolean, timeoutMs: number, stepMs = 100): Promise<void> {
  const end = Date.now() + timeoutMs;
  while (!test()) {
    if (Date.now() > end) throw new PageError('NOT_READY', 'TARBİL sayfası hazır olmadı');
    await new Promise((r) => setTimeout(r, stepMs));
  }
}

export async function setDate(env: TelerikEnv, suffix: string, iso: string): Promise<{ changed: boolean }> {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) throw new PageError('BAD_INPUT', `Geçersiz tarih: ${iso}`);
  const target = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  const picker = component(env, suffix);
  const current = picker.get_selectedDate?.() as Date | null | undefined;
  if (
    current &&
    current.getFullYear() === target.getFullYear() &&
    current.getMonth() === target.getMonth() &&
    current.getDate() === target.getDate()
  ) {
    return { changed: false };
  }
  await withPostback(env, () => picker.set_selectedDate(target));
  return { changed: true };
}

export async function selectComboValue(env: TelerikEnv, suffix: string, value: string): Promise<{ changed: boolean }> {
  const combo = component(env, suffix);
  if (combo.get_value() === value) return { changed: false };
  const item = combo.findItemByValue(value);
  if (!item) throw new PageError('OPTION_NOT_FOUND', `Seçenek yok: ${suffix}`);
  await withPostback(env, () => item.select());
  return { changed: true };
}

export function setText(env: TelerikEnv, suffix: string, value: string): void {
  component(env, suffix).set_value(value);
}

export function clickButton(env: TelerikEnv, suffix: string): Promise<{ postback: boolean }> {
  const id = clientId(env, suffix);
  const c = env.find(id);
  return withPostback(env, () => {
    if (c && typeof c.click === 'function') {
      c.click();
      return;
    }
    const el = env.doc.getElementById(`${id}_input`) ?? env.doc.getElementById(id);
    if (!el) throw new PageError('NOT_FOUND', `Buton bulunamadı: ${suffix}`);
    (el as HTMLElement).click();
  });
}

export function clickElement(env: TelerikEnv, id: string): Promise<{ postback: boolean }> {
  const el = env.doc.getElementById(id);
  if (!el) return Promise.reject(new PageError('NOT_FOUND', `Öğe bulunamadı: ${id}`));
  return withPostback(env, () => (el as HTMLElement).click());
}
```

- [ ] **Step 4: Sayfa komutlarını yaz**

`extension/src/tarbil/page/ops.ts`:

```ts
import type { PageHandler } from '../bridge';
import { RECEIPT, SEARCH } from '../selectors';
import { clickButton, clickElement, selectComboValue, setDate, setText, waitUntil, type TelerikEnv } from './telerik';

/**
 * Koprudan cagrilan komutlar. Bilerek eksik: Onayla (btnInsert), Urun Ekle, sahip alanlari ve cikis icin
 * komut YOKTUR (spec S2, S12.6) -- izole dunya istese bile sayfa bunlari yapamaz.
 */
export function createPageOps(env: TelerikEnv): Record<string, PageHandler> {
  return {
    ready: () => waitUntil(env.isReady, 10_000),
    setDate: ({ iso }: { iso: string }) => setDate(env, RECEIPT.date, iso),
    selectAnimalType: ({ value }: { value: string }) => selectComboValue(env, RECEIPT.animalType, value),
    clickPetVet: () => clickButton(env, RECEIPT.petVet),
    searchChip: async ({ chip }: { chip: string }) => {
      setText(env, SEARCH.chip, chip);
      await clickButton(env, SEARCH.search);
    },
    checkRow: ({ checkboxId }: { checkboxId: string }) => clickElement(env, checkboxId),
    transfer: () => clickButton(env, SEARCH.transfer),
  };
}
```

`extension/src/tarbil/page/main.ts` (tümüyle değiştir):

```ts
// Sayfanin kendi dunyasinda (world: MAIN) calisir: $find ve PageRequestManager yalniz burada erisilebilir.
// Vetly API'sine, eklenti anahtarina ya da chrome.* API'lerine erisimi YOKTUR; yalniz komut yurutur.
import { installPageHandler } from '../bridge';
import { createPageOps } from './ops';
import type { Prm, TelerikComponent, TelerikEnv } from './telerik';

/* eslint-disable @typescript-eslint/no-explicit-any */
const w = window as any;

const env: TelerikEnv = {
  doc: document,
  find: (id: string) => (typeof w.$find === 'function' ? ((w.$find(id) as TelerikComponent | null) ?? null) : null),
  prm: () => (w.Sys?.WebForms?.PageRequestManager?.getInstance?.() as Prm | undefined) ?? null,
  isReady: () => Boolean(w.Sys?.Application?.get_isInitialized?.()),
};

installPageHandler(window, createPageOps(env));
```

- [ ] **Step 5: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx vitest run src/tarbil/page`
Expected: PASS (10 + 2 = 12 test).

- [ ] **Step 6: Tip kontrolü**

Run: `cd extension && npx tsc --noEmit`
Expected: çıktı yok (hata yok).

- [ ] **Step 7: Commit**

```bash
git add extension/src/tarbil/page
git commit -m "feat(tarbil-ext): Telerik yardimcilari ve sayfa komutlari (MAIN dunya)"
```

---

### Task 4: Akış durumu — `tarbilFlow`, arka planda "armed", e-Devlet dönüşü

**Files:**
- Create: `extension/src/shared/flowStore.ts`
- Modify: `extension/src/background/router.ts`
- Create: `extension/src/tarbil/home.ts`
- Test: `extension/src/shared/flowStore.test.ts`
- Modify: `extension/src/background/router.test.ts`
- Test: `extension/src/tarbil/home.test.ts`

**Interfaces:**
- Consumes: `KeyValueStore`, `memoryStore` (`background/chromeStorage.ts`, mevcut).
- Produces (`flowStore.ts`):
  - `FLOW_KEY = 'tarbilFlow'`
  - `type FlowStep = 'armed' | 'filling' | 'searching' | 'transferred' | 'needsVet' | 'awaitingConfirm' | 'done' | 'error'`
  - `interface FlowState { submissionId: string; step: FlowStep; updatedAt: number; message?: string; insertClickedAt?: number; redirectedAt?: number }`
  - `createFlowStore(store: KeyValueStore, now?: () => number): FlowStore`
  - `interface FlowStore { get(): Promise<FlowState | null>; arm(submissionId: string): Promise<void>; update(submissionId: string, patch: Partial<Omit<FlowState, 'submissionId' | 'updatedAt'>>): Promise<FlowState | null> }` — `update` başka aşıya ait durumu değiştirmez (`null` döner); `updatedAt` yalnız `patch.step` verildiğinde yenilenir.
- Produces (`home.ts`): `shouldRedirectHome(state: FlowState | null, now: number): boolean`; sabitler `ARMED_TTL_MS = 30 * 60_000`, `REDIRECT_COOLDOWN_MS = 60_000`.

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/shared/flowStore.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { memoryStore } from '../background/chromeStorage';
import { createFlowStore } from './flowStore';

describe('flowStore', () => {
  it('arms a submission', async () => {
    const flow = createFlowStore(memoryStore(), () => 1000);
    await flow.arm('s1');
    expect(await flow.get()).toEqual({ submissionId: 's1', step: 'armed', updatedAt: 1000 });
  });

  it('does not touch another submission state', async () => {
    const flow = createFlowStore(memoryStore(), () => 1000);
    await flow.arm('s1');
    expect(await flow.update('s2', { step: 'done' })).toBeNull();
    expect((await flow.get())?.step).toBe('armed');
  });

  it('refreshes updatedAt only on step changes', async () => {
    let t = 1000;
    const flow = createFlowStore(memoryStore(), () => t);
    await flow.arm('s1');
    t = 5000;
    await flow.update('s1', { redirectedAt: 5000 });
    expect((await flow.get())?.updatedAt).toBe(1000);
    await flow.update('s1', { step: 'searching' });
    expect(await flow.get()).toMatchObject({ step: 'searching', updatedAt: 5000, redirectedAt: 5000 });
  });
});
```

`extension/src/tarbil/home.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { shouldRedirectHome } from './home';

const NOW = 10_000_000;

describe('shouldRedirectHome', () => {
  it('redirects a freshly armed flow after e-Devlet login', () => {
    expect(shouldRedirectHome({ submissionId: 's1', step: 'armed', updatedAt: NOW - 60_000 }, NOW)).toBe(true);
  });

  it('ignores an armed flow older than 30 minutes', () => {
    expect(shouldRedirectHome({ submissionId: 's1', step: 'armed', updatedAt: NOW - 31 * 60_000 }, NOW)).toBe(false);
  });

  it('ignores flows that are past the armed step', () => {
    expect(shouldRedirectHome({ submissionId: 's1', step: 'awaitingConfirm', updatedAt: NOW }, NOW)).toBe(false);
  });

  it('does not redirect again within a minute (no loop)', () => {
    expect(shouldRedirectHome({ submissionId: 's1', step: 'armed', updatedAt: NOW - 120_000, redirectedAt: NOW - 30_000 }, NOW)).toBe(false);
  });

  it('does nothing without a flow', () => {
    expect(shouldRedirectHome(null, NOW)).toBe(false);
  });
});
```

`extension/src/background/router.test.ts` içindeki `describe('router', () => {` bloğunun sonuna (son `it`'ten sonra) ekle:

```ts
  it('arms the TARBIL flow when Vetly selects a submission', async () => {
    const { router, session } = setup(async () => ({}));

    await router.handleExternal({ type: 'SELECT_SUBMISSION', vaccinationRecordId: 'v1' });

    expect(await session.get('tarbilFlow')).toMatchObject({ submissionId: 'sub-for-v1', step: 'armed' });
  });

  it('arms the TARBIL flow when the side panel sets the active submission', async () => {
    const { router, session } = setup(async () => ({}));

    await router.handle({ type: 'SET_ACTIVE', id: 's9' });

    expect(await session.get('tarbilFlow')).toMatchObject({ submissionId: 's9', step: 'armed' });
    expect(await session.get('activeSubmissionId')).toBe('s9');
  });
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/shared/flowStore.test.ts src/tarbil/home.test.ts src/background/router.test.ts`
Expected: FAIL — `./flowStore`, `./home` çözümlenemiyor; router'ın iki yeni testi `tarbilFlow` `undefined` olduğu için başarısız.

- [ ] **Step 3: Akış durumunu yaz**

`extension/src/shared/flowStore.ts`:

```ts
import type { KeyValueStore } from '../background/chromeStorage';

/**
 * TARBIL doldurma akisinin durumu (chrome.storage.session, tarayici kapaninca silinir).
 * Ana asi sayfasi ile ayri pencerede acilan PetVet arama penceresi bu kayit uzerinden haberlesir.
 */
export const FLOW_KEY = 'tarbilFlow';

export type FlowStep = 'armed' | 'filling' | 'searching' | 'transferred' | 'needsVet' | 'awaitingConfirm' | 'done' | 'error';

export interface FlowState {
  submissionId: string;
  step: FlowStep;
  updatedAt: number;
  message?: string;
  insertClickedAt?: number;
  redirectedAt?: number;
}

export interface FlowStore {
  get(): Promise<FlowState | null>;
  arm(submissionId: string): Promise<void>;
  update(submissionId: string, patch: Partial<Omit<FlowState, 'submissionId' | 'updatedAt'>>): Promise<FlowState | null>;
}

export function createFlowStore(store: KeyValueStore, now: () => number = Date.now): FlowStore {
  async function get(): Promise<FlowState | null> {
    return (await store.get<FlowState>(FLOW_KEY)) ?? null;
  }
  return {
    get,
    arm: (submissionId) => store.set(FLOW_KEY, { submissionId, step: 'armed', updatedAt: now() } satisfies FlowState),
    async update(submissionId, patch) {
      const current = await get();
      if (!current || current.submissionId !== submissionId) return null;
      // updatedAt adim zamanidir: yalniz adim degisince yenilenir (yonlendirme isareti bayat akisi tazelemesin).
      const next: FlowState = { ...current, ...patch, updatedAt: patch.step ? now() : current.updatedAt };
      await store.set(FLOW_KEY, next);
      return next;
    },
  };
}
```

- [ ] **Step 4: Arka planda akışı "armed" yap**

`extension/src/background/router.ts` dosyasında import satırlarına ekle:

```ts
import { createFlowStore } from '../shared/flowStore';
```

`export function createRouter({ api, tokens, outbox, session }: Deps) {` satırının hemen altına ekle:

```ts
  const flow = createFlowStore(session);
```

`case 'SET_ACTIVE':` bloğunu şununla değiştir (önce akış, sonra aktif kimlik: içerik betiği aktif kimlik değişince akışı okur):

```ts
          case 'SET_ACTIVE':
            await flow.arm(req.id);
            await session.set(ACTIVE_KEY, req.id);
            return { ok: true, data: null };
```

`case 'SELECT_SUBMISSION': {` bloğunu şununla değiştir:

```ts
          case 'SELECT_SUBMISSION': {
            const submission = await api.getByVaccination(req.vaccinationRecordId);
            await flow.arm(submission.id);
            await session.set(ACTIVE_KEY, submission.id);
            return { ok: true, data: { submissionId: submission.id } };
          }
```

- [ ] **Step 5: Yönlendirme kararını yaz**

`extension/src/tarbil/home.ts`:

```ts
import type { FlowState } from '../shared/flowStore';

export const ARMED_TTL_MS = 30 * 60_000;
export const REDIRECT_COOLDOWN_MS = 60_000;

/**
 * e-Devlet girisi hekimi TARBIL ana sayfasina dondurur (spec S12.2). Yalniz yeni baslatilmis (armed) bir aktarim
 * varsa asi sayfasina gecilir; bayat akis ya da bir dakika icinde ikinci yonlendirme (dongu) yapilmaz.
 */
export function shouldRedirectHome(state: FlowState | null, now: number): boolean {
  if (!state || state.step !== 'armed') return false;
  if (now - state.updatedAt > ARMED_TTL_MS) return false;
  if (state.redirectedAt !== undefined && now - state.redirectedAt < REDIRECT_COOLDOWN_MS) return false;
  return true;
}
```

- [ ] **Step 6: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx vitest run src/shared/flowStore.test.ts src/tarbil/home.test.ts src/background/router.test.ts`
Expected: PASS (3 + 5 + 7 = 15 test).

- [ ] **Step 7: Commit**

```bash
git add extension/src/shared/flowStore.ts extension/src/shared/flowStore.test.ts extension/src/background/router.ts extension/src/background/router.test.ts extension/src/tarbil/home.ts extension/src/tarbil/home.test.ts
git commit -m "feat(tarbil-ext): tarbilFlow akis durumu ve e-Devlet donusu karari"
```

---

### Task 5: Kart, kart içerikleri ve arama penceresi akışı

**Files:**
- Create: `extension/src/tarbil/card.ts`
- Create: `extension/src/tarbil/views.ts`
- Create: `extension/src/tarbil/searchFlow.ts`
- Test: `extension/src/tarbil/card.test.ts`
- Test: `extension/src/tarbil/searchFlow.test.ts`

**Interfaces:**
- Consumes: `PageBridge` (Task 1); `readSearchRows`, `pickAnimal`, `normalizeChip` (Task 2); `FlowStore` (Task 4); `BackgroundRequest`, `BackgroundResponse` (`shared/messages.ts`, mevcut).
- Produces (`card.ts`):
  - `type Tone = 'strong' | 'muted' | 'warn' | 'ok'`; `interface CardLine { text: string; tone?: Tone }`; `interface CardAction { id: string; label: string }`; `interface CardView { lines: CardLine[]; actions: CardAction[] }`
  - `interface Card { show(view: CardView): void; hide(): void; onAction(handler: (id: string) => void): void }`
  - `createCard(doc: Document, mode?: ShadowRootMode): Card & { root: ShadowRoot }`
- Produces (`views.ts`): `views` nesnesi — `submitted(s)`, `login(s)`, `idle(s)`, `noChip(s)`, `unsupportedSpecies(s)`, `progress(s, text)`, `popupBlocked(s)`, `needsVet(s, message)`, `wrongAnimal(s)`, `addProduct(s)`, `done(s, queued)`, `failed(s, code)`, `elsewhere(s)`, `popup(text, tone?)`. Eylem kimlikleri: `fill`, `petvet`, `manual`, `dismiss`, `open`.
- Produces (`searchFlow.ts`):
  - `type Send = <T>(req: BackgroundRequest) => Promise<BackgroundResponse<T>>`
  - `interface SearchDeps { bridge: PageBridge; flow: FlowStore; send: Send; doc: Document; card: Card; now: () => number }`
  - `SEARCH_HANDOFF_MS = 120_000`
  - `runSearchFlow(d: SearchDeps): Promise<void>`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/card.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { createCard } from './card';

describe('card', () => {
  it('renders untrusted text as text, never as markup', () => {
    const card = createCard(document, 'open');

    card.show({ lines: [{ text: '<img src=x onerror=alert(1)>' }], actions: [] });

    expect(card.root.querySelector('img')).toBeNull();
    expect(card.root.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  it('reports action clicks by id', () => {
    const card = createCard(document, 'open');
    const handler = vi.fn();
    card.onAction(handler);

    card.show({ lines: [], actions: [{ id: 'fill', label: 'Formu doldur' }] });
    (card.root.querySelector('button[data-action="fill"]') as HTMLButtonElement).click();

    expect(handler).toHaveBeenCalledWith('fill');
  });

  it('can be hidden', () => {
    const card = createCard(document, 'open');
    card.show({ lines: [{ text: 'x' }], actions: [] });
    card.hide();
    expect(card.root.host.isConnected).toBe(false);
  });
});
```

`extension/src/tarbil/searchFlow.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { memoryStore } from '../background/chromeStorage';
import type { BackgroundRequest } from '../shared/messages';
import { createFlowStore } from '../shared/flowStore';
import type { Submission } from '../shared/types';
import type { CardView } from './card';
import { runSearchFlow, type Send } from './searchFlow';

const SP = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_radGridAnimal_ctl00';
const CHIP = '900000000000001';

function grid(rows: [string, string, string?][]) {
  document.body.innerHTML = `<table id="${SP}"><thead><tr><th></th><th>Adı</th><th>Çip No</th><th>Pasaport No</th>
    <th>Tür</th><th>Irk</th><th>Cinsiyet</th><th>Renk</th><th>Doğum Tarihi</th><th>Durumu</th><th>Hayvan Sahibi</th>
    <th>Anne Çip No</th></tr></thead><tbody>${rows
      .map(([chip, status, mother = ''], i) => `<tr id="${SP}__${i}"><td><input type="checkbox" id="cb${i}"></td><td>Ad</td>
        <td>${chip}</td><td></td><td>Kedi</td><td></td><td></td><td></td><td></td><td>${status}</td><td></td><td>${mother}</td></tr>`)
      .join('')}</tbody></table>`;
}

const submission = { id: 's1', status: 'PENDING', microchipNumber: CHIP } as Submission;

async function setup(step: 'searching' | 'armed' = 'searching', updatedAt = 1000) {
  const flow = createFlowStore(memoryStore(), () => updatedAt);
  await flow.arm('s1');
  if (step !== 'armed') await flow.update('s1', { step });
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const send = (async (req: BackgroundRequest) => (req.type === 'GET_ACTIVE' ? { ok: true, data: submission } : { ok: true, data: null })) as Send;
  const shown: CardView[] = [];
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined };
  const deps = { bridge, flow, send, doc: document, card, now: () => updatedAt + 5000 };
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  return { deps, flow, calls, text };
}

describe('runSearchFlow', () => {
  it('searches by chip, checks the single alive match and transfers it', async () => {
    grid([[CHIP, 'CANLI']]);
    const { deps, flow, calls } = await setup();

    await runSearchFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchChip', 'checkRow', 'transfer']);
    expect(calls[1].args).toEqual({ chip: CHIP });
    expect(calls[2].args).toEqual({ checkboxId: 'cb0' });
    expect((await flow.get())?.step).toBe('transferred');
  });

  it('hands over to the vet when nothing matches', async () => {
    grid([['900000000000009', 'CANLI', CHIP]]);
    const { deps, flow, calls, text } = await setup();

    await runSearchFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchChip']);
    expect(await flow.get()).toMatchObject({ step: 'needsVet' });
    expect(text()).toContain('bulunamadı');
  });

  it('does not auto-select an animal that is not alive', async () => {
    grid([[CHIP, 'ÖLÜ']]);
    const { deps, calls, text } = await setup();

    await runSearchFlow(deps);

    expect(calls.map((c) => c.op)).not.toContain('checkRow');
    expect(text()).toContain('ÖLÜ');
  });

  it('stays out of the way when the vet opened the window by hand', async () => {
    grid([[CHIP, 'CANLI']]);
    const { deps, calls } = await setup('armed');

    await runSearchFlow(deps);

    expect(calls).toEqual([]);
  });
});
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/card.test.ts src/tarbil/searchFlow.test.ts`
Expected: FAIL — `./card`, `./searchFlow` çözümlenemiyor.

- [ ] **Step 3: Kartı yaz**

`extension/src/tarbil/card.ts`:

```ts
// TARBIL sayfasindaki "Vetly karti". Shadow DOM ile TARBIL CSS'inden yalitilir; icerik YALNIZ textContent ile yazilir.

export type Tone = 'strong' | 'muted' | 'warn' | 'ok';
export interface CardLine {
  text: string;
  tone?: Tone;
}
export interface CardAction {
  id: string;
  label: string;
}
export interface CardView {
  lines: CardLine[];
  actions: CardAction[];
}
export interface Card {
  show(view: CardView): void;
  hide(): void;
  onAction(handler: (id: string) => void): void;
}

// Renkler frontend/src/styles/tokens.css'ten (paper, text-ink, border, text-muted, warning-700, success-700).
const STYLE = `
  .card{font:13px/1.4 system-ui,sans-serif;background:#ffffff;color:#1b1229;border:1px solid #e9e5ef;border-radius:10px;
        box-shadow:0 20px 44px -18px rgba(24,15,36,.18);padding:10px;width:300px;display:flex;flex-direction:column;gap:6px}
  .strong{font-weight:600} .muted{color:#6e6579;font-size:12px} .warn{color:#a65022} .ok{color:#146245}
  button{border:1px solid #e9e5ef;background:#ffffff;color:#1b1229;border-radius:6px;padding:5px 8px;cursor:pointer}
  .row{display:flex;gap:6px;flex-wrap:wrap;align-items:center} .x{margin-left:auto;border:none;background:none;font-size:16px}
`;

export function createCard(doc: Document, mode: ShadowRootMode = 'closed'): Card & { root: ShadowRoot } {
  const host = doc.createElement('div');
  host.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2147483647;';
  const root = host.attachShadow({ mode });
  let handler: (id: string) => void = () => undefined;

  return {
    root,
    show(view) {
      root.replaceChildren();
      const style = doc.createElement('style');
      style.textContent = STYLE;
      const card = doc.createElement('div');
      card.className = 'card';

      const head = doc.createElement('div');
      head.className = 'row';
      const title = doc.createElement('strong');
      title.textContent = 'Vetly';
      const close = doc.createElement('button');
      close.className = 'x';
      close.textContent = '×';
      close.title = 'Kapat';
      close.addEventListener('click', () => host.remove());
      head.append(title, close);
      card.appendChild(head);

      for (const line of view.lines) {
        const div = doc.createElement('div');
        if (line.tone) div.className = line.tone;
        div.textContent = line.text;
        card.appendChild(div);
      }
      if (view.actions.length > 0) {
        const row = doc.createElement('div');
        row.className = 'row';
        for (const a of view.actions) {
          const b = doc.createElement('button');
          b.textContent = a.label;
          b.dataset.action = a.id;
          b.addEventListener('click', () => handler(a.id));
          row.appendChild(b);
        }
        card.appendChild(row);
      }
      root.append(style, card);
      if (!host.isConnected) doc.body.appendChild(host);
    },
    hide() {
      host.remove();
    },
    onAction(h) {
      handler = h;
    },
  };
}
```

- [ ] **Step 4: Kart içeriklerini yaz**

`extension/src/tarbil/views.ts`:

```ts
import type { Submission } from '../shared/types';
import type { CardAction, CardLine, CardView, Tone } from './card';

function trDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return m ? `${m[3]}.${m[2]}.${m[1]}` : iso;
}

function header(s: Submission): CardLine[] {
  return [
    { text: s.patientName, tone: 'strong' },
    { text: `${s.vaccineName}${s.lotNumber ? ` · Lot ${s.lotNumber}` : ''} · ${trDate(s.administeredDate)}`, tone: 'muted' },
  ];
}

const FALLBACK: CardAction[] = [
  { id: 'manual', label: 'Kaydedildi olarak işaretle' },
  { id: 'dismiss', label: 'Bildirilmeyecek' },
];

const view = (s: Submission, text: string, tone: Tone | undefined, actions: CardAction[] = FALLBACK): CardView => ({
  lines: [...header(s), { text, tone }],
  actions,
});

export const views = {
  submitted: (s: Submission): CardView =>
    view(s, `✓ Bu aşı ${new Date(s.submittedAt!).toLocaleDateString('tr-TR')} tarihinde TARBİL'e kaydedilmiş. Tekrar girmeyin.`, 'ok', []),
  login: (s: Submission): CardView =>
    view(
      s,
      'Aşı formu bulunamadı. TARBİL oturumunuz kapanmış olabilir: e-Devlet ile giriş yapın; girişten sonra aşı sayfasına kendimiz geçeceğiz.',
      'warn',
      [],
    ),
  idle: (s: Submission): CardView =>
    view(s, 'Uygulama tarihini ve türü girip hayvanı çip numarasıyla bulacağız.', 'muted', [{ id: 'fill', label: 'Formu doldur' }, ...FALLBACK]),
  noChip: (s: Submission): CardView =>
    view(s, "Çip numarası yok. Hayvan TARBİL'de kayıtlı değilse önce kimliklendirme gerekir; formu kendiniz doldurun.", 'warn'),
  unsupportedSpecies: (s: Submission): CardView =>
    view(s, `Otomatik doldurma yalnız kedi ve köpek için (${s.speciesName ?? 'tür bilinmiyor'}). Formu kendiniz doldurun.`, 'warn'),
  progress: (s: Submission, text: string): CardView => view(s, text, 'muted'),
  popupBlocked: (s: Submission): CardView =>
    view(
      s,
      'Hayvan arama penceresi açılmadı. Tarayıcı açılır pencereyi engellemiş olabilir: adres çubuğundaki simgeden TARBİL için açılır pencerelere izin verin ya da aşağıdaki butona basın.',
      'warn',
      [{ id: 'petvet', label: 'Arama penceresini aç' }, ...FALLBACK],
    ),
  needsVet: (s: Submission, message: string): CardView =>
    view(s, `${message} Hayvan forma eklenince devam edeceğiz.`, 'warn'),
  wrongAnimal: (s: Submission): CardView =>
    view(s, `Forma eklenen hayvanın çipi Vetly'deki çiple (${s.microchipNumber ?? '—'}) aynı değil. Yanlış satırı silip doğru hayvanı ekleyin.`, 'warn'),
  addProduct: (s: Submission): CardView => ({
    lines: [
      ...header(s),
      { text: '✓ Hayvan forma eklendi.', tone: 'ok' },
      {
        text: `Şimdi "Ürün Ekle"den ${s.vaccineName}${s.lotNumber ? ` (lot ${s.lotNumber})` : ''} aşısını stoktan seçin, kontrol edip Onayla'ya basın. Kaydı yakalayıp Vetly'ye işleyeceğiz.`,
      },
    ],
    actions: FALLBACK,
  }),
  done: (s: Submission, queued: boolean): CardView =>
    view(
      s,
      queued
        ? "✓ TARBİL'e kaydedildi. Vetly'ye şu an ulaşılamıyor; bağlantı gelince otomatik işlenecek."
        : "✓ TARBİL'e kaydedildi ve Vetly'de işaretlendi.",
      'ok',
      [],
    ),
  failed: (s: Submission, code: string): CardView =>
    view(s, `Otomatik doldurma durdu (${code}). Kalan adımları TARBİL'de kendiniz tamamlayabilirsiniz.`, 'warn', [
      { id: 'fill', label: 'Yeniden dene' },
      ...FALLBACK,
    ]),
  elsewhere: (s: Submission): CardView =>
    view(s, 'Bu aşıyı "Aşı Uygulama Belgesi Ekle" sayfasında dolduracağız.', 'muted', [{ id: 'open', label: 'Aşı sayfasını aç' }]),
  popup: (text: string, tone?: Tone): CardView => ({ lines: [{ text, tone }], actions: [] }),
};
```

- [ ] **Step 5: Arama penceresi akışını yaz**

`extension/src/tarbil/searchFlow.ts`:

```ts
import type { BackgroundRequest, BackgroundResponse } from '../shared/messages';
import type { FlowStore } from '../shared/flowStore';
import type { Submission } from '../shared/types';
import { normalizeChip, pickAnimal, readSearchRows } from './animalRows';
import type { PageBridge } from './bridge';
import type { Card } from './card';
import { views } from './views';

export type Send = <T>(req: BackgroundRequest) => Promise<BackgroundResponse<T>>;

export interface SearchDeps {
  bridge: PageBridge;
  flow: FlowStore;
  send: Send;
  doc: Document;
  card: Card;
  now: () => number;
}

/** Ana sayfa PetVet'e bastiktan sonra pencerenin bu sure icinde acilmasi beklenir; daha eski "searching" yok sayilir. */
export const SEARCH_HANDOFF_MS = 120_000;

const code = (e: unknown) => (e as { code?: string })?.code ?? 'UNKNOWN';

/**
 * PetVet hayvan arama penceresi (spec S3, S12.4): cip yazilir, Ara'ya basilir; cipi birebir eslesen TEK ve CANLI
 * satir varsa isaretlenip Transfer Et'e basilir. Diger her durumda karar hekime birakilir. Pencereyi hekim
 * kendisi actiysa (akis "searching" degil) hicbir sey yapilmaz.
 */
export async function runSearchFlow(d: SearchDeps): Promise<void> {
  const state = await d.flow.get();
  if (!state || state.step !== 'searching' || d.now() - state.updatedAt > SEARCH_HANDOFF_MS) return;
  const res = await d.send<Submission | null>({ type: 'GET_ACTIVE' });
  const s = res.ok ? res.data : null;
  const chip = normalizeChip(s?.microchipNumber);
  if (!s || s.id !== state.submissionId || !chip) return;

  const needsVet = async (message: string) => {
    await d.flow.update(s.id, { step: 'needsVet', message });
    d.card.show(views.popup(message, 'warn'));
  };

  d.card.show(views.popup('Vetly: çip numarasıyla aranıyor…', 'muted'));
  try {
    await d.bridge.call('ready');
    await d.bridge.call('searchChip', { chip });
  } catch (e) {
    await needsVet(`Arama yapılamadı (${code(e)}). Çip numarasını kendiniz aratın.`);
    return;
  }

  const pick = pickAnimal(readSearchRows(d.doc), chip);
  switch (pick.kind) {
    case 'none':
      await needsVet("Bu çiple TARBİL'de hayvan bulunamadı. Çipi kontrol edin; hayvan kayıtlı değilse önce kimliklendirme gerekir.");
      return;
    case 'many':
      await needsVet("Bu çiple birden fazla hayvan çıktı. Doğru satırı kendiniz işaretleyip Transfer Et'e basın.");
      return;
    case 'notAlive':
      await needsVet(`Hayvanın TARBİL'deki durumu "${pick.row.status ?? '—'}". Otomatik seçilmedi; kontrol edip kendiniz seçin.`);
      return;
    case 'one':
      if (!pick.row.checkboxId) {
        await needsVet("Satır işaretlenemedi. Hayvanı kendiniz işaretleyip Transfer Et'e basın.");
        return;
      }
      try {
        await d.bridge.call('checkRow', { checkboxId: pick.row.checkboxId });
      } catch (e) {
        await needsVet(`Satır işaretlenemedi (${code(e)}). Hayvanı kendiniz işaretleyip Transfer Et'e basın.`);
        return;
      }
      d.card.show(views.popup('Vetly: hayvan forma aktarılıyor…', 'muted'));
      // Transfer Et pencereyi kapatir; yanit gelmeyebilir. Durum once yazilir, ana sayfa sonucu tablodan dogrular.
      await d.flow.update(s.id, { step: 'transferred' });
      await d.bridge.call('transfer', undefined, 5000).catch(() => undefined);
  }
}
```

- [ ] **Step 6: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx vitest run src/tarbil/card.test.ts src/tarbil/searchFlow.test.ts`
Expected: PASS (3 + 4 = 7 test).

- [ ] **Step 7: Commit**

```bash
git add extension/src/tarbil/card.ts extension/src/tarbil/views.ts extension/src/tarbil/searchFlow.ts extension/src/tarbil/card.test.ts extension/src/tarbil/searchFlow.test.ts
git commit -m "feat(tarbil-ext): Vetly karti ve PetVet arama penceresi akisi"
```

---

### Task 6: Aşı sayfası akışı

**Files:**
- Create: `extension/src/tarbil/receiptFlow.ts`
- Test: `extension/src/tarbil/receiptFlow.test.ts`

**Interfaces:**
- Consumes: `PageBridge` (Task 1); `RECEIPT`, `bySuffix` (Task 2); `normalizeChip`, `readReceiptChips` (Task 2); `resolveAnimalType` (Task 2); `FlowStore`, `FlowState` (Task 4); `Card`, `views`, `Send` (Task 5).
- Produces:
  - `interface ReceiptDeps { bridge: PageBridge; flow: FlowStore; send: Send; doc: Document; card: Card; now: () => number; setTimer: (fn: () => void, ms: number) => void; observe: (cb: () => void) => () => void }`
  - `POPUP_WAIT_MS = 15_000`, `SUCCESS_WINDOW_MS = 120_000`
  - `createReceiptFlow(d: ReceiptDeps): { start(): Promise<void>; flowChanged(state: FlowState | null): void }`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/receiptFlow.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { memoryStore } from '../background/chromeStorage';
import { createFlowStore, type FlowStep } from '../shared/flowStore';
import type { BackgroundRequest } from '../shared/messages';
import type { Submission } from '../shared/types';
import type { CardView } from './card';
import { POPUP_WAIT_MS, createReceiptFlow } from './receiptFlow';
import type { Send } from './searchFlow';
import { ANIMAL_TYPE } from './selectors';

const P = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_';
const GRID = `${P}ReceiptAddOtherAnimal_RadOtherAnimal_ctl00`;
const CHIP = '900000000000001';

function page({ withDate = true } = {}) {
  document.body.innerHTML = `
    ${withDate ? `<input id="${P}dpApplicationDate">` : ''}
    <a id="${P}btnInsert"><input id="${P}btnInsert_input" type="button" value="Onayla"></a>
    <table id="${GRID}"><thead><tr><th></th><th style="display:none"></th><th>Sistem Küpe/Çip No</th></tr></thead><tbody></tbody></table>`;
}

function addAnimalRow(chip: string) {
  document.querySelector(`#${GRID} tbody`)!.insertAdjacentHTML('beforeend', `<tr id="${GRID}__0"><td></td><td></td><td>${chip}</td></tr>`);
}

function showSuccess() {
  document.body.insertAdjacentHTML('beforeend', '<div id="bodyCPH_ContentPlaceHolder1_UCVACCINENotification_pnlNotifiSuccess">Kaydedildi</div>');
}

const base = {
  id: 's1', vaccinationRecordId: 'v1', status: 'PENDING', patientName: 'Pamuk', microchipNumber: CHIP,
  speciesId: null, speciesName: 'Kedi', breedName: null, sex: null, birthDate: null, vaccineName: 'Kuduz Aşısı',
  lotNumber: 'L1', administeredDate: '2026-10-03', submittedAt: null, confirmationMethod: null, tarbilReference: null,
  vaccineKey: 'kuduz aşisi', vaccineMapping: null, speciesMapping: null,
} satisfies Submission;

async function setup(sub: Partial<Submission> = {}, step: FlowStep | null = 'armed', extra: Record<string, number> = {}) {
  let t = 100_000;
  const now = () => t;
  const flow = createFlowStore(memoryStore(), now);
  if (step) {
    await flow.arm('s1');
    if (step !== 'armed' || Object.keys(extra).length) await flow.update('s1', { step, ...extra });
  }
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const sent: BackgroundRequest[] = [];
  const submission = { ...base, ...sub };
  const send = (async (req: BackgroundRequest) => {
    sent.push(req);
    return req.type === 'GET_ACTIVE' ? { ok: true, data: submission } : { ok: true, data: submission };
  }) as Send;
  const shown: CardView[] = [];
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined };
  const timers: (() => void)[] = [];
  let observer: () => void = () => undefined;
  const receipt = createReceiptFlow({
    bridge, flow, send, doc: document, card, now,
    setTimer: (fn) => { timers.push(fn); },
    observe: (cb) => { observer = cb; return () => undefined; },
  });
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  return { receipt, flow, calls, sent, shown, timers, text, mutate: () => observer(), advance: (ms: number) => { t += ms; } };
}

describe('receiptFlow', () => {
  it('fills date and species, then opens the PetVet search', async () => {
    page();
    const { receipt, calls, flow } = await setup();

    await receipt.start();

    expect(calls.map((c) => c.op)).toEqual(['ready', 'setDate', 'selectAnimalType', 'clickPetVet']);
    expect(calls[1].args).toEqual({ iso: '2026-10-03' });
    expect(calls[2].args).toEqual({ value: ANIMAL_TYPE.CAT });
    expect((await flow.get())?.step).toBe('searching');
  });

  it('does nothing on the page when the patient has no chip', async () => {
    page();
    const { receipt, calls, text } = await setup({ microchipNumber: null });

    await receipt.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('Çip numarası yok');
  });

  it('does nothing on the page for unsupported species', async () => {
    page();
    const { receipt, calls, text } = await setup({ speciesName: 'Tavşan' });

    await receipt.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('yalnız kedi ve köpek');
  });

  it('warns instead of filling an already submitted vaccination', async () => {
    page();
    const { receipt, calls, text } = await setup({ status: 'SUBMITTED', submittedAt: '2026-10-02T10:00:00Z' });

    await receipt.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('Tekrar girmeyin');
  });

  it('asks for e-Devlet login when the form is missing', async () => {
    page({ withDate: false });
    const { receipt, calls, text } = await setup();

    await receipt.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('e-Devlet');
  });

  it('waits for the vet when the flow is not armed', async () => {
    page();
    const { receipt, calls, shown } = await setup({}, null);

    await receipt.start();

    expect(calls).toEqual([]);
    expect(shown.at(-1)?.actions.map((a) => a.id)).toContain('fill');
  });

  it('moves to awaiting confirmation when the matching animal lands on the form', async () => {
    page();
    const { receipt, flow, mutate, text } = await setup({}, 'transferred');
    await receipt.start();

    addAnimalRow(CHIP);
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('awaitingConfirm'));
    expect(text()).toContain('Ürün Ekle');
  });

  it('warns when a different animal lands on the form', async () => {
    page();
    const { receipt, flow, mutate, text } = await setup({}, 'needsVet');
    await receipt.start();

    addAnimalRow('900000000000777');
    mutate();

    await vi.waitFor(() => expect(text()).toContain('aynı değil'));
    expect((await flow.get())?.step).toBe('needsVet');
  });

  it('marks the vaccination submitted when success follows an Onayla click', async () => {
    page();
    const { receipt, sent, flow, mutate } = await setup({}, 'awaitingConfirm');
    await receipt.start();

    (document.getElementById(`${P}btnInsert_input`) as HTMLInputElement).click();
    await vi.waitFor(async () => expect((await flow.get())?.insertClickedAt).toBeDefined());
    showSuccess();
    mutate();

    await vi.waitFor(() =>
      expect(sent).toContainEqual({ type: 'MARK_SUBMITTED', id: 's1', method: 'AUTO', tarbilReference: null }),
    );
    expect((await flow.get())?.step).toBe('done');
  });

  it('does not mark submitted when success appears without an Onayla click', async () => {
    page();
    const { receipt, sent, mutate } = await setup({}, 'awaitingConfirm');
    await receipt.start();

    showSuccess();
    mutate();
    await new Promise((r) => setTimeout(r, 20));

    expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(false);
  });

  it('catches success after a full page reload that followed an Onayla click', async () => {
    page();
    showSuccess();
    const { receipt, sent } = await setup({}, 'awaitingConfirm', { insertClickedAt: 99_000 });

    await receipt.start();

    await vi.waitFor(() => expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(true));
  });

  it('offers to reopen the search window when the popup never picks up', async () => {
    page();
    const { receipt, timers, shown, advance } = await setup();
    await receipt.start();

    advance(POPUP_WAIT_MS);
    timers.forEach((fn) => fn());

    await vi.waitFor(() => expect(shown.at(-1)?.actions.map((a) => a.id)).toContain('petvet'));
  });

  it('stops and reports when a page step fails', async () => {
    page();
    const flow = createFlowStore(memoryStore(), () => 100_000);
    await flow.arm('s1');
    const shown: CardView[] = [];
    const receipt = createReceiptFlow({
      bridge: {
        call: async (op: string) => {
          if (op === 'selectAnimalType') throw Object.assign(new Error('x'), { code: 'OPTION_NOT_FOUND' });
          return undefined as never;
        },
      },
      flow,
      send: (async () => ({ ok: true, data: base })) as Send,
      doc: document,
      card: { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined },
      now: () => 100_000,
      setTimer: () => undefined,
      observe: () => () => undefined,
    });

    await receipt.start();

    expect(await flow.get()).toMatchObject({ step: 'error', message: 'OPTION_NOT_FOUND' });
    expect(shown.at(-1)?.actions.map((a) => a.id)).toContain('fill');
  });
});
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/receiptFlow.test.ts`
Expected: FAIL — `./receiptFlow` çözümlenemiyor.

- [ ] **Step 3: Aşı sayfası akışını yaz**

`extension/src/tarbil/receiptFlow.ts`:

```ts
import type { FlowState, FlowStore } from '../shared/flowStore';
import type { Submission } from '../shared/types';
import { normalizeChip, readReceiptChips } from './animalRows';
import type { PageBridge } from './bridge';
import type { Card } from './card';
import type { Send } from './searchFlow';
import { RECEIPT, bySuffix } from './selectors';
import { resolveAnimalType } from './species';
import { views } from './views';

export interface ReceiptDeps {
  bridge: PageBridge;
  flow: FlowStore;
  send: Send;
  doc: Document;
  card: Card;
  now: () => number;
  setTimer: (fn: () => void, ms: number) => void;
  observe: (cb: () => void) => () => void;
}

/** PetVet'e basildiktan sonra arama penceresinin akisi devralmasi icin beklenen sure. */
export const POPUP_WAIT_MS = 15_000;
/** Onayla tiklamasindan sonra basari panelinin "bu kayda ait" sayilacagi sure (onay penceresi dahil). */
export const SUCCESS_WINDOW_MS = 120_000;

const WAITING_FOR_ANIMAL = ['searching', 'transferred', 'needsVet'];
const code = (e: unknown) => (e as { code?: string })?.code ?? 'UNKNOWN';

/**
 * "Asi Uygulama Belgesi Ekle" sayfasi (spec S12.6): tarih -> tur -> PetVet aramasi; hayvanin forma eklendigini
 * cipten dogrular; hekim urunu ekleyip Onayla'ya basinca basariyi yakalar. Onayla'ya ASLA basmaz.
 */
export function createReceiptFlow(d: ReceiptDeps) {
  let sub: Submission | null = null;
  let stopObserving: (() => void) | null = null;
  let finishing = false;

  async function current(): Promise<FlowState | null> {
    const st = await d.flow.get();
    return st && sub && st.submissionId === sub.id ? st : null;
  }

  async function start(): Promise<void> {
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
    if (sub.status === 'SUBMITTED') {
      d.card.show(views.submitted(sub));
      return;
    }
    if (!d.doc.querySelector(bySuffix(RECEIPT.date))) {
      d.card.show(views.login(sub));
      return;
    }
    const st = await current();
    if (st?.step === 'armed') return fill();
    if (st && (WAITING_FOR_ANIMAL.includes(st.step) || st.step === 'awaitingConfirm')) {
      if (st.step !== 'awaitingConfirm') d.card.show(views.progress(sub, 'Hayvanın forma eklenmesi bekleniyor…'));
      else d.card.show(views.addProduct(sub));
      watch();
      return;
    }
    d.card.show(views.idle(sub));
  }

  async function fill(): Promise<void> {
    const s = sub!;
    const chip = normalizeChip(s.microchipNumber);
    const animalType = resolveAnimalType(s);
    if (!chip) {
      d.card.show(views.noChip(s));
      return;
    }
    if (!animalType) {
      d.card.show(views.unsupportedSpecies(s));
      return;
    }
    await d.flow.update(s.id, { step: 'filling' });
    try {
      d.card.show(views.progress(s, 'Uygulama tarihi ve tür giriliyor…'));
      await d.bridge.call('ready');
      await d.bridge.call('setDate', { iso: s.administeredDate });
      await d.bridge.call('selectAnimalType', { value: animalType });
      await openSearch();
    } catch (e) {
      await failed(e);
      return;
    }
    watch();
  }

  async function openSearch(): Promise<void> {
    const s = sub!;
    if (!(await current())) await d.flow.arm(s.id);
    await d.flow.update(s.id, { step: 'searching' });
    d.card.show(views.progress(s, 'PetVet arama penceresinde çip numarasıyla aranıyor…'));
    await d.bridge.call('clickPetVet');
    d.setTimer(() => {
      void (async () => {
        const st = await current();
        if (st?.step === 'searching' && d.now() - st.updatedAt >= POPUP_WAIT_MS) d.card.show(views.popupBlocked(s));
      })();
    }, POPUP_WAIT_MS);
  }

  async function failed(e: unknown): Promise<void> {
    const s = sub!;
    await d.flow.update(s.id, { step: 'error', message: code(e) });
    d.card.show(views.failed(s, code(e)));
  }

  function watch(): void {
    if (stopObserving) return;
    stopObserving = d.observe(() => void evaluate());
    void evaluate();
  }

  async function evaluate(): Promise<void> {
    const s = sub;
    if (!s || finishing) return;
    const st = await current();
    if (!st) return;
    if (st.step === 'awaitingConfirm') {
      const clickedRecently = st.insertClickedAt !== undefined && d.now() - st.insertClickedAt <= SUCCESS_WINDOW_MS;
      if (clickedRecently && d.doc.querySelector(bySuffix(RECEIPT.successPanel))) await confirm(s);
      return;
    }
    if (!WAITING_FOR_ANIMAL.includes(st.step)) return;
    const chips = readReceiptChips(d.doc);
    if (chips.includes(normalizeChip(s.microchipNumber))) {
      await d.flow.update(s.id, { step: 'awaitingConfirm' });
      d.card.show(views.addProduct(s));
    } else if (chips.length > 0) {
      d.card.show(views.wrongAnimal(s));
    }
  }

  async function confirm(s: Submission): Promise<void> {
    finishing = true;
    const r = await d.send<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'AUTO', tarbilReference: null });
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

  // Hekimin Onayla tiklamasini fark et (yakalama asamasinda; TARBIL'in kendi isleyicisine dokunmadan).
  d.doc.addEventListener(
    'click',
    (e) => {
      const target = e.target as Element | null;
      if (!target?.closest || !RECEIPT.insertButtons.some((suffix) => target.closest(bySuffix(suffix)))) return;
      void (async () => {
        const st = await current();
        if (st?.step === 'awaitingConfirm') await d.flow.update(st.submissionId, { insertClickedAt: d.now() });
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
          await d.flow.arm(s.id);
          await fill();
          return;
        case 'petvet':
          try {
            await openSearch();
            watch();
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
    /** Arama penceresi akis durumunu degistirdiginde (content.ts storage dinleyicisinden). */
    flowChanged(state: FlowState | null): void {
      const s = sub;
      if (!s || !state || state.submissionId !== s.id) return;
      if (state.step === 'needsVet') d.card.show(views.needsVet(s, state.message ?? ''));
      else if (state.step === 'transferred') d.card.show(views.progress(s, 'Hayvan forma aktarılıyor…'));
      if (WAITING_FOR_ANIMAL.includes(state.step)) watch();
    },
  };
}
```

- [ ] **Step 4: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx vitest run src/tarbil/receiptFlow.test.ts`
Expected: PASS (13 test).

- [ ] **Step 5: Tip kontrolü ve tüm testler**

Run: `cd extension && npx tsc --noEmit && npm test`
Expected: tip hatası yok; tüm testler PASS.

- [ ] **Step 6: Commit**

```bash
git add extension/src/tarbil/receiptFlow.ts extension/src/tarbil/receiptFlow.test.ts
git commit -m "feat(tarbil-ext): asi sayfasi akisi - doldurma, cip dogrulama, Onayla sonrasi yakalama"
```

---

### Task 7: Bağlama, yan panel metni, dokümantasyon ve elle kabul testi

**Files:**
- Modify (yeniden yaz): `extension/src/tarbil/content.ts`
- Modify: `extension/src/sidepanel/SubmissionCard.tsx`
- Modify: `extension/README.md`
- Modify: `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md`

**Interfaces:**
- Consumes: hepsi (Task 1–6): `createPageBridge`, `pageKind`, `VACCINE_PAGE_URL`, `createFlowStore`, `FLOW_KEY`, `chromeSessionStore`, `createCard`, `views`, `runSearchFlow`, `createReceiptFlow`, `shouldRedirectHome`, `Send`.
- Produces: `dist/content.js` (izole dünya giriş noktası).

- [ ] **Step 1: İçerik betiğini yeniden yaz**

`extension/src/tarbil/content.ts` (tümüyle değiştir):

```ts
// TARBIL icerik betigi (izole dunya): Vetly arka planiyla konusur, karti gosterir, akisi yonetir.
// Telerik islemleri page.js (MAIN dunya) uzerinden kopruyle yapilir. Sayfa turune gore:
//  - receipt: Asi Uygulama Belgesi Ekle -> doldurma akisi
//  - search : PetVet arama penceresi -> cip ile secim
//  - home   : e-Devlet donusu -> gerekiyorsa asi sayfasina gecis
//  - other  : bekleyen asi icin "Asi sayfasini ac" karti
import { chromeSessionStore } from '../background/chromeStorage';
import { FLOW_KEY, createFlowStore, type FlowState } from '../shared/flowStore';
import type { Submission } from '../shared/types';
import { createPageBridge } from './bridge';
import { createCard } from './card';
import { shouldRedirectHome } from './home';
import { createReceiptFlow } from './receiptFlow';
import { runSearchFlow, type Send } from './searchFlow';
import { VACCINE_PAGE_URL, pageKind } from './selectors';
import { views } from './views';

const send: Send = (req) => chrome.runtime.sendMessage(req);
const flow = createFlowStore(chromeSessionStore());
const card = createCard(document);

async function showElsewhere(): Promise<void> {
  const res = await send<Submission | null>({ type: 'GET_ACTIVE' });
  if (!res.ok || !res.data || res.data.status !== 'PENDING') return;
  card.onAction((id) => {
    if (id === 'open') location.assign(VACCINE_PAGE_URL);
  });
  card.show(views.elsewhere(res.data));
}

async function home(): Promise<void> {
  const state = await flow.get();
  const now = Date.now();
  if (state && shouldRedirectHome(state, now)) {
    await flow.update(state.submissionId, { redirectedAt: now });
    location.assign(VACCINE_PAGE_URL);
    return;
  }
  await showElsewhere();
}

switch (pageKind(location)) {
  case 'receipt': {
    const receipt = createReceiptFlow({
      bridge: createPageBridge(window),
      flow,
      send,
      doc: document,
      card,
      now: Date.now,
      setTimer: (fn, ms) => {
        setTimeout(fn, ms);
      },
      observe: (cb) => {
        const mo = new MutationObserver(cb);
        mo.observe(document.body, { childList: true, subtree: true });
        return () => mo.disconnect();
      },
    });
    void receipt.start();
    chrome.storage.session.onChanged.addListener((changes) => {
      if ('activeSubmissionId' in changes) void receipt.start();
      else if (FLOW_KEY in changes) receipt.flowChanged((changes[FLOW_KEY].newValue as FlowState | undefined) ?? null);
    });
    break;
  }
  case 'search':
    void runSearchFlow({ bridge: createPageBridge(window), flow, send, doc: document, card, now: Date.now });
    break;
  case 'home':
    void home();
    break;
  default:
    void showElsewhere();
}
```

- [ ] **Step 2: Yan panel metnini güncelle**

`extension/src/sidepanel/SubmissionCard.tsx` içinde `setActive` fonksiyonundaki not satırını değiştir:

```tsx
    setNote("TARBİL'de \"Aşı Uygulama Belgesi Ekle\" sayfası açıksa form hemen doldurulur; değilse o sayfayı açın.");
```

ve buton etiketini değiştir:

```tsx
        <button onClick={setActive} disabled={busy}>TARBİL'de doldur</button>
```

- [ ] **Step 3: Derle, tip kontrolü ve tüm testler**

Run: `cd extension && npm run build:dev && npm test`
Expected: derleme hatasız; `dist/page.js` ve `dist/content.js` var; Vitest tüm testler PASS (yaklaşık 15 + 4 + 17 + 12 + 10 + 7 + 13 = 78).

- [ ] **Step 4: Üretim paketinde kişisel veri günlüğü olmadığını kontrol et**

Run: `cd extension && grep -c "console\." dist/content.js dist/page.js`
Expected: her iki dosya için `0`.

- [ ] **Step 5: README'yi güncelle**

`extension/README.md` sonuna ekle:

```markdown
## TARBİL'de otomatik doldurma (Faz 2a)

1. Vetly'de aşının yanındaki **TARBİL'e aktar**'a basın (ya da yan panelde **TARBİL'de doldur**). TARBİL "Aşı Uygulama Belgesi Ekle" sayfası açılır.
2. TARBİL oturumu kapalıysa e-Devlet ile giriş yapın; giriş sonrası eklenti aşı sayfasına kendisi geçer.
3. Eklenti uygulama tarihini ve türü (kedi/köpek) girer, **PetVet'ten Hayvan Ara ve Ekle** penceresinde çip numarasıyla arar; çipi birebir eşleşen tek ve `CANLI` hayvanı forma aktarır.
4. Siz **Ürün Ekle**'den aşıyı stoktan seçip kontrol eder ve **Onayla**'ya basarsınız. Eklenti Onayla'ya asla basmaz.
5. TARBİL kaydı onaylayınca eklenti aşıyı Vetly'de "gönderildi" olarak işaretler. Yakalayamazsa kartta **Kaydedildi olarak işaretle** her zaman var.

**Açılır pencere izni:** Arama penceresi açılmazsa Chrome adres çubuğundaki engellenen pencere simgesinden `hbsapp.tarbil.gov.tr` için açılır pencerelere izin verin.

Teknik not: `page.js` sayfanın kendi dünyasında (`world: "MAIN"`) çalışır ve yalnız Telerik bileşenlerini tetikler; Vetly API'sine ve eklenti anahtarına erişimi yoktur. TARBİL'e özgü tüm id'ler `src/tarbil/selectors.ts`'tedir.
```

- [ ] **Step 6: Spec'i güncelle**

`docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md` Bölüm 6'daki `tarbil/` ağacını şununla değiştir:

```
    └── tarbil/
        ├── content.ts          izole dünya: sayfa türüne göre yönlendirme
        ├── selectors.ts        TARBİL'e özgü TÜM id'ler/yollar/değerler (tek dosya)
        ├── bridge.ts           izole ↔ MAIN postMessage köprüsü
        ├── receiptFlow.ts      aşı sayfası: tarih, tür, PetVet, çip doğrulama, başarı yakalama
        ├── searchFlow.ts       arama penceresi: çiple ara, tek+CANLI satırı aktar
        ├── animalRows.ts / species.ts / home.ts / card.ts / views.ts
        └── page/               MAIN dünya (page.js)
            ├── telerik.ts      $find, postback bekleme, tarih/combobox/buton
            ├── ops.ts          köprü komutları (Onayla/Ürün Ekle/çıkış komutu YOK)
            └── main.ts
```

ve Bölüm 12.6'nın sonuna ekle:

```markdown
- **Uygulama (2026-10-03):** `docs/superpowers/plans/2026-10-03-tarbil-eklenti-faz2a.md`. Ana sayfa ile arama penceresi `chrome.storage.session`'daki `tarbilFlow` durumu (`armed → filling → searching → transferred|needsVet → awaitingConfirm → done`) üzerinden haberleşir. Başarı yalnız hekimin Onayla tıklamasından sonraki 120 sn içinde görünen başarı paneliyle sayılır.
```

- [ ] **Step 7: Commit**

```bash
git add extension/src/tarbil/content.ts extension/src/sidepanel/SubmissionCard.tsx extension/README.md docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md
git commit -m "feat(tarbil-ext): Faz 2a icerik betigi baglantisi, README ve spec"
```

- [ ] **Step 8: Elle kabul testi (hekimle, gerçek TARBİL'de — kod değişikliği yok)**

Önce `npm run build:dev`, sonra `chrome://extensions`'ta eklentiyi **Yeniden yükle**. **Onayla'ya yalnız gerçekten yapılmış bir aşı için basılır; deneme kaydı açılmaz.**

1. Vetly'de çipli bir kedi aşısında **TARBİL'e aktar** → aşı sayfası yeni sekmede açılır, kart görünür.
2. Kart "Uygulama tarihi ve tür giriliyor…" → tarih Vetly'deki tarih, tür "Kedi" olur.
3. PetVet arama penceresi açılır, çip yazılır, Ara'ya basılır, satır işaretlenir, Transfer Et'e basılır, pencere kapanır. (Pencere açılmazsa: kartta "Arama penceresini aç" çıkmalı; site için açılır pencere izni verilip tekrar denenir.)
4. Ana sayfada hayvan tabloya eklenir; kart "✓ Hayvan forma eklendi. Şimdi Ürün Ekle'den…" der.
5. Yanlış hayvan senaryosu (zorunlu değil): forma başka çipli hayvan eklenirse kart "aynı değil" uyarısı verir.
6. Gerçek aşıda hekim ürünü ekleyip Onayla'ya basar → kart "✓ TARBİL'e kaydedildi ve Vetly'de işaretlendi"; Vetly Ayarlar > Entegrasyonlar'da kayıt "gönderildi (otomatik)". **Kart bunu demezse** başarı panelinin id'si farklıdır: o yanıtı `Desktop\tarbil\05_onayla.txt` olarak kaydet, maskeleme betiğinden geçirip `RECEIPT.successPanel` düzeltilir; bu sırada kayıt kartın **Kaydedildi olarak işaretle** butonuyla işaretlenir.
7. Oturum kapalıyken **TARBİL'e aktar** → e-Devlet girişi → TARBİL ana sayfası açılınca eklenti aşı sayfasına kendisi geçer (bir kez; döngü yok).
8. Çipsiz hasta ve kedi/köpek dışı tür: kart nedenini söyler, sayfada hiçbir alan değişmez.

Sonuçları (geçti/kaldı, kalanların kartta ne yazdığı) ledger'a ve son mesaja yaz; kişisel veri yazma.
