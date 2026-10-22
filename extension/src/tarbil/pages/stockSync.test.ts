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

type Reply = unknown;
const SAME = { ok: true, data: { newCount: 0, quantityDiffersCount: 0, matchedCount: 1 } };
const BROKEN = { ok: false, error: 'x', code: 'NETWORK' };

function setup(uploadResult: Reply = { ok: true, data: { snapshotId: 's1' } }, compareResult: Reply = SAME) {
  const calls: { op: string; args?: unknown }[] = [];
  const bridge = { call: async (op: string, args?: unknown) => { calls.push({ op, args }); return undefined as never; } };
  const sent: BackgroundRequest[] = [];
  const send = (async (req: BackgroundRequest) => {
    sent.push(req);
    return req.type === 'COMPARE_STOCK_SNAPSHOT' ? compareResult : uploadResult;
  }) as Send;
  const shown: CardView[] = [];
  let handler: (id: string) => void = () => undefined;
  const card = { show: (v: CardView) => shown.push(v), hide: () => undefined, onAction: (h: (id: string) => void) => { handler = h; } };
  const text = () => shown.at(-1)?.lines.map((l) => l.text).join(' ') ?? '';
  const uploads = () => sent.filter((r) => r.type === 'UPLOAD_STOCK_SNAPSHOT');
  const store = memoryStore();
  const deps = { bridge, send, doc: document, card, store, now: () => Date.UTC(2026, 9, 7, 11, 32) };
  return { calls, sent, uploads, shown, text, card, bridge, send, store, deps, click: (id: string) => handler(id) };
}

