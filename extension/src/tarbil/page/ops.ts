import type { PageHandler } from '../core/bridge';
import { MEDICINE_STOCK, RECEIPT, SEARCH, VACCINE_STOCK, VACCINE_STOCK_POPUP, allowedButtonSuffix, bySuffix } from '../selectors';
import { PageError, clickButton, clickElement, selectComboValue, setDate, setText, showAllRows, waitUntil, type TelerikEnv } from './telerik';

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
    // Cipi olmayan hasta: TARBIL'de pasaport numarasiyla aranir (cip kutusu bosaltilir, yoksa iki kosul birlesir).
    searchPassport: async ({ passport }: { passport: string }) => {
      setText(env, SEARCH.chip, '');
      setText(env, SEARCH.passport, passport);
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
    // Stok sayfalari: "Ara" + tum satirlari tek sayfaya al. Yalniz okuma icin (spec 2026-10-04 S13).
    loadStockTable: async ({ page }: { page: string }) => {
      const grid = page === 'vaccineStock' ? VACCINE_STOCK.gridComponent : page === 'medicineStock' ? MEDICINE_STOCK.gridComponent : null;
      if (!grid) throw new PageError('BAD_INPUT', `Stok sayfası değil: ${page}`);
      await clickButton(env, allowed(page, 'search'));
      await showAllRows(env, grid, 500);
    },
    // Asi stok penceresi (spec 2026-10-04 P2): seri ile ara, tek satirin "Sec" baglantisi, urun satirina adet.
    searchSerial: async ({ serial }: { serial: string }) => {
      setText(env, VACCINE_STOCK_POPUP.serial, serial);
      await clickButton(env, allowed('vaccineStockPopup', 'search'));
    },
    // Yalniz stok tablosundaki "Sec" baglantilari; tiklama pencereyi kapatir, sonuc beklenmez.
    selectStockRow: ({ linkId }: { linkId: string }) => {
      const link = env.doc.getElementById(linkId);
      if (!link || !link.matches(`table${bySuffix(VACCINE_STOCK_POPUP.grid)} a[id$="SelectlinkButton"]`)) {
        return Promise.reject(new PageError('NOT_FOUND', 'Stok tablosunda böyle bir Seç bağlantısı yok'));
      }
      (link as HTMLElement).click();
      return Promise.resolve();
    },
    setProductQuantity: async ({ quantity }: { quantity: number }) => {
      if (!Number.isInteger(quantity) || quantity < 1) throw new PageError('BAD_INPUT', `Geçersiz adet: ${quantity}`);
      setText(env, RECEIPT.productQuantity, String(quantity));
    },
  };
}
