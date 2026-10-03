import type { KeyValueStore } from '../background/chromeStorage';

/**
 * TARBIL doldurma akisinin durumu (chrome.storage.session, tarayici kapaninca silinir).
 * Ana asi sayfasi ile ayri pencerede acilan PetVet arama penceresi bu kayit uzerinden haberlesir.
 */
export const FLOW_KEY = 'tarbilFlow';

export type FlowStep = 'armed' | 'filling' | 'searching' | 'transferred' | 'needsVet' | 'awaitingConfirm' | 'done' | 'error';

export interface FlowState {
  submissionId: string;
  step: FlowStep;
  updatedAt: number;
  message?: string;
  insertClickedAt?: number;
  redirectedAt?: number;
}

export interface FlowStore {
  get(): Promise<FlowState | null>;
  arm(submissionId: string): Promise<void>;
  update(submissionId: string, patch: Partial<Omit<FlowState, 'submissionId' | 'updatedAt'>>): Promise<FlowState | null>;
}

export function createFlowStore(store: KeyValueStore, now: () => number = Date.now): FlowStore {
  async function get(): Promise<FlowState | null> {
    return (await store.get<FlowState>(FLOW_KEY)) ?? null;
  }
  return {
    get,
    arm: (submissionId) => store.set(FLOW_KEY, { submissionId, step: 'armed', updatedAt: now() } satisfies FlowState),
    async update(submissionId, patch) {
      const current = await get();
      if (!current || current.submissionId !== submissionId) return null;
      // updatedAt adim zamanidir: yalniz adim degisince yenilenir (yonlendirme isareti bayat akisi tazelemesin).
      const next: FlowState = { ...current, ...patch, updatedAt: patch.step ? now() : current.updatedAt };
      await store.set(FLOW_KEY, next);
      return next;
    },
  };
}
