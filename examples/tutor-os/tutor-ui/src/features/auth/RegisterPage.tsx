import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { Loader2, UserPlus } from 'lucide-react';
import { useAuth } from '@/store/auth';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { AuthLayout } from './AuthLayout';

/**
 * Register page — collects just enough to build a meaningful
 * LearnerProfile for the backend on first session start:
 *
 *   name · email · password · level · goal
 *
 * Everything else (analogy domain, sessions-per-week, profession) is
 * editable later from Settings → Profile.
 */
export function RegisterPage() {
  const navigate = useNavigate();
  const register = useAuth(s => s.register);
  const isAuthed = useAuth(s => s.currentUserId != null);

  const [name, setName]         = useState('');
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [level, setLevel]       = useState('higher-sec');
  const [goal, setGoal]         = useState('');
  const [busy, setBusy]         = useState(false);
  const [err, setErr]           = useState<string | null>(null);

  if (isAuthed) return <Navigate to="/" replace />;

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setErr(null);
    try {
      await register({
        name, email, password, level,
        goal: goal.trim() || 'Build a strong conceptual foundation.',
      });
      navigate('/', { replace: true });
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Tell us a little about how you learn — your tutor will calibrate to it."
      footer={
        <>
          Already have an account? <Link to="/login" className="auth-link">Sign in</Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="auth-form" noValidate>
        <label className="auth-field">
          <span className="auth-label">Your name</span>
          <Input
            type="text"
            autoComplete="name"
            placeholder="Priya Sharma"
            value={name}
            onChange={e => setName(e.target.value)}
            required
            autoFocus
          />
        </label>
        <label className="auth-field">
          <span className="auth-label">Email</span>
          <Input
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={e => setEmail(e.target.value)}
            required
          />
        </label>
        <label className="auth-field">
          <span className="auth-label">Password</span>
          <Input
            type="password"
            autoComplete="new-password"
            placeholder="At least 6 characters"
            value={password}
            onChange={e => setPassword(e.target.value)}
            required
            minLength={6}
          />
        </label>
        <label className="auth-field">
          <span className="auth-label">Current level</span>
          <select
            className="input"
            value={level}
            onChange={e => setLevel(e.target.value)}
          >
            <option value="primary">Primary school</option>
            <option value="middle">Middle school</option>
            <option value="higher-sec">Higher secondary</option>
            <option value="college">College / undergraduate</option>
            <option value="pro">Working professional</option>
          </select>
        </label>
        <label className="auth-field">
          <span className="auth-label">Goal <span className="auth-hint">(optional)</span></span>
          <Input
            type="text"
            placeholder="e.g. Crack JEE 2027 · Get AWS SAA-C03 · Build ML fundamentals"
            value={goal}
            onChange={e => setGoal(e.target.value)}
          />
        </label>

        {err && <p className="auth-error" role="alert">{err}</p>}

        <Button type="submit" variant="primary" disabled={busy}>
          {busy
            ? <><Loader2 size={14} className="spin" /> Creating account…</>
            : <><UserPlus size={14} /> Create account</>}
        </Button>
      </form>
    </AuthLayout>
  );
}
