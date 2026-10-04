import { describe, expect, it } from 'vitest';
import { bySuffix, pageKind } from './';

describe('pageKind', () => {
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
