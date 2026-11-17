import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { FilePlus2, LoaderCircle, PencilRuler, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { PageHeader } from '@/components/app/PageHeader';
import { Pagination } from '@/components/app/Pagination';
import { StatusBadge } from '@/components/app/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';
import type { Page, QuizStatus, QuizSummary, StudioQuiz } from '@/types';

const FILTERS: { value: QuizStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' }, { value: 'DRAFT', label: 'Drafts' },
  { value: 'PUBLISHED', label: 'Published' }, { value: 'ARCHIVED', label: 'Archived' },
];

function NewQuizDialog() {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const navigate = useNavigate();
  const create = useMutation({
    mutationFn: () => api.post<StudioQuiz>('/studio/quizzes', { title, description }).then(r => r.data),
    onSuccess: quiz => navigate(`/studio/${quiz.id}`),
    onError: error => toast.error('Could not create the quiz', { description: errorMessage(error) }),
  });
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild><Button><Plus /> New quiz</Button></DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>New quiz</DialogTitle>
          <DialogDescription>It starts as a draft. Add questions by hand or with AI, then publish it.</DialogDescription>
        </DialogHeader>
        <form id="new-quiz" className="grid gap-4" onSubmit={e => { e.preventDefault(); create.mutate(); }}>
          <div className="grid gap-2">
            <Label htmlFor="nq-title">Title</Label>
            <Input id="nq-title" required maxLength={120} value={title} onChange={e => setTitle(e.target.value)} placeholder="e.g. Networking basics" />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="nq-desc">Description <span className="font-normal text-muted-foreground">(optional)</span></Label>
            <Textarea id="nq-desc" maxLength={2000} rows={3} value={description} onChange={e => setDescription(e.target.value)}
                      placeholder="What does this quiz cover? Learners see this in Explore." />
          </div>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
          <Button type="submit" form="new-quiz" disabled={create.isPending}>
            {create.isPending && <LoaderCircle className="animate-spin" />} Create draft
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function StudioPage() {
  const [filter, setFilter] = useState<QuizStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);

  const quizzes = useQuery({
    queryKey: ['quizzes', 'PUBLIC', filter, page],
    queryFn: () => api.get<Page<QuizSummary>>('/quizzes', {
      params: { visibility: 'PUBLIC', status: filter === 'ALL' ? undefined : filter, page, size: 15, sort: 'updatedAt,desc' },
    }).then(r => r.data),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader title="Studio" description="Build quizzes for your learners: draft, review, publish." actions={<NewQuizDialog />} />

      <div className="mb-4 flex flex-wrap gap-1" role="tablist" aria-label="Filter by status">
        {FILTERS.map(f => (
          <Button key={f.value} role="tab" aria-selected={filter === f.value} size="sm"
                  variant={filter === f.value ? 'secondary' : 'ghost'} onClick={() => { setFilter(f.value); setPage(0); }}>
            {f.label}
          </Button>
        ))}
      </div>

      {quizzes.isPending ? (
        <Skeleton className="h-64 rounded-xl" />
      ) : quizzes.isError ? (
        <p role="alert" className="text-destructive">{errorMessage(quizzes.error)}</p>
      ) : quizzes.data.content.length === 0 ? (
        <EmptyState icon={filter === 'ALL' ? FilePlus2 : PencilRuler}
                    title={filter === 'ALL' ? 'No quizzes yet' : `No ${filter.toLowerCase()} quizzes`}
                    description="Create a quiz, add questions by hand or generate them with AI, then publish it for learners." />
      ) : (
        <>
          <Card className={cn('py-0', quizzes.isPlaceholderData && 'opacity-60')}>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">Quiz</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="hidden sm:table-cell">Questions</TableHead>
                  <TableHead className="pr-6 text-right">Updated</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {quizzes.data.content.map(q => (
                  <TableRow key={q.id}>
                    <TableCell className="pl-6 font-medium"><Link to={`/studio/${q.id}`} className="hover:underline">{q.title}</Link></TableCell>
                    <TableCell><StatusBadge status={q.status} /></TableCell>
                    <TableCell className="hidden text-muted-foreground sm:table-cell">{q.questionCount} · {q.totalPoints} pts</TableCell>
                    <TableCell className="pr-6 text-right text-muted-foreground">{new Date(q.updatedAt ?? q.createdAt).toLocaleDateString()}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
          <Pagination page={page} totalPages={quizzes.data.totalPages} onPageChange={setPage} disabled={quizzes.isPlaceholderData} />
        </>
      )}
    </>
  );
}
