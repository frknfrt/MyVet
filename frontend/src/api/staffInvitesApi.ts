import { apiClient } from './client';
import { StaffRole } from '../auth/session';

export type StaffInviteStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';

export interface StaffInviteItem {
  id: string;
  email: string;
  fullName: string;
  role: StaffRole;
  status: StaffInviteStatus;
  createdAt: string;
  expiresAt: string;
}

export interface InviteStaffMemberPayload {
  branchId: string;
  fullName: string;
  email: string;
  role: StaffRole;
}

export interface StaffInvitePublicInfo {
  email: string;
  fullName: string;
  role: StaffRole;
  tenantName: string;
}

export const staffInvitesApi = {
  list: () => apiClient.get<StaffInviteItem[]>('/api/v1/staff-invites'),
  invite: (payload: InviteStaffMemberPayload) => apiClient.post<void>('/api/v1/staff-invites', payload),
  revoke: (id: string) => apiClient.post<void>(`/api/v1/staff-invites/${id}/revoke`),
  getByToken: (token: string) => apiClient.get<StaffInvitePublicInfo>(`/api/v1/public/staff-invites/${token}`),
  accept: (token: string, password: string) =>
    apiClient.post<void>(`/api/v1/public/staff-invites/${token}/accept`, { password }),
};
