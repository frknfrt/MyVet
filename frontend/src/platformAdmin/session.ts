export interface PlatformAdminSession {
  token: string;
  platformAdminId: string;
  email: string;
  fullName: string;
}

const STORAGE_KEY = 'myvet.platformAdminSession';

export function loadStoredPlatformAdminSession(): PlatformAdminSession | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as PlatformAdminSession;
  } catch {
    return null;
  }
}

export function storePlatformAdminSession(session: PlatformAdminSession) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearStoredPlatformAdminSession() {
  localStorage.removeItem(STORAGE_KEY);
}
