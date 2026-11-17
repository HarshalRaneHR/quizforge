import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { AuthResponse } from '../types';

/** Login + register in one form. */
export default function AuthPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { login } = useAuth();
  const navigate = useNavigate();

  const submit = useMutation({
    mutationFn: () => api.post<AuthResponse>(`/auth/${mode}`, { email, password }).then(r => r.data),
    onSuccess: data => {
      login(data);
      navigate('/');
    },
  });

  const isLogin = mode === 'login';

  return (
    <main className="auth">
      <div className="card auth-card">
        <div className="auth-head">
          <div className="brand-mark" aria-hidden="true">⚡</div>
          <h1>{isLogin ? 'Welcome back' : 'Create your account'}</h1>
          <p>{isLogin ? 'Log in to keep forging quizzes.' : 'Turn your notes into quizzes in seconds.'}</p>
        </div>

        <form className="form" onSubmit={e => { e.preventDefault(); submit.mutate(); }}>
          <label className="field">
            Email
            <input type="email" required autoComplete="email" placeholder="you@example.com"
                   value={email} onChange={e => setEmail(e.target.value)} />
          </label>
          <label className="field">
            Password
            <input type="password" required minLength={8} placeholder="At least 8 characters" value={password}
                   autoComplete={isLogin ? 'current-password' : 'new-password'}
                   onChange={e => setPassword(e.target.value)} />
          </label>
          {submit.isError && <p role="alert">{errorMessage(submit.error)}</p>}
          <button className="btn btn-primary btn-block" disabled={submit.isPending}>
            {submit.isPending && <span className="spinner" aria-hidden="true" />}
            {isLogin ? 'Log in' : 'Sign up'}
          </button>
        </form>

        <p className="auth-switch">
          {isLogin ? 'New here? ' : 'Already have an account? '}
          <button type="button" className="btn-link" onClick={() => { setMode(isLogin ? 'register' : 'login'); submit.reset(); }}>
            {isLogin ? 'Create an account' : 'Log in'}
          </button>
        </p>
      </div>
    </main>
  );
}
