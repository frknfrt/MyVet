import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

type Reply = { ok: true; data: unknown } | { ok: false; error: string; code: string } | undefined;

async function load(reply: Reply) {
  vi.stubEnv('VITE_TARBIL_EXTENSION_ID', 'x');
  vi.resetModules();
  (globalThis as { chrome?: unknown }).chrome = {
    runtime: { sendMessage: (_id: string, _msg: unknown, cb: (r: Reply) => void) => cb(reply) },
  };
  return import('./tarbilExtension');
}

beforeEach(() => vi.resetModules());
afterEach(() => {
  vi.unstubAllEnvs();
  delete (globalThis as { chrome?: unknown }).chrome;
});

describe('tarbilExtension', () => {
  it('maps UPGRADE_REQUIRED reply to its own reason', async () => {
    const m = await load({ ok: false, error: 'x', code: 'UPGRADE_REQUIRED' });
    expect(await m.selectForTarbil('v1')).toEqual({ ok: false, reason: 'UPGRADE_REQUIRED' });
  });

  it('returns installed version from PING', async () => {
    const m = await load({ ok: true, data: { version: '0.2.0' } });
    expect(await m.getInstalledExtensionVersion()).toBe('0.2.0');
  });

  it('returns null version when there is no reply', async () => {
    const m = await load(undefined);
    expect(await m.getInstalledExtensionVersion()).toBeNull();
  });
});
