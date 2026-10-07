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

  it('opens the TARBIL vaccine page when the side panel starts a transfer', async () => {
    const tokens = createTokenStore(memoryStore());
    const api = {} as never;
    let opened = 0;
    const router = createRouter({
      api, tokens, outbox: createConfirmationOutbox(memoryStore(), api), session: memoryStore(),
      openVaccinePage: async () => { opened++; },
    });

    const res = await router.handle({ type: 'SET_ACTIVE', id: 's9' });

    expect(res.ok).toBe(true);
    expect(opened).toBe(1);
  });

  it('answers ping', async () => {
    const { router } = setup(async () => ({}));
    expect(await router.handleExternal({ type: 'PING' })).toEqual({ ok: true, data: { version: '0.1.0' } });
  });

  it('uploads a TARBIL stock snapshot through the API', async () => {
    const tokens = createTokenStore(memoryStore());
    const uploaded: unknown[] = [];
    const api = { uploadStockSnapshot: async (system: string, lines: unknown[]) => { uploaded.push({ system, lines }); return { snapshotId: 's1' }; } } as never;
    const router = createRouter({ api, tokens, outbox: createConfirmationOutbox(memoryStore(), api), session: memoryStore() });

    const res = await router.handle({ type: 'UPLOAD_STOCK_SNAPSHOT', system: 'HBSAPP_VACCINE', lines: [] });

    expect(res).toEqual({ ok: true, data: { snapshotId: 's1' } });
    expect(uploaded).toEqual([{ system: 'HBSAPP_VACCINE', lines: [] }]);
  });

  it('compares a TARBIL stock page with Vetly through the API', async () => {
    const tokens = createTokenStore(memoryStore());
    const compared: unknown[] = [];
    const api = { compareStockSnapshot: async (system: string, lines: unknown[]) => { compared.push({ system, lines }); return { newCount: 2, quantityDiffersCount: 0, matchedCount: 5 }; } } as never;
    const router = createRouter({ api, tokens, outbox: createConfirmationOutbox(memoryStore(), api), session: memoryStore() });

    const res = await router.handle({ type: 'COMPARE_STOCK_SNAPSHOT', system: 'VETILAC_MEDICINE', lines: [] });

    expect(res).toEqual({ ok: true, data: { newCount: 2, quantityDiffersCount: 0, matchedCount: 5 } });
    expect(compared).toEqual([{ system: 'VETILAC_MEDICINE', lines: [] }]);
  });
});
