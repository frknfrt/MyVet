import { describe, expect, it } from 'vitest';
import { ALLOWED_BUTTONS, FORBIDDEN_BUTTON_PATTERNS, allowedButtonSuffix } from './allowlist';
import { RECEIPT } from './vaccineReceipt';
import { PRESCRIPTION_RECEIPT } from './prescriptionReceipt';

describe('button allowlist', () => {
  it('treats Reçeteyi Onayla and the stock row Kaydet as forbidden', () => {
    for (const suffix of PRESCRIPTION_RECEIPT.insertButtons) {
      expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)), suffix).toBe(true);
    }
    expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test('RadGridStock_ctl00_ctl02_ctl03_PerformInsertButton'))).toBe(true);
  });
  it('allows the prescription form helpers and the medicine stock popup search', () => {
    expect(allowedButtonSuffix('prescriptionReceipt', 'petVet')).toBe('_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00_ctl02_ctl00_bntPetVet');
    expect(allowedButtonSuffix('prescriptionReceipt', 'addDisease')).toBe('_cntRECEIPTBodyContent_imgAddDisease');
    expect(allowedButtonSuffix('prescriptionReceipt', 'addStock')).toBe('_RadGridStock_ctl00_ctl02_ctl00_InitInsertButton');
    expect(allowedButtonSuffix('prescriptionReceipt', 'stockSearch')).toBe('_RadGridStock_ctl00_ctl02_ctl03_imgSearch');
    expect(allowedButtonSuffix('medicineStockPopup', 'search')).toBe('_UcPharmacyStockSearchForReceipt_btnSearch');
    expect(allowedButtonSuffix('prescriptionReceipt', 'insert')).toBeNull();
  });

  it('contains no button that finalizes an official record', () => {
    const all = Object.values(ALLOWED_BUTTONS).flatMap((page) => Object.values(page));
    for (const suffix of all) {
      expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)), suffix).toBe(false);
    }
  });

  it('treats the vaccine page Onayla buttons as forbidden', () => {
    for (const suffix of RECEIPT.insertButtons) {
      expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)), suffix).toBe(true);
    }
  });

  it('resolves only listed page/button pairs', () => {
    expect(allowedButtonSuffix('vaccineReceipt', 'petVet')).toBe(RECEIPT.petVet);
    expect(allowedButtonSuffix('vaccineReceipt', 'insert')).toBeNull();
    expect(allowedButtonSuffix('nope', 'petVet')).toBeNull();
    expect(allowedButtonSuffix('vaccineReceipt', 'toString')).toBeNull();
  });

  it('allows Ürün Ekle and the stock popup search but never the product row Kaydet', () => {
    expect(allowedButtonSuffix('vaccineReceipt', 'addProduct')).toBe('_RadGridProduct_ctl00_ctl02_ctl00_InitInsertButton');
    expect(allowedButtonSuffix('vaccineStockPopup', 'search')).toBe('_UcVaccineStockSearch_btnSearch');
    expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test('RadGridProduct_ctl00_ctl02_ctl03_PerformInsertButton'))).toBe(true);
  });
});
