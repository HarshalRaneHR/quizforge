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
export type QuizSettings = Schemas['Settings'];
export type SessionQuestion = Schemas['SessionQuestion'];
export type QuestionType = SessionQuestion['type'];
export type Difficulty = SessionQuestion['difficulty'];
export type AttemptSession = Schemas['AttemptSession'];
export type SavedAnswer = Schemas['SavedAnswer'];
export type QuestionResult = Schemas['QuestionResult'];
export type AttemptResult = Schemas['AttemptResult'];
export type AttemptSummary = Schemas['AttemptSummary'];
export type QuizStatus = QuizSummary['status'];
export type Visibility = QuizSummary['visibility'];
export type CatalogQuiz = Schemas['CatalogQuiz'];
export type JobView = Schemas['JobView'];
export type StudioQuiz = Schemas['StudioQuiz'];
export type AuthoringQuestion = Schemas['AuthoringQuestion'];
export type AuthoringQuestionView = Schemas['AuthoringQuestionView'];
export type MatchPair = Schemas['MatchPair'];
export type ClassroomSummary = Schemas['ClassroomSummary'];
export type ClassroomDetail = Schemas['ClassroomDetail'];
export type AssignmentView = Schemas['AssignmentView'];
export type MyAssignment = Schemas['MyAssignment'];
export type Gradebook = Schemas['Gradebook'];
export type Progress = MyAssignment['progress'];
export type MyAnalytics = Schemas['MyAnalytics'];
export type QuizAnalytics = Schemas['QuizAnalytics'];
export type PlatformStats = Schemas['PlatformStats'];

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
