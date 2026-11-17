import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, refreshSession, setSessionExpiredHandler, tokenStore } from '../api/client';
import type { AuthResponse, Role } from '../types';

type User = { email: string; role: Role; permissions: string[] };
type Status = 'loading' | 'authenticated' | 'anonymous';

type AuthValue = {
  user: User | null;
  status: Status;
  login: (r: AuthResponse) => void;
  logout: () => Promise<void>;
  can: (permission: string) => boolean;
};

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<Status>('loading');

  const login = useCallback((r: AuthResponse) => {
    tokenStore.set(r.token);
    setUser({ email: r.email, role: r.role, permissions: r.permissions });
    setStatus('authenticated');
  }, []);

  const clearSession = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
    setStatus('anonymous');
  }, []);

  useEffect(() => {
    // v1 stored the token in localStorage; remove it so nothing sensitive lingers there.
    localStorage.removeItem('token');
    localStorage.removeItem('user');

    setSessionExpiredHandler(clearSession);
    // Restore the session after a page reload using the HttpOnly refresh cookie.
    refreshSession().then(login).catch(clearSession);
  }, [login, clearSession]);

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout');   // revokes the refresh token on the server + clears the cookie
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const can = useCallback((permission: string) => !!user?.permissions.includes(permission), [user]);

  return <AuthContext.Provider value={{ user, status, login, logout, can }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}
