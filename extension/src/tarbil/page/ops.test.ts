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
function addressCombos(P: string, log: string[], selected = 'dolu') {
  const ids = ['cbxNeigbourhood', 'cbxDistrict', 'cbxProvince'].map((n) => `${P}UCProvinceDistrictNeigbourhood_${n}`);
  const html = ids.map((id) => `<div id="${id}"></div>`).join('');
  const comps = Object.fromEntries(ids.map((id) => {
    const name = id.split('_').pop()!;
    return [id, {
      get_value: () => selected,
      findItemByValue: (v: string) => (v === '' ? { select: () => log.push(`clear:${name}`) } : null),
    }];
  }));
  return { html, comps };
}

describe('page ops', () => {
  it('types the chip into the search box and presses Ara', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_';
    const prm = instantPrm();
    const log: string[] = [];
    const address = addressCombos(P, log);
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

  it('rejects an unknown stock page', async () => {
    const env: TelerikEnv = { doc: document, find: () => null, prm: () => instantPrm(), isReady: () => true };
    await expect(createPageOps(env).loadStockTable({ page: 'vaccineReceipt' })).rejects.toMatchObject({ code: 'BAD_INPUT' });
  });

  it('searches by passport: clears the chip box, types the passport and presses Ara', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_';
    const prm = instantPrm();
    const log: string[] = [];
    const address = addressCombos(P, log);
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
});
