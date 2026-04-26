import { NavLink } from 'react-router-dom';
import {
  Sparkles, Library as LibraryIcon, FileText, Layers, BarChart3,
  Users, Settings as Cog
} from 'lucide-react';
import { usePractice } from '@/store/practice';
import { useNotes } from '@/store/notes';

interface Item {
  to: string;
  label: string;
  icon: React.ComponentType<{ size?: string | number }>;
  badge?: string | number;
  /** Hint shown under the label as a one-line description (gpai-style). */
  hint?: string;
}

/**
 * Primary rail — gpai.app-inspired: 5 destinations, tabs handle the rest.
 *
 *   Ask         → /          (chat-first home; the universal entry point)
 *   Library     → /subjects  (Subjects → Courses → Chapters + Sources tab)
 *   Notes       → /notes     (Notes / Cheatsheet / Rough work as tabs)
 *   Practice    → /practice  (Flashcards / Quizzes as tabs)
 *   Progress    → /progress  (Stats / Plan as tabs)
 *
 *   ─ footer ─
 *   Community   → /community
 *   Settings    → /settings
 *
 * Why so few? Each rail click is a context switch; the more destinations,
 * the more the learner has to scan. Sibling surfaces with shared tools
 * collapse into tabs of a single hub (see SubTabs.tsx).
 */
export function SideNav() {
  const dueCount  = usePractice(s => s.dueQueue().length);
  const noteCount = useNotes(s => s.notes.length);

  const primary: Item[] = [
    { to: '/',         label: 'Ask',      icon: Sparkles,    hint: 'Tutor & solver' },
    { to: '/subjects', label: 'Library',  icon: LibraryIcon, hint: 'Subjects · Sources' },
    { to: '/notes',    label: 'Notes',    icon: FileText,    hint: 'Write · Compact · Sketch',
      badge: noteCount > 0 ? noteCount : undefined },
    { to: '/practice', label: 'Practice', icon: Layers,      hint: 'Flashcards · Quizzes',
      badge: dueCount > 0 ? dueCount : undefined },
    { to: '/progress', label: 'Progress', icon: BarChart3,   hint: 'Stats · Plan' },
  ];

  const secondary: Item[] = [
    { to: '/community', label: 'Community', icon: Users },
    { to: '/settings',  label: 'Settings',  icon: Cog },
  ];

  return (
    <nav className="sidenav" aria-label="Primary">
      <div className="sidenav__group">
        {primary.map(({ to, label, icon: Icon, badge, hint }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) => 'sidenav__item sidenav__item--lg' + (isActive ? ' is-active' : '')}
          >
            <Icon size={18} />
            <span className="sidenav__item-body">
              <span className="sidenav__item-label">{label}</span>
              {hint && <span className="sidenav__item-hint">{hint}</span>}
            </span>
            {badge != null && <span className="sidenav__badge">{badge}</span>}
          </NavLink>
        ))}
      </div>

      <div className="sidenav__footer">
        {secondary.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => 'sidenav__item' + (isActive ? ' is-active' : '')}
          >
            <Icon size={16} />
            <span>{label}</span>
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
