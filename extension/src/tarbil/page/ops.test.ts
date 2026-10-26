// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { createPageOps } from './ops';
import type { Prm, TelerikEnv } from './telerik';

function instantPrm(): Prm & { fire: () => void } {
  const begin = new Set<Function>();
  const end = new Set<Function>();
  return {
    add_beginRequest: (h) => begin.add(h),
    remove_beginRequest: (h) => begin.delete(h),
    add_endRequest: (h) => end.add(h),
    remove_endRequest: (h) => end.delete(h),
    fire: () => { begin.forEach((h) => h()); end.forEach((h) => h(null, { get_error: () => null })); },
  };
}


/** Arama penceresindeki il/ilce/mahalle kutulari (2026-10-06 canli: klinik adresiyle dolu, ilk secenek "Seciniz" = ""). */
function addressCombos(P: string, log: string[], selected = 'dolu', onSelect: () => void = () => undefined) {
  const ids = ['cbxNeigbourhood', 'cbxDistrict', 'cbxProvince'].map((n) => `${P}UCProvinceDistrictNeigbourhood_${n}`);
  const html = ids.map((id) => `<div id="${id}"></div>`).join('');
  const comps = Object.fromEntries(ids.map((id) => {
    const name = id.split('_').pop()!;
    // Gercek TARBIL: bosaltma da sunucuya kisa bir istek gonderir (2026-10-06 canli).
    const seciniz = { get_value: () => '', select: () => { log.push(`clear:${name}`); onSelect(); } };
    return [id, {
      get_value: () => selected,
      // Gercek Telerik gibi: findItemByValue('') bos degerli "Seciniz"i BULAMAZ (2026-10-06 canli).
      findItemByValue: () => null,
      get_items: () => ({ get_count: () => 2, getItem: (i: number) => (i === 0 ? seciniz : { get_value: () => 'x', select: () => log.push('wrong') }) }),
    }];
  }));
  return { html, comps };
}

