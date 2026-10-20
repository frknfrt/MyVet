// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/tarbilApi', () => ({
  tarbilApi: {
    extensionTokens: vi.fn().mockResolvedValue([]),
    createPairingCode: vi.fn(),
    revokeExtensionToken: vi.fn(),
  },
}));
vi.mock('../../lib/tarbilExtension', () => ({ getInstalledExtensionVersion: vi.fn() }));

import { tarbilApi } from '../../api/tarbilApi';
import { getInstalledExtensionVersion } from '../../lib/tarbilExtension';
import { TarbilExtensionCard } from './TarbilExtensionCard';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;

async function render(installed: string | null, releaseJson: string | null) {
  vi.mocked(getInstalledExtensionVersion).mockResolvedValue(installed);
  vi.stubGlobal('fetch', async () =>
    releaseJson === null ? new Response('not found', { status: 404 }) : new Response(releaseJson, { status: 200 }));
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(<TarbilExtensionCard />);
  });
  await act(async () => {});
  const link = container.querySelector<HTMLAnchorElement>('a[download]');
  return { text: container.textContent ?? '', link };
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => {
  container?.remove();
  vi.unstubAllGlobals();
});

describe('TarbilExtensionCard version row', () => {
  it('not installed', async () => {
    const { text, link } = await render(null, '{"version":"0.2.0"}');
    expect(text).toContain('Eklenti yüklü değil. İndirip kurun.');
    expect(link?.getAttribute('href')).toBe('/downloads/vetly-tarbil-eklenti.zip?v=0.2.0');
  });

  it('up to date', async () => {
    const { text } = await render('0.2.0', '{"version":"0.2.0"}');
    expect(text).toContain('Yüklü sürüm: 0.2.0 (güncel)');
  });

  it('outdated', async () => {
    const { text, link } = await render('0.1.0', '{"version":"0.2.0"}');
    expect(text).toContain('Yüklü sürüm: 0.1.0. Yeni sürüm var (0.2.0), indirip güncelleyin.');
    expect(link).not.toBeNull();
  });

  it('release file missing', async () => {
    const { text, link } = await render('0.1.0', null);
    expect(text).toContain('Eklenti dosyası şu an hazır değil');
    expect(text).toContain('Yüklü sürüm: 0.1.0');
    expect(text).not.toContain('Yeni sürüm var');
    expect(link).toBeNull();
  });

  it('not installed and release file missing', async () => {
    const { text } = await render(null, null);
    expect(text).toContain('Eklenti yüklü değil.');
    expect(text).not.toContain('İndirip kurun.');
  });

  it('shows install and update steps', async () => {
    const { text } = await render(null, '{"version":"0.2.0"}');
    expect(text).toContain('chrome://extensions');
    expect(text).toContain('Paketlenmemiş öğe yükle');
  });
});

function token(over: Partial<{ lastUsedAt: string | null; revokedAt: string | null; lastClientVersion: string | null }>) {
  return {
    id: crypto.randomUUID(),
    staffUserId: 's',
    staffName: 'Dr. Test',
    label: 'Muayene PC',
    pairedAt: '2026-10-01T08:00:00Z',
    lastUsedAt: '2026-10-19T08:00:00Z',
    revokedAt: null,
    lastClientVersion: null,
    ...over,
  };
}

describe('TarbilExtensionCard paired device version', () => {
  it('shows current, stale and unknown versions', async () => {
    vi.mocked(tarbilApi.extensionTokens).mockResolvedValueOnce([
      token({ lastClientVersion: '0.3.0' }),
      token({ lastClientVersion: '0.2.0' }),
      token({ lastClientVersion: null }),
    ]);
    const { text } = await render('0.3.0', '{"version":"0.3.0"}');
    expect(text).toContain('Sürüm 0.3.0');
    expect(text).not.toContain('Sürüm 0.3.0 (eski)');
    expect(text).toContain('Sürüm 0.2.0 (eski)');
    expect(text).toContain('Sürüm bilinmiyor (0.2.0 öncesi)');
  });

  it('does not judge staleness without a release file', async () => {
    vi.mocked(tarbilApi.extensionTokens).mockResolvedValueOnce([token({ lastClientVersion: '0.2.0' })]);
    const { text } = await render(null, null);
    expect(text).toContain('Sürüm 0.2.0');
    expect(text).not.toContain('(eski)');
  });

  it('omits version for unused or revoked devices', async () => {
    vi.mocked(tarbilApi.extensionTokens).mockResolvedValueOnce([
      token({ lastUsedAt: null }),
      token({ revokedAt: '2026-10-18T08:00:00Z', lastClientVersion: '0.1.0' }),
    ]);
    const { text } = await render(null, '{"version":"0.2.0"}');
    expect(text).not.toContain('Sürüm 0.1.0');
    expect(text).not.toContain('Sürüm bilinmiyor');
  });
});
