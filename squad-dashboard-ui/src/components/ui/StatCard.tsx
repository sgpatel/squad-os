import { clsx } from 'clsx'

interface StatCardProps {
  label: string
  value: string | number
  sub?: string
  icon?: React.ReactNode
  trend?: 'up' | 'down' | 'neutral'
  trendValue?: string
  color?: 'blue' | 'green' | 'red' | 'amber' | 'purple' | 'cyan'
}

const COLORS = {
  blue:   'from-blue-600/20 to-blue-900/10 border-blue-700/30',
  green:  'from-emerald-600/20 to-emerald-900/10 border-emerald-700/30',
  red:    'from-red-600/20 to-red-900/10 border-red-700/30',
  amber:  'from-amber-600/20 to-amber-900/10 border-amber-700/30',
  purple: 'from-purple-600/20 to-purple-900/10 border-purple-700/30',
  cyan:   'from-cyan-600/20 to-cyan-900/10 border-cyan-700/30',
}

const ICON_COLORS = {
  blue:   'text-blue-400',
  green:  'text-emerald-400',
  red:    'text-red-400',
  amber:  'text-amber-400',
  purple: 'text-purple-400',
  cyan:   'text-cyan-400',
}

export function StatCard({ label, value, sub, icon, trend, trendValue, color = 'blue' }: StatCardProps) {
  return (
    <div className={clsx(
      'relative rounded-xl p-5 bg-gradient-to-br border shadow-lg overflow-hidden',
      COLORS[color]
    )}>
      <div className="flex items-start justify-between">
        <div className="flex-1 min-w-0">
          <p className="text-xs font-medium text-slate-400 uppercase tracking-wider truncate">{label}</p>
          <p className="mt-1 text-2xl font-bold text-white tabular-nums">{value}</p>
          {sub && <p className="mt-0.5 text-xs text-slate-400">{sub}</p>}
          {trendValue && (
            <p className={clsx(
              'mt-1.5 text-xs font-medium',
              trend === 'up'   ? 'text-emerald-400' :
              trend === 'down' ? 'text-red-400' : 'text-slate-400'
            )}>
              {trend === 'up' ? '▲' : trend === 'down' ? '▼' : '—'} {trendValue}
            </p>
          )}
        </div>
        {icon && (
          <div className={clsx('ml-3 flex-shrink-0 text-2xl opacity-80', ICON_COLORS[color])}>
            {icon}
          </div>
        )}
      </div>
    </div>
  )
}
