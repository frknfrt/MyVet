/** Kullanim birimi (spec 2026-10-24): ADET bolunmez; ML/G sise/tupten, FLAKON asi kutusundan kullanilir. */
export type StockUnit = 'ADET' | 'ML' | 'G' | 'FLAKON';

const CLOSED_NAME: Record<StockUnit, string> = { ADET: 'adet', ML: 'şişe', G: 'tüp', FLAKON: 'kutu' };
const OPEN_NAME: Record<StockUnit, string> = { ADET: 'adet', ML: 'ml', G: 'g', FLAKON: 'flakon' };

const num = (n: number) => n.toLocaleString('tr-TR', { maximumFractionDigits: 2 });

/** Kapalı adet ya da açık paketlerde kalan miktar var mı (açık şişesi kalan ürün de verilebilir). */
export function hasStock(i: { quantityOnHand: number; openedQuantity?: number | null; unit?: string | null }): boolean {
  return i.quantityOnHand > 0 || (i.unit !== 'ADET' && (i.openedQuantity ?? 0) > 0);
}

/** Reçete ve muayenede ondalıklı miktar girilen birimler. */
export function isDecimalUnit(unit: string | null | undefined): boolean {
  return unit === 'ML' || unit === 'G';
}

export function unitLabel(unit: string | null | undefined): string {
  return OPEN_NAME[(unit as StockUnit) in OPEN_NAME ? (unit as StockUnit) : 'ADET'];
}

/** "0 şişe + 49 ml", "1 kutu + 19 flakon", "3 adet"; açık miktar bilinmiyorsa yalnız kapalı kısım. */
export function formatStock(closed: number, opened: number | null, unit: string | null | undefined): string {
  const u: StockUnit = unit && unit in CLOSED_NAME ? (unit as StockUnit) : 'ADET';
  const head = `${num(closed)} ${CLOSED_NAME[u]}`;
  if (u === 'ADET' || opened === null) return head;
  return `${head} + ${num(opened)} ${OPEN_NAME[u]}`;
}

const signed = (n: number) => `${n < 0 ? '-' : '+'}${num(Math.abs(n))}`;

/**
 * Stok hareketi satırı: "-1 ml açık", "-1 şişe, +49 ml açık". Kapalı kısım hareket tipinden işaret alır;
 * açık kısım (openedDelta) kendi işaretiyle gelir. Adet kalemde ve eski hareketlerde yalnız sayı.
 */
export function formatMovement(type: 'IN' | 'OUT' | 'ADJUSTMENT', quantity: number, openedDelta: number | null | undefined,
  unit: string | null | undefined): string {
  const u: StockUnit = unit && unit in CLOSED_NAME ? (unit as StockUnit) : 'ADET';
  const opened = u === 'ADET' || !openedDelta ? null : openedDelta;
  const parts: string[] = [];
  if (quantity !== 0 || opened === null) {
    parts.push(`${type === 'OUT' ? '-' : '+'}${num(quantity)}${u === 'ADET' ? '' : ` ${CLOSED_NAME[u]}`}`);
  }
  if (opened !== null) parts.push(`${signed(opened)} ${OPEN_NAME[u]} açık`);
  return parts.join(', ');
}
