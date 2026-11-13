// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const authState = vi.hoisted(() => ({ session: { role: 'ADMIN' } as { role: string } | null }));
const accessState = vi.hoisted(() => ({
  access: null as { state: string; trialEndsAt: string | null; daysLeft: number | null } | null,
}));

vi.mock('./AuthContext', () => ({ useAuth: () => authState }));
vi.mock('./FeatureContext', () => ({ useFeatures: () => ({ hasFeature: () => true }) }));
vi.mock('./SubscriptionAccessContext', () => ({
  useSubscriptionAccess: () => ({ access: accessState.access, markExpired: vi.fn(), reload: vi.fn() }),
}));

import { RequireAuth } from './RequireAuth';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function MembershipProbe() {
  const location = useLocation();
  return <div>üyelik sayfası{location.search}</div>;
}

describe('RequireAuth', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    authState.session = { role: 'ADMIN' };
    accessState.access = null;
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
  });

  async function render(entry = '/hastalar') {
    await act(async () => {
      root.render(
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path="/hastalar" element={<RequireAuth>hasta listesi</RequireAuth>} />
            <Route path="/ayarlar/abonelik" element={<RequireAuth>abonelik ayarları</RequireAuth>} />
            <Route path="/uyelik" element={<MembershipProbe />} />
            <Route path="/login" element={<div>giriş sayfası</div>} />
          </Routes>
        </MemoryRouter>
      );
    });
  }

  it('süresi dolmuş denemede /uyelik sayfasına yönlendirir', async () => {
    accessState.access = { state: 'TRIAL_EXPIRED', trialEndsAt: '2026-11-12', daysLeft: 0 };

    await render();

    expect(container.textContent).toBe('üyelik sayfası');
  });

  it('yönlendirirken ödeme sonucu parametresini korur', async () => {
    accessState.access = { state: 'TRIAL_EXPIRED', trialEndsAt: '2026-11-12', daysLeft: 0 };

    await render('/ayarlar/abonelik?odeme=hata');

    expect(container.textContent).toBe('üyelik sayfası?odeme=hata');
  });

  it('erişim durumu yüklenemediğinde sayfayı gösterir', async () => {
    accessState.access = null;

    await render();

    expect(container.textContent).toBe('hasta listesi');
  });

  it('deneme sürerken sayfayı gösterir', async () => {
    accessState.access = { state: 'TRIAL', trialEndsAt: '2026-11-20', daysLeft: 7 };

    await render();

    expect(container.textContent).toBe('hasta listesi');
  });

  it('oturum yoksa giriş sayfasına yönlendirir', async () => {
    authState.session = null;
    accessState.access = { state: 'TRIAL_EXPIRED', trialEndsAt: '2026-11-12', daysLeft: 0 };

    await render();

    expect(container.textContent).toBe('giriş sayfası');
  });
});
