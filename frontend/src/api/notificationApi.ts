import { apiClient } from './client';

export type NotificationChannel = 'SMS' | 'WHATSAPP';
export type NotificationType = 'APPOINTMENT_CONFIRMATION' | 'APPOINTMENT_REMINDER' | 'CAMPAIGN_MESSAGE';
export type NotificationLogStatus = 'PENDING' | 'SENT' | 'FAILED';

export interface NotificationStatus {
  pendingCount: number;
  sentCount: number;
  failedCount: number;
  lastSentAt: string | null;
  smsConfigured: boolean;
  whatsappConfigured: boolean;
}

export interface NotificationLog {
  id: string;
  ownerName: string;
  recipientContact: string;
  channel: NotificationChannel;
  notificationType: NotificationType;
  message: string;
  status: NotificationLogStatus;
  attemptedAt: string;
}

export interface NotificationLogFilters {
  channel?: NotificationChannel;
  status?: NotificationLogStatus;
  notificationType?: NotificationType;
  from?: string;
  to?: string;
  search?: string;
}

function buildQuery(filters: NotificationLogFilters): string {
  const params = new URLSearchParams();
  if (filters.channel) params.set('channel', filters.channel);
  if (filters.status) params.set('status', filters.status);
  if (filters.notificationType) params.set('notificationType', filters.notificationType);
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (filters.search) params.set('search', filters.search);
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

export const notificationApi = {
  status: () => apiClient.get<NotificationStatus>('/api/v1/notifications/status'),
  logs: (filters: NotificationLogFilters = {}) => apiClient.get<NotificationLog[]>(`/api/v1/notifications/logs${buildQuery(filters)}`),
  retry: (id: string) => apiClient.post<void>(`/api/v1/notifications/logs/${id}/retry`),
};
