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

let authToken: string | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
}

let onUnauthorized: (() => void) | null = null;

/** Sunucu 401 dondurdugunde (token suresi dolmus/gecersiz) cagrilacak handler'i kaydeder. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });

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
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`;
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers,
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

  const location = response.headers.get('Location') ?? '';
  const id = location.split('/').pop();
  if (!id) {
    throw new Error(`Sunucu Location header'i doenmedi: ${path}`);
  }
  return id;
}

async function requestMultipart<T>(path: string, form: FormData): Promise<T> {
  const headers: Record<string, string> = {};
  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { method: 'POST', headers, body: form });

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

export const apiClient = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'POST', body: data !== undefined ? JSON.stringify(data) : undefined }),
  put: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'PUT', body: data !== undefined ? JSON.stringify(data) : undefined }),
  /** 201 + Location header donen (govdesiz) uc noktalar icin -- olusturulan kaynagin id'sini doner. */
  postForId: (path: string, data?: unknown) => requestForLocationId(path, data),
  /** multipart/form-data govde gonderir (dosya yukleme uc noktalari icin). */
  postMultipart: <T>(path: string, form: FormData) => requestMultipart<T>(path, form),
  authHeader: (): Record<string, string> => (authToken ? { Authorization: `Bearer ${authToken}` } : {}),
};
