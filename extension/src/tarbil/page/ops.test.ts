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

describe('page ops', () => {
  it('types the chip into the search box and presses Ara', async () => {
    const P = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_';
    document.body.innerHTML = `<input id="${P}txtChipNo"><a id="${P}btnSearch"></a>`;
    const prm = instantPrm();
    const log: string[] = [];
    const comps: Record<string, Record<string, unknown>> = {
      [`${P}txtChipNo`]: { set_value: (v: string) => log.push(`chip:${v}`) },
      [`${P}btnSearch`]: { click: () => { log.push('search'); prm.fire(); } },
    };
    const env: TelerikEnv = { doc: document, find: (id) => comps[id] ?? null, prm: () => prm, isReady: () => true };

    await createPageOps(env).searchChip({ chip: '900000000000001' });

    expect(log).toEqual(['chip:900000000000001', 'search']);
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
});
