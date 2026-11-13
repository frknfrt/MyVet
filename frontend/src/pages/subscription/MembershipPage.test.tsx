// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const authState = vi.hoisted(() => ({
  session: { fullName: 'Ayşe Yılmaz', role: 'ADMIN' } as { fullName: string; role: string } | null,
  logout: vi.fn(),
}));
const accessState = vi.hoisted(() => ({
  access: { state: 'TRIAL_EXPIRED', trialEndsAt: '2026-11-12', daysLeft: 0 } as { state: string; trialEndsAt: string | null; daysLeft: number | null } | null,
}));
const redirect = vi.hoisted(() => vi.fn());

vi.mock('../../auth/AuthContext', () => ({ useAuth: () => authState }));
vi.mock('../../auth/SubscriptionAccessContext', () => ({
  useSubscriptionAccess: () => ({ access: accessState.access, markExpired: vi.fn(), reload: vi.fn() }),
}));
vi.mock('../../api/subscriptionApi', async () => {
  const actual = await vi.importActual<typeof import('../../api/subscriptionApi')>('../../api/subscriptionApi');
  return {
    ...actual,
    subscriptionApi: { ...actual.subscriptionApi, getPlans: vi.fn(), initiateUpgradeCheckout: vi.fn() },
  };
});
vi.mock('./redirectTo', () => ({ redirectTo: redirect }));

import { ApiError } from '../../api/client';
import { subscriptionApi } from '../../api/subscriptionApi';
import { MembershipPage } from './MembershipPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const getPlans = vi.mocked(subscriptionApi.getPlans);
const initiateUpgradeCheckout = vi.mocked(subscriptionApi.initiateUpgradeCheckout);

const PLAN = {
  id: 'p1', code: 'PRO', name: 'Pro', monthlyPrice: 1490, annualPrice: 14900,
  description: null, badge: 'Önerilen', imageUrl: null, features: ['AI asistan'], active: true,
};

describe('MembershipPage', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    authState.session = { fullName: 'Ayşe Yılmaz', role: 'ADMIN' };
    accessState.access = { state: 'TRIAL_EXPIRED', trialEndsAt: '2026-11-12', daysLeft: 0 };
    getPlans.mockResolvedValue([PLAN]);
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.clearAllMocks();
  });

  async function render(entry = '/uyelik') {
    await act(async () => {
      root.render(
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path="/uyelik" element={<MembershipPage />} />
            <Route path="/login" element={<div>giriş sayfası</div>} />
            <Route path="/panel" element={<div>panel</div>} />
          </Routes>
        </MemoryRouter>
      );
    });
  }

  function selectButton(): HTMLButtonElement {
    return container.querySelector('[data-plan-code="PRO"] button') as HTMLButtonElement;
  }

  it('ADMIN için uyarı metnini ve paketleri gösterir', async () => {
    await render();

    expect(container.textContent).toContain('Deneme kullanım süreniz dolmuştur, lütfen üyelik başlatınız.');
    expect(container.textContent).toContain('Pro');
    expect(container.textContent).toContain('AI asistan');
  });

  it('ADMIN olmayan role yöneticiyle iletişime geçin mesajını gösterir ve paket göstermez', async () => {
    authState.session = { fullName: 'Veli Kaya', role: 'VET' };

    await render();

    expect(container.textContent).toContain('Deneme süresi doldu, klinik yöneticinizle iletişime geçin.');
    expect(container.querySelector('[data-plan-code]')).toBeNull();
    expect(getPlans).not.toHaveBeenCalled();
  });

  it('paket seçilince ödeme adresine yönlenir', async () => {
    initiateUpgradeCheckout.mockResolvedValue({ checkoutFormUrl: 'https://odeme.example/form' });
    await render();

    await act(async () => {
      selectButton().dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(initiateUpgradeCheckout).toHaveBeenCalledWith({ planCode: 'PRO', billingPeriod: 'MONTHLY', couponCode: undefined });
    expect(redirect).toHaveBeenCalledWith('https://odeme.example/form');
  });

  it('ödeme başlatılamazsa hata mesajını gösterir ve butonu yeniden açar', async () => {
    initiateUpgradeCheckout.mockRejectedValue(new ApiError('Kupon kodu geçersiz', 'COUPON_NOT_REDEEMABLE', 422));
    await render();

    await act(async () => {
      selectButton().dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(container.textContent).toContain('Kupon kodu geçersiz');
    expect(selectButton().disabled).toBe(false);
    expect(redirect).not.toHaveBeenCalled();
  });

  it('ödeme hatayla döndüyse bunu bildirir', async () => {
    await render('/uyelik?odeme=hata');

    expect(container.textContent).toContain('Ödeme tamamlanamadı, lütfen tekrar deneyin.');
  });

  it('çıkış bağlantısı oturumu kapatır', async () => {
    await render();

    const logoutButton = Array.from(container.querySelectorAll('button')).find((b) => b.textContent?.includes('Çıkış Yap'))!;
    await act(async () => {
      logoutButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(authState.logout).toHaveBeenCalled();
  });

  it('oturum yoksa giriş sayfasına yönlendirir', async () => {
    authState.session = null;

    await render();

    expect(container.textContent).toBe('giriş sayfası');
  });

  it('deneme dolmamışsa panele yönlendirir', async () => {
    accessState.access = { state: 'ACTIVE', trialEndsAt: null, daysLeft: null };

    await render();

    expect(container.textContent).toBe('panel');
  });
});
