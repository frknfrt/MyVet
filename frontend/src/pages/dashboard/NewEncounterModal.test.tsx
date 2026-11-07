// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/patientApi', () => ({ patientApi: { search: vi.fn() } }));
vi.mock('../../api/encounterApi', () => ({ encounterApi: { listByPatient: vi.fn(), start: vi.fn(), cancel: vi.fn() } }));

import { patientApi, PatientSearchResult } from '../../api/patientApi';
import { encounterApi, EncounterDetail } from '../../api/encounterApi';
import { NewEncounterModal } from './NewEncounterModal';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const patient = (over: Partial<PatientSearchResult>): PatientSearchResult => ({
  patientId: 'p1', patientName: 'Pamuk', speciesName: 'Kedi', breedName: null, status: 'ACTIVE',
  ownerId: 'o1', ownerFullName: 'Şeyda FIRAT', ownerPhone: '05058979576', photoVersion: null, ...over,
});

let container: HTMLDivElement;

beforeEach(() => vi.clearAllMocks());
afterEach(() => container?.remove());

async function renderModal() {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(
      <MemoryRouter initialEntries={['/panel']}>
        <Routes>
          <Route path="/panel" element={<NewEncounterModal open onClose={() => undefined} />} />
          <Route path="/muayene/:id" element={<div>MUAYENE SAYFASI</div>} />
        </Routes>
      </MemoryRouter>,
    );
  });
}

async function typeSearch(text: string) {
  const input = container.querySelector('input') as HTMLInputElement;
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  await act(async () => {
    setter.call(input, text);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await act(async () => {});
}

function clickText(text: string) {
  const el = Array.from(container.querySelectorAll('button')).find((b) => b.textContent?.includes(text));
  if (!el) throw new Error(`"${text}" bulunamadı`);
  return act(async () => el.click());
}

describe('NewEncounterModal', () => {
  it('starts a new encounter for a patient without a draft', async () => {
    vi.mocked(patientApi.search).mockResolvedValue([patient({})]);
    vi.mocked(encounterApi.listByPatient).mockResolvedValue([]);
    vi.mocked(encounterApi.start).mockResolvedValue('enc-new');
    await renderModal();

    await typeSearch('Pam');
    expect(patientApi.search).toHaveBeenCalledWith('Pam');
    await clickText('Pamuk');
    await act(async () => {});
    await clickText('Muayeneyi Başlat');
    await act(async () => {});

    expect(encounterApi.start).toHaveBeenCalledWith({ patientId: 'p1' });
    expect(container.textContent).toContain('MUAYENE SAYFASI');
  });

  it('continues the open draft instead of starting a second encounter', async () => {
    vi.mocked(patientApi.search).mockResolvedValue([patient({})]);
    vi.mocked(encounterApi.listByPatient).mockResolvedValue([
      { id: 'enc-draft', status: 'DRAFT', encounterDate: '2026-10-07T22:19:42' } as EncounterDetail,
    ]);
    await renderModal();

    await typeSearch('Pam');
    await clickText('Pamuk');
    await act(async () => {});
    expect(container.textContent).toContain('açık bir taslak muayenesi var');
    await clickText('taslağına dön');
    await act(async () => {});

    expect(encounterApi.start).not.toHaveBeenCalled();
    expect(container.textContent).toContain('MUAYENE SAYFASI');
  });

  it('cancels the old draft and starts a new encounter after confirmation', async () => {
    vi.mocked(patientApi.search).mockResolvedValue([patient({})]);
    vi.mocked(encounterApi.listByPatient).mockResolvedValue([
      { id: 'enc-draft', status: 'DRAFT', encounterDate: '2026-10-07T22:19:42' } as EncounterDetail,
    ]);
    vi.mocked(encounterApi.cancel).mockResolvedValue(undefined);
    vi.mocked(encounterApi.start).mockResolvedValue('enc-new');
    await renderModal();

    await typeSearch('Pam');
    await clickText('Pamuk');
    await act(async () => {});
    expect(container.textContent).toContain('gün önce');
    await clickText('Eski taslağı iptal et');
    expect(encounterApi.cancel).not.toHaveBeenCalled();
    await clickText('Emin misiniz?');
    await act(async () => {});

    expect(encounterApi.cancel).toHaveBeenCalledWith('enc-draft');
    expect(encounterApi.start).toHaveBeenCalledWith({ patientId: 'p1' });
    expect(container.textContent).toContain('MUAYENE SAYFASI');
  });

  it('does not let a deceased patient be selected', async () => {
    vi.mocked(patientApi.search).mockResolvedValue([patient({ status: 'DECEASED' })]);
    await renderModal();

    await typeSearch('Pam');
    const row = Array.from(container.querySelectorAll('button')).find((b) => b.textContent?.includes('Pamuk'));
    expect(row?.disabled).toBe(true);
    expect(container.textContent).toContain('Vefat etti');
  });

  it('does not search for a single character', async () => {
    await renderModal();
    await typeSearch('P');
    expect(patientApi.search).not.toHaveBeenCalled();
  });
});
