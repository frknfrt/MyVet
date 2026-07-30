import { apiClient } from './client';

export type AppointmentStatus =
  | 'REQUESTED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'CANCELLED';

export type AppointmentSource = 'PET_APP' | 'PHONE' | 'WALK_IN' | 'WIDGET';

export interface ServiceTypeItem {
  id: string;
  name: string;
  defaultDurationMin: number;
  defaultPrice: number;
}

export interface StaffItem {
  id: string;
  fullName: string;
  role: string;
}

export interface AppointmentItem {
  id: string;
  patientId: string;
  patientName: string;
  ownerId: string;
  ownerName: string;
  assignedStaffId: string | null;
  staffName: string | null;
  serviceTypeId: string;
  serviceName: string;
  scheduledStart: string;
  scheduledEnd: string;
  status: AppointmentStatus;
  noShowRiskScore: number | null;
  source: AppointmentSource;
  notes: string | null;
}

export interface ScheduleAppointmentPayload {
  patientId: string;
  ownerId: string;
  assignedStaffId: string;
  serviceTypeId: string;
  scheduledStart: string;
  scheduledEnd: string;
  source?: AppointmentSource;
  notes?: string;
}

export const appointmentApi = {
  listServiceTypes: () => apiClient.get<ServiceTypeItem[]>('/api/v1/service-types'),
  listStaff: () => apiClient.get<StaffItem[]>('/api/v1/staff-users'),
  weeklyCalendar: (weekStart: string) =>
    apiClient.get<AppointmentItem[]>(`/api/v1/appointments?weekStart=${weekStart}`),
  schedule: (payload: ScheduleAppointmentPayload) => apiClient.postForId('/api/v1/appointments', payload),
  confirm: (id: string) => apiClient.post<void>(`/api/v1/appointments/${id}/confirm`),
  checkIn: (id: string) => apiClient.post<void>(`/api/v1/appointments/${id}/check-in`),
  start: (id: string) => apiClient.post<void>(`/api/v1/appointments/${id}/start`),
  complete: (id: string) => apiClient.post<void>(`/api/v1/appointments/${id}/complete`),
  cancel: (id: string) => apiClient.post<void>(`/api/v1/appointments/${id}/cancel`),
  markNoShow: (id: string) => apiClient.post<void>(`/api/v1/appointments/${id}/no-show`),
  assignStaff: (id: string, staffId: string) => apiClient.put<void>(`/api/v1/appointments/${id}/assign-staff`, { staffId }),
};
