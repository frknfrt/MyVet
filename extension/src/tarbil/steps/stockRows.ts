import { MEDICINE_STOCK, VACCINE_STOCK, bySuffix } from '../selectors';

export type StockKind = 'vaccineStock' | 'medicineStock';

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
const COLUMNS: Record<StockKind, Columns> = {
  vaccineStock: { product: 'Aşı Adı', presentation: 'Takdim Şekli', lot: 'Seri Numarası', expiry: 'Son Kullanma Tarihi', quantity: 'Ürün Miktarı' },
  medicineStock: { product: 'Ürün', presentation: 'Takdim Şekli', lot: 'Seri Numarası', expiry: 'Son Kullanma Tarihi', quantity: 'Miktar', opened: 'Açılmış Kalan Miktar' },
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
export function readStockRows(doc: Document, kind: StockKind): StockRow[] {
  const gridSuffix = kind === 'vaccineStock' ? VACCINE_STOCK.grid : MEDICINE_STOCK.grid;
  const table = doc.querySelector<HTMLTableElement>(`table${bySuffix(gridSuffix)}`);
  if (!table) return [];
  const headers = headerCells(doc, table);
  const cols = COLUMNS[kind];
  const idx = (name: string | undefined) => (name ? headers.indexOf(name) : -1);
  const product = idx(cols.product);
  const presentation = idx(cols.presentation);
  const lot = idx(cols.lot);
  const expiry = idx(cols.expiry);
  const quantity = idx(cols.quantity);
  const opened = idx(cols.opened);
  if ([product, presentation, lot, expiry, quantity].some((i) => i < 0) || (cols.opened && opened < 0)) return [];
  return Array.from(table.tBodies[0]?.rows ?? [])
    .filter((r) => r.id.startsWith(`${table.id}__`))
    .map((r) => ({
      productName: clean(r.cells[product]?.textContent),
      presentation: clean(r.cells[presentation]?.textContent) || null,
      lotNumber: clean(r.cells[lot]?.textContent) || null,
      expiryDate: parseTrDate(r.cells[expiry]?.textContent),
      quantity: Math.max(0, Math.round(parseTrNumber(r.cells[quantity]?.textContent) ?? 0)),
      openedQuantity: opened >= 0 ? parseTrNumber(r.cells[opened]?.textContent) : null,
    }))
    .filter((row) => row.productName.length > 0);
}
