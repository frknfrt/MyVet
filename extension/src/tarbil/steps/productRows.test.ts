// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { normalizeSerial, pickStockRow, readProductEditRow, readStockPopupRows } from './productRows';

const SP = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_radGridStock_ctl00';
const PG = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_RadGridProduct_ctl00';

function popup(rows: [string, string, string][]) {
  document.body.innerHTML = `<table id="${SP}"><thead><tr><th></th><th>Aşı Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
    <th>Son Kullanma Tarihi</th><th>Ürün Miktarı</th><th>Ruhsat Sahibi Tip</th><th>Ruhsat Sahibi İsim</th></tr></thead><tbody>${rows
      .map(([name, serial, skt], i) => `<tr id="${SP}__${i}"><td><a id="${SP}_ctl0${4 + 2 * i}_SelectlinkButton">Seç</a></td>
        <td>${name}</td><td>Flakon</td><td>${serial}</td><td>${skt}</td><td>3</td><td>Firma</td><td>X</td></tr>`)
      .join('')}</tbody></table>`;
}

describe('stock popup rows', () => {
  it('reads name, serial, expiry and the Seç link of each row', () => {
    popup([['Biocan R', ' 665932 ', '31.01.2027']]);

    expect(readStockPopupRows(document)).toEqual([
      { productName: 'Biocan R', serial: '665932', expiryDate: '2027-01-31', linkId: `${SP}_ctl04_SelectlinkButton` },
    ]);
  });

  it('picks the single row with the same serial and product name', () => {
    popup([['Biocan R', '665932', '31.01.2027'], ['Biocan DHPPI', '145932', '31.01.2027']]);

    const pick = pickStockRow(readStockPopupRows(document), { serial: '665932', productName: 'biocan  r', today: '2026-10-04' });

    expect(pick).toMatchObject({ kind: 'one', row: { serial: '665932' } });
  });

  it('accepts any name when Vetly has no TARBIL product name', () => {
    popup([['Biocan R', '665932', '31.01.2027']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '665932', productName: null, today: '2026-10-04' }).kind).toBe('one');
  });

  it('refuses a serial whose product name differs', () => {
    popup([['Nobivac', '665932', '31.01.2027']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '665932', productName: 'Biocan R', today: '2026-10-04' }).kind).toBe('nameMismatch');
  });

  it('refuses to choose between several rows', () => {
    popup([['Biocan R', '665932', '31.01.2027'], ['Biocan R', '665932', '31.01.2027']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '665932', productName: 'Biocan R', today: '2026-10-04' }).kind).toBe('many');
  });

  it('does not pick an expired serial', () => {
    popup([['Biocan R', '665932', '01.10.2026']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '665932', productName: 'Biocan R', today: '2026-10-04' }).kind).toBe('expired');
  });

  it('does not pick a row whose expiry date cannot be read', () => {
    popup([['Biocan R', '665932', '']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '665932', productName: 'Biocan R', today: '2026-10-04' }).kind).toBe('unknownExpiry');
  });

  it('finds nothing for an unknown serial', () => {
    popup([['Biocan R', '665932', '31.01.2027']]);
    expect(pickStockRow(readStockPopupRows(document), { serial: '000', productName: null, today: '2026-10-04' })).toEqual({ kind: 'none' });
  });

  it('normalizes serials', () => {
    expect(normalizeSerial(' ab 12-3 ')).toBe('AB12-3');
  });
});

describe('product edit row', () => {
  it('reads the serial of the product row TARBIL draws in the grid header', () => {
    document.body.innerHTML = `<table id="${PG}"><thead><tr class="rgCommandRow"><td>Ürün Ekle</td></tr>
      <tr><th>Detay</th><th>Detay</th><th>Ürün</th><th>Takdim Şekli</th><th>Seri Numarası</th><th>Son Kullanım Tarihi</th>
      <th>Stok Miktarı</th><th>Stok Tipi</th><th>Ürün Adet</th><th></th><th>Sil</th></tr>
      <tr class="rgEditRow"><td></td><td></td><td><input></td><td>Flakon</td><td> 665932 </td><td>31.01.2027</td>
      <td>18 Adet</td><td>Parça Stok</td><td><input></td><td></td><td></td></tr></thead>
      <tbody><tr class="rgNoRecords"><td colspan="11">Kayıt Bulunamadı.</td></tr></tbody></table>`;

    expect(readProductEditRow(document)).toEqual({ serial: '665932' });
  });

  it('returns null while no product row is open', () => {
    document.body.innerHTML = `<table id="${PG}"><thead><tr><th>Seri Numarası</th></tr></thead><tbody></tbody></table>`;
    expect(readProductEditRow(document)).toBeNull();
  });
});
