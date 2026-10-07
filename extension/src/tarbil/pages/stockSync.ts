import type { KeyValueStore } from '../../background/chromeStorage';
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
  /** Son basarili gonderim (sistem basina) burada tutulur; sonraki ziyarette kart kisa gorunur. */
  store: KeyValueStore;
  now: () => number;
}

const SYSTEM: Record<StockKind, StockSystem> = { vaccineStock: 'HBSAPP_VACCINE', medicineStock: 'VETILAC_MEDICINE' };
const SEND_ACTION = [{ id: 'sendStock', label: "TARBİL stoğunu Vetly'ye gönder" }];
const RESEND_ACTION = [{ id: 'sendStock', label: 'Yeniden gönder' }];
const LAST_UPLOAD_KEY = 'tarbilStockLastUpload';

interface LastUpload { at: number; lines: number }

const time = (ms: number) => new Date(ms).toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

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
      const all = (await d.store.get<Record<string, LastUpload>>(LAST_UPLOAD_KEY)) ?? {};
      await d.store.set(LAST_UPLOAD_KEY, { ...all, [SYSTEM[d.kind]]: { at: d.now(), lines: lines.length } });
      d.card.show(views.stock(`${lines.length} satır Vetly'ye gönderildi. Vetly > Stok > TARBİL Eşitleme bölümünden kontrol edip onaylayın.`, 'ok', RESEND_ACTION));
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
    /** Daha once gonderildiyse kisa kart (son gonderim + Yeniden gonder), yoksa aciklamali kart (2026-10-07). */
    async start(): Promise<void> {
      const last = ((await d.store.get<Record<string, LastUpload>>(LAST_UPLOAD_KEY)) ?? {})[SYSTEM[d.kind]];
      if (last) {
        d.card.show(views.stock(`Son gönderim ${time(last.at)} (${last.lines} satır).`, 'muted', RESEND_ACTION));
        return;
      }
      d.card.show(views.stock("Bu sayfadaki stoğu Vetly'ye aktarabilirsiniz (TARBİL'de yalnız arama yapılır).", 'muted', SEND_ACTION));
    },
  };
}
