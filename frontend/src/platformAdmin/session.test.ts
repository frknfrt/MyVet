// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadStoredPlatformAdminSession, storePlatformAdminSession } from './session';

const base = { platformAdminId: 'p', email: 'admin@vetly.test', fullName: 'Admin' };

// Node 25'in yerlesik localStorage'i jsdom'unkini golgeliyor; bellek ici bir depo kullan.
beforeEach(() => {
  const data = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => void data.set(k, v),
    removeItem: (k: string) => void data.delete(k),
    clear: () => data.clear(),
  });
});
afterEach(() => vi.unstubAllGlobals());

describe('platform admin session storage', () => {
  it('never writes the token to localStorage', () => {
    storePlatformAdminSession({ ...base, token: 'secret.jwt' });

    expect(localStorage.getItem('myvet.platformAdminSession')).not.toContain('secret.jwt');
    expect(loadStoredPlatformAdminSession()).toEqual(base);
  });

  it('strips a token left by an older version when loading', () => {
    localStorage.setItem('myvet.platformAdminSession', JSON.stringify({ ...base, token: 'old.jwt' }));

    expect(loadStoredPlatformAdminSession()).toEqual(base);
    expect(localStorage.getItem('myvet.platformAdminSession')).not.toContain('old.jwt');
  });
});
