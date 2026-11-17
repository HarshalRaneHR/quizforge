import { CircleCheck, CircleX, EyeOff } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { formatPoints, TYPE_ICON, TYPE_LABEL } from '@/lib/questions';
import { cn } from '@/lib/utils';
import type { AttemptResult, QuestionResult } from '@/types';
import { QuestionInput } from './QuestionInput';

function scoreMessage(pct: number) {
  if (pct === 100) return 'Perfect score! 🎉';
  if (pct >= 80) return 'Great job! 🔥';
  if (pct >= 50) return 'Not bad. Review the explanations below to close the gaps.';
  return 'Keep practising. The explanations below will help.';
}

export function PointsBadge({ result, points }: { result?: QuestionResult; points: number }) {
  if (!result) return <Badge variant="outline">{points} {points === 1 ? 'pt' : 'pts'}</Badge>;
  const none = result.pointsAwarded === 0;
  return (
    <Badge variant="outline" className={cn(
      result.correct && 'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
      !result.correct && !none && 'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300',
      none && 'border-red-500/30 bg-red-500/10 text-red-700 dark:text-red-300',
    )}>
      {formatPoints(result.pointsAwarded)} / {formatPoints(result.maxPoints)} pts
    </Badge>
  );
}

/** Score banner + per-question review (correct answers, partial credit, explanations). */
export function AttemptResultView({ result }: { result: AttemptResult }) {
  const hasPassMark = result.passPercentage !== null && result.passPercentage !== undefined;
  return (
    <div className="space-y-4">
      <div role="status" className="relative overflow-hidden rounded-xl bg-linear-to-br from-violet-600 to-fuchsia-600 p-6 text-white shadow-lg">
        <div className="pointer-events-none absolute -top-16 -right-10 size-48 rounded-full bg-white/10 blur-2xl" />
        <div className="relative flex flex-wrap items-center gap-5">
          <div className="grid size-20 shrink-0 place-items-center rounded-full bg-white/15 text-2xl font-bold ring-4 ring-white/20">
            {result.percentage}%
          </div>
          <div className="flex-1">
            <p className="text-xl font-semibold">You scored {formatPoints(result.score)} / {formatPoints(result.maxScore)} points</p>
            <p className="text-white/85">{scoreMessage(result.percentage)}</p>
          </div>
          {hasPassMark && (
            <span className={cn('flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold',
              result.passed ? 'bg-emerald-400/25' : 'bg-red-400/25')}>
              {result.passed ? <CircleCheck className="size-4" /> : <CircleX className="size-4" />}
              {result.passed ? 'Passed' : 'Not passed'} · pass mark {result.passPercentage}%
            </span>
          )}
        </div>
      </div>

      {result.detailsHidden ? (
        <div className="flex items-center gap-3 rounded-lg border p-4 text-sm text-muted-foreground">
          <EyeOff className="size-4 shrink-0" /> This quiz shows your score only. Correct answers aren't revealed.
        </div>
      ) : result.results.map((r, i) => {
        const q = r.question;
        const Icon = TYPE_ICON[q.type];
        return (
          <Card key={q.id} className={cn(r.correct ? 'ring-1 ring-emerald-500/40' : r.pointsAwarded > 0 ? 'ring-1 ring-amber-500/40' : 'ring-1 ring-red-500/40')}>
            <CardHeader className="gap-2">
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span className="font-medium tracking-wide uppercase">Question {i + 1}</span>
                <Badge variant="secondary"><Icon /> {TYPE_LABEL[q.type]}</Badge>
                <span className="ml-auto"><PointsBadge result={r} points={q.points} /></span>
              </div>
              {q.type !== 'FILL_BLANK' && <h2 id={`q-${q.id}-prompt`} className="text-base leading-snug font-semibold sm:text-lg">{q.prompt}</h2>}
            </CardHeader>
            <CardContent className="space-y-4">
              <QuestionInput question={q} response={r.response ?? undefined} onChange={() => {}} result={r} />
              {r.explanation && (
                <p className={cn('rounded-lg border-l-4 bg-muted/60 px-4 py-3 text-sm',
                  r.correct ? 'border-emerald-500' : r.pointsAwarded > 0 ? 'border-amber-500' : 'border-red-500')}>
                  <strong>{r.correct ? 'Correct. ' : r.pointsAwarded > 0 ? 'Partly correct. ' : 'Incorrect. '}</strong>{r.explanation}
                </p>
              )}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
