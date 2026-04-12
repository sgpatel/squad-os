import { useState, useCallback } from 'react'
import { Sidebar, type Tab } from './components/layout/Sidebar'
import { TopBar } from './components/layout/TopBar'
import { Overview }  from './pages/Overview'
import { Agents }    from './pages/Agents'
import { Traces }    from './pages/Traces'
import { Metrics }   from './pages/Metrics'
import { Workflows } from './pages/Workflows'
import { Security }  from './pages/Security'
import { Activity }  from './pages/Activity'
import { useSSE }    from './hooks/useSSE'
import { useApi }    from './hooks/useApi'
import { api }       from './utils/api'
import type { SystemHealth } from './types'

const PAGE_TITLES: Record<Tab, string> = {
  overview:  'Overview',
  agents:    'Agents',
  traces:    'Traces',
  metrics:   'Metrics',
  workflows: 'Workflows',
  security:  'Security',
  activity:  'Live Activity',
}

const PAGE_SUBTITLES: Record<Tab, string> = {
  overview:  'Real-time SquadOS health at a glance',
  agents:    'All registered agents with runtime metrics',
  traces:    'Agent span history and telemetry',
  metrics:   'Aggregate performance metrics and charts',
  workflows: 'Durable workflow states (@DurableAgent)',
  security:  'Security audit log and guardrail events',
  activity:  'SSE event stream — live agent activity',
}

export default function App() {
  const [tab, setTab] = useState<Tab>('overview')
  const { connected } = useSSE()

  const { data: health } = useApi<SystemHealth>(
    useCallback(() => api.health() as Promise<SystemHealth>, []),
    15000
  )

  const dataMode = health?.components?.mode === 'REDIS' ? 'redis' : 'simulation'

  const PageComponent = {
    overview:  Overview,
    agents:    Agents,
    traces:    Traces,
    metrics:   Metrics,
    workflows: Workflows,
    security:  Security,
    activity:  Activity,
  }[tab]

  return (
    <div className="flex h-screen bg-slate-950 overflow-hidden text-slate-100">
      <Sidebar active={tab} onChange={setTab} />

      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <TopBar
          title={PAGE_TITLES[tab]}
          subtitle={PAGE_SUBTITLES[tab]}
          connected={connected}
          lastRefresh={new Date()}
          dataMode={dataMode}
        />

        <main className="flex-1 overflow-y-auto p-5">
          <PageComponent />
        </main>
      </div>
    </div>
  )
}
