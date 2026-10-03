import type { PageHandler } from '../bridge';
import { RECEIPT, SEARCH } from '../selectors';
import { clickButton, clickElement, selectComboValue, setDate, setText, waitUntil, type TelerikEnv } from './telerik';

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
    checkRow: ({ checkboxId }: { checkboxId: string }) => clickElement(env, checkboxId),
    transfer: () => clickButton(env, SEARCH.transfer),
  };
}
