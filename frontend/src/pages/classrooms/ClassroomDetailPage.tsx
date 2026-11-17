import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft, CalendarClock, CircleX, Copy, Download, LoaderCircle, Plus, RefreshCw, Trash2, UserMinus,
} from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';
import type { ClassroomDetail, Gradebook, Page, QuizSummary } from '@/types';

function AssignDialog({ classroom, onDone }: { classroom: ClassroomDetail; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [quizId, setQuizId] = useState('');
  const [due, setDue] = useState('');   // datetime-local, in the browser's time zone
  const quizzes = useQuery({
    queryKey: ['quizzes', 'PUBLIC', 'PUBLISHED', 'assignable'],
    queryFn: () => api.get<Page<QuizSummary>>('/quizzes', { params: { visibility: 'PUBLIC', status: 'PUBLISHED', size: 100 } }).then(r => r.data),
    enabled: open,
  });
  const assigned = new Set(classroom.assignments.map(a => a.quizId));
  const options = (quizzes.data?.content ?? []).filter(q => !assigned.has(q.id));
  const assign = useMutation({
    mutationFn: () => api.post(`/classrooms/${classroom.id}/assignments`, { quizId: Number(quizId), dueAt: due ? new Date(due).toISOString() : null }),
    onSuccess: () => { toast.success('Quiz assigned'); setOpen(false); setQuizId(''); setDue(''); onDone(); },
    onError: e => toast.error('Could not assign', { description: errorMessage(e) }),
  });
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild><Button size="sm"><Plus /> Assign quiz</Button></DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Assign a quiz</DialogTitle>
          <DialogDescription>Only your published quizzes can be assigned.</DialogDescription>
        </DialogHeader>
        <form id="assign-form" className="grid gap-4" onSubmit={e => { e.preventDefault(); if (quizId) assign.mutate(); }}>
          <div className="grid gap-2">
            <Label>Quiz</Label>
            {quizzes.isPending ? <Skeleton className="h-9" /> : options.length === 0 ? (
              <p className="text-sm text-muted-foreground">No published quizzes left to assign. Publish one in the <Link to="/studio" className="text-primary hover:underline">Studio</Link>.</p>
            ) : (
              <Select value={quizId} onValueChange={setQuizId}>
                <SelectTrigger className="w-full" aria-label="Quiz"><SelectValue placeholder="Choose a quiz" /></SelectTrigger>
                <SelectContent>{options.map(q => <SelectItem key={q.id} value={String(q.id)}>{q.title}</SelectItem>)}</SelectContent>
              </Select>
            )}
          </div>
          <div className="grid gap-2">
            <Label htmlFor="due">Due date <span className="font-normal text-muted-foreground">(optional)</span></Label>
            <Input id="due" type="datetime-local" value={due} onChange={e => setDue(e.target.value)} />
          </div>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
          <Button type="submit" form="assign-form" disabled={!quizId || assign.isPending}>{assign.isPending && <LoaderCircle className="animate-spin" />} Assign</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function GradebookCard({ classroomId }: { classroomId: number }) {
  const gb = useQuery({ queryKey: ['gradebook', classroomId], queryFn: () => api.get<Gradebook>(`/classrooms/${classroomId}/gradebook`).then(r => r.data) });
  const download = useMutation({
    mutationFn: () => api.get(`/classrooms/${classroomId}/gradebook.csv`, { responseType: 'blob' }).then(r => r.data as Blob),
    onSuccess: blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `gradebook-${classroomId}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    },
    onError: e => toast.error('Download failed', { description: errorMessage(e) }),
  });
  const cellClass = (pct: number | null | undefined) =>
    pct === null || pct === undefined ? '' : pct >= 80 ? 'text-emerald-700 dark:text-emerald-400' : pct >= 50 ? 'text-amber-700 dark:text-amber-400' : 'text-red-700 dark:text-red-400';

  return (
    <Card>
      <CardHeader>
        <CardTitle>Gradebook</CardTitle>
        <CardDescription>Best score per learner and quiz.</CardDescription>
        <CardAction>
          <Button variant="outline" size="sm" disabled={download.isPending || !gb.data?.rows.length} onClick={() => download.mutate()}>
            <Download /> CSV
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="px-0">
        {gb.isPending ? <Skeleton className="mx-6 h-32" /> : !gb.data || gb.data.rows.length === 0 || gb.data.assignments.length === 0 ? (
          <p className="px-6 text-sm text-muted-foreground">Results appear here once learners have joined and quizzes are assigned.</p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">Learner</TableHead>
                  {gb.data.assignments.map(a => <TableHead key={a.id} className="max-w-40 truncate" title={a.quizTitle}>{a.quizTitle}</TableHead>)}
                  <TableHead className="pr-6 text-right">Average</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {gb.data.rows.map(row => (
                  <TableRow key={row.userId}>
                    <TableCell className="pl-6 font-medium">{row.email}</TableCell>
                    {row.cells.map(cell => (
                      <TableCell key={cell.assignmentId} className={cn('tabular-nums', cellClass(cell.bestPercentage))}>
                        {cell.progress === 'COMPLETED' ? `${cell.bestPercentage}%` : cell.progress === 'IN_PROGRESS'
                          ? <span className="text-xs text-muted-foreground">in progress</span>
                          : <span className="text-muted-foreground">—</span>}
                      </TableCell>
                    ))}
                    <TableCell className={cn('pr-6 text-right font-semibold tabular-nums', cellClass(row.averagePercentage))}>
                      {row.averagePercentage === null || row.averagePercentage === undefined ? '—' : `${row.averagePercentage}%`}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export default function ClassroomDetailPage() {
  const { id } = useParams();
  const classroomId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const key = ['classroom', classroomId];
  const classroom = useQuery({ queryKey: key, queryFn: () => api.get<ClassroomDetail>(`/classrooms/${classroomId}`).then(r => r.data) });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: key });
    queryClient.invalidateQueries({ queryKey: ['gradebook', classroomId] });
    queryClient.invalidateQueries({ queryKey: ['classrooms'] });
  };

  const regenerate = useMutation({
    mutationFn: () => api.post<ClassroomDetail>(`/classrooms/${classroomId}/join-code`).then(r => r.data),
    onSuccess: c => { queryClient.setQueryData(key, c); toast.success('New code generated; the old one no longer works'); },
  });
  const removeMember = useMutation({
    mutationFn: (userId: number) => api.delete(`/classrooms/${classroomId}/members/${userId}`),
    onSuccess: () => { toast.success('Learner removed'); refresh(); },
  });
  const unassign = useMutation({
    mutationFn: (assignmentId: number) => api.delete(`/classrooms/${classroomId}/assignments/${assignmentId}`),
    onSuccess: () => { toast.success('Assignment removed'); refresh(); },
  });
  const remove = useMutation({
    mutationFn: () => api.delete(`/classrooms/${classroomId}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['classrooms'] }); toast.success('Classroom deleted'); navigate('/classrooms'); },
  });

  if (classroom.isPending) return <div className="space-y-4"><Skeleton className="h-10 w-1/2" /><Skeleton className="h-48 rounded-xl" /></div>;
  if (classroom.isError) {
    return <EmptyState icon={CircleX} title="Classroom not available" description={errorMessage(classroom.error)}
                       action={<Button asChild variant="outline"><Link to="/classrooms">Back to classrooms</Link></Button>} />;
  }
  const c = classroom.data;

  return (
    <div className="space-y-6">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 text-muted-foreground"><Link to="/classrooms"><ArrowLeft /> Classrooms</Link></Button>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{c.name}</h1>
            {c.description && <p className="mt-1 text-muted-foreground">{c.description}</p>}
          </div>
          <AlertDialog>
            <AlertDialogTrigger asChild><Button variant="ghost" size="icon" aria-label="Delete classroom"><Trash2 className="text-destructive" /></Button></AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Delete {c.name}?</AlertDialogTitle>
                <AlertDialogDescription>Memberships and assignments are removed. Quizzes and learners' attempts are kept.</AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Cancel</AlertDialogCancel>
                <AlertDialogAction variant="destructive" onClick={() => remove.mutate()}>Delete</AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Join code</CardTitle>
          <CardDescription>Learners enter this under <span className="font-medium">My classes</span>.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center gap-3">
          <span className="rounded-lg bg-muted px-4 py-2 font-mono text-2xl font-semibold tracking-[0.3em]">{c.joinCode}</span>
          <Button variant="outline" size="sm" onClick={() => navigator.clipboard.writeText(c.joinCode).then(() => toast.success('Code copied'))}><Copy /> Copy</Button>
          <Button variant="ghost" size="sm" disabled={regenerate.isPending} onClick={() => regenerate.mutate()}><RefreshCw /> New code</Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Assignments</CardTitle>
          <CardDescription>{c.assignments.length} quiz{c.assignments.length === 1 ? '' : 'zes'} assigned</CardDescription>
          <CardAction><AssignDialog classroom={c} onDone={refresh} /></CardAction>
        </CardHeader>
        <CardContent>
          {c.assignments.length === 0 ? <p className="text-sm text-muted-foreground">Nothing assigned yet.</p> : (
            <ul className="grid gap-2">
              {c.assignments.map(a => (
                <li key={a.id} className="flex items-center gap-3 rounded-lg border p-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{a.quizTitle}</p>
                    <p className="flex items-center gap-1 text-xs text-muted-foreground">
                      <CalendarClock className="size-3.5" /> {a.dueAt ? `Due ${new Date(a.dueAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}` : 'No due date'}
                      {a.dueAt && new Date(a.dueAt) < new Date() && <Badge variant="outline" className="ml-1">Past due</Badge>}
                    </p>
                  </div>
                  <Button variant="ghost" size="icon-sm" aria-label={`Remove ${a.quizTitle}`} onClick={() => unassign.mutate(a.id)}><Trash2 /></Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Learners</CardTitle>
          <CardDescription>{c.members.length} joined</CardDescription>
        </CardHeader>
        <CardContent>
          {c.members.length === 0 ? <p className="text-sm text-muted-foreground">No one has joined yet. Share the code above.</p> : (
            <ul className="grid gap-1">
              {c.members.map(m => (
                <li key={m.userId} className="flex items-center gap-3 rounded-md px-2 py-1.5 hover:bg-muted/60">
                  <span className="flex-1 truncate text-sm">{m.email}</span>
                  <span className="text-xs text-muted-foreground">joined {new Date(m.joinedAt).toLocaleDateString()}</span>
                  <Button variant="ghost" size="icon-sm" aria-label={`Remove ${m.email}`} onClick={() => removeMember.mutate(m.userId)}><UserMinus /></Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <GradebookCard classroomId={classroomId} />
    </div>
  );
}
