import axios from 'axios';

export const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  response => response,
  error => {
    // Expired/invalid token anywhere except the login form itself → back to login.
    if (error.response?.status === 401 && !error.config?.url?.startsWith('/auth')) {
      localStorage.clear();
      window.location.href = '/login';
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
