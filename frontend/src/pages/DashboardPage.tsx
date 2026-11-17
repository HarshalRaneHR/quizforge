import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BookOpen, Calendar, ListChecks, LoaderCircle, Play, Sparkles, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/app/EmptyState';
import { PageHeader } from '@/components/app/PageHeader';
import { Pagination } from '@/components/app/Pagination';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { PERMISSIONS } from '@/lib/permissions';
import { cn } from '@/lib/utils';
import { BlueprintPicker } from '@/components/quiz/BlueprintPicker';
import { BLUEPRINT_PRESETS, MAX_QUESTIONS, totalOf, type Counts } from '@/lib/questions';
import type { Difficulty, GenerateQuizRequest, Page, QuizSummary } from '@/types';

const MIN_CHARS = 200;
const MAX_CHARS = 20000;
const PAGE_SIZE = 9;

function GenerateQuizCard() {
  const [title, setTitle] = useState('');
  const [sourceText, setSourceText] = useState('');
  const [counts, setCounts] = useState<Counts>(BLUEPRINT_PRESETS[1].counts);
  const [difficulty, setDifficulty] = useState<Difficulty>('MEDIUM');
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const generate = useMutation({
    mutationFn: (body: GenerateQuizRequest) => api.post<QuizSummary>('/quizzes/generate', body).then(r => r.data),
    onSuccess: quiz => {
      queryClient.invalidateQueries({ queryKey: ['quizzes'] });
      toast.success(`"${quiz.title}" is ready`, { description: `${quiz.questionCount} questions generated.` });
      navigate(`/quiz/${quiz.id}`);
    },
    onError: error => toast.error('Could not generate the quiz', { description: errorMessage(error) }),
  });

  const chars = sourceText.length;
  const enough = chars >= MIN_CHARS;
  const total = totalOf(counts);
  const validBlueprint = total >= 1 && total <= MAX_QUESTIONS;

  const onSubmit = () => generate.mutate({
    title,
    sourceText,
    blueprint: { counts: Object.fromEntries(Object.entries(counts).filter(([, n]) => (n ?? 0) > 0)) as Record<string, number>, difficulty },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles className="size-5 text-primary" aria-hidden="true" /> Generate a quiz
        </CardTitle>
        <CardDescription>Paste study notes, an article or a textbook section. AI writes the questions.</CardDescription>
      </CardHeader>
      <CardContent>
        <form id="generate-form" className="grid gap-5" onSubmit={e => { e.preventDefault(); onSubmit(); }}>
          <div className="grid gap-2">
            <Label htmlFor="title">Title <span className="font-normal text-muted-foreground">(optional)</span></Label>
            <Input id="title" maxLength={120} placeholder="AI will suggest one" value={title}
                   onChange={e => setTitle(e.target.value)} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="source">Study material</Label>
            <Textarea id="source" required minLength={MIN_CHARS} maxLength={MAX_CHARS} rows={8}
                      className="min-h-40 resize-y" placeholder="Paste your notes here…" value={sourceText}
                      aria-describedby="source-hint"
                      onChange={e => setSourceText(e.target.value)} />
            <div id="source-hint" className="flex justify-between text-xs text-muted-foreground">
              <span className={cn(enough && 'text-emerald-600 dark:text-emerald-400')}>
                {enough ? 'Looks good ✓' : `${MIN_CHARS - chars} more characters needed`}
              </span>
              <span>{chars.toLocaleString()} / {MAX_CHARS.toLocaleString()}</span>
            </div>
          </div>
          <BlueprintPicker counts={counts} onCountsChange={setCounts} difficulty={difficulty} onDifficultyChange={setDifficulty} />
        </form>
      </CardContent>
      <CardFooter className="justify-end gap-3 border-t">
        {generate.isPending && <span className="text-sm text-muted-foreground">Each question type is a separate AI call; this can take a few minutes.</span>}
        <Button type="submit" form="generate-form" disabled={generate.isPending || !enough || !validBlueprint}>
          {generate.isPending ? <><LoaderCircle className="animate-spin" /> Generating…</> : <><Sparkles /> Generate {total} questions</>}
        </Button>
      </CardFooter>
    </Card>
  );
}

function DeleteQuizButton({ quiz }: { quiz: QuizSummary }) {
  const queryClient = useQueryClient();
  const remove = useMutation({
    mutationFn: () => api.delete(`/quizzes/${quiz.id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quizzes'] });
      queryClient.invalidateQueries({ queryKey: ['attempts'] });
      toast.success('Quiz deleted');
    },
    onError: error => toast.error('Could not delete the quiz', { description: errorMessage(error) }),
  });

  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={`Delete ${quiz.title}`} disabled={remove.isPending}>
          <Trash2 className="text-muted-foreground" />
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete "{quiz.title}"?</AlertDialogTitle>
          <AlertDialogDescription>This also deletes all attempts of this quiz. It can't be undone.</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction variant="destructive" onClick={() => remove.mutate()}>Delete</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function QuizCard({ quiz }: { quiz: QuizSummary }) {
  return (
    <Card className="w-full gap-3 transition-all hover:-translate-y-0.5 hover:shadow-md">
      <CardHeader>
        <CardTitle className="line-clamp-2 leading-snug">
          <Link to={`/quiz/${quiz.id}`} className="hover:underline">{quiz.title}</Link>
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
        <Badge variant="secondary"><ListChecks /> {quiz.questionCount} questions</Badge>
        <span>{quiz.totalPoints} pts</span>
        <span className="flex items-center gap-1"><Calendar className="size-3.5" aria-hidden="true" />{new Date(quiz.createdAt).toLocaleDateString()}</span>
      </CardContent>
      <CardFooter className="mt-auto justify-between">
        <Button asChild size="sm"><Link to={`/quiz/${quiz.id}`}><Play /> Start</Link></Button>
        <DeleteQuizButton quiz={quiz} />
      </CardFooter>
    </Card>
  );
}

export default function DashboardPage() {
  const { user, can } = useAuth();
  const [page, setPage] = useState(0);

  const quizzes = useQuery({
    queryKey: ['quizzes', page],
    queryFn: () => api.get<Page<QuizSummary>>('/quizzes', { params: { page, size: PAGE_SIZE } }).then(r => r.data),
    placeholderData: keepPreviousData,   // keeps the old page visible while the next one loads
  });

  const firstName = user?.email.split('@')[0] ?? '';

  return (
    <>
      <PageHeader title={<>Hi, <span className="capitalize">{firstName}</span> 👋</>}
                  description="Generate a new quiz or pick up one you've already made." />

      {can(PERMISSIONS.QUIZ_GENERATE) && <GenerateQuizCard />}

      <div className="mt-10 mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold">My quizzes</h2>
        {quizzes.data && <Badge variant="outline">{quizzes.data.totalElements} total</Badge>}
      </div>

      {quizzes.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
          {Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-44 rounded-xl" />)}
        </div>
      ) : quizzes.isError ? (
        <p role="alert" className="text-destructive">{errorMessage(quizzes.error)}</p>
      ) : quizzes.data.content.length === 0 ? (
        <EmptyState icon={BookOpen} title="No quizzes yet" description="Paste some study material above to generate your first quiz." />
      ) : (
        <>
          <ul className={cn('grid gap-4 sm:grid-cols-2 lg:grid-cols-3', quizzes.isPlaceholderData && 'opacity-60')}>
            {quizzes.data.content.map(q => <li key={q.id} className="flex"><QuizCard quiz={q} /></li>)}
          </ul>
          <Pagination page={page} totalPages={quizzes.data.totalPages} onPageChange={setPage} disabled={quizzes.isPlaceholderData} />
        </>
      )}
    </>
  );
}
