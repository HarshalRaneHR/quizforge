import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../api/client';
import type { GenerateQuizRequest, Page, QuizSummary } from '../types';

const MIN_CHARS = 200;

/** Generate a quiz + paginated list of my quizzes. */
export default function Dashboard() {
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<GenerateQuizRequest>({ title: '', sourceText: '', questionCount: 5 });
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const quizzes = useQuery({
    queryKey: ['quizzes', page],
    queryFn: () => api.get<Page<QuizSummary>>('/quizzes', { params: { page, size: 9 } }).then(r => r.data),
    placeholderData: keepPreviousData,   // keeps the old page visible while the next one loads
  });

  const generate = useMutation({
    mutationFn: (body: GenerateQuizRequest) => api.post<QuizSummary>('/quizzes/generate', body).then(r => r.data),
    onSuccess: quiz => {
      queryClient.invalidateQueries({ queryKey: ['quizzes'] });
      navigate(`/quiz/${quiz.id}`);
    },
  });

  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/quizzes/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['quizzes'] }),
  });

  const chars = form.sourceText.length;

  return (
    <main className="page">
      <header className="page-header">
        <h1>Forge a new quiz</h1>
        <p>Paste your study notes and let AI turn them into a multiple-choice quiz.</p>
      </header>

      <form className="card form" onSubmit={e => { e.preventDefault(); generate.mutate(form); }}>
        <div className="field-row">
          <label className="field">
            Title <span className="hint">Optional — AI will suggest one</span>
            <input value={form.title} maxLength={120} placeholder="e.g. Java Basics"
                   onChange={e => setForm({ ...form, title: e.target.value })} />
          </label>
          <label className="field">
            Questions <span className="hint">3 – 15</span>
            <input type="number" min={3} max={15} value={form.questionCount}
                   onChange={e => setForm({ ...form, questionCount: Number(e.target.value) })} />
          </label>
        </div>
        <label className="field">
          Study material
          <textarea required minLength={MIN_CHARS} maxLength={20000} rows={9} value={form.sourceText}
                    placeholder="Paste your notes, an article, or a textbook section…"
                    onChange={e => setForm({ ...form, sourceText: e.target.value })} />
          <span className="hint">
            <span>{chars < MIN_CHARS ? `${MIN_CHARS - chars} more characters needed` : 'Looks good ✓'}</span>
            <span>{chars.toLocaleString()} / 20,000</span>
          </span>
        </label>
        {generate.isError && <p role="alert">{errorMessage(generate.error)}</p>}
        <div>
          <button className="btn btn-primary" disabled={generate.isPending}>
            {generate.isPending ? <><span className="spinner" aria-hidden="true" /> Generating… this can take a minute</> : '✨ Generate quiz'}
          </button>
        </div>
      </form>

      <div className="section-title">
        <h2>My quizzes</h2>
        {quizzes.data && <span className="muted">{quizzes.data.totalElements} total</span>}
      </div>

      {quizzes.isPending ? (
        <p className="center">Loading…</p>
      ) : quizzes.isError ? (
        <p role="alert">{errorMessage(quizzes.error)}</p>
      ) : quizzes.data.content.length === 0 ? (
        <div className="empty">
          <strong>No quizzes yet</strong>
          Generate your first one above.
        </div>
      ) : (
        <>
          <ul className="quiz-grid" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {quizzes.data.content.map(q => (
              <li key={q.id} className="quiz-card">
                <h3><Link to={`/quiz/${q.id}`}>{q.title}</Link></h3>
                <div className="quiz-meta">
                  <span className="chip">{q.questionCount} questions</span>
                  <span>{new Date(q.createdAt).toLocaleDateString()}</span>
                </div>
                <div className="quiz-actions">
                  <Link to={`/quiz/${q.id}`} className="btn btn-ghost">Start →</Link>
                  <button className="btn btn-danger" onClick={() => remove.mutate(q.id)}
                          disabled={remove.isPending} aria-label={`Delete ${q.title}`}>Delete</button>
                </div>
              </li>
            ))}
          </ul>
          {quizzes.data.totalPages > 1 && (
            <div className="pager">
              <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(p => p - 1)}>← Previous</button>
              <span>Page {page + 1} of {quizzes.data.totalPages}</span>
              <button className="btn btn-ghost" disabled={page + 1 >= quizzes.data.totalPages} onClick={() => setPage(p => p + 1)}>Next →</button>
            </div>
          )}
        </>
      )}
    </main>
  );
}
