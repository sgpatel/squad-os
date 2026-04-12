import { useCallback, useState } from 'react'
import { RadarChart, Radar, PolarGrid, PolarAngleAxis, ResponsiveContainer, Tooltip } from 'recharts'
import { useApi } from '../hooks/useApi'
import { api } from '../utils/api'
import { fmtNumber, fmtTokens, fmtMs, fmtPct, timeAgo } from '../utils/format'
import { Card, CardHeader } from '../components/ui/Card'
import { LoadingOverlay } from '../components/ui/Spinner'
import { statusBadge } from '../components/ui/Badge'
import type { AgentSummary } from '../types'

export function Agents() {
  const { data: agents, loading } = useApi<AgentSummary[]>(
    useCallback(() => api.agents() as Promise<AgentSummary[]>, [])
  )
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<AgentSummary | null>(null)

  if (loading || !agents) return <LoadingOverlay />

  const filtered = agents.filter(a =>
    a.name.toLowerCase().includes(search.toLowerCase()) ||
    a.role.toLowerCase().includes(search.toLowerCase())
  )

  const radarData = selected ? [
    { metric: 'Success Rate', value: Math.round(selected.successRate * 100) },
    { metric: 'Call Volume', value: Math.min(100, Math.round((selected.totalCalls / 5000) * 100)) },
    { metric: 'Token Efficiency', value: selected.totalCalls > 0 ? Math.min(100, Math.round((selected.totalTokens / selected.totalCalls / 800) * 100)) : 0 },
    { metric: 'Speed Score', value: Math.max(0, 100 - Math.round(selected.avgLatencyMs / 20)) },
    { metric: 'Stability', value: Math.round((1 - selected.errorCalls / Math.max(1, selected.totalCalls)) * 100) },
  ] : []

  return (
    <div className="space-y-4">
      {/* Search */}
      <div className="flex gap-3">
        <input
          className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-blue-500 transition"
          placeholder="Search agents by name or role..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <span className="text-xs text-slate-500 self-center">{filtered.length} agents</span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Agent List */}
        <div className="lg:col-span-2 space-y-2">
          {filtered.map(agent => (
            <button
              key={agent.name}
              onClick={() => setSelected(agent === selected ? null : agent)}
              className={`w-full text-left rounded-xl border p-4 transition-all duration-150 ${
                selected?.name === agent.name
                  ? 'bg-blue-900/20 border-blue-600/50'
                  : 'bg-slate-900 border-slate-700/60 hover:border-slate-600'
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-semibold text-slate-100 text-sm">{agent.name}</span>
                    {statusBadge(agent.status)}
                    <span className="text-xs text-slate-500 font-mono">{agent.role}</span>
                  </div>
                  <p className="text-xs text-slate-500 mt-0.5 truncate">{agent.annotations}</p>
                </div>
                <div className="flex-shrink-0 text-right space-y-1">
                  <p className="text-xs text-slate-400">{fmtNumber(agent.totalCalls)} calls</p>
                  <p className="text-xs text-slate-400">{fmtPct(agent.successRate)} success</p>
                </div>
              </div>

              <div className="mt-3 grid grid-cols-4 gap-3">
                <div>
                  <p className="text-[10px] text-slate-500 uppercase tracking-wide">Calls</p>
                  <p className="text-sm font-semibold text-slate-200">{fmtNumber(agent.totalCalls)}</p>
                </div>
                <div>
                  <p className="text-[10px] text-slate-500 uppercase tracking-wide">Errors</p>
                  <p className="text-sm font-semibold text-red-400">{fmtNumber(agent.errorCalls)}</p>
                </div>
                <div>
                  <p className="text-[10px] text-slate-500 uppercase tracking-wide">Tokens</p>
                  <p className="text-sm font-semibold text-purple-400">{fmtTokens(agent.totalTokens)}</p>
                </div>
                <div>
                  <p className="text-[10px] text-slate-500 uppercase tracking-wide">Avg Latency</p>
                  <p className="text-sm font-semibold text-amber-400">{fmtMs(agent.avgLatencyMs)}</p>
                </div>
              </div>

              {/* Success bar */}
              <div className="mt-3 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-emerald-500 to-blue-500 rounded-full transition-all"
                  style={{ width: `${agent.successRate * 100}%` }}
                />
              </div>
            </button>
          ))}
        </div>

        {/* Detail Panel */}
        <div className="space-y-4">
          {selected ? (
            <>
              <Card>
                <CardHeader title={selected.name} subtitle={`Role: ${selected.role}`} />
                <dl className="space-y-2 text-sm">
                  {[
                    ['Status', statusBadge(selected.status)],
                    ['Total Calls', fmtNumber(selected.totalCalls)],
                    ['Success', fmtNumber(selected.successCalls)],
                    ['Errors', fmtNumber(selected.errorCalls)],
                    ['Avg Latency', fmtMs(selected.avgLatencyMs)],
                    ['Total Tokens', fmtTokens(selected.totalTokens)],
                    ['Prompt Tokens', fmtTokens(selected.totalPromptTokens)],
                    ['Completion Tokens', fmtTokens(selected.totalCompletionTokens)],
                    ['Last Call', timeAgo(selected.lastCallAt)],
                  ].map(([k, v]) => (
                    <div key={String(k)} className="flex justify-between">
                      <dt className="text-slate-500 text-xs">{k}</dt>
                      <dd className="text-slate-200 text-xs font-medium">{v}</dd>
                    </div>
                  ))}
                </dl>
                <div className="mt-3 pt-3 border-t border-slate-800">
                  <p className="text-[10px] text-slate-500 uppercase mb-1">Annotations</p>
                  <p className="text-xs text-blue-400 font-mono">{selected.annotations}</p>
                </div>
              </Card>

              <Card>
                <CardHeader title="Agent Radar" subtitle="Performance profile" />
                <ResponsiveContainer width="100%" height={220}>
                  <RadarChart data={radarData}>
                    <PolarGrid stroke="#1e293b" />
                    <PolarAngleAxis dataKey="metric" tick={{ fill: '#64748b', fontSize: 10 }} />
                    <Radar name={selected.name} dataKey="value" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.25} />
                    <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} />
                  </RadarChart>
                </ResponsiveContainer>
              </Card>
            </>
          ) : (
            <Card className="h-48 flex items-center justify-center text-slate-500 text-sm">
              Select an agent to see details
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
