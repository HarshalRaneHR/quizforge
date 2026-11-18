import { createContext, useContext } from 'react';
import type { Permission } from '@/lib/permissions';
import type { AuthResponse, Role } from '@/types';

export type AuthUser = {
  email: string;
  fullName?: string | null;
  role: Role;
  permissions: string[];
  /** Account created or reset by an admin: the person must set their own password first. */
  mustChangePassword: boolean;
};
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export type AuthValue = {
  user: AuthUser | null;
  status: AuthStatus;
  login: (r: AuthResponse) => void;
  logout: () => Promise<void>;
  /** Update the signed-in user's details in place (e.g. after changing their name). */
  updateUser: (patch: Partial<AuthUser>) => void;
  can: (permission: Permission) => boolean;
};

export const AuthContext = createContext<AuthValue | null>(null);

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}

/** Name to show for the signed-in user: full name if set, otherwise the email. */
export const displayName = (user: Pick<AuthUser, 'email' | 'fullName'>) => user.fullName?.trim() || user.email;
