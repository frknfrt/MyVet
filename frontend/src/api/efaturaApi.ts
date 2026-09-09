import { apiClient } from './client';

export type EInvoiceDocumentType = 'E_ARSIV' | 'E_FATURA';
export type EInvoiceSubmissionStatus = 'PENDING' | 'PROCESSING' | 'SUBMITTED' | 'FAILED';

export interface EInvoiceStatus {
  pendingCount: number;
  submittedCount: number;
  failedCount: number;
  lastSubmittedAt: string | null;
  connected: boolean;
}

export interface EInvoiceSubmission {
  id: string;
  invoiceId: string;
  ownerName: string;
  documentType: EInvoiceDocumentType;
  status: EInvoiceSubmissionStatus;
  gibReference: string | null;
  failureReason: string | null;
  attemptedAt: string;
}

export const efaturaApi = {
  status: () => apiClient.get<EInvoiceStatus>('/api/v1/efatura/status'),
  submissions: () => apiClient.get<EInvoiceSubmission[]>('/api/v1/efatura/submissions'),
  retry: (id: string) => apiClient.post<void>(`/api/v1/efatura/submissions/${id}/retry`),
};
