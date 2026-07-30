import { apiClient } from './client';

export type Sex = 'MALE' | 'FEMALE' | 'UNKNOWN';
export type PatientStatus = 'ACTIVE' | 'DECEASED' | 'TRANSFERRED';

export interface SpeciesItem {
  id: string;
  name: string;
}

export interface BreedItem {
  id: string;
  name: string;
}

export interface PatientSearchResult {
  patientId: string;
  patientName: string;
  speciesName: string | null;
  breedName: string | null;
  status: PatientStatus;
  ownerId: string;
  ownerFullName: string;
  ownerPhone: string;
}

export interface PatientProfile {
  id: string;
  name: string;
  speciesName: string | null;
  breedName: string | null;
  sex: Sex | null;
  neutered: boolean;
  birthDate: string | null;
  microchipNumber: string | null;
  tarbilAnimalId: string | null;
  weightKg: number | null;
  photoUrl: string | null;
  status: PatientStatus;
  ownerId: string;
  ownerFullName: string;
  ownerPhone: string;
}

export interface OwnerProfile {
  id: string;
  fullName: string;
  phone: string;
  email: string | null;
  address: string | null;
  marketingConsent: boolean;
  patients: { id: string; name: string; speciesName: string | null }[];
}

export interface RegisterOwnerPayload {
  fullName: string;
  phone: string;
  email?: string;
  address?: string;
  marketingConsent: boolean;
}

export interface RegisterPatientPayload {
  ownerId: string;
  speciesId: string;
  breedId?: string;
  name: string;
  sex?: Sex;
  birthDate?: string;
}

export const patientApi = {
  listSpecies: () => apiClient.get<SpeciesItem[]>('/api/v1/species'),
  listBreeds: (speciesId: string) => apiClient.get<BreedItem[]>(`/api/v1/species/${speciesId}/breeds`),
  search: (query: string) =>
    apiClient.get<PatientSearchResult[]>(`/api/v1/patients?query=${encodeURIComponent(query)}`),
  getProfile: (patientId: string) => apiClient.get<PatientProfile>(`/api/v1/patients/${patientId}`),
  register: (payload: RegisterPatientPayload) => apiClient.post<{ id: string }>('/api/v1/patients', payload),
  markDeceased: (patientId: string) => apiClient.post<void>(`/api/v1/patients/${patientId}/deceased`),
  getOwnerProfile: (ownerId: string) => apiClient.get<OwnerProfile>(`/api/v1/owners/${ownerId}`),
  registerOwner: (payload: RegisterOwnerPayload) => apiClient.post<{ id: string }>('/api/v1/owners', payload),
};
