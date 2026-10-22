// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/tarbilApi', () => ({
  tarbilApi: { stockSync: vi.fn(), applyStockSync: vi.fn() },
}));

import { tarbilApi, TarbilStockSyncLine } from '../../api/tarbilApi';
import { TarbilStockSyncPanel } from './TarbilStockSyncPanel';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const line = (over: Partial<TarbilStockSyncLine>): TarbilStockSyncLine => ({
  lineId: 'l', productName: 'Ürün', presentation: null, lotNumber: 'L1', expiryDate: null, tarbilQuantity: 1,
  openedQuantity: null, status: 'QUANTITY_DIFFERS', inventoryItemId: 'i', vetlyQuantity: 0, partStock: false,
  pendingTarbilCount: 0, ...over,
});

let container: HTMLDivElement;

async function render(lines: TarbilStockSyncLine[]) {
  vi.mocked(tarbilApi.stockSync).mockResolvedValue({ snapshotId: 's1', system: 'VETILAC_MEDICINE', takenAt: null, lines });
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(<TarbilStockSyncPanel onApplied={() => undefined} />);
  });
  await act(async () => {});
  const boxes = Array.from(container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
  return { selectAll: boxes[0], rows: boxes.slice(1), text: container.textContent ?? '' };
}

afterEach(() => container?.remove());

describe('TarbilStockSyncPanel', () => {
  it('blocks a differing line whose product still waits to be sent to TARBİL', async () => {
    const { rows, text } = await render([
      line({ lineId: 'a', productName: 'Biocan DHPPI', pendingTarbilCount: 1 }),
      line({ lineId: 'b', productName: 'Rabisin' }),
    ]);

    expect(rows[0].disabled).toBe(true);
    expect(rows[1].disabled).toBe(false);
    expect(text).toContain("TARBİL'e gönderilmeyi bekleyen 1 kayıt var");
  });

  it('selecting all leaves the blocked line out', async () => {
    const { selectAll, text: before } = await render([
      line({ lineId: 'a', pendingTarbilCount: 2 }),
      line({ lineId: 'b' }),
    ]);
    expect(before).toContain('0 / 1 satır seçili');

    await act(async () => selectAll.click());

    expect(container.textContent).toContain('1 / 1 satır seçili');
  });

  it('explains why nothing can be selected when every difference is waiting for TARBİL', async () => {
    const { selectAll, text } = await render([line({ lineId: 'a', pendingTarbilCount: 1 })]);

    expect(selectAll.disabled).toBe(true);
    expect(text).toContain("Farklı görünen ürünlerin TARBİL'e gönderilmeyi bekleyen kaydı var.");
    expect(text).not.toContain('Vetly stoğu TARBİL ile aynı.');
  });
});
