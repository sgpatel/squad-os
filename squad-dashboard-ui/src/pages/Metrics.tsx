import { useCallback } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, LineChart, Line, Legend
} from 'recharts'
import { useApi } from '../hooks/useApi'
import { api } from '../utils/api'
import { fmtMs, fmtTokens, fmtNumber, fmtPct } from '../utils/format'
import { Card, CardHeader } from '../components/ui/Card'
import { LoadingOverlay } from '../components/ui/Spinner'
import type { MetricsSummary } from '../types'

export function Metrics() {
  const { data: metrics, loading } = useApi<MetricsSummary>(
    useCallback(() => api.metrics() as Promise<MetricsSummary>, [])
  )

  if (loading || !metrics) return <LoadingOverlay />

  const perAgentData = metrics.perAgent.slice(0, 8).map(a => ({
    name: a.name.replace('Agent', '').replace('Detector', 'Det.'),
    calls: a.calls,
    errors: a.errors,
    tokens: Math.round(a.tokens / 1000),
    latency: Math.round(a.avgLatencyMs),
  }))

  const errorData = Object.entries(metrics.errorsByType).map(([name, value]) => ({ name, value }))

  return (
    <div className="space-y-4">
      {/* Summary row */}
      <div className="grid grid-cols-3 gap-4">
        <Card>
          <p className="text-xs text-slate-500 uppercase tracking-wide">Avg Latency</p>
          <p className="text-2xl font-bold text-amber-400 mt-1">{fmtMs(metrics.avgLatencyMs)}</p>
          <p className="text-xs text-slate-500 mt-1">p95: {fmtMs(metrics.p95LatencyMs)} · p99: {fmtMs(metrics.p99LatencyMs)}</p>
        </Card>
        <Card>
          <p className="text-xs text-slate-500 uppercase tracking-wide">Total Tokens</p>
          <p className="text-2xl font-bold text-purple-400 mt-1">{fmtTokens(metrics.totalTokensConsumed)}</p>
          <p className="text-xs text-slate-500 mt-1">P: {fmtTokens(metrics.totalPromptTokens)} · C: {fmtTokens(metrics.totalCompletionTokens)}</p>
        </Card>
        <Card>
          <p className="text-xs text-slate-500 uppercase tracking-wide">Error Rate</p>
          <p className="text-2xl font-bold text-red-400 mt-1">{fmtPct(1 - metrics.overallSuccessRate)}</p>
          <p className="text-xs text-slate-500 mt-1">{fmtNumber(metrics.totalErrorCalls)} errors / {fmtNumber(metrics.totalAgentCalls)} calls</p>
        </Card>
      </div>

      {/* Per-agent calls vs errors */}
      <Card>
        <CardHeader title="Calls vs Errors per Agent" subtitle="All registered agents" />
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={perAgentData} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="name" tick={{ fill: '#64748b', fontSize: 10 }} />
            <YAxis tick={{ fill: '#64748b', fontSize: 10 }} />
            <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} />
            <Legend wrapperStyle={{ fontSize: 11, color: '#94a3b8' }} />
            <Bar dataKey="calls"  name="Total Calls"  fill="#3b82f6" radius={[4,4,0,0]} />
            <Bar dataKey="errors" name="Errors"        fill="#ef4444" radius={[4,4,0,0]} />
          </BarChart>
        </ResponsiveContainer>
      </Card>

      {/* Token usage per agent + Latency per agent */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader title="Token Usage per Agent" subtitle="Thousands (K)" />
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={perAgentData} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="name" tick={{ fill: '#64748b', fontSize: 10 }} />
              <YAxis tick={{ fill: '#64748b', fontSize: 10 }} unit="K" />
              <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} formatter={(v) => [`${v}K`, 'Tokens']} />
              <Bar dataKey="tokens" fill="#8b5cf6" radius={[4,4,0,0]} name="Tokens (K)" />
            </BarChart>
          </ResponsiveContainer>
        </Card>

        <Card>
          <CardHeader title="Avg Latency per Agent" subtitle="Milliseconds" />
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={perAgentData} layout="vertical" margin={{ top: 0, right: 20, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" horizontal={false} />
              <XAxis type="number" tick={{ fill: '#64748b', fontSize: 10 }} unit="ms" />
              <YAxis type="category" dataKey="name" tick={{ fill: '#94a3b8', fontSize: 10 }} width={80} />
              <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} formatter={(v) => [`${v}ms`, 'Avg Latency']} />
              <Bar dataKey="latency" fill="#f59e0b" radius={[0,4,4,0]} name="Avg Latency (ms)" />
            </BarChart>
          </ResponsiveContainer>
        </Card>
      </div>

      {/* Time series comparison */}
      <Card>
        <CardHeader title="Call Rate (last 60 min)" subtitle="Per-minute buckets" />
        <ResponsiveContainer width="100%" height={200}>
          <LineChart data={metrics.callsOverTime} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="label" tick={{ fill: '#64748b', fontSize: 10 }} interval={9} />
            <YAxis tick={{ fill: '#64748b', fontSize: 10 }} />
            <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} itemStyle={{ color: '#3b82f6' }} />
            <Line type="monotone" dataKey="value" stroke="#3b82f6" strokeWidth={2} dot={false} name="Calls/min" />
          </LineChart>
        </ResponsiveContainer>
      </Card>

      {/* Error breakdown */}
      <Card>
        <CardHeader title="Error Breakdown" subtitle="By exception type" />
        <ResponsiveContainer width="100%" height={180}>
          <BarChart data={errorData} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="name" tick={{ fill: '#94a3b8', fontSize: 10 }} />
            <YAxis tick={{ fill: '#64748b', fontSize: 10 }} />
            <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} />
            <Bar dataKey="value" fill="#ef4444" radius={[4,4,0,0]} name="Count" />
          </BarChart>
        </ResponsiveContainer>
      </Card>
    </div>
  )
}
