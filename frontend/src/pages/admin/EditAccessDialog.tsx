import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { LoaderCircle, SlidersHorizontal } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { AccessCheckboxes } from '@/components/admin/AccessCheckboxes';
import { Button } from '@/components/ui/button';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { useAccessModel } from '@/hooks/use-access-model';
import { defaultsFor } from '@/lib/access';
import { ROLE_LABELS } from '@/lib/roles';
import type { PermissionName, UserView } from '@/types';

/** Turn a single user's abilities on or off. Takes effect on their next request. */
export function EditAccessDialog({ user }: { user: UserView }) {
  const [open, setOpen] = useState(false);
  const model = useAccessModel(open);
  const adjustable = model.data?.adjustable ?? [];
  const current = user.permissions.filter(p => adjustable.includes(p));
  const [value, setValue] = useState<PermissionName[] | null>(null);   // null = untouched (show current)
  const chosen = value ?? current;
  const defaults = defaultsFor(model.data, user.role);
  const queryClient = useQueryClient();

  const save = useMutation({
    mutationFn: (permissions: PermissionName[]) => api.put<UserView>(`/admin/users/${user.id}/permissions`, { permissions }).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      toast.success(`Access updated for ${user.email}`, { description: 'It applies on their next action, no need to log out.' });
      setOpen(false);
    },
  });

  return (
    <Dialog open={open} onOpenChange={o => { setOpen(o); if (!o) { setValue(null); save.reset(); } }}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="icon-sm" aria-label={`Edit access for ${user.email}`}><SlidersHorizontal /></Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Edit access</DialogTitle>
          <DialogDescription>{user.email} · {ROLE_LABELS[user.role]}</DialogDescription>
        </DialogHeader>
        {model.isPending ? <Skeleton className="h-40" /> : (
          <AccessCheckboxes idPrefix={`edit-${user.id}`} adjustable={adjustable} defaults={defaults} role={user.role}
                            value={chosen} onChange={setValue} />
        )}
        {save.isError && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{errorMessage(save.error)}</p>
        )}
        <DialogFooter className="sm:justify-between">
          <Button variant="ghost" onClick={() => setValue(defaults)}>Reset to {ROLE_LABELS[user.role]} defaults</Button>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button disabled={save.isPending} onClick={() => save.mutate(chosen)}>
              {save.isPending && <LoaderCircle className="animate-spin" />} Save
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
