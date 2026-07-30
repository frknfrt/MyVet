export type StaffRole = 'VET' | 'TECHNICIAN' | 'RECEPTIONIST' | 'ADMIN' | 'OWNER_ACCOUNT';

export interface AuthSession {
  token: string;
  staffUserId: string;
  tenantId: string;
  branchId: string;
  fullName: string;
  role: StaffRole;
}

const STORAGE_KEY = 'myvet.session';

export function loadStoredSession(): AuthSession | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    return null;
  }
}

export function storeSession(session: AuthSession) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearStoredSession() {
  localStorage.removeItem(STORAGE_KEY);
}
