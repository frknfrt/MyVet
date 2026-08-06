import { apiClient } from './client';

export interface SoapDraft {
  subjective: string;
  objective: string;
  assessment: string;
  plan: string;
  modelConnected: boolean;
}

export const aiApi = {
  generateSoapDraft: (transcript: string) => apiClient.post<SoapDraft>('/api/v1/ai/soap-drafts', { transcript }),
};
