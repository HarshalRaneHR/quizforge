import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Search, UserX } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/app/EmptyState';
import { PageHeader } from '@/components/app/PageHeader';
import { Pagination } from '@/components/app/Pagination';
import { RoleBadge } from '@/components/app/RoleBadge';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { PERMISSIONS } from '@/lib/permissions';
import { ROLE_LABELS } from '@/lib/roles';
import { cn } from '@/lib/utils';
import { ROLES, type Page, type Role, type UserView } from '@/types';
import { AddUserDialog } from './AddUserDialog';

const ALL = 'ALL';

function RoleSelect({ user }: { user: UserView }) {
  const queryClient = useQueryClient();
  const change = useMutation({
    mutationFn: (role: Role) => api.patch<UserView>(`/admin/users/${user.id}/role`, { role }).then(r => r.data),
    onSuccess: updated => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      toast.success(`${updated.email} is now ${ROLE_LABELS[updated.role]}`);
    },
    onError: error => toast.error('Could not change the role', { description: errorMessage(error) }),
  });

  return (
    <Select value={user.role} onValueChange={v => change.mutate(v as Role)} disabled={change.isPending}>
      <SelectTrigger size="sm" className="w-36" aria-label={`Role for ${user.email}`}><SelectValue /></SelectTrigger>
      <SelectContent>
        {ROLES.map(r => <SelectItem key={r} value={r}>{ROLE_LABELS[r]}</SelectItem>)}
      </SelectContent>
    </Select>
  );
}

export default function UsersPage() {
  const { user: me, can } = useAuth();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState<Role | typeof ALL>(ALL);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search.trim());

  const users = useQuery({
    queryKey: ['admin-users', debouncedSearch, role, page],
    queryFn: () => api.get<Page<UserView>>('/admin/users', {
      params: { search: debouncedSearch || undefined, role: role === ALL ? undefined : role, page, size: 20 },
    }).then(r => r.data),
    placeholderData: keepPreviousData,
  });

  const canManage = can(PERMISSIONS.USER_MANAGE);

  return (
    <>
      <PageHeader title="Users" description="Search accounts and manage what each person can do."
                  actions={canManage && <AddUserDialog />} />

      <div className="mb-4 flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input type="search" className="pl-9" placeholder="Search by email…" aria-label="Search users by email"
                 value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} />
        </div>
        <Select value={role} onValueChange={v => { setRole(v as Role | typeof ALL); setPage(0); }}>
          <SelectTrigger className="sm:w-44" aria-label="Filter by role"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All roles</SelectItem>
            {ROLES.map(r => <SelectItem key={r} value={r}>{ROLE_LABELS[r]}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      {users.isPending ? (
        <Skeleton className="h-72 rounded-xl" />
      ) : users.isError ? (
        <p role="alert" className="text-destructive">{errorMessage(users.error)}</p>
      ) : users.data.content.length === 0 ? (
        <EmptyState icon={UserX} title="No users found" description="Try a different search or role filter." />
      ) : (
        <>
          <Card className={cn('py-0', users.isPlaceholderData && 'opacity-60')}>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">User</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead className="pr-6 text-right">Joined</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.data.content.map(u => {
                  const isMe = u.email === me?.email;
                  return (
                    <TableRow key={u.id}>
                      <TableCell className="pl-6">
                        <div className="flex items-center gap-3">
                          <Avatar className="size-8">
                            <AvatarFallback className="bg-primary/10 text-xs font-semibold text-primary uppercase">{u.email[0]}</AvatarFallback>
                          </Avatar>
                          <span className="font-medium">{u.email}</span>
                          {isMe && <Badge variant="secondary">You</Badge>}
                        </div>
                      </TableCell>
                      <TableCell>
                        {/* Admins can't change their own role (the API refuses too). */}
                        {canManage && !isMe ? <RoleSelect user={u} /> : <RoleBadge role={u.role} />}
                      </TableCell>
                      <TableCell className="pr-6 text-right text-muted-foreground">{new Date(u.createdAt).toLocaleDateString()}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </Card>
          <Pagination page={page} totalPages={users.data.totalPages} onPageChange={setPage} disabled={users.isPlaceholderData} />
        </>
      )}
    </>
  );
}
