// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import { createFlowStore, type FlowStep } from '../../shared/flowStore';
import type { BackgroundRequest } from '../../shared/messages';
import type { Submission } from '../../shared/types';
import type { CardView } from '../core/card';
import { ANIMAL_TYPE } from '../selectors';
import type { Send } from '../steps/findAnimal';
import { createPrescriptionFlow } from './prescriptionReceipt';

const P = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntRECEIPTBodyContent_';
const GRID = `${P}ReceiptAddOtherAnimal_RadOtherAnimal_ctl00`;
const T = `${P}RadGridStock_ctl00`;
const CHIP = '900000000000001';
const HEAD = ['Detay', 'Detay', 'Ürün', 'Takdim Şekli', 'Seri Numarası', 'Son Kullanım Tarihi', 'Stok Miktarı', 'Stok Tipi', 'Kullanılacak Adet', 'Kullanılacak Miktarı', 'Sil'];

function page() {
  document.body.innerHTML = `
    <input id="${P}dpTreatmentStartDate">
    <a id="${P}btnInsert"><input id="${P}btnInsert_input" type="button" value="Reçeteyi Onayla"></a>
    <table id="${GRID}"><thead><tr><th></th><th style="display:none"></th><th>Sistem Küpe/Çip No</th></tr></thead><tbody></tbody></table>
    <div id="stock"></div>`;
}

function addAnimalRow(chip: string) {
  document.querySelector(`#${GRID} tbody`)!.insertAdjacentHTML('beforeend', `<tr id="${GRID}__0"><td></td><td></td><td>${chip}</td></tr>`);
}

/** Ilac tablosu: kaydedilmis satirlar tbody'de; acik ekleme satirinin alanlari ctl02_ctl03 id'leriyle. */
function stockGrid({ saved = [] as [string, string, string][], edit = null as string | null } = {}) {
  document.getElementById('stock')!.innerHTML = `<table id="${T}"><thead><tr>${HEAD.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${
    saved.map(([name, serial, qty], i) => `<tr id="${T}__${i}" class="rgRow"><td></td><td></td><td>${name}</td><td>Kutu</td><td>${serial}</td>
      <td>01.01.2027</td><td>3 Adet</td><td>Bütün Stok</td><td>${qty}</td><td>${qty}</td><td></td></tr>`).join('')
  }</tbody></table>${edit !== null ? `<input id="${T}_ctl02_ctl03_txtStockProductName" value="x"><span id="${T}_ctl02_ctl03_lblSerialNumber">${edit}</span>` : ''}`;
}

function showSuccess() {
  document.body.insertAdjacentHTML('beforeend', '<div id="bodyCPH_ContentPlaceHolder1_UCRECEIPTNotification_pnlNotifiSuccess">Kaydedildi</div>');
}

const LINES = [
  { itemName: 'Advantage', tarbilProductName: 'Advantage % 10', lotNumber: 'KV04Z81', quantity: 1 },
  { itemName: 'Synulox', tarbilProductName: 'SYNULOX', lotNumber: 'S1', quantity: 2 },
];

const base = {
  id: 's1', vaccinationRecordId: 'r1', status: 'PENDING', patientName: 'Pamuk', microchipNumber: CHIP, passportNumber: null,
  speciesId: null, speciesName: 'Kedi', breedName: null, sex: null, birthDate: null, vaccineName: 'Reçete',
  lotNumber: null, tarbilProductName: null, administeredDate: null, submittedAt: null, confirmationMethod: null, tarbilReference: null,
  vaccineKey: '', vaccineMapping: null, speciesMapping: null, documentType: 'PRESCRIPTION',
  prescription: { sourceKind: 'ENCOUNTER', tarbilDiseaseId: 'd1', tarbilDiseaseName: 'Pnömoni', treatmentStartDate: '2026-10-09', lines: LINES },
} satisfies Submission;

async function setup(sub: Partial<Submission> = {}, step: FlowStep | null = 'armed', stepData?: Record<string, unknown>,
  results: Record<string, unknown> = { addDisease: { added: true, listed: true } }, failing: string[] = []) {
  let t = 100_000;
  const now = () => t;
  const flow = createFlowStore(memoryStore(), now);
  if (step) {
    await flow.arm('s1', 'PRESCRIPTION');
    if (step !== 'armed' || stepData) await flow.update('s1', { step, ...(stepData ? { stepData } : {}) });
  }
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return results[op] as never; } };
  const sent: BackgroundRequest[] = [];
  const submission = { ...base, ...sub };
  const send = (async (req: BackgroundRequest) => {
    sent.push(req);
    return failing.includes(req.type) ? { ok: false, code: 'CONFLICT', error: 'x' } : { ok: true, data: submission };
  }) as Send;
  const shown: CardView[] = [];
  let action: (id: string) => void = () => undefined;
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: (h: (id: string) => void) => { action = h; } };
  let observer: () => void = () => undefined;
  const flowUnderTest = createPrescriptionFlow({
    bridge, flow, send, doc: document, card, now,
    setTimer: () => undefined,
    observe: (cb) => { observer = cb; return () => undefined; },
    reload: () => undefined,
  });
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  const actions = () => shown.at(-1)?.actions.map((a) => a.label) ?? [];
  return { prescription: flowUnderTest, flow, calls, sent, text, actions, mutate: () => observer(), act: (id: string) => action(id) };
}

