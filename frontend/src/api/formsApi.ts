import { apiClient, API_BASE_URL } from './client';
import type { FormTemplateContent, FormValues } from '../forms/formModel';

export type FormTemplateStatus = 'ACTIVE' | 'ARCHIVED';
export type SignedFormStatus = 'VALID' | 'VOIDED';
export type SignerKind = 'OWNER' | 'OTHER';

export interface FormTemplateSummary {
  id: string;
  title: string;
  status: FormTemplateStatus;
  updatedAt: string;
  signedCount: number;
  hasSourceImage: boolean;
}

export interface FormTemplateDetail {
  id: string;
  title: string;
  status: FormTemplateStatus;
  content: FormTemplateContent;
  sourceImageId: string | null;
  clinicName: string;
  contentHash: string;
}

export interface FormTemplateDraft {
  aiJobId: string;
  modelConnected: boolean;
  content: FormTemplateContent | null;
  warnings: string[];
}

export interface SignerPayload {
  type: SignerKind;
  name: string | null;
  nationalId: string | null;
  phone: string | null;
}

export interface CreateSignedFormPayload {
  requestId: string;
  templateId: string;
  templateContentHash: string;
  patientId: string;
  encounterId: string | null;
  values: FormValues;
  signer: SignerPayload;
  /** HAM base64 -- "data:image/png;base64," oneki OLMAMALI (blobToBase64 kullan). */
  signaturePngBase64: string;
}

export interface SignedFormSummary {
  id: string;
  templateTitle: string;
  signedAt: string;
  signerName: string | null;
  signerType: SignerKind;
  status: SignedFormStatus;
}

export interface SignedFormDetail {
  id: string;
  templateTitle: string;
  /** Kayit bozulmussa null olabilir (integrityOk=false). */
  content: FormTemplateContent | null;
  values: FormValues | null;
  signer: SignerPayload;
  signedAt: string;
  signedByName: string | null;
  patientId: string;
  ownerId: string;
  encounterId: string | null;
  status: SignedFormStatus;
  voidReason: string | null;
  voidedAt: string | null;
  integrityOk: boolean;
}

export interface WriteBackCommand {
  ownerId: string;
  patientId: string;
  signedFormId: string;
  items: { binding: string; value: string }[];
}

export interface WriteBackResult {
  applied: string[];
  skipped: { binding: string; reason: string }[];
}

/** Blob'u HAM base64'e cevirir (data URL onekini atar). */
export function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error ?? new Error('Dosya okunamadı'));
    reader.onload = () => {
      const result = String(reader.result ?? '');
      const comma = result.indexOf(',');
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.readAsDataURL(blob);
  });
}

export const formsApi = {
  uploadSourceImage: (file: File | Blob) => {
    const form = new FormData();
    form.append('file', file);
    return apiClient.postMultipart<{ id: string }>('/api/v1/form-templates/source-images', form).then((r) => r.id);
  },
  extractDraft: (sourceImageId: string) =>
    apiClient.post<FormTemplateDraft>('/api/v1/ai/form-template-drafts', { sourceImageId }),
  createTemplate: (payload: { content: FormTemplateContent; sourceImageId?: string | null }) =>
    apiClient.post<{ id: string }>('/api/v1/form-templates', payload).then((r) => r.id),
  updateTemplate: (id: string, content: FormTemplateContent) =>
    apiClient.put<void>(`/api/v1/form-templates/${id}`, { content }),
  archiveTemplate: (id: string) => apiClient.post<void>(`/api/v1/form-templates/${id}/archive`),
  listTemplates: (status: 'ACTIVE' | 'ALL' = 'ACTIVE') =>
    apiClient.get<FormTemplateSummary[]>(`/api/v1/form-templates?status=${status}`),
  getTemplate: (id: string) => apiClient.get<FormTemplateDetail>(`/api/v1/form-templates/${id}`),
  sourceImageUrl: (id: string) => `${API_BASE_URL}/api/v1/form-templates/source-images/${id}`,
  templatePdfUrl: (id: string) => `${API_BASE_URL}/api/v1/form-templates/${id}/pdf`,

  createSignedForm: (payload: CreateSignedFormPayload) => apiClient.post<{ id: string }>('/api/v1/signed-forms', payload).then((r) => r.id),
  listSignedForms: (filter: { patientId: string; ownerId?: string } | { ownerId: string; patientId?: string }) => {
    const qs = new URLSearchParams();
    if (filter.patientId !== undefined) qs.set('patientId', filter.patientId);
    if (filter.ownerId !== undefined) qs.set('ownerId', filter.ownerId);
    return apiClient.get<SignedFormSummary[]>(`/api/v1/signed-forms?${qs}`);
  },
  getSignedForm: (id: string) => apiClient.get<SignedFormDetail>(`/api/v1/signed-forms/${id}`),
  signatureUrl: (id: string) => `${API_BASE_URL}/api/v1/signed-forms/${id}/signature`,
  signedFormPdfUrl: (id: string) => `${API_BASE_URL}/api/v1/signed-forms/${id}/pdf`,
  voidSignedForm: (id: string, reason: string) =>
    apiClient.post<void>(`/api/v1/signed-forms/${id}/void`, { reason }),
  applyWriteBacks: (cmd: WriteBackCommand) => apiClient.post<WriteBackResult>('/api/v1/form-write-backs', cmd),
  fetchFile: (url: string) => apiClient.fetchFile(url),
};
