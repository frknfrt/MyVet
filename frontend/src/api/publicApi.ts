const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

async function publicRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers as Record<string, string> | undefined) },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message ?? 'İstek başarısız oldu');
  }
  if (response.status === 204) return undefined as T;
  return response.json().catch(() => undefined as T);
}

async function publicRequestForId(path: string, data: unknown): Promise<string> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message ?? 'İstek başarısız oldu');
  }
  const location = response.headers.get('Location') ?? '';
  return location.split('/').pop() ?? '';
}

export interface PublicClinicInfo {
  branchId: string;
  tenantId: string;
  tenantName: string;
  branchName: string;
  address: string | null;
  city: string | null;
  timezone: string | null;
}

export interface PublicSpecies {
  id: string;
  name: string;
}

export interface PublicServiceType {
  id: string;
  name: string;
  defaultDurationMin: number;
  defaultPrice: number;
}

export const publicApi = {
  getClinic: (branchId: string) => publicRequest<PublicClinicInfo>(`/api/v1/public/clinics/${branchId}`),
  listSpecies: () => publicRequest<PublicSpecies[]>('/api/v1/public/species'),
  listServiceTypes: (tenantId: string) =>
    publicRequest<PublicServiceType[]>(`/api/v1/public/service-types?tenantId=${tenantId}`),
  registerOwner: (payload: { tenantId: string; fullName: string; phone: string; email?: string }) =>
    publicRequest<{ id: string }>('/api/v1/public/owners', { method: 'POST', body: JSON.stringify(payload) }),
  registerPatient: (payload: { ownerId: string; speciesId: string; name: string }) =>
    publicRequest<{ id: string }>('/api/v1/public/patients', { method: 'POST', body: JSON.stringify(payload) }),
  requestAppointment: (payload: {
    tenantId: string;
    branchId: string;
    patientId: string;
    ownerId: string;
    serviceTypeId: string;
    scheduledStart: string;
    scheduledEnd: string;
    notes?: string;
  }) => publicRequestForId('/api/v1/public/appointment-requests', payload),
};