describe('stock sync page', () => {
  it('compares the TARBIL stock with Vetly on load without saving anything', async () => {
    medicineTable();
    const s = setup();
    await createStockSync({ ...s.deps, kind: 'medicineStock' }).start();

    expect(s.calls.map((c) => c.op)).toEqual(['ready', 'loadStockTable']);
    expect(s.sent).toEqual([{
      type: 'COMPARE_STOCK_SNAPSHOT', system: 'VETILAC_MEDICINE',
      lines: [{ productName: 'İlaç Y', presentation: 'Kutu', lotNumber: 'A1', expiryDate: '2027-02-01', quantity: 2, openedQuantity: null }],
    }]);
    expect(s.uploads()).toEqual([]);
  });

  it('shows only a small up-to-date line when nothing differs', async () => {
    medicineTable();
    const s = setup();
    await createStockSync({ ...s.deps, kind: 'medicineStock' }).start();

    expect(s.text()).toContain('güncel');
    expect(s.shown.at(-1)?.actions).toEqual([]);
  });

  it('offers to send when TARBIL has products Vetly lacks or other quantities', async () => {
    medicineTable();
    const s = setup(undefined, { ok: true, data: { newCount: 2, quantityDiffersCount: 1, matchedCount: 0 } });
    await createStockSync({ ...s.deps, kind: 'medicineStock' }).start();

    expect(s.text()).toContain('olmayan 2 ürün');
    expect(s.text()).toContain('miktarı farklı 1 ürün');
    expect(s.shown.at(-1)?.lines.at(-1)?.tone).toBe('warn');
    expect(s.shown.at(-1)?.actions.map((a) => a.id)).toEqual(['sendStock']);
  });

  it('mentions only differing quantities when nothing is new', async () => {
    medicineTable();
    const s = setup(undefined, { ok: true, data: { newCount: 0, quantityDiffersCount: 3, matchedCount: 4 } });
    await createStockSync({ ...s.deps, kind: 'medicineStock' }).start();

    expect(s.text()).toContain('miktarı farklı 3 ürün');
    expect(s.text()).not.toContain('olmayan');
  });

  it('falls back to the explanatory card when the comparison fails', async () => {
    medicineTable();
    const s = setup(undefined, { ok: false, error: 'Eklenti bağlı değil', code: 'UNAUTHORIZED' });
    await createStockSync({ ...s.deps, kind: 'medicineStock' }).start();

    expect(s.text()).toContain('aktarabilirsiniz');
    expect(s.shown.at(-1)?.actions.map((a) => a.id)).toEqual(['sendStock']);
  });

  it('falls back without asking Vetly when the table is not recognized on load', async () => {
    document.body.innerHTML = '<div></div>';
    const s = setup();
    await createStockSync({ ...s.deps, kind: 'vaccineStock' }).start();

    expect(s.sent).toEqual([]);
    expect(s.text()).toContain('aktarabilirsiniz');
  });

  it('falls back when loading the table throws', async () => {
    medicineTable();
    const s = setup();
    const bridge = { call: async () => { throw { code: 'TIMEOUT' }; } };
    await createStockSync({ ...s.deps, bridge, kind: 'medicineStock' }).start();

    expect(s.sent).toEqual([]);
    expect(s.text()).toContain('aktarabilirsiniz');
  });

  it('loads the table, reads it and uploads it to Vetly', async () => {
    medicineTable();
    const s = setup();
    await createStockSync({ ...s.deps, kind: 'medicineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.uploads()).toHaveLength(1));
    expect(s.calls.map((c) => c.op)).toEqual(['ready', 'loadStockTable', 'ready', 'loadStockTable']);
    expect(s.calls[3].args).toEqual({ page: 'medicineStock' });
    expect(s.uploads()[0]).toEqual({
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
    expect(s.uploads()).toEqual([]);
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

    const next = setup(undefined, BROKEN);
    await createStockSync({ ...next.deps, store: first.store, kind: 'medicineStock' }).start();

    expect(next.text()).toContain('Son gönderim');
    expect(next.text()).toContain('1 satır');
    expect(next.text()).not.toContain('aktarabilirsiniz');
    expect(next.shown.at(-1)?.actions).toEqual([{ id: 'sendStock', label: 'Yeniden gönder' }]);
  });

  it('keeps vaccine and medicine uploads apart', async () => {
    medicineTable();
    const first = setup();
    await createStockSync({ ...first.deps, kind: 'medicineStock' }).start();
    first.click('sendStock');
    await vi.waitFor(() => expect(first.uploads()).toHaveLength(1));

    const vaccine = setup(undefined, BROKEN);
    await createStockSync({ ...vaccine.deps, store: first.store, kind: 'vaccineStock' }).start();

    expect(vaccine.text()).toContain('aktarabilirsiniz');
  });

  it('does not remember a failed upload', async () => {
    medicineTable();
    const failed = setup({ ok: false, error: 'Eklenti bağlı değil', code: 'UNAUTHORIZED' }, BROKEN);
    await createStockSync({ ...failed.deps, kind: 'medicineStock' }).start();
    failed.click('sendStock');
    await vi.waitFor(() => expect(failed.text()).toContain('Eklenti bağlı değil'));

    const next = setup(undefined, BROKEN);
    await createStockSync({ ...next.deps, store: failed.store, kind: 'medicineStock' }).start();

    expect(next.text()).toContain('aktarabilirsiniz');
  });
});


const VB = 'ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_';
const VACCINE_HEADERS = ['', '', '', 'Aşı Adı', 'Takdim Şekli', 'Seri Numarası', 'Son Kullanma Tarihi', 'Ürün Miktarı', 'Stok Tipi'];

function vaccineGrid(grid: string, rows: [string, string, number][]) {
  const id = `${VB}${grid}_ctl00`;
  const body = rows
    .map(([name, lot, qty], i) => `<tr id="${id}__${i}"><td></td><td></td><td></td><td>${name}</td><td>Flakon</td><td>${lot}</td><td>31.08.2027</td><td>${qty}</td><td>Kendi Ürünü</td></tr>`)
    .join('');
  return `<table id="${id}"><thead><tr>${VACCINE_HEADERS.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${body}</tbody></table>`;
}

const line = (productName: string, lotNumber: string, quantity: number) =>
  ({ productName, presentation: 'Flakon', lotNumber, expiryDate: '2027-08-31', quantity, openedQuantity: null });

describe('vaccine stock page with Parça Stok (2026-10-21)', () => {
  it('reads Ara and Parça Stok Ara and sends both in one upload', async () => {
    document.body.innerHTML = vaccineGrid('radGridStock', [['Biocan DHPPI', '145932', 1]]) + vaccineGrid('radGridStockInbox', [['Biocan R', '665932', 18]]);
    const s = setup(undefined, BROKEN);
    await createStockSync({ ...s.deps, kind: 'vaccineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.uploads()).toHaveLength(1));
    expect(s.calls.map((c) => c.op)).toEqual(['ready', 'loadStockTable', 'loadVaccinePartStock', 'ready', 'loadStockTable', 'loadVaccinePartStock']);
    expect(s.uploads()[0]).toEqual({
      type: 'UPLOAD_STOCK_SNAPSHOT', system: 'HBSAPP_VACCINE',
      lines: [line('Biocan DHPPI', '145932', 1), { ...line('Biocan R', '665932', 18), partStock: true }],
    });
    await vi.waitFor(() => expect(s.text()).toContain('2 satır'));
    expect(s.text()).toContain('Bütün stok 1 satır, parça stok 1 satır');
  });

  it('shows both counts in the comparison card on load', async () => {
    document.body.innerHTML = vaccineGrid('radGridStock', [['Biocan DHPPI', '145932', 1]]) + vaccineGrid('radGridStockInbox', []);
    const s = setup();
    await createStockSync({ ...s.deps, kind: 'vaccineStock' }).start();

    expect(s.text()).toContain('güncel');
    expect(s.text()).toContain('Bütün stok 1 satır, parça stok 0 satır');
  });

  it('sends Parça Stok alone when the whole-stock table is empty', async () => {
    document.body.innerHTML = vaccineGrid('radGridStock', []) + vaccineGrid('radGridStockInbox', [['Felocell CVR', '823287', 23]]);
    const s = setup(undefined, BROKEN);
    await createStockSync({ ...s.deps, kind: 'vaccineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.uploads()).toHaveLength(1));
    expect(s.uploads()[0]).toMatchObject({ lines: [{ ...line('Felocell CVR', '823287', 23), partStock: true }] });
  });

  it('still sends the whole stock but warns when Parça Stok cannot be read', async () => {
    document.body.innerHTML = vaccineGrid('radGridStock', [['Biocan DHPPI', '145932', 1]]);
    const s = setup(undefined, BROKEN);
    await createStockSync({ ...s.deps, kind: 'vaccineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.uploads()).toHaveLength(1));
    expect(s.uploads()[0]).toMatchObject({ lines: [line('Biocan DHPPI', '145932', 1)] });
    await vi.waitFor(() => expect(s.text()).toContain('Parça stok okunamadı'));
    expect(s.shown.at(-1)?.lines.at(-1)?.tone).toBe('warn');
  });

  it('still sends the whole stock when pressing Parça Stok Ara fails', async () => {
    document.body.innerHTML = vaccineGrid('radGridStock', [['Biocan DHPPI', '145932', 1]]) + vaccineGrid('radGridStockInbox', [['Biocan R', '665932', 18]]);
    const s = setup(undefined, BROKEN);
    const bridge = { call: async (op: string) => { if (op === 'loadVaccinePartStock') throw { code: 'NOT_FOUND' }; return undefined as never; } };
    await createStockSync({ ...s.deps, bridge, kind: 'vaccineStock' }).start();

    s.click('sendStock');

    await vi.waitFor(() => expect(s.uploads()).toHaveLength(1));
    expect(s.uploads()[0]).toMatchObject({ lines: [line('Biocan DHPPI', '145932', 1)] });
    await vi.waitFor(() => expect(s.text()).toContain('Parça stok okunamadı'));
  });

  it('mentions Parça Stok in the explanatory card', async () => {
    document.body.innerHTML = '<div></div>';
    const s = setup();
    await createStockSync({ ...s.deps, kind: 'vaccineStock' }).start();

    expect(s.text()).toContain('bütün stok ve parça stok');
  });
});

describe('stock sync after the extension was reloaded (2026-10-21)', () => {
  it('asks to refresh the page instead of showing UNKNOWN when sending throws without a code', async () => {
    medicineTable();
    const s = setup(undefined, BROKEN);
    const dead = (async () => { throw new Error('Extension context invalidated.'); }) as Send;
    createStockSync({ ...s.deps, send: dead, kind: 'medicineStock' });

    s.click('sendStock');

    await vi.waitFor(() => expect(s.text()).toContain('Sayfayı yenileyip (F5)'));
    expect(s.text()).not.toContain('UNKNOWN');
  });

  it('keeps showing the error code when there is one', async () => {
    medicineTable();
    const s = setup(undefined, BROKEN);
    const bridge = { call: async (op: string) => { if (op === 'loadStockTable') throw { code: 'TIMEOUT' }; return undefined as never; } };
    createStockSync({ ...s.deps, bridge, kind: 'medicineStock' });

    s.click('sendStock');

    await vi.waitFor(() => expect(s.text()).toContain('Stok okunamadı (TIMEOUT)'));
  });
});
