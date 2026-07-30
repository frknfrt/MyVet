import { apiClient } from './client';

export type EncounterStatus = 'DRAFT' | 'FINALIZED' | 'AMENDED';

export interface EncounterDetail {
  id: string;
  patientId: string;
  patientName: string;
  staffUserId: string;
  staffName: string;
  appointmentId: string | null;
  encounterDate: string;
  subjective: string | null;
  objective: string | null;
  assessment: string | null;
  plan: string | null;
  weightKg: number | null;
  temperatureC: number | null;
  heartRate: number | null;
  respiratoryRate: number | null;
  templateUsed: string | null;
  status: EncounterStatus;
  aiGenerated: boolean;
  finalizedAt: string | null;
}

export interface StartEncounterPayload {
  patientId: string;
  appointmentId?: string;
  templateUsed?: string;
}

export interface UpdateSoapPayload {
  subjective: string;
  objective: string;
  assessment: string;
  plan: string;
}

export interface UpdateVitalsPayload {
  weightKg: number | null;
  temperatureC: number | null;
  heartRate: number | null;
  respiratoryRate: number | null;
}

export interface InventoryUsage {
  id: string;
  inventoryItemId: string;
  quantity: number;
}

export const encounterApi = {
  start: (payload: StartEncounterPayload) => apiClient.postForId('/api/v1/encounters', payload),
  get: (id: string) => apiClient.get<EncounterDetail>(`/api/v1/encounters/${id}`),
  listByPatient: (patientId: string) => apiClient.get<EncounterDetail[]>(`/api/v1/encounters?patientId=${patientId}`),
  updateSoap: (id: string, payload: UpdateSoapPayload) => apiClient.put<void>(`/api/v1/encounters/${id}/soap`, payload),
  updateVitals: (id: string, payload: UpdateVitalsPayload) => apiClient.put<void>(`/api/v1/encounters/${id}/vitals`, payload),
  finalize: (id: string) => apiClient.post<void>(`/api/v1/encounters/${id}/finalize`),
  addMaterial: (id: string, payload: { inventoryItemId: string; quantity: number }) =>
    apiClient.post<void>(`/api/v1/encounters/${id}/materials`, payload),
  listMaterials: (id: string) => apiClient.get<InventoryUsage[]>(`/api/v1/encounters/${id}/materials`),
};
