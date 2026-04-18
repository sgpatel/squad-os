import { useNavigate } from 'react-router-dom';
import { FileText, Plus } from 'lucide-react';
import { useNotes } from '@/store/notes';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Tag } from '@/components/ui/Misc';
import { fmtRelative } from '@/lib/format';

/**
 * Notes index — tag-tinted card grid.
 *
 * Click a card to open the editor. New notes are created via the +
 * button in the header *or* from the command palette ("New note").
 */
export function NotesPage() {
  const navigate = useNavigate();
  const notes  = useNotes(s => s.notes);
  const create = useNotes(s => s.create);

  const newNote = () => {
    const n = create();
    navigate(`/notes/${n.id}`);
  };

  return (
    <div className="page-react">
      <header className="page-header">
        <div>
          <h1>Notes</h1>
          <p>{notes.length} note{notes.length === 1 ? '' : 's'}.</p>
        </div>
        <div className="page-actions">
          <Button variant="primary" leading={<Plus size={14} />} onClick={newNote}>New note</Button>
        </div>
      </header>

      {notes.length === 0 ? (
        <EmptyState
          icon={<FileText size={20} />}
          title="No notes yet"
          hint="Capture a thought from a chapter, paste a snippet, or hit ⌘K → New note. Notes auto-save and stay on your device."
          action={<Button variant="primary" leading={<Plus size={14} />} onClick={newNote}>Create your first note</Button>}
        />
      ) : (
      <div className="subj-grid">
        {notes.map(n => (
          <button
            key={n.id}
            className="subj-card"
            onClick={() => navigate(`/notes/${n.id}`)}
            style={{ textAlign: 'left', cursor: 'pointer' }}
          >
            <div className="subj-card__name">{n.title || 'Untitled'}</div>
            <p className="muted small" style={{ display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
              {n.body.split('\n').slice(0, 4).join(' ') || 'Empty note'}
            </p>
            <div className="row" style={{ gap: 4 }}>
              {n.tags.slice(0, 3).map(t => <Tag key={t}>{t}</Tag>)}
            </div>
            <div className="subj-card__meta">Updated {fmtRelative(n.updatedAt)}</div>
          </button>
        ))}
      </div>
      )}
    </div>
  );
}
