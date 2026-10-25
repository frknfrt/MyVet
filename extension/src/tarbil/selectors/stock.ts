// TARBIL stok sayfalari (spec 2026-10-04 S13; 2026-10-04 canli okuma). Eklenti burada yalniz "Ara"ya basar ve
// tabloyu tek sayfaya alir (sayfa boyutu); baska hicbir sey yapmaz.

/**
 * hbsapp: Asi > Stok > Ara (ATS/VaccineStock/VaccineStockSearch.aspx). Ayni sayfadaki "Parca Stok Ara" ayri bir
 * tabloyu doldurur (2026-10-21 canli: ana stokta olmayan urunler; sutun adlari ana tabloyla ayni).
 */
export const VACCINE_STOCK = {
  search: 'BodyContent_btnSearch',
  grid: '_radGridStock_ctl00',
  gridComponent: '_radGridStock',
  partSearch: 'BodyContent_btnStocklInboxSearch',
  partGrid: '_radGridStockInbox_ctl00',
  partGridComponent: '_radGridStockInbox',
} as const;

/** vetilac: Ilac Takip Sistemi > Stok Ara (/Pages/StockSearch.aspx). Baslik ayri `<grid>_Header` tablosunda. */
export const MEDICINE_STOCK = {
  search: 'ContentHolder_btnSearch',
  grid: '_radGridStockSearch_ctl00',
  gridComponent: '_radGridStockSearch',
} as const;

/** hbsapp asi belgesi stok penceresi (UcVaccineStockSearchModalPage.aspx). "Sec" baglantisi pencereyi kapatir. */
export const VACCINE_STOCK_POPUP = {
  serial: '_UcVaccineStockSearch_txtSerialNo',
  search: '_UcVaccineStockSearch_btnSearch',
  grid: '_UcVaccineStockSearch_radGridStock_ctl00',
  /** "Parça Stok" radyosu (değer 2) ve ayrı listesi (2026-10-24 canlı). */
  partStockRadio: '_UcVaccineStockSearch_rbtnStockType_1',
  partGrid: '_UcVaccineStockSearch_radGridPieceStock_ctl00',
} as const;

/** hbsapp ilac recetesi stok penceresi (UcPharmacyStockSearchForReceiptModalPage.aspx; 2026-10-10 canli). "Sec" pencereyi kapatir. */
export const MEDICINE_STOCK_POPUP = {
  serial: '_UcPharmacyStockSearchForReceipt_txtSerialNumber',
  search: '_UcPharmacyStockSearchForReceipt_btnSearch',
  grid: '_UcPharmacyStockSearchForReceipt_radGridStock_ctl00',
  /** "Açık Stok" radyosu ve listesi (2026-10-24 canlı): son kolon "Açık Stok Miktarı", Seç bağlantısı SelectlinkButton2. */
  openStockRadio: '_UcPharmacyStockSearchForReceipt_rbtnStockType_1',
  openGrid: '_UcPharmacyStockSearchForReceipt_radGridOpenStock_ctl00',
} as const;
