import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { LoaderCircle, ShieldCheck, UserPlus } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { AccessCheckboxes } from '@/components/admin/AccessCheckboxes';
import { TemporaryPasswordPanel } from '@/components/admin/TemporaryPasswordPanel';
import { Button } from '@/components/ui/button';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { useAccessModel } from '@/hooks/use-access-model';
import { defaultsFor } from '@/lib/access';
import { ROLE_DESCRIPTIONS, ROLE_LABELS } from '@/lib/roles';
import { ROLES, type CreatedUser, type PermissionName, type Role } from '@/types';

type Form = { fullName: string; email: string; role: Role; permissions: PermissionName[] | null };
const EMPTY: Form = { fullName: '', email: '', role: 'LEARNER', permissions: null };   // null = role defaults

/** Step 1: who and what they can do. Step 2: the one-time password to hand over. */
export function AddUserDialog() {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Form>(EMPTY);
  const model = useAccessModel(open);
  const queryClient = useQueryClient();

  const defaults = defaultsFor(model.data, form.role);
  const chosen = form.permissions ?? defaults;   // checkboxes start at the role's defaults

  const create = useMutation({
    mutationFn: () => api.post<CreatedUser>('/admin/users', {
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      role: form.role,
      // Admins always have full access; for others send exactly what's ticked.
      permissions: form.role === 'ADMIN' ? undefined : chosen,
    }).then(r => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-users'] }),
  });

  const close = () => {
    setOpen(false);
    setForm(EMPTY);
    create.reset();
  };
  const created = create.data;

  return (
    <Dialog open={open} onOpenChange={o => (o ? setOpen(true) : close())}>
      <DialogTrigger asChild>
        <Button><UserPlus /> Add user</Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg"
                     // Don't lose the one-time password to a stray click outside.
                     onInteractOutside={e => { if (created) e.preventDefault(); }}>
        {created ? (
          <>
            <DialogHeader>
              <DialogTitle>{ROLE_LABELS[created.user.role]} account created</DialogTitle>
              <DialogDescription>Send these login details to {created.user.fullName || created.user.email}.</DialogDescription>
            </DialogHeader>
            <TemporaryPasswordPanel email={created.user.email} fullName={created.user.fullName} password={created.temporaryPassword} />
            <DialogFooter>
              <Button variant="outline" onClick={() => { setForm(EMPTY); create.reset(); }}>Add another</Button>
              <Button onClick={close}>Done</Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>Add a user</DialogTitle>
              <DialogDescription>
                QuizForge creates a one-time password for them. They choose their own password at first login.
              </DialogDescription>
            </DialogHeader>
            <form id="add-user-form" className="grid gap-4" onSubmit={e => { e.preventDefault(); create.mutate(); }}>
              <div className="grid gap-2">
                <Label htmlFor="new-name">Full name</Label>
                <Input id="new-name" required maxLength={80} autoComplete="off" placeholder="Priya Sharma"
                       value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="new-email">Email</Label>
                <Input id="new-email" type="email" required autoComplete="off" placeholder="priya@school.edu"
                       value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} />
                <p className="text-xs text-muted-foreground">They log in with this email.</p>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="new-role">Role</Label>
                {/* Changing the role resets the checkboxes to that role's defaults. */}
                <Select value={form.role} onValueChange={v => setForm({ ...form, role: v as Role, permissions: null })}>
                  <SelectTrigger id="new-role" className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {ROLES.map(r => <SelectItem key={r} value={r}>{ROLE_LABELS[r]}</SelectItem>)}
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">{ROLE_DESCRIPTIONS[form.role]}</p>
              </div>

              <fieldset className="grid gap-2">
                <legend className="mb-2 text-sm font-medium">What can this user do?</legend>
                {form.role === 'ADMIN' ? (
                  <p className="flex items-center gap-2 rounded-lg border p-3 text-sm text-muted-foreground">
                    <ShieldCheck className="size-4 shrink-0 text-primary" /> Admins always have full access, including user management.
                  </p>
                ) : model.isPending ? (
                  <Skeleton className="h-40" />
                ) : model.data ? (
                  <AccessCheckboxes idPrefix="new" adjustable={model.data.adjustable} defaults={defaults} role={form.role}
                                    value={chosen} onChange={next => setForm({ ...form, permissions: next })} />
                ) : null}
              </fieldset>

              {create.isError && (
                <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{errorMessage(create.error)}</p>
              )}
            </form>
            <DialogFooter>
              <Button variant="outline" onClick={close}>Cancel</Button>
              <Button type="submit" form="add-user-form" disabled={create.isPending}>
                {create.isPending && <LoaderCircle className="animate-spin" />} Create user
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
