import { describe, expect, it } from 'vitest';
import { extensionVersionStatus, fetchLatestExtensionVersion } from './extensionRelease';

const respond = (body: string, status = 200) => (async () => new Response(body, { status })) as unknown as typeof fetch;

describe('fetchLatestExtensionVersion', () => {
  it('reads the version from the release json', async () => {
    expect(await fetchLatestExtensionVersion(respond('{"version":"0.2.0"}'))).toBe('0.2.0');
  });

  it('returns null when the file is missing, is HTML (SPA fallback) or has no version', async () => {
    expect(await fetchLatestExtensionVersion(respond('not found', 404))).toBeNull();
    expect(await fetchLatestExtensionVersion(respond('<!doctype html><html></html>'))).toBeNull();
    expect(await fetchLatestExtensionVersion(respond('{"foo":1}'))).toBeNull();
    const failing = (async () => { throw new TypeError('offline'); }) as unknown as typeof fetch;
    expect(await fetchLatestExtensionVersion(failing)).toBeNull();
  });
});

describe('extensionVersionStatus', () => {
  it('covers every state', () => {
    expect(extensionVersionStatus(null, '0.2.0')).toEqual({ kind: 'not-installed' });
    expect(extensionVersionStatus('0.2.0', '0.2.0')).toEqual({ kind: 'up-to-date', installed: '0.2.0' });
    expect(extensionVersionStatus('0.3.0', '0.2.0')).toEqual({ kind: 'up-to-date', installed: '0.3.0' });
    expect(extensionVersionStatus('0.1.0', '0.2.0')).toEqual({ kind: 'outdated', installed: '0.1.0', latest: '0.2.0' });
    expect(extensionVersionStatus('0.9.0', '0.10.0')).toEqual({ kind: 'outdated', installed: '0.9.0', latest: '0.10.0' });
    expect(extensionVersionStatus('0.2.0', null)).toEqual({ kind: 'installed', installed: '0.2.0' });
  });
});
