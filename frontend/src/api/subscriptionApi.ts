import { apiClient } from './client';

export type BillingStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED';

export interface SubscriptionOverview {
  planCode: string;
  startedAt: string;
  renewsAt: string | null;
  billingStatus: BillingStatus;
}

export const subscriptionApi = {
  getCurrent: () => apiClient.get<SubscriptionOverview>('/api/v1/subscriptions/current'),
};
