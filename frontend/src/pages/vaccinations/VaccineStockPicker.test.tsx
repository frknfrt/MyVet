// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/inventoryApi', () => ({ inventoryApi: { list: vi.fn() } }));

import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { VaccineStockPicker } from './VaccineStockPicker';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
afterEach(() => container?.remove());

describe('VaccineStockPicker (spec 2026-10-24)', () => {
  it('lists a vaccine whose only stock is an opened box', async () => {
    vi.mocked(inventoryApi.list).mockResolvedValue([{
      id: 'r', name: 'Biocan R', category: 'Aşı', skuBarcode: null, quantityOnHand: 0, reorderThreshold: 0, belowReorderThreshold: true,
      expiryDate: null, lotNumber: '665932', unitCost: null, tarbilProductName: 'Biocan R', unit: 'FLAKON', packageSize: 20,
      openedQuantity: 18, unitSource: 'AUTO',
    } as InventoryItem]);
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(<VaccineStockPicker value={null} onChange={() => undefined} />);
    });
    await act(async () => {});

    expect(container.textContent).toContain('Biocan R · Seri 665932');
    expect(container.textContent).toContain('0 kutu + 18 flakon');
  });
});
