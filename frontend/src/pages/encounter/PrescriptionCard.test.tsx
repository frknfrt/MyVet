// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../auth/FeatureContext', () => ({ useFeatures: () => ({ hasFeature: () => true }) }));
vi.mock('../../api/clinicalApi', () => ({
  clinicalApi: {
    listDrugs: vi.fn().mockResolvedValue([{ id: 'd1', name: 'Amoksisilin', isControlled: false }]),
    listPrescriptionsByPatient: vi.fn().mockResolvedValue([]),
    checkDrugInteractions: vi.fn().mockResolvedValue([]),
    issuePrescription: vi.fn().mockResolvedValue('p1'),
  },
}));
vi.mock('../../api/inventoryApi', () => ({ inventoryApi: { list: vi.fn() } }));
// Seçiciler kendi testlerinde; burada yalnız seçimin sonucunu veriyorlar.
vi.mock('./StockGivePicker', () => ({
  StockGivePicker: ({ onChange }: { onChange: (id: string, name: string) => void }) => (
    <>
      <button type="button" onClick={() => onChange('c', 'Clavon')}>Clavon seç</button>
      <button type="button" onClick={() => onChange('m', 'Metacam')}>Metacam seç</button>
    </>
  ),
}));
vi.mock('./TarbilDiseaseSelect', () => ({
  TarbilDiseaseSelect: ({ onChange }: { onChange: (id: string) => void }) => (
    <button type="button" onClick={() => onChange('h1')}>Hastalık seç</button>
  ),
}));

import { clinicalApi } from '../../api/clinicalApi';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { PrescriptionCard } from './PrescriptionCard';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const item = (over: Partial<InventoryItem>): InventoryItem => ({
  id: 'i', name: 'Kalem', category: 'İlaç', skuBarcode: null, quantityOnHand: 1, reorderThreshold: 0, belowReorderThreshold: false,
  expiryDate: null, lotNumber: null, unitCost: null, tarbilProductName: null, unit: 'ADET', packageSize: null, openedQuantity: 0,
  unitSource: 'AUTO', ...over,
});

let container: HTMLDivElement;
afterEach(() => container?.remove());

async function change(el: HTMLInputElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value);
  await act(async () => {
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

const button = (text: string) => Array.from(container.querySelectorAll('button')).find((b) => b.textContent === text)!;
const click = (el: HTMLElement) => act(async () => el.click());

async function openStockLine(pick: string) {
  vi.mocked(inventoryApi.list).mockResolvedValue([
    item({ id: 'c', name: 'Clavon', unit: 'ML', packageSize: 50, quantityOnHand: 0, openedQuantity: 49 }),
    item({ id: 'm', name: 'Metacam', quantityOnHand: 5 }),
  ]);
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(<PrescriptionCard encounterId="e1" patientId="p" readOnly={false} />);
  });
  await act(async () => {});
  await click(button('+ Yeni Reçete'));
  await click(container.querySelector<HTMLInputElement>('input[aria-label="Kliniğin stoğundan ver"]')!);
  await click(button(pick));
  return container.querySelector<HTMLInputElement>('input[type="number"][step]')!;
}

describe('PrescriptionCard — stoktan verilen miktar (spec 2026-10-24)', () => {
  it('sends a decimal ml amount for an item used by the ml', async () => {
    const amount = await openStockLine('Clavon seç');
    expect(container.textContent).toContain('Miktar (ml)');
    expect(amount.step).toBe('0.01');
    expect(amount.min).toBe('0.01');

    await change(amount, '1.5');
    await change(container.querySelector<HTMLInputElement>('input[placeholder="Örn. 10 mg/kg"]')!, '1 ml');
    await change(container.querySelector<HTMLInputElement>('input[placeholder="Örn. Günde 2 kez"]')!, 'Tek doz');
    await click(button('Hastalık seç'));
    await act(async () => container.querySelector<HTMLFormElement>('form')!.requestSubmit());

    expect(clinicalApi.issuePrescription).toHaveBeenCalledWith(expect.objectContaining({
      tarbilDiseaseId: 'h1',
      items: [expect.objectContaining({ drugId: null, drugName: 'Clavon', inventoryItemId: 'c', stockQuantity: 1.5 })],
    }));
  });

  it('keeps whole-number entry for an item used by the unit', async () => {
    const amount = await openStockLine('Metacam seç');
    expect(container.textContent).toContain('Adet');
    expect(container.textContent).not.toContain('Miktar (');
    expect(amount.step).toBe('1');
    expect(amount.min).toBe('1');
  });
});
