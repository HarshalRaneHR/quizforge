// API types are generated from the backend's OpenAPI spec (npm run gen:api → src/api/schema.d.ts),
// so frontend and backend can't silently drift apart. This file only gives them short names.
import type { components } from '@/api/schema';

type Schemas = components['schemas'];

export type Role = 'LEARNER' | 'INSTRUCTOR' | 'ADMIN';
export const ROLES: Role[] = ['LEARNER', 'INSTRUCTOR', 'ADMIN'];

/** The backend sends role as a string; narrow it to the known values. */
type WithRole<T> = Omit<T, 'role'> & { role: Role };

export type AuthResponse = WithRole<Schemas['AuthResponse']>;
export type UserView = WithRole<Schemas['UserView']>;

export type GenerateQuizRequest = Schemas['GenerateQuizRequest'];
export type QuizSummary = Schemas['QuizSummary'];
export type QuizDetail = Schemas['QuizDetail'];
export type AttemptResult = Schemas['AttemptResult'];
export type AttemptSummary = Schemas['AttemptSummary'];

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
