import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Archive, ArrowDown, ArrowLeft, ArrowUp, CircleX, Copy, Eye, Info, LoaderCircle, Pencil, Plus, RotateCcw, Rocket,
  Sparkles, Trash2, Undo2,
} from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { StatusBadge } from '@/components/app/StatusBadge';
import { GenerateForm, type GenerateValues } from '@/components/quiz/GenerateForm';
import { GenerationProgress } from '@/components/quiz/GenerationProgress';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { isFinished, useGenerationJob } from '@/hooks/use-generation-job';
import { QUESTION_TYPES, TYPE_ICON, TYPE_LABEL } from '@/lib/questions';
import type { AuthoringQuestionView, JobView, QuestionType, StudioQuiz } from '@/types';
import { QuestionEditorDialog } from './QuestionEditorDialog';
import { QuizAnalyticsCard } from './QuizAnalyticsCard';
import { SettingsCard } from './SettingsCard';

type Editor = { editing?: AuthoringQuestionView; type?: QuestionType } | null;

function DetailsCard({ quiz, onSaved }: { quiz: StudioQuiz; onSaved: (q: StudioQuiz) => void }) {
  const [title, setTitle] = useState(quiz.title);
  const [description, setDescription] = useState(quiz.description ?? '');
  const dirty = title !== quiz.title || description !== (quiz.description ?? '');
  const save = useMutation({
    mutationFn: () => api.put<StudioQuiz>(`/studio/quizzes/${quiz.id}`, { title, description }).then(r => r.data),
    onSuccess: q => { onSaved(q); toast.success('Details saved'); },
    onError: e => toast.error('Could not save', { description: errorMessage(e) }),
  });
  return (
    <Card>
      <CardHeader><CardTitle>Details</CardTitle><CardDescription>Learners see these in Explore.</CardDescription></CardHeader>
      <CardContent>
        <form className="grid gap-4" onSubmit={e => { e.preventDefault(); save.mutate(); }}>
          <div className="grid gap-2">
            <Label htmlFor="b-title">Title</Label>
            <Input id="b-title" required maxLength={120} value={title} onChange={e => setTitle(e.target.value)} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="b-desc">Description</Label>
            <Textarea id="b-desc" rows={2} maxLength={2000} value={description} onChange={e => setDescription(e.target.value)} />
          </div>
          {dirty && (
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" onClick={() => { setTitle(quiz.title); setDescription(quiz.description ?? ''); }}>Discard</Button>
              <Button type="submit" disabled={save.isPending}>{save.isPending && <LoaderCircle className="animate-spin" />} Save details</Button>
            </div>
          )}
        </form>
      </CardContent>
    </Card>
  );
}

function AiCard({ quizId, onDone }: { quizId: number; onDone: () => void }) {
  const [jobId, setJobId] = useState<string | null>(null);
  const job = useGenerationJob(jobId);
  const start = useMutation({
    mutationFn: (v: GenerateValues) => api.post<JobView>(`/studio/quizzes/${quizId}/generate`, { sourceText: v.sourceText, blueprint: v.blueprint }).then(r => r.data),
    onSuccess: j => setJobId(j.id),
    onError: e => toast.error('Could not start generating', { description: errorMessage(e) }),
  });
  const handled = useRef<string | null>(null);   // react to each finished job exactly once
  useEffect(() => {
    const j = job.data;
    if (!j || !isFinished(j) || handled.current === j.id) return;
    handled.current = j.id;
    if (j.status === 'SUCCEEDED') {
      toast.success(`${j.questionCount} questions added`, { description: 'Review them below before publishing.' });
      onDone();
    } else {
      toast.error('Generation failed', { description: j.error });
    }
  }, [job.data, onDone]);
  const running = !!jobId && !isFinished(job.data);
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Sparkles className="size-5 text-primary" /> Generate questions with AI</CardTitle>
        <CardDescription>Generated questions are added to this draft. Review and edit them before publishing.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {jobId && <GenerationProgress job={job.data} doneText="Added to the question list above." onDismiss={() => setJobId(null)} />}
        <GenerateForm busy={start.isPending || running} onSubmit={v => start.mutate(v)} />
      </CardContent>
    </Card>
  );
}

