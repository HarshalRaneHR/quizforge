import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { LoaderCircle, ShieldCheck, UserPlus } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { AccessCheckboxes } from '@/components/admin/AccessCheckboxes';
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
import { ROLES, type PermissionName, type Role, type UserView } from '@/types';

type Form = { email: string; password: string; role: Role; permissions: PermissionName[] | null };
const EMPTY: Form = { email: '', password: '', role: 'INSTRUCTOR', permissions: null };   // null = role defaults

export function AddUserDialog() {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Form>(EMPTY);
  const model = useAccessModel(open);
  const queryClient = useQueryClient();

  const defaults = defaultsFor(model.data, form.role);
  const chosen = form.permissions ?? defaults;   // checkboxes start at the role's defaults

  const create = useMutation({
    mutationFn: () => api.post<UserView>('/admin/users', {
      email: form.email,
      password: form.password,
      role: form.role,
      // Admins always have full access; for others send exactly what's ticked.
      permissions: form.role === 'ADMIN' ? undefined : chosen,
    }).then(r => r.data),
    onSuccess: user => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      toast.success(`${ROLE_LABELS[user.role]} account created`, {
        description: `${user.email} can now log in with the temporary password.`,
      });
      setOpen(false);
      setForm(EMPTY);
    },
  });

  return (
    <Dialog open={open} onOpenChange={o => { setOpen(o); if (!o) create.reset(); }}>
      <DialogTrigger asChild>
        <Button><UserPlus /> Add user</Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Add a user</DialogTitle>
          <DialogDescription>Create an account, choose a role, then decide exactly what this person can do.</DialogDescription>
        </DialogHeader>
        <form id="add-user-form" className="grid gap-4" onSubmit={e => { e.preventDefault(); create.mutate(); }}>
          <div className="grid gap-2">
            <Label htmlFor="new-email">Email</Label>
            <Input id="new-email" type="email" required autoComplete="off" placeholder="teacher@school.edu"
                   value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="new-password">Temporary password</Label>
            <Input id="new-password" type="text" required minLength={8} maxLength={72} autoComplete="new-password"
                   placeholder="At least 8 characters"
                   value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} />
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
          <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
          <Button type="submit" form="add-user-form" disabled={create.isPending}>
            {create.isPending && <LoaderCircle className="animate-spin" />} Create user
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
