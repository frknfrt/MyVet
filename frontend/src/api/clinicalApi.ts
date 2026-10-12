import { apiClient } from './client';

export type PrescriptionStatus = 'ACTIVE' | 'FULFILLED' | 'CANCELLED';
export type DrugRoute = 'ORAL' | 'TOPICAL' | 'INJECTABLE';

export interface PrescriptionItem {
  /** Stoktan verilen kalemde null: ilaç stok ürünüdür (spec 2026-10-12). */
  drugId: string | null;
  drugName: string | null;
  dosage: string;
  frequency: string;
  durationDays: number;
  route: string;
  /** "Kliniğin stoğundan ver" (spec 2026-10-09 S5.6). */
  inventoryItemId: string | null;
  stockQuantity: number | null;
}

export interface Prescription {
  id: string;
  patientId: string;
  /** Muayenesiz reçetede null. */
  encounterId: string | null;
  issuedDate: string;
  status: PrescriptionStatus;
  controlledSubstance: boolean;
  tarbilDiseaseId: string | null;
  items: PrescriptionItem[];
}

export interface DrugSummary {
  id: string;
  name: string;
  activeIngredient: string | null;
  isControlled: boolean;
  interactingDrugIds: string[];
}

export interface CreateDrugPayload {
  name: string;
  activeIngredient: string;
  isControlled: boolean;
}

export interface UpdateDrugPayload extends CreateDrugPayload {
  interactingDrugIds: string[];
}

export interface DrugInteractionWarning {
  drugAId: string;
  drugAName: string;
  drugBId: string;
  drugBName: string;
}

export interface PrescriptionItemPayload {
  /** Stoktan verilen kalemde null; ad drugName'de. */
  drugId: string | null;
  drugName?: string | null;
  dosage: string;
  frequency: string;
  durationDays: number;
  route: DrugRoute;
  inventoryItemId?: string | null;
  stockQuantity?: number | null;
}

export interface IssuePrescriptionPayload {
  patientId: string;
  encounterId: string | null;
  controlledSubstance: boolean;
  tarbilDiseaseId: string | null;
  items: PrescriptionItemPayload[];
}

export const clinicalApi = {
  listPrescriptionsByPatient: (patientId: string) =>
    apiClient.get<Prescription[]>(`/api/v1/prescriptions?patientId=${patientId}`),
  getPrescription: (id: string) => apiClient.get<Prescription>(`/api/v1/prescriptions/${id}`),
  issuePrescription: (payload: IssuePrescriptionPayload) => apiClient.postForId('/api/v1/prescriptions', payload),
  cancelPrescription: (id: string) => apiClient.post<void>(`/api/v1/prescriptions/${id}/cancel`),
  listDrugs: () => apiClient.get<DrugSummary[]>('/api/v1/drugs'),
  createDrug: (payload: CreateDrugPayload) => apiClient.postForId('/api/v1/drugs', payload),
  updateDrug: (id: string, payload: UpdateDrugPayload) => apiClient.put<void>(`/api/v1/drugs/${id}`, payload),
  checkDrugInteractions: (drugIds: string[]) =>
    apiClient
      .post<{ warnings: DrugInteractionWarning[] }>('/api/v1/drugs/check-interactions', { drugIds })
      .then((r) => r.warnings),
};
