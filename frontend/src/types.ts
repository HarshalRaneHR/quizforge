// Mirrors the backend DTOs.
export type Role = 'LEARNER' | 'INSTRUCTOR' | 'ADMIN';
export type AuthResponse = {
  token: string;
  expiresInSeconds: number;
  email: string;
  role: Role;
  permissions: string[];
};

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export type GenerateQuizRequest = { title?: string; sourceText: string; questionCount: number };
export type QuizSummary = { id: number; title: string; questionCount: number; createdAt: string };
export type QuestionView = { id: number; prompt: string; options: string[] };
export type QuizDetail = { id: number; title: string; questions: QuestionView[] };

export type QuestionResult = { questionId: number; chosen: number; correct: number; isCorrect: boolean; explanation: string };
export type AttemptResult = { attemptId: number; score: number; total: number; results: QuestionResult[] };
export type AttemptSummary = { id: number; quizId: number; quizTitle: string; score: number; total: number; createdAt: string };
