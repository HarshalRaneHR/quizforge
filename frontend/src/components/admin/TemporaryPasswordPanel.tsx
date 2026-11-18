import { useState } from 'react';
import { Check, Copy, KeyRound, TriangleAlert } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';

/**
 * Shows a one-time password exactly once, with a button that copies ready-to-send login details.
 * The server only stores a hash, so once this closes the password can't be shown again (only reset).
 */
export function TemporaryPasswordPanel({ email, fullName, password }: { email: string; fullName?: string | null; password: string }) {
  const [copied, setCopied] = useState(false);

  const details = [
    `Hi${fullName ? ` ${fullName}` : ''}, here is your QuizForge login.`,
    `Address: ${window.location.origin}`,
    `Email: ${email}`,
    `Temporary password: ${password}`,
    'You will be asked to choose your own password when you first log in.',
  ].join('\n');

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(details);
      setCopied(true);
      toast.success('Login details copied', { description: 'Paste them into an email or message to the person.' });
      setTimeout(() => setCopied(false), 2500);
    } catch {
      toast.error('Could not copy automatically', { description: 'Select the password and copy it by hand.' });
    }
  };

  return (
    <div className="grid gap-3">
      <div className="grid gap-1 rounded-lg border bg-muted/40 p-4">
        <span className="text-xs text-muted-foreground">Email</span>
        <span className="font-medium break-all">{email}</span>
        <span className="mt-2 flex items-center gap-1.5 text-xs text-muted-foreground"><KeyRound className="size-3.5" /> Temporary password</span>
        <code className="text-lg font-semibold tracking-wider select-all">{password}</code>
      </div>
      <p className="flex items-start gap-2 rounded-md bg-amber-500/10 px-3 py-2 text-sm text-amber-800 dark:text-amber-300">
        <TriangleAlert className="mt-0.5 size-4 shrink-0" />
        This password is shown only once. Copy it now and share it privately. They will set their own password at first login.
      </p>
      <Button type="button" variant="secondary" onClick={copy}>
        {copied ? <Check /> : <Copy />} {copied ? 'Copied' : 'Copy login details'}
      </Button>
    </div>
  );
}
