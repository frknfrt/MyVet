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

  it('should_reportUnpaired_when_meIsUnauthorized', async () => {
    // Anahtar iptal edildi / personel pasif: panel "Yükleniyor…"da takilmamali, eslestirmeye donmeli.
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_revoked');
    const api = {
      me: async () => {
        await tokens.clear();
        throw new ApiError('UNAUTHORIZED', 'iptal');
      },
    } as never;
    const router = createRouter({ api, tokens, outbox: createConfirmationOutbox(memoryStore(), api), session: memoryStore() });

    const res = await router.handle({ type: 'GET_STATE' });

    expect(res).toEqual({ ok: true, data: { paired: false, profile: null, pendingConfirmations: 0 } });
  });

  it('keeps paired state without profile when me fails unexpectedly', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const api = {
      me: async () => {
        throw new ApiError('UNKNOWN', '500');
      },
    } as never;
    const router = createRouter({ api, tokens, outbox: createConfirmationOutbox(memoryStore(), api), session: memoryStore() });

    const res = await router.handle({ type: 'GET_STATE' });

    expect(res).toEqual({ ok: true, data: { paired: true, profile: null, pendingConfirmations: 0 } });
  });

  it('arms the TARBIL flow when Vetly selects a submission', async () => {
    const { router, session } = setup(async () => ({}));

    await router.handleExternal({ type: 'SELECT_SUBMISSION', vaccinationRecordId: 'v1' });

    expect(await session.get('tarbilFlow')).toMatchObject({ submissionId: 'sub-for-v1', step: 'armed' });
  });

  it('arms the TARBIL flow when the side panel sets the active submission', async () => {
    const { router, session } = setup(async () => ({}));

    await router.handle({ type: 'SET_ACTIVE', id: 's9' });

    expect(await session.get('tarbilFlow')).toMatchObject({ submissionId: 's9', step: 'armed' });
    expect(await session.get('activeSubmissionId')).toBe('s9');
  });

  it('answers ping', async () => {
    const { router } = setup(async () => ({}));
    expect(await router.handleExternal({ type: 'PING' })).toEqual({ ok: true, data: { version: '0.1.0' } });
  });
});
