import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../api/client';
import type { AttemptResult, QuizDetail } from '../types';

const LETTERS = ['A', 'B', 'C', 'D'];

function scoreMessage(pct: number) {
  if (pct === 100) return 'Perfect score! 🎉';
  if (pct >= 80) return 'Great job! 🔥';
  if (pct >= 50) return 'Not bad — review the explanations below.';
  return 'Keep practising — the explanations below will help.';
}

/** fieldset/legend + native radios: fully keyboard and screen-reader accessible. */
export default function TakeQuiz() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [answers, setAnswers] = useState<Record<number, number>>({});

  const quiz = useQuery({
    queryKey: ['quiz', id],
    queryFn: () => api.get<QuizDetail>(`/quizzes/${id}`).then(r => r.data),
  });

  const submit = useMutation({
    mutationFn: (body: { answers: number[] }) =>
      api.post<AttemptResult>(`/quizzes/${id}/attempts`, body).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attempts'] });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
  });

  if (quiz.isPending) return <p className="center">Loading quiz…</p>;
  if (quiz.isError) return <main className="page"><p role="alert">{errorMessage(quiz.error)}</p></main>;

  const { questions } = quiz.data;
  const result = submit.data;
  const answered = Object.keys(answers).length;
  const allAnswered = questions.every((_, i) => answers[i] !== undefined);
  const pct = result ? Math.round((result.score / result.total) * 100) : 0;

  return (
    <main className="page">
      <header className="page-header">
        <Link to="/" className="muted">← Back to my quizzes</Link>
        <h1 style={{ marginTop: '0.5rem' }}>{quiz.data.title}</h1>
        {!result && (
          <>
            <div className="progress" aria-hidden="true">
              <span style={{ width: `${(answered / questions.length) * 100}%` }} />
            </div>
            <div className="progress-label">
              <span>{answered} of {questions.length} answered</span>
              <span>{Math.round((answered / questions.length) * 100)}%</span>
            </div>
          </>
        )}
      </header>

      {result && (
        <div className="score-banner" role="status">
          <div className="score-ring">{pct}%</div>
          <div>
            <h2>You scored {result.score} / {result.total}</h2>
            <p>{scoreMessage(pct)}</p>
          </div>
        </div>
      )}

      <form onSubmit={e => { e.preventDefault(); submit.mutate({ answers: questions.map((_, i) => answers[i]) }); }}>
        {questions.map((q, i) => {
          const r = result?.results[i];
          return (
            <fieldset key={q.id} className="question" disabled={!!result}>
              <legend><span className="qnum">Q{i + 1}.</span>{q.prompt}</legend>
              <div className="options">
                {q.options.map((option, j) => {
                  const state = r ? (j === r.correct ? 'is-correct' : j === r.chosen ? 'is-wrong' : '') : '';
                  return (
                    <label key={j} className={`option ${state}`}>
                      <input type="radio" name={`q-${q.id}`} checked={answers[i] === j}
                             onChange={() => setAnswers({ ...answers, [i]: j })} />
                      <span className="letter" aria-hidden="true">{LETTERS[j]}</span>
                      <span>{option}</span>
                      {state === 'is-correct' && <span className="option-tag">✓ Correct answer</span>}
                      {state === 'is-wrong' && <span className="option-tag">✗ Your answer</span>}
                    </label>
                  );
                })}
              </div>
              {r && (
                <p className={`explanation ${r.isCorrect ? 'good' : 'bad'}`}>
                  <strong>{r.isCorrect ? 'Correct. ' : 'Incorrect. '}</strong>{r.explanation}
                </p>
              )}
            </fieldset>
          );
        })}

        {submit.isError && <p role="alert">{errorMessage(submit.error)}</p>}
        <div className="sticky-actions">
          {result ? (
            <>
              <Link to="/history" className="btn btn-ghost" style={{ background: 'var(--surface)' }}>View history</Link>
              <button type="button" className="btn btn-primary"
                      onClick={() => { setAnswers({}); submit.reset(); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>
                ↻ Try again
              </button>
            </>
          ) : (
            <button className="btn btn-primary" disabled={!allAnswered || submit.isPending}>
              {submit.isPending && <span className="spinner" aria-hidden="true" />}
              Submit answers
            </button>
          )}
        </div>
      </form>
    </main>
  );
}
