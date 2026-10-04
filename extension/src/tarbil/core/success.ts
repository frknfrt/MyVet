import { bySuffix } from '../selectors/shared';

// Basari yakalamanin belge turunden bagimsiz kurallari (spec 2026-10-04 S7.1): basari = hekimin resmi onay
// butonuna tiklamasindan SONRA beliren METINLI basari paneli. TARBIL panelleri her yuklemede bos cizer.

export function isConfirmClick(target: EventTarget | null, confirmSuffixes: readonly string[]): boolean {
  const el = target as Element | null;
  if (!el || typeof el.closest !== 'function') return false;
  return confirmSuffixes.some((suffix) => el.closest(bySuffix(suffix)) !== null);
}

/** Onay tiklamasi aninda sayfada zaten olan paneller (onceki islemlerden kalma) sayilmaz. */
export function markStaleSuccess(doc: Document, panelSuffix: string, stale: WeakSet<Element>): void {
  doc.querySelectorAll(bySuffix(panelSuffix)).forEach((el) => stale.add(el));
}

export function hasFreshSuccess(doc: Document, panelSuffix: string, stale: WeakSet<Element>): boolean {
  return Array.from(doc.querySelectorAll(bySuffix(panelSuffix))).some(
    (el) => !stale.has(el) && (el.textContent ?? '').trim().length > 0,
  );
}
