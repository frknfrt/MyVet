import { apiClient } from './client';

export type BillingStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED';

export interface SubscriptionOverview {
  planCode: string;
  startedAt: string;
  renewsAt: string | null;
  billingStatus: BillingStatus;
}

export type BillingPeriod = 'MONTHLY' | 'ANNUAL';

/** Kliniğin abonelik erişim durumu; trialEndsAt/daysLeft yalnız TRIAL ve TRIAL_EXPIRED için dolu. */
export interface SubscriptionAccess {
  state: 'TRIAL' | 'TRIAL_EXPIRED' | 'INTERNAL' | 'ACTIVE';
  trialEndsAt: string | null;
  daysLeft: number | null;
}

export interface UpgradeCheckoutPayload {
  planCode: string;
  billingPeriod: BillingPeriod;
  couponCode?: string;
}

export interface EnabledFeatures {
  enabledFeatures: string[];
  enforcementActive: boolean;
}

export type PlatformInvoiceStatus = 'ISSUED' | 'PAID' | 'OVERDUE' | 'VOID';

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

export interface TenantBillingOverview {
  paymentInstructions: string;
  invoices: PlatformInvoice[];
}

export interface CatalogPlan {
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

export interface CheckoutSession {
  checkoutFormUrl: string;
}

export interface UsageItem {
  used: number;
  quota: number | null;
}

export interface UsageSnapshot {
  period: string;
  enforced: boolean;
  ai: UsageItem;
  message: UsageItem;
}

export const subscriptionApi = {
  getCurrent: () => apiClient.get<SubscriptionOverview>('/api/v1/subscriptions/current'),
  getFeatures: () => apiClient.get<EnabledFeatures>('/api/v1/subscriptions/features'),
  getAccess: () => apiClient.get<SubscriptionAccess>('/api/v1/subscriptions/access'),
  /** Deneme sürümünden seçilen pakete geçiş için ödeme sayfası adresini döner. */
  initiateUpgradeCheckout: (payload: UpgradeCheckoutPayload) =>
    apiClient.post<CheckoutSession>('/api/v1/subscriptions/upgrade-checkout', payload),
  getInvoices: () => apiClient.get<TenantBillingOverview>('/api/v1/subscriptions/invoices'),
  getUsage: () => apiClient.get<UsageSnapshot>('/api/v1/subscriptions/usage'),
  getPlans: () => apiClient.get<CatalogPlan[]>('/api/v1/subscriptions/plans'),
  initiateCheckout: (invoiceId: string) =>
    apiClient.post<CheckoutSession>(`/api/v1/subscriptions/invoices/${invoiceId}/checkout`),
  cancel: () => apiClient.post<SubscriptionOverview>('/api/v1/subscriptions/cancel'),
  reactivate: () => apiClient.post<SubscriptionOverview>('/api/v1/subscriptions/reactivate'),
};
