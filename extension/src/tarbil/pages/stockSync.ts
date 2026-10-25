import type { KeyValueStore } from '../../background/chromeStorage';
import type { StockComparison, StockSnapshotLine, StockSystem } from '../../shared/types';
import type { PageBridge } from '../core/bridge';
import type { Card, CardLine } from '../core/card';
import { views } from '../core/views';
import type { Send } from '../steps/findAnimal';
import { readStockTable, type StockKind, type StockRow } from '../steps/stockRows';

export interface StockSyncDeps {
  bridge: PageBridge;
  send: Send;
  doc: Document;
  card: Card;
  kind: StockKind;
  /** Son basarili gonderim (sistem basina) burada tutulur; sonraki ziyarette kart kisa gorunur. */
  store: KeyValueStore;
  now: () => number;
}

const SYSTEM: Record<StockKind, StockSystem> = { vaccineStock: 'HBSAPP_VACCINE', medicineStock: 'VETILAC_MEDICINE' };
const SEND_ACTION = [{ id: 'sendStock', label: "TARBİL stoğunu Vetly'ye gönder" }];
const RESEND_ACTION = [{ id: 'sendStock', label: 'Yeniden gönder' }];
const LAST_UPLOAD_KEY = 'tarbilStockLastUpload';

interface LastUpload { at: number; lines: number }

/**
 * Okunan stok. Asi sayfasinda "Ara" (butun stok) ile "Parca Stok Ara" ayri tablolardir; ikisi tek gonderimde birlesir
 * (2026-10-21 kullanici karari: Vetly yalniz son gonderimi gosterir, ayri gonderimler birbirini gizlerdi).
 * part: undefined = sayfada parca stok yok (ilac); null = parca stok tablosu okunamadi.
 */
interface StockRead {
  lines: StockSnapshotLine[];
  main: number;
  part?: number | null;
}

const time = (ms: number) => new Date(ms).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

/**
 * TARBIL asi/ilac stok sayfasi (spec 2026-10-04 S13): hekim tiklayinca "Ara" + tum satirlar, tablo okunur, Vetly'ye
 * gonderilir. Vetly stoguna isleme hekimin Vetly Stok sayfasindaki onayiyla olur; TARBIL'de baska hicbir sey yapilmaz.
 * Sayfa acilinca (2026-10-07) tablo ayni yolla okunup Vetly'de KAYDEDILMEDEN karsilastirilir: fark varsa uyari karti,
 * yoksa kucuk "guncel" satiri; karsilastirma olmazsa eski kart (son gonderim ya da aciklama) gosterilir.
 * Asi sayfasinda "Parca Stok Ara" tablosu da okunur (2026-10-21); okunamazsa butun stok yine gonderilir, kart uyarir.
 */
