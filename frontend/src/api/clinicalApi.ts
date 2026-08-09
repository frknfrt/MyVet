import { apiClient } from './client';

export type PrescriptionStatus = 'ACTIVE' | 'FULFILLED' | 'CANCELLED';
export type DrugRoute = 'ORAL' | 'TOPICAL' | 'INJECTABLE';

export interface PrescriptionItem {
  drugId: string;
  drugName: string | null;
  dosage: string;
  frequency: string;
  durationDays: number;
  route: string;
}

export interface Prescription {
  id: string;
  patientId: string;
  encounterId: string;
  issuedDate: string;
  status: PrescriptionStatus;
  controlledSubstance: boolean;
  items: PrescriptionItem[];
}

export interface DrugSummary {
  id: string;
  name: string;
  activeIngredient: string | null;
  isControlled: boolean;
}

export interface PrescriptionItemPayload {
  drugId: string;
  dosage: string;
  frequency: string;
  durationDays: number;
  route: DrugRoute;
}

export interface IssuePrescriptionPayload {
  patientId: string;
  encounterId: string;
  controlledSubstance: boolean;
  items: PrescriptionItemPayload[];
}

export const clinicalApi = {
  listPrescriptionsByPatient: (patientId: string) =>
    apiClient.get<Prescription[]>(`/api/v1/prescriptions?patientId=${patientId}`),
  getPrescription: (id: string) => apiClient.get<Prescription>(`/api/v1/prescriptions/${id}`),
  issuePrescription: (payload: IssuePrescriptionPayload) => apiClient.postForId('/api/v1/prescriptions', payload),
  listDrugs: () => apiClient.get<DrugSummary[]>('/api/v1/drugs'),
};
