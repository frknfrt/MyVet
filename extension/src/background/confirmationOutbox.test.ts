import { describe, expect, it } from 'vitest';
import { memoryStore } from './chromeStorage';
import { createConfirmationOutbox } from './confirmationOutbox';
import { ApiError } from './vetlyApi';

function apiThat(outcomes: Array<'ok' | ApiError['code']>) {
  const calls: string[] = [];
  return {
    calls,
    markSubmitted: async (id: string) => {
      calls.push(id);
      const next = outcomes.shift() ?? 'ok';
      if (next !== 'ok') throw new ApiError(next, next);
      return {} as never;
    },
  };
}

describe('confirmationOutbox', () => {
  it('should_keepEntry_when_flushFails', async () => {
    const api = apiThat(['OFFLINE']);
    const outbox = createConfirmationOutbox(memoryStore(), api);
    await outbox.enqueue({ id: 's1', method: 'AUTO', tarbilReference: null });

    expect(await outbox.flush()).toBe(0);
    expect(await outbox.size()).toBe(1);
  });

  it('removes sent, not-found and conflict entries', async () => {
    const api = apiThat(['ok', 'NOT_FOUND', 'CONFLICT']);
    const outbox = createConfirmationOutbox(memoryStore(), api);
    await outbox.enqueue({ id: 'a', method: 'AUTO', tarbilReference: null });
    await outbox.enqueue({ id: 'b', method: 'AUTO', tarbilReference: null });
    await outbox.enqueue({ id: 'c', method: 'AUTO', tarbilReference: null });

    expect(await outbox.flush()).toBe(1);
    expect(await outbox.size()).toBe(0);
  });

  it('does not duplicate the same submission', async () => {
    const outbox = createConfirmationOutbox(memoryStore(), apiThat([]));
    await outbox.enqueue({ id: 'a', method: 'AUTO', tarbilReference: null });
    await outbox.enqueue({ id: 'a', method: 'MANUAL', tarbilReference: null });

    expect(await outbox.size()).toBe(1);
  });

  it('stops on UNAUTHORIZED and keeps remaining', async () => {
    const api = apiThat(['UNAUTHORIZED']);
    const outbox = createConfirmationOutbox(memoryStore(), api);
    await outbox.enqueue({ id: 'a', method: 'AUTO', tarbilReference: null });
    await outbox.enqueue({ id: 'b', method: 'AUTO', tarbilReference: null });

    await outbox.flush();

    expect(api.calls).toEqual(['a']);
    expect(await outbox.size()).toBe(2);
  });
});
