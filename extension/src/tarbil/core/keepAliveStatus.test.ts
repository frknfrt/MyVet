import { describe, expect, it } from 'vitest';
import { memoryStore } from '../../background/chromeStorage';
import { classifyPing, pingAll, readKeepAliveStatus, recordPing, statusText, summarizeOrigin } from './keepAliveStatus';

const HBS = 'https://hbsapp.tarbil.gov.tr';
const VET = 'https://vetilac.tarbil.gov.tr';
const MIN = 60_000;

describe('classifyPing', () => {
  it('treats a redirect as a closed TARBIL session', () => {
    expect(classifyPing({ type: 'opaqueredirect', status: 0 })).toBe('loggedOut');
    expect(classifyPing({ type: 'basic', status: 302 })).toBe('loggedOut');
  });

  it('treats 200 as alive and anything else as an error', () => {
    expect(classifyPing({ type: 'basic', status: 200 })).toBe('ok');
    expect(classifyPing({ type: 'basic', status: 500 })).toBe('error');
    expect(classifyPing(new Error('ag'))).toBe('error');
  });
});

describe('recordPing', () => {
  it('keeps the last success and marks when the session closed', async () => {
    const store = memoryStore();
    await recordPing(store, HBS, 'tab', 'ok', 1000);
    await recordPing(store, HBS, 'tab', 'loggedOut', 5000);
    await recordPing(store, HBS, 'tab', 'loggedOut', 9000);

    const s = (await readKeepAliveStatus(store))[HBS].tab!;
    expect(s).toMatchObject({ lastResult: 'loggedOut', lastOkAt: 1000, loggedOutSince: 5000, lastCheckedAt: 9000 });
  });

  it('clears the closed mark after a new login', async () => {
    const store = memoryStore();
    await recordPing(store, HBS, 'background', 'loggedOut', 1000);
    await recordPing(store, HBS, 'background', 'ok', 2000);

    expect((await readKeepAliveStatus(store))[HBS].background).toMatchObject({ lastResult: 'ok', lastOkAt: 2000, loggedOutSince: undefined });
  });
});

describe('summarizeOrigin', () => {
  it('is open when any source succeeded recently', () => {
    expect(summarizeOrigin({ tab: { lastResult: 'ok', lastOkAt: 100 * MIN, lastCheckedAt: 100 * MIN }, background: { lastResult: 'loggedOut', lastCheckedAt: 100 * MIN, loggedOutSince: 90 * MIN } }, 105 * MIN))
      .toMatchObject({ state: 'open', lastOkAt: 100 * MIN });
  });

  it('is closed when every source sees a redirect after an earlier success', () => {
    expect(summarizeOrigin({ background: { lastResult: 'loggedOut', lastOkAt: 50 * MIN, lastCheckedAt: 100 * MIN, loggedOutSince: 52 * MIN } }, 105 * MIN))
      .toMatchObject({ state: 'closed', lastOkAt: 50 * MIN, closedAt: 52 * MIN });
  });

  it('is unknown when nothing was checked for a long time (pings stopped)', () => {
    expect(summarizeOrigin({ tab: { lastResult: 'ok', lastOkAt: 10 * MIN, lastCheckedAt: 10 * MIN } }, 200 * MIN)).toMatchObject({ state: 'stale' });
  });

  it('is never when the vet has not logged in to that site', () => {
    expect(summarizeOrigin({ background: { lastResult: 'loggedOut', lastCheckedAt: 100 * MIN, loggedOutSince: 100 * MIN } }, 101 * MIN)).toMatchObject({ state: 'never' });
  });
});

describe('pingAll', () => {
  it('pings both TARBIL sites, records results and flags a closed session on the icon', async () => {
    const store = memoryStore();
    await recordPing(store, HBS, 'background', 'ok', 0);
    const badges: string[] = [];
    const fetched: string[] = [];

    await pingAll({
      store,
      now: () => 10 * MIN,
      fetch: async (url) => {
        fetched.push(url);
        return url.startsWith(HBS) ? { type: 'opaqueredirect', status: 0 } : { type: 'basic', status: 200 };
      },
      setBadge: (text) => badges.push(text),
    });

    expect(fetched).toEqual([`${HBS}/Default.aspx`, `${VET}/Pages/PharmacyDefault.aspx`]);
    const status = await readKeepAliveStatus(store);
    expect(status[HBS].background?.lastResult).toBe('loggedOut');
    expect(status[VET].background?.lastResult).toBe('ok');
    expect(badges.at(-1)).toBe('!');
  });

  it('does nothing when the vet turned keep-alive off', async () => {
    const store = memoryStore();
    await store.set('tarbilKeepAlive', false);
    const fetched: string[] = [];

    await pingAll({ store, now: () => 0, fetch: async (u) => { fetched.push(u); return { type: 'basic', status: 200 }; }, setBadge: () => undefined });

    expect(fetched).toEqual([]);
  });
});

describe('statusText', () => {
  const t = (ms: number) => `${Math.floor(ms / MIN)}dk`;

  it('explains each state in Turkish', () => {
    expect(statusText({ state: 'open', lastOkAt: 5 * MIN }, t)).toBe('Açık (son kontrol 5dk)');
    expect(statusText({ state: 'closed', lastOkAt: 5 * MIN, closedAt: 9 * MIN }, t)).toBe('Kapandı 9dk (son başarılı 5dk) — e-Devlet ile yeniden giriş yapın');
    expect(statusText({ state: 'stale', lastOkAt: 5 * MIN, lastCheckedAt: 5 * MIN }, t)).toBe('Kontrol durdu (son kontrol 5dk) — Chrome eklentiyi uyutmuş olabilir');
    expect(statusText({ state: 'never' }, t)).toBe('Giriş yapılmamış');
  });
});

