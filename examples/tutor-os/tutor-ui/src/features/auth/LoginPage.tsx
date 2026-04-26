import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Loader2, LogIn } from 'lucide-react';
import { useAuth } from '@/store/auth';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { AuthLayout } from './AuthLayout';

/**
 * Login page — email + password.
 *
 * On success we redirect either to the `from` location the guard captured
 * (e.g. the page the learner tried to reach pre-auth), or home.
 */
export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login    = useAuth(s => s.login);
  const isAuthed = useAuth(s => s.currentUserId != null);

  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy]         = useState(false);
  const [err, setErr]           = useState<string | null>(null);

  if (isAuthed) return <Navigate to="/" replace />;

  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setErr(null);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in to pick up where you left off."
      footer={
        <>
          New to TutorOS? <Link to="/register" className="auth-link">Create an account</Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="auth-form" noValidate>
        <label className="auth-field">
          <span className="auth-label">Email</span>
          <Input
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={e => setEmail(e.target.value)}
            required
            autoFocus
          />
        </label>
        <label className="auth-field">
          <span className="auth-label">Password</span>
          <Input
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            value={password}
            onChange={e => setPassword(e.target.value)}
            required
          />
        </label>

        {err && <p className="auth-error" role="alert">{err}</p>}

        <Button type="submit" variant="primary" disabled={busy}>
          {busy
            ? <><Loader2 size={14} className="spin" /> Signing in…</>
            : <><LogIn size={14} /> Sign in</>}
        </Button>
      </form>
    </AuthLayout>
  );
}
