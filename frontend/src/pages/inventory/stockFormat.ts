/**
 * Kullanim birimi (spec 2026-10-24): ADET bolunmez; ML/G sise/tupten, FLAKON asi kutusundan kullanilir.
 * TABLET (spec 2026-10-25): kutudan tablet tablet; TARBİL'e tablet sayisi yazilir.
 */
export type StockUnit = 'ADET' | 'ML' | 'G' | 'FLAKON' | 'TABLET';

const CLOSED_NAME: Record<StockUnit, string> = { ADET: 'adet', ML: 'şişe', G: 'tüp', FLAKON: 'kutu', TABLET: 'kutu' };
const OPEN_NAME: Record<StockUnit, string> = { ADET: 'adet', ML: 'ml', G: 'g', FLAKON: 'flakon', TABLET: 'tablet' };

const num = (n: number) => n.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;
const unitOf = (unit: string | null | undefined): StockUnit => (unit && unit in CLOSED_NAME ? (unit as StockUnit) : 'ADET');

/** Kapalı adet ya da açık paketlerde kalan miktar var mı (açık şişesi kalan ürün de verilebilir). */
export function hasStock(i: { quantityOnHand: number; openedQuantity?: number | null; unit?: string | null }): boolean {
  return i.quantityOnHand > 0 || (i.unit !== 'ADET' && (i.openedQuantity ?? 0) > 0);
}

/** Reçete ve muayenede ondalıklı miktar girilen birimler. */
export function isDecimalUnit(unit: string | null | undefined): boolean {
  return unit === 'ML' || unit === 'G';
}

export function unitLabel(unit: string | null | undefined): string {
  return OPEN_NAME[unitOf(unit)];
}

/**
 * "0 şişe + 49 ml", "1 kutu + 19 flakon", "2 kutu + 8 tablet", "3 adet"; açık miktar bilinmiyorsa yalnız kapalı kısım.
 * ADET kalemde TARBİL'den açık miktar geldiyse o da görünür ("2 adet, 0,5 açık"; spec 2026-10-25 §4.4/13).
 */
export function formatStock(closed: number, opened: number | null, unit: string | null | undefined): string {
  const u = unitOf(unit);
  const head = `${num(closed)} ${CLOSED_NAME[u]}`;
  if (u === 'ADET') return opened ? `${head}, ${num(opened)} açık` : head;
  if (opened === null) return head;
  return `${head} + ${num(opened)} ${OPEN_NAME[u]}`;
}

/** Kullanım miktarı: "2 pipet (1 ml)", "2 tablet", "1,5 ml", "2 adet" (spec 2026-10-25 §4.4/12). */
export function formatAmount(amount: number, unit: string | null | undefined, pieceSize?: number | null, pieceLabel?: string | null): string {
  const u = unitOf(unit);
  if (u === 'ML' && pieceSize && pieceSize > 0) {
    return `${num(round2(amount / pieceSize))} ${pieceLabel ?? 'parça'} (${num(amount)} ml)`;
  }
  return `${num(amount)} ${OPEN_NAME[u]}`;
}

export interface InputMode {
  label: string;
  step: string;
  min: number;
  /** Ekranda girilen sayıyı stok/TARBİL birimine çevirir (pipet → ml). */
  toBase: (n: number) => number;
  fromBase: (n: number) => number;
}

/** Reçete/kullanım girişi: pipet (tam sayı, ml'ye çevrilir), tablet (tam sayı), ml/g (0,01), adet (tam sayı). */
export function inputModeFor(unit: string | null | undefined, pieceSize?: number | null, pieceLabel?: string | null): InputMode {
  const u = unitOf(unit);
  const same = (n: number) => n;
  if (u === 'ML' && pieceSize && pieceSize > 0) {
    return {
      label: `Miktar (${pieceLabel ?? 'parça'})`, step: '1', min: 1,
      toBase: (n) => round2(n * pieceSize), fromBase: (n) => Math.round(n / pieceSize),
    };
  }
  if (u === 'TABLET') return { label: 'Miktar (tablet)', step: '1', min: 1, toBase: same, fromBase: same };
  if (isDecimalUnit(u)) return { label: `Miktar (${OPEN_NAME[u]})`, step: '0.01', min: 0.01, toBase: same, fromBase: same };
  return { label: 'Adet', step: '1', min: 1, toBase: same, fromBase: same };
}

/** Açılmış ürün son kullanma tarihi: geçti → expired, 3 gün içinde → soon (spec 2026-10-25 §4.4/15). */
export function openedExpiryStatus(date: string | null | undefined, today: Date = new Date()): 'expired' | 'soon' | null {
  if (!date) return null;
  const [y, m, d] = date.split('-').map(Number);
  const expiry = new Date(y, m - 1, d).getTime();
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  const days = Math.round((expiry - start) / 86_400_000);
  if (days < 0) return 'expired';
  return days <= 3 ? 'soon' : null;
}

const signed = (n: number) => `${n < 0 ? '-' : '+'}${num(Math.abs(n))}`;

/**
 * Stok hareketi satırı: "-1 ml açık", "-1 şişe, +49 ml açık". Kapalı kısım hareket tipinden işaret alır;
 * açık kısım (openedDelta) kendi işaretiyle gelir. Adet kalemde ve eski hareketlerde yalnız sayı.
 */
export function formatMovement(type: 'IN' | 'OUT' | 'ADJUSTMENT', quantity: number, openedDelta: number | null | undefined,
  unit: string | null | undefined): string {
  const u = unitOf(unit);
  const opened = u === 'ADET' || !openedDelta ? null : openedDelta;
  const parts: string[] = [];
  if (quantity !== 0 || opened === null) {
    parts.push(`${type === 'OUT' ? '-' : '+'}${num(quantity)}${u === 'ADET' ? '' : ` ${CLOSED_NAME[u]}`}`);
  }
  if (opened !== null) parts.push(`${signed(opened)} ${OPEN_NAME[u]} açık`);
  return parts.join(', ');
}
