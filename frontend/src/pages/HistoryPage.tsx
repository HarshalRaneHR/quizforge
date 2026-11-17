import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ChartLine, Flame, ListChecks, Play, Target, Trophy } from 'lucide-react';
import { HorizontalBars, TrendChart } from '@/components/charts/Charts';
import { StatTile } from '@/components/app/StatTile';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { PageHeader } from '@/components/app/PageHeader';
import { Pagination } from '@/components/app/Pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatPoints, TYPE_LABEL } from '@/lib/questions';
import { cn } from '@/lib/utils';
import type { AttemptSummary, MyAnalytics, Page } from '@/types';

const percent = (a: AttemptSummary) => a.percentage;

function scoreClass(pct: number) {
  if (pct >= 80) return 'border-emerald-500/25 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300';
  if (pct >= 50) return 'border-amber-500/25 bg-amber-500/10 text-amber-700 dark:text-amber-300';
  return 'border-red-500/25 bg-red-500/10 text-red-700 dark:text-red-300';
}

/** All-time stats and charts (from /analytics/me, not just the current page of the table). */
function Insights({ data }: { data: MyAnalytics }) {
  const trend = data.trend.map((t, i) => ({
    label: new Date(t.submittedAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
    value: t.percentage,
    detail: `#${i + 1} · ${t.quizTitle}`,
  }));
  const byType = data.byType.map(t => ({ label: TYPE_LABEL[t.type], value: t.accuracyPercentage, detail: `${t.answered} answered` }));
  return (
    <>
      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile icon={ListChecks} label="Quizzes completed" value={String(data.attempts)} hint={`${data.quizzesTaken} different quizzes`} />
        <StatTile icon={Target} label="Average score" value={data.averagePercentage === null || data.averagePercentage === undefined ? '—' : `${data.averagePercentage}%`} />
        <StatTile icon={Trophy} label="Best score" value={data.bestPercentage === null || data.bestPercentage === undefined ? '—' : `${data.bestPercentage}%`} />
        <StatTile icon={Flame} label="Day streak" value={String(data.streakDays)} hint={data.streakDays > 0 ? 'Keep it going!' : 'Take a quiz today'} />
      </div>
      {data.attempts > 0 && (
        <div className="mb-6 grid gap-4 lg:grid-cols-5">
          <Card className="lg:col-span-3">
            <CardHeader>
              <CardTitle>Score trend</CardTitle>
              <CardDescription>Your last {trend.length} submitted attempts</CardDescription>
            </CardHeader>
            <CardContent>
              <TrendChart data={trend} ariaLabel={`Score trend over your last ${trend.length} attempts: ${trend.map(t => `${t.value}%`).join(', ')}`} />
            </CardContent>
          </Card>
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>Accuracy by question type</CardTitle>
              <CardDescription>
                {data.weakestType ? <>Focus area: <span className="font-medium text-foreground">{TYPE_LABEL[data.weakestType]}</span></> : 'Share of points earned per type'}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <HorizontalBars data={byType} ariaLabel={`Accuracy by question type: ${byType.map(b => `${b.label} ${b.value}%`).join(', ')}`} />
            </CardContent>
          </Card>
        </div>
      )}
    </>
  );
}

export default function HistoryPage() {
  const [page, setPage] = useState(0);
  const analytics = useQuery({ queryKey: ['attempts', 'analytics'], queryFn: () => api.get<MyAnalytics>('/analytics/me').then(r => r.data) });
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

  return (
    <>
      {header}
      {analytics.data ? <Insights data={analytics.data} /> : <Skeleton className="mb-6 h-24 rounded-xl" />}
      <h2 className="mb-3 text-lg font-semibold">All attempts</h2>

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
                  {a.status === 'IN_PROGRESS'
                    ? <Link to={`/quiz/${a.quizId}`} className="hover:underline">{a.quizTitle}</Link>
                    : <Link to={`/attempts/${a.id}`} className="hover:underline">{a.quizTitle}</Link>}
                </TableCell>
                <TableCell>
                  {a.status === 'IN_PROGRESS' ? (
                    <Button asChild size="xs" variant="outline"><Link to={`/quiz/${a.quizId}`}><Play /> Resume</Link></Button>
                  ) : (
                    <span className="flex flex-wrap items-center gap-2">
                      <Badge variant="outline" className={scoreClass(percent(a))}>{percent(a)}%</Badge>
                      <span className="text-sm text-muted-foreground">{formatPoints(a.score)}/{formatPoints(a.maxScore)} pts</span>
                      {a.passed !== null && a.passed !== undefined && (
                        <Badge variant="outline" className={a.passed ? scoreClass(100) : scoreClass(0)}>{a.passed ? 'Passed' : 'Not passed'}</Badge>
                      )}
                    </span>
                  )}
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
