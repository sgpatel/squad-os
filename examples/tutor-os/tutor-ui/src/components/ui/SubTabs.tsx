import { NavLink } from 'react-router-dom';

/**
 * SubTabs — a horizontal tab strip used at the top of "hub" pages.
 *
 * The rail collapses sibling surfaces under a single primary destination
 * (e.g. Notes / Cheatsheet / Rough work all live under "Notes" in the
 * rail). SubTabs gives the learner the secondary level — gpai.app-style:
 *
 *   ┌─ Notes ─────────────────────────────────────────────────────────┐
 *   │  Notes  ·  Cheatsheet  ·  Rough work                            │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * Each tab is a `NavLink`, so the active state is driven by the URL —
 * no extra state, no flicker on refresh, deep-linkable.
 */
export interface SubTab {
  to: string;
  label: string;
  /** Match exactly (default true for short, distinct paths). */
  end?: boolean;
}

export function SubTabs({ tabs, ariaLabel }: { tabs: SubTab[]; ariaLabel: string }) {
  return (
    <nav className="subtabs" role="tablist" aria-label={ariaLabel}>
      {tabs.map(t => (
        <NavLink
          key={t.to}
          to={t.to}
          end={t.end ?? true}
          className={({ isActive }) => 'subtabs__tab' + (isActive ? ' is-active' : '')}
          role="tab"
        >
          {t.label}
        </NavLink>
      ))}
    </nav>
  );
}