function QuestionRow({ q, index, count, editable, onEdit, onMove, onDelete, busy }: {
  q: AuthoringQuestionView; index: number; count: number; editable: boolean; busy: boolean;
  onEdit: () => void; onMove: (delta: number) => void; onDelete: () => void;
}) {
  const Icon = TYPE_ICON[q.question.type];
  return (
    <li className="flex items-start gap-3 rounded-lg border p-3">
      <span className="mt-0.5 grid size-7 shrink-0 place-items-center rounded-md bg-muted text-xs font-semibold">{index + 1}</span>
      <div className="min-w-0 flex-1 space-y-1.5">
        <p className="line-clamp-2 text-sm font-medium">{q.question.prompt}</p>
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <Badge variant="secondary"><Icon /> {TYPE_LABEL[q.question.type]}</Badge>
          <span>{q.question.points} pt{q.question.points === 1 ? '' : 's'}</span>
          <span className="capitalize">{q.question.difficulty?.toLowerCase()}</span>
        </div>
      </div>
      {editable && (
        <div className="flex shrink-0 items-center">
          <Button variant="ghost" size="icon-sm" aria-label={`Move question ${index + 1} up`} disabled={busy || index === 0} onClick={() => onMove(-1)}><ArrowUp /></Button>
          <Button variant="ghost" size="icon-sm" aria-label={`Move question ${index + 1} down`} disabled={busy || index === count - 1} onClick={() => onMove(1)}><ArrowDown /></Button>
          <Button variant="ghost" size="icon-sm" aria-label={`Edit question ${index + 1}`} onClick={onEdit}><Pencil /></Button>
          <Button variant="ghost" size="icon-sm" aria-label={`Delete question ${index + 1}`} disabled={busy} onClick={onDelete}><Trash2 className="text-destructive" /></Button>
        </div>
      )}
    </li>
  );
}

