import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ScrollText, Filter, Printer, Download, Copy, Sparkles,
  Columns, Rows, Check, X, Wand2
} from 'lucide-react';
import { useNotes } from '@/store/notes';
import { useWorkspace } from '@/store/workspace';
import { usePipeline } from '@/store/pipeline';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Markdown } from '@/components/ui/Markdown';
import { SubTabs } from '@/components/ui/SubTabs';
import { NOTES_TABS } from '@/components/layout/hubTabs';

/**
 * Cheatsheet Builder — compacts selected notes into a single print-ready
 * cheatsheet. Inspired by GPAI's Cheatsheet Builder:
 *
 *   1. Filter: subject + tag → candidate notes
 *   2. Select: checkbox list (all-by-default)
 *   3. Density: 1-column (readable) · 2-column (dense) · 3-column (exam)
 *   4. Compose: live preview rendered via the shared Markdown pipeline
 *   5. Ship: Print · Download .md · Copy · "AI compact" (fires the tutor
 *      pipeline to condense every selected note into a tighter cheatsheet)
 *
 * Everything lives in localStorage via the existing notes store — no
 * backend round-trip required.
 */
export function CheatsheetPage() {
  const navigate   = useNavigate();
  const notes      = useNotes(s => s.notes);
  const subjects   = useWorkspace(s => s.workspaceSubjects)();
  const getSubject = useWorkspace(s => s.getSubject);
  const startPipeline = usePipeline(s => s.start);
  const isRunning     = usePipeline(s => s.isRunning);

  const [filterSubjectId, setFilterSubjectId] = useState<string | 'all'>('all');
  const [selectedTags,    setSelectedTags]    = useState<string[]>([]);
  const [excluded,        setExcluded]        = useState<Set<string>>(new Set());
  const [density,         setDensity]         = useState<1 | 2 | 3>(2);
  const [title,           setTitle]           = useState('My cheatsheet');

  // All tags present, for the quick chip filter.
  const tagsWithCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    notes.forEach(n => n.tags.forEach(t => { counts[t] = (counts[t] ?? 0) + 1; }));
    return Object.entries(counts).sort((a, b) => b[1] - a[1]);
  }, [notes]);

  // Candidate notes = matching filter.
  const candidates = useMemo(() => {
    return notes.filter(n => {
      if (filterSubjectId !== 'all' && n.subjectId !== filterSubjectId) return false;
      if (selectedTags.length && !selectedTags.every(t => n.tags.includes(t))) return false;
      return true;
    });
  }, [notes, filterSubjectId, selectedTags]);

  // Included = candidates minus user-excluded.
  const included = candidates.filter(n => !excluded.has(n.id));

  const toggleTag = (t: string) =>
    setSelectedTags(prev => prev.includes(t) ? prev.filter(x => x !== t) : [...prev, t]);
  const toggleNote = (id: string) =>
    setExcluded(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  const includeAll = () => setExcluded(new Set());
  const excludeAll = () => setExcluded(new Set(candidates.map(n => n.id)));

  /** Compose a single markdown document from the included notes. */
  const markdown = useMemo(() => {
    if (!included.length) return '';
    const subjLabel = filterSubjectId === 'all'
      ? 'All subjects'
      : subjects.find(s => s.id === filterSubjectId)?.name ?? '—';
    const tagLabel = selectedTags.length ? selectedTags.map(t => `#${t}`).join(' ') : '—';
    const header =
      `# ${title}\n` +
      `_${subjLabel} · ${tagLabel} · ${included.length} note${included.length === 1 ? '' : 's'}_\n\n`;
    const body = included.map(n => {
      const subj = n.subjectId ? getSubject(n.subjectId) : undefined;
      const subjLine = subj ? `_${subj.name}_` : '';
      const tagLine = n.tags.length ? n.tags.map(t => `\`#${t}\``).join(' ') : '';
      const meta = [subjLine, tagLine].filter(Boolean).join(' · ');
      return `## ${n.title || 'Untitled'}\n${meta ? meta + '\n\n' : ''}${n.body.trim()}`;
    }).join('\n\n---\n\n');
    return header + body;
  }, [included, title, filterSubjectId, selectedTags, subjects, getSubject]);

  // ── Actions ──────────────────────────────────────────────────────────
  const copy = async () => {
    try { await navigator.clipboard.writeText(markdown); }
    catch { /* clipboard blocked */ }
  };
  const downloadMd = () => {
    const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const slug = (title || 'cheatsheet').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'cheatsheet';
    a.href = url; a.download = `${slug}.md`;
    document.body.appendChild(a); a.click(); a.remove();
    URL.revokeObjectURL(url);
  };
  const printSheet = () => { window.print(); };

  const aiCompact = () => {
    if (!markdown.trim() || isRunning) return;
    const clipped = markdown.length > 8000 ? markdown.slice(0, 8000) + '\n…' : markdown;
    const prompt =
      `Compact the cheatsheet below into a tight, exam-ready one-pager.\n` +
      `Rules:\n` +
      `  - Preserve every heading so the structure matches the source.\n` +
      `  - Under each heading, keep only the 3-5 highest-yield bullets.\n` +
      `  - Inline formulas with $…$, multiline with $$ $$.\n` +
      `  - Use bold for terms, code ticks for keywords.\n` +
      `  - No fluff, no intro, no closing — just the cheatsheet.\n\n` +
      `---\n\n${clipped}`;
    void startPipeline(prompt);
    navigate('/'); // watch the compaction stream on the home thread
  };

  // ── Render ───────────────────────────────────────────────────────────
  if (notes.length === 0) {
    return (
      <div className="page-react">
        <EmptyState
          icon={<ScrollText size={20} />}
          title="No notes to compact yet"
          hint="Save notes from tutor replies or create them manually — then compose them into a cheatsheet here."
          action={<Button variant="primary" onClick={() => navigate('/notes')}>Go to Notes</Button>}
        />
      </div>
    );
  }

  return (
    <div className="page-react cheatsheet">
      <SubTabs tabs={NOTES_TABS} ariaLabel="Notes hub" />
      <header className="page-header">
        <div>
          <h1>Cheatsheet Builder</h1>
          <p>
            {included.length} of {candidates.length} matching note{candidates.length === 1 ? '' : 's'}
            {' · '}{density}-column layout
          </p>
        </div>
        <div className="page-actions">
          <Button variant="ghost" size="sm" leading={<Copy size={12} />} onClick={copy} disabled={!included.length}>
            Copy
          </Button>
          <Button variant="ghost" size="sm" leading={<Download size={12} />} onClick={downloadMd} disabled={!included.length}>
            .md
          </Button>
          <Button variant="ghost" size="sm" leading={<Printer size={12} />} onClick={printSheet} disabled={!included.length}>
            Print
          </Button>
          <Button
            variant="primary"
            size="sm"
            leading={<Wand2 size={12} />}
            onClick={aiCompact}
            disabled={!included.length || isRunning}
            title="Let the tutor compact this cheatsheet to a tighter one-pager"
          >
            AI compact
          </Button>
        </div>
      </header>

      {/* ── Filter + layout controls ──────────────────────────── */}
      <section className="cheatsheet__controls no-print">
        <div className="notes-filter">
          <div className="notes-filter__row">
            <span className="notes-filter__label"><Filter size={12} /> Subject</span>
            <button
              type="button"
              className={'subj-chip' + (filterSubjectId === 'all' ? ' subj-chip--on' : '')}
              onClick={() => setFilterSubjectId('all')}
            >All</button>
            {subjects.map(s => {
              const count = notes.filter(n => n.subjectId === s.id).length;
              if (count === 0) return null;
              const on = filterSubjectId === s.id;
              return (
                <button
                  key={s.id}
                  type="button"
                  className={'subj-chip' + (on ? ' subj-chip--on' : '')}
                  style={{ ['--subj-color' as any]: s.color }}
                  onClick={() => setFilterSubjectId(s.id)}
                >
                  {s.name} <span className="subj-chip__count">{count}</span>
                </button>
              );
            })}
          </div>

          {tagsWithCounts.length > 0 && (
            <div className="notes-filter__row">
              <span className="notes-filter__label"><Filter size={12} /> Tags</span>
              {tagsWithCounts.map(([tag, count]) => {
                const on = selectedTags.includes(tag);
                return (
                  <button
                    key={tag}
                    type="button"
                    className={'intent-chip' + (on ? ' intent-chip--on' : '')}
                    onClick={() => toggleTag(tag)}
                  >
                    #{tag}
                    <span className="intent-chip__badge">{count}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <div className="cheatsheet__options">
          <label className="field" style={{ flex: '1 1 240px' }}>
            <span className="field__label">Title</span>
            <input
              className="field__input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={80}
            />
          </label>
          <div className="field">
            <span className="field__label">Density</span>
            <div className="md-mode-toggle" role="tablist" aria-label="Density">
              <button
                type="button" role="tab" aria-selected={density === 1}
                className={'md-mode-toggle__btn' + (density === 1 ? ' md-mode-toggle__btn--on' : '')}
                onClick={() => setDensity(1)}
              ><Rows size={12} /> 1 col</button>
              <button
                type="button" role="tab" aria-selected={density === 2}
                className={'md-mode-toggle__btn' + (density === 2 ? ' md-mode-toggle__btn--on' : '')}
                onClick={() => setDensity(2)}
              ><Columns size={12} /> 2 col</button>
              <button
                type="button" role="tab" aria-selected={density === 3}
                className={'md-mode-toggle__btn' + (density === 3 ? ' md-mode-toggle__btn--on' : '')}
                onClick={() => setDensity(3)}
              ><Columns size={12} /> 3 col</button>
            </div>
          </div>
        </div>

        {/* Per-note pick list */}
        <div className="cheatsheet__picker">
          <div className="cheatsheet__picker-head">
            <span className="small muted">Included notes ({included.length} of {candidates.length})</span>
            <span className="row" style={{ gap: 4 }}>
              <Button size="sm" variant="ghost" onClick={includeAll} disabled={excluded.size === 0}>
                <Check size={11} /> All
              </Button>
              <Button size="sm" variant="ghost" onClick={excludeAll} disabled={excluded.size === candidates.length}>
                <X size={11} /> None
              </Button>
            </span>
          </div>
          {candidates.length === 0 ? (
            <p className="small muted">No notes match these filters.</p>
          ) : (
            <ul className="cheatsheet__picker-list">
              {candidates.map(n => {
                const on = !excluded.has(n.id);
                const subj = n.subjectId ? getSubject(n.subjectId) : undefined;
                return (
                  <li key={n.id}>
                    <button
                      type="button"
                      className={'cheatsheet__pick' + (on ? ' cheatsheet__pick--on' : '')}
                      onClick={() => toggleNote(n.id)}
                    >
                      <span className="cheatsheet__pick-check">
                        {on ? <Check size={12} /> : null}
                      </span>
                      <span className="cheatsheet__pick-text">
                        <span className="cheatsheet__pick-title">{n.title || 'Untitled'}</span>
                        {subj && (
                          <span className="cheatsheet__pick-sub" style={{ color: subj.color }}>
                            {subj.name}
                          </span>
                        )}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </section>

      {/* ── Live cheatsheet preview ─────────────────────────── */}
      <section className="cheatsheet__sheet" aria-label="Cheatsheet preview">
        {included.length === 0 ? (
          <p className="muted small" style={{ padding: 'var(--space-6)' }}>
            Select at least one note to compose your cheatsheet.
          </p>
        ) : (
          <div
            className="cheatsheet__page"
            style={{ ['--cs-cols' as any]: density }}
          >
            <Markdown>{markdown}</Markdown>
          </div>
        )}
      </section>

      <footer className="no-print" style={{ marginTop: 'var(--space-5)', textAlign: 'center' }}>
        <Button variant="ghost" size="sm" leading={<Sparkles size={12} />} onClick={() => navigate('/notes')}>
          Manage source notes
        </Button>
      </footer>
    </div>
  );
}
