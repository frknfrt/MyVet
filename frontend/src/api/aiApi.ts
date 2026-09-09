import { apiClient } from './client';

export interface SoapDraft {
  subjective: string;
  objective: string;
  assessment: string;
  plan: string;
  modelConnected: boolean;
}

export interface TreatmentRecommendation {
  aiJobId: string;
  suggestionText: string;
  modelConnected: boolean;
}

export interface DiagnosisSuggestion {
  aiJobId: string;
  suggestionText: string;
  modelConnected: boolean;
}

export type TreatmentRecommendationDecisionStatus = 'ACCEPTED_AS_IS' | 'REJECTED';

export const aiApi = {
  generateSoapDraft: (transcript: string) => apiClient.post<SoapDraft>('/api/v1/ai/soap-drafts', { transcript }),
  generateTreatmentRecommendation: (encounterId: string) =>
    apiClient.post<TreatmentRecommendation>('/api/v1/ai/treatment-recommendations', { encounterId }),
  decideTreatmentRecommendation: (aiJobId: string, status: TreatmentRecommendationDecisionStatus, appliedContent?: string) =>
    apiClient.post<void>(`/api/v1/ai/treatment-recommendations/${aiJobId}/decision`, { status, appliedContent }),
  generateDiagnosisSuggestion: (encounterId: string) =>
    apiClient.post<DiagnosisSuggestion>('/api/v1/ai/diagnosis-suggestions', { encounterId }),
  decideDiagnosisSuggestion: (aiJobId: string, status: TreatmentRecommendationDecisionStatus, appliedContent?: string) =>
    apiClient.post<void>(`/api/v1/ai/diagnosis-suggestions/${aiJobId}/decision`, { status, appliedContent }),
};
