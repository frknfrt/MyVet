// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import type { BackgroundRequest } from '../../shared/messages';
import type { CardView } from '../core/card';
import type { Send } from '../steps/findAnimal';
import { createStockSync } from './stockSync';

const MP = 'ctl00_ContentHolder_radGridStockSearch_ctl00';

function medicineTable() {
  document.body.innerHTML = `
    <table id="${MP}_Header"><thead><tr><th></th><th>Satış Yeri</th><th>Ürün</th><th>Takdim Şekli</th><th>Miktar</th>
      <th>Son Kullanma Tarihi</th><th>Açılmış Kalan Miktar</th><th>Seri Numarası</th></tr></thead></table>
    <table id="${MP}"><tbody><tr id="${MP}__0"><td></td><td>K</td><td>İlaç Y</td><td>Kutu</td><td>2</td><td>01.02.2027</td><td></td><td>A1</td></tr></tbody></table>`;
}

function setup(sendResult: unknown = { ok: true, data: { snapshotId: 's1' } }) {
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const sent: BackgroundRequest[] = [];
  const send = (async (req: BackgroundRequest) => { sent.push(req); return sendResult; }) as Send;
  const shown: CardView[] = [];
  let handler: (id: string) => void = () => undefined;
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: (h: (id: string) => void) => { handler = h; } };
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  return { calls, sent, shown, text, card, bridge, send, click: (id: string) => handler(id) };
}

describe('stock sync page', () => {
  it('offers a button and does nothing on TARBIL until the vet clicks it', () => {
    medicineTable();
    const s = setup();
    createStockSync({ bridge: s.bridge, send: s.send, doc: document, card: s.card, kind: 'medicineStock' }).start();

    expect(s.calls).toEqual([]);
    expect(s.shown.at(-1)?.actions.map((a) => a.id)).toEqual(['sendStock']);
  });

  it('loads the table, reads it and uploads it to Vetly', async () => {
    medicineTable();
    const s = setup();
    createStockSync({ bridge: s.bridge, send: s.send, doc: document, card: s.card, kind: 'medicineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.sent).toHaveLength(1));
    expect(s.calls.map((c) => c.op)).toEqual(['ready', 'loadStockTable']);
    expect(s.calls[1].args).toEqual({ page: 'medicineStock' });
    expect(s.sent[0]).toEqual({
      type: 'UPLOAD_STOCK_SNAPSHOT', system: 'VETILAC_MEDICINE',
      lines: [{ productName: 'İlaç Y', presentation: 'Kutu', lotNumber: 'A1', expiryDate: '2027-02-01', quantity: 2, openedQuantity: null }],
    });
    await vi.waitFor(() => expect(s.text()).toContain('1 satır'));
  });

  it('warns instead of uploading when the table is not recognized', async () => {
    document.body.innerHTML = '<div></div>';
    const s = setup();
    createStockSync({ bridge: s.bridge, send: s.send, doc: document, card: s.card, kind: 'vaccineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.text()).toContain('tanınmadı'));
    expect(s.sent).toEqual([]);
  });

  it('reports a Vetly error', async () => {
    medicineTable();
    const s = setup({ ok: false, error: 'Eklenti bağlı değil', code: 'UNAUTHORIZED' });
    createStockSync({ bridge: s.bridge, send: s.send, doc: document, card: s.card, kind: 'medicineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.text()).toContain('Eklenti bağlı değil'));
  });
});
