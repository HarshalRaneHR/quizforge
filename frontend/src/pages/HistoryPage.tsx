import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ChartLine, ListChecks, Target, Trophy, type LucideIcon } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { PageHeader } from '@/components/app/PageHeader';
import { Pagination } from '@/components/app/Pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';
import type { AttemptSummary, Page } from '@/types';

const percent = (a: AttemptSummary) => Math.round((a.score / a.total) * 100);

function scoreClass(pct: number) {
  if (pct >= 80) return 'border-emerald-500/25 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300';
  if (pct >= 50) return 'border-amber-500/25 bg-amber-500/10 text-amber-700 dark:text-amber-300';
  return 'border-red-500/25 bg-red-500/10 text-red-700 dark:text-red-300';
}

function Stat({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <Card className="py-5">
      <CardContent className="flex items-center gap-4">
        <span className="grid size-11 place-items-center rounded-lg bg-primary/10 text-primary"><Icon className="size-5" aria-hidden="true" /></span>
        <div>
          <p className="text-2xl font-bold tracking-tight">{value}</p>
          <p className="text-sm text-muted-foreground">{label}</p>
        </div>
      </CardContent>
    </Card>
  );
}

export default function HistoryPage() {
  const [page, setPage] = useState(0);
  const attempts = useQuery({
    queryKey: ['attempts', page],
    queryFn: () => api.get<Page<AttemptSummary>>('/attempts/me', { params: { page, size: 10 } }).then(r => r.data),
    placeholderData: keepPreviousData,
  });

  const header = <PageHeader title="Progress" description="Every quiz attempt you've made, newest first." />;

  if (attempts.isPending) {
    return <>{header}<div className="grid gap-4 sm:grid-cols-3">{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}</div></>;
  }
  if (attempts.isError) return <>{header}<p role="alert" className="text-destructive">{errorMessage(attempts.error)}</p></>;

  const rows = attempts.data.content;
  if (rows.length === 0) {
    return (
      <>
        {header}
        <EmptyState icon={ChartLine} title="No attempts yet" description="Take a quiz and your scores will show up here."
                    action={<Button asChild><Link to="/">Go to my quizzes</Link></Button>} />
      </>
    );
  }

  const pageNote = attempts.data.totalPages > 1 ? ' (this page)' : '';
  const avg = Math.round(rows.reduce((sum, a) => sum + percent(a), 0) / rows.length);
  const best = Math.max(...rows.map(percent));

  return (
    <>
      {header}
      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <Stat icon={ListChecks} label="Total attempts" value={String(attempts.data.totalElements)} />
        <Stat icon={Target} label={`Average score${pageNote}`} value={`${avg}%`} />
        <Stat icon={Trophy} label={`Best score${pageNote}`} value={`${best}%`} />
      </div>

      <Card className={cn('py-0', attempts.isPlaceholderData && 'opacity-60')}>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="pl-6">Quiz</TableHead>
              <TableHead>Score</TableHead>
              <TableHead className="pr-6 text-right">Date</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map(a => (
              <TableRow key={a.id}>
                <TableCell className="pl-6 font-medium">
                  <Link to={`/quiz/${a.quizId}`} className="hover:underline">{a.quizTitle}</Link>
                </TableCell>
                <TableCell>
                  <Badge variant="outline" className={scoreClass(percent(a))}>{percent(a)}%</Badge>
                  <span className="ml-2 text-sm text-muted-foreground">{a.score}/{a.total}</span>
                </TableCell>
                <TableCell className="pr-6 text-right text-muted-foreground">{new Date(a.createdAt).toLocaleString()}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
      <Pagination page={page} totalPages={attempts.data.totalPages} onPageChange={setPage} disabled={attempts.isPlaceholderData} />
    </>
  );
}
