import { RECEIPT, VACCINE_STOCK_POPUP, bySuffix } from '../selectors';

// Gizlilik: yalniz urun adi, seri, son kullanma tarihi ve "Sec" baglantisinin id'si okunur (urun verisi, kisisel veri yok).

export interface StockPopupRow {
  productName: string;
  serial: string;
  expiryDate: string | null;
  linkId: string;
}

export type StockPick =
  | { kind: 'one'; row: StockPopupRow }
  | { kind: 'none' }
  | { kind: 'many' }
  | { kind: 'expired'; row: StockPopupRow }
  | { kind: 'nameMismatch'; row: StockPopupRow };

const clean = (s: string | null | undefined) => (s ?? '').replace(/\s+/g, ' ').trim();

export function normalizeSerial(s: string | null | undefined): string {
  return clean(s).replace(/\s+/g, '').toLocaleUpperCase('tr-TR');
}

const normalizeName = (s: string | null | undefined) => clean(s).toLocaleUpperCase('tr-TR');

/** "31.01.2027" ya da "31.01.2027 00:00:00" -> "2027-01-31". */
function isoDate(s: string | null | undefined): string | null {
  const m = /(\d{2})\.(\d{2})\.(\d{4})/.exec(clean(s));
  return m ? `${m[3]}-${m[2]}-${m[1]}` : null;
}

function headerIndex(table: HTMLTableElement, name: string, fallback: number): number {
  const rows = table.tHead?.rows;
  if (!rows) return fallback;
  for (const row of Array.from(rows)) {
    const i = Array.from(row.cells).findIndex((c) => clean(c.textContent) === name);
    if (i >= 0) return i;
  }
  return fallback;
}

export function readStockPopupRows(doc: Document): StockPopupRow[] {
  const table = doc.querySelector<HTMLTableElement>(`table${bySuffix(VACCINE_STOCK_POPUP.grid)}`);
  if (!table) return [];
  const name = headerIndex(table, 'Aşı Adı', 1);
  const serial = headerIndex(table, 'Seri Numarası', 3);
  const expiry = headerIndex(table, 'Son Kullanma Tarihi', 4);
  return Array.from(table.tBodies[0]?.rows ?? [])
    .filter((r) => r.id.startsWith(`${table.id}__`))
    .map((r) => ({
      productName: clean(r.cells[name]?.textContent),
      serial: normalizeSerial(r.cells[serial]?.textContent),
      expiryDate: isoDate(r.cells[expiry]?.textContent),
      linkId: r.querySelector<HTMLAnchorElement>('a[id$="SelectlinkButton"]')?.id ?? '',
    }))
    .filter((r) => r.serial && r.linkId);
}

/** Seri birebir; Vetly'de TARBIL urun adi varsa o da birebir (normalize). Tek eslesme ve SKT >= bugun ise secilir. */
export function pickStockRow(rows: StockPopupRow[], want: { serial: string; productName: string | null; today: string }): StockPick {
  const serial = normalizeSerial(want.serial);
  const matches = rows.filter((r) => r.serial === serial);
  if (matches.length === 0) return { kind: 'none' };
  if (matches.length > 1) return { kind: 'many' };
  const row = matches[0];
  if (want.productName && normalizeName(row.productName) !== normalizeName(want.productName)) return { kind: 'nameMismatch', row };
  if (row.expiryDate && row.expiryDate < want.today) return { kind: 'expired', row };
  return { kind: 'one', row };
}

/** Stoktan secilen urun TARBIL'de urun tablosunun thead'ine rgEditRow olarak cizilir (2026-10-04 canli). */
export function readProductEditRow(doc: Document): { serial: string } | null {
  const table = doc.querySelector<HTMLTableElement>(`table${bySuffix(RECEIPT.productGrid)}`);
  const edit = table?.tHead?.querySelector<HTMLTableRowElement>('tr.rgEditRow');
  if (!table || !edit) return null;
  const serial = headerIndex(table, 'Seri Numarası', 4);
  return { serial: normalizeSerial(edit.cells[serial]?.textContent) };
}
