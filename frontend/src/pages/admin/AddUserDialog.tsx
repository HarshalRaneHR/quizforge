import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { LoaderCircle, UserPlus } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { Button } from '@/components/ui/button';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ROLE_DESCRIPTIONS, ROLE_LABELS } from '@/lib/roles';
import { ROLES, type Role, type UserView } from '@/types';

const EMPTY = { email: '', password: '', role: 'INSTRUCTOR' as Role };

export function AddUserDialog() {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const queryClient = useQueryClient();

  const create = useMutation({
    mutationFn: () => api.post<UserView>('/admin/users', form).then(r => r.data),
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
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Add a user</DialogTitle>
          <DialogDescription>Create an account directly, for example an instructor. Share the temporary password with them.</DialogDescription>
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
            <Select value={form.role} onValueChange={v => setForm({ ...form, role: v as Role })}>
              <SelectTrigger id="new-role" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {ROLES.map(r => <SelectItem key={r} value={r}>{ROLE_LABELS[r]}</SelectItem>)}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">{ROLE_DESCRIPTIONS[form.role]}</p>
          </div>
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
