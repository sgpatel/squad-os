import { useSSE } from '../hooks/useSSE'
import { timeAgo, fmtMs } from '../utils/format'
import { statusBadge } from '../components/ui/Badge'
import { clsx } from 'clsx'

const TYPE_ICONS: Record<string, string> = {
  AGENT_CALL: '🤖',
  APPROVAL:   '✅',
  VOTE:       '🗳️',
  SECURITY:   '🛡️',
  SYSTEM:     '⚙️',
  ERROR:      '❌',
}

const TYPE_COLORS: Record<string, string> = {
  AGENT_CALL: 'border-l-blue-500',
  APPROVAL:   'border-l-emerald-500',
  VOTE:       'border-l-purple-500',
  SECURITY:   'border-l-red-500',
  SYSTEM:     'border-l-slate-500',
  ERROR:      'border-l-red-600',
}

export function Activity() {
  const { events, connected } = useSSE()

  return (
    <div className="space-y-3">
      {/* Connection status */}
      <div className={clsx(
        'flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium',
        connected
          ? 'bg-emerald-900/20 border border-emerald-700/30 text-emerald-400'
          : 'bg-red-900/20 border border-red-700/30 text-red-400'
      )}>
        <div className={clsx('w-2 h-2 rounded-full', connected ? 'bg-emerald-400 animate-pulse' : 'bg-red-500')} />
        {connected ? 'Connected — receiving live events' : 'Disconnected — attempting to reconnect…'}
        <span className="ml-auto text-slate-500">{events.length} events</span>
      </div>

      {/* Event stream */}
      <div className="space-y-2">
        {events.length === 0 && (
          <div className="text-center py-16 text-slate-500 text-sm">
            Waiting for events…
          </div>
        )}
        {events.map(ev => (
          <div
            key={ev.id}
            className={clsx(
              'bg-slate-900 border border-slate-700/60 rounded-xl p-3.5 border-l-4 transition-all',
              TYPE_COLORS[ev.type] ?? 'border-l-slate-600'
            )}
          >
            <div className="flex items-start gap-3">
              <span className="text-lg mt-0.5 flex-shrink-0">{TYPE_ICONS[ev.type] ?? '•'}</span>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-sm font-medium text-slate-200">{ev.message}</span>
                  {statusBadge(ev.status)}
                </div>
                <div className="flex gap-3 mt-1 text-xs text-slate-500 flex-wrap">
                  {ev.agentName && <span>{ev.agentName}</span>}
                  {ev.role && <><span>·</span><span>{ev.role}</span></>}
                  {ev.latencyMs != null && <><span>·</span><span>{fmtMs(ev.latencyMs)}</span></>}
                  {ev.tokens != null && <><span>·</span><span>{ev.tokens} tokens</span></>}
                  <span>·</span>
                  <span>{timeAgo(ev.timestamp)}</span>
                  <span className="ml-auto text-[10px] font-mono text-slate-600">{ev.type}</span>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
