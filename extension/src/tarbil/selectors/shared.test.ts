// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { SEARCH, VACCINE_PAGE_URL, bySuffix, pageKind, pageUrlFor } from './';

describe('pageKind', () => {
  it('recognizes the stock prescription page only for type=1', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx', search: '?type=1' })).toBe('prescriptionReceipt');
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx', search: '?type=2' })).toBe('other');
  });
  it('recognizes the prescription animal search and medicine stock popups', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ModalPages/KKBSAnimalSearchModalPage.aspx', search: '?AnimalType=x' })).toBe('search');
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ModalPages/UcPharmacyStockSearchForReceiptModalPage.aspx', search: '?animalid=x' })).toBe('medicineStockPopup');
  });
  it('picks the TARBIL page by document type', () => {
    expect(pageUrlFor('PRESCRIPTION')).toBe('https://hbsapp.tarbil.gov.tr/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx?type=1');
    expect(pageUrlFor('VACCINATION')).toBe(VACCINE_PAGE_URL);
  });
  it('matches the animal search fields of both search windows', () => {
    for (const p of ['ctl00_X_UCVaccineKKBSAnimalSearch_', 'ctl00_X_UCKKBSAnimalSearch_']) {
      document.body.innerHTML = `<input id="${p}txtChipNo">`;
      expect(document.querySelector(bySuffix(SEARCH.chip))).not.toBeNull();
    }
  });

  it('recognizes the vaccine stock popup', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ModalPages/UcVaccineStockSearchModalPage.aspx', search: '?animalid=x' })).toBe('vaccineStockPopup');
  });

  it('recognizes the vaccine and medicine stock pages', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ATS/VaccineStock/VaccineStockSearch.aspx', search: '' })).toBe('vaccineStock');
    expect(pageKind({ pathname: '/Pages/StockSearch.aspx', search: '' })).toBe('medicineStock');
  });

  it('recognizes the vaccine receipt page only for type=1', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx', search: '?type=1' })).toBe('receipt');
    expect(pageKind({ pathname: '/modules/receipt/pages/ats/vaccinereceipt/vaccinereceiptpage.aspx', search: '?type=1' })).toBe('receipt');
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx', search: '?type=2' })).toBe('other');
  });

  it('recognizes the animal search popup', () => {
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/ModalPages/VaccineKKBSAnimalSearchModalPage.aspx', search: '?AnimalType=C' })).toBe('search');
  });

  it('recognizes the TARBIL home page', () => {
    expect(pageKind({ pathname: '/', search: '' })).toBe('home');
    expect(pageKind({ pathname: '/Default.aspx', search: '' })).toBe('home');
    expect(pageKind({ pathname: '/Modules/RECEIPT/Pages/VaccineDefault.aspx', search: '' })).toBe('other');
  });

  it('builds an id-suffix selector', () => {
    expect(bySuffix('_btnInsert')).toBe('[id$="_btnInsert"]');
  });
});
