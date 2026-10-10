// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import type { PlannedLine } from '../../shared/types';
import { confirmedLines, nextLine, readMedicinePopupRows, readSavedStockLines, readStockEditRow } from './stockLines';

const P = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntRECEIPTBodyContent_';
const T = `${P}RadGridStock_ctl00`;
const HEAD = ['Detay', 'Detay', 'Ürün', 'Takdim Şekli', 'Seri Numarası', 'Son Kullanım Tarihi', 'Stok Miktarı', 'Stok Tipi', 'Kullanılacak Adet', 'Kullanılacak Miktarı', 'Sil'];

function stockGrid(saved: [string, string, string][]) {
  document.body.innerHTML = `<table id="${T}"><thead><tr>${HEAD.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${
    saved.length === 0 ? '<tr class="rgNoRecords"><td colspan="11">Kayıt Bulunamadı.</td></tr>'
      : saved.map(([name, serial, qty], i) => `<tr id="${T}__${i}" class="rgRow"><td></td><td></td><td>${name}</td><td>Kutu</td>
          <td>${serial}</td><td>01.01.2027</td><td>3 Adet</td><td>Bütün Stok</td><td>${qty}</td><td>${qty}</td><td></td></tr>`).join('')
  }</tbody></table>`;
}

const plan: PlannedLine[] = [
  { itemName: 'Advantage', tarbilProductName: 'Advantage % 10', lotNumber: 'KV04Z81', quantity: 1 },
  { itemName: 'Synulox', tarbilProductName: 'SYNULOX', lotNumber: 's1', quantity: 2 },
];

describe('stock lines', () => {
  it('reads saved rows of the prescription stock grid', () => {
    stockGrid([['Advantage % 10', 'KV04Z81', '1'], ['SYNULOX', 'S1', '']]);

    expect(readSavedStockLines(document)).toEqual([
      { productName: 'Advantage % 10', serial: 'KV04Z81', quantity: 1 },
      { productName: 'SYNULOX', serial: 'S1', quantity: null },
    ]);
  });

  it('reads nothing from an empty grid', () => {
    stockGrid([]);
    expect(readSavedStockLines(document)).toEqual([]);
  });

  it('reads the open insert row', () => {
    document.body.innerHTML = `<input id="${T}_ctl02_ctl03_txtStockProductName" value="Advantage % 10"><span id="${T}_ctl02_ctl03_lblSerialNumber"> kv04z81 </span>`;
    expect(readStockEditRow(document)).toEqual({ serial: 'KV04Z81', productName: 'Advantage % 10' });
    document.body.innerHTML = '';
    expect(readStockEditRow(document)).toBeNull();
  });

  it('finds the first planned line that is not saved yet', () => {
    expect(nextLine(plan, [])).toBe(0);
    expect(nextLine(plan, [{ productName: 'x', serial: 'KV04Z81', quantity: 1 }])).toBe(1);
    expect(nextLine(plan, [{ productName: 'x', serial: 'KV04Z81', quantity: 1 }, { productName: 'y', serial: 'S1', quantity: 2 }])).toBe(-1);
  });

  it('fills missing names and quantities from the plan', () => {
    expect(confirmedLines([
      { productName: '', serial: 'KV04Z81', quantity: 1 },
      { productName: 'SYNULOX', serial: 'S1', quantity: null },
      { productName: '', serial: 'ZZ9', quantity: null },
    ], plan)).toEqual([
      { productName: 'Advantage % 10', serialNumber: 'KV04Z81', quantity: 1 },
      { productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 },
    ]);
  });

  it('reads the medicine stock popup rows', () => {
    const G = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_radGridStock_ctl00';
    document.body.innerHTML = `<table id="${G}"><thead><tr><th></th><th>Ürün Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
      <th>Son Kullanma Tarihi</th><th>Ürün Miktarı</th></tr></thead><tbody><tr id="${G}__0"><td><a id="${G}_ctl04_SelectlinkButton">Seç</a></td>
      <td>Advantage % 10</td><td>Kutu</td><td>KV04Z81</td><td>31.01.2027</td><td>3</td></tr></tbody></table>`;

    expect(readMedicinePopupRows(document)).toEqual([
      { productName: 'Advantage % 10', serial: 'KV04Z81', expiryDate: '2027-01-31', linkId: `${G}_ctl04_SelectlinkButton` },
    ]);
  });
});
