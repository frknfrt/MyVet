import { apiClient } from './client';

export type ImagingModality = 'XRAY' | 'ULTRASOUND' | 'DICOM' | 'OTHER';
export type ImagingRecordStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED';

export interface ImagingRecordSummary {
  id: string;
  patientId: string;
  patientName: string;
  ownerId: string;
  ownerFullName: string;
  modality: ImagingModality;
  bodyRegion: string | null;
  status: ImagingRecordStatus;
  requestedAt: string;
  resultedAt: string | null;
  orderingStaffName: string | null;
}

export interface ImagingRecordFileMeta {
  id: string;
  fileName: string;
  contentType: string | null;
  fileSize: number;
  uploadedAt: string;
}

export interface ImagingRecordDetail extends ImagingRecordSummary {
  findings: string | null;
  notes: string | null;
  files: ImagingRecordFileMeta[];
}

export interface RequestImagingRecordPayload {
  patientId: string;
  modality: ImagingModality;
  bodyRegion?: string;
  notes?: string;
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const imagingApi = {
  request: (payload: RequestImagingRecordPayload) =>
    apiClient.post<{ id: string; modality: ImagingModality }>('/api/v1/imaging-records', payload).then((r) => r.id),
  list: (patientId?: string) =>
    apiClient.get<ImagingRecordSummary[]>(`/api/v1/imaging-records${patientId ? `?patientId=${patientId}` : ''}`),
  get: (id: string) => apiClient.get<ImagingRecordDetail>(`/api/v1/imaging-records/${id}`),
  complete: (id: string, findings: string) => apiClient.put<void>(`/api/v1/imaging-records/${id}/complete`, { findings }),
  cancel: (id: string) => apiClient.post<void>(`/api/v1/imaging-records/${id}/cancel`),
  uploadFile: (id: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiClient.postMultipart<void>(`/api/v1/imaging-records/${id}/files`, form);
  },
  fileUrl: (fileId: string) => `${API_BASE_URL}/api/v1/imaging-records/files/${fileId}`,
  authHeader: () => apiClient.authHeader(),
};
