import { apiClient } from './client';

export interface BranchItem {
  branchId: string;
  tenantId: string;
  tenantName: string;
  branchName: string;
  address: string | null;
  city: string | null;
  timezone: string | null;
  tarbilBranchCode: string | null;
}

export interface CreateBranchPayload {
  name: string;
  address?: string;
  city?: string;
  timezone?: string;
  tarbilBranchCode?: string;
}

export interface UpdateBranchPayload {
  address: string;
  city: string;
  timezone: string;
  tarbilBranchCode?: string;
}

export const branchesApi = {
  list: () => apiClient.get<BranchItem[]>('/api/v1/branches'),
  get: (id: string) => apiClient.get<BranchItem>(`/api/v1/branches/${id}`),
  create: (payload: CreateBranchPayload) => apiClient.post<void>('/api/v1/branches', payload),
  update: (id: string, payload: UpdateBranchPayload) => apiClient.put<void>(`/api/v1/branches/${id}`, payload),
};
