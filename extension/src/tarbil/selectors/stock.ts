// TARBIL stok sayfalari (spec 2026-10-04 S13; 2026-10-04 canli okuma). Eklenti burada yalniz "Ara"ya basar ve
// tabloyu tek sayfaya alir (sayfa boyutu); baska hicbir sey yapmaz.

/** hbsapp: Asi > Stok > Ara (ATS/VaccineStock/VaccineStockSearch.aspx). */
export const VACCINE_STOCK = {
  search: 'BodyContent_btnSearch',
  grid: '_radGridStock_ctl00',
  gridComponent: '_radGridStock',
} as const;

/** vetilac: Ilac Takip Sistemi > Stok Ara (/Pages/StockSearch.aspx). Baslik ayri `<grid>_Header` tablosunda. */
export const MEDICINE_STOCK = {
  search: 'ContentHolder_btnSearch',
  grid: '_radGridStockSearch_ctl00',
  gridComponent: '_radGridStockSearch',
} as const;
