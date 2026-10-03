import type { BackgroundRequest, BackgroundResponse, ExtensionState, ExternalRequest } from '../shared/messages';
import type { KeyValueStore, TokenStore } from './chromeStorage';
import type { ConfirmationOutbox } from './confirmationOutbox';
import { createFlowStore } from '../shared/flowStore';
import { ApiError, type VetlyApi } from './vetlyApi';

const ACTIVE_KEY = 'activeSubmissionId';
const VERSION = '0.1.0';

interface Deps {
  api: VetlyApi;
  tokens: TokenStore;
  outbox: ConfirmationOutbox;
  session: KeyValueStore;
}

function fail(e: unknown): BackgroundResponse<never> {
  if (e instanceof ApiError) return { ok: false, error: e.message, code: e.code };
  return { ok: false, error: 'Beklenmeyen hata', code: 'UNKNOWN' };
}

export function createRouter({ api, tokens, outbox, session }: Deps) {
  const flow = createFlowStore(session);
  async function state(): Promise<ExtensionState> {
    const paired = (await tokens.get()) !== null;
    let profile = null;
    if (paired) {
      try {
        profile = await api.me();
      } catch {
        // 401'de api anahtari zaten sildi -> asagida paired:false; cevrimdisi/5xx'te bagli kalinir, profil bos.
      }
    }
    return { paired: (await tokens.get()) !== null, profile, pendingConfirmations: await outbox.size() };
  }

  return {
    async handle(req: BackgroundRequest): Promise<BackgroundResponse<unknown>> {
      try {
        switch (req.type) {
          case 'GET_STATE':
            return { ok: true, data: await state() };
          case 'PAIR':
            await api.pair(req.code.trim(), req.label.trim());
            return { ok: true, data: await state() };
          case 'UNPAIR':
            await tokens.clear();
            return { ok: true, data: await state() };
          case 'LIST_PENDING':
            await outbox.flush().catch(() => 0);
            return { ok: true, data: await api.listPending() };
          case 'GET_SUBMISSION':
            return { ok: true, data: await api.getSubmission(req.id) };
          case 'SET_ACTIVE':
            await flow.arm(req.id);
            await session.set(ACTIVE_KEY, req.id);
            return { ok: true, data: null };
          case 'GET_ACTIVE': {
            const id = await session.get<string>(ACTIVE_KEY);
            return { ok: true, data: id ? await api.getSubmission(id) : null };
          }
          case 'MARK_SUBMITTED':
            try {
              return { ok: true, data: await api.markSubmitted(req.id, req.method, req.tarbilReference) };
            } catch (e) {
              if (e instanceof ApiError && (e.code === 'OFFLINE' || e.code === 'UNKNOWN')) {
                await outbox.enqueue({ id: req.id, method: req.method, tarbilReference: req.tarbilReference });
                return { ok: true, data: { queued: true } };
              }
              throw e;
            }
          case 'DISMISS':
            await api.dismiss(req.id, req.reason);
            return { ok: true, data: null };
        }
      } catch (e) {
        return fail(e);
      }
    },

    async handleExternal(req: ExternalRequest): Promise<BackgroundResponse<unknown>> {
      try {
        switch (req.type) {
          case 'PING':
            return { ok: true, data: { version: VERSION } };
          case 'SELECT_SUBMISSION': {
            const submission = await api.getByVaccination(req.vaccinationRecordId);
            await flow.arm(submission.id);
            await session.set(ACTIVE_KEY, submission.id);
            return { ok: true, data: { submissionId: submission.id } };
          }
        }
      } catch (e) {
        return fail(e);
      }
    },
  };
}
