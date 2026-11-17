import { useQuery } from '@tanstack/react-query';
import { ChartColumn, Target, TriangleAlert, UsersRound } from 'lucide-react';
import { api } from '@/api/client';
import { StatTile } from '@/components/app/StatTile';
import { Columns } from '@/components/charts/Charts';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { TYPE_LABEL } from '@/lib/questions';
import type { QuizAnalytics } from '@/types';

/** "Too hard" threshold: under 40% of learners get it fully right — worth reviewing the wording. */
const HARD = 40;

export function QuizAnalyticsCard({ quizId }: { quizId: number }) {
  const q = useQuery({ queryKey: ['quiz-analytics', quizId], queryFn: () => api.get<QuizAnalytics>(`/studio/quizzes/${quizId}/analytics`).then(r => r.data) });

  if (q.isPending) return <Skeleton className="h-48 rounded-xl" />;
  if (!q.data) return null;
  const a = q.data;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><ChartColumn className="size-5 text-primary" /> Results</CardTitle>
        <CardDescription>How learners did (your own previews are excluded).</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {a.attempts === 0 ? (
          <p className="text-sm text-muted-foreground">No learner has submitted this quiz yet.</p>
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              <StatTile icon={UsersRound} label="Attempts" value={String(a.attempts)} hint={`${a.learners} learner${a.learners === 1 ? '' : 's'}`} />
              <StatTile icon={Target} label="Average score" value={`${a.averagePercentage}%`} />
              <StatTile icon={Target} label="Pass rate" value={a.passRatePercentage === null || a.passRatePercentage === undefined ? '—' : `${a.passRatePercentage}%`}
                        hint={a.passPercentage ? `pass mark ${a.passPercentage}%` : 'no pass mark set'} />
            </div>

            <div>
              <h3 className="mb-2 text-sm font-medium">Score distribution</h3>
              <Columns valueName="attempts"
                       data={a.distribution.map(b => ({ label: `${b.from}`, value: b.count, detail: `${b.label}%` }))}
                       ariaLabel={`Score distribution: ${a.distribution.filter(b => b.count > 0).map(b => `${b.count} attempts scored ${b.label}%`).join(', ')}`} />
            </div>

            <div>
              <h3 className="mb-2 text-sm font-medium">Questions</h3>
              <div className="overflow-x-auto rounded-lg border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-10">#</TableHead>
                      <TableHead>Question</TableHead>
                      <TableHead className="w-44">Fully correct</TableHead>
                      <TableHead>Most common wrong answer</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {a.questions.map(s => {
                      const pct = s.fullyCorrectPercentage;
                      return (
                        <TableRow key={s.questionId}>
                          <TableCell className="text-muted-foreground">{s.position}</TableCell>
                          <TableCell className="max-w-72">
                            <p className="truncate font-medium" title={s.prompt}>{s.prompt}</p>
                            <p className="text-xs text-muted-foreground">{TYPE_LABEL[s.type]} · {s.answered} answered · avg {s.averagePercentage ?? 0}% of points</p>
                          </TableCell>
                          <TableCell>
                            {pct === null || pct === undefined ? '—' : (
                              <div className="flex items-center gap-2">
                                {/* inline bar = the table's own chart; the number is always printed */}
                                <div className="h-2 w-20 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                                  <div className="h-full rounded-full bg-chart-1" style={{ width: `${pct}%` }} />
                                </div>
                                <span className="text-sm tabular-nums">{pct}%</span>
                                {pct < HARD && (
                                  <Badge variant="outline" className="border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300">
                                    <TriangleAlert /> Hard
                                  </Badge>
                                )}
                              </div>
                            )}
                          </TableCell>
                          <TableCell className="text-sm">
                            {s.commonWrongAnswer ? <>{s.commonWrongAnswer} <span className="text-muted-foreground">({s.commonWrongCount}×)</span></> : <span className="text-muted-foreground">—</span>}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
