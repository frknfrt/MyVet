import { platformAdminClient } from '../platformAdmin/platformAdminClient';
import { PlatformAdminSession } from '../platformAdmin/session';

export interface PlatformAdminLoginPayload {
  email: string;
  password: string;
}

export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'TRIAL';
export type BillingStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED';

export type TenantSuspensionReason = 'BILLING_OVERDUE' | 'PRICE' | 'COMPETITOR' | 'NOT_USING' | 'DISSATISFIED' | 'CLOSED_BUSINESS' | 'OTHER';

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
  suspensionReason: TenantSuspensionReason | null;
  suspensionNote: string | null;
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

export type PlanFeatureFlag = 'AI_ASSISTANT' | 'IMAGING' | 'LAB_INTEGRATION' | 'BOARDING' | 'INVENTORY' | 'E_FATURA';

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
  enabledFeatures: PlanFeatureFlag[];
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
  enabledFeatures: PlanFeatureFlag[];
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

export type NotificationChannel = 'SMS' | 'WHATSAPP';
export type NotificationType =
  | 'APPOINTMENT_CONFIRMATION' | 'APPOINTMENT_REMINDER' | 'CAMPAIGN_MESSAGE' | 'VACCINATION_REMINDER';

export interface FailedNotification {
  notificationLogId: string;
  tenantId: string;
  tenantName: string;
  recipientLabel: string | null;
  recipientContact: string;
  channel: NotificationChannel;
  notificationType: NotificationType;
  failureReason: string | null;
  attemptCount: number;
  attemptedAt: string;
  nextRetryAt: string | null;
}

export type EInvoiceDocumentType = 'E_FATURA' | 'E_ARSIV';

export interface FailedEInvoice {
  submissionId: string;
  tenantId: string;
  tenantName: string;
  invoiceId: string;
  documentType: EInvoiceDocumentType;
  totalAmount: number;
  failureReason: string | null;
  attemptCount: number;
  attemptedAt: string;
}

export type TarbilSyncType = 'VACCINATION' | 'IDENTIFICATION' | 'TREATMENT';

export interface FailedTarbilSync {
  syncLogId: string;
  tenantId: string;
  tenantName: string;
  patientId: string;
  patientName: string;
  syncType: TarbilSyncType;
  failureReason: string | null;
  attemptCount: number;
  attemptedAt: string;
}

export interface TenantIntegrationHealth {
  failedEInvoiceCount: number;
  failedTarbilSyncCount: number;
}

export interface PlanRevenueBreakdown {
  planCode: string;
  planName: string;
  tenantCount: number;
  monthlyRevenue: number;
}

export interface RecentTenantSummary {
  tenantId: string;
  name: string;
  planCode: string;
  createdAt: string;
}

export interface PlatformOverview {
  totalTenants: number;
  activeTenants: number;
  suspendedTenants: number;
  trialBillingTenants: number;
  newTenantsLast30Days: number;
  monthlyRecurringRevenue: number;
  collectedThisMonth: number;
  overdueInvoiceCount: number;
  overdueInvoiceTotal: number;
  planBreakdown: PlanRevenueBreakdown[];
  recentTenants: RecentTenantSummary[];
  churnBreakdown: ChurnReasonBreakdown[];
}

export interface ChurnReasonBreakdown {
  reason: string;
  count: number;
}

export interface ImpersonationSession {
  token: string;
  staffUserId: string;
  tenantId: string;
  branchId: string;
  fullName: string;
  role: string;
}

export type TenantSignupRequestStatus = 'PENDING' | 'COMPLETED';

export interface TenantSignupRequest {
  id: string;
  clinicName: string;
  adminFullName: string;
  adminEmail: string;
  phone: string | null;
  planCode: string;
  status: TenantSignupRequestStatus;
  createdAt: string;
}


export type CouponDiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';

export interface Coupon {
  id: string;
  code: string;
  discountType: CouponDiscountType;
  discountValue: number;
  maxRedemptions: number | null;
  redemptionCount: number;
  expiresAt: string | null;
  active: boolean;
  redeemable: boolean;
  createdAt: string;
}

