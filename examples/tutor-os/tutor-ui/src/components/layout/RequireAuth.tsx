import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/store/auth';

/**
 * Route guard — if no one is logged in, redirect to /login and remember
 * the URL the learner tried to reach so we can send them back there
 * after a successful sign-in.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const isAuthed = useAuth(s => s.currentUserId != null);
  const location = useLocation();

  if (!isAuthed) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname + location.search }}
      />
    );
  }
  return <>{children}</>;
}
