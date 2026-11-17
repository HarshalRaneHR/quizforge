import { CircleCheck, CircleX, LoaderCircle, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { cn } from '@/lib/utils';
import type { JobView } from '@/types';

/** Live status of a background AI generation job. */
export function GenerationProgress({ job, onDismiss, doneText = 'Taking you there…' }: {
  job: JobView | undefined; onDismiss?: () => void; doneText?: string;
}) {
  if (!job) {
    return (
      <div className="flex items-center gap-3 rounded-xl border p-4 text-sm" role="status">
        <LoaderCircle className="size-4 animate-spin" /> Starting generation…
      </div>
    );
  }
  const pct = job.totalSteps ? Math.round((job.doneSteps / job.totalSteps) * 100) : 0;
  const failed = job.status === 'FAILED';
  const done = job.status === 'SUCCEEDED';

  return (
    <div role="status" aria-live="polite"
         className={cn('space-y-3 rounded-xl border p-4', failed && 'border-destructive/40 bg-destructive/5', done && 'border-emerald-500/40 bg-emerald-500/5')}>
      <div className="flex items-center gap-3">
        {failed ? <CircleX className="size-5 text-destructive" /> : done ? <CircleCheck className="size-5 text-emerald-600" /> : <Sparkles className="size-5 animate-pulse text-primary" />}
        <div className="flex-1 text-sm">
          <p className="font-medium">
            {failed ? 'Generation failed' : done ? `Done: ${job.questionCount} questions created` : 'Generating questions…'}
          </p>
          <p className="text-muted-foreground">
            {failed ? job.error : done ? doneText
              : `Step ${Math.min(job.doneSteps + 1, job.totalSteps)} of ${job.totalSteps}: one AI call per question type. You can keep this page open.`}
          </p>
        </div>
        {(failed || done) && onDismiss && <Button variant="outline" size="sm" onClick={onDismiss}>Dismiss</Button>}
      </div>
      {!failed && <Progress value={done ? 100 : Math.max(5, pct)} aria-label="Generation progress" />}
    </div>
  );
}
