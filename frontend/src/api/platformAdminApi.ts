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

export interface CreateTenantPayload {
  tenantName: string;
  taxNumber: string;
  branchName: string;
  address: string;
  city: string;
  adminFullName: string;
  adminEmail: string;
  adminPassword: string;
}

export interface Plan {
  id: string;
  code: string;
  name: string;
  monthlyPrice: number;
  annualPrice: number | null;
  description: string | null;
  badge: string | null;
  imageUrl: string | null;
  features: string[];
  active: boolean;
}

export interface UpdatePlanPayload {
  name: string;
  monthlyPrice: number;
  annualPrice: number | null;
  description: string | null;
  badge: string | null;
  imageUrl: string | null;
  features: string[];
  active: boolean;
}

export type PlatformInvoiceStatus = 'ISSUED' | 'PAID' | 'OVERDUE' | 'VOID';
export type PlatformPaymentMethod = 'BANK_TRANSFER' | 'CARD' | 'OTHER';

export interface PlatformInvoice {
  id: string;
  planCode: string;
  amount: number;
  periodStart: string;
  periodEnd: string;
  dueDate: string;
  status: PlatformInvoiceStatus;
  issuedAt: string;
  paidAt: string | null;
}

export interface RecordPlatformPaymentPayload {
  amount: number;
  method: PlatformPaymentMethod;
  paidAt: string;
  notes?: string;
}

export const platformAdminApi = {
  login: (payload: PlatformAdminLoginPayload) =>
    platformAdminClient.post<PlatformAdminSession>('/api/v1/platform-admin/auth/login', payload),
  listTenants: () => platformAdminClient.get<TenantAdminOverview[]>('/api/v1/platform-admin/tenants'),
  getTenant: (id: string) => platformAdminClient.get<TenantAdminOverview>(`/api/v1/platform-admin/tenants/${id}`),
  createTenant: (payload: CreateTenantPayload) =>
    platformAdminClient.post<TenantAdminOverview>('/api/v1/platform-admin/tenants', payload),
  updateSubscription: (id: string, payload: UpdateTenantSubscriptionPayload) =>
    platformAdminClient.put<void>(`/api/v1/platform-admin/tenants/${id}/subscription`, payload),
  suspendTenant: (id: string) => platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${id}/suspend`),
  activateTenant: (id: string) => platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${id}/activate`),
  listPlans: () => platformAdminClient.get<Plan[]>('/api/v1/platform-admin/plans'),
  createPlan: (payload: { code: string; name: string; monthlyPrice: number }) =>
    platformAdminClient.post<void>('/api/v1/platform-admin/plans', payload),
  updatePlan: (id: string, payload: UpdatePlanPayload) =>
    platformAdminClient.put<void>(`/api/v1/platform-admin/plans/${id}`, payload),
  deletePlan: (id: string) => platformAdminClient.delete<void>(`/api/v1/platform-admin/plans/${id}`),
  listInvoices: (tenantId: string) =>
    platformAdminClient.get<PlatformInvoice[]>(`/api/v1/platform-admin/tenants/${tenantId}/invoices`),
  recordInvoicePayment: (tenantId: string, invoiceId: string, payload: RecordPlatformPaymentPayload) =>
    platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${tenantId}/invoices/${invoiceId}/payments`, payload),
  voidInvoice: (tenantId: string, invoiceId: string) =>
    platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${tenantId}/invoices/${invoiceId}/void`),
};
