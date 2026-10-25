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
      { productName: 'Advantage % 10', serial: 'KV04Z81', quantity: 1, amount: 1, source: 'WHOLE' },
      { productName: 'SYNULOX', serial: 'S1', quantity: null, amount: null, source: 'WHOLE' },
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
      { productName: 'Advantage % 10', serialNumber: 'KV04Z81', quantity: 1, amount: 1, source: null },
      { productName: 'SYNULOX', serialNumber: 'S1', quantity: 2, amount: 2, source: null },
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

  it('reads only whole quantities and never inflates a decimal display', () => {
    stockGrid([['A', 'X1', '1,00'], ['B', 'X2', '2,50'], ['C', 'X3', '3 Adet'], ['D', 'X4', '1.000']]);

    expect(readSavedStockLines(document).map((l) => l.quantity)).toEqual([1, null, 3, null]);
  });

  it('reads Kullanılacak Miktarı as a decimal and Stok Tipi as the source (spec 2026-10-24)', () => {
    document.body.innerHTML = `<table id="${T}"><thead><tr>${HEAD.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>
      <tr id="${T}__0" class="rgRow"><td></td><td></td><td>Clavon</td><td>Şişe</td><td>63016</td><td>27.04.2027</td><td>49,00 mL</td>
      <td>Açık Stok</td><td>0</td><td>1,50</td><td></td></tr></tbody></table>`;

    expect(readSavedStockLines(document)).toEqual([{ productName: 'Clavon', serial: '63016', quantity: null, amount: 1.5, source: 'OPEN' }]);
  });

  it('keeps an ml line without a whole quantity and fills amount and source from the plan', () => {
    const mlPlan: PlannedLine[] = [{ itemName: 'Clavon', tarbilProductName: 'CLAVON = Clavon', lotNumber: '63016', quantity: 0, amount: 1.5, unit: 'ML', source: 'OPEN' }];
    expect(confirmedLines([{ productName: 'Clavon', serial: '63016', quantity: null }], mlPlan)).toEqual([
      { productName: 'Clavon', serialNumber: '63016', quantity: 0, amount: 1.5, source: 'OPEN' },
    ]);
  });

  it('reads the open-stock popup grid (spec 2026-10-24)', () => {
    const O = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_radGridOpenStock_ctl00';
    document.body.innerHTML = `<table id="${O}"><thead><tr><th></th><th>Ürün Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
      <th>Son Kullanma Tarihi</th><th>Açık Stok Miktarı</th></tr></thead><tbody><tr id="${O}__0"><td><a id="${O}_ctl04_SelectlinkButton2">Seç</a></td>
      <td>Clavon</td><td>50 ml</td><td>63016</td><td>27.04.2027</td><td>49,00</td></tr></tbody></table>`;

    expect(readMedicinePopupRows(document, true)).toEqual([
      { productName: 'Clavon', serial: '63016', expiryDate: '2027-04-27', linkId: `${O}_ctl04_SelectlinkButton2` },
    ]);
  });
});
