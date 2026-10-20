// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it } from 'vitest';
import { PendingList } from './PendingList';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;

async function renderWith(reply: unknown) {
  (globalThis as unknown as { chrome: unknown }).chrome = { runtime: { sendMessage: async () => reply } };
  container = document.createElement('div');
  document.body.appendChild(container);
  let unauthorized = false;
  await act(async () => {
    createRoot(container).render(<PendingList onUnauthorized={() => { unauthorized = true; }} />);
  });
  return { container, unauthorized: () => unauthorized };
}

afterEach(() => container?.remove());

describe('PendingList', () => {
  it('shows a persistent upgrade warning without unpairing', async () => {
    const { container, unauthorized } = await renderWith({ ok: false, code: 'UPGRADE_REQUIRED', error: 'Yeni sürümü indirin.' });

    const warning = container.querySelector('.warning');
    expect(warning?.textContent).toBe('Yeni sürümü indirin.');
    expect(container.querySelector('button')).toBeNull();
    expect(unauthorized()).toBe(false);
  });

  it('keeps the offline banner with a refresh button', async () => {
    const { container } = await renderWith({ ok: false, code: 'OFFLINE', error: 'x' });

    expect(container.textContent).toContain('Çevrimdışı');
    expect(container.querySelector('button')?.textContent).toBe('Yenile');
  });
});
