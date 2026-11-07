/**
 * Tarayicida tutulan platform admin oturum bilgisi: yalniz gorunen kimlik. Oturum anahtari (JWT) BURADA YOK;
 * HttpOnly cerezde (vetly_admin_session) durur ve JavaScript'e hic gelmez (2026-11-06).
 */
export interface PlatformAdminSession {
  platformAdminId: string;
  email: string;
  fullName: string;
}

const STORAGE_KEY = 'myvet.platformAdminSession';

function withoutToken(raw: PlatformAdminSession & { token?: unknown }): PlatformAdminSession {
  const { platformAdminId, email, fullName } = raw;
  return { platformAdminId, email, fullName };
}

export function loadStoredPlatformAdminSession(): PlatformAdminSession | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as PlatformAdminSession & { token?: unknown };
    // Eski surumden kalan anahtar yerel depodan silinir; cerez yoksa ilk istek 401 alir ve giris istenir.
    if ('token' in parsed) storePlatformAdminSession(parsed);
    return withoutToken(parsed);
  } catch {
    return null;
  }
}

export function storePlatformAdminSession(session: PlatformAdminSession & { token?: unknown }) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(withoutToken(session)));
}

export function clearStoredPlatformAdminSession() {
  localStorage.removeItem(STORAGE_KEY);
}
