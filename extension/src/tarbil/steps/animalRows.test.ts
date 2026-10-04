// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { normalizeChip, pickAnimal, readReceiptChips, readSearchRows } from './animalRows';

const SP = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_radGridAnimal_ctl00';

function searchTable(rows: string) {
  document.body.innerHTML = `<table id="${SP}"><thead><tr><th></th><th>Adı</th><th>Çip No</th><th>Pasaport No</th>
    <th>Tür</th><th>Irk</th><th>Cinsiyet</th><th>Renk</th><th>Doğum Tarihi</th><th>Durumu</th><th>Hayvan Sahibi</th>
    <th>Anne Çip No</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function srow(i: number, chip: string, status = 'CANLI', mother = '', passport = 'TR00000000') {
  return `<tr id="${SP}__${i}"><td><input type="checkbox" id="${SP}_ctl0${4 + 2 * i}_gridchkBoxAnimalIscheckedTempColumn"></td>
    <td>Ad</td><td>${chip}</td><td>${passport}</td><td>Kedi</td><td>Irk</td><td>Erkek</td><td>Gri</td><td>01/01/23</td>
    <td> ${status} </td><td></td><td>${mother}</td></tr>`;
}

const RP = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00';

function receiptTable(rows: string) {
  document.body.innerHTML = `<table id="${RP}"><thead><tr class="rgCommandRow"><td colspan="7">butonlar</td></tr>
    <tr><th></th><th style="display:none">&nbsp;</th><th>Sistem Küpe/Çip No</th><th>Hayvan Doğum Tarihi</th>
    <th>Hayvan Cinsiyeti</th><th>Hayvan Irkı</th><th>Hayvan Eşgali</th><th>Sil</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function rrow(i: number, chip: string) {
  return `<tr id="${RP}__${i}"><td><input type="checkbox"></td><td style="display:none"></td><td>
    ${chip}
    </td><td>1.1.2023 00:00:00</td><td>Erkek</td><td>Irk</td><td></td><td></td></tr>`;
}

describe('normalizeChip', () => {
  it('keeps digits only', () => {
    expect(normalizeChip(' 900 000-000000001 ')).toBe('900000000000001');
    expect(normalizeChip(null)).toBe('');
  });
});

describe('readSearchRows', () => {
  it('reads chip, status and checkbox id of each result row', () => {
    searchTable(srow(0, '900000000000001'));

    expect(readSearchRows(document)).toEqual([
      { rowId: `${SP}__0`, chip: '900000000000001', passport: 'TR00000000', status: 'CANLI', checkboxId: `${SP}_ctl04_gridchkBoxAnimalIscheckedTempColumn` },
    ]);
  });

  it('returns no rows when the grid is absent', () => {
    document.body.innerHTML = '<div></div>';
    expect(readSearchRows(document)).toEqual([]);
  });
});

describe('pickAnimal', () => {
  it('picks the single alive exact match', () => {
    searchTable(srow(0, '900000000000001') + srow(1, '900000000000002'));

    const result = pickAnimal(readSearchRows(document), { chip: '900000000000002' });

    expect(result).toMatchObject({ kind: 'one', row: { rowId: `${SP}__1` } });
  });

  it('does not match on the mother chip column', () => {
    searchTable(srow(0, '900000000000009', 'CANLI', '900000000000001'));

    expect(pickAnimal(readSearchRows(document), { chip: '900000000000001' })).toEqual({ kind: 'none' });
  });

  it('refuses to choose between several matches', () => {
    searchTable(srow(0, '900000000000001') + srow(1, '900000000000001'));

    expect(pickAnimal(readSearchRows(document), { chip: '900000000000001' })).toEqual({ kind: 'many' });
  });

  it('does not auto-select an animal that is not alive', () => {
    searchTable(srow(0, '900000000000001', 'ÖLÜ'));

    expect(pickAnimal(readSearchRows(document), { chip: '900000000000001' })).toMatchObject({ kind: 'notAlive', row: { status: 'ÖLÜ' } });
  });
});

describe('pickAnimal by passport', () => {
  it('matches the passport ignoring spaces and case when the patient has no chip', () => {
    searchTable(srow(0, '900000000000001', 'CANLI', '', 'TR-34 AB12') + srow(1, '900000000000002', 'CANLI', '', 'TR-34 AB99'));

    expect(pickAnimal(readSearchRows(document), { passport: 'tr-34ab12' })).toMatchObject({ kind: 'one', row: { rowId: `${SP}__0` } });
  });

  it('prefers the chip when both are known', () => {
    searchTable(srow(0, '900000000000001', 'CANLI', '', 'TR1'));

    expect(pickAnimal(readSearchRows(document), { chip: '900000000000009', passport: 'TR1' })).toEqual({ kind: 'none' });
  });
});

describe('readReceiptChips', () => {
  it('reads chips of animals added to the receipt form', () => {
    receiptTable(rrow(0, '900000000000001'));

    expect(readReceiptChips(document)).toEqual(['900000000000001']);
  });

  it('ignores the empty-grid row', () => {
    receiptTable('<tr class="rgNoRecords"><td colspan="8">Kayıt Bulunamadı.</td></tr>');

    expect(readReceiptChips(document)).toEqual([]);
  });
});
