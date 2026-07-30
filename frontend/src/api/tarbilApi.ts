import { apiClient } from './client';

export type TarbilSyncType = 'VACCINATION' | 'IDENTIFICATION' | 'TREATMENT';
export type TarbilSyncStatus = 'PENDING' | 'SYNCED' | 'FAILED';

export interface TarbilStatus {
  pendingCount: number;
  syncedCount: number;
  failedCount: number;
  lastSyncedAt: string | null;
  connected: boolean;
}

export interface TarbilSyncLog {
  id: string;
  patientId: string;
  patientName: string;
  syncType: TarbilSyncType;
  status: TarbilSyncStatus;
  attemptedAt: string;
}

export const tarbilApi = {
  status: () => apiClient.get<TarbilStatus>('/api/v1/tarbil/status'),
  syncLogs: () => apiClient.get<TarbilSyncLog[]>('/api/v1/tarbil/sync-logs'),
  retry: (id: string) => apiClient.post<void>(`/api/v1/tarbil/sync-logs/${id}/retry`),
};
