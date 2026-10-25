import type { Submission } from '../../shared/types';
import { views } from '../core/views';
import { normalizeSerial, pickStockRow } from '../steps/productRows';
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
  // TARBIL aramasi sunucuda (Turkce harmanlama olabilir): Vetly'deki "ki7" yerine "KI7" aranir.
  const serial = normalizeSerial(line.lotNumber);

  const handOver = async (message: string) => {
    await d.flow.update(s.id, { step: 'awaitingConfirm', message });
    d.card.show(views.stockPopup(`${message} Satırı kendiniz seçin.`, 'warn'));
  };

  d.card.show(views.stockPopup(`Seri ${serial} aranıyor…`, 'muted'));
  try {
    await d.bridge.call('ready');
    await d.bridge.call('searchMedicineSerial', { serial, open: line.source === 'OPEN' });
  } catch (e) {
    await handOver(`Stokta arama yapılamadı (${code(e)}).`);
    return;
  }

  const pick = pickStockRow(readMedicinePopupRows(d.doc, line.source === 'OPEN'), { serial, productName: line.tarbilProductName, today: d.today() });
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
