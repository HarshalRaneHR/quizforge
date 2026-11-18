import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Eye, EyeOff, LoaderCircle } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { TemporaryPasswordPanel } from '@/components/admin/TemporaryPasswordPanel';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import type { PasswordReset, UserView } from '@/types';

type Mode = 'generate' | 'set';

/**
 * Two ways to reset: QuizForge generates a one-time password (shown once), or the admin types one.
 * A typed password can optionally skip the "choose your own password" step at next login.
 */
export function ResetPasswordDialog({ user, open, onOpenChange }: { user: UserView; open: boolean; onOpenChange: (open: boolean) => void }) {
  const [mode, setMode] = useState<Mode>('generate');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [requireChange, setRequireChange] = useState(true);
  const queryClient = useQueryClient();
  const name = user.fullName || user.email;

  const reset = useMutation({
    mutationFn: () => api.post<PasswordReset>(`/admin/users/${user.id}/reset-password`,
      mode === 'set' ? { password, requireChange } : {}).then(r => r.data),
    onSuccess: data => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      if (!data.temporaryPassword) {
        toast.success(`Password updated for ${name}`, {
          description: requireChange
            ? 'They were signed out and must choose their own password at next login.'
            : 'They were signed out and can log in with the new password.',
        });
        close();
      }
    },
  });

  function close() {
    onOpenChange(false);
    setMode('generate'); setPassword(''); setShow(false); setRequireChange(true);
    reset.reset();
  }

  const generated = reset.data?.temporaryPassword;

  return (
    <Dialog open={open} onOpenChange={o => (o ? onOpenChange(true) : close())}>
      <DialogContent className="sm:max-w-md" onInteractOutside={e => { if (generated) e.preventDefault(); }}>
        {generated ? (
          <>
            <DialogHeader>
              <DialogTitle>New temporary password</DialogTitle>
              <DialogDescription>Send these login details to {name}.</DialogDescription>
            </DialogHeader>
            <TemporaryPasswordPanel email={user.email} fullName={user.fullName} password={generated} />
            <DialogFooter><Button onClick={close}>Done</Button></DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>Reset password for {name}</DialogTitle>
              <DialogDescription>Their current password stops working and they are signed out everywhere.</DialogDescription>
            </DialogHeader>
            <form id="reset-password-form" className="grid gap-4" onSubmit={e => { e.preventDefault(); reset.mutate(); }}>
              <RadioGroup value={mode} onValueChange={v => setMode(v as Mode)} className="gap-2">
                <Label htmlFor="reset-generate"
                       className="flex cursor-pointer items-start gap-3 rounded-lg border p-3 font-normal has-data-[state=checked]:border-primary/60 has-data-[state=checked]:bg-accent/50">
                  <RadioGroupItem id="reset-generate" value="generate" className="mt-0.5" />
                  <span className="grid gap-0.5">
                    <span className="text-sm font-medium">Generate a one-time password <span className="text-xs font-normal text-muted-foreground">recommended</span></span>
                    <span className="text-xs text-muted-foreground">Shown once for you to send. They choose their own at next login.</span>
                  </span>
                </Label>
                <Label htmlFor="reset-set"
                       className="flex cursor-pointer items-start gap-3 rounded-lg border p-3 font-normal has-data-[state=checked]:border-primary/60 has-data-[state=checked]:bg-accent/50">
                  <RadioGroupItem id="reset-set" value="set" className="mt-0.5" />
                  <span className="grid gap-0.5">
                    <span className="text-sm font-medium">Set a password myself</span>
                    <span className="text-xs text-muted-foreground">Type the new password and tell it to them.</span>
                  </span>
                </Label>
              </RadioGroup>

              {mode === 'set' && (
                <div className="grid gap-3 rounded-lg border bg-muted/30 p-3">
                  <div className="grid gap-2">
                    <Label htmlFor="reset-password">New password</Label>
                    <div className="relative">
                      <Input id="reset-password" type={show ? 'text' : 'password'} required minLength={8} maxLength={72}
                             autoComplete="new-password" placeholder="At least 8 characters" className="pr-10"
                             value={password} onChange={e => setPassword(e.target.value)} />
                      <Button type="button" variant="ghost" size="icon-sm" className="absolute top-1/2 right-1 -translate-y-1/2"
                              aria-label={show ? 'Hide password' : 'Show password'} onClick={() => setShow(s => !s)}>
                        {show ? <EyeOff /> : <Eye />}
                      </Button>
                    </div>
                  </div>
                  <Label htmlFor="reset-require-change" className="flex cursor-pointer items-start gap-2 font-normal">
                    <Checkbox id="reset-require-change" className="mt-0.5" checked={requireChange}
                              onCheckedChange={c => setRequireChange(c === true)} />
                    <span className="grid gap-0.5">
                      <span className="text-sm">Ask them to choose their own password at next login</span>
                      <span className="text-xs text-muted-foreground">Recommended, so only they know their password.</span>
                    </span>
                  </Label>
                </div>
              )}

              {reset.isError && (
                <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{errorMessage(reset.error)}</p>
              )}
            </form>
            <DialogFooter>
              <Button variant="outline" onClick={close}>Cancel</Button>
              <Button type="submit" form="reset-password-form" disabled={reset.isPending}>
                {reset.isPending && <LoaderCircle className="animate-spin" />} Reset password
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
