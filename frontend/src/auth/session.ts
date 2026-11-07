export type StaffRole = 'VET' | 'TECHNICIAN' | 'RECEPTIONIST' | 'ADMIN' | 'OWNER_ACCOUNT';

/**
 * Tarayicida tutulan oturum bilgisi: yalniz ekranda gosterilen kimlik ve rol. Oturum anahtari (JWT) BURADA YOK;
 * HttpOnly cerezde durur ve JavaScript'e hic gelmez (2026-11-06).
 */
export interface AuthSession {
  staffUserId: string;
  tenantId: string;
  branchId: string;
  fullName: string;
  role: StaffRole;
}

const STORAGE_KEY = 'myvet.session';

function withoutToken(raw: AuthSession & { token?: unknown }): AuthSession {
  const { staffUserId, tenantId, branchId, fullName, role } = raw;
  return { staffUserId, tenantId, branchId, fullName, role };
}

export function loadStoredSession(): AuthSession | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as AuthSession & { token?: unknown };
    // Eski surumden kalan anahtar yerel depodan silinir; oturum cerezi yoksa ilk istek 401 alir ve giris istenir.
    if ('token' in parsed) storeSession(parsed);
    return withoutToken(parsed);
  } catch {
    return null;
  }
}

export function storeSession(session: AuthSession & { token?: unknown }) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(withoutToken(session)));
}

export function clearStoredSession() {
  localStorage.removeItem(STORAGE_KEY);
}
