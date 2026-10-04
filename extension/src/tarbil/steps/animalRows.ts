import { RECEIPT, SEARCH, bySuffix } from '../selectors';

// Gizlilik: tablolardan YALNIZ cip, pasaport no, durum ve satir onay kutusunun id'si okunur; ad, sahip vb. hucrelere dokunulmaz.

export interface AnimalRow {
  rowId: string;
  chip: string;
  passport: string;
  status: string | null;
  checkboxId: string | null;
}

export type PickResult =
  | { kind: 'one'; row: AnimalRow }
  | { kind: 'none' }
  | { kind: 'many' }
  | { kind: 'notAlive'; row: AnimalRow };

export function normalizeChip(s: string | null | undefined): string {
  return (s ?? '').replace(/\D/g, '');
}

/** Pasaport no: bosluklar atilir, Turkce buyuk harf (TR-34 ab12 = TR-34AB12). */
export function normalizePassport(s: string | null | undefined): string {
  return (s ?? '').replace(/\s+/g, '').toLocaleUpperCase('tr-TR');
}

export interface AnimalKey {
  chip?: string | null;
  passport?: string | null;
}

const headerText = (s: string | null) => (s ?? '').replace(/\s+/g, ' ').trim().toLocaleLowerCase('tr-TR');

/** Sutun sirasi baslik metninden bulunur (gizli sutunlar baslikta da var); bulunamazsa bilinen sira. */
function columnIndex(table: HTMLTableElement, test: (header: string) => boolean, fallback: number): number {
  const head = table.tHead?.rows;
  const cells = head && head.length > 0 ? Array.from(head[head.length - 1].cells) : [];
  const i = cells.findIndex((c) => test(headerText(c.textContent)));
  return i >= 0 ? i : fallback;
}

function dataRows(table: HTMLTableElement): HTMLTableRowElement[] {
  return Array.from(table.tBodies[0]?.rows ?? []).filter((r) => r.id.startsWith(`${table.id}__`));
}

function findTable(doc: Document, suffix: string): HTMLTableElement | null {
  return doc.querySelector<HTMLTableElement>(`table${bySuffix(suffix)}`);
}

export function readSearchRows(doc: Document): AnimalRow[] {
  const table = findTable(doc, SEARCH.grid);
  if (!table) return [];
  const chipCol = columnIndex(table, (h) => h.includes('çip') && !h.includes('anne'), 2);
  const passportCol = columnIndex(table, (h) => h.includes('pasaport'), 3);
  const statusCol = columnIndex(table, (h) => h.startsWith('durum'), 9);
  return dataRows(table).map((r) => ({
    rowId: r.id,
    chip: normalizeChip(r.cells[chipCol]?.textContent),
    passport: normalizePassport(r.cells[passportCol]?.textContent),
    status: r.cells[statusCol]?.textContent?.trim() || null,
    checkboxId: r.querySelector<HTMLInputElement>('input[type="checkbox"]')?.id || null,
  }));
}

export function readReceiptChips(doc: Document): string[] {
  const table = findTable(doc, RECEIPT.animalGrid);
  if (!table) return [];
  const chipCol = columnIndex(table, (h) => h.includes('çip'), 2);
  return dataRows(table)
    .map((r) => normalizeChip(r.cells[chipCol]?.textContent))
    .filter((c) => c.length > 0);
}

/** Cip biliniyorsa yalniz cip ile, bilinmiyorsa pasaport no ile birebir eslesme. */
export function pickAnimal(rows: AnimalRow[], key: AnimalKey): PickResult {
  const chip = normalizeChip(key.chip);
  const passport = normalizePassport(key.passport);
  const matches = chip ? rows.filter((r) => r.chip === chip) : passport ? rows.filter((r) => r.passport === passport) : [];
  if (matches.length === 0) return { kind: 'none' };
  if (matches.length > 1) return { kind: 'many' };
  const row = matches[0];
  return (row.status ?? '').toLocaleUpperCase('tr-TR') === 'CANLI' ? { kind: 'one', row } : { kind: 'notAlive', row };
}
