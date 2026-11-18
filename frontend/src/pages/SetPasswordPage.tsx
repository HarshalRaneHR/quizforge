import { Navigate, useNavigate } from 'react-router-dom';
import { LogOut, ShieldCheck } from 'lucide-react';
import { toast } from 'sonner';
import { displayName, useAuth } from '@/auth/auth-context';
import { ChangePasswordForm } from '@/components/app/ChangePasswordForm';
import { Logo } from '@/components/app/Logo';
import { ThemeToggle } from '@/components/app/ThemeToggle';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

/**
 * First login (or after an admin reset): the person must replace the one-time password before
 * using QuizForge. The server refuses every other request until this is done.
 */
export default function SetPasswordPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (user && !user.mustChangePassword) return <Navigate to="/" replace />;

  return (
    <main className="relative flex min-h-svh flex-col items-center justify-center gap-6 p-6">
      <div className="absolute top-4 right-4"><ThemeToggle /></div>
      <Logo textClassName="text-lg" />
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl"><ShieldCheck className="size-5 text-primary" /> Set your password</CardTitle>
          <CardDescription>
            Welcome{user ? `, ${displayName(user)}` : ''}. Your administrator gave you a temporary password.
            Choose your own to continue. Only you will know it.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <ChangePasswordForm currentLabel="Temporary password" submitLabel="Save and continue"
                              onDone={() => { toast.success('Password set. Welcome to QuizForge!'); navigate('/', { replace: true }); }} />
          <Button variant="ghost" size="sm" className="justify-self-center text-muted-foreground" onClick={() => logout()}>
            <LogOut /> Log out
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
