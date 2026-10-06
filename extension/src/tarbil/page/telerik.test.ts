// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { clickButton, selectComboValue, setDate, withPostback, type Prm, type TelerikComponent, type TelerikEnv } from './telerik';

type Handler = (...a: unknown[]) => void;

function fakePrm() {
  const begin = new Set<Handler>();
  const end = new Set<Handler>();
  return {
    add_beginRequest: (h: Handler) => begin.add(h),
    remove_beginRequest: (h: Handler) => begin.delete(h),
    add_endRequest: (h: Handler) => end.add(h),
    remove_endRequest: (h: Handler) => end.delete(h),
    begin: () => begin.forEach((h) => h()),
    end: (error: unknown = null) => end.forEach((h) => h(null, { get_error: () => error })),
  };
}

function env(prm: ReturnType<typeof fakePrm>, comps: Record<string, TelerikComponent> = {}): TelerikEnv {
  return { doc: document, find: (id) => comps[id] ?? null, prm: () => prm as unknown as Prm, isReady: () => true };
}

afterEach(() => vi.useRealTimers());

describe('withPostback', () => {
  it('resolves after the triggered postback ends', async () => {
    const prm = fakePrm();
    await expect(withPostback(env(prm), () => { prm.begin(); prm.end(); })).resolves.toEqual({ postback: true });
  });

  it('resolves without postback when none starts within the grace period', async () => {
    vi.useFakeTimers();
    const prm = fakePrm();
    const p = withPostback(env(prm), () => undefined);
    vi.advanceTimersByTime(1500);
    await expect(p).resolves.toEqual({ postback: false });
  });

  it('rejects when the postback never ends', async () => {
    vi.useFakeTimers();
    const prm = fakePrm();
    const p = withPostback(env(prm), () => prm.begin());
    vi.advanceTimersByTime(10_000);
    await expect(p).rejects.toMatchObject({ code: 'AJAX_TIMEOUT' });
  });

  it('ignores the end of an earlier postback that arrives before its own one starts (2026-10-06 canli)', async () => {
    const prm = fakePrm();
    let settled = false;
    const p = withPostback(env(prm), () => {
      prm.end(); // onceki (ornegin il kutusu bosaltma) isteginin gec gelen yaniti
      setTimeout(() => prm.begin(), 5);
    }).then((r) => {
      settled = true;
      return r;
    });
    await new Promise((r) => setTimeout(r, 20));
    expect(settled).toBe(false);
    prm.end();
    await expect(p).resolves.toEqual({ postback: true });
  });

  it('rejects when the postback reports an error', async () => {
    const prm = fakePrm();
    await expect(withPostback(env(prm), () => { prm.begin(); prm.end(new Error('500')); })).rejects.toMatchObject({ code: 'AJAX_ERROR' });
  });
});

describe('Telerik controls', () => {
  const ID = 'ctl00_X_cntVACCINEBodyContent_cbxAnimalType';

  it('selects a combobox value and waits for its postback', async () => {
    document.body.innerHTML = `<div id="${ID}"></div>`;
    const prm = fakePrm();
    const selected: string[] = [];
    const combo = {
      get_value: () => '',
      findItemByValue: (v: string) => ({ select: () => { selected.push(v); prm.begin(); prm.end(); } }),
    };

    await expect(selectComboValue(env(prm, { [ID]: combo }), '_cntVACCINEBodyContent_cbxAnimalType', 'cat')).resolves.toEqual({ changed: true });
    expect(selected).toEqual(['cat']);
  });

  it('leaves an already selected combobox value alone', async () => {
    document.body.innerHTML = `<div id="${ID}"></div>`;
    const combo = { get_value: () => 'cat', findItemByValue: () => { throw new Error('should not select'); } };

    await expect(selectComboValue(env(fakePrm(), { [ID]: combo }), '_cntVACCINEBodyContent_cbxAnimalType', 'cat')).resolves.toEqual({ changed: false });
  });

  it('fails clearly when a combobox option is missing', async () => {
    document.body.innerHTML = `<div id="${ID}"></div>`;
    const combo = { get_value: () => '', findItemByValue: () => null };

    await expect(selectComboValue(env(fakePrm(), { [ID]: combo }), '_cntVACCINEBodyContent_cbxAnimalType', 'x')).rejects.toMatchObject({ code: 'OPTION_NOT_FOUND' });
  });

  it('fails clearly when the control is not on the page', async () => {
    document.body.innerHTML = '';
    await expect(selectComboValue(env(fakePrm()), '_cntVACCINEBodyContent_cbxAnimalType', 'x')).rejects.toMatchObject({ code: 'NOT_FOUND' });
  });

  it('sets the date picker to the given local date', async () => {
    const DID = 'ctl00_X_cntVACCINEBodyContent_dpApplicationDate';
    document.body.innerHTML = `<input id="${DID}">`;
    const prm = fakePrm();
    let set: Date | null = null;
    const picker = { get_selectedDate: () => new Date(2026, 9, 1), set_selectedDate: (d: Date) => { set = d; prm.begin(); prm.end(); } };

    await expect(setDate(env(prm, { [DID]: picker }), '_cntVACCINEBodyContent_dpApplicationDate', '2026-10-03')).resolves.toEqual({ changed: true });
    expect([set!.getFullYear(), set!.getMonth(), set!.getDate()]).toEqual([2026, 9, 3]);
  });

  it('clicks the inner input when the button has no Telerik component', async () => {
    document.body.innerHTML = '<a id="ctl00_X_btnSearch"><input id="ctl00_X_btnSearch_input" type="button"></a>';
    const clicked = vi.fn();
    document.getElementById('ctl00_X_btnSearch_input')!.addEventListener('click', clicked);
    vi.useFakeTimers();
    const p = clickButton(env(fakePrm()), '_btnSearch');
    vi.advanceTimersByTime(1500);
    await p;
    expect(clicked).toHaveBeenCalledOnce();
  });
});
