import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';
import { FullPageLoader, PublicOnly, RequireAuth, RequirePermission } from '@/components/app/Guards';
import { PERMISSIONS } from '@/lib/permissions';

// Route-level code splitting: each page is its own JS chunk, loaded on first visit.
const LoginPage = lazy(() => import('@/pages/LoginPage'));
const AppLayout = lazy(() => import('@/components/layout/AppLayout'));
const DashboardPage = lazy(() => import('@/pages/DashboardPage'));
const TakeQuizPage = lazy(() => import('@/pages/TakeQuizPage'));
const HistoryPage = lazy(() => import('@/pages/HistoryPage'));
const UsersPage = lazy(() => import('@/pages/admin/UsersPage'));
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'));

export default function App() {
  return (
    <Suspense fallback={<FullPageLoader />}>
      <Routes>
        <Route path="/login" element={<PublicOnly><LoginPage /></PublicOnly>} />

        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route index element={<DashboardPage />} />
          <Route path="quiz/:id" element={<TakeQuizPage />} />
          <Route path="history" element={<HistoryPage />} />
          <Route path="admin/users" element={
            <RequirePermission permission={PERMISSIONS.USER_READ}><UsersPage /></RequirePermission>
          } />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