describe('page ops', () => {
  it('types the chip into the search box and presses Ara', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_';
    const prm = instantPrm();
    const log: string[] = [];
    const address = addressCombos(P, log, 'dolu', () => prm.fire());
    document.body.innerHTML = `<input id="${P}txtChipNo"><a id="${P}btnSearch"></a>${address.html}`;
    const comps: Record<string, Record<string, unknown>> = {
      ...address.comps,
      [`${P}txtChipNo`]: { set_value: (v: string) => log.push(`chip:${v}`) },
      [`${P}btnSearch`]: { click: () => { log.push('search'); prm.fire(); } },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchChip({ chip: '900000000000001' });

    // Il/ilce/mahalle filtresi kaldirilir: hayvan baska ilde kayitli olabilir.
    expect(log).toEqual(['clear:cbxNeigbourhood', 'clear:cbxDistrict', 'clear:cbxProvince', 'chip:900000000000001', 'search']);
  });

  it('re-selects the animal type when the value is set but the form never posted back', async () => {
    const ID = 'ctl00_X_cntVACCINEBodyContent_cbxAnimalType';
    document.body.innerHTML = `<div id="${ID}"></div>`;
    const prm = instantPrm();
    const log: string[] = [];
    const combo = {
      get_value: () => 'cat',
      clearSelection: () => log.push('clear'),
      findItemByValue: () => ({ select: () => { log.push('select'); prm.fire(); } }),
    };
    const env: TelerikEnv = { doc: document, find: (id) => (id === ID ? combo : null), prm: () => prm, isReady: () => true };

    await createPageOps(env).selectAnimalType({ value: 'cat' });

    expect(log).toEqual(['clear', 'select']);
  });

  it('refuses to click anything that is not a search-result checkbox', async () => {
    document.body.innerHTML = '<a id="ctl00_X_cntVACCINEBodyContent_btnInsert"><input id="ins" type="button"></a>';
    let clicked = false;
    document.getElementById('ctl00_X_cntVACCINEBodyContent_btnInsert')!.addEventListener('click', () => { clicked = true; });
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => instantPrm(), isReady: () => true };

    await expect(createPageOps(env).checkRow({ checkboxId: 'ctl00_X_cntVACCINEBodyContent_btnInsert' })).rejects.toMatchObject({ code: 'NOT_FOUND' });
    expect(clicked).toBe(false);
  });

  it('checks a result row by clicking its checkbox and waits for the postback', async () => {
    document.body.innerHTML =
      '<table id="ctl00_X_UCVaccineKKBSAnimalSearch_radGridAnimal_ctl00"><tbody><tr><td><input type="checkbox" id="cb1"></td></tr></tbody></table>';
    const prm = instantPrm();
    document.getElementById('cb1')!.addEventListener('click', () => prm.fire());
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => prm, isReady: () => true };

    await expect(createPageOps(env).checkRow({ checkboxId: 'cb1' })).resolves.toEqual({ postback: true });
    expect((document.getElementById('cb1') as HTMLInputElement).checked).toBe(true);
  });

  it('clicks an allowlisted button by page and key', async () => {
    const ID = 'ctl00_X_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00_ctl02_ctl00_bntPetVet';
    document.body.innerHTML = `<a id="${ID}"></a>`;
    const prm = instantPrm();
    let clicked = 0;
    const env: TelerikEnv = { doc: document, find: (id) => (id === ID ? { click: () => { clicked++; prm.fire(); } } : null), prm: () => prm, isReady: () => true };

    await createPageOps(env).clickAllowed({ page: 'vaccineReceipt', button: 'petVet' });

    expect(clicked).toBe(1);
  });

  it('refuses to click Onayla even when asked through the bridge', async () => {
    const ID = 'ctl00_X_cntVACCINEBodyContent_btnInsert';
    document.body.innerHTML = `<a id="${ID}"></a>`;
    let clicked = 0;
    const env: TelerikEnv = { doc: document, find: () => ({ click: () => { clicked++; } }), prm: () => instantPrm(), isReady: () => true };
    const ops = createPageOps(env);

    await expect(ops.clickAllowed({ page: 'vaccineReceipt', button: 'insert' })).rejects.toMatchObject({ code: 'NOT_ALLOWED' });
    expect(ops).not.toHaveProperty('clickPetVet');
    expect(ops).not.toHaveProperty('transfer');
    expect(clicked).toBe(0);
  });

  it('loads the whole medicine stock table: presses Ara, then shows all rows', async () => {
    const BTN = 'ctl00_ContentHolder_btnSearch';
    const GRID = 'ctl00_ContentHolder_radGridStockSearch';
    document.body.innerHTML = `<a id="${BTN}"></a><div id="${GRID}"></div>`;
    const prm = instantPrm();
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [BTN]: { click: () => { log.push('ara'); prm.fire(); } },
      [GRID]: { get_masterTableView: () => ({ get_pageSize: () => 10, set_pageSize: (n: number) => { log.push(`size:${n}`); prm.fire(); } }) },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).loadStockTable({ page: 'medicineStock' });

    expect(log).toEqual(['ara', 'size:500']);
  });

  it('loads the vaccine Parça Stok table: presses Parça Stok Ara, then shows all rows', async () => {
    const P = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_';
    const BTN = `${P}btnStocklInboxSearch`;
    const GRID = `${P}radGridStockInbox`;
    document.body.innerHTML = `<a id="${BTN}"></a><div id="${GRID}"></div>`;
    const prm = instantPrm();
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [BTN]: { click: () => { log.push('parca-ara'); prm.fire(); } },
      [GRID]: { get_masterTableView: () => ({ get_pageSize: () => 15, set_pageSize: (n: number) => { log.push(`size:${n}`); prm.fire(); } }) },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).loadVaccinePartStock({});

    expect(log).toEqual(['parca-ara', 'size:500']);
  });

  it('rejects an unknown stock page', async () => {
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => instantPrm(), isReady: () => true };
    await expect(createPageOps(env).loadStockTable({ page: 'vaccineReceipt' })).rejects.toMatchObject({ code: 'BAD_INPUT' });
  });

  it('searches by passport: clears the chip box, types the passport and presses Ara', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_';
    const prm = instantPrm();
    const log: string[] = [];
    const address = addressCombos(P, log, 'dolu', () => prm.fire());
    document.body.innerHTML = `<input id="${P}txtChipNo"><input id="${P}txtPassportNo"><a id="${P}btnSearch"></a>${address.html}`;
    const comps: Record<string, Record<string, unknown>> = {
      ...address.comps,
      [`${P}txtChipNo`]: { set_value: (v: string) => log.push(`chip:${v}`) },
      [`${P}txtPassportNo`]: { set_value: (v: string) => log.push(`passport:${v}`) },
      [`${P}btnSearch`]: { click: () => { log.push('search'); prm.fire(); } },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchPassport({ passport: 'TR-34 AB12' });

    expect(log).toEqual(['clear:cbxNeigbourhood', 'clear:cbxDistrict', 'clear:cbxProvince', 'chip:', 'passport:TR-34 AB12', 'search']);
  });

  it('searches the stock popup by serial', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_';
    document.body.innerHTML = `<input id="${P}txtSerialNo"><a id="${P}btnSearch"></a>`;
    const prm = instantPrm();
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [`${P}txtSerialNo`]: { set_value: (v: string) => log.push(`serial:${v}`) },
      [`${P}btnSearch`]: { click: () => { log.push('ara'); prm.fire(); } },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchSerial({ serial: '665932' });

    expect(log).toEqual(['serial:665932', 'ara']);
  });

  it('clicks Seç only inside the stock popup grid', async () => {
    const G = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_radGridStock_ctl00';
    document.body.innerHTML = `<table id="${G}"><tbody><tr><td><a id="${G}_ctl04_SelectlinkButton">Seç</a></td></tr></tbody></table>
      <a id="other_SelectlinkButton">Seç</a>`;
    let clicked = 0;
    document.getElementById(`${G}_ctl04_SelectlinkButton`)!.addEventListener('click', () => clicked++);
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => null, isReady: () => true };
    const ops = createPageOps(env);

    await ops.selectStockRow({ linkId: `${G}_ctl04_SelectlinkButton` });
    await expect(ops.selectStockRow({ linkId: 'other_SelectlinkButton' })).rejects.toMatchObject({ code: 'NOT_FOUND' });

    expect(clicked).toBe(1);
  });

  it('types the dose count into the product row quantity box', async () => {
    const Q = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_RadGridProduct_ctl00_ctl02_ctl03_txtQuantity';
    document.body.innerHTML = `<input id="${Q}">`;
    const log: string[] = [];
    const env: TelerikEnv = { doc: document, find: (id) => (id === Q ? { set_value: (v: string) => log.push(v) } : null), prm: () => null, isReady: () => true };

    await createPageOps(env).setProductQuantity({ quantity: 1 });

    expect(log).toEqual(['1']);
  });

  it('leaves the address filter alone when it is already empty', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_';
    const prm = instantPrm();
    const log: string[] = [];
    const address = addressCombos(P, log, '');
    document.body.innerHTML = `<input id="${P}txtChipNo"><a id="${P}btnSearch"></a>${address.html}`;
    const comps: Record<string, Record<string, unknown>> = {
      ...address.comps,
      [`${P}txtChipNo`]: { set_value: (v: string) => log.push(`chip:${v}`) },
      [`${P}btnSearch`]: { click: () => { log.push('search'); prm.fire(); } },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchChip({ chip: '900000000000001' });

    expect(log).toEqual(['chip:900000000000001', 'search']);
  });

  it('waits for the real search result, not the late reply of the address clearing (2026-10-06 canli)', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_';
    const begin = new Set<Function>();
    const end = new Set<Function>();
    const prm = {
      add_beginRequest: (h: Function) => begin.add(h),
      remove_beginRequest: (h: Function) => begin.delete(h),
      add_endRequest: (h: Function) => end.add(h),
      remove_endRequest: (h: Function) => end.delete(h),
    };
    const fireEnd = () => end.forEach((h) => h(null, { get_error: () => null }));
    const log: string[] = [];
    // Bosaltma istegi hemen baslar ama yaniti gec gelir; arama yaniti daha da gec.
    const address = addressCombos(P, log, 'dolu', () => {
      begin.forEach((h) => h());
      setTimeout(fireEnd, 10);
    });
    document.body.innerHTML = `<input id="${P}txtChipNo"><input id="${P}txtPassportNo"><a id="${P}btnSearch"></a>${address.html}`;
    const comps: Record<string, Record<string, unknown>> = {
      ...address.comps,
      [`${P}txtChipNo`]: { set_value: () => undefined },
      [`${P}txtPassportNo`]: { set_value: () => undefined },
      [`${P}btnSearch`]: {
        click: () => {
          log.push('search');
          begin.forEach((h) => h());
          setTimeout(() => { log.push('results'); fireEnd(); }, 30);
        },
      },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm as never, isReady: () => true };

    await createPageOps(env).searchPassport({ passport: 'TR0000000' });

    expect(log).toEqual(['clear:cbxNeigbourhood', 'clear:cbxDistrict', 'clear:cbxProvince', 'search', 'results']);
  });

  const RP = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntRECEIPTBodyContent_';

  it('sets the treatment start date on the prescription form', async () => {
    document.body.innerHTML = `<div id="${RP}dpTreatmentStartDate"></div>`;
    const log: string[] = [];
    const picker = { get_selectedDate: () => null, set_selectedDate: (d: Date) => log.push(d.toDateString()) };
    const env: TelerikEnv = { doc: document, find: (id) => (id === `${RP}dpTreatmentStartDate` ? picker : null), prm: () => null, isReady: () => true };

    await createPageOps(env).setTreatmentDate({ iso: '2026-10-09' });

    expect(log).toEqual([new Date(2026, 9, 9).toDateString()]);
  });

  it('selects Muayene/Tedavi Reçetesi only when it is not selected', async () => {
    document.body.innerHTML = `<input type="radio" name="c" id="${RP}rbtnReceiptClass_0"><input type="radio" name="c" id="${RP}rbtnReceiptClass_1" checked>`;
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => null, isReady: () => true };
    const ops = createPageOps(env);

    expect(await ops.ensureTreatmentClass({})).toEqual({ changed: true });
    expect((document.getElementById(`${RP}rbtnReceiptClass_0`) as HTMLInputElement).checked).toBe(true);
    expect(await ops.ensureTreatmentClass({})).toEqual({ changed: false });
  });

  it('checks the disease in the tree and presses the add button', async () => {
    document.body.innerHTML = `<div id="${RP}cbxDisease_i0_treeDisease"></div><div id="${RP}lbxDiagnosedDisease"></div>
      <input type="image" id="${RP}imgAddDisease">`;
    const log: string[] = [];
    let listed = false;
    document.getElementById(`${RP}imgAddDisease`)!.addEventListener('click', (e) => { e.preventDefault(); log.push('add'); listed = true; });
    const node = { get_text: () => 'PNÖMONİ', set_checked: (v: boolean) => log.push(`check:${v}`) };
    const comps: Record<string, Record<string, unknown>> = {
      [`${RP}cbxDisease_i0_treeDisease`]: { findNodeByValue: (v: string) => (v === 'd1' ? node : null) },
      [`${RP}lbxDiagnosedDisease`]: { findItemByValue: () => (listed ? {} : null), findItemByText: () => null },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true };

    expect(await createPageOps(env).addDisease({ value: 'd1' })).toEqual({ added: true, listed: true });
    expect(log).toEqual(['check:true', 'add']);
  });

  it('does not add a disease that is already in the diagnosed list', async () => {
    document.body.innerHTML = `<div id="${RP}cbxDisease_i0_treeDisease"></div><div id="${RP}lbxDiagnosedDisease"></div>
      <input type="image" id="${RP}imgAddDisease">`;
    let clicked = 0;
    document.getElementById(`${RP}imgAddDisease`)!.addEventListener('click', (e) => { e.preventDefault(); clicked++; });
    const comps: Record<string, Record<string, unknown>> = {
      [`${RP}cbxDisease_i0_treeDisease`]: { findNodeByValue: () => ({ get_text: () => 'PNÖMONİ', set_checked: () => undefined }) },
      [`${RP}lbxDiagnosedDisease`]: { findItemByValue: () => null, findItemByText: (t: string) => (t === 'PNÖMONİ' ? {} : null) },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true };

    expect(await createPageOps(env).addDisease({ value: 'd1' })).toEqual({ added: false, listed: true });
    expect(clicked).toBe(0);
  });

  it('rejects a disease that is not in the TARBIL tree', async () => {
    document.body.innerHTML = `<div id="${RP}cbxDisease_i0_treeDisease"></div><div id="${RP}lbxDiagnosedDisease"></div>`;
    const comps: Record<string, Record<string, unknown>> = {
      [`${RP}cbxDisease_i0_treeDisease`]: { findNodeByValue: () => null },
      [`${RP}lbxDiagnosedDisease`]: { findItemByValue: () => null, findItemByText: () => null },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true };

    await expect(createPageOps(env).addDisease({ value: 'x' })).rejects.toMatchObject({ code: 'OPTION_NOT_FOUND' });
  });

  it('searches the medicine stock popup by serial (plain input)', async () => {
    const P = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_';
    document.body.innerHTML = `<input id="${P}txtSerialNumber"><a id="${P}btnSearch"></a>`;
    const prm = instantPrm();
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = { [`${P}btnSearch`]: { click: () => { log.push('ara'); prm.fire(); } } };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchMedicineSerial({ serial: 'KV04Z81' });

    expect((document.getElementById(`${P}txtSerialNumber`) as HTMLInputElement).value).toBe('KV04Z81');
    expect(log).toEqual(['ara']);
  });

  it('clicks Seç inside the medicine stock popup grid', async () => {
    const G = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_radGridStock_ctl00';
    document.body.innerHTML = `<table id="${G}"><tbody><tr><td><a id="${G}_ctl04_SelectlinkButton">Seç</a></td></tr></tbody></table>`;
    let clicked = 0;
    document.getElementById(`${G}_ctl04_SelectlinkButton`)!.addEventListener('click', () => clicked++);
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => null, isReady: () => true };

    await createPageOps(env).selectStockRow({ linkId: `${G}_ctl04_SelectlinkButton` });

    expect(clicked).toBe(1);
  });

  it('writes the same number into Kullanılacak Adet and Kullanılacak Miktarı', async () => {
    const R = `${RP}RadGridStock_ctl00_ctl02_ctl03_`;
    document.body.innerHTML = `<input id="${R}txtStockQuantity"><input id="${R}txtStockAmount">`;
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [`${R}txtStockQuantity`]: { set_value: (v: string) => log.push(`adet:${v}`) },
      [`${R}txtStockAmount`]: { set_value: (v: string) => log.push(`miktar:${v}`) },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true };
    const ops = createPageOps(env);

    await ops.setStockQuantities({ quantity: 2 });
    await expect(ops.setStockQuantities({ quantity: 0 })).rejects.toMatchObject({ code: 'BAD_INPUT' });

    expect(log).toEqual(['adet:2', 'miktar:2']);
  });

  it('writes only the amount for an ml line and leaves Adet alone (spec 2026-10-24)', async () => {
    const R = `${RP}RadGridStock_ctl00_ctl02_ctl03_`;
    document.body.innerHTML = `<input id="${R}txtStockQuantity"><input id="${R}txtStockAmount">`;
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [`${R}txtStockQuantity`]: { set_value: (v: string) => log.push(`adet:${v}`) },
      [`${R}txtStockAmount`]: { set_value: (v: string) => log.push(`miktar:${v}`) },
    };
    const ops = createPageOps({ doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true });

    await ops.setStockQuantities({ quantity: 0, amount: 1.5 });
    await expect(ops.setStockQuantities({ quantity: 0, amount: 0 })).rejects.toMatchObject({ code: 'BAD_INPUT' });

    expect(log).toEqual(['miktar:1,5']);
  });

  it('amountBelowMinimumAfterRounding_isNotWritten (spec 2026-10-25)', async () => {
    const R = `${RP}RadGridStock_ctl00_ctl02_ctl03_`;
    document.body.innerHTML = `<input id="${R}txtStockQuantity"><input id="${R}txtStockAmount">`;
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [`${R}txtStockQuantity`]: { set_value: (v: string) => log.push(`adet:${v}`) },
      [`${R}txtStockAmount`]: { set_value: (v: string) => log.push(`miktar:${v}`) },
    };
    const ops = createPageOps({ doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true });

    await expect(ops.setStockQuantities({ quantity: 0, amount: 0.004 })).rejects.toMatchObject({ code: 'BAD_INPUT' });
    expect(log).toEqual([]);
  });

  it('amountIsWrittenWithCommaAndNoDot (spec 2026-10-25)', async () => {
    const R = `${RP}RadGridStock_ctl00_ctl02_ctl03_`;
    document.body.innerHTML = `<input id="${R}txtStockQuantity"><input id="${R}txtStockAmount">`;
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [`${R}txtStockAmount`]: { set_value: (v: string) => log.push(v) },
    };
    const ops = createPageOps({ doc: document, find: (id) => comps[id] ?? null, prm: () => null, isReady: () => true });

    await ops.setStockQuantities({ quantity: 0, amount: 1234.5 });
    await ops.setStockQuantities({ quantity: 0, amount: 0.01 });
    await ops.setStockQuantities({ quantity: 0, amount: 2.675 });
    expect(log).toEqual(['1234,5', '0,01', '2,68']);
    expect(log.every((v) => !v.includes('.'))).toBe(true);
  });

  it('chooses Açık Stok before searching the medicine popup when asked (spec 2026-10-24)', async () => {
    const P = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_';
    document.body.innerHTML = `<input type="radio" id="${P}rbtnStockType_1"><input id="${P}txtSerialNumber"><a id="${P}btnSearch"></a>`;
    const prm = instantPrm();
    const log: string[] = [];
    document.getElementById(`${P}rbtnStockType_1`)!.addEventListener('click', () => { log.push('acik'); prm.fire(); });
    const comps: Record<string, Record<string, unknown>> = { [`${P}btnSearch`]: { click: () => { log.push('ara'); prm.fire(); } } };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchMedicineSerial({ serial: '63016', open: true });

    expect(log).toEqual(['acik', 'ara']);
  });

  it('chooses Parça Stok before searching the vaccine popup when asked (spec 2026-10-24)', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_';
    document.body.innerHTML = `<input type="radio" id="${P}rbtnStockType_1"><input id="${P}txtSerialNo"><a id="${P}btnSearch"></a>`;
    const prm = instantPrm();
    const log: string[] = [];
    document.getElementById(`${P}rbtnStockType_1`)!.addEventListener('click', () => { log.push('parca'); prm.fire(); });
    const comps: Record<string, Record<string, unknown>> = {
      [`${P}txtSerialNo`]: { set_value: (v: string) => log.push(`serial:${v}`) },
      [`${P}btnSearch`]: { click: () => { log.push('ara'); prm.fire(); } },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchSerial({ serial: '665932', part: true });

    expect(log).toEqual(['parca', 'serial:665932', 'ara']);
  });

  it('clicks Seç in the open-stock and part-stock lists too (spec 2026-10-24)', async () => {
    const O = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_radGridOpenStock_ctl00';
    const PP = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_radGridPieceStock_ctl00';
    document.body.innerHTML = `<table id="${O}"><tbody><tr><td><a id="${O}_ctl04_SelectlinkButton2">Seç</a></td></tr></tbody></table>
      <table id="${PP}"><tbody><tr><td><a id="${PP}_ctl04_SelectlinkButton">Seç</a></td></tr></tbody></table>`;
    let clicked = 0;
    for (const id of [`${O}_ctl04_SelectlinkButton2`, `${PP}_ctl04_SelectlinkButton`]) document.getElementById(id)!.addEventListener('click', () => clicked++);
    const ops = createPageOps({ doc: document, find: () => null, prm: () => null, isReady: () => true });

    await ops.selectStockRow({ linkId: `${O}_ctl04_SelectlinkButton2` });
    await ops.selectStockRow({ linkId: `${PP}_ctl04_SelectlinkButton` });

    expect(clicked).toBe(2);
  });
});
