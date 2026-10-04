import type { KeyValueStore } from '../background/chromeStorage';
import type { DocumentType } from './types';

/**
 * TARBIL doldurma akisinin durumu (chrome.storage.session, tarayici kapaninca silinir).
 * Ana sayfa ile ayri pencerede acilan TARBIL pencereleri (hayvan arama, stok) bu kayit uzerinden haberlesir.
 * stepData: belge turune ozel ara durum (ornegin recetede { itemIndex, awaiting }).
 */
export const FLOW_KEY = 'tarbilFlow';

export type FlowStep =
  | 'armed' | 'filling' | 'searching' | 'transferred' | 'needsVet' | 'awaitingConfirm'
  | 'choosingProduct' | 'productReady' | 'done' | 'error';

export interface FlowState {
  submissionId: string;
  documentType: DocumentType;
  step: FlowStep;
  updatedAt: number;
  message?: string;
  insertClickedAt?: number;
  redirectedAt?: number;
  stepData?: Record<string, unknown>;
}

export interface FlowStore {
  get(): Promise<FlowState | null>;
  arm(submissionId: string, documentType?: DocumentType): Promise<void>;
  update(submissionId: string, patch: Partial<Omit<FlowState, 'submissionId' | 'updatedAt'>>): Promise<FlowState | null>;
}

export function createFlowStore(store: KeyValueStore, now: () => number = Date.now): FlowStore {
  async function get(): Promise<FlowState | null> {
    const raw = await store.get<Partial<FlowState>>(FLOW_KEY);
    // Eklenti guncellemesinden once yazilmis kayitlarda belge turu yok: o zaman yalniz asi vardi.
    return raw ? ({ documentType: 'VACCINATION', ...raw } as FlowState) : null;
  }
  return {
    get,
    arm: (submissionId, documentType = 'VACCINATION') =>
      store.set(FLOW_KEY, { submissionId, documentType, step: 'armed', updatedAt: now() } satisfies FlowState),
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
