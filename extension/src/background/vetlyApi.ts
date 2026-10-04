import type { ErrorCode } from '../shared/messages';
import type { ConfirmationMethod, ExtensionProfile, StockSnapshotLine, StockSystem, Submission } from '../shared/types';
import type { TokenStore } from './chromeStorage';

export class ApiError extends Error {
  constructor(public readonly code: ErrorCode, message: string) {
    super(message);
  }
}

interface Options {
  baseUrl: string;
  tokens: TokenStore;
  fetchFn?: typeof fetch;
}

export function createVetlyApi({ baseUrl, tokens, fetchFn = fetch }: Options) {
  async function request<T>(path: string, init: RequestInit = {}, auth = true): Promise<T> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (auth) {
      const token = await tokens.get();
      if (!token) throw new ApiError('UNAUTHORIZED', 'Eklenti bağlı değil');
      headers.Authorization = `Bearer ${token}`;
    }
    let response: Response;
    try {
      response = await fetchFn(`${baseUrl}${path}`, { ...init, headers });
    } catch {
      throw new ApiError('OFFLINE', "Vetly'ye ulaşılamıyor");
    }
    if (response.status === 401) {
      if (auth) await tokens.clear();
      throw new ApiError('UNAUTHORIZED', 'Bağlantı geçersiz ya da iptal edilmiş');
    }
    if (response.status === 404) throw new ApiError('NOT_FOUND', 'Kayıt bulunamadı');
    if (response.status === 409) throw new ApiError('CONFLICT', 'Kayıt bu işlem için uygun durumda değil');
    if (!response.ok) throw new ApiError('UNKNOWN', `Beklenmeyen yanıt: ${response.status}`);
    return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
  }

  return {
    async pair(code: string, label: string) {
      const { token } = await request<{ token: string }>(
        '/api/v1/tarbil-extension/pair',
        { method: 'POST', body: JSON.stringify({ code, label }) },
        false,
      );
      await tokens.set(token);
    },
    me: () => request<ExtensionProfile>('/api/v1/tarbil-extension/me'),
    listPending: () => request<Submission[]>('/api/v1/tarbil-extension/pending'),
    getSubmission: (id: string) => request<Submission>(`/api/v1/tarbil-extension/submissions/${id}`),
    getByVaccination: (vaccinationRecordId: string) =>
      request<Submission>(`/api/v1/tarbil-extension/submissions/by-vaccination/${vaccinationRecordId}`),
    markSubmitted: (id: string, method: ConfirmationMethod, tarbilReference: string | null) =>
      request<Submission>(`/api/v1/tarbil-extension/submissions/${id}/submitted`, {
        method: 'POST',
        body: JSON.stringify({ method, tarbilReference }),
      }),
    dismiss: (id: string, reason: string) =>
      request<void>(`/api/v1/tarbil-extension/submissions/${id}/dismiss`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      }),
    uploadStockSnapshot: (system: StockSystem, lines: StockSnapshotLine[]) =>
      request<{ snapshotId: string }>('/api/v1/tarbil-extension/stock-snapshots', {
        method: 'POST',
        body: JSON.stringify({ system, lines }),
      }),
  };
}

export type VetlyApi = ReturnType<typeof createVetlyApi>;
