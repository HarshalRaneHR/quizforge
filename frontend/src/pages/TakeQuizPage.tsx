import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft, ChartLine, CircleX, Cloud, CloudOff, Copy, Eye, Flag, KeyRound, ListChecks, LoaderCircle, Pencil, Play,
  RotateCcw, Send, Shuffle, Target, Trophy,
} from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/app/EmptyState';
import { LIBRARY_AUTHOR, LibraryBadge } from '@/components/app/LibraryBadge';
import { AttemptResultView, PointsBadge } from '@/components/quiz/AttemptResultView';
import { QuestionInput } from '@/components/quiz/QuestionInput';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import { PERMISSIONS } from '@/lib/permissions';
import { initialResponse, isAnswered, TYPE_ICON, TYPE_LABEL, type Response } from '@/lib/questions';
import { cn } from '@/lib/utils';
import type { AttemptResult, AttemptSession, QuizDetail, StudioQuiz } from '@/types';

type SaveState = 'idle' | 'saving' | 'saved' | 'error';
const AUTOSAVE_DELAY_MS = 700;

// ---------------------------------------------------------------- landing

/** Instructors can copy any quiz they can open (e.g. a library quiz) into an editable Studio draft. */
function CopyToStudioButton({ quizId }: { quizId: number }) {
  const navigate = useNavigate();
  const copy = useMutation({
    mutationFn: () => api.post<StudioQuiz>(`/studio/quizzes/${quizId}/duplicate`).then(r => r.data),
    onSuccess: q => { toast.success('Copied to your Studio as a draft'); navigate(`/studio/${q.id}`); },
    onError: e => toast.error('Could not copy the quiz', { description: errorMessage(e) }),
  });
  return (
    <Button variant="outline" disabled={copy.isPending} onClick={() => copy.mutate()}>
      {copy.isPending ? <LoaderCircle className="animate-spin" /> : <Copy />} Make an editable copy
    </Button>
  );
}

function Landing({ quiz, onStart, starting }: { quiz: QuizDetail; onStart: () => void; starting: boolean }) {
  const { can } = useAuth();
  const s = quiz.settings;
  const left = quiz.attemptsLeft ?? null;
  const resume = !!quiz.openAttemptId;
  const facts = [
    { icon: ListChecks, text: `${quiz.questionCount} questions · ${quiz.totalPoints} points` },
    { icon: RotateCcw, text: s.maxAttempts ? `${left} of ${s.maxAttempts} attempts left` : 'Unlimited attempts' },
    ...(s.passPercentage ? [{ icon: Target, text: `Pass mark ${s.passPercentage}%` }] : []),
    ...(s.shuffleQuestions || s.shuffleOptions ? [{ icon: Shuffle, text: 'Questions/options are shuffled for each attempt' }] : []),
    ...(s.resultsVisibility === 'SCORE_ONLY' ? [{ icon: Eye, text: 'You will see your score, not the correct answers' }] : []),
  ];
  return (
    <Card>
      <CardHeader>
        {quiz.builtIn && <div><LibraryBadge /></div>}
        <CardTitle className="text-2xl">{quiz.title}</CardTitle>
        <CardDescription>
          {quiz.description && <span className="block">{quiz.description}</span>}
          <span className="block">By {quiz.builtIn ? LIBRARY_AUTHOR : quiz.author}</span>
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <ul className="grid gap-2 sm:grid-cols-2">
          {facts.map(f => (
            <li key={f.text} className="flex items-center gap-2 text-sm">
              <f.icon className="size-4 text-primary" aria-hidden="true" /> {f.text}
            </li>
          ))}
        </ul>
        {quiz.attemptsUsed > 0 && (
          <p className="text-sm text-muted-foreground">
            You've taken this quiz {quiz.attemptsUsed} time{quiz.attemptsUsed === 1 ? '' : 's'}. <Link to="/history" className="text-primary hover:underline">See your results</Link>
          </p>
        )}
        <p className="text-sm text-muted-foreground">Your answers save automatically: you can close the page and resume later.</p>
      </CardContent>
      <CardFooter className="flex-wrap justify-end gap-2 border-t">
        {quiz.canAuthor && <Button asChild variant="outline"><Link to={`/studio/${quiz.id}`}><Pencil /> Edit in Studio</Link></Button>}
        {quiz.canViewAnswers && <Button asChild variant="outline"><Link to={`/quiz/${quiz.id}/answers`}><KeyRound /> View with answers</Link></Button>}
        {quiz.builtIn && can(PERMISSIONS.QUIZ_AUTHOR) && <CopyToStudioButton quizId={quiz.id} />}
        {left === 0 && !resume ? (
          <Button disabled>No attempts left</Button>
        ) : (
          <Button size="lg" onClick={onStart} disabled={starting}>
            {starting ? <LoaderCircle className="animate-spin" /> : <Play />} {resume ? 'Resume attempt' : 'Start quiz'}
          </Button>
        )}
      </CardFooter>
    </Card>
  );
}

