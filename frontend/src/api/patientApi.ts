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
  color: string | null;
  temperament: string | null;
  distinguishingMarks: string | null;
  aggressive: boolean;
  bloodType: string | null;
  foodBrand: string | null;
  criticalAlert: string | null;
  notes: string | null;
  protocolNumber: string | null;
  rabiesTag: string | null;
  status: PatientStatus;
  ownerId: string;
  ownerFullName: string;
  ownerPhone: string;
}

export interface RegisterPatientPayload {
  ownerId: string;
  speciesId: string;
  breedId?: string;
  name: string;
  sex?: Sex;
  birthDate?: string;
  color?: string;
  temperament?: string;
  distinguishingMarks?: string;
  aggressive?: boolean;
  bloodType?: string;
  foodBrand?: string;
  criticalAlert?: string;
  notes?: string;
  protocolNumber?: string;
  rabiesTag?: string;
}

export interface UpdatePatientPayload {
  name: string;
  breedId?: string;
  sex?: Sex;
  birthDate?: string;
  neutered: boolean;
  color?: string;
  temperament?: string;
  distinguishingMarks?: string;
  aggressive?: boolean;
  bloodType?: string;
  foodBrand?: string;
  criticalAlert?: string;
  notes?: string;
  protocolNumber?: string;
  rabiesTag?: string;
}

export interface OwnerSearchResult {
  id: string;
  fullName: string;
  phone: string;
}

export interface OwnerProfile {
  id: string;
  fullName: string;
  middleName: string | null;
  phone: string;
  secondaryPhone: string | null;
  email: string | null;
  address: string | null;
  city: string | null;
  district: string | null;
  occupation: string | null;
  referralSource: string | null;
  clientDiscount: number;
  criticalAlert: string | null;
  notes: string | null;
  marketingConsent: boolean;
  smsConsent: boolean;
  whatsappConsent: boolean;
  notificationConsent: boolean;
  protocolNumber: string | null;
  patients: { id: string; name: string; speciesName: string | null; breedName: string | null; status: PatientStatus }[];
}

export interface RegisterOwnerPayload {
  fullName: string;
  middleName?: string;
  phone: string;
  secondaryPhone?: string;
  email?: string;
  address?: string;
  city?: string;
  district?: string;
  occupation?: string;
  referralSource?: string;
  clientDiscount?: number;
  criticalAlert?: string;
  notes?: string;
  marketingConsent: boolean;
  smsConsent?: boolean;
  whatsappConsent?: boolean;
  notificationConsent?: boolean;
  protocolNumber?: string;
}

export interface UpdateOwnerPayload {
  phone: string;
  email?: string;
  address?: string;
  middleName?: string;
  secondaryPhone?: string;
  city?: string;
  district?: string;
  occupation?: string;
  referralSource?: string;
  clientDiscount?: number;
  criticalAlert?: string;
  notes?: string;
  smsConsent?: boolean;
  whatsappConsent?: boolean;
  notificationConsent?: boolean;
  protocolNumber?: string;
}

export interface PatientGrowthSummary {
  newPatientsThisMonth: number;
  newPatientsLastMonth: number;
}

export type ConsentType = 'KVKK_ACIK_RIZA' | 'PAZARLAMA' | 'VERI_AKTARIMI';

export interface ConsentRecordItem {
  id: string;
  consentType: ConsentType;
  granted: boolean;
  ipAddress: string | null;
  grantedAt: string;
  revokedAt: string | null;
}

export const patientApi = {
  listSpecies: () => apiClient.get<SpeciesItem[]>('/api/v1/species'),
  growthSummary: () => apiClient.get<PatientGrowthSummary>('/api/v1/patients/growth-summary'),
  createSpecies: (name: string) => apiClient.post<SpeciesItem>('/api/v1/species', { name }),
  listBreeds: (speciesId: string) => apiClient.get<BreedItem[]>(`/api/v1/species/${speciesId}/breeds`),
  createBreed: (speciesId: string, name: string) =>
    apiClient.post<BreedItem>(`/api/v1/species/${speciesId}/breeds`, { name }),
  search: (query: string) =>
    apiClient.get<PatientSearchResult[]>(`/api/v1/patients?query=${encodeURIComponent(query)}`),
  getProfile: (patientId: string) => apiClient.get<PatientProfile>(`/api/v1/patients/${patientId}`),
  register: (payload: RegisterPatientPayload) => apiClient.post<{ id: string }>('/api/v1/patients', payload),
  update: (patientId: string, payload: UpdatePatientPayload) =>
    apiClient.put<void>(`/api/v1/patients/${patientId}`, payload),
  updateIdentification: (patientId: string, payload: { microchipNumber?: string; tarbilAnimalId?: string }) =>
    apiClient.put<void>(`/api/v1/patients/${patientId}/identification`, payload),
  markDeceased: (patientId: string) => apiClient.post<void>(`/api/v1/patients/${patientId}/deceased`),
  searchOwners: (query: string) =>
    apiClient.get<OwnerSearchResult[]>(`/api/v1/owners?query=${encodeURIComponent(query)}`),
  getOwnerProfile: (ownerId: string) => apiClient.get<OwnerProfile>(`/api/v1/owners/${ownerId}`),
  registerOwner: (payload: RegisterOwnerPayload) => apiClient.post<{ id: string }>('/api/v1/owners', payload),
  updateOwner: (ownerId: string, payload: UpdateOwnerPayload) =>
    apiClient.put<void>(`/api/v1/owners/${ownerId}`, payload),
  listConsents: (ownerId: string) => apiClient.get<ConsentRecordItem[]>(`/api/v1/owners/${ownerId}/consents`),
  recordConsent: (ownerId: string, consentType: ConsentType) =>
    apiClient.post<void>(`/api/v1/owners/${ownerId}/consents`, { consentType }),
  revokeConsent: (ownerId: string, consentId: string) =>
    apiClient.post<void>(`/api/v1/owners/${ownerId}/consents/${consentId}/revoke`),
};
