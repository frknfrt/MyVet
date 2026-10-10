// "Stoktan Duserek Ilac Recetesi Ekle" sayfasina ozgu id sonekleri (spec 2026-10-09 S11; 2026-10-10 canli okuma).
const B = '_cntRECEIPTBodyContent_';

export const PRESCRIPTION_RECEIPT = {
  date: `${B}dpTreatmentStartDate`,
  /** Muayene/Tedavi Recetesi (varsayilan secili); _1 Tavsiye Recetesi. */
  treatmentClass: `${B}rbtnReceiptClass_0`,
  animalType: `${B}cbxAnimalType`,
  // Teshis: RadComboBox icinde RadTreeView (dugum degeri = tarbil_disease.id), "+" ile RadListBox'a eklenir.
  diseaseTree: `${B}cbxDisease_i0_treeDisease`,
  addDisease: `${B}imgAddDisease`,
  diagnosedList: `${B}lbxDiagnosedDisease`,
  // Hayvan bolumu asi sayfasiyla ayni bilesen (PetVet arama penceresi KKBSAnimalSearchModalPage.aspx).
  petVet: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00_ctl02_ctl00_bntPetVet',
  animalGrid: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00',
  // Urun bolumu: "Stok Ekle" ekleme satiri acar; buyutec stok penceresini window.open ile acar.
  stockGrid: '_RadGridStock_ctl00',
  addStock: '_RadGridStock_ctl00_ctl02_ctl00_InitInsertButton',
  stockSearch: '_RadGridStock_ctl00_ctl02_ctl03_imgSearch',
  editSerial: '_RadGridStock_ctl00_ctl02_ctl03_lblSerialNumber',
  editProductName: '_RadGridStock_ctl00_ctl02_ctl03_txtStockProductName',
  /** Kullanilacak Adet (tam sayi; TARBIL stoktan bunu duser). */
  stockQuantity: '_RadGridStock_ctl00_ctl02_ctl03_txtStockQuantity',
  /** Kullanilacak Miktari (2 ondalik, min 0,01); kullanici karari: Adet ile ayni sayi. */
  stockAmount: '_RadGridStock_ctl00_ctl02_ctl03_txtStockAmount',
  // Receteyi Onayla: eklenti ASLA basmaz, yalniz hekimin tiklamasini fark eder.
  insertButtons: [`${B}btnInsert`, `${B}btnInsert2`],
  // DOGRULANMADI (2026-10-10): sayfada bos UCRECEIPTNotification_pnlNotifi var; asi sayfasindaki desene gore aday.
  successPanel: '_UCRECEIPTNotification_pnlNotifiSuccess',
} as const;
