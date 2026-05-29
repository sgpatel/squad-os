import { useEffect, useSyncExternalStore } from 'react';
import { CheckCircle2, AlertCircle, Sparkles, X } from 'lucide-react';

/**
 * `<Toaster />` — minimal global notifications.
 *
 * Mounted once at the AppShell level. Anywhere in the app can call
 * {@link toast} (see exports below) to surface a small floating message
 * in the bottom-right corner. Toasts auto-dismiss after a configurable
 * timeout and can be dismissed by click.
 *
 * Why hand-rolled (not radix-toast / sonner / react-hot-toast):
 *   - the app intentionally has zero runtime dependencies on UI kits
 *     (look at `components/ui/` — every primitive is local), and
 *   - the surface needed for "+12% Trigonometry mastery" announcements
 *     is small enough that pulling in a 5-15 KB lib for it would be
 *     out of proportion. ~2 KB minified for this whole file.
 *
 * Mechanics:
 *   - subscribe to the {@link toastBus} via {@link useSyncExternalStore}
 *     so React rerenders only when toast state actually changes
 *   - the bus is a plain singleton, so toasts can fire from any module
 *     (zustand actions, fetch error handlers, etc.) without prop drilling
 *   - aria-live="polite" + role="status" so screen readers get the
 *     announcement once when each toast appears.
 */

// ── Types ──────────────────────────────────────────────────────────

export type ToastKind = 'success' | 'info' | 'error' | 'mastery';

export interface ToastInput {
  kind?: ToastKind;
  title?: string;
  body: string;
  /** ms before auto-dismiss. 0 disables auto-dismiss. Default 4000. */
  durationMs?: number;
}

interface Toast extends Required<ToastInput> {
  id: string;
}

// ── External store (singleton) ─────────────────────────────────────

const listeners = new Set<() => void>();
let toasts: Toast[] = [];

const toastBus = {
  getSnapshot(): Toast[] { return toasts; },
  subscribe(fn: () => void): () => void {
    listeners.add(fn);
    return () => { listeners.delete(fn); };
  },
  push(input: ToastInput): string {
    const t: Toast = {
      id:         `toast_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`,
      kind:       input.kind     ?? 'info',
      title:      input.title    ?? '',
      body:       input.body,
      durationMs: input.durationMs ?? 4000,
    };
    toasts = [...toasts, t];
    listeners.forEach(fn => fn());
    return t.id;
  },
  dismiss(id: string): void {
    toasts = toasts.filter(t => t.id !== id);
    listeners.forEach(fn => fn());
  },
};

// ── Public API ─────────────────────────────────────────────────────

/**
 * Push a toast onto the global queue. Safe to call from any module
 * (no React context required). Returns the toast id so the caller can
 * dismiss programmatically (rare — auto-dismiss is the default).
 */
export function toast(input: ToastInput): string {
  return toastBus.push(input);
}

/**
 * Convenience for the M3 mastery-delta announcement on Quiz / Review
 * submit. Renders as a pill-style "kind=mastery" toast with a sparkle
 * icon and a sign-aware delta ("+10%" / "−5%").
 */
export function masteryToast(opts: {
  concept: string;
  deltaPct: number;
  scorePct?: number;
}): string {
  const sign = opts.deltaPct >= 0 ? '+' : '−';
  const abs  = Math.round(Math.abs(opts.deltaPct));
  const tail = opts.scorePct != null ? ` · now ${Math.round(opts.scorePct)}%` : '';
  return toast({
    kind:  'mastery',
    title: `${sign}${abs}% ${opts.concept}`,
    body:  `Mastery updated${tail}`,
    durationMs: 4500,
  });
}

// ── Component ──────────────────────────────────────────────────────

export function Toaster() {
  const items = useSyncExternalStore(toastBus.subscribe, toastBus.getSnapshot);

  return (
    <div className="toaster" role="region" aria-label="Notifications">
      {items.map(t => <ToastItem key={t.id} t={t} />)}
    </div>
  );
}

function ToastItem({ t }: { t: Toast }) {
  // Auto-dismiss timer. We re-set on every mount; if the consumer
  // pushes the same toast twice the second one gets its own timer.
  useEffect(() => {
    if (t.durationMs <= 0) return;
    const id = window.setTimeout(() => toastBus.dismiss(t.id), t.durationMs);
    return () => window.clearTimeout(id);
  }, [t.id, t.durationMs]);

  return (
    <div
      className={`toast toast--${t.kind}`}
      role="status"
      aria-live="polite"
      onClick={() => toastBus.dismiss(t.id)}
    >
      <span className="toast__icon" aria-hidden>
        {t.kind === 'success'   ? <CheckCircle2 size={16} />
         : t.kind === 'error'   ? <AlertCircle  size={16} />
         : t.kind === 'mastery' ? <Sparkles     size={16} />
         :                        <CheckCircle2 size={16} />}
      </span>
      <div className="toast__body">
        {t.title && <strong className="toast__title">{t.title}</strong>}
        <span className="toast__text">{t.body}</span>
      </div>
      <button
        type="button"
        className="toast__close"
        aria-label="Dismiss"
        onClick={(e) => { e.stopPropagation(); toastBus.dismiss(t.id); }}
      >
        <X size={12} />
      </button>
    </div>
  );
}
