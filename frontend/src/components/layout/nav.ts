import { ChartLine, Compass, House, PencilRuler, Users, type LucideIcon } from 'lucide-react';
import { PERMISSIONS, type Permission } from '@/lib/permissions';

export type NavItem = { to: string; label: string; icon: LucideIcon; end?: boolean; permission?: Permission };
export type NavSection = { label: string; items: NavItem[] };

/** Sidebar menu. Items with a permission are only shown to users who have it (sections with none left are hidden). */
export const NAV: NavSection[] = [
  {
    label: 'Learn',
    items: [
      { to: '/', label: 'Home', icon: House, end: true },
      { to: '/explore', label: 'Explore', icon: Compass },
      { to: '/history', label: 'Progress', icon: ChartLine },
    ],
  },
  {
    label: 'Teach',
    items: [{ to: '/studio', label: 'Studio', icon: PencilRuler, permission: PERMISSIONS.QUIZ_AUTHOR }],
  },
  {
    label: 'Administration',
    items: [{ to: '/admin/users', label: 'Users', icon: Users, permission: PERMISSIONS.USER_READ }],
  },
];

/** Title shown in the top bar for the current path. */
export function titleFor(pathname: string): string {
  if (pathname.startsWith('/quiz/')) return 'Take quiz';
  if (pathname.startsWith('/studio/')) return 'Quiz builder';
  for (const section of NAV) {
    for (const item of section.items) {
      if (item.end ? pathname === item.to : pathname.startsWith(item.to)) return item.label;
    }
  }
  return 'QuizForge';
}
