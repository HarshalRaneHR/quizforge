import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, CircleX, KeyRound, Play } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { QuestionInput } from '@/components/quiz/QuestionInput';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { asPerfectResult } from '@/lib/answer-key';
import { TYPE_ICON, TYPE_LABEL } from '@/lib/questions';
import type { AnswerKeyView } from '@/types';

/** Read-only answer key for authors and admins: every question with its correct answer. No attempt is created. */
export default function AnswerKeyPage() {
  const { id } = useParams();
  const key = useQuery({
    queryKey: ['answer-key', id],
    queryFn: () => api.get<AnswerKeyView>(`/quizzes/${id}/answer-key`).then(r => r.data),
  });

  if (key.isPending) {
    return <div className="space-y-4" aria-hidden="true"><Skeleton className="h-9 w-2/3" />{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-48 rounded-xl" />)}</div>;
  }
  if (key.isError) {
    return <EmptyState icon={CircleX} title="Answer key not available" description={errorMessage(key.error)}
                       action={<Button asChild variant="outline"><Link to={`/quiz/${id}`}>Back to the quiz</Link></Button>} />;
  }
  const k = key.data;

  return (
    <div className="space-y-6">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 text-muted-foreground">
          <Link to={`/quiz/${k.quizId}`}><ArrowLeft /> Back to the quiz</Link>
        </Button>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{k.title}</h1>
            <p className="mt-1 text-sm text-muted-foreground">{k.questions.length} questions · {k.totalPoints} points</p>
          </div>
          <Button asChild variant="outline"><Link to={`/quiz/${k.quizId}`}><Play /> Take the quiz instead</Link></Button>
        </div>
      </div>

      <div className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/5 p-4 text-sm">
        <KeyRound className="size-4 shrink-0 text-primary" aria-hidden="true" />
        <p>Answer key: correct answers are highlighted. This view is only for the quiz's author and admins, and doesn't count as an attempt.</p>
      </div>

      {k.questions.map((q, i) => {
        const Icon = TYPE_ICON[q.question.type];
        const result = asPerfectResult(q);
        return (
          <Card key={q.question.id}>
            <CardHeader className="gap-2">
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span className="font-medium tracking-wide uppercase">Question {i + 1} of {k.questions.length}</span>
                <Badge variant="secondary"><Icon /> {TYPE_LABEL[q.question.type]}</Badge>
                <Badge variant="outline" className="ml-auto">{q.question.points} {q.question.points === 1 ? 'pt' : 'pts'}</Badge>
              </div>
              {q.question.type !== 'FILL_BLANK' && (
                <h2 id={`q-${q.question.id}-prompt`} className="text-base leading-snug font-semibold sm:text-lg">{q.question.prompt}</h2>
              )}
            </CardHeader>
            <CardContent className="space-y-4">
              <QuestionInput question={q.question} response={result.response ?? undefined} onChange={() => {}} result={result} />
              {q.explanation && (
                <p className="rounded-lg border-l-4 border-emerald-500 bg-muted/60 px-4 py-3 text-sm"><strong>Why: </strong>{q.explanation}</p>
              )}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
