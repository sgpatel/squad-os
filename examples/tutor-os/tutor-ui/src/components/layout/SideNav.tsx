import { NavLink } from 'react-router-dom';
import {
  Home, MessageSquare, BookOpen, FileText, PenLine, Layers, ListChecks,
  CalendarDays, BarChart3, Library, Users, Settings as Cog
} from 'lucide-react';
import { usePractice } from '@/store/practice';
import { useNotes } from '@/store/notes';

interface Item {
  to: string;
  label: string;
  icon: React.ComponentType<{ size?: string | number }>;
  badge?: string | number;
}

/**
 * Side rail — three groups:
 *   1. Active surfaces       (Home, Tutor, Subjects)
 *   2. Capture & practice    (Notes, Rough work, Practice, Quizzes)
 *   3. Track & support       (Plan, Progress, Library, Community)
 *   ─ footer ─
 *   Settings
 *
 * Order encodes intent: greet first, do work, then capture, then plan.
 */
export function SideNav() {
  const dueCount = usePractice(s => s.dueQueue().length);
  const noteCount = useNotes(s => s.notes.length);

  const groups: { label: string; items: Item[] }[] = [
    {
      label: 'Learn',
      items: [
        { to: '/',          label: 'Home',       icon: Home },
        { to: '/tutor',     label: 'Tutor',      icon: MessageSquare },
        { to: '/subjects',  label: 'Subjects',   icon: BookOpen }
      ]
    },
    {
      label: 'Capture',
      items: [
        { to: '/notes',    label: 'Notes',       icon: FileText, badge: noteCount },
        { to: '/scratch',  label: 'Rough work',  icon: PenLine },
        { to: '/practice', label: 'Practice',    icon: Layers,    badge: dueCount > 0 ? dueCount : undefined },
        { to: '/quiz/qz_photo', label: 'Quizzes', icon: ListChecks }
      ]
    },
    {
      label: 'Track',
      items: [
        { to: '/plan',      label: 'Plan',       icon: CalendarDays },
        { to: '/progress',  label: 'Progress',   icon: BarChart3 },
        { to: '/library',   label: 'Library',    icon: Library },
        { to: '/community', label: 'Community',  icon: Users }
      ]
    }
  ];

  return (
    <nav className="sidenav" aria-label="Primary">
      {groups.map(g => (
        <div className="sidenav__group" key={g.label}>
          <div className="sidenav__label">{g.label}</div>
          {g.items.map(({ to, label, icon: Icon, badge }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) => 'sidenav__item' + (isActive ? ' is-active' : '')}
            >
              <Icon size={16} />
              <span>{label}</span>
              {badge != null && <span className="sidenav__badge">{badge}</span>}
            </NavLink>
          ))}
        </div>
      ))}
      <div className="sidenav__footer">
        <NavLink to="/settings" className={({ isActive }) => 'sidenav__item' + (isActive ? ' is-active' : '')}>
          <Cog size={16} />
          <span>Settings</span>
        </NavLink>
      </div>
    </nav>
  );
}
