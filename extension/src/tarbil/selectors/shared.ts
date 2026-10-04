// TARBIL'e ozgu TUM id/yol/deger bilgisi burada (spec S3). TARBIL ekrani degisirse yalniz bu dosya degisir.
// Id'ler on ek degil SONEK ile aranir: Telerik istemci id'leri (ctl00_ctl00_...) ile duz id'ler (bodyCPH_...)
// ayni bilesende farkli on ekler tasiyor.

export const TARBIL_ORIGIN = 'https://hbsapp.tarbil.gov.tr';
const VACCINE_PAGE_PATH = '/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx';
export const VACCINE_PAGE_URL = `${TARBIL_ORIGIN}${VACCINE_PAGE_PATH}?type=1`;
const SEARCH_PAGE_FILE = 'vaccinekkbsanimalsearchmodalpage.aspx';
const HOME_PATHS = ['/', '/default.aspx'];
const VACCINE_STOCK_PATH = '/modules/receipt/pages/ats/vaccinestock/vaccinestocksearch.aspx';
const MEDICINE_STOCK_PATH = '/pages/stocksearch.aspx';
const VACCINE_STOCK_POPUP_FILE = 'ucvaccinestocksearchmodalpage.aspx';

/** cbxAnimalType degerleri (spec S3). */
export const ANIMAL_TYPE = {
  CAT: '245f5f71-2cfd-4a04-9072-640069e3268e',
  DOG: '3eea84e6-b2d8-494f-a01c-89e739bb004d',
} as const;

/** PetVet hayvan arama penceresi (VaccineKKBSAnimalSearchModalPage.aspx). */
export const SEARCH = {
  chip: '_UCVaccineKKBSAnimalSearch_txtChipNo',
  passport: '_UCVaccineKKBSAnimalSearch_txtPassportNo',
  search: '_UCVaccineKKBSAnimalSearch_btnSearch',
  grid: '_UCVaccineKKBSAnimalSearch_radGridAnimal_ctl00',
  transfer: '_UCVaccineKKBSAnimalSearch_btnAddBulkAnimal',
} as const;

export const bySuffix = (suffix: string): string => `[id$="${suffix}"]`;

export type PageKind = 'receipt' | 'search' | 'home' | 'vaccineStock' | 'medicineStock' | 'vaccineStockPopup' | 'other';

export function pageKind(loc: { pathname: string; search: string }): PageKind {
  const path = loc.pathname.toLowerCase();
  if (path === VACCINE_PAGE_PATH.toLowerCase()) {
    return new URLSearchParams(loc.search).get('type') === '1' ? 'receipt' : 'other';
  }
  if (path.endsWith(`/${SEARCH_PAGE_FILE}`)) return 'search';
  if (path.endsWith(`/${VACCINE_STOCK_POPUP_FILE}`)) return 'vaccineStockPopup';
  if (path === VACCINE_STOCK_PATH) return 'vaccineStock';
  if (path === MEDICINE_STOCK_PATH) return 'medicineStock';
  if (HOME_PATHS.includes(path)) return 'home';
  return 'other';
}
