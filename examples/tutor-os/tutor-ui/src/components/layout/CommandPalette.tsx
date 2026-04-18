import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Home, MessageSquare, BookOpen, FileText, PenLine, Layers, ListChecks,
  CalendarDays, BarChart3, Library, Users, Settings as Cog,
  Sparkles, FilePlus, Play
} from 'lucide-react';
import { useNotes } from '@/store/notes';
import { usePipeline } from '@/store/pipeline';

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

interface Cmd {
  id: string;
  label: string;
  group: string;
  icon: React.ComponentType<{ size?: string | number }>;
  meta?: string;
  action: () => void;
}

/**
 * ⌘K command palette. Lightweight — pure React, no library.
 * Filters by token-substring match across label + group.
 */
export function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const [q, setQ] = useState('');
  const [active, setActive] = useState(0);
  const createNote = useNotes(s => s.create);
  const startPipeline = usePipeline(s => s.start);

  const commands: Cmd[] = useMemo(() => [
    { id: 'go-home',      group: 'Jump',   icon: Home,            label: 'Home',          action: () => navigate('/') },
    { id: 'go-tutor',     group: 'Jump',   icon: MessageSquare,   label: 'Tutor',         action: () => navigate('/tutor') },
    { id: 'go-subjects',  group: 'Jump',   icon: BookOpen,        label: 'Subjects',      action: () => navigate('/subjects') },
    { id: 'go-notes',     group: 'Jump',   icon: FileText,        label: 'Notes',         action: () => navigate('/notes') },
    { id: 'go-scratch',   group: 'Jump',   icon: PenLine,         label: 'Rough work',    action: () => navigate('/scratch') },
    { id: 'go-practice',  group: 'Jump',   icon: Layers,          label: 'Practice',      action: () => navigate('/practice') },
    { id: 'go-quiz',      group: 'Jump',   icon: ListChecks,      label: 'Quizzes',       action: () => navigate('/quiz/qz_photo') },
    { id: 'go-plan',      group: 'Jump',   icon: CalendarDays,    label: 'Plan',          action: () => navigate('/plan') },
    { id: 'go-progress',  group: 'Jump',   icon: BarChart3,       label: 'Progress',      action: () => navigate('/progress') },
    { id: 'go-library',   group: 'Jump',   icon: Library,         label: 'Library',       action: () => navigate('/library') },
    { id: 'go-community', group: 'Jump',   icon: Users,           label: 'Community',     action: () => navigate('/community') },
    { id: 'go-settings',  group: 'Jump',   icon: Cog,             label: 'Settings',      action: () => navigate('/settings') },

    { id: 'new-note',     group: 'Create', icon: FilePlus,        label: 'New note',      meta: 'in /notes',
      action: () => { const n = createNote(); navigate(`/notes/${n.id}`); } },

    { id: 'ask-photo',    group: 'Ask',    icon: Sparkles,        label: 'Explain photosynthesis', meta: 'tutor',
      action: () => { navigate('/tutor'); void startPipeline('Explain photosynthesis like I\'m 16 — and prove it.'); } },
    { id: 'ask-iam',      group: 'Ask',    icon: Sparkles,        label: 'Walk me through AWS IAM', meta: 'tutor',
      action: () => { navigate('/tutor'); void startPipeline('Walk me through AWS IAM with a concrete example.'); } },

    { id: 'run-quiz',     group: 'Action', icon: Play,            label: 'Start photosynthesis quiz',
      action: () => navigate('/quiz/qz_photo') }
  ], [navigate, createNote, startPipeline]);

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase();
    if (!term) return commands;
    return commands.filter(c =>
      c.label.toLowerCase().includes(term) || c.group.toLowerCase().includes(term)
    );
  }, [q, commands]);

  // Group preserved insertion order for predictability.
  const grouped = useMemo(() => {
    const m = new Map<string, Cmd[]>();
    filtered.forEach(c => {
      const arr = m.get(c.group) ?? [];
      arr.push(c);
      m.set(c.group, arr);
    });
    return Array.from(m.entries());
  }, [filtered]);

  useEffect(() => { if (open) { setQ(''); setActive(0); inputRef.current?.focus(); } }, [open]);
  useEffect(() => { setActive(0); }, [q]);

  if (!open) return null;

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape')      { onClose(); }
    else if (e.key === 'ArrowDown') { e.preventDefault(); setActive(a => Math.min(a + 1, filtered.length - 1)); }
    else if (e.key === 'ArrowUp')   { e.preventDefault(); setActive(a => Math.max(a - 1, 0)); }
    else if (e.key === 'Enter')     { e.preventDefault(); const c = filtered[active]; if (c) { c.action(); onClose(); } }
  };

  let runningIdx = 0;
  return (
    <div className="cmdk-backdrop" onClick={onClose}>
      <div className="cmdk" onClick={e => e.stopPropagation()} onKeyDown={onKey}>
        <input
          ref={inputRef}
          className="cmdk__input"
          placeholder="Jump to · ask · create…"
          value={q}
          onChange={e => setQ(e.target.value)}
          aria-label="Command palette"
        />
        <div className="cmdk__list" role="listbox">
          {grouped.map(([group, items]) => (
            <div key={group}>
              <div className="cmdk__group-label">{group}</div>
              {items.map(c => {
                const isActive = runningIdx === active;
                runningIdx++;
                const Icon = c.icon;
                return (
                  <div
                    key={c.id}
                    role="option"
                    aria-selected={isActive}
                    className={'cmdk__item' + (isActive ? ' is-active' : '')}
                    onMouseEnter={() => setActive(filtered.indexOf(c))}
                    onClick={() => { c.action(); onClose(); }}
                  >
                    <span className="cmdk__item-icon"><Icon size={14} /></span>
                    <span>{c.label}</span>
                    {c.meta && <span className="cmdk__item-meta">{c.meta}</span>}
                  </div>
                );
              })}
            </div>
          ))}
          {filtered.length === 0 && (
            <div style={{ padding: 24, textAlign: 'center', color: 'var(--color-text-subtle)', fontSize: 13 }}>
              No matches.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
