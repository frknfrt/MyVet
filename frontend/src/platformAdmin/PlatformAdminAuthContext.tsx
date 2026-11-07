import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { platformAdminApi, PlatformAdminLoginPayload } from '../api/platformAdminApi';
import { setPlatformAdminUnauthorizedHandler } from './platformAdminClient';
import {
  clearStoredPlatformAdminSession,
  loadStoredPlatformAdminSession,
  PlatformAdminSession,
  storePlatformAdminSession,
} from './session';

interface PlatformAdminAuthContextValue {
  session: PlatformAdminSession | null;
  login: (payload: PlatformAdminLoginPayload) => Promise<PlatformAdminSession>;
  logout: () => void;
}

const PlatformAdminAuthContext = createContext<PlatformAdminAuthContextValue | undefined>(undefined);

export function PlatformAdminAuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<PlatformAdminSession | null>(() => loadStoredPlatformAdminSession());

  useEffect(() => {
    setPlatformAdminUnauthorizedHandler(() => {
      clearStoredPlatformAdminSession();
      setSession(null);
    });
    return () => setPlatformAdminUnauthorizedHandler(null);
  }, []);

  async function login(payload: PlatformAdminLoginPayload) {
    // Sunucu anahtari HttpOnly cereze yazar; yanittaki token alani saklanmaz.
    const result = await platformAdminApi.login(payload);
    storePlatformAdminSession(result);
    const stored = loadStoredPlatformAdminSession()!;
    setSession(stored);
    return stored;
  }

  function logout() {
    clearStoredPlatformAdminSession();
    setSession(null);
    // Cerezi sunucu siler; ag hatasinda da yerel oturum kapanmis olur, cerez en gec 8 saatte gecersizlesir.
    platformAdminApi.logout().catch(() => undefined);
  }

  return (
    <PlatformAdminAuthContext.Provider value={{ session, login, logout }}>
      {children}
    </PlatformAdminAuthContext.Provider>
  );
}

export function usePlatformAdminAuth() {
  const context = useContext(PlatformAdminAuthContext);
  if (!context) {
    throw new Error('usePlatformAdminAuth, PlatformAdminAuthProvider disinda cagrildi');
  }
  return context;
}
