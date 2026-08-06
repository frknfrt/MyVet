import { apiClient } from './client';

export type LabResultStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED';
export type LabValueFlag = 'NORMAL' | 'LOW' | 'HIGH' | 'ABNORMAL';

export interface LabResultSummary {
  id: string;
  patientId: string;
  patientName: string;
  ownerId: string;
  ownerFullName: string;
  testName: string;
  status: LabResultStatus;
  requestedAt: string;
  resultedAt: string | null;
  orderingStaffName: string | null;
}

export interface LabResultItem {
  id: string;
  parameterName: string;
  value: string;
  unit: string | null;
  referenceRange: string | null;
  flag: LabValueFlag | null;
}

export interface LabResultFileMeta {
  id: string;
  fileName: string;
  contentType: string | null;
  fileSize: number;
  uploadedAt: string;
}

export interface LabResultDetail extends LabResultSummary {
  resultSummary: string | null;
  notes: string | null;
  items: LabResultItem[];
  files: LabResultFileMeta[];
}

export interface RequestLabResultPayload {
  patientId: string;
  testName: string;
  notes?: string;
}

export interface LabResultItemInput {
  parameterName: string;
  value: string;
  unit?: string;
  referenceRange?: string;
  flag?: LabValueFlag;
}

export interface CompleteLabResultPayload {
  resultSummary: string;
  items: LabResultItemInput[];
}

export interface LabResultEvaluation {
  items: LabResultItemInput[];
  draftSummary: string;
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const labApi = {
  request: (payload: RequestLabResultPayload) =>
    apiClient.post<{ id: string; testName: string }>('/api/v1/lab-results', payload).then((r) => r.id),
  list: (patientId?: string) =>
    apiClient.get<LabResultSummary[]>(`/api/v1/lab-results${patientId ? `?patientId=${patientId}` : ''}`),
  get: (id: string) => apiClient.get<LabResultDetail>(`/api/v1/lab-results/${id}`),
  complete: (id: string, payload: CompleteLabResultPayload) =>
    apiClient.put<void>(`/api/v1/lab-results/${id}/complete`, payload),
  evaluate: (items: LabResultItemInput[]) =>
    apiClient.post<LabResultEvaluation>('/api/v1/lab-results/evaluate', { items }),
  cancel: (id: string) => apiClient.post<void>(`/api/v1/lab-results/${id}/cancel`),
  uploadFile: (id: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiClient.postMultipart<void>(`/api/v1/lab-results/${id}/files`, form);
  },
  fileUrl: (fileId: string) => `${API_BASE_URL}/api/v1/lab-results/files/${fileId}`,
  authHeader: () => apiClient.authHeader(),
};
