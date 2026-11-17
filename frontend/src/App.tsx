import type { ReactNode } from 'react';
import { Link, Navigate, NavLink, Route, Routes } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from './auth/AuthContext';
import AuthPage from './pages/AuthPage';
import Dashboard from './pages/Dashboard';
import TakeQuiz from './pages/TakeQuiz';
import History from './pages/History';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user ? <>{children}</> : <Navigate to="/login" replace />;
}

const navClass = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? ' active' : ''}`;

export default function App() {
  const { user, logout } = useAuth();
  const queryClient = useQueryClient();

  return (
    <>
      {user && (
        <nav className="nav">
          <Link to="/" className="brand"><span className="brand-mark" aria-hidden="true">⚡</span>QuizForge</Link>
          <NavLink to="/" end className={navClass}>My quizzes</NavLink>
          <NavLink to="/history" className={navClass}>History</NavLink>
          <div className="nav-user">
            <span className="avatar" aria-hidden="true">{user.email[0]}</span>
            <span className="nav-email">{user.email}</span>
            {/* Clear cached data so the next user never sees the previous user's quizzes */}
            <button className="btn btn-ghost" onClick={() => { logout(); queryClient.clear(); }}>Log out</button>
          </div>
        </nav>
      )}
      <Routes>
        <Route path="/login" element={user ? <Navigate to="/" replace /> : <AuthPage />} />
        <Route path="/" element={<RequireAuth><Dashboard /></RequireAuth>} />
        <Route path="/quiz/:id" element={<RequireAuth><TakeQuiz /></RequireAuth>} />
        <Route path="/history" element={<RequireAuth><History /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
