// "Asi Uygulama Belgesi Ekle" sayfasina ozgu id sonekleri (spec 2026-10-02 S3, 2026-10-04 S3).

/** Asi Uygulama Belgesi Ekle sayfasi. */
export const RECEIPT = {
  date: '_cntVACCINEBodyContent_dpApplicationDate',
  animalType: '_cntVACCINEBodyContent_cbxAnimalType',
  petVet: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00_ctl02_ctl00_bntPetVet',
  animalGrid: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00',
  // Onayla butonlari: eklenti bunlara ASLA basmaz, yalniz hekimin tiklamasini fark eder.
  insertButtons: ['_cntVACCINEBodyContent_btnInsert', '_cntVACCINEBodyContent_btnInsert2'],
  // Bildirim panelleri her yuklemede bos olarak DOM'da; yalniz metinli basari paneli sayilir (2026-10-04 canli dogrulama).
  successPanel: '_UCVACCINENotification_pnlNotifiSuccess',
} as const;
