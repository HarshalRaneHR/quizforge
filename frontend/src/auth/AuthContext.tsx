import { createContext, useContext, useState, type ReactNode } from 'react';
import type { AuthResponse, Role } from '../types';

type User = { email: string; role: Role } | null;
type AuthValue = { user: User; login: (r: AuthResponse) => void; logout: () => void };

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User>(() => {
    const saved = localStorage.getItem('user');
    return saved ? JSON.parse(saved) : null;
  });

  const login = (r: AuthResponse) => {
    const next = { email: r.email, role: r.role };
    localStorage.setItem('token', r.token);
    localStorage.setItem('user', JSON.stringify(next));
    setUser(next);
  };

  const logout = () => {
    localStorage.clear();
    setUser(null);
  };

  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}
