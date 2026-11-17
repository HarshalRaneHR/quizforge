import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../api/client';
import type { AttemptSummary, Page } from '../types';

const percent = (a: AttemptSummary) => Math.round((a.score / a.total) * 100);
const level = (pct: number) => (pct >= 80 ? 'high' : pct >= 50 ? 'mid' : 'low');

export default function History() {
  const [page, setPage] = useState(0);
  const attempts = useQuery({
    queryKey: ['attempts', page],
    queryFn: () => api.get<Page<AttemptSummary>>('/attempts/me', { params: { page, size: 10 } }).then(r => r.data),
    placeholderData: keepPreviousData,
  });

  if (attempts.isPending) return <p className="center">Loading…</p>;
  if (attempts.isError) return <main className="page"><p role="alert">{errorMessage(attempts.error)}</p></main>;

  const rows = attempts.data.content;
  // Stats cover the attempts on this page.
  const avg = rows.length ? Math.round(rows.reduce((sum, a) => sum + percent(a), 0) / rows.length) : 0;
  const best = rows.length ? Math.max(...rows.map(percent)) : 0;

  return (
    <main className="page">
      <header className="page-header">
        <h1>My progress</h1>
        <p>Every quiz attempt you've made, newest first.</p>
      </header>

      {rows.length === 0 ? (
        <div className="empty">
          <strong>No attempts yet</strong>
          <Link to="/">Take a quiz</Link> to start tracking your progress.
        </div>
      ) : (
        <>
          <div className="stats">
            <div className="card stat">
              <div className="stat-value">{attempts.data.totalElements}</div>
              <div className="stat-label">Total attempts</div>
            </div>
            <div className="card stat">
              <div className="stat-value">{avg}%</div>
              <div className="stat-label">Average score{attempts.data.totalPages > 1 ? ' (this page)' : ''}</div>
            </div>
            <div className="card stat">
              <div className="stat-value">{best}%</div>
              <div className="stat-label">Best score{attempts.data.totalPages > 1 ? ' (this page)' : ''}</div>
            </div>
          </div>

          <div className="card table-wrap">
            <table>
              <thead><tr><th>Quiz</th><th>Score</th><th>Date</th></tr></thead>
              <tbody>
                {rows.map(a => (
                  <tr key={a.id}>
                    <td><Link to={`/quiz/${a.quizId}`}>{a.quizTitle}</Link></td>
                    <td>
                      <span className={`score-pill ${level(percent(a))}`}>{percent(a)}%</span>
                      <span className="muted"> {a.score}/{a.total}</span>
                    </td>
                    <td className="muted">{new Date(a.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {attempts.data.totalPages > 1 && (
            <div className="pager">
              <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(p => p - 1)}>← Previous</button>
              <span>Page {page + 1} of {attempts.data.totalPages}</span>
              <button className="btn btn-ghost" disabled={page + 1 >= attempts.data.totalPages} onClick={() => setPage(p => p + 1)}>Next →</button>
            </div>
          )}
        </>
      )}
    </main>
  );
}
