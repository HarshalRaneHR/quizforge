import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { BookCheck, FilePen, NotebookPen, School, Sparkles, UsersRound } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { PageHeader } from '@/components/app/PageHeader';
import { StatTile } from '@/components/app/StatTile';
import { Columns } from '@/components/charts/Charts';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { ROLE_LABELS } from '@/lib/roles';
import type { PlatformStats, Role } from '@/types';

export default function AdminOverviewPage() {
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: () => api.get<PlatformStats>('/admin/stats').then(r => r.data) });

  const header = <PageHeader title="Overview" description="Platform activity at a glance."
                             actions={<Button asChild variant="outline"><Link to="/admin/users"><UsersRound /> Manage users</Link></Button>} />;
  if (stats.isPending) return <>{header}<Skeleton className="h-64 rounded-xl" /></>;
  if (stats.isError) return <>{header}<p role="alert" className="text-destructive">{errorMessage(stats.error)}</p></>;
  const s = stats.data;
  const roles = Object.entries(s.usersByRole).map(([role, n]) => `${n} ${ROLE_LABELS[role as Role]?.toLowerCase() ?? role}${n === 1 ? '' : 's'}`).join(' · ');
  const days = s.attemptsLast14Days.map(d => ({
    label: new Date(`${d.day}T00:00:00Z`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' }),
    value: d.count,
  }));
  const jobs = s.generationSucceeded + s.generationFailed;

  return (
    <>
      {header}
      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <StatTile icon={UsersRound} label="Users" value={String(s.totalUsers)} hint={roles} />
        <StatTile icon={BookCheck} label="Published quizzes" value={String(s.publishedQuizzes)} hint={`${s.draftQuizzes} in draft`} />
        <StatTile icon={NotebookPen} label="Practice quizzes" value={String(s.practiceQuizzes)} />
        <StatTile icon={FilePen} label="Submitted attempts" value={String(s.submittedAttempts)} />
        <StatTile icon={School} label="Classrooms" value={String(s.classrooms)} />
        <StatTile icon={Sparkles} label="AI generation success" value={jobs ? `${Math.round((100 * s.generationSucceeded) / jobs)}%` : '—'}
                  hint={`${s.generationSucceeded} succeeded · ${s.generationFailed} failed`} />
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Submitted attempts per day</CardTitle>
          <CardDescription>Last 14 days (UTC)</CardDescription>
        </CardHeader>
        <CardContent>
          <Columns data={days} valueName="attempts" height={240}
                   ariaLabel={`Submitted attempts per day over the last 14 days: ${days.map(d => `${d.label} ${d.value}`).join(', ')}`} />
        </CardContent>
      </Card>
    </>
  );
}
