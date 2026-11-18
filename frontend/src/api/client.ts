import axios, { type InternalAxiosRequestConfig } from 'axios';
import type { AuthResponse } from '@/types';

export const api = axios.create({ baseURL: '/api' });

// ---- Access token: kept in memory only (never localStorage), so injected scripts can't read it from storage.
let accessToken: string | null = null;
export const tokenStore = {
  get: () => accessToken,
  set: (token: string | null) => { accessToken = token; },
};

// ---- Refresh: the HttpOnly cookie is sent automatically; we never see the refresh token.
// Concurrent callers share one in-flight request, so a burst of 401s (or React StrictMode's double
// effects) causes exactly one rotation.
let inFlight: Promise<AuthResponse> | null = null;
export function refreshSession(): Promise<AuthResponse> {
  if (!inFlight) {
    inFlight = axios
      .post<AuthResponse>('/api/auth/refresh')
      .then(r => { accessToken = r.data.token; return r.data; })
      .finally(() => { inFlight = null; });
  }
  return inFlight;
}

// ---- Called when the session can't be renewed (AuthContext sets this to log the user out).
let sessionExpiredHandler: () => void = () => {};
export function setSessionExpiredHandler(handler: () => void) {
  sessionExpiredHandler = handler;
}

// ---- Called when the server says the user must set their own password first.
let passwordChangeRequiredHandler: () => void = () => {};
export function setPasswordChangeRequiredHandler(handler: () => void) {
  passwordChangeRequiredHandler = handler;
}

api.interceptors.request.use(config => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

// Each request is retried at most once after a refresh, to avoid infinite loops.
const retried = new WeakSet<InternalAxiosRequestConfig>();

api.interceptors.response.use(
  response => response,
  async error => {
    const original: InternalAxiosRequestConfig | undefined = error.config;
    const isAuthCall = original?.url?.startsWith('/auth/');
    if (error.response?.status === 401 && original && !isAuthCall && !retried.has(original)) {
      retried.add(original);
      try {
        await refreshSession();           // access token expired → get a new one silently
        return api(original);             // and replay the original request
      } catch {
        sessionExpiredHandler();          // refresh token expired/revoked → back to login
      }
    }
    if (error.response?.status === 403 && error.response.data?.code === 'PASSWORD_CHANGE_REQUIRED') {
      passwordChangeRequiredHandler();
    }
    return Promise.reject(error);
  },
);

/** Shape of Spring's ProblemDetail error body. */
type ProblemDetail = { detail?: string; errors?: Record<string, string> };

/** Turns a ProblemDetail error from Spring into a readable message. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ProblemDetail>(error)) {
    const data = error.response?.data;
    if (data?.errors) return Object.entries(data.errors).map(([field, msg]) => `${field} ${msg}`).join(', ');
    return data?.detail ?? error.message;
  }
  return 'Something went wrong';
}
