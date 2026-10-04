import { SEARCH } from './shared';
import { MEDICINE_STOCK, VACCINE_STOCK } from './stock';
import { RECEIPT } from './vaccineReceipt';

/**
 * Eklentinin basabilecegi butonlarin TAMAMI (spec 2026-10-04 S2, S7.1). Burada olmayan butona MAIN dunya basmaz.
 * Resmi kaydi tamamlayan butonlar (Onayla, Receteyi Onayla, Urun Kabul Onayla/Reddet, cikis) bu listeye GIREMEZ.
 */
export const ALLOWED_BUTTONS = {
  vaccineReceipt: { petVet: RECEIPT.petVet },
  animalSearch: { search: SEARCH.search, transfer: SEARCH.transfer },
  vaccineStock: { search: VACCINE_STOCK.search },
  medicineStock: { search: MEDICINE_STOCK.search },
} as const;

export const FORBIDDEN_BUTTON_PATTERNS: readonly RegExp[] = [/btnInsert2?$/i, /btnApprove$/i, /btnReject$/i, /exit/i];

export function allowedButtonSuffix(page: string, button: string): string | null {
  const buttons = (ALLOWED_BUTTONS as Record<string, Record<string, string>>)[page];
  if (!buttons || !Object.prototype.hasOwnProperty.call(ALLOWED_BUTTONS, page)) return null;
  if (!Object.prototype.hasOwnProperty.call(buttons, button)) return null;
  const suffix = buttons[button];
  return FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)) ? null : suffix;
}
