import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BookOpen, Plus, RefreshCw, AlertCircle } from 'lucide-react';
import { api, DEMO_LEARNER_ID } from '@/lib/api';
import { useAuth } from '@/store/auth';
import { useWorkspace } from '@/store/workspace';
import { BookUploadModal } from './BookUploadModal';
import type { BookSummary } from '@/lib/types';

/**
 * `<BooksListPage />` — the library view for uploaded textbooks.
 *
 * Cards show title + author + chapter count + upload date and link
 * into {@code /books/:bookId} where the learner picks a chapter.
 *
 * The upload tile opens {@link BookUploadModal} — a multipart PDF
 * dropzone that posts to {@code POST /api/books/upload}. After a
 * successful upload we navigate straight to the new book's detail
 * page so the learner can start studying without going back to the
 * library.
 *
 * Empty state intentionally has the upload affordance up front rather
 * than buried — first-time learners shouldn't have to hunt for the
 * "upload" button.
 */
export function BooksListPage() {
  const navigate  = useNavigate();
  const learnerId = useAuth(s => s.currentUser()?.id ?? DEMO_LEARNER_ID);
  const subjectId = useWorkspace(s => s.activeSubjectId);
  const getSubj   = useWorkspace(s => s.getSubject);
  const subject   = subjectId ? getSubj(subjectId)?.name : null;

  const [books,   setBooks]   = useState<BookSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);

  const refresh = async () => {
    if (!learnerId) return;
    setLoading(true);
    setError(null);
    try {
      const list = await api.books.list(learnerId);
      setBooks(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void refresh(); /* eslint-disable-next-line */ }, [learnerId]);

  return (
    <div className="books">
      <header className="books__head">
        <div>
          <h1 className="books__title">
            <BookOpen size={18} /> Books
          </h1>
          <p className="muted" style={{ fontSize: 12, margin: '4px 0 0' }}>
            Upload a PDF textbook — the tutor will split it into chapters
            and coach you through each one with six learning lenses.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void refresh()}
            disabled={loading}
            title="Refresh"
          >
            <RefreshCw size={13} className={loading ? 'spin' : ''} />
          </button>
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => setUploadOpen(true)}
          >
            <Plus size={13} /> Upload book
          </button>
        </div>
      </header>

      {error && (
        <div className="review__error" role="alert" style={{ marginTop: 12 }}>
          <AlertCircle size={14} /> {error}
        </div>
      )}

      {loading && books.length === 0 ? (
        <div className="books__empty">
          <RefreshCw size={28} className="spin" />
          <p>Loading library…</p>
        </div>
      ) : books.length === 0 ? (
        <div className="books__empty">
          <BookOpen size={32} />
          <p>No books yet — upload your first PDF to get started.</p>
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => setUploadOpen(true)}
          >
            <Plus size={13} /> Upload book
          </button>
        </div>
      ) : (
        <div className="books__grid">
          {books.map(b => <BookCard key={b.id} book={b} onOpen={() => navigate(`/books/${b.id}`)} />)}
        </div>
      )}

      <BookUploadModal
        open={uploadOpen}
        defaultSubject={subject ?? ''}
        learnerId={learnerId}
        onClose={() => setUploadOpen(false)}
        onUploaded={(b) => {
          setUploadOpen(false);
          // Refresh the list so the new book shows even if the user
          // navigates back, but go straight to its detail page now.
          void refresh();
          navigate(`/books/${b.id}`);
        }}
      />
    </div>
  );
}

// ── Card ──────────────────────────────────────────────────────────

function BookCard({ book, onOpen }: { book: BookSummary; onOpen: () => void }) {
  const uploaded = book.uploadedAt
    ? new Date(book.uploadedAt).toLocaleDateString()
    : '';
  return (
    <button type="button" className="book-card" onClick={onOpen}>
      <div className="book-card__cover" aria-hidden>
        <BookOpen size={26} />
      </div>
      <div className="book-card__body">
        <h2 className="book-card__title">{book.title}</h2>
        {book.author && (
          <p className="book-card__author">{book.author}</p>
        )}
        <div className="book-card__meta">
          <span>{book.chapters.length} chapter{book.chapters.length === 1 ? '' : 's'}</span>
          <span aria-hidden>·</span>
          <span>{book.totalPages} pages</span>
          {book.subject && (<><span aria-hidden>·</span><span>{book.subject}</span></>)}
        </div>
        {uploaded && (
          <p className="book-card__uploaded">Uploaded {uploaded}</p>
        )}
      </div>
    </button>
  );
}
