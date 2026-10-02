import { describe, expect, it } from 'vitest';
import { createTokenStore, memoryStore } from './chromeStorage';
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
});
