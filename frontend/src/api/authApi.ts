import { apiClient } from './client';
import { AuthSession } from '../auth/session';

export interface LoginPayload {
  email: string;
  password: string;
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

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export const authApi = {
  login: (payload: LoginPayload) => apiClient.post<AuthSession>('/api/v1/auth/login', payload),
  getCurrentBranch: () => apiClient.get<BranchOverview>('/api/v1/branches/current'),
  updateCurrentBranch: (payload: UpdateBranchDetailsPayload) =>
    apiClient.put<void>('/api/v1/branches/current', payload),
  changePassword: (payload: ChangePasswordPayload) =>
    apiClient.put<void>('/api/v1/staff-users/me/password', payload),
};
