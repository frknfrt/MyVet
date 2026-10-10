import type { PageHandler } from '../core/bridge';
import { MEDICINE_STOCK, MEDICINE_STOCK_POPUP, PRESCRIPTION_RECEIPT, RECEIPT, SEARCH, VACCINE_STOCK, VACCINE_STOCK_POPUP, allowedButtonSuffix, bySuffix } from '../selectors';
import { PageError, clearCombo, clickButton, clickElement, clientId, selectComboValue, setDate, setText, setTextLoose, showAllRows, waitUntil, type TelerikEnv } from './telerik';

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
    // Il/ilce/mahalle filtresi kaldirilir: hayvan klinigin mahallesinde kayitli olmayabilir (2026-10-06 canli bulgu).
    searchChip: async ({ chip }: { chip: string }) => {
      for (const suffix of SEARCH.addressFilters) await clearCombo(env, suffix);
      setText(env, SEARCH.chip, chip);
      await clickButton(env, allowed('animalSearch', 'search'));
    },
    // Cipi olmayan hasta: TARBIL'de pasaport numarasiyla aranir (cip kutusu bosaltilir, yoksa iki kosul birlesir).
    searchPassport: async ({ passport }: { passport: string }) => {
      for (const suffix of SEARCH.addressFilters) await clearCombo(env, suffix);
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
    // Yalniz stok pencerelerindeki (asi ya da ilac) "Sec" baglantilari; tiklama pencereyi kapatir, sonuc beklenmez.
    selectStockRow: ({ linkId }: { linkId: string }) => {
      const link = env.doc.getElementById(linkId);
      const inGrid = [VACCINE_STOCK_POPUP.grid, MEDICINE_STOCK_POPUP.grid]
        .map((g) => `table${bySuffix(g)} a[id$="SelectlinkButton"]`)
        .join(', ');
      if (!link || !link.matches(inGrid)) {
        return Promise.reject(new PageError('NOT_FOUND', 'Stok tablosunda böyle bir Seç bağlantısı yok'));
      }
      (link as HTMLElement).click();
      return Promise.resolve();
    },
    setProductQuantity: async ({ quantity }: { quantity: number }) => {
      if (!Number.isInteger(quantity) || quantity < 1) throw new PageError('BAD_INPUT', `Geçersiz adet: ${quantity}`);
      setText(env, RECEIPT.productQuantity, String(quantity));
    },
    // Stoktan Duserek Ilac Recetesi Ekle (spec 2026-10-09 S11).
    setTreatmentDate: ({ iso }: { iso: string }) => setDate(env, PRESCRIPTION_RECEIPT.date, iso),
    ensureTreatmentClass: async () => {
      const radio = env.doc.querySelector<HTMLInputElement>(bySuffix(PRESCRIPTION_RECEIPT.treatmentClass));
      if (!radio) throw new PageError('NOT_FOUND', 'Reçete sınıfı bulunamadı');
      if (radio.checked) return { changed: false };
      await clickElement(env, radio.id);
      return { changed: true };
    },
    selectPrescriptionAnimalType: ({ value }: { value: string }) => selectComboValue(env, PRESCRIPTION_RECEIPT.animalType, value),
    // Teshis agacindan dugum isaretlenir, "+" ile eklenen listesine alinir; listede (deger ya da adla) varsa dokunulmaz.
    addDisease: async ({ value }: { value: string }) => {
      const tree = env.find(clientId(env, PRESCRIPTION_RECEIPT.diseaseTree));
      const node = tree?.findNodeByValue?.(value);
      if (!node) throw new PageError('OPTION_NOT_FOUND', 'Teşhis TARBİL listesinde yok');
      const text = String(node.get_text());
      const listed = () => {
        const list = env.find(clientId(env, PRESCRIPTION_RECEIPT.diagnosedList));
        return Boolean(list && (list.findItemByValue(value) || list.findItemByText(text)));
      };
      if (listed()) return { added: false, listed: true };
      node.set_checked(true);
      await clickButton(env, allowed('prescriptionReceipt', 'addDisease'));
      return { added: true, listed: listed() };
    },
    searchMedicineSerial: async ({ serial }: { serial: string }) => {
      setTextLoose(env, MEDICINE_STOCK_POPUP.serial, serial);
      await clickButton(env, allowed('medicineStockPopup', 'search'));
    },
    // Kullanici karari (2026-10-10): TARBIL Adet'i duser; Miktar ayni sayi.
    setStockQuantities: async ({ quantity }: { quantity: number }) => {
      if (!Number.isInteger(quantity) || quantity < 1) throw new PageError('BAD_INPUT', `Geçersiz adet: ${quantity}`);
      setText(env, PRESCRIPTION_RECEIPT.stockQuantity, String(quantity));
      setText(env, PRESCRIPTION_RECEIPT.stockAmount, String(quantity));
    },
  };
}
