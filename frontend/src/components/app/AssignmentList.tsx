import { Link } from 'react-router-dom';
import { CalendarClock, CircleCheck, Play, RotateCcw } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import type { MyAssignment } from '@/types';

function DueLabel({ a }: { a: MyAssignment }) {
  if (!a.dueAt) return <span>No due date</span>;
  const due = new Date(a.dueAt);
  return (
    <span className={cn('flex items-center gap-1', a.overdue && 'font-medium text-red-600 dark:text-red-400')}>
      <CalendarClock className="size-3.5" aria-hidden="true" />
      {a.overdue ? 'Overdue · was due ' : 'Due '}{due.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
    </span>
  );
}

/** Quizzes assigned to the learner, with due date, progress and the right action. */
export function AssignmentList({ items, limit }: { items: MyAssignment[]; limit?: number }) {
  const shown = limit ? items.slice(0, limit) : items;
  return (
    <ul className="grid gap-2">
      {shown.map(a => (
        <li key={a.assignmentId} className={cn('flex flex-col gap-3 rounded-lg border p-3 sm:flex-row sm:items-center', a.overdue && 'border-red-500/40')}>
          <div className="min-w-0 flex-1 space-y-1">
            <p className="truncate font-medium">{a.quizTitle}</p>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
              <span>{a.classroomName}</span>
              <span>{a.questionCount} questions</span>
              <DueLabel a={a} />
            </div>
          </div>
          <div className="flex items-center gap-2">
            {a.progress === 'COMPLETED' && (
              <Badge variant="outline" className="border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300">
                <CircleCheck /> {a.bestPercentage}%
              </Badge>
            )}
            {a.progress === 'IN_PROGRESS' && <Badge variant="outline" className="border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300">In progress</Badge>}
            <Button asChild size="sm" variant={a.progress === 'COMPLETED' ? 'outline' : 'default'}>
              <Link to={`/quiz/${a.quizId}`}>
                {a.progress === 'COMPLETED' ? <><RotateCcw /> Open</> : a.progress === 'IN_PROGRESS' ? <><Play /> Resume</> : <><Play /> Start</>}
              </Link>
            </Button>
          </div>
        </li>
      ))}
    </ul>
  );
}
