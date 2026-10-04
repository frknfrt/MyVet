// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import type { BackgroundRequest } from '../../shared/messages';
import { createFlowStore } from '../../shared/flowStore';
import type { Submission } from '../../shared/types';
import type { CardView } from '../core/card';
import { runSearchFlow, type Send } from './findAnimal';

const SP = 'ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_radGridAnimal_ctl00';
const CHIP = '900000000000001';

function grid(rows: [string, string, string?][]) {
  document.body.innerHTML = `<table id="${SP}"><thead><tr><th></th><th>Adı</th><th>Çip No</th><th>Pasaport No</th>
    <th>Tür</th><th>Irk</th><th>Cinsiyet</th><th>Renk</th><th>Doğum Tarihi</th><th>Durumu</th><th>Hayvan Sahibi</th>
    <th>Anne Çip No</th></tr></thead><tbody>${rows
      .map(([chip, status, mother = ''], i) => `<tr id="${SP}__${i}"><td><input type="checkbox" id="cb${i}"></td><td>Ad</td>
        <td>${chip}</td><td></td><td>Kedi</td><td></td><td></td><td></td><td></td><td>${status}</td><td></td><td>${mother}</td></tr>`)
      .join('')}</tbody></table>`;
}

const submission = { id: 's1', status: 'PENDING', microchipNumber: CHIP } as Submission;

async function setup(step: 'searching' | 'armed' = 'searching', updatedAt = 1000) {
  const flow = createFlowStore(memoryStore(), () => updatedAt);
  await flow.arm('s1');
  if (step !== 'armed') await flow.update('s1', { step });
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const send = (async (req: BackgroundRequest) => (req.type === 'GET_ACTIVE' ? { ok: true, data: submission } : { ok: true, data: null })) as Send;
  const shown: CardView[] = [];
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined };
  const deps = { bridge, flow, send, doc: document, card, now: () => updatedAt + 5000 };
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  return { deps, flow, calls, text };
}

describe('runSearchFlow', () => {
  it('searches by chip, checks the single alive match and transfers it', async () => {
    grid([[CHIP, 'CANLI']]);
    const { deps, flow, calls } = await setup();

    await runSearchFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchChip', 'checkRow', 'transfer']);
    expect(calls[1].args).toEqual({ chip: CHIP });
    expect(calls[2].args).toEqual({ checkboxId: 'cb0' });
    expect((await flow.get())?.step).toBe('transferred');
  });

  it('hands over to the vet when nothing matches', async () => {
    grid([['900000000000009', 'CANLI', CHIP]]);
    const { deps, flow, calls, text } = await setup();

    await runSearchFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchChip']);
    expect(await flow.get()).toMatchObject({ step: 'needsVet' });
    expect(text()).toContain('bulunamadı');
  });

  it('does not auto-select an animal that is not alive', async () => {
    grid([[CHIP, 'ÖLÜ']]);
    const { deps, calls, text } = await setup();

    await runSearchFlow(deps);

    expect(calls.map((c) => c.op)).not.toContain('checkRow');
    expect(text()).toContain('ÖLÜ');
  });

  it('stays out of the way when the vet opened the window by hand', async () => {
    grid([[CHIP, 'CANLI']]);
    const { deps, calls } = await setup('armed');

    await runSearchFlow(deps);

    expect(calls).toEqual([]);
  });
});
