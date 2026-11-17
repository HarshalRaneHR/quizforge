import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardList, DoorOpen, LoaderCircle, LogIn, UsersRound } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { AssignmentList } from '@/components/app/AssignmentList';
import { EmptyState } from '@/components/app/EmptyState';
import { PageHeader } from '@/components/app/PageHeader';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import type { ClassroomDetail, ClassroomSummary, MyAssignment } from '@/types';

/** Learner view: join classes with a code, see what's assigned and when it's due. */
export default function ClassesPage() {
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');

  const assignments = useQuery({ queryKey: ['my-assignments'], queryFn: () => api.get<MyAssignment[]>('/assignments/me').then(r => r.data) });
  const classes = useQuery({ queryKey: ['joined-classes'], queryFn: () => api.get<ClassroomSummary[]>('/classrooms/joined').then(r => r.data) });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['my-assignments'] });
    queryClient.invalidateQueries({ queryKey: ['joined-classes'] });
  };

  const join = useMutation({
    mutationFn: () => api.post<ClassroomDetail>('/classrooms/join', { code }).then(r => r.data),
    onSuccess: c => { toast.success(`Joined ${c.name}`); setCode(''); refresh(); },
    onError: e => toast.error('Could not join', { description: errorMessage(e) }),
  });
  const leave = useMutation({
    mutationFn: (id: number) => api.post(`/classrooms/${id}/leave`),
    onSuccess: () => { toast.success('Left the classroom'); refresh(); },
  });

  const pending = assignments.data?.filter(a => a.progress !== 'COMPLETED') ?? [];
  const done = assignments.data?.filter(a => a.progress === 'COMPLETED') ?? [];

  return (
    <>
      <PageHeader title="My classes" description="Join your instructor's class and keep track of assigned quizzes." />

      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><LogIn className="size-5 text-primary" /> Join a class</CardTitle>
          <CardDescription>Enter the code your instructor shared, e.g. ABCD2345.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="flex max-w-md gap-2" onSubmit={e => { e.preventDefault(); if (code.trim()) join.mutate(); }}>
            <Input aria-label="Class code" placeholder="Class code" maxLength={12} className="font-mono uppercase tracking-widest"
                   value={code} onChange={e => setCode(e.target.value.toUpperCase())} />
            <Button type="submit" disabled={join.isPending || !code.trim()}>{join.isPending && <LoaderCircle className="animate-spin" />} Join</Button>
          </form>
        </CardContent>
      </Card>

      <h2 className="mb-3 text-lg font-semibold">Assigned to me</h2>
      {assignments.isPending ? <Skeleton className="h-32 rounded-xl" /> : assignments.isError ? (
        <p role="alert" className="text-destructive">{errorMessage(assignments.error)}</p>
      ) : (assignments.data ?? []).length === 0 ? (
        <EmptyState icon={ClipboardList} title="Nothing assigned yet" description="Quizzes your instructors assign will show up here." />
      ) : (
        <div className="space-y-6">
          {pending.length > 0 && <AssignmentList items={pending} />}
          {done.length > 0 && (
            <div>
              <h3 className="mb-2 text-sm font-medium text-muted-foreground">Completed</h3>
              <AssignmentList items={done} />
            </div>
          )}
        </div>
      )}

      <h2 className="mt-10 mb-3 text-lg font-semibold">Classes</h2>
      {classes.isPending ? <Skeleton className="h-24 rounded-xl" /> : (classes.data ?? []).length === 0 ? (
        <EmptyState icon={UsersRound} title="You haven't joined a class" description="Ask your instructor for a class code." />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {classes.data!.map(c => (
            <li key={c.id}>
              <Card className="gap-2 py-4">
                <CardHeader className="px-4">
                  <CardTitle className="text-base">{c.name}</CardTitle>
                  <CardDescription>{c.owner} · {c.assignmentCount} assignment{c.assignmentCount === 1 ? '' : 's'}</CardDescription>
                </CardHeader>
                <CardContent className="flex justify-end px-4">
                  <AlertDialog>
                    <AlertDialogTrigger asChild><Button variant="ghost" size="sm"><DoorOpen /> Leave</Button></AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>Leave {c.name}?</AlertDialogTitle>
                        <AlertDialogDescription>Its assignments disappear from your list. Your past attempts are kept. You can rejoin with the code.</AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>Cancel</AlertDialogCancel>
                        <AlertDialogAction variant="destructive" onClick={() => leave.mutate(c.id)}>Leave</AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