export default function QuizBuilderPage() {
  const { id } = useParams();
  const quizId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editor, setEditor] = useState<Editor>(null);
  const key = ['studio-quiz', quizId];

  const quiz = useQuery({ queryKey: key, queryFn: () => api.get<StudioQuiz>(`/studio/quizzes/${quizId}`).then(r => r.data) });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: key });
    queryClient.invalidateQueries({ queryKey: ['quizzes'] });
  };
  const setQuiz = (q: StudioQuiz) => { queryClient.setQueryData(key, q); queryClient.invalidateQueries({ queryKey: ['quizzes'] }); };

  const lifecycle = useMutation({
    mutationFn: (action: 'publish' | 'unpublish' | 'archive' | 'restore' | 'duplicate') =>
      api.post<StudioQuiz>(`/studio/quizzes/${quizId}/${action}`).then(r => ({ action, quiz: r.data })),
    onSuccess: ({ action, quiz: q }) => {
      if (action === 'duplicate') {
        toast.success('Duplicated as a new draft');
        queryClient.invalidateQueries({ queryKey: ['quizzes'] });
        navigate(`/studio/${q.id}`);
        return;
      }
      setQuiz(q);
      toast.success({ publish: 'Published: learners can now find it in Explore', unpublish: 'Back to draft', archive: 'Archived', restore: 'Restored' }[action]);
    },
    onError: e => toast.error('Action failed', { description: errorMessage(e) }),
  });

  const reorder = useMutation({
    mutationFn: (ids: number[]) => api.put<StudioQuiz>(`/studio/quizzes/${quizId}/questions/order`, { questionIds: ids }).then(r => r.data),
    onSuccess: setQuiz,
    onError: e => toast.error('Could not reorder', { description: errorMessage(e) }),
  });

  const removeQuestion = useMutation({
    mutationFn: (questionId: number) => api.delete(`/studio/quizzes/${quizId}/questions/${questionId}`),
    onSuccess: () => { refresh(); toast.success('Question deleted'); },
    onError: e => toast.error('Could not delete', { description: errorMessage(e) }),
  });

  const deleteQuiz = useMutation({
    mutationFn: () => api.delete(`/quizzes/${quizId}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['quizzes'] }); toast.success('Quiz deleted'); navigate('/studio'); },
    onError: e => toast.error('Could not delete', { description: errorMessage(e) }),
  });

  if (quiz.isPending) return <div className="space-y-4"><Skeleton className="h-10 w-2/3" /><Skeleton className="h-64 rounded-xl" /></div>;
  if (quiz.isError) {
    return <EmptyState icon={CircleX} title="Quiz not available" description={errorMessage(quiz.error)}
                       action={<Button asChild variant="outline"><Link to="/studio">Back to Studio</Link></Button>} />;
  }

  const q = quiz.data;
  const isDraft = q.status === 'DRAFT';
  const ids = q.questions.map(x => x.id);
  const move = (index: number, delta: number) => {
    const next = [...ids];
    [next[index], next[index + delta]] = [next[index + delta], next[index]];
    reorder.mutate(next);
  };
  const busy = lifecycle.isPending;

  return (
    <div className="space-y-6">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 text-muted-foreground"><Link to="/studio"><ArrowLeft /> Studio</Link></Button>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0 space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{q.title}</h1>
              <StatusBadge status={q.status} />
            </div>
            <p className="text-sm text-muted-foreground">{q.questionCount} questions · {q.totalPoints} points</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline"><Link to={`/quiz/${q.id}`}><Eye /> Preview</Link></Button>
            {q.status === 'DRAFT' && (
              <Button disabled={busy || q.questionCount === 0} onClick={() => lifecycle.mutate('publish')}><Rocket /> Publish</Button>
            )}
            {q.status === 'PUBLISHED' && (
              <>
                {!q.hasLearnerAttempts && <Button variant="outline" disabled={busy} onClick={() => lifecycle.mutate('unpublish')}><Undo2 /> Unpublish</Button>}
                <Button variant="outline" disabled={busy} onClick={() => lifecycle.mutate('archive')}><Archive /> Archive</Button>
              </>
            )}
            {q.status === 'ARCHIVED' && <Button disabled={busy} onClick={() => lifecycle.mutate('restore')}><RotateCcw /> Restore</Button>}
            {q.status !== 'DRAFT' && <Button variant="outline" disabled={busy} onClick={() => lifecycle.mutate('duplicate')}><Copy /> Duplicate</Button>}
            {!q.hasLearnerAttempts && (
              <AlertDialog>
                <AlertDialogTrigger asChild><Button variant="ghost" size="icon" aria-label="Delete quiz"><Trash2 className="text-destructive" /></Button></AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Delete "{q.title}"?</AlertDialogTitle>
                    <AlertDialogDescription>The quiz and all its questions are deleted permanently.</AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction variant="destructive" onClick={() => deleteQuiz.mutate()}>Delete</AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )}
          </div>
        </div>
      </div>

      {!isDraft && (
        <div className="flex gap-3 rounded-lg border border-sky-500/30 bg-sky-500/5 p-4 text-sm">
          <Info className="mt-0.5 size-4 shrink-0 text-sky-600" />
          <p>
            {q.status === 'PUBLISHED' ? 'Published quizzes are locked so learners\' results stay valid. ' : 'Archived quizzes are hidden from learners; results are kept. '}
            {q.hasLearnerAttempts ? 'Learners have taken this quiz, so use Duplicate to make a new version.' : q.status === 'PUBLISHED' ? 'Unpublish it to make changes.' : ''}
          </p>
        </div>
      )}

      {!isDraft && <QuizAnalyticsCard quizId={q.id} />}

      {isDraft && <DetailsCard key={q.updatedAt} quiz={q} onSaved={setQuiz} />}

      <SettingsCard key={`settings-${q.updatedAt}`} quiz={q} editable={isDraft} onSaved={setQuiz} />

      <Card>
        <CardHeader>
          <CardTitle>Questions</CardTitle>
          <CardDescription>{isDraft ? 'Add, edit and reorder. Learners see them in this order.' : 'Read-only while not a draft.'}</CardDescription>
          {isDraft && (
            <CardAction>
              <DropdownMenu>
                <DropdownMenuTrigger asChild><Button size="sm"><Plus /> Add question</Button></DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuLabel>Question type</DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  {QUESTION_TYPES.map(t => (
                    <DropdownMenuItem key={t.type} onSelect={() => setEditor({ type: t.type })}><t.icon /> {t.label}</DropdownMenuItem>
                  ))}
                </DropdownMenuContent>
              </DropdownMenu>
            </CardAction>
          )}
        </CardHeader>
        <CardContent>
          {q.questions.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">No questions yet. Add one by hand or generate some with AI below.</p>
          ) : (
            <ol className="grid gap-2">
              {q.questions.map((question, i) => (
                <QuestionRow key={question.id} q={question} index={i} count={q.questions.length} editable={isDraft}
                             busy={reorder.isPending || removeQuestion.isPending}
                             onEdit={() => setEditor({ editing: question })} onMove={d => move(i, d)}
                             onDelete={() => removeQuestion.mutate(question.id)} />
              ))}
            </ol>
          )}
        </CardContent>
      </Card>

      {isDraft && <AiCard quizId={quizId} onDone={refresh} />}

      {editor && (
        <QuestionEditorDialog key={editor.editing?.id ?? `new-${editor.type}`} quizId={quizId} editing={editor.editing}
                              initialType={editor.type} open onOpenChange={o => { if (!o) setEditor(null); }}
                              onSaved={() => { refresh(); toast.success(editor.editing ? 'Question saved' : 'Question added'); }} />
      )}
    </div>
  );
}
