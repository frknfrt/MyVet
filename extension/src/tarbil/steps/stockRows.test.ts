// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { parseTrDate, parseTrNumber, readStockRows, readStockTable } from './stockRows';

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
      { productName: 'İlaç Y', presentation: 'Kutu', lotNumber: 'A1B2', expiryDate: '2027-02-01', quantity: 2, openedQuantity: 0.5, openedExpiryDate: '2027-03-01' },
    ]);
  });

  it('returns no rows when a required column is missing', () => {
    vaccineTable(`<tr id="${VP}__0"><td>Aşı X</td></tr>`, ['Ad']);
    expect(readStockRows(document, 'vaccineStock')).toEqual([]);
  });

  it('rejects the whole table when a quantity or date cell cannot be read', () => {
    vaccineTable(`<tr id="${VP}__0"><td></td><td></td><td></td><td>R</td><td>Aşı X</td><td>Flakon</td><td>1</td><td>31.01.2027</td><td>4</td><td>A</td></tr>
      <tr id="${VP}__1"><td></td><td></td><td></td><td>R</td><td>Aşı Y</td><td>Flakon</td><td>2</td><td>31.01.2027</td><td>4 Adet</td><td>A</td></tr>`);
    expect(readStockRows(document, 'vaccineStock')).toEqual([]);

    vaccineTable(`<tr id="${VP}__0"><td></td><td></td><td></td><td>R</td><td>Aşı X</td><td>Flakon</td><td>1</td><td>2027/01/31</td><td>4</td><td>A</td></tr>`);
    expect(readStockRows(document, 'vaccineStock')).toEqual([]);

    vaccineTable(`<tr id="${VP}__0"><td></td><td></td><td></td><td>R</td><td>Aşı X</td><td>Flakon</td><td>1</td><td>31.01.2027</td><td>2,5</td><td>A</td></tr>`);
    expect(readStockRows(document, 'vaccineStock')).toEqual([]);
  });
});

describe('Parça Stok table (2026-10-21)', () => {
  const PP = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_radGridStockInbox_ctl00';
  const headers = ['', '', '', 'Aşı Adı', 'Takdim Şekli', 'Seri Numarası', 'Son Kullanma Tarihi', 'Ürün Miktarı', 'Ruhsat Sahibi Tip'];
  const table = (rows: string) =>
    `<table id="${PP}"><thead><tr>${headers.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${rows}</tbody></table>`;

  it('reads the Parça Stok grid without mixing it up with the whole-stock grid', () => {
    document.body.innerHTML = table(`<tr id="${PP}__0"><td></td><td></td><td></td><td>Biocan R</td><td>Flakon</td><td>665932</td><td>31.08.2027</td><td>18</td><td>Firma</td></tr>`);

    expect(readStockTable(document, 'vaccinePartStock')).toEqual([
      { productName: 'Biocan R', presentation: 'Flakon', lotNumber: '665932', expiryDate: '2027-08-31', quantity: 18, openedQuantity: null },
    ]);
    expect(readStockTable(document, 'vaccineStock')).toBeNull();
  });

  it('tells an empty table apart from a missing or unrecognized one', () => {
    document.body.innerHTML = table('<tr class="rgNoRecords"><td>Kayıt yok</td></tr>');
    expect(readStockTable(document, 'vaccinePartStock')).toEqual([]);

    document.body.innerHTML = '<div></div>';
    expect(readStockTable(document, 'vaccinePartStock')).toBeNull();
    expect(readStockRows(document, 'vaccinePartStock')).toEqual([]);
  });

  it('reads doses per package from the vaccine whole-stock grid (spec 2026-10-24)', () => {
    vaccineTable(
      `<tr id="${VP}__0"><td></td><td></td><td></td><td>R</td><td>Biocan DHPPI</td><td>Kutusunda 20 adet flakon</td><td>145932</td><td>31.08.2027</td><td>1</td><td>20</td><td>20</td><td>Kendi Ürünü</td></tr>`,
      ['', '', '', 'Ruhsat Tipi', 'Aşı Adı', 'Takdim Şekli', 'Seri Numarası', 'Son Kullanma Tarihi', 'Ürün Miktarı', 'Üretilen Doz-Küçük Hayvan', 'Üretilen Doz-Büyük Hayvan', 'Stok Tipi'],
    );

    expect(readStockRows(document, 'vaccineStock')[0]).toMatchObject({ productName: 'Biocan DHPPI', quantity: 1, dosesPerPackage: 20 });
  });
});

describe('opened expiry date (spec 2026-10-25)', () => {
  function medicine(header: string[], cells: string[]) {
    document.body.innerHTML = `
      <table id="${MP}_Header"><thead><tr>${header.map((h) => `<th>${h}</th>`).join('')}</tr></thead></table>
      <table id="${MP}"><tbody><tr id="${MP}__0">${cells.map((c) => `<td>${c}</td>`).join('')}</tr></tbody></table>`;
  }
  const base = ['', 'Satış Yeri', 'Ürün', 'Takdim Şekli', 'Miktar', 'Son Kullanma Tarihi', 'Açılmış Kalan Miktar'];

  it('readsOpenedExpiryDate', () => {
    medicine([...base, 'Açılmış Kalan Miktar Son Kullanma Tarihi', 'Seri Numarası'], ['', 'K', 'CLAVON', '50 ml', '0', '01.02.2027', '49,00', '30.11.2027', '63016']);
    expect(readStockRows(document, 'medicineStock')[0].openedExpiryDate).toBe('2027-11-30');
  });

  it('emptyOpenedExpiryCell_isNull', () => {
    medicine([...base, 'Açılmış Kalan Miktar Son Kullanma Tarihi', 'Seri Numarası'], ['', 'K', 'TENIFEN', 'Kutu', '3', '01.02.2027', '', '', 'T1']);
    expect(readStockRows(document, 'medicineStock')[0].openedExpiryDate).toBeNull();
  });

  it('missingOpenedExpiryColumn_omitsField', () => {
    medicine([...base, 'Seri Numarası'], ['', 'K', 'CLAVON', '50 ml', '0', '01.02.2027', '49,00', '63016']);
    expect('openedExpiryDate' in readStockRows(document, 'medicineStock')[0]).toBe(false);
  });

  it('unreadableOpenedExpiry_rejectsTheTable', () => {
    medicine([...base, 'Açılmış Kalan Miktar Son Kullanma Tarihi', 'Seri Numarası'], ['', 'K', 'CLAVON', '50 ml', '0', '01.02.2027', '49,00', 'yarın', '63016']);
    expect(readStockTable(document, 'medicineStock')).toBeNull();
  });
});
