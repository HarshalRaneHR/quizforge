import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ChartLine, CircleX, LoaderCircle, RotateCcw, Send } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { QuestionInput } from '@/components/quiz/QuestionInput';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import { formatPoints, initialResponse, isAnswered, TYPE_ICON, TYPE_LABEL, type Response } from '@/lib/questions';
import { cn } from '@/lib/utils';
import type { AttemptResult, QuestionResult, QuizDetail } from '@/types';

function scoreMessage(pct: number) {
  if (pct === 100) return 'Perfect score! 🎉';
  if (pct >= 80) return 'Great job! 🔥';
  if (pct >= 50) return 'Not bad. Review the explanations below to close the gaps.';
  return 'Keep practising. The explanations below will help.';
}

function PointsBadge({ result, points }: { result?: QuestionResult; points: number }) {
  if (!result) return <Badge variant="outline">{points} {points === 1 ? 'pt' : 'pts'}</Badge>;
  const full = result.correct;
  const none = result.pointsAwarded === 0;
  return (
    <Badge variant="outline" className={cn(
      full && 'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
      !full && !none && 'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300',
      none && 'border-red-500/30 bg-red-500/10 text-red-700 dark:text-red-300',
    )}>
      {formatPoints(result.pointsAwarded)} / {formatPoints(result.maxPoints)} pts
    </Badge>
  );
}

export default function TakeQuizPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [responses, setResponses] = useState<Record<number, Response | undefined>>({});
  const [touched, setTouched] = useState<Set<number>>(new Set());

  const quiz = useQuery({
    queryKey: ['quiz', id],
    queryFn: () => api.get<QuizDetail>(`/quizzes/${id}`).then(r => r.data),
  });

  const submit = useMutation({
    mutationFn: (body: { answers: { questionId: number; response: Response | undefined }[] }) =>
      api.post<AttemptResult>(`/quizzes/${id}/attempts`, body).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attempts'] });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
  });

  if (quiz.isPending) {
    return (
      <div className="space-y-4" aria-hidden="true">
        <Skeleton className="h-9 w-2/3" />
        <Skeleton className="h-2 w-full" />
        {Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-56 rounded-xl" />)}
      </div>
    );
  }
  if (quiz.isError) {
    return <EmptyState icon={CircleX} title="Quiz not available" description={errorMessage(quiz.error)}
                       action={<Button asChild variant="outline"><Link to="/">Back to my quizzes</Link></Button>} />;
  }

  const { questions } = quiz.data;
  const result = submit.data;
  const resultById = new Map(result?.results.map(r => [r.questionId, r]));
  const responseFor = (qId: number) => responses[qId] ?? initialResponse(
    questions.find(q => q.id === qId)!.type, questions.find(q => q.id === qId)!.content);
  const answeredCount = questions.filter(q => isAnswered(q.type, q.content, responseFor(q.id), touched.has(q.id))).length;

  const setResponse = (qId: number, r: Response) => {
    setResponses(prev => ({ ...prev, [qId]: r }));
    setTouched(prev => new Set(prev).add(qId));
  };

  const reset = () => {
    setResponses({});
    setTouched(new Set());
    submit.reset();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const onSubmit = () => submit.mutate({
    answers: questions.map(q => ({ questionId: q.id, response: responseFor(q.id) })),
  });

  return (
    <div className="space-y-6">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 text-muted-foreground">
          <Link to="/"><ArrowLeft /> My quizzes</Link>
        </Button>
        {quiz.data.status !== 'PUBLISHED' && (
          <p className="mb-3 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-sm text-amber-800 dark:text-amber-300">
            Preview: this quiz is {quiz.data.status.toLowerCase()}, so learners can't see it.
          </p>
        )}
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{quiz.data.title}</h1>
        {quiz.data.description && <p className="mt-1 text-muted-foreground">{quiz.data.description}</p>}
        <p className="mt-1 text-sm text-muted-foreground">{questions.length} questions · {quiz.data.totalPoints} points · by {quiz.data.author}</p>
        {!result && (
          <div className="mt-4 space-y-2">
            <Progress value={(answeredCount / questions.length) * 100} aria-label="Quiz progress" />
            <p className="text-sm text-muted-foreground">{answeredCount} of {questions.length} answered</p>
          </div>
        )}
      </div>

      {result && (
        <div role="status" className="relative overflow-hidden rounded-xl bg-linear-to-br from-violet-600 to-fuchsia-600 p-6 text-white shadow-lg">
          <div className="pointer-events-none absolute -top-16 -right-10 size-48 rounded-full bg-white/10 blur-2xl" />
          <div className="relative flex items-center gap-5">
            <div className="grid size-20 shrink-0 place-items-center rounded-full bg-white/15 text-2xl font-bold ring-4 ring-white/20">
              {result.percentage}%
            </div>
            <div>
              <p className="text-xl font-semibold">You scored {formatPoints(result.score)} / {formatPoints(result.maxScore)} points</p>
              <p className="text-white/85">{scoreMessage(result.percentage)}</p>
            </div>
          </div>
        </div>
      )}

      <form className="space-y-4" onSubmit={e => { e.preventDefault(); onSubmit(); }}>
        {questions.map((q, i) => {
          const r = resultById.get(q.id);
          const Icon = TYPE_ICON[q.type];
          return (
            <Card key={q.id} className={cn(r && (r.correct ? 'ring-1 ring-emerald-500/40' : r.pointsAwarded > 0 ? 'ring-1 ring-amber-500/40' : 'ring-1 ring-red-500/40'))}>
              <CardHeader className="gap-2">
                <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  <span className="font-medium tracking-wide uppercase">Question {i + 1} of {questions.length}</span>
                  <Badge variant="secondary"><Icon /> {TYPE_LABEL[q.type]}</Badge>
                  <span className="ml-auto"><PointsBadge result={r} points={q.points} /></span>
                </div>
                {q.type !== 'FILL_BLANK' && (
                  <h2 id={`q-${q.id}-prompt`} className="text-base leading-snug font-semibold sm:text-lg">{q.prompt}</h2>
                )}
              </CardHeader>
              <CardContent className="space-y-4">
                <QuestionInput question={q} response={responseFor(q.id)} onChange={resp => setResponse(q.id, resp)} result={r} />
                {r?.explanation && (
                  <p className={cn('rounded-lg border-l-4 bg-muted/60 px-4 py-3 text-sm',
                    r.correct ? 'border-emerald-500' : r.pointsAwarded > 0 ? 'border-amber-500' : 'border-red-500')}>
                    <strong>{r.correct ? 'Correct. ' : r.pointsAwarded > 0 ? 'Partly correct. ' : 'Incorrect. '}</strong>{r.explanation}
                  </p>
                )}
              </CardContent>
            </Card>
          );
        })}

        {submit.isError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{errorMessage(submit.error)}</p>
        )}

        <div className="flex flex-wrap items-center justify-end gap-3 pt-2">
          {result ? (
            <>
              <Button asChild variant="outline"><Link to="/history"><ChartLine /> View progress</Link></Button>
              <Button type="button" onClick={reset}><RotateCcw /> Try again</Button>
            </>
          ) : (
            <>
              {answeredCount < questions.length && (
                <span className="text-sm text-muted-foreground">{questions.length - answeredCount} unanswered (scored as 0)</span>
              )}
              <Button type="submit" size="lg" disabled={submit.isPending || answeredCount === 0}>
                {submit.isPending ? <LoaderCircle className="animate-spin" /> : <Send />}
                Submit answers
              </Button>
            </>
          )}
        </div>
      </form>
    </div>
  );
}
