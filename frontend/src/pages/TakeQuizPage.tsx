import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ChartLine, CircleCheck, CircleX, LoaderCircle, RotateCcw, Send } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Progress } from '@/components/ui/progress';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';
import type { AttemptResult, QuizDetail } from '@/types';

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];

function scoreMessage(pct: number) {
  if (pct === 100) return 'Perfect score! 🎉';
  if (pct >= 80) return 'Great job! 🔥';
  if (pct >= 50) return 'Not bad. Review the explanations below to close the gaps.';
  return 'Keep practising. The explanations below will help.';
}

type OptionState = 'correct' | 'wrong' | 'idle';

export default function TakeQuizPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [answers, setAnswers] = useState<Record<number, number>>({});

  const quiz = useQuery({
    queryKey: ['quiz', id],
    queryFn: () => api.get<QuizDetail>(`/quizzes/${id}`).then(r => r.data),
  });

  const submit = useMutation({
    mutationFn: (body: { answers: number[] }) =>
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
  const answered = Object.keys(answers).length;
  const allAnswered = questions.every((_, i) => answers[i] !== undefined);
  const pct = result ? Math.round((result.score / result.total) * 100) : 0;

  const reset = () => {
    setAnswers({});
    submit.reset();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="space-y-6">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 text-muted-foreground">
          <Link to="/"><ArrowLeft /> My quizzes</Link>
        </Button>
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{quiz.data.title}</h1>
        {!result && (
          <div className="mt-4 space-y-2">
            <Progress value={(answered / questions.length) * 100} aria-label="Quiz progress" />
            <p className="text-sm text-muted-foreground">{answered} of {questions.length} answered</p>
          </div>
        )}
      </div>

      {result && (
        <div role="status"
             className="relative overflow-hidden rounded-xl bg-linear-to-br from-violet-600 to-fuchsia-600 p-6 text-white shadow-lg">
          <div className="pointer-events-none absolute -top-16 -right-10 size-48 rounded-full bg-white/10 blur-2xl" />
          <div className="relative flex items-center gap-5">
            <div className="grid size-20 shrink-0 place-items-center rounded-full bg-white/15 text-2xl font-bold ring-4 ring-white/20">
              {pct}%
            </div>
            <div>
              <p className="text-xl font-semibold">You scored {result.score} / {result.total}</p>
              <p className="text-white/85">{scoreMessage(pct)}</p>
            </div>
          </div>
        </div>
      )}

      <form className="space-y-4" onSubmit={e => { e.preventDefault(); submit.mutate({ answers: questions.map((_, i) => answers[i]) }); }}>
        {questions.map((q, i) => {
          const r = result?.results[i];
          return (
            <Card key={q.id} className={cn(r && (r.isCorrect ? 'ring-1 ring-emerald-500/40' : 'ring-1 ring-red-500/40'))}>
              <CardHeader>
                <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Question {i + 1} of {questions.length}</p>
                <h2 id={`q-${q.id}-prompt`} className="text-base leading-snug font-semibold sm:text-lg">{q.prompt}</h2>
              </CardHeader>
              <CardContent className="space-y-4">
                <RadioGroup aria-labelledby={`q-${q.id}-prompt`} disabled={!!result}
                            value={answers[i] === undefined ? '' : String(answers[i])}
                            onValueChange={v => setAnswers({ ...answers, [i]: Number(v) })}>
                  {q.options.map((option, j) => {
                    const state: OptionState = r ? (j === r.correct ? 'correct' : j === r.chosen ? 'wrong' : 'idle') : 'idle';
                    const optionId = `q-${q.id}-o-${j}`;
                    return (
                      <Label key={j} htmlFor={optionId}
                             className={cn(
                               'flex items-center gap-3 rounded-lg border p-3 font-normal transition-colors',
                               !result && 'cursor-pointer hover:bg-accent/60 has-data-[state=checked]:border-primary has-data-[state=checked]:bg-accent',
                               state === 'correct' && 'border-emerald-500 bg-emerald-500/10',
                               state === 'wrong' && 'border-red-500 bg-red-500/10',
                             )}>
                        <RadioGroupItem value={String(j)} id={optionId} />
                        <span className={cn(
                          'grid size-6 shrink-0 place-items-center rounded-md border text-xs font-semibold text-muted-foreground',
                          state === 'correct' && 'border-emerald-500 bg-emerald-500 text-white',
                          state === 'wrong' && 'border-red-500 bg-red-500 text-white',
                        )} aria-hidden="true">{LETTERS[j]}</span>
                        <span className="flex-1">{option}</span>
                        {state === 'correct' && <span className="flex items-center gap-1 text-xs font-semibold text-emerald-700 dark:text-emerald-400"><CircleCheck className="size-4" /> Correct</span>}
                        {state === 'wrong' && <span className="flex items-center gap-1 text-xs font-semibold text-red-700 dark:text-red-400"><CircleX className="size-4" /> Your answer</span>}
                      </Label>
                    );
                  })}
                </RadioGroup>
                {r && (
                  <p className={cn('rounded-lg border-l-4 bg-muted/60 px-4 py-3 text-sm', r.isCorrect ? 'border-emerald-500' : 'border-red-500')}>
                    <strong>{r.isCorrect ? 'Correct. ' : 'Incorrect. '}</strong>{r.explanation}
                  </p>
                )}
              </CardContent>
            </Card>
          );
        })}

        {submit.isError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{errorMessage(submit.error)}</p>
        )}

        <div className="flex flex-wrap justify-end gap-2 pt-2">
          {result ? (
            <>
              <Button asChild variant="outline"><Link to="/history"><ChartLine /> View progress</Link></Button>
              <Button type="button" onClick={reset}><RotateCcw /> Try again</Button>
            </>
          ) : (
            <Button type="submit" size="lg" disabled={!allAnswered || submit.isPending}>
              {submit.isPending ? <LoaderCircle className="animate-spin" /> : <Send />}
              Submit answers
            </Button>
          )}
        </div>
      </form>
    </div>
  );
}