describe('prescriptionFlow', () => {
  it('fills date, class, species and disease, then opens the PetVet search', async () => {
    page();
    const { prescription, calls, flow } = await setup();

    await prescription.start();

    expect(calls.map((c) => c.op)).toEqual(['ready', 'setTreatmentDate', 'ensureTreatmentClass', 'selectPrescriptionAnimalType', 'addDisease', 'clickAllowed']);
    expect(calls[1].args).toEqual({ iso: '2026-10-09' });
    expect(calls[3].args).toEqual({ value: ANIMAL_TYPE.CAT });
    expect(calls[4].args).toEqual({ value: 'd1' });
    expect(calls[5].args).toEqual({ page: 'prescriptionReceipt', button: 'petVet' });
    expect((await flow.get())?.step).toBe('searching');
  });

  it('stops when the disease did not reach the diagnosed list', async () => {
    page();
    const { prescription, calls, flow, text } = await setup({}, 'armed', undefined, { addDisease: { added: true, listed: false } });

    await prescription.start();

    expect(calls.map((c) => c.op)).not.toContain('clickAllowed');
    expect((await flow.get())?.step).toBe('error');
    expect(text()).toContain('DISEASE_NOT_ADDED');
  });

  it('does nothing on the page when the prescription has no TARBIL diagnosis', async () => {
    page();
    const { prescription, calls, text } = await setup({ prescription: { ...base.prescription, tarbilDiseaseId: null, tarbilDiseaseName: null } });

    await prescription.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('teşhis');
  });

  it('leaves a vaccination alone on the prescription page', async () => {
    page();
    const { prescription, calls, text } = await setup({ documentType: 'VACCINATION', prescription: null });

    await prescription.start();

    expect(calls).toEqual([]);
    expect(text()).toContain('Aşı Uygulama Belgesi Ekle');
  });

  it('opens Stok Ekle and the stock window for the first line once the animal is on the form', async () => {
    page();
    const { prescription, flow, calls, mutate } = await setup({}, 'transferred');
    await prescription.start();

    addAnimalRow(CHIP);
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('choosingProduct'));
    expect((await flow.get())?.stepData).toMatchObject({ lineIndex: 0 });
    expect(calls.filter((c) => c.op === 'clickAllowed').map((c) => c.args)).toEqual([
      { page: 'prescriptionReceipt', button: 'addStock' },
      { page: 'prescriptionReceipt', button: 'stockSearch' },
    ]);
  });

  it('writes Adet and Miktar when the selected row has the planned serial', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, calls, mutate, text } = await setup({}, 'choosingProduct', { lineIndex: 0 });
    await prescription.start();

    stockGrid({ edit: 'KV04Z81' });
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('productReady'));
    expect(calls.find((c) => c.op === 'setStockQuantities')?.args).toEqual({ quantity: 1, amount: 1 });
    expect(text()).toContain('Kaydet');
  });

  it('refuses a row with a different serial', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, calls, mutate, text } = await setup({}, 'choosingProduct', { lineIndex: 0 });
    await prescription.start();

    stockGrid({ edit: 'XX1' });
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('awaitingConfirm'));
    expect(calls.map((c) => c.op)).not.toContain('setStockQuantities');
    expect(text()).toContain('Yanlış ürün');
  });

  it('moves to the next line once the vet saved the row', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, calls, mutate } = await setup({}, 'productReady', { lineIndex: 0 });
    await prescription.start();

    stockGrid({ saved: [['Advantage % 10', 'KV04Z81', '1']] });
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.stepData).toMatchObject({ lineIndex: 1 }));
    expect((await flow.get())?.step).toBe('choosingProduct');
    expect(calls.filter((c) => c.op === 'clickAllowed')).toHaveLength(2);
  });

  it('asks for Reçeteyi Onayla once every line is saved', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, mutate, text } = await setup({}, 'productReady', { lineIndex: 1 });
    await prescription.start();

    stockGrid({ saved: [['Advantage % 10', 'KV04Z81', '1'], ['SYNULOX', 'S1', '2']] });
    mutate();

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('awaitingConfirm'));
    expect(text()).toContain('Reçeteyi Onayla');
  });

  it('reports the saved lines when TARBIL confirms the prescription', async () => {
    page();
    addAnimalRow(CHIP);
    stockGrid({ saved: [['Advantage % 10', 'KV04Z81', '1'], ['SYNULOX', 'S1', '2']] });
    const { prescription, flow, sent, mutate } = await setup({}, 'awaitingConfirm');
    await prescription.start();

    document.getElementById(`${P}btnInsert_input`)!.click();
    await vi.waitFor(async () => expect((await flow.get())?.insertClickedAt).toBeDefined());
    showSuccess();
    mutate();

    await vi.waitFor(() => expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(true));
    expect(sent.find((r) => r.type === 'MARK_SUBMITTED')).toEqual({
      type: 'MARK_SUBMITTED', id: 's1', method: 'AUTO', tarbilReference: null,
      lines: [
        { productName: 'Advantage % 10', serialNumber: 'KV04Z81', quantity: 1, amount: 1, source: 'WHOLE' },
        { productName: 'SYNULOX', serialNumber: 'S1', quantity: 2, amount: 2, source: 'WHOLE' },
      ],
    });
    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('done'));
  });

  it('ignores a success panel that was on the page before Reçeteyi Onayla', async () => {
    page();
    addAnimalRow(CHIP);
    showSuccess();
    const { prescription, sent, mutate } = await setup({}, 'awaitingConfirm');
    await prescription.start();

    document.getElementById(`${P}btnInsert_input`)!.click();
    mutate();
    await new Promise((r) => setTimeout(r, 20));

    expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(false);
  });

  it('refuses a form that holds another animal', async () => {
    page();
    addAnimalRow('900000000000999');
    const { prescription, calls, flow, text } = await setup();

    await prescription.start();

    expect(calls).toEqual([]);
    expect((await flow.get())?.message).toBe('DIRTY_FORM');
    expect(text()).toContain('yarım kalmış');
  });

  it('resumes on a form with its own animal and a saved line', async () => {
    page();
    addAnimalRow(CHIP);
    stockGrid({ saved: [['Advantage % 10', 'KV04Z81', '1']] });
    const { prescription, calls, flow } = await setup();

    await prescription.start();

    await vi.waitFor(async () => expect((await flow.get())?.stepData).toMatchObject({ lineIndex: 1 }));
    expect(calls.map((c) => c.op)).not.toContain('setTreatmentDate');
    expect(calls.map((c) => c.op)).toContain('addDisease');
  });

  it('re-arms as a prescription when the vet presses Formu doldur', async () => {
    page();
    const { prescription, flow, act } = await setup({}, null);
    await prescription.start();

    act('fill');

    await vi.waitFor(async () => expect((await flow.get())?.documentType).toBe('PRESCRIPTION'));
  });

  it('offers the stock window again when the page reloads while choosing a product', async () => {
    page();
    addAnimalRow(CHIP);
    stockGrid();
    const { prescription, actions } = await setup({}, 'choosingProduct', { lineIndex: 0 });

    await prescription.start();

    await vi.waitFor(() => expect(actions()).toContain('Stok penceresini aç'));
  });

  it('keeps the hand-over message after a reload', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, text } = await setup({}, 'choosingProduct', { lineIndex: 0 });
    await flow.update('s1', { step: 'awaitingConfirm', message: 'Seri KV04Z81 TARBİL stoğunuzda bulunamadı.' });

    await prescription.start();

    await vi.waitFor(() => expect(text()).toContain('bulunamadı'));
  });

  it('continues with the line after a wrong selection was cancelled', async () => {
    page();
    addAnimalRow(CHIP);
    const { prescription, flow, calls, mutate, act, actions } = await setup({}, 'choosingProduct', { lineIndex: 0 });
    await prescription.start();
    stockGrid({ edit: 'XX1' });
    mutate();
    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('awaitingConfirm'));
    expect(actions()).toContain('Devam et');

    stockGrid();
    act('stockWindow');

    await vi.waitFor(async () => expect((await flow.get())?.step).toBe('choosingProduct'));
    expect(calls.filter((c) => c.op === 'clickAllowed').map((c) => (c.args as { button: string }).button)).toEqual(['addStock', 'stockSearch']);
  });

  it('does not offer to refill the form when Vetly rejects the confirmation', async () => {
    page();
    addAnimalRow(CHIP);
    stockGrid({ saved: [['Advantage % 10', 'KV04Z81', '1'], ['SYNULOX', 'S1', '2']] });
    const { prescription, flow, mutate, actions, sent } = await setup({}, 'awaitingConfirm', undefined, undefined, ['MARK_SUBMITTED']);
    await prescription.start();

    document.getElementById(`${P}btnInsert_input`)!.click();
    await vi.waitFor(async () => expect((await flow.get())?.insertClickedAt).toBeDefined());
    showSuccess();
    mutate();

    await vi.waitFor(() => expect(sent.some((r) => r.type === 'MARK_SUBMITTED')).toBe(true));
    await vi.waitFor(() => expect(actions()).toContain('Kaydedildi olarak işaretle'));
    expect(actions()).not.toContain('Yeniden dene');
  });
});
