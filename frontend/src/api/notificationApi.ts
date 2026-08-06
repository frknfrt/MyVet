import { apiClient } from './client';

export type NotificationChannel = 'SMS' | 'WHATSAPP';
export type NotificationType = 'APPOINTMENT_CONFIRMATION' | 'APPOINTMENT_REMINDER';
export type NotificationLogStatus = 'PENDING' | 'SENT' | 'FAILED';

export interface NotificationStatus {
  pendingCount: number;
  sentCount: number;
  failedCount: number;
  lastSentAt: string | null;
  connected: boolean;
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

export const notificationApi = {
  status: () => apiClient.get<NotificationStatus>('/api/v1/notifications/status'),
  logs: () => apiClient.get<NotificationLog[]>('/api/v1/notifications/logs'),
  retry: (id: string) => apiClient.post<void>(`/api/v1/notifications/logs/${id}/retry`),
};
