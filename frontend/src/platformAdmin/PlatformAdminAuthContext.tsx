import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { platformAdminApi, PlatformAdminLoginPayload } from '../api/platformAdminApi';
import { setPlatformAdminAuthToken, setPlatformAdminUnauthorizedHandler } from './platformAdminClient';
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
  const [session, setSession] = useState<PlatformAdminSession | null>(() => {
    const initial = loadStoredPlatformAdminSession();
    setPlatformAdminAuthToken(initial?.token ?? null);
    return initial;
  });

  useEffect(() => {
    setPlatformAdminUnauthorizedHandler(() => {
      clearStoredPlatformAdminSession();
      setPlatformAdminAuthToken(null);
      setSession(null);
    });
    return () => setPlatformAdminUnauthorizedHandler(null);
  }, []);

  async function login(payload: PlatformAdminLoginPayload) {
    const result = await platformAdminApi.login(payload);
    storePlatformAdminSession(result);
    setPlatformAdminAuthToken(result.token);
    setSession(result);
    return result;
  }

  function logout() {
    clearStoredPlatformAdminSession();
    setPlatformAdminAuthToken(null);
    setSession(null);
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
