import { useEffect, useMemo, useRef, useState } from 'react';
import { X, Upload, FileText, RefreshCw } from 'lucide-react';
import { api } from '@/lib/api';
import { useWorkspace } from '@/store/workspace';
import { ApiError } from '@/lib/api';
import type { BookSummary } from '@/lib/types';

/**
 * `<BookUploadModal />` — multipart PDF upload for a learner-supplied
 * textbook.
 *
 * <p>Mirrors the SyllabusSheet upload pattern (PR-D) — same backdrop
 * shape, same drag-and-drop dropzone, same 8/32 MiB error mapping.
 * Differs in that we collect title + (optional) author + subject as
 * form fields BEFORE the backend ingestion, so the resulting Book
 * row has the metadata the learner expects to see on the library
 * card.
 *
 * <p>Closes automatically on a successful upload and calls
 * {@code onUploaded(BookSummary)} so the parent can navigate to the
 * new book's detail page.
 */
export function BookUploadModal({
  open, onClose, onUploaded, learnerId, defaultSubject,
}: {
  open: boolean;
  onClose: () => void;
  onUploaded: (book: BookSummary) => void;
  learnerId: string;
  /** Subject pulled from the active workspace subject; learner can override. */
  defaultSubject: string;
}) {
  // IMPORTANT: do NOT do `useWorkspace(s => s.workspaceSubjects())` here.
  // That selector returns a freshly-`.filter()`-ed array on every call,
  // so zustand's useSyncExternalStore sees a new snapshot reference each
  // render and schedules another update → "Maximum update depth exceeded".
  // Subscribe to stable primitives instead and derive the filtered list
  // with useMemo so the reference is stable when nothing actually changed.
  const activeWorkspaceId = useWorkspace(s => s.activeWorkspaceId);
  const allSubjects       = useWorkspace(s => s.subjects);
  const workspaces        = useWorkspace(s => s.workspaces);
  const subjects = useMemo(() => {
    const ws = workspaces.find(w => w.id === activeWorkspaceId);
    if (!ws) return allSubjects;
    return allSubjects.filter(s => ws.subjectIds.includes(s.id));
  }, [activeWorkspaceId, allSubjects, workspaces]);

  const [title,   setTitle]   = useState('');
  const [author,  setAuthor]  = useState('');
  const [subject, setSubject] = useState(defaultSubject);
  const [file,    setFile]    = useState<File | null>(null);
  const [over,    setOver]    = useState(false);
  const [busy,    setBusy]    = useState(false);
  const [error,   setError]   = useState<string | null>(null);

  // Reset whenever the modal opens — stale state from a prior upload
  // would be confusing.
  useEffect(() => {
    if (open) {
      setTitle('');
      setAuthor('');
      setSubject(defaultSubject);
      setFile(null);
      setError(null);
    }
  }, [open, defaultSubject]);

  // Escape closes (same affordance as SyllabusSheet).
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const titleRef = useRef<HTMLInputElement>(null);
  useEffect(() => { if (open) titleRef.current?.focus(); }, [open]);

  if (!open) return null;

  const submit = async () => {
    if (!file || !title.trim() || !subject.trim()) {
      setError('Title, subject, and a PDF are all required.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const book = await api.books.upload({
        file,
        learnerId,
        subject: subject.trim(),
        title:   title.trim(),
        author:  author.trim() || undefined,
      });
      onUploaded(book);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        setError('Backend rejected the upload — empty / oversized / not a PDF? (max 32 MB)');
      } else {
        setError(e instanceof Error ? e.message : String(e));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="savenote-backdrop"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="book-upload-title"
    >
      <div className="savenote" style={{ maxWidth: 560 }}>
        <header className="savenote__head">
          <div className="savenote__head-title" id="book-upload-title">
            <Upload size={14} /> Upload a textbook
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
          {/* Metadata first — the dropzone is the last step so the
              learner only thinks about the file once their context is
              set. */}
          <label className="field">
            <span className="field__label">Book title *</span>
            <input
              ref={titleRef}
              className="field__input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Campbell Biology, 12th ed."
              maxLength={200}
            />
          </label>

          <label className="field">
            <span className="field__label">Author (optional)</span>
            <input
              className="field__input"
              value={author}
              onChange={(e) => setAuthor(e.target.value)}
              placeholder="e.g. Urry et al."
              maxLength={120}
            />
          </label>

          <label className="field">
            <span className="field__label">Subject *</span>
            {subjects.length > 0 ? (
              <select
                className="field__input"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
              >
                <option value="">— pick a subject —</option>
                {subjects.map(s => (
                  <option key={s.id} value={s.name}>{s.name}</option>
                ))}
              </select>
            ) : (
              <input
                className="field__input"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder="e.g. Biology"
              />
            )}
          </label>

          {/* Dropzone */}
          <div
            className={'syllabus-drop' + (over ? ' syllabus-drop--over' : '')
              + (busy ? ' syllabus-drop--busy' : '')}
            onDragOver={(e) => { e.preventDefault(); setOver(true); }}
            onDragLeave={() => setOver(false)}
            onDrop={(e) => {
              e.preventDefault();
              setOver(false);
              if (busy) return;
              const f = e.dataTransfer.files?.[0];
              if (f) setFile(f);
            }}
          >
            <input
              id="book-pdf"
              type="file"
              accept="application/pdf"
              style={{ display: 'none' }}
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            <label htmlFor="book-pdf" className="syllabus-drop__inner">
              <div className="syllabus-drop__icon">
                {busy ? <RefreshCw size={20} className="spin" /> : <FileText size={20} />}
              </div>
              <div className="syllabus-drop__text">
                <strong>
                  {busy ? 'Reading textbook…' : file ? file.name : 'Drop a PDF here or click to browse'}
                </strong>
                <span className="muted">
                  {busy
                    ? 'Extracting chapters and concepts — may take a minute on large books'
                    : file
                      ? `${(file.size / 1024 / 1024).toFixed(1)} MB`
                      : 'PDF up to 128 MB'}
                </span>
              </div>
            </label>
            <p className="muted" style={{ fontSize: 11, marginTop: 8 }}>
              The tutor splits chapters automatically by heading
              ("Chapter N", "Unit N", "N. Title"). Books without
              detectable headings get a page-bucket fallback.
            </p>
          </div>

          {error && (
            <div role="alert" className="muted" style={{ color: 'var(--color-danger)', fontSize: 12 }}>
              {error}
            </div>
          )}
        </div>

        <footer className="savenote__foot">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn--primary"
            onClick={submit}
            disabled={busy || !file || !title.trim() || !subject.trim()}
          >
            <Upload size={13} /> {busy ? 'Uploading…' : 'Upload + extract'}
          </button>
        </footer>
      </div>
    </div>
  );
}
