import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, refreshSession, setPasswordChangeRequiredHandler, setSessionExpiredHandler, tokenStore } from '@/api/client';
import type { Permission } from '@/lib/permissions';
import type { AuthResponse } from '@/types';
import { AuthContext, type AuthStatus, type AuthUser } from './auth-context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');

  const login = useCallback((r: AuthResponse) => {
    tokenStore.set(r.token);
    setUser({ email: r.email, fullName: r.fullName, role: r.role, permissions: r.permissions, mustChangePassword: r.mustChangePassword });
    setStatus('authenticated');
  }, []);

  const clearSession = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
    setStatus('anonymous');
    queryClient.clear();   // the next user must never see the previous user's cached data
  }, [queryClient]);

  const updateUser = useCallback((patch: Partial<AuthUser>) => {
    setUser(current => (current ? { ...current, ...patch } : current));
  }, []);

  useEffect(() => {
    // v1 stored the token in localStorage; remove it so nothing sensitive lingers there.
    localStorage.removeItem('token');
    localStorage.removeItem('user');

    setSessionExpiredHandler(clearSession);
    // An admin reset the password while this tab was open: the router then shows "Set your password".
    setPasswordChangeRequiredHandler(() => updateUser({ mustChangePassword: true }));
    // Restore the session after a page reload using the HttpOnly refresh cookie.
    refreshSession().then(login).catch(clearSession);
  }, [login, clearSession, updateUser]);

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout');   // revokes the refresh token on the server + clears the cookie
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const can = useCallback((permission: Permission) => !!user?.permissions.includes(permission), [user]);

  const value = useMemo(() => ({ user, status, login, logout, updateUser, can }), [user, status, login, logout, updateUser, can]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
