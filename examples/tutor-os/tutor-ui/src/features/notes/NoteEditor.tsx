import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { ArrowLeft, Trash2, Tag as TagIcon, Save } from 'lucide-react';
import { useNotes } from '@/store/notes';
import { Button } from '@/components/ui/Button';
import { Tag } from '@/components/ui/Misc';
import { fmtRelative } from '@/lib/format';

/**
 * Note editor — title input + contenteditable body + tag manager.
 *
 * Autosaves silently on blur and on a 1.5s debounce. The contenteditable
 * is plain text (no rich formatting) — clean, fast, dependency-free.
 * The architecture leaves room to swap in a real rich-text engine
 * (Tiptap/Lexical) without touching the rest of the app.
 */
export function NoteEditor() {
  const { noteId } = useParams<{ noteId: string }>();
  const navigate = useNavigate();
  const noteList = useNotes(s => s.notes);
  const update   = useNotes(s => s.update);
  const remove   = useNotes(s => s.remove);

  const note = noteList.find(n => n.id === noteId);
  const bodyRef = useRef<HTMLDivElement>(null);
  const [title,    setTitle]    = useState(note?.title ?? '');
  const [body,     setBody]     = useState(note?.body  ?? '');
  const [tagInput, setTagInput] = useState('');
  const [tags,     setTags]     = useState(note?.tags  ?? []);
  const [savedAt,  setSavedAt]  = useState<number | null>(null);

  // Re-sync local state when switching notes.
  useEffect(() => {
    if (!note) return;
    setTitle(note.title); setBody(note.body); setTags(note.tags);
    if (bodyRef.current && bodyRef.current.innerText !== note.body) {
      bodyRef.current.innerText = note.body;
    }
  }, [note?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Debounced autosave.
  useEffect(() => {
    if (!note) return;
    const handle = window.setTimeout(() => {
      update(note.id, { title, body, tags });
      setSavedAt(Date.now());
    }, 1500);
    return () => clearTimeout(handle);
  }, [title, body, tags, note, update]);

  if (!note) {
    return (
      <div className="page-react">
        <p className="muted">Note not found.</p>
        <Link to="/notes" className="btn btn--sm mt-3">Back to notes</Link>
      </div>
    );
  }

  const wordCount = body.trim().split(/\s+/).filter(Boolean).length;

  const addTag = () => {
    const t = tagInput.trim().toLowerCase();
    if (!t || tags.includes(t)) return;
    setTags([...tags, t]); setTagInput('');
  };

  return (
    <div className="page-react">
      <Link to="/notes" className="small muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginBottom: 12 }}>
        <ArrowLeft size={12} /> Notes
      </Link>

      <div className="note-editor">
        <div className="note-editor__toolbar">
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Save size={12} />
            {savedAt ? `Saved ${fmtRelative(savedAt)}` : 'Autosaves on type'}
          </span>
          <span style={{ flex: 1 }} />
          <Button variant="ghost" size="sm" leading={<Trash2 size={12} />}
            onClick={() => { if (confirm('Delete this note?')) { remove(note.id); navigate('/notes'); } }}>
            Delete
          </Button>
        </div>

        <input
          className="note-editor__title"
          value={title}
          onChange={e => setTitle(e.target.value)}
          placeholder="Title"
          aria-label="Note title"
        />

        <div
          ref={bodyRef}
          className="note-editor__body"
          contentEditable
          suppressContentEditableWarning
          data-placeholder="Start writing — Markdown-ish syntax works (`code`, **bold**)."
          onInput={(e) => setBody((e.currentTarget as HTMLDivElement).innerText)}
        />

        <div className="note-editor__status">
          <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
            <TagIcon size={12} />
            {tags.map(t => (
              <Tag key={t}>
                {t}
                <button onClick={() => setTags(tags.filter(x => x !== t))} aria-label={`Remove ${t}`}
                  style={{ marginLeft: 4, background: 'none', border: 'none', color: 'inherit', cursor: 'pointer' }}>×</button>
              </Tag>
            ))}
            <input
              value={tagInput}
              onChange={e => setTagInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
              placeholder="add tag"
              style={{ background: 'transparent', border: 'none', outline: 'none', color: 'var(--color-text-muted)', fontSize: 11, width: 80 }}
            />
          </div>
          <span>{wordCount} word{wordCount === 1 ? '' : 's'}</span>
        </div>
      </div>
    </div>
  );
}
