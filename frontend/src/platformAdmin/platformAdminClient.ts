import { ApiError } from '../api/client';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/**
 * Platform admin istemcisi: oturum anahtari HttpOnly cerezde (vetly_admin_session, Path=/api/v1/platform-admin)
 * durur, istekler onu otomatik tasir. Veri degistiren cerezli isteklerde sunucu CLIENT_HEADER'i ister (CSRF).
 * Klinik oturumundan ayri bir cerez oldugu icin ikisi ayni tarayicida birbirini ezmez.
 */
const CLIENT_HEADER = { 'X-Vetly-Client': 'web' };

let onUnauthorized: (() => void) | null = null;

export function setPlatformAdminUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...CLIENT_HEADER,
    ...(options.headers as Record<string, string> | undefined),
  };

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers, credentials: 'include' });

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


async function throwApiError(response: Response): Promise<never> {
  if (response.status === 401) {
    onUnauthorized?.();
  }
  const body = await response.json().catch(() => null);
  throw new ApiError(
    body?.message ?? 'Beklenmeyen bir hata olustu',
    body?.errorCode ?? 'UNKNOWN_ERROR',
    response.status,
    body?.fieldErrors ?? undefined
  );
}

/** Content-Type'i tarayici koyar (multipart sinir degeri icin); elle ayarlanmaz. */
async function requestMultipart<T>(path: string, form: FormData): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: { ...CLIENT_HEADER },
    body: form,
    credentials: 'include',
  });
  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.ok) {
    return throwApiError(response);
  }
  return (await response.json().catch(() => null)) as T;
}

async function requestBlob(path: string): Promise<Blob> {
  const response = await fetch(`${API_BASE_URL}${path}`, { method: 'GET', headers: { ...CLIENT_HEADER }, credentials: 'include' });
  if (!response.ok) {
    return throwApiError(response);
  }
  return response.blob();
}

export const platformAdminClient = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'POST', body: data !== undefined ? JSON.stringify(data) : undefined }),
  put: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'PUT', body: data !== undefined ? JSON.stringify(data) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  postMultipart: <T>(path: string, form: FormData) => requestMultipart<T>(path, form),
  getBlob: (path: string) => requestBlob(path),
};
