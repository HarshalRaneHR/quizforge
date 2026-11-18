import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Compass, ListChecks, Play, Search, UserRound } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { EmptyState } from '@/components/app/EmptyState';
import { LIBRARY_AUTHOR, LibraryBadge } from '@/components/app/LibraryBadge';
import { PageHeader } from '@/components/app/PageHeader';
import { Pagination } from '@/components/app/Pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { cn } from '@/lib/utils';
import type { CatalogQuiz, Page } from '@/types';

export default function ExplorePage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());

  const quizzes = useQuery({
    queryKey: ['explore', debounced, page],
    queryFn: () => api.get<Page<CatalogQuiz>>('/quizzes/explore', { params: { search: debounced || undefined, page, size: 12 } }).then(r => r.data),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader title="Explore" description="Quizzes published by instructors. Pick one and test yourself." />
      <div className="relative mb-6 max-w-md">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
        <Input type="search" className="pl-9" placeholder="Search quizzes by title…" aria-label="Search quizzes"
               value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} />
      </div>

      {quizzes.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
          {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-48 rounded-xl" />)}
        </div>
      ) : quizzes.isError ? (
        <p role="alert" className="text-destructive">{errorMessage(quizzes.error)}</p>
      ) : quizzes.data.content.length === 0 ? (
        <EmptyState icon={Compass} title={debounced ? 'No quizzes match your search' : 'No published quizzes yet'}
                    description={debounced ? 'Try another word.' : 'When instructors publish quizzes, they appear here.'} />
      ) : (
        <>
          <ul className={cn('grid gap-4 sm:grid-cols-2 lg:grid-cols-3', quizzes.isPlaceholderData && 'opacity-60')}>
            {quizzes.data.content.map(q => (
              <li key={q.id} className="flex">
                <Card className="w-full gap-3 transition-all hover:-translate-y-0.5 hover:shadow-md">
                  <CardHeader>
                    {q.builtIn && <div><LibraryBadge /></div>}
                    <CardTitle className="line-clamp-2 leading-snug">{q.title}</CardTitle>
                    {q.description && <CardDescription className="line-clamp-2">{q.description}</CardDescription>}
                  </CardHeader>
                  <CardContent className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
                    <Badge variant="secondary"><ListChecks /> {q.questionCount} questions</Badge>
                    <span>{q.totalPoints} pts</span>
                    <span className="flex min-w-0 items-center gap-1"><UserRound className="size-3.5 shrink-0" aria-hidden="true" /><span className="truncate">{q.builtIn ? LIBRARY_AUTHOR : q.author}</span></span>
                  </CardContent>
                  <CardFooter className="mt-auto">
                    <Button asChild size="sm"><Link to={`/quiz/${q.id}`}><Play /> Start quiz</Link></Button>
                  </CardFooter>
                </Card>
              </li>
            ))}
          </ul>
          <Pagination page={page} totalPages={quizzes.data.totalPages} onPageChange={setPage} disabled={quizzes.isPlaceholderData} />
        </>
      )}
    </>
  );
}
