// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import type { BackgroundRequest } from '../../shared/messages';
import { createFlowStore, type FlowStep } from '../../shared/flowStore';
import type { Submission } from '../../shared/types';
import type { CardView } from '../core/card';
import type { Send } from '../steps/findAnimal';
import { runStockPopupFlow } from './stockPopup';

const SP = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcVaccineStockSearch_radGridStock_ctl00';

function popup(rows: [string, string, string][]) {
  document.body.innerHTML = `<table id="${SP}"><thead><tr><th></th><th>Aşı Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
    <th>Son Kullanma Tarihi</th><th>Ürün Miktarı</th></tr></thead><tbody>${rows
      .map(([name, serial, skt], i) => `<tr id="${SP}__${i}"><td><a id="${SP}_ctl0${4 + 2 * i}_SelectlinkButton">Seç</a></td>
        <td>${name}</td><td>Flakon</td><td>${serial}</td><td>${skt}</td><td>3</td></tr>`)
      .join('')}</tbody></table>`;
}

const submission = { id: 's1', status: 'PENDING', lotNumber: '665932', tarbilProductName: 'Biocan R' } as Submission;

async function setup(step: FlowStep = 'choosingProduct', sub: Submission = submission, age = 5000) {
  const flow = createFlowStore(memoryStore(), () => 1000);
  await flow.arm('s1');
  await flow.update('s1', { step });
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const send = (async (req: BackgroundRequest) => (req.type === 'GET_ACTIVE' ? { ok: true, data: sub } : { ok: true, data: null })) as Send;
  const shown: CardView[] = [];
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined };
  const deps = { bridge, flow, send, doc: document, card, now: () => 1000 + age, today: () => '2026-10-04' };
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  return { deps, flow, calls, text };
}

describe('runStockPopupFlow', () => {
  it('searches by the Vetly serial and selects the single matching row', async () => {
    popup([['Biocan R', '665932', '31.01.2027'], ['Biocan DHPPI', '145932', '31.01.2027']]);
    const { deps, calls } = await setup();

    await runStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchSerial', 'selectStockRow']);
    expect(calls[1].args).toEqual({ serial: '665932' });
    expect(calls[2].args).toEqual({ linkId: `${SP}_ctl04_SelectlinkButton` });
  });

  it('does nothing when the vet opened the window without Vetly', async () => {
    popup([['Biocan R', '665932', '31.01.2027']]);
    const { deps, calls } = await setup('awaitingConfirm');

    await runStockPopupFlow(deps);

    expect(calls).toEqual([]);
  });

  it('does nothing when the product step is stale', async () => {
    popup([['Biocan R', '665932', '31.01.2027']]);
    const { deps, calls } = await setup('choosingProduct', submission, 200_000);

    await runStockPopupFlow(deps);

    expect(calls).toEqual([]);
  });

  it('hands over to the vet when the serial is not in the clinic stock', async () => {
    popup([['Biocan R', '111111', '31.01.2027']]);
    const { deps, flow, calls, text } = await setup();

    await runStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchSerial']);
    expect(await flow.get()).toMatchObject({ step: 'awaitingConfirm' });
    expect((await flow.get())?.message).toContain('665932');
    expect(text()).toContain('bulunamadı');
  });

  it('does not pick an expired serial', async () => {
    popup([['Biocan R', '665932', '01.10.2026']]);
    const { deps, flow, calls } = await setup();

    await runStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchSerial']);
    expect((await flow.get())?.message).toContain('son kullanma');
  });

  it('hands over when the product name in TARBIL differs', async () => {
    popup([['Nobivac', '665932', '31.01.2027']]);
    const { deps, flow } = await setup();

    await runStockPopupFlow(deps);

    expect((await flow.get())?.message).toContain('Nobivac');
  });

  it('hands over when the expiry date cannot be read', async () => {
    popup([['Biocan R', '665932', '']]);
    const { deps, flow, calls } = await setup();

    await runStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchSerial']);
    expect((await flow.get())?.message).toContain('okunamadı');
  });
});
