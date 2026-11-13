// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ session: null, login: vi.fn(), logout: vi.fn() }) }));

import { LoginPage } from './LoginPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe('LoginPage — deneme kaydı sonrası', () => {
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

  async function render(entry: string) {
    await act(async () => {
      root.render(
        <MemoryRouter initialEntries={[entry]}>
          <LoginPage />
        </MemoryRouter>
      );
    });
  }

  function emailInput(): HTMLInputElement {
    return container.querySelector('input[type="email"]') as HTMLInputElement;
  }

  it('email parametresiyle gelindiğinde e-postayı doldurur', async () => {
    await render('/login?email=ayse%40ornek.com&deneme=1');

    expect(emailInput().value).toBe('ayse@ornek.com');
  });

  it('deneme=1 ile gelindiğinde bilgi notunu gösterir', async () => {
    await render('/login?email=ayse%40ornek.com&deneme=1');

    expect(container.textContent).toContain('Hesabınız oluşturuldu. Şifrenizle giriş yapın.');
  });

  it('parametre yokken e-posta boş gelir ve bilgi notu görünmez', async () => {
    await render('/login');

    expect(emailInput().value).toBe('');
    expect(container.textContent).not.toContain('Hesabınız oluşturuldu');
  });
});
