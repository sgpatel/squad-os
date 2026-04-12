import { clsx } from 'clsx'

export type Tab =
  | 'overview'
  | 'agents'
  | 'traces'
  | 'metrics'
  | 'workflows'
  | 'security'
  | 'activity'

interface NavItem { id: Tab; label: string; icon: string }

const NAV: NavItem[] = [
  { id: 'overview',  label: 'Overview',  icon: '⬡' },
  { id: 'agents',    label: 'Agents',    icon: '🤖' },
  { id: 'traces',    label: 'Traces',    icon: '🔍' },
  { id: 'metrics',   label: 'Metrics',   icon: '📊' },
  { id: 'workflows', label: 'Workflows', icon: '⚙️' },
  { id: 'security',  label: 'Security',  icon: '🛡️' },
  { id: 'activity',  label: 'Activity',  icon: '⚡' },
]

export function Sidebar({
  active,
  onChange,
}: {
  active: Tab
  onChange: (t: Tab) => void
}) {
  return (
    <aside className="w-56 flex-shrink-0 bg-slate-950 border-r border-slate-800 flex flex-col h-full">
      {/* Logo */}
      <div className="px-5 py-4 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-bold text-sm">S</div>
          <div>
            <p className="text-white font-semibold text-sm leading-tight">SquadOS</p>
            <p className="text-slate-500 text-[10px]">v3.7.0</p>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 py-3 overflow-y-auto">
        {NAV.map(item => (
          <button
            key={item.id}
            onClick={() => onChange(item.id)}
            className={clsx(
              'w-full flex items-center gap-2.5 px-4 py-2.5 text-sm transition-all duration-150',
              active === item.id
                ? 'bg-blue-600/20 text-blue-300 border-r-2 border-blue-500 font-medium'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
            )}
          >
            <span className="text-base leading-none w-5 text-center">{item.icon}</span>
            <span className="flex-1 text-left">{item.label}</span>
          </button>
        ))}
      </nav>

      {/* Footer */}
      <div className="px-4 py-3 border-t border-slate-800">
        <p className="text-[10px] text-slate-600 text-center">© 2026 SquadOS</p>
      </div>
    </aside>
  )
}
