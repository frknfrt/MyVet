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
