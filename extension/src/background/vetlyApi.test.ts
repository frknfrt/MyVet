import { describe, expect, it } from 'vitest';
import { createTokenStore, memoryStore } from './chromeStorage';
import { EXTENSION_VERSION } from '../shared/version';
import { ApiError, createVetlyApi } from './vetlyApi';

function fakeFetch(status: number, body: unknown = {}) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fn = async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return new Response(status === 204 ? null : JSON.stringify(body), { status });
  };
  return { fn: fn as unknown as typeof fetch, calls };
}

describe('vetlyApi', () => {
  it('sends bearer token and parses json', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const { fn, calls } = fakeFetch(200, []);
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fn });

    expect(await api.listPending()).toEqual([]);
    expect(calls[0].url).toBe('https://x/api/v1/tarbil-extension/pending');
    expect((calls[0].init.headers as Record<string, string>).Authorization).toBe('Bearer vtx_abc');
  });

  it('should_clearTokenAndThrow_when_401', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_revoked');
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fakeFetch(401).fn });

    await expect(api.listPending()).rejects.toMatchObject({ code: 'UNAUTHORIZED' });
    expect(await tokens.get()).toBeNull();
  });

  it('maps network failure to OFFLINE', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const failing = (async () => {
      throw new TypeError('Failed to fetch');
    }) as unknown as typeof fetch;
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: failing });

    await expect(api.listPending()).rejects.toBeInstanceOf(ApiError);
    await expect(api.listPending()).rejects.toMatchObject({ code: 'OFFLINE' });
  });

  it('stores token on pair without sending authorization', async () => {
    const tokens = createTokenStore(memoryStore());
    const { fn, calls } = fakeFetch(200, { token: 'vtx_new' });
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fn });

    await api.pair('K7QM-2XPA', 'PC');

    expect(await tokens.get()).toBe('vtx_new');
    expect((calls[0].init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it('sends confirmed prescription lines with the submitted call', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const { fn, calls } = fakeFetch(200, {});
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fn });

    await api.markSubmitted('s1', 'AUTO', null, [{ productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 }]);
    await api.markSubmitted('s2', 'MANUAL', null);

    expect(JSON.parse(calls[0].init.body as string)).toEqual({
      method: 'AUTO', tarbilReference: null, lines: [{ productName: 'SYNULOX', serialNumber: 'S1', quantity: 2 }],
    });
    expect(JSON.parse(calls[1].init.body as string)).toEqual({ method: 'MANUAL', tarbilReference: null });
  });

  it('sends X-Client-Version on every request, including pair', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const { fn, calls } = fakeFetch(200, []);
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fn });

    await api.listPending();
    const pairFetch = fakeFetch(200, { token: 'vtx_new' });
    await createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: pairFetch.fn }).pair('K7QM-2XPA', 'PC');

    expect((calls[0].init.headers as Record<string, string>)['X-Client-Version']).toBe(EXTENSION_VERSION);
    expect((pairFetch.calls[0].init.headers as Record<string, string>)['X-Client-Version']).toBe(EXTENSION_VERSION);
  });

  it('should_mapUpgradeRequired_andKeepToken_when_426', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const api = createVetlyApi({
      baseUrl: 'https://x', tokens,
      fetchFn: fakeFetch(426, { errorCode: 'CLIENT_UPGRADE_REQUIRED', message: 'Sunucudan gelen mesaj' }).fn,
    });

    await expect(api.listPending()).rejects.toMatchObject({ code: 'UPGRADE_REQUIRED', message: 'Sunucudan gelen mesaj' });
    expect(await tokens.get()).toBe('vtx_abc');
  });

  it('should_useFixedMessage_when_426BodyIsNotJson', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const htmlFetch = (async () => new Response('<html>426</html>', { status: 426 })) as unknown as typeof fetch;
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: htmlFetch });

    await expect(api.listPending()).rejects.toMatchObject({
      code: 'UPGRADE_REQUIRED',
      message: "Vetly TARBİL eklentinizin sürümü artık desteklenmiyor. Vetly'de Ayarlar > Entegrasyonlar bölümünden yeni sürümü indirin.",
    });
  });
});
