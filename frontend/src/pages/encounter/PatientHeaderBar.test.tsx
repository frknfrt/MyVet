// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { expect, it, vi } from 'vitest';
import type { PatientProfile } from '../../api/patientApi';
import type { EncounterDetail } from '../../api/encounterApi';
vi.mock('../forms/FillFormButton', () => ({ FillFormButton: () => <button>FORM_DOLDUR</button> }));
import { PatientHeaderBar } from './PatientHeaderBar';
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
it.each(['ACTIVE', 'DECEASED', 'TRANSFERRED'] as const)('only active patient offers new form: %s', async status => {
  const container = document.createElement('div'); document.body.appendChild(container); const root = createRoot(container);
  const profile = { id: 'p', name: 'Pamuk', status, birthDate: null, ownerFullName: 'Sahip', sex: null } as PatientProfile;
  const encounter = { id: 'e', status: 'DRAFT', encounterDate: '2026-11-07T10:00:00Z', staffName: 'Hekim' } as EncounterDetail;
  try {
    await act(async () => root.render(<PatientHeaderBar profile={profile} encounter={encounter} onBack={() => {}} />));
    expect(container.textContent?.includes('FORM_DOLDUR')).toBe(status === 'ACTIVE');
    expect(container.textContent).toContain('Hastalara Dön');
  } finally { act(() => root.unmount()); container.remove(); }
});
