import { useEffect, useRef, useState } from 'react';
import { Bookmark, X, Tag as TagIcon, Check } from 'lucide-react';
import { useNotes } from '@/store/notes';
import { useWorkspace } from '@/store/workspace';
import { Button } from '@/components/ui/Button';
import { Tag } from '@/components/ui/Misc';

/**
 * SaveNotePopover — lightweight inline dialog for saving a tutor message
 * as a note. Opens from a {@link SaveNoteButton} anchored to a message.
 *
 * Fields:
 *   - Title (editable; pre-filled with first line / truncated body)
 *   - Body  (editable; pre-filled with the tutor message)
 *   - Subject (defaults to the active subject; user can override or clear)
 *   - Tags (chip input, free-form, comma-or-Enter to add)
 *
 * The popover commits to the Zustand notes store and closes. No backend
 * call — notes live in localStorage via the persisted store.
 */

interface Props {
  open: boolean;
  /** Raw tutor message text — pre-fills the body. */
  sourceBody: string;
  /** Message id so we can back-reference. */
  sourceMessageId: string;
  /** Optional suggested tags (e.g. from the concept the message was about). */
  suggestedTags?: string[];
  onClose: () => void;
  /** Fires after a successful save so the caller can show a "Saved ✓" pulse. */
  onSaved?: () => void;
}

export function SaveNotePopover({
  open,
  sourceBody,
  sourceMessageId,
  suggestedTags = [],
  onClose,
  onSaved
}: Props) {
  const create            = useNotes(s => s.create);
  const workspaceSubjects = useWorkspace(s => s.workspaceSubjects);
  const activeSubjectId   = useWorkspace(s => s.activeSubjectId);
  const subjects          = workspaceSubjects();

  const [title,     setTitle]     = useState('');
  const [body,      setBody]      = useState('');
  const [subjectId, setSubjectId] = useState<string | ''>('');
  const [tags,      setTags]      = useState<string[]>([]);
  const [tagInput,  setTagInput]  = useState('');

  const titleRef = useRef<HTMLInputElement>(null);

  // Reset + focus on open so each save is fresh.
  useEffect(() => {
    if (!open) return;
    const trimmed = sourceBody.trim();
    // Title: first line up to 70 chars, stripping markdown-ish leads.
    const firstLine = trimmed.split('\n')[0] ?? '';
    const cleaned   = firstLine.replace(/^[#>*\-\s]+/, '').slice(0, 72);
    setTitle(cleaned || 'Saved from tutor');
    setBody(trimmed);
    setSubjectId(activeSubjectId ?? '');
    setTags(suggestedTags.slice(0, 3));
    setTagInput('');
    requestAnimationFrame(() => titleRef.current?.select());
  }, [open, sourceBody, activeSubjectId, suggestedTags]);

  // Escape-to-close.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  const addTag = (raw: string) => {
    raw.split(',').map(t => t.trim().toLowerCase()).filter(Boolean).forEach(t => {
      setTags(prev => prev.includes(t) ? prev : [...prev, t]);
    });
    setTagInput('');
  };

  const onTagKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      if (tagInput.trim()) addTag(tagInput);
    } else if (e.key === 'Backspace' && !tagInput && tags.length) {
      setTags(tags.slice(0, -1));
    }
  };

  const save = (e?: React.FormEvent) => {
    e?.preventDefault();
    const trimmedTitle = title.trim() || 'Saved from tutor';
    const trimmedBody  = body.trim();
    if (!trimmedBody) return;
    create({
      title:           trimmedTitle,
      body:            trimmedBody,
      subjectId:       subjectId || undefined,
      sourceMessageId,
      tags:            Array.from(new Set(tags))
    });
    onSaved?.();
    onClose();
  };

  const activeSubject = subjects.find(s => s.id === subjectId);

  return (
    <div
      className="savenote-backdrop"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="savenote-title"
    >
      <form className="savenote" onSubmit={save}>
        <header className="savenote__head">
          <div className="savenote__head-title" id="savenote-title">
            <Bookmark size={14} /> Save as note
          </div>
          <button
            type="button"
            className="savenote__close"
            onClick={onClose}
            aria-label="Close"
          >
            <X size={14} />
          </button>
        </header>

        <div className="savenote__body">
          {/* Title */}
          <label className="field">
            <span className="field__label">Title</span>
            <input
              ref={titleRef}
              className="field__input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={120}
              autoComplete="off"
            />
          </label>

          {/* Body preview — editable so the user can trim the quote */}
          <label className="field">
            <span className="field__label">Note content</span>
            <textarea
              className="field__textarea"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              rows={6}
            />
          </label>

          {/* Subject picker */}
          <div className="field">
            <span className="field__label">Subject</span>
            <div className="savenote__subjects" role="radiogroup" aria-label="Subject">
              <button
                type="button"
                role="radio"
                aria-checked={!subjectId}
                className={'subj-chip' + (!subjectId ? ' subj-chip--on' : '')}
                style={{ ['--subj-color' as any]: 'var(--color-text-subtle)' }}
                onClick={() => setSubjectId('')}
              >
                No subject
              </button>
              {subjects.map(s => {
                const on = subjectId === s.id;
                return (
                  <button
                    key={s.id}
                    type="button"
                    role="radio"
                    aria-checked={on}
                    className={'subj-chip' + (on ? ' subj-chip--on' : '')}
                    style={{ ['--subj-color' as any]: s.color }}
                    onClick={() => setSubjectId(s.id)}
                  >
                    {s.name}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Tags */}
          <div className="field">
            <span className="field__label">Tags</span>
            <div className="savenote__tags">
              <TagIcon size={12} />
              {tags.map(t => (
                <Tag key={t}>
                  {t}
                  <button
                    type="button"
                    onClick={() => setTags(tags.filter(x => x !== t))}
                    aria-label={`Remove ${t}`}
                    className="savenote__tag-rm"
                  >×</button>
                </Tag>
              ))}
              <input
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onKeyDown={onTagKey}
                onBlur={() => { if (tagInput.trim()) addTag(tagInput); }}
                placeholder={tags.length ? 'add tag' : 'e.g. exam, revisit, formula'}
                className="savenote__tag-input"
              />
            </div>
            <span className="small muted">
              Enter or comma to add. Subject is saved alongside tags — filter both on the Notes page.
            </span>
          </div>
        </div>

        <footer className="savenote__foot">
          <span className="small muted">
            {activeSubject
              ? <>Saving to <strong style={{ color: activeSubject.color }}>{activeSubject.name}</strong></>
              : <>Saving without a subject</>}
          </span>
          <div className="row">
            <Button type="button" variant="ghost" size="sm" onClick={onClose}>Cancel</Button>
            <Button type="submit" variant="primary" size="sm" leading={<Check size={13} />}>
              Save note
            </Button>
          </div>
        </footer>
      </form>
    </div>
  );
}
