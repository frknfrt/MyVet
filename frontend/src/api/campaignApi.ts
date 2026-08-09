import { apiClient } from './client';
import { NotificationChannel } from './notificationApi';

export type TemplateChannel = 'SMS' | 'WHATSAPP' | 'BOTH';

export interface MessageTemplate {
  id: string;
  name: string;
  channel: TemplateChannel;
  category: string;
  body: string;
  createdAt: string;
  updatedAt: string;
}

export interface MessageTemplateInput {
  name: string;
  channel: TemplateChannel;
  category: string;
  body: string;
}

export const templateApi = {
  list: () => apiClient.get<MessageTemplate[]>('/api/v1/message-templates'),
  create: (input: MessageTemplateInput) => apiClient.post<void>('/api/v1/message-templates', input),
  update: (id: string, input: MessageTemplateInput) => apiClient.put<void>(`/api/v1/message-templates/${id}`, input),
  remove: (id: string) => apiClient.delete<void>(`/api/v1/message-templates/${id}`),
};

export interface OwnerCampaignCandidate {
  id: string;
  fullName: string;
  phone: string;
  smsConsent: boolean;
  whatsappConsent: boolean;
  createdAt: string;
}

export interface OwnerCandidateFilters {
  name?: string;
  registeredFrom?: string;
  registeredTo?: string;
}

export interface AppointmentCampaignCandidate {
  ownerId: string;
  ownerFullName: string;
  ownerPhone: string;
  smsConsent: boolean;
  whatsappConsent: boolean;
  patientName: string;
  scheduledStart: string;
}

export interface AppointmentCandidateFilters {
  from: string;
  to: string;
  status?: string;
}

export interface VaccinationCampaignCandidate {
  ownerId: string;
  ownerFullName: string;
  ownerPhone: string;
  smsConsent: boolean;
  whatsappConsent: boolean;
  patientName: string;
  vaccineName: string;
  nextDueDate: string;
}

export interface VaccinationCandidateFilters {
  dueFrom?: string;
  dueTo?: string;
}

function toQuery(params: object): string {
  const search = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
    if (typeof value === 'string' && value) search.set(key, value);
  });
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

export const recipientCandidatesApi = {
  owners: (filters: OwnerCandidateFilters) =>
    apiClient.get<OwnerCampaignCandidate[]>(`/api/v1/owners/campaign-candidates${toQuery(filters)}`),
  appointments: (filters: AppointmentCandidateFilters) =>
    apiClient.get<AppointmentCampaignCandidate[]>(`/api/v1/appointments/campaign-candidates${toQuery(filters)}`),
  vaccinations: (filters: VaccinationCandidateFilters) =>
    apiClient.get<VaccinationCampaignCandidate[]>(`/api/v1/vaccination-records/campaign-candidates${toQuery(filters)}`),
};

export interface CampaignRecipientInput {
  ownerId: string | null;
  phone: string;
  smsConsent: boolean;
  whatsappConsent: boolean;
  variables: Record<string, string>;
  label: string | null;
}

export interface CampaignSendSummary {
  queued: number;
  skippedNoPhone: number;
  skippedNoConsent: number;
}

export const campaignApi = {
  sendCampaign: (channel: NotificationChannel, messageBody: string, recipients: CampaignRecipientInput[]) =>
    apiClient.post<CampaignSendSummary>('/api/v1/notifications/campaigns/send', { channel, messageBody, recipients }),
};
