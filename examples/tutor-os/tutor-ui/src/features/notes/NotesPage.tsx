import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileText, Plus, Filter, X } from 'lucide-react';
import { useNotes } from '@/store/notes';
import { useWorkspace } from '@/store/workspace';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Tag } from '@/components/ui/Misc';
import { fmtRelative } from '@/lib/format';
import { SubTabs } from '@/components/ui/SubTabs';
import { NOTES_TABS } from '@/components/layout/hubTabs';

/**
 * Notes index — grid of saved notes with subject + tag filters.
 *
 * Filter model is AND-of-filters: matching a subject AND all selected tags.
 * This matches the user's mental model when saving: "notes I want to see
 * for THIS subject that include THIS tag".
 */
export function NotesPage() {
  const navigate = useNavigate();
  const notes      = useNotes(s => s.notes);
  const create     = useNotes(s => s.create);
  const subjects   = useWorkspace(s => s.workspaceSubjects)();
  const getSubject = useWorkspace(s => s.getSubject);

  const [filterSubjectId, setFilterSubjectId] = useState<string | 'all'>('all');
  const [selectedTags,    setSelectedTags]    = useState<string[]>([]);

  // All tags in the store, with counts, so the UI can show usage hints.
  const tagsWithCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    notes.forEach(n => n.tags.forEach(t => { counts[t] = (counts[t] ?? 0) + 1; }));
    return Object.entries(counts).sort((a, b) => b[1] - a[1]);
  }, [notes]);

  const filteredNotes = useMemo(() => {
    return notes.filter(n => {
      if (filterSubjectId !== 'all') {
        if (filterSubjectId === 'none') {
          if (n.subjectId) return false;
        } else if (n.subjectId !== filterSubjectId) {
          return false;
        }
      }
      if (selectedTags.length) {
        if (!selectedTags.every(t => n.tags.includes(t))) return false;
      }
      return true;
    });
  }, [notes, filterSubjectId, selectedTags]);

  const toggleTag = (t: string) => {
    setSelectedTags(prev => prev.includes(t) ? prev.filter(x => x !== t) : [...prev, t]);
  };

  const clearFilters = () => {
    setFilterSubjectId('all');
    setSelectedTags([]);
  };

  const filtersActive = filterSubjectId !== 'all' || selectedTags.length > 0;

  const newNote = () => {
    const n = create({
      subjectId: filterSubjectId !== 'all' && filterSubjectId !== 'none'
        ? filterSubjectId : undefined,
      tags: selectedTags
    });
    navigate(`/notes/${n.id}`);
  };

  return (
    <div className="page-react">
      <SubTabs tabs={NOTES_TABS} ariaLabel="Notes hub" />
      <header className="page-header">
        <div>
          <h1>Notes</h1>
          <p>
            {filteredNotes.length} of {notes.length} note{notes.length === 1 ? '' : 's'}
            {filtersActive && ' (filtered)'}
          </p>
        </div>
        <div className="page-actions">
          <Button variant="primary" leading={<Plus size={14} />} onClick={newNote}>
            New note
          </Button>
        </div>
      </header>

      {/* ── Filter bar ───────────────────────────────────────── */}
      {notes.length > 0 && (
        <section className="notes-filter" aria-label="Filter notes">
          <div className="notes-filter__row">
            <span className="notes-filter__label"><Filter size={12} /> Subject</span>
            <button
              type="button"
              className={'subj-chip' + (filterSubjectId === 'all' ? ' subj-chip--on' : '')}
              onClick={() => setFilterSubjectId('all')}
            >
              All
            </button>
            <button
              type="button"
              className={'subj-chip' + (filterSubjectId === 'none' ? ' subj-chip--on' : '')}
              style={{ ['--subj-color' as any]: 'var(--color-text-subtle)' }}
              onClick={() => setFilterSubjectId('none')}
            >
              Untagged
            </button>
            {subjects.map(s => {
              const on = filterSubjectId === s.id;
              const count = notes.filter(n => n.subjectId === s.id).length;
              if (count === 0) return null;
              return (
                <button
                  key={s.id}
                  type="button"
                  className={'subj-chip' + (on ? ' subj-chip--on' : '')}
                  style={{ ['--subj-color' as any]: s.color }}
                  onClick={() => setFilterSubjectId(s.id)}
                  title={s.blurb}
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

          {filtersActive && (
            <button
              type="button"
              className="notes-filter__clear"
              onClick={clearFilters}
            >
              <X size={12} /> Clear filters
            </button>
          )}
        </section>
      )}

      {/* ── Results ──────────────────────────────────────────── */}
      {notes.length === 0 ? (
        <EmptyState
          icon={<FileText size={20} />}
          title="No notes yet"
          hint="Tap Save note on any tutor reply, or create one manually. Notes auto-save and stay on your device."
          action={<Button variant="primary" leading={<Plus size={14} />} onClick={newNote}>Create your first note</Button>}
        />
      ) : filteredNotes.length === 0 ? (
        <EmptyState
          icon={<Filter size={20} />}
          title="No notes match these filters"
          hint="Try clearing a filter or picking a different subject/tag combination."
          action={<Button size="sm" onClick={clearFilters}>Clear filters</Button>}
        />
      ) : (
        <div className="subj-grid">
          {filteredNotes.map(n => {
            const subj = n.subjectId ? getSubject(n.subjectId) : undefined;
            return (
              <button
                key={n.id}
                className="subj-card"
                onClick={() => navigate(`/notes/${n.id}`)}
                style={{
                  textAlign: 'left',
                  cursor: 'pointer',
                  ['--subj-color' as any]: subj?.color ?? 'var(--color-border)',
                  ['--subj-tint' as any]: (subj?.color ?? '#64748b') + '18'
                }}
              >
                {subj && (
                  <div className="subj-card__subject-pill" style={{ color: subj.color }}>
                    <span className="subj-card__dot" style={{ background: subj.color }} />
                    {subj.name}
                  </div>
                )}
                <div className="subj-card__name">{n.title || 'Untitled'}</div>
                <p
                  className="muted small"
                  style={{
                    display: '-webkit-box',
                    WebkitLineClamp: 3,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden'
                  }}
                >
                  {n.body.split('\n').slice(0, 4).join(' ') || 'Empty note'}
                </p>
                <div className="row" style={{ gap: 4, flexWrap: 'wrap' }}>
                  {n.tags.slice(0, 3).map(t => <Tag key={t}>#{t}</Tag>)}
                  {n.tags.length > 3 && (
                    <span className="small muted">+{n.tags.length - 3}</span>
                  )}
                </div>
                <div className="subj-card__meta">Updated {fmtRelative(n.updatedAt)}</div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
