import type { PermissionName, Role } from '@/types';

/** Human wording for the abilities an admin can switch on or off per user. */
export const PERMISSION_INFO: Partial<Record<PermissionName, { label: string; description: string }>> = {
  QUIZ_GENERATE: { label: 'Generate practice quizzes with AI', description: 'Paste notes and get a private quiz (limited per hour).' },
  QUIZ_AUTHOR: { label: 'Create and publish quizzes', description: 'Use the Studio to build quizzes and publish them in Explore.' },
  CLASSROOM_MANAGE: { label: 'Run classrooms', description: 'Create classes, assign quizzes and see the gradebook.' },
};

export const permissionLabel = (p: PermissionName) => PERMISSION_INFO[p]?.label ?? p;

/** Default adjustable permissions for a role, from the server's access model. */
export function defaultsFor(model: { roles: { role: Role; defaultPermissions: PermissionName[] }[]; adjustable: PermissionName[] } | undefined, role: Role): PermissionName[] {
  const defaults = model?.roles.find(r => r.role === role)?.defaultPermissions ?? [];
  return defaults.filter(p => model?.adjustable.includes(p));
}