export function createStockSync(d: StockSyncDeps) {
  let busy = false;
  const hasPart = d.kind === 'vaccineStock';

  /** null: butun stok tablosu tanınmadı; yanlis veri gonderilmez. */
  async function readTable(): Promise<StockRead | null> {
    await d.bridge.call('ready');
    await d.bridge.call('loadStockTable', { page: d.kind });
    // Butun stok, parca stok aramasindan once okunur (ikinci arama sayfayi yeniden cizer).
    const main = readStockTable(d.doc, d.kind);
    if (main === null) return null;
    if (!hasPart) return { lines: main, main: main.length };
    let part: StockRow[] | null;
    try {
      await d.bridge.call('loadVaccinePartStock');
      part = readStockTable(d.doc, 'vaccinePartStock');
    } catch {
      part = null;
    }
    const partLines = (part ?? []).map((l) => ({ ...l, partStock: true }));
    return { lines: [...main, ...partLines], main: main.length, part: part === null ? null : part.length };
  }

  /** Aşıda Vetly parça stoğu ancak tablo okunduysa eşitler (spec 2026-10-24); ilaçta alan yok. */
  function partFlag(r: StockRead): { partStockRead?: boolean } {
    return hasPart ? { partStockRead: r.part !== null && r.part !== undefined } : {};
  }

  function details(r: StockRead): CardLine[] {
    if (r.part === undefined) return [];
    if (r.part === null) {
      return [{ text: `Bütün stok ${r.main} satır. Parça stok okunamadı (TARBİL ekranı değişmiş olabilir); yalnız bütün stok kullanıldı.`, tone: 'warn' }];
    }
    return [{ text: `Bütün stok ${r.main} satır, parça stok ${r.part} satır.`, tone: 'muted' }];
  }

  async function compare(): Promise<{ c: StockComparison; read: StockRead } | null> {
    try {
      const read = await readTable();
      if (!read || read.lines.length === 0) return null;
      const res = await d.send<StockComparison>({ type: 'COMPARE_STOCK_SNAPSHOT', system: SYSTEM[d.kind], lines: read.lines, ...partFlag(read) });
      return res.ok ? { c: res.data, read } : null;
    } catch {
      return null;
    }
  }

  async function showFallback(): Promise<void> {
    const last = ((await d.store.get<Record<string, LastUpload>>(LAST_UPLOAD_KEY)) ?? {})[SYSTEM[d.kind]];
    if (last) {
      d.card.show(views.stock(`Son gönderim ${time(last.at)} (${last.lines} satır).`, 'muted', RESEND_ACTION));
      return;
    }
    const what = hasPart ? 'Bu sayfadaki stoğu (bütün stok ve parça stok)' : 'Bu sayfadaki stoğu';
    d.card.show(views.stock(`${what} Vetly'ye aktarabilirsiniz (TARBİL'de yalnız arama yapılır).`, 'muted', SEND_ACTION));
  }

  async function sendStock(): Promise<void> {
    if (busy) return;
    busy = true;
    try {
      d.card.show(views.stock('TARBİL stoğu okunuyor…', 'muted'));
      const read = await readTable();
      if (!read || read.lines.length === 0) {
        d.card.show(views.stock('Stok tablosu tanınmadı ya da boş. TARBİL ekranı değişmiş olabilir; Vetly ekibine haber verin.', 'warn', SEND_ACTION));
        return;
      }
      const res = await d.send<{ snapshotId: string }>({ type: 'UPLOAD_STOCK_SNAPSHOT', system: SYSTEM[d.kind], lines: read.lines, ...partFlag(read) });
      if (!res.ok) {
        d.card.show(views.stock(res.error, 'warn', SEND_ACTION));
        return;
      }
      const all = (await d.store.get<Record<string, LastUpload>>(LAST_UPLOAD_KEY)) ?? {};
      await d.store.set(LAST_UPLOAD_KEY, { ...all, [SYSTEM[d.kind]]: { at: d.now(), lines: read.lines.length } });
      d.card.show(
        views.stock(
          `${read.lines.length} satır Vetly'ye gönderildi. Vetly > Stok > TARBİL Eşitleme bölümünden kontrol edip onaylayın.`,
          'ok',
          RESEND_ACTION,
          details(read),
        ),
      );
    } catch (e) {
      // Kodsuz hata: cogunlukla eklenti yeniden yuklendi/guncellendi ve bu sekmedeki eski betik koptu (2026-10-21).
      const code = (e as { code?: string })?.code;
      d.card.show(
        code
          ? views.stock(`Stok okunamadı (${code}).`, 'warn', SEND_ACTION)
          : views.stock('Eklenti güncellenmiş ya da bağlantı kopmuş. Sayfayı yenileyip (F5) tekrar deneyin.', 'warn'),
      );
    } finally {
      busy = false;
    }
  }

  d.card.onAction((id) => {
    if (id === 'sendStock') void sendStock();
  });

  return {
    /** Sayfa acilinca otomatik karsilastirma; hekim gonderene kadar Vetly'de hicbir sey kaydedilmez (2026-10-07). */
    async start(): Promise<void> {
      if (busy) return;
      busy = true;
      try {
        d.card.show(views.stock('Vetly stoğuyla karşılaştırılıyor…', 'muted'));
        const result = await compare();
        if (!result) {
          await showFallback();
        } else if (result.c.newCount === 0 && result.c.quantityDiffersCount === 0) {
          d.card.show(views.stock('TARBİL stoğu Vetly ile güncel ✓', 'ok', [], details(result.read)));
        } else {
          d.card.show(views.stock(differenceText(result.c), 'warn', SEND_ACTION, details(result.read)));
        }
      } finally {
        busy = false;
      }
    },
  };
}

function differenceText(c: StockComparison): string {
  const parts: string[] = [];
  if (c.newCount > 0) parts.push(`Vetly'de olmayan ${c.newCount} ürün`);
  if (c.quantityDiffersCount > 0) parts.push(`Vetly'de miktarı farklı ${c.quantityDiffersCount} ürün`);
  return `TARBİL stoğunda ${parts.join(' ve ')} var.`;
}
