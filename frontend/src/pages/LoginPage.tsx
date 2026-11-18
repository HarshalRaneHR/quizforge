import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ChartLine, LoaderCircle, ShieldCheck, Sparkles } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { useAuth } from '@/auth/auth-context';
import { Logo } from '@/components/app/Logo';
import { ThemeToggle } from '@/components/app/ThemeToggle';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { AuthConfig, AuthResponse } from '@/types';

const FEATURES = [
  { icon: Sparkles, title: 'AI-generated quizzes', text: 'Paste your notes and get a quiz in seconds.' },
  { icon: ShieldCheck, title: 'Fair, server-side grading', text: 'Answers stay hidden until you submit.' },
  { icon: ChartLine, title: 'Track your progress', text: 'See scores and improvement over time.' },
];

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  // Invite-only unless the server says public sign-up is open.
  const config = useQuery({
    queryKey: ['auth-config'],
    queryFn: () => api.get<AuthConfig>('/auth/config').then(r => r.data),
    staleTime: Infinity,
  });
  const signupOpen = config.data?.publicSignup === true;

  const submit = useMutation({
    mutationFn: () => api.post<AuthResponse>(`/auth/${mode}`,
      mode === 'register' ? { email, password, fullName: fullName.trim() || undefined } : { email, password }).then(r => r.data),
    onSuccess: data => {
      login(data);
      // Accounts made by an admin go to "Set your password" first (the route guard enforces it too).
      navigate(data.mustChangePassword ? '/set-password' : from, { replace: true });
    },
  });

  const isLogin = mode === 'login' || !signupOpen;

  return (
    <div className="grid min-h-svh lg:grid-cols-2">
      {/* Brand panel (large screens) */}
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-linear-to-br from-violet-700 via-indigo-700 to-fuchsia-700 p-10 text-white lg:flex">
        <div className="pointer-events-none absolute -top-24 -right-24 size-96 rounded-full bg-fuchsia-400/30 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -left-16 size-96 rounded-full bg-sky-400/20 blur-3xl" />
        <Logo className="relative text-white" textClassName="text-lg" />
        <div className="relative max-w-md space-y-8">
          <h2 className="text-4xl leading-tight font-bold tracking-tight">Turn your notes into quizzes in seconds.</h2>
          <ul className="space-y-5">
            {FEATURES.map(f => (
              <li key={f.title} className="flex gap-4">
                <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-white/15 backdrop-blur">
                  <f.icon className="size-5" aria-hidden="true" />
                </span>
                <div>
                  <p className="font-semibold">{f.title}</p>
                  <p className="text-sm text-white/75">{f.text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <p className="relative text-sm text-white/60">React · Spring Boot · Spring AI</p>
      </aside>

      {/* Form */}
      <main className="relative flex flex-col items-center justify-center gap-6 p-6">
        <div className="absolute top-4 right-4"><ThemeToggle /></div>
        <Logo className="lg:hidden" textClassName="text-lg" />
        <Card className="w-full max-w-sm">
          <CardHeader>
            <CardTitle className="text-xl">{isLogin ? 'Welcome back' : 'Create your account'}</CardTitle>
            <CardDescription>{isLogin ? 'Log in to continue to QuizForge.' : 'Start turning notes into quizzes.'}</CardDescription>
          </CardHeader>
          <CardContent>
            <form id="auth-form" className="grid gap-4" onSubmit={e => { e.preventDefault(); submit.mutate(); }}>
              {!isLogin && (
                <div className="grid gap-2">
                  <Label htmlFor="fullName">Full name</Label>
                  <Input id="fullName" autoComplete="name" maxLength={80} placeholder="Your name"
                         value={fullName} onChange={e => setFullName(e.target.value)} />
                </div>
              )}
              <div className="grid gap-2">
                <Label htmlFor="email">Email</Label>
                <Input id="email" type="email" required autoComplete="email" placeholder="you@example.com"
                       value={email} onChange={e => setEmail(e.target.value)} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="password">Password</Label>
                <Input id="password" type="password" required minLength={8} placeholder="At least 8 characters"
                       autoComplete={isLogin ? 'current-password' : 'new-password'}
                       value={password} onChange={e => setPassword(e.target.value)} />
              </div>
              {submit.isError && (
                <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {errorMessage(submit.error)}
                </p>
              )}
            </form>
          </CardContent>
          <CardFooter className="flex-col gap-3">
            <Button type="submit" form="auth-form" className="w-full" disabled={submit.isPending}>
              {submit.isPending && <LoaderCircle className="animate-spin" />}
              {isLogin ? 'Log in' : 'Create account'}
            </Button>
            {signupOpen ? (
              <p className="text-sm text-muted-foreground">
                {isLogin ? 'New to QuizForge? ' : 'Already have an account? '}
                <button type="button" className="font-medium text-primary underline-offset-4 hover:underline"
                        onClick={() => { setMode(isLogin ? 'register' : 'login'); submit.reset(); }}>
                  {isLogin ? 'Create an account' : 'Log in'}
                </button>
              </p>
            ) : config.isSuccess && (
              <p className="text-center text-sm text-muted-foreground">
                Need an account or forgot your password? Ask your administrator.
              </p>
            )}
          </CardFooter>
        </Card>
      </main>
    </div>
  );
}
