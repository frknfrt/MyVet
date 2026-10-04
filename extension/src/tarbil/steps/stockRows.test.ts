// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { parseTrDate, parseTrNumber, readStockRows } from './stockRows';

const VP = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_radGridStock_ctl00';
const MP = 'ctl00_ContentHolder_radGridStockSearch_ctl00';

function vaccineTable(rows: string, headers = ['', '', '', 'Ruhsat Tipi', 'Aşı Adı', 'Takdim Şekli', 'Seri Numarası', 'Son Kullanma Tarihi', 'Ürün Miktarı', 'Stok Tipi']) {
  document.body.innerHTML = `<table id="${VP}"><thead><tr>${headers.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${rows}</tbody></table>`;
}

describe('stock rows', () => {
  it('parses Turkish dates and numbers', () => {
    expect(parseTrDate('31.01.2027')).toBe('2027-01-31');
    expect(parseTrDate(' ')).toBeNull();
    expect(parseTrNumber('9,99')).toBe(9.99);
    expect(parseTrNumber('1.234,5')).toBe(1234.5);
    expect(parseTrNumber('')).toBeNull();
  });

  it('reads the vaccine stock table by header names', () => {
    vaccineTable(`<tr id="${VP}__0"><td></td><td></td><td></td><td>R</td><td> Aşı X </td><td>Flakon</td><td>123456</td><td>31.01.2027</td><td>4</td><td>Ana Stok</td></tr>`);

    expect(readStockRows(document, 'vaccineStock')).toEqual([
      { productName: 'Aşı X', presentation: 'Flakon', lotNumber: '123456', expiryDate: '2027-01-31', quantity: 4, openedQuantity: null },
    ]);
  });

  it('reads the medicine table whose header lives in a separate table', () => {
    document.body.innerHTML = `
      <table id="${MP}_Header"><thead><tr><th></th><th>Satış Yeri</th><th>Ürün</th><th>Takdim Şekli</th><th>Miktar</th>
        <th>Son Kullanma Tarihi</th><th>Açılmış Kalan Miktar</th><th>Açılmış Kalan Miktar Son Kullanma Tarihi</th><th>Seri Numarası</th><th>Kaydeden</th></tr></thead></table>
      <table id="${MP}"><tbody><tr id="${MP}__0"><td></td><td>Klinik</td><td>İlaç Y</td><td>Kutu</td><td>2</td><td>01.02.2027</td><td>0,5</td><td>01.03.2027</td><td>A1B2</td><td>X</td></tr></tbody></table>`;

    expect(readStockRows(document, 'medicineStock')).toEqual([
      { productName: 'İlaç Y', presentation: 'Kutu', lotNumber: 'A1B2', expiryDate: '2027-02-01', quantity: 2, openedQuantity: 0.5 },
    ]);
  });

  it('returns no rows when a required column is missing', () => {
    vaccineTable(`<tr id="${VP}__0"><td>Aşı X</td></tr>`, ['Ad']);
    expect(readStockRows(document, 'vaccineStock')).toEqual([]);
  });
});
