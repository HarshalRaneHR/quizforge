import { Badge } from '@/components/ui/badge';
import { ROLE_LABELS } from '@/lib/roles';
import { cn } from '@/lib/utils';
import type { Role } from '@/types';

const STYLES: Record<Role, string> = {
  ADMIN: 'border-rose-500/25 bg-rose-500/10 text-rose-700 dark:text-rose-300',
  INSTRUCTOR: 'border-sky-500/25 bg-sky-500/10 text-sky-700 dark:text-sky-300',
  LEARNER: 'border-emerald-500/25 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
};

export function RoleBadge({ role, className }: { role: Role; className?: string }) {
  return (
    <Badge variant="outline" className={cn(STYLES[role], className)}>
      {ROLE_LABELS[role]}
    </Badge>
  );
}
