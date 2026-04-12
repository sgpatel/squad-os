import { useCallback, useState } from 'react'
import { useApi } from '../hooks/useApi'
import { api } from '../utils/api'
import { fmtMs, timeAgo } from '../utils/format'
import { Card } from '../components/ui/Card'
import { LoadingOverlay } from '../components/ui/Spinner'
import { statusBadge } from '../components/ui/Badge'
import type { WorkflowItem } from '../types'

export function Workflows() {
  const { data: workflows, loading } = useApi<WorkflowItem[]>(
    useCallback(() => api.workflows() as Promise<WorkflowItem[]>, [])
  )
  const [stateFilter, setStateFilter] = useState<string>('ALL')
  const [selected, setSelected] = useState<WorkflowItem | null>(null)

  if (loading || !workflows) return <LoadingOverlay />

  const states = ['ALL', 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED']
  const filtered = workflows.filter(w => stateFilter === 'ALL' || w.state === stateFilter)

  const stateCounts = states.slice(1).reduce((acc, s) => {
    acc[s] = workflows.filter(w => w.state === s).length
    return acc
  }, {} as Record<string, number>)

  return (
    <div className="space-y-4">
      {/* State counts */}
      <div className="grid grid-cols-4 gap-3">
        {[
          { label: 'Running',   count: stateCounts['RUNNING']   ?? 0, color: 'text-blue-400' },
          { label: 'Paused',    count: stateCounts['PAUSED']    ?? 0, color: 'text-amber-400' },
          { label: 'Completed', count: stateCounts['COMPLETED'] ?? 0, color: 'text-emerald-400' },
          { label: 'Failed',    count: stateCounts['FAILED']    ?? 0, color: 'text-red-400' },
        ].map(s => (
          <div key={s.label} className="bg-slate-900 border border-slate-700/60 rounded-xl p-4">
            <p className="text-xs text-slate-500">{s.label}</p>
            <p className={`text-2xl font-bold ${s.color}`}>{s.count}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex gap-1 bg-slate-900 border border-slate-700/60 rounded-lg p-1 w-fit">
        {states.map(s => (
          <button
            key={s}
            onClick={() => setStateFilter(s)}
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition ${
              stateFilter === s ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            {s}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">
        {/* List */}
        <div className="lg:col-span-3 space-y-2">
          {filtered.map(wf => (
            <button
              key={wf.workflowId}
              onClick={() => setSelected(wf === selected ? null : wf)}
              className={`w-full text-left rounded-xl border p-4 transition ${
                selected?.workflowId === wf.workflowId
                  ? 'bg-blue-900/20 border-blue-600/50'
                  : 'bg-slate-900 border-slate-700/60 hover:border-slate-600'
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-mono text-xs text-blue-400">{wf.workflowId}</span>
                    {statusBadge(wf.state)}
                  </div>
                  <p className="text-sm font-medium text-slate-200">{wf.agentName}</p>
                  <div className="flex gap-3 mt-1 text-xs text-slate-500">
                    <span>Created {timeAgo(wf.createdAt)}</span>
                    <span>·</span>
                    <span>{fmtMs(wf.elapsedMs)}</span>
                    <span>·</span>
                    <span>{wf.checkpointCount} checkpoints</span>
                  </div>
                </div>
              </div>

              {/* Step progress */}
              {wf.completedSteps.length > 0 && (
                <div className="mt-3 flex gap-1 flex-wrap">
                  {wf.completedSteps.map(step => (
                    <span key={step} className="text-[10px] bg-emerald-900/40 text-emerald-400 border border-emerald-700/30 rounded px-1.5 py-0.5">
                      ✓ {step}
                    </span>
                  ))}
                  {wf.state === 'FAILED' && wf.error && (
                    <span className="text-[10px] bg-red-900/40 text-red-400 border border-red-700/30 rounded px-1.5 py-0.5">
                      ✗ failed
                    </span>
                  )}
                </div>
              )}

              {wf.error && (
                <p className="mt-2 text-xs text-red-400 font-mono">{wf.error}</p>
              )}
            </button>
          ))}
        </div>

        {/* Detail */}
        <div className="lg:col-span-2">
          {selected ? (
            <Card>
              <h3 className="text-sm font-semibold text-slate-200 mb-4">Workflow Detail</h3>
              <dl className="space-y-2.5">
                {[
                  ['ID',         selected.workflowId],
                  ['Agent',      selected.agentName],
                  ['State',      statusBadge(selected.state)],
                  ['Created',    timeAgo(selected.createdAt)],
                  ['Updated',    timeAgo(selected.updatedAt)],
                  ['Elapsed',    fmtMs(selected.elapsedMs)],
                  ['Checkpoints', selected.checkpointCount],
                  ['Last Step',  selected.lastStep ?? '—'],
                ].map(([k, v]) => (
                  <div key={String(k)} className="flex gap-2">
                    <dt className="text-[10px] text-slate-500 w-24 flex-shrink-0 pt-0.5 uppercase tracking-wide">{k}</dt>
                    <dd className="text-xs text-slate-200">{v}</dd>
                  </div>
                ))}
              </dl>
              {selected.completedSteps.length > 0 && (
                <div className="mt-4">
                  <p className="text-[10px] uppercase tracking-wide text-slate-500 mb-2">Completed Steps</p>
                  <div className="space-y-1">
                    {selected.completedSteps.map((step, i) => (
                      <div key={step} className="flex items-center gap-2 text-xs">
                        <span className="w-4 h-4 rounded-full bg-emerald-900/50 border border-emerald-600/40 flex items-center justify-center text-emerald-400 text-[10px]">✓</span>
                        <span className="text-slate-300">{step}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {selected.error && (
                <div className="mt-3 p-2 bg-red-900/20 border border-red-700/30 rounded-lg">
                  <p className="text-xs text-red-400">{selected.error}</p>
                </div>
              )}
            </Card>
          ) : (
            <Card className="h-48 flex items-center justify-center text-slate-500 text-sm">
              Select a workflow to see details
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
