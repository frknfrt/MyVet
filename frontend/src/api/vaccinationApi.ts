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
}

export const vaccinationApi = {
  list: (patientId?: string) =>
    apiClient.get<VaccinationScheduleItem[]>(`/api/v1/vaccination-records${patientId ? `?patientId=${patientId}` : ''}`),
  record: (payload: RecordVaccinationPayload) => apiClient.postForId('/api/v1/vaccination-records', payload),
  markAdministered: (id: string, administeredDate?: string) =>
    apiClient.post<void>(`/api/v1/vaccination-records/${id}/administer`, administeredDate ? { administeredDate } : {}),
  cancel: (id: string) => apiClient.post<void>(`/api/v1/vaccination-records/${id}/cancel`),
};
