// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/encounterApi', () => ({ encounterApi: { listOpen: vi.fn() } }));
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ session: { role: 'VET' } }) }));
vi.mock('../../components/layout/AppShell', () => ({ AppShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock('../dashboard/NewEncounterModal', () => ({ NewEncounterModal: () => null }));

import { encounterApi, EncounterDetail } from '../../api/encounterApi';
import { EncountersPage } from './EncountersPage';
import { ageLabel, daysOpen } from './encounterAge';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
afterEach(() => container?.remove());

const enc = (id: string, patientName: string, encounterDate: string) =>
  ({ id, patientName, staffName: 'Dr. Furkan', encounterDate, status: 'DRAFT' }) as EncounterDetail;

describe('encounterAge', () => {
  it('counts whole days and labels them', () => {
    const now = new Date('2026-11-05T12:00:00Z');
    expect(daysOpen('2026-11-05T08:00:00Z', now)).toBe(0);
    expect(daysOpen('2026-10-21T12:00:00Z', now)).toBe(15);
    expect(ageLabel(0)).toBe('Bugün');
    expect(ageLabel(1)).toBe('Dün');
    expect(ageLabel(15)).toBe('15 gün önce');
  });
});

describe('EncountersPage', () => {
  it('lists open drafts, flags stale ones and opens a draft on click', async () => {
    const old = new Date(Date.now() - 15 * 24 * 3600 * 1000).toISOString();
    vi.mocked(encounterApi.listOpen).mockResolvedValue([enc('e1', 'Çomar', old), enc('e2', 'Pamuk', new Date().toISOString())]);
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(
        <MemoryRouter initialEntries={['/muayeneler']}>
          <Routes>
            <Route path="/muayeneler" element={<EncountersPage />} />
            <Route path="/muayene/:id" element={<div>MUAYENE SAYFASI</div>} />
          </Routes>
        </MemoryRouter>,
      );
    });

    expect(container.textContent).toContain('1 muayene 2 günden uzun süredir açık');
    expect(container.textContent).toContain('15 gün önce');
    expect(container.textContent).toContain('Bugün');
    const row = Array.from(container.querySelectorAll('button')).find((b) => b.textContent?.includes('Çomar'))!;
    await act(async () => row.click());
    expect(container.textContent).toContain('MUAYENE SAYFASI');
  });
});