// ---------------------------------------------------------------- attempt

function AttemptRunner({ session, onSubmitted }: { session: AttemptSession; onSubmitted: (r: AttemptResult) => void }) {
  const { questions } = session;
  const [responses, setResponses] = useState<Record<number, Response | undefined>>(() =>
    Object.fromEntries(session.answers.map(a => [a.questionId, (a.response ?? undefined) as Response | undefined])));
  const [flags, setFlags] = useState<Set<number>>(() => new Set(session.answers.filter(a => a.flagged).map(a => a.questionId)));
  const [touched, setTouched] = useState<Set<number>>(() => new Set(session.answers.filter(a => a.response).map(a => a.questionId)));
  const [saveState, setSaveState] = useState<SaveState>('idle');

  // Autosave: remember which questions changed, then save them together after a short pause.
  const latest = useRef({ responses, flags });
  useEffect(() => { latest.current = { responses, flags }; }, [responses, flags]);
  const dirty = useRef(new Set<number>());
  const timer = useRef<number | undefined>(undefined);
  const inFlight = useRef<Promise<unknown>>(Promise.resolve());

  const flush = useCallback(() => {
    window.clearTimeout(timer.current);
    const ids = [...dirty.current];
    dirty.current.clear();
    if (ids.length === 0) return inFlight.current;
    setSaveState('saving');
    const { responses: r, flags: f } = latest.current;
    const batch = Promise.all(ids.map(id =>
      api.put(`/attempts/${session.attemptId}/answers/${id}`, { response: r[id] ?? null, flagged: f.has(id) })));
    inFlight.current = inFlight.current.then(() => batch)
      .then(() => setSaveState('saved'))
      .catch(e => { ids.forEach(id => dirty.current.add(id)); setSaveState('error'); throw e; });
    return inFlight.current;
  }, [session.attemptId]);

  const markDirty = (qId: number) => {
    dirty.current.add(qId);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => { flush().catch(() => {}); }, AUTOSAVE_DELAY_MS);
  };

  // Save anything pending if the learner leaves the page.
  useEffect(() => () => { flush().catch(() => {}); }, [flush]);

  const responseFor = (qId: number) => {
    const q = questions.find(x => x.id === qId)!;
    return responses[qId] ?? initialResponse(q.type, q.content);
  };
  const answeredIds = questions.filter(q => isAnswered(q.type, q.content, responseFor(q.id), touched.has(q.id))).map(q => q.id);
  const unanswered = questions.length - answeredIds.length;

  const setResponse = (qId: number, r: Response) => {
    setResponses(prev => ({ ...prev, [qId]: r }));
    setTouched(prev => new Set(prev).add(qId));
    markDirty(qId);
  };
  const toggleFlag = (qId: number) => {
    setFlags(prev => { const n = new Set(prev); if (n.has(qId)) n.delete(qId); else n.add(qId); return n; });
    markDirty(qId);
  };

  const submit = useMutation({
    mutationFn: async () => {
      await flush();   // every answer is on the server before grading
      return api.post<AttemptResult>(`/attempts/${session.attemptId}/submit`).then(r => r.data);
    },
    onSuccess: onSubmitted,
    onError: e => toast.error('Could not submit', { description: errorMessage(e) }),
  });

  const scrollTo = (qId: number) =>
    document.getElementById(`question-${qId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });

  return (
    <div className="space-y-4">
      {/* Navigator: progress, save status, jump to any question */}
      <div className="sticky top-14 z-10 -mx-4 space-y-3 border-b bg-background/90 px-4 py-3 backdrop-blur md:-mx-8 md:px-8">
        <div className="flex items-center justify-between gap-3 text-sm">
          <span className="font-medium">{answeredIds.length} of {questions.length} answered{flags.size > 0 && ` · ${flags.size} flagged`}</span>
          <span className="flex items-center gap-1.5 text-muted-foreground" aria-live="polite">
            {saveState === 'saving' && <><LoaderCircle className="size-3.5 animate-spin" /> Saving…</>}
            {saveState === 'saved' && <><Cloud className="size-3.5" /> All changes saved</>}
            {saveState === 'error' && <><CloudOff className="size-3.5 text-destructive" /> Not saved, will retry</>}
          </span>
        </div>
        <Progress value={(answeredIds.length / questions.length) * 100} aria-label="Quiz progress" />
        <nav aria-label="Questions" className="flex flex-wrap gap-1.5">
          {questions.map((q, i) => {
            const answered = answeredIds.includes(q.id);
            const flagged = flags.has(q.id);
            return (
              <button key={q.id} type="button" onClick={() => scrollTo(q.id)}
                      aria-label={`Question ${i + 1}${answered ? ', answered' : ''}${flagged ? ', flagged' : ''}`}
                      className={cn('relative grid size-8 place-items-center rounded-md border text-xs font-semibold transition-colors',
                        answered ? 'border-primary bg-primary text-primary-foreground' : 'hover:bg-accent',
                        flagged && 'ring-2 ring-amber-500 ring-offset-1 ring-offset-background')}>
                {i + 1}
              </button>
            );
          })}
        </nav>
      </div>

      {questions.map((q, i) => {
        const Icon = TYPE_ICON[q.type];
        const flagged = flags.has(q.id);
        return (
          <Card key={q.id} id={`question-${q.id}`} className={cn('scroll-mt-48', flagged && 'ring-1 ring-amber-500/50')}>
            <CardHeader className="gap-2">
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span className="font-medium tracking-wide uppercase">Question {i + 1} of {questions.length}</span>
                <Badge variant="secondary"><Icon /> {TYPE_LABEL[q.type]}</Badge>
                <span className="ml-auto flex items-center gap-2">
                  <PointsBadge points={q.points} />
                  <Button type="button" variant={flagged ? 'secondary' : 'ghost'} size="xs" aria-pressed={flagged} onClick={() => toggleFlag(q.id)}>
                    <Flag className={cn(flagged && 'fill-amber-500 text-amber-500')} /> {flagged ? 'Flagged' : 'Flag'}
                  </Button>
                </span>
              </div>
              {q.type !== 'FILL_BLANK' && <h2 id={`q-${q.id}-prompt`} className="text-base leading-snug font-semibold sm:text-lg">{q.prompt}</h2>}
            </CardHeader>
            <CardContent>
              <QuestionInput question={q} response={responseFor(q.id)} onChange={r => setResponse(q.id, r)} />
            </CardContent>
          </Card>
        );
      })}

      <div className="flex flex-wrap items-center justify-end gap-3 pt-2">
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button size="lg" disabled={submit.isPending}>
              {submit.isPending ? <LoaderCircle className="animate-spin" /> : <Send />} Submit answers
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Submit your answers?</AlertDialogTitle>
              <AlertDialogDescription>
                {unanswered > 0 ? `${unanswered} question${unanswered === 1 ? ' is' : 's are'} unanswered and will score 0. ` : 'All questions are answered. '}
                {flags.size > 0 && `${flags.size} question${flags.size === 1 ? ' is' : 's are'} flagged for review. `}
                You can't change your answers after submitting.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Keep working</AlertDialogCancel>
              <AlertDialogAction onClick={() => submit.mutate()}>Submit</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- page

export default function TakeQuizPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [session, setSession] = useState<AttemptSession | null>(null);
  const [result, setResult] = useState<AttemptResult | null>(null);

  const overview = useQuery({
    queryKey: ['quiz', id],
    queryFn: () => api.get<QuizDetail>(`/quizzes/${id}`).then(r => r.data),
  });

  const start = useMutation({
    mutationFn: () => api.post<AttemptSession>(`/quizzes/${id}/attempts`).then(r => r.data),
    onSuccess: s => { setSession(s); setResult(null); window.scrollTo({ top: 0 }); },
    onError: e => toast.error('Could not start the quiz', { description: errorMessage(e) }),
  });

  if (overview.isPending) return <div className="space-y-4" aria-hidden="true"><Skeleton className="h-9 w-2/3" /><Skeleton className="h-56 rounded-xl" /></div>;
  if (overview.isError) {
    return <EmptyState icon={CircleX} title="Quiz not available" description={errorMessage(overview.error)}
                       action={<Button asChild variant="outline"><Link to="/">Back home</Link></Button>} />;
  }
  const quiz = overview.data;

  const onSubmitted = (r: AttemptResult) => {
    setResult(r);
    setSession(null);
    queryClient.invalidateQueries({ queryKey: ['attempts'] });
    queryClient.invalidateQueries({ queryKey: ['quiz', id] });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const left = quiz.attemptsLeft ?? null;

  return (
    <div className="space-y-6">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 text-muted-foreground">
          <Link to="/"><ArrowLeft /> Home</Link>
        </Button>
        {quiz.status !== 'PUBLISHED' && (
          <p className="mb-3 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-sm text-amber-800 dark:text-amber-300">
            Preview: this quiz is {quiz.status.toLowerCase()}, so learners can't see it.
          </p>
        )}
        {(session || result) && <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{quiz.title}</h1>}
      </div>

      {result ? (
        <>
          <AttemptResultView result={result} />
          <div className="flex flex-wrap justify-end gap-2">
            <Button asChild variant="outline"><Link to="/history"><ChartLine /> View progress</Link></Button>
            {left !== 0 && <Button onClick={() => start.mutate()} disabled={start.isPending}><RotateCcw /> Try again</Button>}
            {left === 0 && <Badge variant="outline" className="h-9 px-3"><Trophy /> No attempts left</Badge>}
          </div>
        </>
      ) : session ? (
        <AttemptRunner key={session.attemptId} session={session} onSubmitted={onSubmitted} />
      ) : (
        <Landing quiz={quiz} onStart={() => start.mutate()} starting={start.isPending} />
      )}
    </div>
  );
}
