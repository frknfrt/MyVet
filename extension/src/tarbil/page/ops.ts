import type { PageHandler } from '../bridge';
import { RECEIPT, SEARCH, bySuffix } from '../selectors';
import { PageError, clickButton, clickElement, selectComboValue, setDate, setText, waitUntil, type TelerikEnv } from './telerik';

/**
 * Koprudan cagrilan komutlar. Bilerek eksik: Onayla (btnInsert), Urun Ekle, sahip alanlari ve cikis icin
 * komut YOKTUR (spec S2, S12.6) -- izole dunya istese bile sayfa bunlari yapamaz.
 */
export function createPageOps(env: TelerikEnv): Record<string, PageHandler> {
  return {
    ready: () => waitUntil(env.isReady, 10_000),
    setDate: ({ iso }: { iso: string }) => setDate(env, RECEIPT.date, iso),
    selectAnimalType: ({ value }: { value: string }) => selectComboValue(env, RECEIPT.animalType, value),
    clickPetVet: () => clickButton(env, RECEIPT.petVet),
    searchChip: async ({ chip }: { chip: string }) => {
      setText(env, SEARCH.chip, chip);
      await clickButton(env, SEARCH.search);
    },
    // Yalniz arama tablosundaki satir kutulari: id ile herhangi bir oge (ornegin Onayla) tiklanamaz.
    checkRow: ({ checkboxId }: { checkboxId: string }) => {
      const box = env.doc.getElementById(checkboxId);
      if (!box || !box.matches(`table${bySuffix(SEARCH.grid)} input[type="checkbox"]`)) {
        return Promise.reject(new PageError('NOT_FOUND', 'Arama sonucunda böyle bir satır kutusu yok'));
      }
      return clickElement(env, checkboxId);
    },
    transfer: () => clickButton(env, SEARCH.transfer),
  };
}