export interface CreateCouponPayload {
  code: string;
  discountType: CouponDiscountType;
  discountValue: number;
  maxRedemptions: number | null;
  expiresAt: string | null;
}


export interface Announcement {
  id: string;
  title: string;
  body: string;
  createdByAdminEmail: string;
  recipientCount: number;
  createdAt: string;
}

export interface AuditLogEntry {
  id: string;
  platformAdminId: string;
  platformAdminEmail: string;
  action: string;
  targetType: string;
  targetId: string | null;
  details: string | null;
  createdAt: string;
}

export interface AiUsageByTenant {
  tenantId: string;
  tenantName: string;
  totalJobs: number;
  diagnosisJobs: number;
  treatmentJobs: number;
  acceptedAsIs: number;
  acceptedWithEdits: number;
  rejected: number;
  noDecisionYet: number;
  accurateFeedback: number;
  inaccurateFeedback: number;
  lastUsedAt: string | null;
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
  suspendTenant: (id: string, payload: { reason: TenantSuspensionReason; note: string | null }) =>
    platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${id}/suspend`, payload),
  activateTenant: (id: string) => platformAdminClient.post<void>(`/api/v1/platform-admin/tenants/${id}/activate`),
  getTenantIntegrationHealth: (id: string) =>
    platformAdminClient.get<TenantIntegrationHealth>(`/api/v1/platform-admin/tenants/${id}/integration-health`),
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
  listFailedNotifications: () =>
    platformAdminClient.get<FailedNotification[]>('/api/v1/platform-admin/system-health/notifications'),
  listFailedEInvoices: () =>
    platformAdminClient.get<FailedEInvoice[]>('/api/v1/platform-admin/system-health/efatura'),
  listFailedTarbilSyncs: () =>
    platformAdminClient.get<FailedTarbilSync[]>('/api/v1/platform-admin/system-health/tarbil'),
  retryFailedNotification: (notificationLogId: string) =>
    platformAdminClient.post<void>(`/api/v1/platform-admin/system-health/notifications/${notificationLogId}/retry`),
  retryFailedEInvoice: (submissionId: string) =>
    platformAdminClient.post<void>(`/api/v1/platform-admin/system-health/efatura/${submissionId}/retry`),
  retryFailedTarbilSync: (syncLogId: string) =>
    platformAdminClient.post<void>(`/api/v1/platform-admin/system-health/tarbil/${syncLogId}/retry`),
  getOverview: () => platformAdminClient.get<PlatformOverview>('/api/v1/platform-admin/overview'),
  impersonateTenant: (tenantId: string) =>
    platformAdminClient.post<ImpersonationSession>(`/api/v1/platform-admin/tenants/${tenantId}/impersonate`),
  listSignupRequests: () => platformAdminClient.get<TenantSignupRequest[]>('/api/v1/platform-admin/signup-requests'),
  listCoupons: () => platformAdminClient.get<Coupon[]>('/api/v1/platform-admin/coupons'),
  createCoupon: (payload: CreateCouponPayload) =>
    platformAdminClient.post<Coupon>('/api/v1/platform-admin/coupons', payload),
  activateCoupon: (id: string) => platformAdminClient.post<void>(`/api/v1/platform-admin/coupons/${id}/activate`),
  deactivateCoupon: (id: string) => platformAdminClient.post<void>(`/api/v1/platform-admin/coupons/${id}/deactivate`),
  listAuditLog: () => platformAdminClient.get<AuditLogEntry[]>('/api/v1/platform-admin/audit-log'),
  listAiUsage: () => platformAdminClient.get<AiUsageByTenant[]>('/api/v1/platform-admin/ai-usage'),
  listAnnouncements: () => platformAdminClient.get<Announcement[]>('/api/v1/platform-admin/announcements'),
  sendAnnouncement: (payload: { title: string; body: string }) =>
    platformAdminClient.post<void>('/api/v1/platform-admin/announcements', payload),
};
