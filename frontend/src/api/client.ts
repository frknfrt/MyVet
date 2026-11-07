const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export interface ApiFieldError {
  field: string;
  message: string;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly errorCode: string,
    public readonly status: number,
    public readonly fieldErrors?: ApiFieldError[]
  ) {
    super(message);
  }
}

/**
 * Oturum anahtari HttpOnly cerezde (vetly_session) durur; JavaScript ona erisemez (2026-11-06). Istekler cerezi
 * otomatik tasir (credentials: 'include'); veri degistiren cerezli isteklerde sunucu bu basligi ister (CSRF).
 * Herkese acik uclara cerez gonderilmez: o uclar vitrin sitesine de acik ve cerezsiz CORS kullanir.
 */
const CLIENT_HEADER = { 'X-Vetly-Client': 'web' };

function credentialsFor(path: string): RequestCredentials {
  return path.startsWith('/api/v1/public/') ? 'omit' : 'include';
}

let onUnauthorized: (() => void) | null = null;

/** Sunucu 401 dondurdugunde (token suresi dolmus/gecersiz) cagrilacak handler'i kaydeder. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...CLIENT_HEADER,
    ...(options.headers as Record<string, string> | undefined),
  };

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers, credentials: credentialsFor(path) });

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 401) {
      onUnauthorized?.();
    }
    throw new ApiError(
      body?.message ?? 'Beklenmeyen bir hata olustu',
      body?.errorCode ?? 'UNKNOWN_ERROR',
      response.status,
      body?.fieldErrors ?? undefined
    );
  }

  return body as T;
}

async function requestForLocationId(path: string, data?: unknown): Promise<string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', ...CLIENT_HEADER };
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers,
    credentials: credentialsFor(path),
    body: data !== undefined ? JSON.stringify(data) : undefined,
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    if (response.status === 401) {
      onUnauthorized?.();
    }
    throw new ApiError(
      body?.message ?? 'Beklenmeyen bir hata olustu',
      body?.errorCode ?? 'UNKNOWN_ERROR',
      response.status,
      body?.fieldErrors ?? undefined
    );
  }

  return locationIdOf(response, path);
}

/** 201 yanitinin Location header'indaki son parca (olusturulan kaynagin id'si). */
function locationIdOf(response: Response, path: string): string {
  const location = response.headers.get('Location') ?? '';
  const id = location.split('/').pop();
  if (!id) {
    throw new Error(`Sunucu Location header'i doenmedi: ${path}`);
  }
  return id;
}

/** multipart govdeyi gonderir; basarisiz yanitta ApiError firlatir, basarili yaniti oldugu gibi doner. */
async function sendMultipart(path: string, form: FormData, method: 'POST' | 'PUT'): Promise<Response> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: { ...CLIENT_HEADER },
    body: form,
    credentials: credentialsFor(path),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    if (response.status === 401) {
      onUnauthorized?.();
    }
    throw new ApiError(
      body?.message ?? 'Beklenmeyen bir hata olustu',
      body?.errorCode ?? 'UNKNOWN_ERROR',
      response.status,
      body?.fieldErrors ?? undefined
    );
  }

  return response;
}

async function requestMultipart<T>(path: string, form: FormData, method: 'POST' | 'PUT' = 'POST'): Promise<T> {
  const response = await sendMultipart(path, form, method);
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json().catch(() => null)) as T;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'POST', body: data !== undefined ? JSON.stringify(data) : undefined }),
  put: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'PUT', body: data !== undefined ? JSON.stringify(data) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  /** 201 + Location header donen (govdesiz) uc noktalar icin -- olusturulan kaynagin id'sini doner. */
  postForId: (path: string, data?: unknown) => requestForLocationId(path, data),
  /** multipart/form-data govde gonderir (dosya yukleme uc noktalari icin). */
  postMultipart: <T>(path: string, form: FormData) => requestMultipart<T>(path, form),
  /** multipart, 201 + Location donen uclar icin -- olusturulan kaynagin (ornegin dosyanin) id'sini doner. */
  postMultipartForId: async (path: string, form: FormData): Promise<string> =>
    locationIdOf(await sendMultipart(path, form, 'POST'), path),
  /** Ayni, ama PUT ile (ornegin hasta fotografi). */
  putMultipart: <T>(path: string, form: FormData) => requestMultipart<T>(path, form, 'PUT'),
  /** Dosya/PDF indirme: tam adresle, oturum cereziyle GET. 401'de oturum kapatilir. */
  fetchFile: async (url: string): Promise<Response> => {
    const res = await fetch(url, { headers: { ...CLIENT_HEADER }, credentials: 'include' });
    if (res.status === 401) onUnauthorized?.();
    return res;
  },
};
