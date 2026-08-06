import { apiClient } from './client';

export type BoardingStayStatus = 'CHECKED_IN' | 'CHECKED_OUT' | 'CANCELLED';

export interface BoardingRoom {
  id: string;
  groupName: string;
  name: string;
  capacity: number;
  dailyRate: number | null;
  notes: string | null;
  active: boolean;
}

export interface CreateBoardingRoomPayload {
  groupName: string;
  name: string;
  capacity: number;
  dailyRate?: number;
  notes?: string;
}

export interface BoardingStay {
  id: string;
  roomId: string;
  roomName: string;
  groupName: string;
  patientId: string;
  patientName: string;
  ownerId: string;
  ownerName: string;
  checkInDate: string;
  expectedCheckOutDate: string | null;
  actualCheckOutDate: string | null;
  status: BoardingStayStatus;
  notes: string | null;
}

export interface CreateBoardingStayPayload {
  roomId: string;
  patientId: string;
  checkInDate: string;
  expectedCheckOutDate?: string;
  notes?: string;
}

export const boardingApi = {
  listRooms: () => apiClient.get<BoardingRoom[]>('/api/v1/boarding-rooms'),
  createRoom: (payload: CreateBoardingRoomPayload) => apiClient.postForId('/api/v1/boarding-rooms', payload),
  listStays: () => apiClient.get<BoardingStay[]>('/api/v1/boarding-stays'),
  createStay: (payload: CreateBoardingStayPayload) => apiClient.postForId('/api/v1/boarding-stays', payload),
  checkOut: (id: string, actualCheckOutDate?: string) =>
    apiClient.post<void>(`/api/v1/boarding-stays/${id}/check-out`, actualCheckOutDate ? { actualCheckOutDate } : {}),
  cancel: (id: string) => apiClient.post<void>(`/api/v1/boarding-stays/${id}/cancel`),
  getInvoiceIdForStay: (boardingStayId: string) =>
    apiClient.get<{ id: string }>(`/api/v1/invoices/by-boarding-stay/${boardingStayId}`).then((r) => r.id),
};
