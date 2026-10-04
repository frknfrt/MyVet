import type { PageHandler } from '../core/bridge';
import { RECEIPT, SEARCH, allowedButtonSuffix, bySuffix } from '../selectors';
import { PageError, clickButton, clickElement, selectComboValue, setDate, setText, waitUntil, type TelerikEnv } from './telerik';

/**
 * Koprudan cagrilan komutlar. Butonlara yalniz clickAllowed ile, selectors/allowlist.ts'teki listeden basilir;
 * Onayla/Kaydet/cikis gibi resmi kaydi tamamlayan butonlar listede olamaz (spec 2026-10-04 S2, S7.1).
 */
export function createPageOps(env: TelerikEnv): Record<string, PageHandler> {
  const allowed = (page: string, button: string): string => {
    const suffix = allowedButtonSuffix(page, button);
    if (!suffix) throw new PageError('NOT_ALLOWED', `İzin listesinde olmayan buton: ${page}.${button}`);
    return suffix;
  };
  return {
    ready: () => waitUntil(env.isReady, 10_000),
    setDate: ({ iso }: { iso: string }) => setDate(env, RECEIPT.date, iso),
    // Tur postback'i tamamlanmissa PetVet butonu vardir; yoksa secim istemcide kalmis demektir, zorla yeniden sec.
    selectAnimalType: ({ value }: { value: string }) =>
      selectComboValue(env, RECEIPT.animalType, value, !env.doc.querySelector(bySuffix(RECEIPT.petVet))),
    clickAllowed: async ({ page, button }: { page: string; button: string }) => clickButton(env, allowed(page, button)),
    searchChip: async ({ chip }: { chip: string }) => {
      setText(env, SEARCH.chip, chip);
      await clickButton(env, allowed('animalSearch', 'search'));
    },
    // Yalniz arama tablosundaki satir kutulari: id ile herhangi bir oge (ornegin Onayla) tiklanamaz.
    checkRow: ({ checkboxId }: { checkboxId: string }) => {
      const box = env.doc.getElementById(checkboxId);
      if (!box || !box.matches(`table${bySuffix(SEARCH.grid)} input[type="checkbox"]`)) {
        return Promise.reject(new PageError('NOT_FOUND', 'Arama sonucunda böyle bir satır kutusu yok'));
      }
      return clickElement(env, checkboxId);
    },
  };
}
