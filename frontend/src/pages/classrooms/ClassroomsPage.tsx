import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ClipboardList, LoaderCircle, Plus, School, UsersRound } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { PageHeader } from '@/components/app/PageHeader';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import type { ClassroomDetail, ClassroomSummary } from '@/types';

function NewClassroomDialog() {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const navigate = useNavigate();
  const create = useMutation({
    mutationFn: () => api.post<ClassroomDetail>('/classrooms', { name, description }).then(r => r.data),
    onSuccess: c => navigate(`/classrooms/${c.id}`),
    onError: e => toast.error('Could not create the classroom', { description: errorMessage(e) }),
  });
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild><Button><Plus /> New classroom</Button></DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>New classroom</DialogTitle>
          <DialogDescription>You'll get a code to share with your learners.</DialogDescription>
        </DialogHeader>
        <form id="new-class" className="grid gap-4" onSubmit={e => { e.preventDefault(); create.mutate(); }}>
          <div className="grid gap-2">
            <Label htmlFor="nc-name">Name</Label>
            <Input id="nc-name" required maxLength={120} placeholder="e.g. Networking, group A" value={name} onChange={e => setName(e.target.value)} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="nc-desc">Description <span className="font-normal text-muted-foreground">(optional)</span></Label>
            <Textarea id="nc-desc" rows={2} maxLength={2000} value={description} onChange={e => setDescription(e.target.value)} />
          </div>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
          <Button type="submit" form="new-class" disabled={create.isPending}>{create.isPending && <LoaderCircle className="animate-spin" />} Create</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function ClassroomsPage() {
  const list = useQuery({ queryKey: ['classrooms'], queryFn: () => api.get<ClassroomSummary[]>('/classrooms').then(r => r.data) });
  return (
    <>
      <PageHeader title="Classrooms" description="Share a code with learners, assign quizzes with due dates, and follow their results."
                  actions={<NewClassroomDialog />} />
      {list.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2"><Skeleton className="h-36 rounded-xl" /><Skeleton className="h-36 rounded-xl" /></div>
      ) : list.isError ? (
        <p role="alert" className="text-destructive">{errorMessage(list.error)}</p>
      ) : list.data.length === 0 ? (
        <EmptyState icon={School} title="No classrooms yet" description="Create one, share its code, then assign published quizzes." />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2">
          {list.data.map(c => (
            <li key={c.id}>
              <Link to={`/classrooms/${c.id}`} className="block rounded-xl focus-visible:outline-2">
                <Card className="gap-3 transition-all hover:-translate-y-0.5 hover:shadow-md">
                  <CardHeader>
                    <CardTitle>{c.name}</CardTitle>
                    {c.description && <CardDescription className="line-clamp-2">{c.description}</CardDescription>}
                  </CardHeader>
                  <CardContent className="flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
                    <span className="flex items-center gap-1"><UsersRound className="size-4" /> {c.memberCount} learners</span>
                    <span className="flex items-center gap-1"><ClipboardList className="size-4" /> {c.assignmentCount} assignments</span>
                    <span className="ml-auto rounded-md bg-muted px-2 py-0.5 font-mono text-xs tracking-widest text-foreground">{c.joinCode}</span>
                  </CardContent>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
