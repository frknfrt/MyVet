import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { setUnauthorizedHandler } from '../api/client';
import { authApi, LoginPayload } from '../api/authApi';
import { AuthSession, clearStoredSession, loadStoredSession, storeSession } from './session';

interface AuthContextValue {
  session: AuthSession | null;
  login: (payload: LoginPayload) => Promise<AuthSession>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => loadStoredSession());

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearStoredSession();
      setSession(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  async function login(payload: LoginPayload) {
    // Sunucu anahtari HttpOnly cereze yazar; yanittaki token alani saklanmaz.
    const result = await authApi.login(payload);
    storeSession(result);
    const stored = loadStoredSession()!;
    setSession(stored);
    return stored;
  }

  function logout() {
    clearStoredSession();
    setSession(null);
    // Cerezi sunucu siler; ag hatasinda da yerel oturum kapanmis olur, cerez en gec 8 saatte gecersizlesir.
    authApi.logout().catch(() => undefined);
  }

  return (
    <AuthContext.Provider value={{ session, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth, AuthProvider disinda cagrildi');
  }
  return context;
}
