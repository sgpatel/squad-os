import { useCallback } from 'react'
import {
  AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts'
import { useApi } from '../hooks/useApi'
import { api } from '../utils/api'
import { fmtNumber, fmtTokens, fmtMs, fmtPct } from '../utils/format'
import { StatCard } from '../components/ui/StatCard'
import { Card, CardHeader } from '../components/ui/Card'
import { LoadingOverlay } from '../components/ui/Spinner'
import type { MetricsSummary, SystemHealth, AgentSummary } from '../types'

const PIE_COLORS = ['#3b82f6', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b', '#ef4444']

export function Overview() {
  const { data: metrics, loading: mLoad } = useApi<MetricsSummary>(
    useCallback(() => api.metrics() as Promise<MetricsSummary>, [])
  )
  const { data: health } = useApi<SystemHealth>(
    useCallback(() => api.health() as Promise<SystemHealth>, [])
  )
  const { data: agents } = useApi<AgentSummary[]>(
    useCallback(() => api.agents() as Promise<AgentSummary[]>, [])
  )

  if (mLoad || !metrics) return <LoadingOverlay />

  const errorPieData = Object.entries(metrics.errorsByType).map(([name, value]) => ({ name, value }))
  const top5 = [...(agents ?? [])].sort((a, b) => b.totalCalls - a.totalCalls).slice(0, 5)

  return (
    <div className="space-y-6">
      {/* KPI Row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          label="Total Calls"
          value={fmtNumber(metrics.totalAgentCalls)}
          sub={`${fmtNumber(metrics.totalSuccessCalls)} succeeded`}
          color="blue"
          icon="📞"
        />
        <StatCard
          label="Success Rate"
          value={fmtPct(metrics.overallSuccessRate)}
          color="green"
          icon="✅"
          trend={metrics.overallSuccessRate > 0.9 ? 'up' : 'down'}
          trendValue="last hour"
        />
        <StatCard
          label="Tokens Consumed"
          value={fmtTokens(metrics.totalTokensConsumed)}
          sub={`P: ${fmtTokens(metrics.totalPromptTokens)} / C: ${fmtTokens(metrics.totalCompletionTokens)}`}
          color="purple"
          icon="🪙"
        />
        <StatCard
          label="Avg Latency"
          value={fmtMs(metrics.avgLatencyMs)}
          sub={`p95: ${fmtMs(metrics.p95LatencyMs)}`}
          color="amber"
          icon="⏱️"
        />
      </div>

      {/* Health + Rate limits row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          label="Active Agents"
          value={health?.activeAgents ?? '—'}
          sub={`of ${health?.totalAgents ?? '—'} registered`}
          color="cyan"
          icon="🤖"
        />
        <StatCard
          label="Total Errors"
          value={fmtNumber(metrics.totalErrorCalls)}
          sub={`${metrics.rateLimitBreach24h} rate-limit breaches`}
          color="red"
          icon="⚠️"
        />
        <StatCard
          label="SSE Subscribers"
          value={health?.sseSubscribers ?? '—'}
          color="blue"
          icon="📡"
        />
        <StatCard
          label="Heap Usage"
          value={`${Math.round(health?.heapUsagePct ?? 0)}%`}
          sub={`${health?.jvmHeapUsedMb ?? 0}MB / ${health?.jvmHeapMaxMb ?? 0}MB`}
          color={health?.heapUsagePct && health.heapUsagePct > 80 ? 'red' : 'green'}
          icon="💾"
        />
      </div>

      {/* Charts row 1 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader title="Agent Calls Over Time" subtitle="Last 60 minutes" />
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={metrics.callsOverTime} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id="callGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor="#3b82f6" stopOpacity={0.4} />
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="label" tick={{ fill: '#64748b', fontSize: 10 }} interval={9} />
              <YAxis tick={{ fill: '#64748b', fontSize: 10 }} />
              <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} labelStyle={{ color: '#94a3b8' }} itemStyle={{ color: '#3b82f6' }} />
              <Area type="monotone" dataKey="value" stroke="#3b82f6" strokeWidth={2} fill="url(#callGrad)" name="Calls" />
            </AreaChart>
          </ResponsiveContainer>
        </Card>

        <Card>
          <CardHeader title="Token Usage Over Time" subtitle="Last 60 minutes" />
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={metrics.tokensOverTime} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id="tokGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor="#8b5cf6" stopOpacity={0.4} />
                  <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="label" tick={{ fill: '#64748b', fontSize: 10 }} interval={9} />
              <YAxis tick={{ fill: '#64748b', fontSize: 10 }} />
              <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} labelStyle={{ color: '#94a3b8' }} itemStyle={{ color: '#8b5cf6' }} />
              <Area type="monotone" dataKey="value" stroke="#8b5cf6" strokeWidth={2} fill="url(#tokGrad)" name="Tokens" />
            </AreaChart>
          </ResponsiveContainer>
        </Card>
      </div>

      {/* Charts row 2 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card>
          <CardHeader title="Errors by Type" subtitle="Breakdown" />
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie data={errorPieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={75} labelLine={false}
                label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}>
                {errorPieData.map((_, i) => (
                  <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                ))}
              </Pie>
              <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} itemStyle={{ color: '#94a3b8' }} />
            </PieChart>
          </ResponsiveContainer>
        </Card>

        <Card>
          <CardHeader title="Avg Latency" subtitle="Last 60 minutes" />
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={metrics.latencyOverTime} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id="latGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor="#f59e0b" stopOpacity={0.4} />
                  <stop offset="95%" stopColor="#f59e0b" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="label" tick={{ fill: '#64748b', fontSize: 10 }} interval={9} />
              <YAxis tick={{ fill: '#64748b', fontSize: 10 }} unit="ms" />
              <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} labelStyle={{ color: '#94a3b8' }} itemStyle={{ color: '#f59e0b' }} />
              <Area type="monotone" dataKey="value" stroke="#f59e0b" strokeWidth={2} fill="url(#latGrad)" name="Latency (ms)" />
            </AreaChart>
          </ResponsiveContainer>
        </Card>

        <Card>
          <CardHeader title="Top 5 Agents by Volume" />
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={top5} layout="vertical" margin={{ top: 0, right: 20, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" horizontal={false} />
              <XAxis type="number" tick={{ fill: '#64748b', fontSize: 10 }} />
              <YAxis type="category" dataKey="name" tick={{ fill: '#94a3b8', fontSize: 10 }} width={110} />
              <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: 8 }} itemStyle={{ color: '#3b82f6' }} />
              <Bar dataKey="totalCalls" fill="#3b82f6" radius={[0, 4, 4, 0]} name="Calls" />
            </BarChart>
          </ResponsiveContainer>
        </Card>
      </div>
    </div>
  )
}
