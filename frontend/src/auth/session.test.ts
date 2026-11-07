// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadStoredSession, storeSession } from './session';

const base = { staffUserId: 's', tenantId: 't', branchId: 'b', fullName: 'Dr. Test', role: 'VET' as const };

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

describe('session storage', () => {
  it('never writes the token to localStorage', () => {
    storeSession({ ...base, token: 'secret.jwt' });

    expect(localStorage.getItem('myvet.session')).not.toContain('secret.jwt');
    expect(loadStoredSession()).toEqual(base);
  });

  it('strips a token left by an older version when loading', () => {
    localStorage.setItem('myvet.session', JSON.stringify({ ...base, token: 'old.jwt' }));

    expect(loadStoredSession()).toEqual(base);
    expect(localStorage.getItem('myvet.session')).not.toContain('old.jwt');
  });
});
