import { useCallback, useState } from 'react'
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts'
import { useApi } from '../hooks/useApi'
import { api } from '../utils/api'
import { timeAgo } from '../utils/format'
import { Card, CardHeader } from '../components/ui/Card'
import { LoadingOverlay } from '../components/ui/Spinner'
import { statusBadge } from '../components/ui/Badge'
import type { SecurityEvent } from '../types'

const SEV_COLORS: Record<string, string> = {
  LOW: '#64748b', MEDIUM: '#3b82f6', HIGH: '#f59e0b', CRITICAL: '#ef4444'
}

const TYPE_ICONS: Record<string, string> = {
  AUTH_FAILURE:        '🔐',
  GUARDRAIL_BLOCK:     '🛡️',
  RATE_LIMIT:          '⏱️',
  INJECTION_DETECTED:  '💉',
  PII_REDACTED:        '🔏',
  ACCESS_DENIED:       '🚫',
}

export function Security() {
  const { data: events, loading } = useApi<SecurityEvent[]>(
    useCallback(() => api.security(100) as Promise<SecurityEvent[]>, [])
  )
  const [severityFilter, setSeverityFilter] = useState<string>('ALL')
  const [typeFilter, setTypeFilter] = useState<string>('ALL')

  if (loading || !events) return <LoadingOverlay />

  const filtered = events.filter(e =>
    (severityFilter === 'ALL' || e.severity === severityFilter) &&
    (typeFilter === 'ALL' || e.type === typeFilter)
  )

  const byType = Object.entries(
    events.reduce((acc, e) => { acc[e.type] = (acc[e.type] ?? 0) + 1; return acc }, {} as Record<string, number>)
  ).map(([name, value]) => ({ name, value }))

  const bySeverity = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(sev => ({
    severity: sev,
    count: events.filter(e => e.severity === sev).length
  }))

  return (
    <div className="space-y-4">
      {/* Summary */}
      <div className="grid grid-cols-4 gap-3">
        {bySeverity.map(({ severity, count }) => (
          <div key={severity} className="bg-slate-900 border border-slate-700/60 rounded-xl p-4">
            <p className="text-xs text-slate-500">{severity}</p>
            <p className="text-2xl font-bold" style={{ color: SEV_COLORS[severity] }}>{count}</p>
          </div>
        ))}
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader title="Events by Type" />
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={byType} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="name" tick={{ fill: '#64748b', fontSize: 9 }} angle={-20} textAnchor="end" height={40} />
              <YAxis tick={{ fill: '#64748b', fontSize: 10 }} />
              <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} />
              <Bar dataKey="value" fill="#ef4444" radius={[4,4,0,0]} name="Events" />
            </BarChart>
          </ResponsiveContainer>
        </Card>

        <Card>
          <CardHeader title="Severity Distribution" />
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie data={bySeverity} dataKey="count" nameKey="severity" cx="50%" cy="50%" outerRadius={80}
                label={({ severity, percent }) => `${severity} ${(percent * 100).toFixed(0)}%`} labelLine={false}>
                {bySeverity.map(({ severity }) => (
                  <Cell key={severity} fill={SEV_COLORS[severity]} />
                ))}
              </Pie>
              <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} />
            </PieChart>
          </ResponsiveContainer>
        </Card>
      </div>

      {/* Filters */}
      <div className="flex gap-3 flex-wrap">
        <div className="flex gap-1 bg-slate-900 border border-slate-700/60 rounded-lg p-1">
          {['ALL', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(s => (
            <button key={s} onClick={() => setSeverityFilter(s)}
              className={`px-2.5 py-1.5 rounded-md text-xs font-medium transition ${
                severityFilter === s ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-slate-200'
              }`}>{s}</button>
          ))}
        </div>
        <div className="flex gap-1 bg-slate-900 border border-slate-700/60 rounded-lg p-1 flex-wrap">
          {['ALL', ...Object.keys(TYPE_ICONS)].map(t => (
            <button key={t} onClick={() => setTypeFilter(t)}
              className={`px-2.5 py-1.5 rounded-md text-xs font-medium transition ${
                typeFilter === t ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-slate-200'
              }`}>{t === 'ALL' ? 'ALL' : (TYPE_ICONS[t] + ' ' + t.replace(/_/g, ' '))}</button>
          ))}
        </div>
      </div>

      {/* Event list */}
      <div className="space-y-2">
        {filtered.map(event => (
          <div key={event.id} className="bg-slate-900 border border-slate-700/60 rounded-xl p-4">
            <div className="flex items-start justify-between gap-3">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap mb-1">
                  <span className="text-base">{TYPE_ICONS[event.type] ?? '⚠️'}</span>
                  <span className="text-sm font-medium text-slate-200">{event.message}</span>
                  {statusBadge(event.severity)}
                </div>
                <div className="flex gap-3 text-xs text-slate-500 flex-wrap">
                  <span>Agent: {event.agentName}</span>
                  {event.user && <><span>·</span><span>User: {event.user}</span></>}
                  <span>·</span>
                  <span>{timeAgo(event.timestamp)}</span>
                  <span>·</span>
                  <span className="font-mono text-[10px]">{event.type}</span>
                </div>
              </div>
            </div>
          </div>
        ))}
        {filtered.length === 0 && (
          <div className="text-center py-12 text-slate-500 text-sm">No events match filters</div>
        )}
      </div>
    </div>
  )
}
