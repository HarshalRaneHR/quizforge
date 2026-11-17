import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { LoaderCircle, ShieldAlert } from 'lucide-react';
import { useAuth } from '@/auth/auth-context';
import { Button } from '@/components/ui/button';
import type { Permission } from '@/lib/permissions';
import { EmptyState } from './EmptyState';

export function FullPageLoader() {
  return (
    <div className="grid min-h-svh place-items-center" role="status" aria-label="Loading">
      <LoaderCircle className="size-6 animate-spin text-muted-foreground" />
    </div>
  );
}

/** Logged-in users only; remembers where they were going. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <FullPageLoader />;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

/** Login page: logged-in users go straight to the app. */
export function PublicOnly({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  if (status === 'loading') return <FullPageLoader />;
  if (status === 'authenticated') return <Navigate to="/" replace />;
  return <>{children}</>;
}

/** UI-level gate. The backend enforces the same permission on every request. */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const { can } = useAuth();
  if (!can(permission)) {
    return (
      <EmptyState
        icon={ShieldAlert}
        title="You don't have access to this page"
        description="Ask an administrator if you think you should."
        action={<Button asChild variant="outline"><Link to="/">Back to my quizzes</Link></Button>}
      />
    );
  }
  return <>{children}</>;
}
