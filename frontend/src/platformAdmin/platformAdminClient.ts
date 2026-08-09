import { ApiError } from '../api/client';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/**
 * apiClient (api/client.ts) tek bir modul-seviyesi authToken degiskeni
 * kullaniyor -- klinik ve platform admin ayni istemciyi paylasirsa biri
 * girince digerinin token'i sessizce ezilir. Bu yuzden platform admin
 * tamamen bagimsiz bir fetch istemcisi kullanir.
 */
let platformAdminAuthToken: string | null = null;

export function setPlatformAdminAuthToken(token: string | null) {
  platformAdminAuthToken = token;
}

let onUnauthorized: (() => void) | null = null;

export function setPlatformAdminUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  if (platformAdminAuthToken) {
    headers.Authorization = `Bearer ${platformAdminAuthToken}`;
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

export const platformAdminClient = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'POST', body: data !== undefined ? JSON.stringify(data) : undefined }),
  put: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'PUT', body: data !== undefined ? JSON.stringify(data) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
