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
