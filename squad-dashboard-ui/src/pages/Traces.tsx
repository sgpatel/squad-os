import { useCallback, useState } from 'react'
import { useApi } from '../hooks/useApi'
import { api } from '../utils/api'
import { fmtMs, fmtNumber, fmtDateTime } from '../utils/format'
import { Card } from '../components/ui/Card'
import { LoadingOverlay } from '../components/ui/Spinner'
import { statusBadge } from '../components/ui/Badge'
import type { TraceSpan } from '../types'

export function Traces() {
  const { data: traces, loading } = useApi<TraceSpan[]>(
    useCallback(() => api.traces(200) as Promise<TraceSpan[]>, [])
  )
  const [selected, setSelected] = useState<TraceSpan | null>(null)
  const [filter, setFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'OK' | 'ERROR'>('ALL')

  if (loading || !traces) return <LoadingOverlay />

  const filtered = traces.filter(t => {
    const matchText = !filter ||
      t.agentName.toLowerCase().includes(filter.toLowerCase()) ||
      t.traceId.includes(filter)
    const matchStatus = statusFilter === 'ALL' || t.status === statusFilter
    return matchText && matchStatus
  })

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex gap-3 items-center">
        <input
          className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-blue-500"
          placeholder="Search by agent name or trace ID..."
          value={filter}
          onChange={e => setFilter(e.target.value)}
        />
        <div className="flex rounded-lg border border-slate-700 overflow-hidden">
          {(['ALL', 'OK', 'ERROR'] as const).map(s => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-2 text-xs font-medium transition ${
                statusFilter === s
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-900 text-slate-400 hover:text-slate-200'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
        <span className="text-xs text-slate-500">{filtered.length} spans</span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">
        {/* Table */}
        <div className="lg:col-span-3">
          <Card className="p-0 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-800">
                    <th className="text-left text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Agent</th>
                    <th className="text-left text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Status</th>
                    <th className="text-right text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Duration</th>
                    <th className="text-right text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Tokens</th>
                    <th className="text-right text-[10px] font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Time</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.slice(0, 100).map(span => (
                    <tr
                      key={span.spanId}
                      onClick={() => setSelected(span === selected ? null : span)}
                      className={`border-b border-slate-800/50 cursor-pointer transition-colors ${
                        selected?.spanId === span.spanId
                          ? 'bg-blue-900/20'
                          : 'hover:bg-slate-800/40'
                      }`}
                    >
                      <td className="px-4 py-3">
                        <p className="font-medium text-slate-200 text-xs">{span.agentName}</p>
                        <p className="text-slate-500 text-[10px] font-mono">{span.traceId}</p>
                      </td>
                      <td className="px-4 py-3">{statusBadge(span.status)}</td>
                      <td className="px-4 py-3 text-right">
                        <span className={`text-xs font-medium ${span.durationMs > 1000 ? 'text-amber-400' : 'text-slate-300'}`}>
                          {fmtMs(span.durationMs)}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right text-xs text-purple-400">{fmtNumber(span.totalTokens)}</td>
                      <td className="px-4 py-3 text-right text-xs text-slate-500">
                        {new Date(span.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>

        {/* Span Detail */}
        <div className="lg:col-span-2">
          {selected ? (
            <Card>
              <h3 className="text-sm font-semibold text-slate-200 mb-4">Span Detail</h3>
              <dl className="space-y-2.5">
                {[
                  ['Span', selected.spanName],
                  ['Trace ID', selected.traceId],
                  ['Span ID', selected.spanId],
                  ['Agent', selected.agentName],
                  ['Role', selected.role],
                  ['Status', statusBadge(selected.status)],
                  ['Duration', fmtMs(selected.durationMs)],
                  ['Start', fmtDateTime(selected.startTime)],
                  ['End', fmtDateTime(selected.endTime)],
                  ['Prompt Tokens', fmtNumber(selected.promptTokens)],
                  ['Completion Tokens', fmtNumber(selected.completionTokens)],
                  ['Total Tokens', fmtNumber(selected.totalTokens)],
                  ['Input Length', fmtNumber(selected.inputLength)],
                  ['Output Length', fmtNumber(selected.outputLength)],
                ].map(([k, v]) => (
                  <div key={String(k)} className="flex gap-2">
                    <dt className="text-[10px] text-slate-500 w-28 flex-shrink-0 pt-0.5 uppercase tracking-wide">{k}</dt>
                    <dd className="text-xs text-slate-200 font-mono break-all">{v}</dd>
                  </div>
                ))}
              </dl>
              {selected.errorMessage && (
                <div className="mt-3 p-2 bg-red-900/20 border border-red-700/30 rounded-lg">
                  <p className="text-xs text-red-400">{selected.errorMessage}</p>
                </div>
              )}
              {Object.keys(selected.attributes).length > 0 && (
                <div className="mt-3">
                  <p className="text-[10px] uppercase tracking-wide text-slate-500 mb-2">Attributes</p>
                  {Object.entries(selected.attributes).map(([k, v]) => (
                    <div key={k} className="flex gap-2 text-xs">
                      <span className="text-slate-500">{k}:</span>
                      <span className="text-slate-300">{v}</span>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          ) : (
            <Card className="h-48 flex items-center justify-center text-slate-500 text-sm">
              Select a span to see details
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
