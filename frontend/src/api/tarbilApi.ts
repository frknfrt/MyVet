import { apiClient } from './client';

export type TarbilSyncStatus = 'PENDING' | 'SUBMITTED' | 'DISMISSED';
export type TarbilConfirmationMethod = 'AUTO' | 'MANUAL';
export type TarbilMappingKind = 'VACCINE' | 'SPECIES';

export interface TarbilStatus {
  pendingCount: number;
  submittedCount: number;
  dismissedCount: number;
  lastSubmittedAt: string | null;
}

export interface TarbilSyncLog {
  id: string;
  patientId: string;
  patientName: string;
  vaccineName: string;
  administeredDate: string | null;
  status: TarbilSyncStatus;
  queuedAt: string;
  submittedAt: string | null;
  confirmationMethod: TarbilConfirmationMethod | null;
  tarbilReference: string | null;
  dismissedReason: string | null;
}

export interface PairingCode {
  code: string;
  expiresAt: string;
}

export interface ExtensionToken {
  id: string;
  staffUserId: string;
  staffName: string;
  label: string | null;
  pairedAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

export interface TarbilMapping {
  id: string;
  kind: TarbilMappingKind;
  vetlyKey: string;
  tarbilFields: Record<string, { value?: string; text?: string }>;
  updatedAt: string;
}

export type TarbilStockSystem = 'HBSAPP_VACCINE' | 'VETILAC_MEDICINE';
export type TarbilStockSyncStatus = 'NEW' | 'QUANTITY_DIFFERS' | 'MATCHED' | 'APPLIED';

export interface TarbilStockSyncLine {
  lineId: string;
  productName: string;
  presentation: string | null;
  lotNumber: string | null;
  expiryDate: string | null;
  tarbilQuantity: number;
  openedQuantity: number | null;
  status: TarbilStockSyncStatus;
  inventoryItemId: string | null;
  vetlyQuantity: number | null;
}

export interface TarbilStockSync {
  snapshotId: string | null;
  system: TarbilStockSystem;
  takenAt: string | null;
  lines: TarbilStockSyncLine[];
}

export const tarbilApi = {
  status: () => apiClient.get<TarbilStatus>('/api/v1/tarbil/status'),
  syncLogs: () => apiClient.get<TarbilSyncLog[]>('/api/v1/tarbil/sync-logs'),
  dismiss: (id: string, reason: string) => apiClient.post<void>(`/api/v1/tarbil/sync-logs/${id}/dismiss`, { reason }),
  restore: (id: string) => apiClient.post<void>(`/api/v1/tarbil/sync-logs/${id}/restore`),
  createPairingCode: () => apiClient.post<PairingCode>('/api/v1/tarbil/extension/pairing-codes'),
  extensionTokens: () => apiClient.get<ExtensionToken[]>('/api/v1/tarbil/extension/tokens'),
  revokeExtensionToken: (id: string) => apiClient.delete<void>(`/api/v1/tarbil/extension/tokens/${id}`),
  mappings: () => apiClient.get<TarbilMapping[]>('/api/v1/tarbil/mappings'),
  stockSync: (system: TarbilStockSystem) => apiClient.get<TarbilStockSync>(`/api/v1/tarbil/stock-sync?system=${system}`),
  applyStockSync: (snapshotId: string, lineIds: string[]) =>
    apiClient.post<{ applied: number }>(`/api/v1/tarbil/stock-sync/${snapshotId}/apply`, { lineIds }),
  deleteMapping: (id: string) => apiClient.delete<void>(`/api/v1/tarbil/mappings/${id}`),
};
