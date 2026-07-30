import { apiClient } from './client';
import { AuthSession } from '../auth/session';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterClinicPayload {
  tenantName: string;
  taxNumber: string;
  branchName: string;
  adminFullName: string;
  adminEmail: string;
  adminPassword: string;
}

export interface BranchOverview {
  branchId: string;
  tenantName: string;
  branchName: string;
  address: string | null;
  city: string | null;
  timezone: string | null;
  tarbilBranchCode: string | null;
}

export interface UpdateBranchDetailsPayload {
  address: string;
  city: string;
  timezone: string;
  tarbilBranchCode?: string;
}

export const authApi = {
  login: (payload: LoginPayload) => apiClient.post<AuthSession>('/api/v1/auth/login', payload),
  registerClinic: (payload: RegisterClinicPayload) =>
    apiClient.post<AuthSession>('/api/v1/auth/register-clinic', payload),
  getCurrentBranch: () => apiClient.get<BranchOverview>('/api/v1/branches/current'),
  updateCurrentBranch: (payload: UpdateBranchDetailsPayload) =>
    apiClient.put<void>('/api/v1/branches/current', payload),
};
