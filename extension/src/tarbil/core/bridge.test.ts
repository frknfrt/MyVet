// @vitest-environment jsdom
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { createPageBridge, installPageHandler } from './bridge';

// jsdom postMessage event.source'u bos birakir; Chrome'da pencerenin kendine gonderdigi mesajda source === window.
beforeAll(() => {
  window.postMessage = ((data: unknown) => {
    setTimeout(() => window.dispatchEvent(new MessageEvent('message', { data, source: window })), 0);
  }) as typeof window.postMessage;
});

const uninstallers: (() => void)[] = [];
afterEach(() => uninstallers.splice(0).forEach((u) => u()));

describe('page bridge', () => {
  it('round-trips a command to the page handler', async () => {
    uninstallers.push(installPageHandler(window, { echo: async (a: unknown) => ({ got: a }) }));
    const bridge = createPageBridge(window);

    await expect(bridge.call('echo', { x: 1 })).resolves.toEqual({ got: { x: 1 } });
  });

  it('rejects with the code thrown by the page handler', async () => {
    uninstallers.push(
      installPageHandler(window, {
        boom: () => {
          throw Object.assign(new Error('yok'), { code: 'NOT_FOUND' });
        },
      }),
    );
    const bridge = createPageBridge(window);

    await expect(bridge.call('boom')).rejects.toMatchObject({ code: 'NOT_FOUND', message: 'yok' });
  });

  it('reports unknown operations', async () => {
    uninstallers.push(installPageHandler(window, {}));
    const bridge = createPageBridge(window);

    await expect(bridge.call('nope')).rejects.toMatchObject({ code: 'UNKNOWN_OP' });
  });

  it('times out when no page handler answers', async () => {
    const bridge = createPageBridge(window);

    await expect(bridge.call('nothing', undefined, 50)).rejects.toMatchObject({ code: 'BRIDGE_TIMEOUT' });
  });
});

describe('page bridge sender check', () => {
  it('ignores commands that do not come from the same window', async () => {
    let ran = false;
    uninstallers.push(installPageHandler(window, { hit: () => { ran = true; } }));

    window.dispatchEvent(new MessageEvent('message', { data: { __vetly: 'cmd', id: 'x1', op: 'hit' }, source: null }));
    await new Promise((r) => setTimeout(r, 10));

    expect(ran).toBe(false);
  });
});
