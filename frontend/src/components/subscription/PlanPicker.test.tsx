// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CatalogPlan } from '../../api/subscriptionApi';
import { PlanPicker } from './PlanPicker';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function plan(over: Partial<CatalogPlan>): CatalogPlan {
  return {
    id: over.code ?? 'p',
    code: 'PRO',
    name: 'Pro',
    monthlyPrice: 1490,
    annualPrice: 14900,
    description: null,
    badge: null,
    imageUrl: null,
    features: [],
    active: true,
    ...over,
  };
}

const PLANS: CatalogPlan[] = [
  plan({ code: 'BASIC', name: 'Başlangıç', monthlyPrice: 790, annualPrice: null }),
  plan({ code: 'PRO', name: 'Pro', badge: 'Önerilen', features: ['Sınırsız hasta', 'AI asistan'] }),
];

describe('PlanPicker', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
  });

  async function render(onSelect = vi.fn(), plans = PLANS, extra: { submittingPlanCode?: string | null; error?: string | null } = {}) {
    await act(async () => {
      root.render(
        <PlanPicker
          plans={plans}
          onSelect={onSelect}
          submittingPlanCode={extra.submittingPlanCode ?? null}
          error={extra.error ?? null}
        />
      );
    });
    return onSelect;
  }

  function button(label: string): HTMLButtonElement {
    const found = Array.from(container.querySelectorAll('button')).find((b) => b.textContent?.includes(label));
    if (!found) throw new Error(`buton bulunamadı: ${label}`);
    return found;
  }

  function selectButtonOf(planName: string): HTMLButtonElement {
    const card = Array.from(container.querySelectorAll('[data-plan-code]')).find((c) => c.textContent?.includes(planName));
    if (!card) throw new Error(`paket kartı bulunamadı: ${planName}`);
    return card.querySelector('button')!;
  }

  async function click(el: Element) {
    await act(async () => {
      el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
  }

  it('paketleri adı, fiyatı ve özellikleriyle listeler', async () => {
    await render();

    expect(container.textContent).toContain('Başlangıç');
    expect(container.textContent).toContain('790');
    expect(container.textContent).toContain('Pro');
    expect(container.textContent).toContain('1.490');
    expect(container.textContent).toContain('Sınırsız hasta');
  });

  it('yalnızca bir primary buton içerir ve o rozetli pakete aittir', async () => {
    await render();

    const primaries = container.querySelectorAll('button[data-variant="primary"]');
    expect(primaries).toHaveLength(1);
    expect(selectButtonOf('Pro').getAttribute('data-variant')).toBe('primary');
    expect(selectButtonOf('Başlangıç').getAttribute('data-variant')).toBe('secondary');
  });

  it('rozetli paket yoksa ilk paketin butonu primary olur', async () => {
    await render(vi.fn(), [plan({ code: 'A', name: 'A Paketi' }), plan({ code: 'B', name: 'B Paketi' })]);

    expect(container.querySelectorAll('button[data-variant="primary"]')).toHaveLength(1);
    expect(selectButtonOf('A Paketi').getAttribute('data-variant')).toBe('primary');
  });

  it('paket seçilince aylık dönemle onSelect çağırır', async () => {
    const onSelect = await render();

    await click(selectButtonOf('Pro'));

    expect(onSelect).toHaveBeenCalledWith('PRO', 'MONTHLY', undefined);
  });

  it('yıllık seçilince yıllık fiyatı gösterir ve ANNUAL gönderir', async () => {
    const onSelect = await render();

    await click(button('Yıllık'));

    expect(container.textContent).toContain('14.900');
    await click(selectButtonOf('Pro'));
    expect(onSelect).toHaveBeenCalledWith('PRO', 'ANNUAL', undefined);
  });

  it('yıllık fiyatı olmayan pakette yıllık seçimi devre dışıdır', async () => {
    const onSelect = await render();

    await click(button('Yıllık'));

    expect(selectButtonOf('Başlangıç').disabled).toBe(true);
    expect(container.textContent).toContain('Bu pakette yıllık ödeme yok');
    await click(selectButtonOf('Başlangıç'));
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('kupon kodu girilince onSelect ile birlikte gönderir', async () => {
    const onSelect = await render();

    await click(button('Kupon kodum var'));
    const input = container.querySelector('input[name="couponCode"]') as HTMLInputElement;
    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
      setter.call(input, ' hosgeldin ');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await click(selectButtonOf('Pro'));

    expect(onSelect).toHaveBeenCalledWith('PRO', 'MONTHLY', 'hosgeldin');
  });

  it('ödeme başlatılırken tüm butonları kilitler ve hata mesajını gösterir', async () => {
    await render(vi.fn(), PLANS, { submittingPlanCode: 'PRO', error: 'Kupon kodu geçersiz' });

    expect(selectButtonOf('Pro').disabled).toBe(true);
    expect(selectButtonOf('Başlangıç').disabled).toBe(true);
    expect(selectButtonOf('Pro').textContent).toContain('Yönlendiriliyor');
    expect(container.textContent).toContain('Kupon kodu geçersiz');
  });

  it('paket yokken bilgilendirme mesajı gösterir', async () => {
    await render(vi.fn(), []);

    expect(container.textContent).toContain('Şu anda satın alınabilir paket yok');
  });
});
