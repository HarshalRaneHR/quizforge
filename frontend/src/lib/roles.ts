import type { Role } from '@/types';

export const ROLE_LABELS: Record<Role, string> = { ADMIN: 'Admin', INSTRUCTOR: 'Instructor', LEARNER: 'Learner' };

export const ROLE_DESCRIPTIONS: Record<Role, string> = {
  LEARNER: 'Takes quizzes and makes practice quizzes',
  INSTRUCTOR: 'Creates and publishes quizzes, runs classrooms',
  ADMIN: 'Full access, including user management',
};
