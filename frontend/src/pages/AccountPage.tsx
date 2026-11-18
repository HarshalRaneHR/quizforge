import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Check, LoaderCircle } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { useAuth } from '@/auth/auth-context';
import { ChangePasswordForm } from '@/components/app/ChangePasswordForm';
import { PageHeader } from '@/components/app/PageHeader';
import { RoleBadge } from '@/components/app/RoleBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { PERMISSIONS } from '@/lib/permissions';
import { ROLE_DESCRIPTIONS } from '@/lib/roles';
import type { MeResponse } from '@/types';

/** What each permission lets the person do, in plain words. */
const ABILITIES: Record<string, string> = {
  [PERMISSIONS.QUIZ_GENERATE]: 'Generate practice quizzes with AI',
  [PERMISSIONS.QUIZ_AUTHOR]: 'Create and publish quizzes in the Studio',
  [PERMISSIONS.CLASSROOM_MANAGE]: 'Run classrooms and see gradebooks',
  [PERMISSIONS.QUIZ_MANAGE_ALL]: 'Manage every quiz on the platform',
  [PERMISSIONS.USER_MANAGE]: 'Add and manage users',
};

export default function AccountPage() {
  const { user, updateUser } = useAuth();
  const [fullName, setFullName] = useState(user?.fullName ?? '');

  const saveName = useMutation({
    mutationFn: () => api.put<MeResponse>('/account/profile', { fullName: fullName.trim() }).then(r => r.data),
    onSuccess: me => { updateUser({ fullName: me.fullName }); toast.success('Name saved'); },
  });

  if (!user) return null;
  const abilities = user.permissions.map(p => ABILITIES[p]).filter(Boolean);

  return (
    <>
      <PageHeader title="Account" description="Your profile and password." />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
            <CardDescription>Your name is shown to instructors in classrooms and gradebooks.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <form className="grid gap-4" onSubmit={e => { e.preventDefault(); saveName.mutate(); }}>
              <div className="grid gap-2">
                <Label htmlFor="account-name">Full name</Label>
                <Input id="account-name" required maxLength={80} value={fullName} onChange={e => setFullName(e.target.value)} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="account-email">Email</Label>
                <Input id="account-email" value={user.email} disabled />
                <p className="text-xs text-muted-foreground">Only an administrator can change the email you log in with.</p>
              </div>
              {saveName.isError && (
                <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{errorMessage(saveName.error)}</p>
              )}
              <Button type="submit" variant="secondary" className="justify-self-start"
                      disabled={saveName.isPending || fullName.trim() === (user.fullName ?? '')}>
                {saveName.isPending ? <LoaderCircle className="animate-spin" /> : <Check />} Save name
              </Button>
            </form>
            <div className="grid gap-2 border-t pt-4">
              <div className="flex items-center gap-2 text-sm font-medium">Role <RoleBadge role={user.role} /></div>
              <p className="text-sm text-muted-foreground">{ROLE_DESCRIPTIONS[user.role]}</p>
              <ul className="grid gap-1 text-sm">
                <li className="flex items-center gap-2"><Check className="size-4 text-primary" /> Take quizzes and track your progress</li>
                {abilities.map(a => <li key={a} className="flex items-center gap-2"><Check className="size-4 text-primary" /> {a}</li>)}
              </ul>
            </div>
          </CardContent>
        </Card>

        <Card className="self-start">
          <CardHeader>
            <CardTitle>Password</CardTitle>
            <CardDescription>Changing it signs you out on every other device.</CardDescription>
          </CardHeader>
          <CardContent>
            <ChangePasswordForm onDone={() => toast.success('Password changed', { description: 'Other devices were signed out.' })} />
          </CardContent>
        </Card>
      </div>
    </>
  );
}
