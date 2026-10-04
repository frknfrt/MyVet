import { describe, expect, it } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import { KEEP_ALIVE_INTERVAL_MS, isKeepAliveEnabled, keepAliveUrl, setKeepAliveEnabled, startKeepAlive } from './keepAlive';

function fakeTimer() {
  let tick: (() => void) | null = null;
  let interval = 0;
  let cleared = false;
  return {
    setInterval: (fn: () => void, ms: number) => { tick = fn; interval = ms; return 1; },
    clearInterval: () => { cleared = true; },
    tick: async () => { tick?.(); await new Promise((r) => setTimeout(r, 0)); },
    get interval() { return interval; },
    get cleared() { return cleared; },
  };
}

describe('keepAliveUrl', () => {
  it('pings a plain page of the same TARBIL site', () => {
    expect(keepAliveUrl('https://hbsapp.tarbil.gov.tr')).toBe('https://hbsapp.tarbil.gov.tr/Default.aspx');
    expect(keepAliveUrl('https://vetilac.tarbil.gov.tr')).toBe('https://vetilac.tarbil.gov.tr/Pages/PharmacyDefault.aspx');
  });

  it('does nothing on other sites', () => {
    expect(keepAliveUrl('https://hbs.tarbil.gov.tr')).toBeNull();
    expect(keepAliveUrl('https://example.com')).toBeNull();
  });
});

describe('startKeepAlive', () => {
  it('pings the site on every tick while enabled', async () => {
    const timer = fakeTimer();
    const pinged: string[] = [];
    startKeepAlive({ origin: 'https://hbsapp.tarbil.gov.tr', enabled: async () => true, ping: async (u) => { pinged.push(u); }, ...timer });

    await timer.tick();
    await timer.tick();

    expect(timer.interval).toBe(KEEP_ALIVE_INTERVAL_MS);
    expect(pinged).toEqual(['https://hbsapp.tarbil.gov.tr/Default.aspx', 'https://hbsapp.tarbil.gov.tr/Default.aspx']);
  });

  it('does not ping when the vet turned it off', async () => {
    const timer = fakeTimer();
    const pinged: string[] = [];
    startKeepAlive({ origin: 'https://hbsapp.tarbil.gov.tr', enabled: async () => false, ping: async (u) => { pinged.push(u); }, ...timer });

    await timer.tick();

    expect(pinged).toEqual([]);
  });

  it('survives a failed ping and can be stopped', async () => {
    const timer = fakeTimer();
    let calls = 0;
    const stop = startKeepAlive({ origin: 'https://vetilac.tarbil.gov.tr', enabled: async () => true, ping: async () => { calls++; throw new Error('ag'); }, ...timer });

    await timer.tick();
    await timer.tick();
    stop();

    expect(calls).toBe(2);
    expect(timer.cleared).toBe(true);
  });

  it('starts nothing on a site without a keep-alive page', () => {
    const timer = fakeTimer();
    startKeepAlive({ origin: 'https://example.com', enabled: async () => true, ping: async () => undefined, ...timer });
    expect(timer.interval).toBe(0);
  });
});

describe('keep-alive setting', () => {
  it('is on by default and can be switched off', async () => {
    const store = memoryStore();
    expect(await isKeepAliveEnabled(store)).toBe(true);
    await setKeepAliveEnabled(store, false);
    expect(await isKeepAliveEnabled(store)).toBe(false);
  });
});
