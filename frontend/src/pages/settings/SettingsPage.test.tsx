// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ session: { role: 'ADMIN', fullName: 'A' } }) }));
vi.mock('../../auth/FeatureContext', () => ({ useFeatures: () => ({ hasFeature: () => true }) }));
vi.mock('../../components/layout/AppShell', () => ({ AppShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock('./ActivityHistoryPanel', () => ({ ActivityHistoryPanel: () => null }));
vi.mock('./AiCenterPanel', () => ({ AiCenterPanel: () => null }));
vi.mock('./BranchManagementPanel', () => ({ BranchManagementPanel: () => <div>subeler-paneli</div> }));
vi.mock('./DrugCatalogPanel', () => ({ DrugCatalogPanel: () => null }));
vi.mock('./IntegrationsPanel', () => ({ IntegrationsPanel: () => null }));
vi.mock('./RolePermissionsPanel', () => ({ RolePermissionsPanel: () => null }));
vi.mock('./ServiceTypesPanel', () => ({ ServiceTypesPanel: () => null }));
vi.mock('./SmsWhatsappPanel', () => ({ SmsWhatsappPanel: () => null }));
vi.mock('./SpeciesBreedsPanel', () => ({ SpeciesBreedsPanel: () => null }));
vi.mock('./StaffManagementPanel', () => ({ StaffManagementPanel: () => <div>kullanicilar-paneli</div> }));
vi.mock('./SubscriptionPanel', () => ({ SubscriptionPanel: () => null }));
vi.mock('./WorkingHoursPanel', () => ({ WorkingHoursPanel: () => null }));
vi.mock('./forms/FormsSettings', () => ({ FormsSettings: () => null }));

import { SettingsPage } from './SettingsPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
afterEach(() => container?.remove());

describe('SettingsPage sekmeleri', () => {
  it('links every tab to /ayarlar/<tab> even when another tab is open, and switches panels', async () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(
        <MemoryRouter initialEntries={['/ayarlar/kullanicilar']}>
          <Routes>
            <Route path="/ayarlar/*" element={<SettingsPage />} />
          </Routes>
        </MemoryRouter>
      );
    });
    expect(container.textContent).toContain('kullanicilar-paneli');

    const subeler = Array.from(container.querySelectorAll('a')).find((a) => a.textContent === 'Şubeler')!;
    expect(subeler.getAttribute('href')).toBe('/ayarlar/subeler');

    await act(async () => subeler.dispatchEvent(new MouseEvent('click', { bubbles: true, button: 0 })));
    expect(container.textContent).toContain('subeler-paneli');
  });
});
