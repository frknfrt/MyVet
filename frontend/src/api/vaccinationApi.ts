import { apiClient } from './client';

export type VaccinationStatus = 'SCHEDULED' | 'ADMINISTERED' | 'CANCELLED';

export interface VaccinationScheduleItem {
  id: string;
  patientId: string;
  patientName: string;
  ownerId: string;
  ownerFullName: string;
  vaccineName: string;
  lotNumber: string | null;
  administeredDate: string;
  nextDueDate: string | null;
  status: VaccinationStatus;
  notes: string | null;
  administeredByStaffName: string | null;
  seriesId: string | null;
  doseNumber: number | null;
  doseTotal: number | null;
}

export interface RecordVaccinationPayload {
  patientId: string;
  encounterId?: string;
  vaccineName: string;
  lotNumber?: string;
  administeredDate: string;
  nextDueDate?: string;
  status: VaccinationStatus;
  notes?: string;
  /** Stoktan secilen asi (spec 2026-10-04 P2): form vaccineName/lotNumber'i kalemden doldurur; sunucu yalniz kalem asi ve serisi ayniysa stoktan duser. */
  inventoryItemId?: string;
}

export interface RecordVaccinationSeriesPayload {
  patientId: string;
  encounterId?: string;
  vaccineName: string;
  lotNumber?: string;
  startDate: string;
  intervalDays: number;
  doseCount: number;
  firstDoseStatus: VaccinationStatus;
  notes?: string;
}

export interface RecordVaccinationSeriesResult {
  seriesId: string;
  vaccinationRecordIds: string[];
}

export const vaccinationApi = {
  list: (patientId?: string) =>
    apiClient.get<VaccinationScheduleItem[]>(`/api/v1/vaccination-records${patientId ? `?patientId=${patientId}` : ''}`),
  record: (payload: RecordVaccinationPayload) => apiClient.postForId('/api/v1/vaccination-records', payload),
  recordSeries: (payload: RecordVaccinationSeriesPayload) =>
    apiClient.post<RecordVaccinationSeriesResult>('/api/v1/vaccination-records/series', payload),
  markAdministered: (id: string, administeredDate?: string) =>
    apiClient.post<void>(`/api/v1/vaccination-records/${id}/administer`, administeredDate ? { administeredDate } : {}),
  cancel: (id: string) => apiClient.post<void>(`/api/v1/vaccination-records/${id}/cancel`),
  cancelRemainingSeries: (seriesId: string) =>
    apiClient.post<void>(`/api/v1/vaccination-records/series/${seriesId}/cancel-remaining`),
};
