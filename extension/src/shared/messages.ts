import type { ConfirmationMethod, ExtensionProfile, StockSnapshotLine, StockSystem, Submission } from './types';

export type BackgroundRequest =
  | { type: 'GET_STATE' }
  | { type: 'PAIR'; code: string; label: string }
  | { type: 'UNPAIR' }
  | { type: 'LIST_PENDING' }
  | { type: 'GET_SUBMISSION'; id: string }
  | { type: 'SET_ACTIVE'; id: string }
  | { type: 'GET_ACTIVE' }
  | { type: 'MARK_SUBMITTED'; id: string; method: ConfirmationMethod; tarbilReference: string | null }
  | { type: 'UPLOAD_STOCK_SNAPSHOT'; system: StockSystem; lines: StockSnapshotLine[] }
  | { type: 'COMPARE_STOCK_SNAPSHOT'; system: StockSystem; lines: StockSnapshotLine[] }
  | { type: 'DISMISS'; id: string; reason: string };

export type ErrorCode = 'UNAUTHORIZED' | 'OFFLINE' | 'NOT_FOUND' | 'CONFLICT' | 'UNKNOWN';

export type BackgroundResponse<T> = { ok: true; data: T } | { ok: false; error: string; code: ErrorCode };

export type ExternalRequest = { type: 'PING' } | { type: 'SELECT_SUBMISSION'; vaccinationRecordId: string };

export interface ExtensionState {
  paired: boolean;
  profile: ExtensionProfile | null;
  pendingConfirmations: number;
}

export type { Submission };
