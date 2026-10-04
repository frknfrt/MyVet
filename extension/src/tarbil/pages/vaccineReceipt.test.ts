// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import { createFlowStore, type FlowStep } from '../../shared/flowStore';
import type { BackgroundRequest } from '../../shared/messages';
import type { Submission } from '../../shared/types';
import type { CardView } from '../core/card';
import { POPUP_WAIT_MS, createReceiptFlow } from './vaccineReceipt';
import type { Send } from '../steps/findAnimal';
import { ANIMAL_TYPE } from '../selectors';

const P = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_';
const GRID = `${P}ReceiptAddOtherAnimal_RadOtherAnimal_ctl00`;
const CHIP = '900000000000001';

function page({ withDate = true } = {}) {
  document.body.innerHTML = `
    ${withDate ? `<input id="${P}dpApplicationDate">` : ''}
    <a id="${P}btnInsert"><input id="${P}btnInsert_input" type="button" value="Onayla"></a>
    <table id="${GRID}"><thead><tr><th></th><th style="display:none"></th><th>Sistem Küpe/Çip No</th></tr></thead><tbody></tbody></table>`;
}

function addAnimalRow(chip: string) {
  document.querySelector(`#${GRID} tbody`)!.insertAdjacentHTML('beforeend', `<tr id="${GRID}__0"><td></td><td></td><td>${chip}</td></tr>`);
}

function showSuccess() {
  document.body.insertAdjacentHTML('beforeend', '<div id="bodyCPH_ContentPlaceHolder1_UCVACCINENotification_pnlNotifiSuccess">Kaydedildi</div>');
}

const base = {
  id: 's1', vaccinationRecordId: 'v1', status: 'PENDING', patientName: 'Pamuk', microchipNumber: CHIP, passportNumber: null,
  speciesId: null, speciesName: 'Kedi', breedName: null, sex: null, birthDate: null, vaccineName: 'Kuduz Aşısı',
  lotNumber: 'L1', administeredDate: '2026-10-03', submittedAt: null, confirmationMethod: null, tarbilReference: null,
  vaccineKey: 'kuduz aşisi', vaccineMapping: null, speciesMapping: null,
} satisfies Submission;

async function setup(sub: Partial<Submission> = {}, step: FlowStep | null = 'armed', extra: Record<string, number> = {}) {
  let t = 100_000;
  const now = () => t;
  const flow = createFlowStore(memoryStore(), now);
  if (step) {
    await flow.arm('s1');
    if (step !== 'armed' || Object.keys(extra).length) await flow.update('s1', { step, ...extra });
  }
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const sent: BackgroundRequest[] = [];
  const submission = { ...base, ...sub };
  const send = (async (req: BackgroundRequest) => {
    sent.push(req);
    return req.type === 'GET_ACTIVE' ? { ok: true, data: submission } : { ok: true, data: submission };
  }) as Send;
  const shown: CardView[] = [];
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined };
  const timers: (() => void)[] = [];
  let observer: () => void = () => undefined;
  const receipt = createReceiptFlow({
    bridge, flow, send, doc: document, card, now,
    setTimer: (fn) => { timers.push(fn); },
    observe: (cb) => { observer = cb; return () => undefined; },
  });
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  return { receipt, flow, calls, sent, shown, timers, text, mutate: () => observer(), advance: (ms: number) => { t += ms; } };
}

