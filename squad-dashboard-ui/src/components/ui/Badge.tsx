import { clsx } from 'clsx'

type Variant = 'success' | 'error' | 'warning' | 'info' | 'neutral' | 'purple'

const VARIANTS: Record<Variant, string> = {
  success: 'bg-emerald-900/50 text-emerald-300 border border-emerald-700/50',
  error:   'bg-red-900/50 text-red-300 border border-red-700/50',
  warning: 'bg-amber-900/50 text-amber-300 border border-amber-700/50',
  info:    'bg-blue-900/50 text-blue-300 border border-blue-700/50',
  neutral: 'bg-slate-800 text-slate-300 border border-slate-600',
  purple:  'bg-purple-900/50 text-purple-300 border border-purple-700/50',
}

export function Badge({ label, variant = 'neutral', className }: {
  label: string
  variant?: Variant
  className?: string
}) {
  return (
    <span className={clsx(
      'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium',
      VARIANTS[variant],
      className
    )}>
      {label}
    </span>
  )
}

export function statusBadge(status: string) {
  const map: Record<string, Variant> = {
    ACTIVE: 'success', IDLE: 'neutral', ERROR: 'error', RATE_LIMITED: 'warning',
    OK: 'success', success: 'success', error: 'error', warning: 'warning', info: 'info',
    APPROVED: 'success', REJECTED: 'error', PENDING: 'warning', EXPIRED: 'neutral',
    RUNNING: 'info', PAUSED: 'warning', COMPLETED: 'success', FAILED: 'error',
    UP: 'success', DEGRADED: 'warning', DOWN: 'error',
    LOW: 'neutral', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'error',
  }
  return <Badge label={status} variant={map[status] ?? 'neutral'} />
}
