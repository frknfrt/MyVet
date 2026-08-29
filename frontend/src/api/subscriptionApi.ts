import { apiClient } from './client';

export type BillingStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED';

export interface SubscriptionOverview {
  planCode: string;
  startedAt: string;
  renewsAt: string | null;
  billingStatus: BillingStatus;
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

export const subscriptionApi = {
  getCurrent: () => apiClient.get<SubscriptionOverview>('/api/v1/subscriptions/current'),
  getInvoices: () => apiClient.get<TenantBillingOverview>('/api/v1/subscriptions/invoices'),
  getPlans: () => apiClient.get<CatalogPlan[]>('/api/v1/subscriptions/plans'),
  initiateCheckout: (invoiceId: string) =>
    apiClient.post<CheckoutSession>(`/api/v1/subscriptions/invoices/${invoiceId}/checkout`),
};
