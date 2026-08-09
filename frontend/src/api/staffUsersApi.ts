import { apiClient } from './client';
import { StaffRole } from '../auth/session';

export interface StaffUserItem {
  id: string;
  branchId: string;
  fullName: string;
  email: string;
  phone: string | null;
  role: StaffRole;
  licenseNumber: string | null;
  specialty: string | null;
  bio: string | null;
  active: boolean;
  createdAt: string;
}

export interface CreateStaffUserPayload {
  branchId: string;
  fullName: string;
  email: string;
  password: string;
  role: StaffRole;
  phone?: string;
  licenseNumber?: string;
  specialty?: string;
  bio?: string;
}

export interface UpdateStaffUserPayload {
  fullName: string;
  phone?: string;
  role: StaffRole;
  licenseNumber?: string;
  specialty?: string;
  bio?: string;
}

export const staffUsersApi = {
  list: () => apiClient.get<StaffUserItem[]>('/api/v1/staff-users'),
  create: (payload: CreateStaffUserPayload) => apiClient.post<void>('/api/v1/staff-users', payload),
  update: (id: string, payload: UpdateStaffUserPayload) => apiClient.put<void>(`/api/v1/staff-users/${id}`, payload),
  deactivate: (id: string) => apiClient.post<void>(`/api/v1/staff-users/${id}/deactivate`),
  activate: (id: string) => apiClient.post<void>(`/api/v1/staff-users/${id}/activate`),
};
