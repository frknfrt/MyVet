import { MEDICINE_STOCK, VACCINE_STOCK, bySuffix } from '../selectors';

export type StockKind = 'vaccineStock' | 'medicineStock';
/** Okunabilen stok tablolari: sayfa turleri + asi sayfasindaki "Parca Stok Ara" tablosu (2026-10-21). */
export type StockTable = StockKind | 'vaccinePartStock';

export interface StockRow {
  productName: string;
  presentation: string | null;
  lotNumber: string | null;
  expiryDate: string | null;
  quantity: number;
  openedQuantity: number | null;
}

interface Columns { product: string; presentation: string; lot: string; expiry: string; quantity: string; opened?: string }

// Basliklar 2026-10-04 canli okumasindan; tam esitlikle aranir ("Acilmis Kalan Miktar Son Kullanma Tarihi" karismasin).
const VACCINE_COLUMNS: Columns = { product: 'Aşı Adı', presentation: 'Takdim Şekli', lot: 'Seri Numarası', expiry: 'Son Kullanma Tarihi', quantity: 'Ürün Miktarı' };
const TABLES: Record<StockTable, { grid: string; cols: Columns }> = {
  vaccineStock: { grid: VACCINE_STOCK.grid, cols: VACCINE_COLUMNS },
  vaccinePartStock: { grid: VACCINE_STOCK.partGrid, cols: VACCINE_COLUMNS },
  medicineStock: {
    grid: MEDICINE_STOCK.grid,
    cols: { product: 'Ürün', presentation: 'Takdim Şekli', lot: 'Seri Numarası', expiry: 'Son Kullanma Tarihi', quantity: 'Miktar', opened: 'Açılmış Kalan Miktar' },
  },
};

const clean = (s: string | null | undefined) => (s ?? '').replace(/\s+/g, ' ').trim();

export function parseTrDate(s: string | null | undefined): string | null {
  const m = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(clean(s));
  return m ? `${m[3]}-${m[2]}-${m[1]}` : null;
}

export function parseTrNumber(s: string | null | undefined): number | null {
  const t = clean(s).replace(/\./g, '').replace(',', '.');
  if (!t) return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

function headerCells(doc: Document, table: HTMLTableElement): string[] {
  const separate = doc.getElementById(`${table.id}_Header`) as HTMLTableElement | null;
  const head = (separate ?? table).tHead?.rows;
  return head && head.length > 0 ? Array.from(head[head.length - 1].cells).map((c) => clean(c.textContent)) : [];
}

/** Bilinen sutunlardan biri yoksa (TARBIL ekrani degismis) bos liste doner; yanlis veri gonderilmez. */
export function readStockRows(doc: Document, table: StockTable): StockRow[] {
  return readStockTable(doc, table) ?? [];
}

/**
 * Tablo yoksa, sutunlari tanınmadıysa ya da bir satiri supheliyse null; tablo tanindi ama bossa []. Parca stok gibi
 * cogu klinikte bos olan tablolarda "bos" ile "okunamadi" ayrilsin diye (2026-10-21).
 */
export function readStockTable(doc: Document, which: StockTable): StockRow[] | null {
  const { grid, cols } = TABLES[which];
  const table = doc.querySelector<HTMLTableElement>(`table${bySuffix(grid)}`);
  if (!table) return null;
  const headers = headerCells(doc, table);
  const idx = (name: string | undefined) => (name ? headers.indexOf(name) : -1);
  const product = idx(cols.product);
  const presentation = idx(cols.presentation);
  const lot = idx(cols.lot);
  const expiry = idx(cols.expiry);
  const quantity = idx(cols.quantity);
  const opened = idx(cols.opened);
  if ([product, presentation, lot, expiry, quantity].some((i) => i < 0) || (cols.opened && opened < 0)) return null;
  const rows: StockRow[] = [];
  for (const r of Array.from(table.tBodies[0]?.rows ?? [])) {
    if (!r.id.startsWith(`${table.id}__`)) continue;
    const productName = clean(r.cells[product]?.textContent);
    if (!productName) continue;
    // Okunamayan miktar/tarih: TARBIL bicimi degismis olabilir. Tek satir bile supheliyse hicbiri gonderilmez
    // (0 olarak gonderilen miktar Vetly stogunu sifirlardi).
    const qty = parseTrNumber(r.cells[quantity]?.textContent);
    const expiryText = clean(r.cells[expiry]?.textContent);
    const expiryDate = parseTrDate(expiryText);
    const openedText = opened >= 0 ? clean(r.cells[opened]?.textContent) : '';
    const openedQuantity = openedText ? parseTrNumber(openedText) : null;
    if (qty === null || !Number.isInteger(qty) || qty < 0) return null;
    if (expiryText && !expiryDate) return null;
    if (openedText && openedQuantity === null) return null;
    rows.push({
      productName,
      presentation: clean(r.cells[presentation]?.textContent) || null,
      lotNumber: clean(r.cells[lot]?.textContent) || null,
      expiryDate,
      quantity: qty,
      openedQuantity,
    });
  }
  return rows;
}
