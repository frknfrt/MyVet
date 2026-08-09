import { platformAdminClient } from '../platformAdmin/platformAdminClient';
import { PlatformAdminSession } from '../platformAdmin/session';

export interface PlatformAdminLoginPayload {
  email: string;
  password: string;
}

export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'TRIAL';
export type BillingStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED';

export interface TenantAdminOverview {
  tenantId: string;
  name: string;
  taxNumber: string | null;
  status: TenantStatus;
  createdAt: string;
  planCode: string;
  billingStatus: BillingStatus;
  startedAt: string;
  renewsAt: string | null;
  branchCount: number;
  staffUserCount: number;
}

export interface UpdateTenantSubscriptionPayload {
  planCode: string;
  billingStatus: BillingStatus;
  renewsAt?: string | null;
}

export interface Plan {
  id: string;
  code: string;
  name: string;
  monthlyPrice: number;
  active: boolean;
}

export interface PlanPayload {
  code?: string;
  name: string;
  monthlyPrice: number;
  active?: boolean;
}

export const platformAdminApi = {
  login: (payload: PlatformAdminLoginPayload) =>
    platformAdminClient.post<PlatformAdminSession>('/api/v1/platform-admin/auth/login', payload),
  listTenants: () => platformAdminClient.get<TenantAdminOverview[]>('/api/v1/platform-admin/tenants'),
  getTenant: (id: string) => platformAdminClient.get<TenantAdminOverview>(`/api/v1/platform-admin/tenants/${id}`),
  updateSubscription: (id: string, payload: UpdateTenantSubscriptionPayload) =>
    platformAdminClient.put<void>(`/api/v1/platform-admin/tenants/${id}/subscription`, payload),
  suspendTenant: (id: string) => platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${id}/suspend`),
  activateTenant: (id: string) => platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${id}/activate`),
  listPlans: () => platformAdminClient.get<Plan[]>('/api/v1/platform-admin/plans'),
  createPlan: (payload: { code: string; name: string; monthlyPrice: number }) =>
    platformAdminClient.post<void>('/api/v1/platform-admin/plans', payload),
  updatePlan: (id: string, payload: { name: string; monthlyPrice: number; active: boolean }) =>
    platformAdminClient.put<void>(`/api/v1/platform-admin/plans/${id}`, payload),
  deletePlan: (id: string) => platformAdminClient.delete<void>(`/api/v1/platform-admin/plans/${id}`),
};
