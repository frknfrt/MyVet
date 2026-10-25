import type { ConfirmedLine, PlannedLine, StockSource } from '../../shared/types';
import { MEDICINE_STOCK_POPUP, PRESCRIPTION_RECEIPT, bySuffix } from '../selectors';
import { headerIndex, normalizeSerial, readPopupRows, type StockPopupRow } from './productRows';

// Gizlilik: yalniz urun adi, seri ve adet okunur (urun verisi, kisisel veri yok).

export interface SavedStockLine {
  productName: string;
  serial: string;
  quantity: number | null;
  /** "Kullanılacak Miktarı" (ondalık) ve "Stok Tipi" (spec 2026-10-24); okunamazsa null. */
  amount?: number | null;
  source?: StockSource | null;
}

const clean = (s: string | null | undefined) => (s ?? '').replace(/\s+/g, ' ').trim();

/** open: "Açık Stok" listesi (spec 2026-10-24). */
export function readMedicinePopupRows(doc: Document, open = false): StockPopupRow[] {
  return readPopupRows(doc, open ? MEDICINE_STOCK_POPUP.openGrid : MEDICINE_STOCK_POPUP.grid, 'Ürün Adı');
}

function decimal(s: string): number | null {
  const m = /^(\d+(?:,\d{1,2})?)(?:\s*[a-zA-Z]+)?$/.exec(s);
  const n = m ? Number(m[1].replace(',', '.')) : Number.NaN;
  return Number.isFinite(n) && n > 0 ? n : null;
}

function stockSource(s: string): StockSource | null {
  return s === 'Açık Stok' ? 'OPEN' : s === 'Bütün Stok' ? 'WHOLE' : null;
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
  const amount = headerIndex(table, 'Kullanılacak Miktarı', 9);
  const type = headerIndex(table, 'Stok Tipi', 7);
  return Array.from(table.tBodies[0]?.rows ?? [])
    .filter((r) => r.id.startsWith(`${table.id}__`) && !r.classList.contains('rgEditRow'))
    .map((r) => {
      // Yalniz tam sayi ("1", "3 Adet", "1,00"); "2,50" ya da belirsiz "1.000" okunamaz sayilir (plan adedine duser, sisirilmez).
      const m = /^(\d+)(?:[.,]00?)?(?:\s*Adet)?$/i.exec(clean(r.cells[quantity]?.textContent));
      const n = m ? Number.parseInt(m[1], 10) : Number.NaN;
      return {
        productName: clean(r.cells[name]?.textContent),
        serial: normalizeSerial(r.cells[serial]?.textContent),
        quantity: Number.isInteger(n) && n > 0 ? n : null,
        amount: decimal(clean(r.cells[amount]?.textContent)),
        source: stockSource(clean(r.cells[type]?.textContent)),
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
    // TARBİL esas (spec 2026-10-24): okunan miktar ve stok tipi; okunamazsa plandan. Paketli kalemde adet 0 olabilir.
    const amount = l.amount ?? plan?.amount ?? (quantity || null);
    const source = l.source ?? plan?.source ?? null;
    if (!productName || quantity === null || !(quantity > 0 || (amount ?? 0) > 0)) return [];
    return [{ productName, serialNumber: l.serial, quantity, amount: amount ?? undefined, source }];
  });
}
