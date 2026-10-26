// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/encounterApi', () => ({
  encounterApi: { listMaterials: vi.fn().mockResolvedValue([]), addMaterial: vi.fn().mockResolvedValue('u1') },
}));
vi.mock('../../api/inventoryApi', () => ({ inventoryApi: { list: vi.fn() } }));

import { encounterApi } from '../../api/encounterApi';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { MaterialsUsedCard } from './MaterialsUsedCard';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const item = (over: Partial<InventoryItem>): InventoryItem => ({
  id: 'i', name: 'Kalem', category: 'İlaç', skuBarcode: null, quantityOnHand: 1, reorderThreshold: 0, belowReorderThreshold: false,
  expiryDate: null, lotNumber: null, unitCost: null, tarbilProductName: null, unit: 'ADET', packageSize: null, openedQuantity: 0,
  unitSource: 'AUTO', pieceSize: null, pieceLabel: null, openedExpiryDate: null, ...over,
});

let container: HTMLDivElement;
afterEach(() => container?.remove());

async function change(el: HTMLSelectElement | HTMLInputElement, value: string) {
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  await act(async () => {
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  });
}

describe('MaterialsUsedCard — ml ile kullanılan ilaç (spec 2026-10-24)', () => {
  it('lets an ml item be entered as a decimal amount', async () => {
    vi.mocked(inventoryApi.list).mockResolvedValue([
      item({ id: 'a', name: 'Bandaj' }),
      item({ id: 'c', name: 'Clavon', unit: 'ML', packageSize: 50, quantityOnHand: 0, openedQuantity: 49 }),
    ]);
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(<MaterialsUsedCard encounterId="e1" readOnly={false} />);
    });
    await act(async () => {});

    const select = container.querySelector('select')!;
    expect(container.textContent).toContain('Clavon (0 şişe + 49 ml mevcut)');
    await change(select, 'c');
    const input = container.querySelector<HTMLInputElement>('input[type="number"]')!;
    expect(input.step).toBe('0.01');
    expect(container.textContent).toContain('Miktar (ml)');
    await change(input, '1.5');
    await act(async () => container.querySelector<HTMLFormElement>('form')!.requestSubmit());

    expect(encounterApi.addMaterial).toHaveBeenCalledWith('e1', { inventoryItemId: 'c', quantity: 1.5 });
  });
});
