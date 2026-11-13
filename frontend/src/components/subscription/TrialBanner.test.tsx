// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const authState = vi.hoisted(() => ({ session: { role: 'ADMIN' } as { role: string } | null }));
const accessState = vi.hoisted(() => ({
  access: null as { state: string; trialEndsAt: string | null; daysLeft: number | null } | null,
}));

vi.mock('../../auth/AuthContext', () => ({ useAuth: () => authState }));
vi.mock('../../auth/SubscriptionAccessContext', () => ({
  useSubscriptionAccess: () => ({ access: accessState.access, markExpired: vi.fn(), reload: vi.fn() }),
}));

import { TrialBanner } from './TrialBanner';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe('TrialBanner', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    authState.session = { role: 'ADMIN' };
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
  });

  async function render(access: typeof accessState.access) {
    accessState.access = access;
    await act(async () => {
      root.render(
        <MemoryRouter>
          <TrialBanner />
        </MemoryRouter>
      );
    });
  }

  function banner(): HTMLElement | null {
    return container.querySelector('[data-testid="trial-banner"]');
  }

  it('7 gün kaldığında nötr bant gösterir', async () => {
    await render({ state: 'TRIAL', trialEndsAt: '2026-11-20', daysLeft: 7 });

    expect(banner()?.textContent).toContain('Denemenizin bitmesine 7 gün kaldı.');
    expect(banner()?.getAttribute('data-tone')).toBe('neutral');
  });

  it('2 gün kaldığında warning tonu kullanır', async () => {
    await render({ state: 'TRIAL', trialEndsAt: '2026-11-15', daysLeft: 2 });

    expect(banner()?.textContent).toContain('Denemenizin bitmesine 2 gün kaldı.');
    expect(banner()?.getAttribute('data-tone')).toBe('warning');
  });

  it('3 gün kaldığında hâlâ nötr tondadır', async () => {
    await render({ state: 'TRIAL', trialEndsAt: '2026-11-16', daysLeft: 3 });

    expect(banner()?.getAttribute('data-tone')).toBe('neutral');
  });

  it('son gün "bugün sona eriyor" yazar', async () => {
    await render({ state: 'TRIAL', trialEndsAt: '2026-11-13', daysLeft: 0 });

    expect(banner()?.textContent).toContain('Denemeniz bugün sona eriyor.');
    expect(banner()?.getAttribute('data-tone')).toBe('warning');
  });

  it.each(['INTERNAL', 'ACTIVE', 'TRIAL_EXPIRED'])('%s durumunda bant göstermez', async (state) => {
    await render({ state, trialEndsAt: null, daysLeft: null });

    expect(banner()).toBeNull();
  });

  it('erişim durumu yüklenmemişken bant göstermez', async () => {
    await render(null);

    expect(banner()).toBeNull();
  });

  it('ADMIN için abonelik sayfasına giden Üyelik Başlat bağlantısını gösterir', async () => {
    await render({ state: 'TRIAL', trialEndsAt: '2026-11-20', daysLeft: 7 });

    const link = banner()?.querySelector('a');
    expect(link?.textContent).toBe('Üyelik Başlat');
    expect(link?.getAttribute('href')).toBe('/ayarlar/abonelik');
  });

  it('ADMIN olmayan role Üyelik Başlat bağlantısı göstermez', async () => {
    authState.session = { role: 'VET' };

    await render({ state: 'TRIAL', trialEndsAt: '2026-11-20', daysLeft: 7 });

    expect(banner()?.textContent).toContain('Denemenizin bitmesine 7 gün kaldı.');
    expect(banner()?.querySelector('a')).toBeNull();
  });
});
