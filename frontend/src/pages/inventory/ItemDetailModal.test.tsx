// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ session: { role: 'ADMIN' } }) }));
vi.mock('../../api/inventoryApi', () => ({
  inventoryApi: { listMovements: vi.fn().mockResolvedValue([]), recordMovement: vi.fn(), update: vi.fn().mockResolvedValue(undefined) },
}));

import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { ItemDetailModal } from './ItemDetailModal';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const CLAVON: InventoryItem = {
  id: 'c', name: 'Clavon', category: 'İlaç', skuBarcode: null, quantityOnHand: 0, reorderThreshold: 1, belowReorderThreshold: true,
  expiryDate: null, lotNumber: '63016', unitCost: null, tarbilProductName: 'CLAVON = Clavon', unit: 'ADET', packageSize: null,
  openedQuantity: 0, unitSource: 'AUTO', pieceSize: null, pieceLabel: null, openedExpiryDate: null,
};

let container: HTMLDivElement;
afterEach(() => container?.remove());

async function change(el: HTMLSelectElement | HTMLInputElement, value: string) {
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  await act(async () => {
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  });
}

describe('ItemDetailModal — kullanım birimi (spec 2026-10-24)', () => {
  it('saves a manually chosen unit and package size', async () => {
    const onChanged = vi.fn();
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(<ItemDetailModal item={CLAVON} onClose={() => undefined} onChanged={onChanged} />);
    });
    expect(document.body.textContent).toContain('TARBİL bilgisinden tahmin edildi');

    await change(document.querySelector<HTMLSelectElement>('select[aria-label="Kullanım birimi"]')!, 'ML');
    await change(document.querySelector<HTMLInputElement>('input[aria-label="Paket büyüklüğü"]')!, '50');
    const save = Array.from(document.querySelectorAll('button')).find((b) => b.textContent === 'Birimi Kaydet')!;
    await act(async () => save.click());

    expect(inventoryApi.update).toHaveBeenCalledWith('c', {
      name: 'Clavon', category: 'İlaç', reorderThreshold: 1, unitCost: null, unit: 'ML', packageSize: 50, pieceSize: null, pieceLabel: null,
    });
    expect(onChanged).toHaveBeenCalled();
  });
});

describe('ItemDetailModal — stok hareketleri', () => {
  it('shows the open-amount change of a movement instead of "-0"', async () => {
    vi.mocked(inventoryApi.listMovements).mockResolvedValueOnce([
      { id: 'm1', movementType: 'OUT', quantity: 0, openedDelta: -1, referenceType: 'ENCOUNTER', referenceId: null, createdAt: '2026-10-24T10:00:00Z' },
      { id: 'm2', movementType: 'OUT', quantity: 1, openedDelta: 49, referenceType: 'ENCOUNTER', referenceId: null, createdAt: '2026-10-24T09:00:00Z' },
    ]);
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(<ItemDetailModal item={{ ...CLAVON, unit: 'ML', packageSize: 50, openedQuantity: 48 }}
        onClose={() => undefined} onChanged={() => undefined} />);
    });

    expect(document.body.textContent).toContain('-1 ml açık');
    expect(document.body.textContent).toContain('-1 şişe, +49 ml açık');
    expect(document.body.textContent).not.toContain('-0');
  });
});

describe('ItemDetailModal — açık miktar ve tarih (spec 2026-10-25)', () => {
  async function render(item: InventoryItem) {
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(<ItemDetailModal item={item} onClose={() => undefined} onChanged={() => undefined} />);
    });
  }

  it('opened adjustment sends openedDelta', async () => {
    vi.mocked(inventoryApi.recordMovement).mockResolvedValue(undefined);
    await render({ ...CLAVON, unit: 'ML', packageSize: 50, openedQuantity: 49 });
    await change(document.querySelector<HTMLSelectElement>('select[aria-label="Hareket türü"]')!, 'ADJUSTMENT');
    await change(document.querySelector<HTMLInputElement>('input[aria-label="Kapalı adet"]')!, '0');
    await change(document.querySelector<HTMLInputElement>('input[aria-label="Açık miktar farkı"]')!, '-3');
    const apply = Array.from(document.querySelectorAll('button')).find((b) => b.textContent === 'Uygula')!;
    await act(async () => apply.closest('form')!.requestSubmit());

    expect(inventoryApi.recordMovement).toHaveBeenCalledWith('c', { movementType: 'ADJUSTMENT', quantity: 0, openedDelta: -3 });
  });

  it('shows expired opened badge', async () => {
    await render({ ...CLAVON, unit: 'ML', packageSize: 50, openedQuantity: 20, openedExpiryDate: '2020-01-01' });
    expect(document.body.textContent).toContain('Açık ürünün süresi doldu');
  });

  it('offers tablet unit and pipet size for ml items', async () => {
    await render({ ...CLAVON, unit: 'ML', packageSize: 1.5 });
    const select = document.querySelector<HTMLSelectElement>('select[aria-label="Kullanım birimi"]')!;
    expect(Array.from(select.options).map((o) => o.value)).toContain('TABLET');
    await change(document.querySelector<HTMLInputElement>('input[aria-label="Pipet ml"]')!, '0.5');
    const save = Array.from(document.querySelectorAll('button')).find((b) => b.textContent === 'Birimi Kaydet')!;
    await act(async () => save.click());
    expect(inventoryApi.update).toHaveBeenLastCalledWith('c', expect.objectContaining({ unit: 'ML', packageSize: 1.5, pieceSize: 0.5, pieceLabel: 'pipet' }));
  });
});
