import { clsx } from 'clsx'

export function TopBar({
  title,
  subtitle,
  connected,
  lastRefresh,
  dataMode,
}: {
  title: string
  subtitle?: string
  connected: boolean
  lastRefresh?: Date
  dataMode?: 'redis' | 'simulation'
}) {
  return (
    <header className="h-14 flex-shrink-0 flex items-center justify-between px-6 bg-slate-950/80 border-b border-slate-800 backdrop-blur-sm">
      <div>
        <h1 className="text-base font-semibold text-slate-100">{title}</h1>
        {subtitle && <p className="text-xs text-slate-500">{subtitle}</p>}
      </div>
      <div className="flex items-center gap-4">
        {/* Data source badge */}
        {dataMode && (
          <span className={clsx(
            'px-2 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wider border',
            dataMode === 'redis'
              ? 'bg-emerald-900/40 text-emerald-400 border-emerald-700/40'
              : 'bg-amber-900/40 text-amber-400 border-amber-700/40'
          )}>
            {dataMode === 'redis' ? '⬢ Redis' : '◎ Simulation'}
          </span>
        )}
        {lastRefresh && (
          <span className="text-xs text-slate-500">
            Updated {lastRefresh.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
          </span>
        )}
        <div className="flex items-center gap-1.5">
          <div className={clsx(
            'w-2 h-2 rounded-full',
            connected ? 'bg-emerald-400 animate-pulse' : 'bg-red-500'
          )} />
          <span className={clsx(
            'text-xs font-medium',
            connected ? 'text-emerald-400' : 'text-red-400'
          )}>
            {connected ? 'Live' : 'Offline'}
          </span>
        </div>
      </div>
    </header>
  )
}
