// @vitest-environment jsdom
import { act, ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../components/layout/AppShell', () => ({ AppShell: ({ children }: { children: ReactNode }) => <>{children}</> }));
vi.mock('../../api/patientApi', () => ({
  patientApi: {
    getOwnerProfile: vi.fn().mockResolvedValue({
      id: 'o1', fullName: 'Ayşe Yılmaz', phone: '5550000000', patients: [{ id: 'p1', name: 'Pamuk' }],
    }),
    searchOwners: vi.fn().mockResolvedValue([]),
  },
}));
vi.mock('../../api/inventoryApi', () => ({ inventoryApi: { list: vi.fn() } }));
vi.mock('../../api/vaccinationApi', () => ({ vaccinationApi: { record: vi.fn(), recordSeries: vi.fn() } }));

import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import { vaccinationApi } from '../../api/vaccinationApi';
import { NewVaccinationPage } from './NewVaccinationPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const item = (id: string, name: string, lot: string): InventoryItem => ({
  id, name, category: 'Aşı', skuBarcode: null, quantityOnHand: 5, reorderThreshold: 0, belowReorderThreshold: false,
  expiryDate: null, lotNumber: lot, unitCost: null, tarbilProductName: null, unit: 'ADET', packageSize: null, openedQuantity: 0, unitSource: 'AUTO', pieceSize: null, pieceLabel: null, openedExpiryDate: null });

let container: HTMLDivElement;

async function render() {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(
      <MemoryRouter initialEntries={['/asi/yeni?ownerId=o1&patientId=p1']}>
        <NewVaccinationPage />
      </MemoryRouter>
    );
  });
  await act(async () => {});
}

const byLabel = <T extends HTMLElement>(label: string) => container.querySelector<T>(`[aria-label="${label}"]`)!;
const button = (text: string) =>
  Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.includes(text))!;

async function change(el: HTMLSelectElement | HTMLInputElement, value: string) {
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  await act(async () => {
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  });
}

beforeEach(() => {
  vi.mocked(inventoryApi.list).mockResolvedValue([item('i1', 'Drontal', 'D1'), item('i2', 'Frontline', 'F1')]);
  vi.mocked(vaccinationApi.record).mockReset().mockResolvedValue('v1');
});
afterEach(() => container?.remove());

describe('NewVaccinationPage — ikinci ürün', () => {
  it('records each product separately, each with its own stock item', async () => {
    await render();
    await change(byLabel<HTMLSelectElement>('Aşı stoktan'), 'i1');
    await act(async () => button('İkinci ürün ekle').click());

    const second = byLabel<HTMLSelectElement>('2. ürün stoktan');
    expect(Array.from(second.options).map((o) => o.value)).toEqual(['', 'i2']);
    await change(second, 'i2');
    expect(button('2 Aşı Kaydı Oluştur')).toBeTruthy();

    await act(async () => button('Kaydet ve Yeni').click());

    const calls = vi.mocked(vaccinationApi.record).mock.calls.map(([p]) => [p.vaccineName, p.inventoryItemId, p.lotNumber]);
    expect(calls).toEqual([
      ['Drontal', 'i1', 'D1'],
      ['Frontline', 'i2', 'F1'],
    ]);
    expect(container.textContent).toContain('2 aşı kaydı oluşturuldu');
  });

  it('keeps only the failed second product in the form so a retry does not duplicate the first', async () => {
    vi.mocked(vaccinationApi.record).mockResolvedValueOnce('v1').mockRejectedValueOnce(new Error('Sunucu hatası.'));
    await render();
    await change(byLabel<HTMLSelectElement>('Aşı stoktan'), 'i1');
    await act(async () => button('İkinci ürün ekle').click());
    await change(byLabel<HTMLSelectElement>('2. ürün stoktan'), 'i2');

    await act(async () => button('Kaydet ve Yeni').click());

    expect(container.textContent).toContain('"Drontal" kaydedildi, "Frontline" kaydedilemedi');
    expect(byLabel<HTMLSelectElement>('Aşı stoktan').value).toBe('i2');
    expect(container.querySelector('[aria-label="2. ürün stoktan"]')).toBeNull();
  });

  it('cannot save while the added second product is empty', async () => {
    await render();
    await change(byLabel<HTMLSelectElement>('Aşı stoktan'), 'i1');
    await act(async () => button('İkinci ürün ekle').click());

    expect(button('2 Aşı Kaydı Oluştur').disabled).toBe(true);
    await change(byLabel<HTMLInputElement>('2. ürün adı'), 'Dış Parazit');
    expect(button('2 Aşı Kaydı Oluştur').disabled).toBe(false);
  });
});
