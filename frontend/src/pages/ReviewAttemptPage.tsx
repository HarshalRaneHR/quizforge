import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, CircleX, Play } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { AttemptResultView } from '@/components/quiz/AttemptResultView';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import type { AttemptResult } from '@/types';

/** Read-only review of a submitted attempt (linked from Progress). */
export default function ReviewAttemptPage() {
  const { id } = useParams();
  const attempt = useQuery({
    queryKey: ['attempt', id],
    queryFn: () => api.get<AttemptResult>(`/attempts/${id}`).then(r => r.data),
  });

  if (attempt.isPending) return <div className="space-y-4"><Skeleton className="h-9 w-2/3" /><Skeleton className="h-40 rounded-xl" /></div>;
  if (attempt.isError) {
    return <EmptyState icon={CircleX} title="Attempt not available" description={errorMessage(attempt.error)}
                       action={<Button asChild variant="outline"><Link to="/history">Back to progress</Link></Button>} />;
  }
  const a = attempt.data;
  return (
    <div className="space-y-6">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 text-muted-foreground"><Link to="/history"><ArrowLeft /> Progress</Link></Button>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{a.quizTitle}</h1>
            <p className="text-sm text-muted-foreground">Submitted {a.submittedAt ? new Date(a.submittedAt).toLocaleString() : ''}</p>
          </div>
          <Button asChild variant="outline"><Link to={`/quiz/${a.quizId}`}><Play /> Open quiz</Link></Button>
        </div>
      </div>
      <AttemptResultView result={a} />
    </div>
  );
}
