// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/inventoryApi', () => ({ inventoryApi: { list: vi.fn() } }));

import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { StockGivePicker } from './StockGivePicker';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const item = (over: Partial<InventoryItem>): InventoryItem => ({
  id: 'i', name: 'Kalem', category: 'İlaç', skuBarcode: null, quantityOnHand: 1, reorderThreshold: 0, belowReorderThreshold: false,
  expiryDate: null, lotNumber: 'L1', unitCost: null, tarbilProductName: 'KALEM', unit: 'ADET', packageSize: null, openedQuantity: 0, pieceSize: null, pieceLabel: null, openedExpiryDate: null,
  unitSource: 'AUTO', ...over,
});

let container: HTMLDivElement;
afterEach(() => container?.remove());

describe('StockGivePicker (spec 2026-10-24)', () => {
  it('lists an ml item that only has an open bottle left', async () => {
    vi.mocked(inventoryApi.list).mockResolvedValue([
      item({ id: 'c', name: 'Clavon', lotNumber: '63016', unit: 'ML', packageSize: 50, quantityOnHand: 0, openedQuantity: 49 }),
      item({ id: 'e', name: 'Boş', quantityOnHand: 0 }),
    ]);
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(<StockGivePicker value={null} onChange={() => undefined} />);
    });
    await act(async () => {});

    expect(container.textContent).toContain('Clavon · Seri 63016 · 0 şişe + 49 ml');
    expect(container.textContent).not.toContain('Boş');
  });
});