describe('receiptFlow', () => {
  it('fills date and species, then opens the PetVet search', async () => {
    page();
    const { receipt, calls, flow } = await setup();

    await receipt.start();

    expect(calls.map((c) => c.op)).toEqual(['ready', 'setDate', 'selectAnimalType', 'clickAllowed']);
    expect(calls[3].args).toEqual({ page: 'vaccineReceipt', button: 'petVet' });
    expect(calls[1].args).toEqual({ iso: '2026-10-03' });
    expect(calls[2].args).toEqual({ value: ANIMAL_TYPE.CAT });
    expect((await flow.get())?.step).toBe('searching');
  });

  it('does nothing on the page when the patient has no chip', async () => {
    page();
    const { receipt, calls, text } = await setup({ microchipNumber: null, passportNumber: null });

    await receipt.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('Çip ve pasaport numarası yok');
  });

  it('fills the form when the patient has only a passport number', async () => {
    page();
    const { receipt, calls, flow } = await setup({ microchipNumber: null, passportNumber: 'TR-34 AB12' });

    await receipt.start();

    expect(calls.map((c) => c.op)).toEqual(['ready', 'setDate', 'selectAnimalType', 'clickAllowed']);
    expect((await flow.get())?.step).toBe('searching');
  });

  it('verifies a passport-only animal by the chip TARBIL showed in the search', async () => {
    page();
    const { receipt, flow, mutate, text } = await setup({ microchipNumber: null, passportNumber: 'TR-34 AB12' }, 'transferred');
    await flow.update('s1', { stepData: { matchedChip: CHIP } });
    await receipt.start();

    addAnimalRow(CHIP);
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('awaitingConfirm'));
    expect(text()).toContain('Ürün Ekle');
  });

  it('does nothing on the page for unsupported species', async () => {
    page();
    const { receipt, calls, text } = await setup({ speciesName: 'Tavşan' });

    await receipt.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('yalnız kedi ve köpek');
  });

  it('warns instead of filling an already submitted vaccination', async () => {
    page();
    const { receipt, calls, text } = await setup({ status: 'SUBMITTED', submittedAt: '2026-10-02T10:00:00Z' });

    await receipt.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('Tekrar girmeyin');
  });

  it('asks for e-Devlet login when the form is missing', async () => {
    page({ withDate: false });
    const { receipt, calls, text } = await setup();

    await receipt.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('e-Devlet');
  });

  it('waits for the vet when the flow is not armed', async () => {
    page();
    const { receipt, calls, shown } = await setup({}, null);

    await receipt.start();

    expect(calls).toEqual([]);
    expect(shown.at(-1)?.actions.map((a) => a.id)).toContain('fill');
  });

  it('moves to awaiting confirmation when the matching animal lands on the form', async () => {
    page();
    const { receipt, flow, mutate, text } = await setup({}, 'transferred');
    await receipt.start();

    addAnimalRow(CHIP);
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('awaitingConfirm'));
    expect(text()).toContain('Ürün Ekle');
  });

  it('warns when a different animal lands on the form', async () => {
    page();
    const { receipt, flow, mutate, text } = await setup({}, 'needsVet');
    await receipt.start();

    addAnimalRow('900000000000777');
    mutate();

    await vi.waitFor(() => expect(text()).toContain('aynı değil'));
    expect((await flow.get())?.step).toBe('needsVet');
  });

  it('marks the vaccination submitted when success follows an Onayla click', async () => {
    page();
    const { receipt, sent, flow, mutate } = await setup({}, 'awaitingConfirm');
    await receipt.start();

    (document.getElementById(`${P}btnInsert_input`) as HTMLInputElement).click();
    await vi.waitFor(async () => expect((await flow.get())?.insertClickedAt).toBeDefined());
    showSuccess();
    mutate();

    await vi.waitFor(() =>
      expect(sent).toContainEqual({ type: 'MARK_SUBMITTED', id: 's1', method: 'AUTO', tarbilReference: null }),
    );
    expect((await flow.get())?.step).toBe('done');
  });

  it('does not mark submitted when success appears without an Onayla click', async () => {
    page();
    const { receipt, sent, mutate } = await setup({}, 'awaitingConfirm');
    await receipt.start();

    showSuccess();
    mutate();
    await new Promise((r) => setTimeout(r, 20));

    expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(false);
  });

  it('catches success after a full page reload that followed an Onayla click', async () => {
    page();
    showSuccess();
    const { receipt, sent } = await setup({}, 'awaitingConfirm', { insertClickedAt: 99_000 });

    await receipt.start();

    await vi.waitFor(() => expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(true));
  });

  it('offers to reopen the search window when the popup never picks up', async () => {
    page();
    const { receipt, timers, shown, advance } = await setup();
    await receipt.start();

    advance(POPUP_WAIT_MS);
    timers.forEach((fn) => fn());

    await vi.waitFor(() => expect(shown.at(-1)?.actions.map((a) => a.id)).toContain('petvet'));
  });

  it('ignores a success panel that was already on the page before Onayla', async () => {
    page();
    showSuccess();
    const { receipt, sent, flow, mutate } = await setup({}, 'awaitingConfirm');
    await receipt.start();

    (document.getElementById(`${P}btnInsert_input`) as HTMLInputElement).click();
    await vi.waitFor(async () => expect((await flow.get())?.insertClickedAt).toBeDefined());
    mutate();
    await new Promise((r) => setTimeout(r, 20));
    expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(false);

    document.getElementById('bodyCPH_ContentPlaceHolder1_UCVACCINENotification_pnlNotifiSuccess')!.remove();
    showSuccess();
    mutate();
    await vi.waitFor(() => expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(true));
  });

  it('marks submitted only once under a burst of page mutations', async () => {
    page();
    const { receipt, sent, flow, mutate } = await setup({}, 'awaitingConfirm');
    await receipt.start();

    (document.getElementById(`${P}btnInsert_input`) as HTMLInputElement).click();
    await vi.waitFor(async () => expect((await flow.get())?.insertClickedAt).toBeDefined());
    showSuccess();
    mutate();
    mutate();
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('done'));
    await new Promise((r) => setTimeout(r, 20));
    expect(sent.filter((r) => r.type === 'MARK_SUBMITTED')).toHaveLength(1);
  });

  it('starts filling when the same vaccination is armed again from the side panel', async () => {
    page();
    const { receipt, flow, calls } = await setup({}, null);
    await receipt.start();
    expect(calls).toEqual([]);

    await flow.arm('s1');
    receipt.flowChanged(await flow.get());

    await vi.waitFor(() => expect(calls.map((c) => c.op)).toContain('clickAllowed'));
  });

  it('offers the search-window button right after asking PetVet to open', async () => {
    page();
    const { receipt, shown } = await setup();

    await receipt.start();

    expect(shown.at(-1)?.actions.map((a) => a.id)).toContain('petvet');
  });

  it('does not leave the flow armed when it cannot fill', async () => {
    page();
    const { receipt, flow } = await setup({ microchipNumber: null });

    await receipt.start();

    expect((await flow.get())?.step).toBe('error');
  });

  it('ignores the empty notification panels TARBIL renders on every page load', async () => {
    page();
    document.body.insertAdjacentHTML('beforeend', '<div id="bodyCPH_ContentPlaceHolder1_UCVACCINENotification_pnlNotifiSuccess"></div>');
    const { receipt, sent } = await setup({}, 'awaitingConfirm', { insertClickedAt: 99_000 });

    await receipt.start();
    await new Promise((r) => setTimeout(r, 20));

    expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(false);
  });

  it('stops and reports when a page step fails', async () => {
    page();
    const flow = createFlowStore(memoryStore(), () => 100_000);
    await flow.arm('s1');
    const shown: CardView[] = [];
    const receipt = createReceiptFlow({
      bridge: {
        call: async (op: string) => {
          if (op === 'selectAnimalType') throw Object.assign(new Error('x'), { code: 'OPTION_NOT_FOUND' });
          return undefined as never;
        },
      },
      flow,
      send: (async () => ({ ok: true, data: base })) as Send,
      doc: document,
      card: { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined },
      now: () => 100_000,
      setTimer: () => undefined,
      observe: () => () => undefined,
    });

    await receipt.start();

    expect(await flow.get()).toMatchObject({ step: 'error', message: 'OPTION_NOT_FOUND' });
    expect(shown.at(-1)?.actions.map((a) => a.id)).toContain('fill');
  });
});
