import { createContext, useContext } from 'react';
import type { Permission } from '@/lib/permissions';
import type { AuthResponse, Role } from '@/types';

export type AuthUser = { email: string; role: Role; permissions: string[] };
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export type AuthValue = {
  user: AuthUser | null;
  status: AuthStatus;
  login: (r: AuthResponse) => void;
  logout: () => Promise<void>;
  can: (permission: Permission) => boolean;
};

export const AuthContext = createContext<AuthValue | null>(null);

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}
