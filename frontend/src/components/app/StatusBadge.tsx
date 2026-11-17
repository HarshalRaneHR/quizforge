import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { QuizStatus } from '@/types';

const STYLES: Record<QuizStatus, string> = {
  DRAFT: 'border-amber-500/25 bg-amber-500/10 text-amber-700 dark:text-amber-300',
  PUBLISHED: 'border-emerald-500/25 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
  ARCHIVED: 'border-zinc-500/25 bg-zinc-500/10 text-zinc-600 dark:text-zinc-300',
};
const LABELS: Record<QuizStatus, string> = { DRAFT: 'Draft', PUBLISHED: 'Published', ARCHIVED: 'Archived' };

export function StatusBadge({ status, className }: { status: QuizStatus; className?: string }) {
  return <Badge variant="outline" className={cn(STYLES[status], className)}>{LABELS[status]}</Badge>;
}
