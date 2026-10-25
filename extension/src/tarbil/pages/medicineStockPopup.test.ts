// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import type { BackgroundRequest } from '../../shared/messages';
import { createFlowStore } from '../../shared/flowStore';
import type { DocumentType, Submission } from '../../shared/types';
import type { CardView } from '../core/card';
import type { Send } from '../steps/findAnimal';
import { runMedicineStockPopupFlow } from './medicineStockPopup';

const G = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_radGridStock_ctl00';

function popup(rows: [string, string, string][]) {
  document.body.innerHTML = `<table id="${G}"><thead><tr><th></th><th>Ürün Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
    <th>Son Kullanma Tarihi</th><th>Ürün Miktarı</th></tr></thead><tbody>${rows
      .map(([name, serial, skt], i) => `<tr id="${G}__${i}"><td><a id="${G}_ctl0${4 + 2 * i}_SelectlinkButton">Seç</a></td>
        <td>${name}</td><td>Kutu</td><td>${serial}</td><td>${skt}</td><td>3</td></tr>`)
      .join('')}</tbody></table>`;
}

const submission = {
  id: 's1', status: 'PENDING', documentType: 'PRESCRIPTION', lotNumber: null,
  prescription: {
    sourceKind: 'ENCOUNTER', tarbilDiseaseId: 'd1', tarbilDiseaseName: 'Pnömoni', treatmentStartDate: '2026-10-09',
    lines: [
      { itemName: 'Advantage', tarbilProductName: 'Advantage % 10', lotNumber: 'KV04Z81', quantity: 1 },
      { itemName: 'Synulox', tarbilProductName: 'SYNULOX', lotNumber: 'S1', quantity: 2 },
      { itemName: 'Ketofen', tarbilProductName: 'KETOFEN', lotNumber: 'ki7', quantity: 1 },
      { itemName: 'Clavon', tarbilProductName: 'Clavon', lotNumber: '63016', quantity: 0, amount: 1.5, unit: 'ML', source: 'OPEN' },
    ],
  },
} as Submission;

async function setup(documentType: DocumentType = 'PRESCRIPTION', lineIndex = 1) {
  const flow = createFlowStore(memoryStore(), () => 1000);
  await flow.arm('s1', documentType);
  await flow.update('s1', { step: 'choosingProduct', stepData: { lineIndex } });
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const send = (async (req: BackgroundRequest) => (req.type === 'GET_ACTIVE' ? { ok: true, data: submission } : { ok: true, data: null })) as Send;
  const shown: CardView[] = [];
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: () => undefined };
  const deps = { bridge, flow, send, doc: document, card, now: () => 6000, today: () => '2026-10-10' };
  return { deps, flow, calls };
}

describe('runMedicineStockPopupFlow', () => {
  it('searches the serial of the current line and selects its single row', async () => {
    popup([['SYNULOX', 'S1', '31.01.2027'], ['Advantage % 10', 'KV04Z81', '31.01.2027']]);
    const { deps, calls } = await setup();

    await runMedicineStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchMedicineSerial', 'selectStockRow']);
    expect(calls[1].args).toEqual({ serial: 'S1', open: false });
    expect(calls[2].args).toEqual({ linkId: `${G}_ctl04_SelectlinkButton` });
  });

  it('hands the choice to the vet when the serial is not in stock', async () => {
    popup([['Advantage % 10', 'KV04Z81', '31.01.2027']]);
    const { deps, calls, flow } = await setup();

    await runMedicineStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchMedicineSerial']);
    expect(await flow.get()).toMatchObject({ step: 'awaitingConfirm', message: expect.stringContaining('bulunamadı') });
  });

  it('does nothing for a vaccination flow', async () => {
    popup([['SYNULOX', 'S1', '31.01.2027']]);
    const { deps, calls } = await setup('VACCINATION');

    await runMedicineStockPopupFlow(deps);

    expect(calls).toEqual([]);
  });

  it('searches TARBIL with the normalized serial', async () => {
    popup([['KETOFEN', 'KI7', '31.01.2027']]);
    const { deps, calls } = await setup('PRESCRIPTION', 2);

    await runMedicineStockPopupFlow(deps);

    expect(calls[1].args).toEqual({ serial: 'KI7', open: false });
    expect(calls.map((c) => c.op)).toContain('selectStockRow');
  });

  it('searches the open-stock list when the line source is OPEN (spec 2026-10-24)', async () => {
    const O = 'ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UcPharmacyStockSearchForReceipt_radGridOpenStock_ctl00';
    document.body.innerHTML = `<table id="${O}"><thead><tr><th></th><th>Ürün Adı</th><th>Takdim Şekli</th><th>Seri Numarası</th>
      <th>Son Kullanma Tarihi</th><th>Açık Stok Miktarı</th></tr></thead><tbody><tr id="${O}__0"><td><a id="${O}_ctl04_SelectlinkButton2">Seç</a></td>
      <td>Clavon</td><td>50 ml</td><td>63016</td><td>27.04.2027</td><td>49,00</td></tr></tbody></table>`;
    const { deps, calls } = await setup('PRESCRIPTION', 3);

    await runMedicineStockPopupFlow(deps);

    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchMedicineSerial', 'selectStockRow']);
    expect(calls[1].args).toEqual({ serial: '63016', open: true });
    expect(calls[2].args).toEqual({ linkId: `${O}_ctl04_SelectlinkButton2` });
  });
});
