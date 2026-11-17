import { ChartLine, LayoutDashboard, Users, type LucideIcon } from 'lucide-react';
import { PERMISSIONS, type Permission } from '@/lib/permissions';

export type NavItem = { to: string; label: string; icon: LucideIcon; end?: boolean; permission?: Permission };
export type NavSection = { label: string; items: NavItem[] };

/** Sidebar menu. Items with a permission are only shown to users who have it. */
export const NAV: NavSection[] = [
  {
    label: 'Learn',
    items: [
      { to: '/', label: 'My quizzes', icon: LayoutDashboard, end: true },
      { to: '/history', label: 'Progress', icon: ChartLine },
    ],
  },
  {
    label: 'Administration',
    items: [{ to: '/admin/users', label: 'Users', icon: Users, permission: PERMISSIONS.USER_READ }],
  },
];

/** Title shown in the top bar for the current path. */
export function titleFor(pathname: string): string {
  if (pathname.startsWith('/quiz/')) return 'Take quiz';
  for (const section of NAV) {
    for (const item of section.items) {
      if (item.end ? pathname === item.to : pathname.startsWith(item.to)) return item.label;
    }
  }
  return 'QuizForge';
}
