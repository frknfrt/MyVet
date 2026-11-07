// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/dataImportApi', () => ({ dataImportApi: { getLegacyData: vi.fn() } }));

import { dataImportApi } from '../../api/dataImportApi';
import { LegacyDataCard } from './LegacyDataCard';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
afterEach(() => container?.remove());

async function mount() {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(<LegacyDataCard entityType="OWNER" id="o1" />);
  });
}

describe('LegacyDataCard', () => {
  it('renders nothing when no legacy data', async () => {
    vi.mocked(dataImportApi.getLegacyData).mockResolvedValue(null);
    await mount();
    expect(container.innerHTML).toBe('');
  });

  it('renders key-value rows collapsed by default and expands on click', async () => {
    vi.mocked(dataImportApi.getLegacyData).mockResolvedValue({
      sourceSystem: 'KolayVet',
      sourceId: '17',
      importedAt: '2026-11-05T10:00:00Z',
      data: { 'Bakiye Notu': 'Borcu var', Not2: 'x' },
    });
    await mount();

    const toggle = container.querySelector('button') as HTMLButtonElement;
    expect(toggle.textContent).toContain('Eski sistem bilgileri (KolayVet)');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(container.textContent).not.toContain('Borcu var');

    await act(async () => {
      toggle.click();
    });

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(container.textContent).toContain('Bakiye Notu');
    expect(container.textContent).toContain('Borcu var');
    expect(container.textContent).toContain('Not2');
  });

  it('shows a muted note when the data map is empty', async () => {
    vi.mocked(dataImportApi.getLegacyData).mockResolvedValue({
      sourceSystem: 'KolayVet', sourceId: '17', importedAt: '2026-11-05T10:00:00Z', data: {},
    });
    await mount();
    await act(async () => {
      (container.querySelector('button') as HTMLButtonElement).click();
    });
    expect(container.textContent).toContain('Ek bilgi yok');
  });
});
