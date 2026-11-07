// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ session: { fullName: 'Ayşe Yılmaz', role: 'VET' }, logout: vi.fn() }),
}));
vi.mock('./Sidebar', () => ({ Sidebar: () => null }));

import { AppShell } from './AppShell';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
afterEach(() => container?.remove());

describe('AppShell — mobil ust cubuk', () => {
  it('takes the user to the dashboard when the Vetly word is tapped', async () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(
        <MemoryRouter initialEntries={['/hastalar']}>
          <Routes>
            <Route path="/hastalar" element={<AppShell>hasta listesi</AppShell>} />
            <Route path="/panel" element={<div>ana sayfa</div>} />
          </Routes>
        </MemoryRouter>
      );
    });

    const brand = Array.from(container.querySelectorAll('a')).find((a) => a.textContent === 'Vetly');
    expect(brand?.getAttribute('href')).toBe('/panel');
    await act(async () => brand!.dispatchEvent(new MouseEvent('click', { bubbles: true, button: 0 })));
    expect(container.textContent).toBe('ana sayfa');
  });
});
