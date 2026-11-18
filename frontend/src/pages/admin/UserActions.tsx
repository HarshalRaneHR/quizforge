import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { KeyRound, MoreHorizontal, SlidersHorizontal, UserCheck, UserX } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { UserView } from '@/types';
import { EditAccessDialog } from './EditAccessDialog';
import { ResetPasswordDialog } from './ResetPasswordDialog';

type Open = 'access' | 'reset' | 'deactivate' | null;

/** Row menu on the Users page: edit access, reset password, deactivate / reactivate. */
export function UserActions({ user }: { user: UserView }) {
  const [open, setOpen] = useState<Open>(null);
  const queryClient = useQueryClient();
  const name = user.fullName || user.email;
  const deactivated = user.status === 'DEACTIVATED';

  const setActive = useMutation({
    mutationFn: (active: boolean) => api.post<UserView>(`/admin/users/${user.id}/${active ? 'reactivate' : 'deactivate'}`).then(r => r.data),
    onSuccess: updated => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
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

      <ResetPasswordDialog user={user} open={open === 'reset'} onOpenChange={o => setOpen(o ? 'reset' : null)} />

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
