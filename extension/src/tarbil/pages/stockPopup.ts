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
    case 'unknownExpiry':
      await handOver(`Seri ${serial} için son kullanma tarihi okunamadı.`);
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
