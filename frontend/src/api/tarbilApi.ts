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
  /** Sunucunun son gördüğü eklenti sürümü; null = 0.2.0 öncesi (sürüm göndermeyen) eklenti. */
  lastClientVersion: string | null;
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
  /** Spec 2026-10-24: ürün+seri başına tek satır; tarbilQuantity kapalı adet, tarbilOpenedQuantity açık/parça (null = bilinmiyor). */
  tarbilOpenedQuantity: number | null;
  status: TarbilStockSyncStatus;
  inventoryItemId: string | null;
  vetlyQuantity: number | null;
  vetlyOpenedQuantity: number | null;
  unit: string;
  /** TARBİL aşı sayfasındaki "Parça Stok Ara" listesinden geldi (2026-10-21). */
  partStock: boolean;
  /** Bu ürünün TARBİL'e henüz işlenmemiş aşı/reçete kaydı sayısı; > 0 ise satır eşitlenemez (2026-10-21). */
  pendingTarbilCount: number;
}

export interface TarbilStockSync {
  snapshotId: string | null;
  system: TarbilStockSystem;
  takenAt: string | null;
  lines: TarbilStockSyncLine[];
}

export interface TarbilDiseaseOption {
  id: string;
  parentId: string | null;
  name: string;
  path: string;
  selectable: boolean;
}

export interface EncounterDeductionLine {
  itemName: string;
  lotNumber: string | null;
  quantity: number;
  status: 'PENDING' | 'DEDUCTED' | 'CANCELLED';
  settledQuantity: number | null;
  /** Kalemin kullanım birimi (spec 2026-10-25); eski sunucu göndermez. */
  unit?: string | null;
}

export interface EncounterDeduction {
  lines: EncounterDeductionLine[];
  submissionId: string | null;
  submissionStatus: TarbilSyncStatus | null;
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
  listDiseases: () => apiClient.get<TarbilDiseaseOption[]>('/api/v1/tarbil/diseases'),
  getEncounterDeduction: (encounterId: string) =>
    apiClient.get<EncounterDeduction>(`/api/v1/tarbil/encounters/${encounterId}/deduction`),
  queueEncounterDeduction: (encounterId: string, tarbilDiseaseId: string) =>
    apiClient.post<{ submissionId: string }>(`/api/v1/tarbil/encounters/${encounterId}/deduction`, { tarbilDiseaseId }),
};
