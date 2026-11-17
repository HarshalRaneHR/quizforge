import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BookOpen, Calendar, Compass, ListChecks, Play, Sparkles, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/app/EmptyState';
import { PageHeader } from '@/components/app/PageHeader';
import { Pagination } from '@/components/app/Pagination';
import { GenerateForm, type GenerateValues } from '@/components/quiz/GenerateForm';
import { GenerationProgress } from '@/components/quiz/GenerationProgress';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { isFinished, useGenerationJob } from '@/hooks/use-generation-job';
import { PERMISSIONS } from '@/lib/permissions';
import { cn } from '@/lib/utils';
import type { JobView, Page, QuizSummary } from '@/types';

const PAGE_SIZE = 9;

function GenerateQuizCard() {
  const [jobId, setJobId] = useState<string | null>(null);
  const job = useGenerationJob(jobId);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const start = useMutation({
    mutationFn: (body: GenerateValues) => api.post<JobView>('/quizzes/generate', body).then(r => r.data),
    onSuccess: j => setJobId(j.id),
    onError: error => toast.error('Could not start generating', { description: errorMessage(error) }),
  });

  // When the background job finishes: open the new quiz, or report the failure (once per job).
  const handled = useRef<string | null>(null);
  useEffect(() => {
    const j = job.data;
    if (!j || !isFinished(j) || handled.current === j.id) return;
    handled.current = j.id;
    if (j.status === 'SUCCEEDED') {
      queryClient.invalidateQueries({ queryKey: ['quizzes'] });
      toast.success('Your quiz is ready', { description: `${j.questionCount} questions generated.` });
      navigate(`/quiz/${j.quizId}`);
    } else {
      toast.error('Generation failed', { description: j.error });
    }
  }, [job.data, navigate, queryClient]);

  const running = !!jobId && !isFinished(job.data);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles className="size-5 text-primary" aria-hidden="true" /> Generate a practice quiz
        </CardTitle>
        <CardDescription>Paste study material, choose the question types, and AI writes a quiz just for you.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {jobId && <GenerationProgress job={job.data} onDismiss={() => setJobId(null)} />}
        <GenerateForm withTitle busy={start.isPending || running} onSubmit={values => start.mutate(values)} />
      </CardContent>
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
    queryKey: ['quizzes', 'PRIVATE', page],
    queryFn: () => api.get<Page<QuizSummary>>('/quizzes', { params: { visibility: 'PRIVATE', page, size: PAGE_SIZE } }).then(r => r.data),
    placeholderData: keepPreviousData,   // keeps the old page visible while the next one loads
  });

  const firstName = user?.email.split('@')[0] ?? '';

  return (
    <>
      <PageHeader title={<>Hi, <span className="capitalize">{firstName}</span> 👋</>}
                  description="Make a practice quiz from your notes, or explore quizzes from instructors."
                  actions={<Button asChild variant="outline"><Link to="/explore"><Compass /> Explore quizzes</Link></Button>} />

      {can(PERMISSIONS.QUIZ_GENERATE) && <GenerateQuizCard />}

      <div className="mt-10 mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold">My practice quizzes</h2>
        {quizzes.data && <Badge variant="outline">{quizzes.data.totalElements} total</Badge>}
      </div>

      {quizzes.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
          {Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-44 rounded-xl" />)}
        </div>
      ) : quizzes.isError ? (
        <p role="alert" className="text-destructive">{errorMessage(quizzes.error)}</p>
      ) : quizzes.data.content.length === 0 ? (
        <EmptyState icon={BookOpen} title="No practice quizzes yet" description="Paste some study material above to generate your first one." />
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
