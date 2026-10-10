// TARBIL'e ozgu TUM id/yol/deger bilgisi burada (spec S3). TARBIL ekrani degisirse yalniz bu dosya degisir.
// Id'ler on ek degil SONEK ile aranir: Telerik istemci id'leri (ctl00_ctl00_...) ile duz id'ler (bodyCPH_...)
// ayni bilesende farkli on ekler tasiyor.
import type { DocumentType } from '../../shared/types';

export const TARBIL_ORIGIN = 'https://hbsapp.tarbil.gov.tr';
const VACCINE_PAGE_PATH = '/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx';
export const VACCINE_PAGE_URL = `${TARBIL_ORIGIN}${VACCINE_PAGE_PATH}?type=1`;
const PRESCRIPTION_PAGE_PATH = '/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx';
export const PRESCRIPTION_PAGE_URL = `${TARBIL_ORIGIN}${PRESCRIPTION_PAGE_PATH}?type=1`;
// Asi sayfasi VaccineKKBS..., recete sayfasi KKBS... penceresini acar; alanlar ayni (2026-10-10 canli).
const SEARCH_PAGE_FILES = ['vaccinekkbsanimalsearchmodalpage.aspx', 'kkbsanimalsearchmodalpage.aspx'];
const MEDICINE_STOCK_POPUP_FILE = 'ucpharmacystocksearchforreceiptmodalpage.aspx';
const HOME_PATHS = ['/', '/default.aspx'];
const VACCINE_STOCK_PATH = '/modules/receipt/pages/ats/vaccinestock/vaccinestocksearch.aspx';
const MEDICINE_STOCK_PATH = '/pages/stocksearch.aspx';
const VACCINE_STOCK_POPUP_FILE = 'ucvaccinestocksearchmodalpage.aspx';

/** cbxAnimalType degerleri (spec S3). */
export const ANIMAL_TYPE = {
  CAT: '245f5f71-2cfd-4a04-9072-640069e3268e',
  DOG: '3eea84e6-b2d8-494f-a01c-89e739bb004d',
} as const;

/** Aktarimin dolduruldugu TARBIL sayfasi. */
export function pageUrlFor(documentType: DocumentType): string {
  return documentType === 'PRESCRIPTION' ? PRESCRIPTION_PAGE_URL : VACCINE_PAGE_URL;
}

/**
 * PetVet hayvan arama penceresi: asi sayfasinda VaccineKKBSAnimalSearchModalPage (_UCVaccineKKBSAnimalSearch_),
 * recete sayfasinda KKBSAnimalSearchModalPage (_UCKKBSAnimalSearch_). Sonek ikisini de yakalar.
 */
export const SEARCH = {
  chip: 'KKBSAnimalSearch_txtChipNo',
  passport: 'KKBSAnimalSearch_txtPassportNo',
  search: 'KKBSAnimalSearch_btnSearch',
  grid: 'KKBSAnimalSearch_radGridAnimal_ctl00',
  transfer: 'KKBSAnimalSearch_btnAddBulkAnimal',
  // Adres filtresi (2026-10-06 canli): klinik adresiyle dolu gelir, sonucu o mahalleyle sinirlar. Ilk secenek
  // "Seciniz" (deger ""), secim postback yapmaz. Mahalle -> ilce -> il sirasiyla bosaltilir.
  addressFilters: [
    'KKBSAnimalSearch_UCProvinceDistrictNeigbourhood_cbxNeigbourhood',
    'KKBSAnimalSearch_UCProvinceDistrictNeigbourhood_cbxDistrict',
    'KKBSAnimalSearch_UCProvinceDistrictNeigbourhood_cbxProvince',
  ],
} as const;

export const bySuffix = (suffix: string): string => `[id$="${suffix}"]`;

export type PageKind =
  | 'receipt' | 'prescriptionReceipt' | 'search' | 'home' | 'vaccineStock' | 'medicineStock'
  | 'vaccineStockPopup' | 'medicineStockPopup' | 'other';

export function pageKind(loc: { pathname: string; search: string }): PageKind {
  const path = loc.pathname.toLowerCase();
  const type1 = new URLSearchParams(loc.search).get('type') === '1';
  if (path === VACCINE_PAGE_PATH.toLowerCase()) return type1 ? 'receipt' : 'other';
  if (path === PRESCRIPTION_PAGE_PATH.toLowerCase()) return type1 ? 'prescriptionReceipt' : 'other';
  if (SEARCH_PAGE_FILES.some((f) => path.endsWith(`/${f}`))) return 'search';
  if (path.endsWith(`/${VACCINE_STOCK_POPUP_FILE}`)) return 'vaccineStockPopup';
  if (path.endsWith(`/${MEDICINE_STOCK_POPUP_FILE}`)) return 'medicineStockPopup';
  if (path === VACCINE_STOCK_PATH) return 'vaccineStock';
  if (path === MEDICINE_STOCK_PATH) return 'medicineStock';
  if (HOME_PATHS.includes(path)) return 'home';
  return 'other';
}
