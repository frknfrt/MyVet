import { describe, expect, it } from 'vitest';
import { createTokenStore, memoryStore } from './chromeStorage';
import { createConfirmationOutbox } from './confirmationOutbox';
import { createRouter } from './router';
import { ApiError } from './vetlyApi';

function setup(markSubmitted: () => Promise<unknown>) {
  const tokens = createTokenStore(memoryStore());
  const session = memoryStore();
  const api = {
    markSubmitted,
    getByVaccination: async (vid: string) => ({ id: 'sub-for-' + vid }),
  } as never;
  const outbox = createConfirmationOutbox(memoryStore(), api);
  return { router: createRouter({ api, tokens, outbox, session }), outbox, session };
}

describe('router', () => {
  it('queues confirmation when offline and reports queued', async () => {
    const { router, outbox } = setup(async () => {
      throw new ApiError('OFFLINE', 'offline');
    });

    const res = await router.handle({ type: 'MARK_SUBMITTED', id: 's1', method: 'MANUAL', tarbilReference: null });

    expect(res).toEqual({ ok: true, data: { queued: true } });
    expect(await outbox.size()).toBe(1);
  });

  it('selects active submission from external request by vaccination id', async () => {
    const { router, session } = setup(async () => ({}));

    const res = await router.handleExternal({ type: 'SELECT_SUBMISSION', vaccinationRecordId: 'v1' });

    expect(res.ok).toBe(true);
    expect(await session.get('activeSubmissionId')).toBe('sub-for-v1');
  });

  it('answers ping', async () => {
    const { router } = setup(async () => ({}));
    expect(await router.handleExternal({ type: 'PING' })).toEqual({ ok: true, data: { version: '0.1.0' } });
  });
});
