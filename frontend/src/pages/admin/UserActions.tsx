import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { KeyRound, MoreHorizontal, SlidersHorizontal, UserCheck, UserX } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { TemporaryPasswordPanel } from '@/components/admin/TemporaryPasswordPanel';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { PasswordReset, UserView } from '@/types';
import { EditAccessDialog } from './EditAccessDialog';

type Open = 'access' | 'reset' | 'deactivate' | null;

/** Row menu on the Users page: edit access, reset password, deactivate / reactivate. */
export function UserActions({ user }: { user: UserView }) {
  const [open, setOpen] = useState<Open>(null);
  const queryClient = useQueryClient();
  const name = user.fullName || user.email;
  const deactivated = user.status === 'DEACTIVATED';
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin-users'] });

  const reset = useMutation({
    mutationFn: () => api.post<PasswordReset>(`/admin/users/${user.id}/reset-password`).then(r => r.data),
    onSuccess: refresh,
    onError: error => { setOpen(null); toast.error('Could not reset the password', { description: errorMessage(error) }); },
  });

  const setActive = useMutation({
    mutationFn: (active: boolean) => api.post<UserView>(`/admin/users/${user.id}/${active ? 'reactivate' : 'deactivate'}`).then(r => r.data),
    onSuccess: updated => {
      refresh();
      toast.success(updated.status === 'DEACTIVATED' ? `${name} was deactivated` : `${name} can log in again`, {
        description: updated.status === 'DEACTIVATED' ? 'They were signed out everywhere. Their results are kept.' : undefined,
      });
    },
    onError: error => toast.error('Could not change the account', { description: errorMessage(error) }),
  });

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${name}`}><MoreHorizontal /></Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-52">
          {user.role !== 'ADMIN' && (
            <DropdownMenuItem onSelect={() => setOpen('access')}><SlidersHorizontal /> Edit access</DropdownMenuItem>
          )}
          <DropdownMenuItem disabled={deactivated} onSelect={() => setOpen('reset')}><KeyRound /> Reset password</DropdownMenuItem>
          <DropdownMenuSeparator />
          {deactivated ? (
            <DropdownMenuItem onSelect={() => setActive.mutate(true)}><UserCheck /> Reactivate</DropdownMenuItem>
          ) : (
            <DropdownMenuItem variant="destructive" onSelect={() => setOpen('deactivate')}><UserX /> Deactivate</DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {user.role !== 'ADMIN' && (
        <EditAccessDialog user={user} open={open === 'access'} onOpenChange={o => setOpen(o ? 'access' : null)} />
      )}

      {/* Reset: confirm first, then show the new one-time password once. */}
      <AlertDialog open={open === 'reset' && !reset.data} onOpenChange={o => { if (!o && !reset.isPending) setOpen(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Reset password for {name}?</AlertDialogTitle>
            <AlertDialogDescription>
              Their current password stops working and they are signed out everywhere. You get a one-time password to send them.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction disabled={reset.isPending} onClick={e => { e.preventDefault(); reset.mutate(); }}>Reset password</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <Dialog open={open === 'reset' && !!reset.data} onOpenChange={o => { if (!o) { setOpen(null); reset.reset(); } }}>
        <DialogContent onInteractOutside={e => e.preventDefault()}>
          <DialogHeader>
            <DialogTitle>New temporary password</DialogTitle>
            <DialogDescription>Send these login details to {name}.</DialogDescription>
          </DialogHeader>
          {reset.data && <TemporaryPasswordPanel email={user.email} fullName={user.fullName} password={reset.data.temporaryPassword} />}
          <DialogFooter>
            <Button onClick={() => { setOpen(null); reset.reset(); }}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={open === 'deactivate'} onOpenChange={o => setOpen(o ? 'deactivate' : null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Deactivate {name}?</AlertDialogTitle>
            <AlertDialogDescription>
              They are signed out right away and can't log in. Their attempts, grades and quizzes are kept, and you can reactivate them at any time.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => setActive.mutate(false)}>Deactivate</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
