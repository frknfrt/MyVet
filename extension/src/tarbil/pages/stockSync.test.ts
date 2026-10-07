// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
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
  const store = memoryStore();
  const deps = { bridge, send, doc: document, card, store, now: () => Date.UTC(2026, 9, 7, 11, 32) };
  return { calls, sent, shown, text, card, bridge, send, store, deps, click: (id: string) => handler(id) };
}

describe('stock sync page', () => {
  it('offers a button and does nothing on TARBIL until the vet clicks it', async () => {
    medicineTable();
    const s = setup();
    await createStockSync({ ...s.deps, kind: 'medicineStock' }).start();

    expect(s.calls).toEqual([]);
    expect(s.shown.at(-1)?.actions.map((a) => a.id)).toEqual(['sendStock']);
  });

  it('loads the table, reads it and uploads it to Vetly', async () => {
    medicineTable();
    const s = setup();
    await createStockSync({ ...s.deps, kind: 'medicineStock' }).start();

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
    await createStockSync({ ...s.deps, kind: 'vaccineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.text()).toContain('tanınmadı'));
    expect(s.sent).toEqual([]);
  });

  it('reports a Vetly error', async () => {
    medicineTable();
    const s = setup({ ok: false, error: 'Eklenti bağlı değil', code: 'UNAUTHORIZED' });
    await createStockSync({ ...s.deps, kind: 'medicineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.text()).toContain('Eklenti bağlı değil'));
  });

  it('remembers a successful upload and shows a compact card on the next visit', async () => {
    medicineTable();
    const first = setup();
    await createStockSync({ ...first.deps, kind: 'medicineStock' }).start();
    first.click('sendStock');
    await vi.waitFor(() => expect(first.text()).toContain('1 satır'));

    const next = setup();
    await createStockSync({ ...next.deps, store: first.store, kind: 'medicineStock' }).start();

    expect(next.text()).toContain('Son gönderim');
    expect(next.text()).toContain('1 satır');
    expect(next.text()).not.toContain('aktarabilirsiniz');
    expect(next.shown.at(-1)?.actions).toEqual([{ id: 'sendStock', label: 'Yeniden gönder' }]);
    expect(next.calls).toEqual([]);
  });

  it('keeps vaccine and medicine uploads apart', async () => {
    medicineTable();
    const first = setup();
    await createStockSync({ ...first.deps, kind: 'medicineStock' }).start();
    first.click('sendStock');
    await vi.waitFor(() => expect(first.sent).toHaveLength(1));

    const vaccine = setup();
    await createStockSync({ ...vaccine.deps, store: first.store, kind: 'vaccineStock' }).start();

    expect(vaccine.text()).toContain('aktarabilirsiniz');
  });

  it('does not remember a failed upload', async () => {
    medicineTable();
    const failed = setup({ ok: false, error: 'Eklenti bağlı değil', code: 'UNAUTHORIZED' });
    await createStockSync({ ...failed.deps, kind: 'medicineStock' }).start();
    failed.click('sendStock');
    await vi.waitFor(() => expect(failed.text()).toContain('Eklenti bağlı değil'));

    const next = setup();
    await createStockSync({ ...next.deps, store: failed.store, kind: 'medicineStock' }).start();

    expect(next.text()).toContain('aktarabilirsiniz');
  });
});

