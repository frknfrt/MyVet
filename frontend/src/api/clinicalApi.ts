import { apiClient } from './client';

export type PrescriptionStatus = 'ACTIVE' | 'FULFILLED' | 'CANCELLED';

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

export const clinicalApi = {
  listPrescriptionsByPatient: (patientId: string) =>
    apiClient.get<Prescription[]>(`/api/v1/prescriptions?patientId=${patientId}`),
};
