/** Mirrors backend Permission.java. The UI uses these to show/hide features; the backend enforces them. */
export const PERMISSIONS = {
  QUIZ_GENERATE: 'quiz:generate',
  QUIZ_AUTHOR: 'quiz:author',
  QUIZ_MANAGE_ALL: 'quiz:manage-all',
  CLASSROOM_MANAGE: 'classroom:manage',
  USER_READ: 'user:read',
  USER_MANAGE: 'user:manage',
} as const;

export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];
