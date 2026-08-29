import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { setAuthToken, setUnauthorizedHandler } from '../api/client';
import { authApi, LoginPayload } from '../api/authApi';
import { AuthSession, clearStoredSession, loadStoredSession, storeSession } from './session';

interface AuthContextValue {
  session: AuthSession | null;
  login: (payload: LoginPayload) => Promise<AuthSession>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => {
    const initial = loadStoredSession();
    setAuthToken(initial?.token ?? null);
    return initial;
  });

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearStoredSession();
      setAuthToken(null);
      setSession(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  async function login(payload: LoginPayload) {
    const result = await authApi.login(payload);
    storeSession(result);
    setAuthToken(result.token);
    setSession(result);
    return result;
  }

  function logout() {
    clearStoredSession();
    setAuthToken(null);
    setSession(null);
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
