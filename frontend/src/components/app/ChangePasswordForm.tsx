import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { LoaderCircle } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { useAuth } from '@/auth/auth-context';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { AuthResponse } from '@/types';

/**
 * Current password + new password twice. On success the server signs out every other session and
 * returns a fresh one for this browser, which we adopt straight away.
 */
export function ChangePasswordForm({ currentLabel = 'Current password', submitLabel = 'Change password', onDone }: {
  currentLabel?: string; submitLabel?: string; onDone?: () => void;
}) {
  const { login } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const mismatch = confirm.length > 0 && next !== confirm;

  const change = useMutation({
    mutationFn: () => api.post<AuthResponse>('/account/password', { currentPassword: current, newPassword: next }).then(r => r.data),
    onSuccess: data => {
      login(data);
      setCurrent(''); setNext(''); setConfirm('');
      onDone?.();
    },
  });

  return (
    <form className="grid gap-4" onSubmit={e => { e.preventDefault(); if (!mismatch) change.mutate(); }}>
      <div className="grid gap-2">
        <Label htmlFor="current-password">{currentLabel}</Label>
        <Input id="current-password" type="password" required autoComplete="current-password"
               value={current} onChange={e => setCurrent(e.target.value)} />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="new-password">New password</Label>
        <Input id="new-password" type="password" required minLength={8} maxLength={72} autoComplete="new-password"
               placeholder="At least 8 characters" value={next} onChange={e => setNext(e.target.value)} />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="confirm-password">Confirm new password</Label>
        <Input id="confirm-password" type="password" required autoComplete="new-password" aria-invalid={mismatch}
               value={confirm} onChange={e => setConfirm(e.target.value)} />
        {mismatch && <p className="text-xs text-destructive">The passwords don't match.</p>}
      </div>
      {change.isError && (
        <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{errorMessage(change.error)}</p>
      )}
      <Button type="submit" disabled={change.isPending || mismatch}>
        {change.isPending && <LoaderCircle className="animate-spin" />} {submitLabel}
      </Button>
    </form>
  );
}
