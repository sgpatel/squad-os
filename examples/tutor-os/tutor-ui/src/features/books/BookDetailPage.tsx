import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, BookOpen, AlertCircle, RefreshCw, ChevronRight } from 'lucide-react';
import { api, DEMO_LEARNER_ID } from '@/lib/api';
import { useAuth } from '@/store/auth';
import type { BookSummary } from '@/lib/types';

/**
 * `<BookDetailPage />` — chapter index for one uploaded book.
 *
 * The book metadata (title, author, subject, total pages) anchors the
 * top of the page; each chapter is a row showing number, title, page
 * range, summary, and concept tags. Clicking a row navigates to
 * {@code /books/:bookId/chapter/:n} where the six-lens learning
 * panel lives.
 *
 * Concept tags double as quick-action affordances: each chip links
 * directly into the chapter at the same concept, which becomes the
 * mastery key when the learner self-grades a quiz lens.
 */
export function BookDetailPage() {
  const { bookId } = useParams<{ bookId: string }>();
  const navigate = useNavigate();
  const learnerId = useAuth(s => s.currentUser()?.id ?? DEMO_LEARNER_ID);

  const [book, setBook]       = useState<BookSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState<string | null>(null);

  useEffect(() => {
    if (!bookId) return;
    setLoading(true);
    setError(null);
    api.books.get(learnerId, bookId)
      .then(setBook)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setLoading(false));
  }, [bookId, learnerId]);

  if (loading) {
    return (
      <div className="book-detail">
        <div className="review__empty">
          <RefreshCw size={28} className="spin" />
          <p>Loading book…</p>
        </div>
      </div>
    );
  }
  if (error || !book) {
    return (
      <div className="book-detail">
        <header className="book-detail__head">
          <button className="btn btn--ghost btn--sm" onClick={() => navigate('/books')}>
            <ArrowLeft size={13} /> Back to library
          </button>
        </header>
        <div className="review__empty">
          <AlertCircle size={28} />
          <p>{error || 'Book not found.'}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="book-detail">
      <header className="book-detail__head">
        <button className="btn btn--ghost btn--sm" onClick={() => navigate('/books')}>
          <ArrowLeft size={13} /> Library
        </button>
        <div className="book-detail__meta">
          <h1 className="book-detail__title">
            <BookOpen size={18} /> {book.title}
          </h1>
          <p className="muted">
            {book.author && <>{book.author} · </>}
            {book.subject && <>{book.subject} · </>}
            {book.totalPages} pages · {book.chapters.length} chapter{book.chapters.length === 1 ? '' : 's'}
          </p>
        </div>
      </header>

      <ol className="chapter-list">
        {book.chapters.map(ch => (
          <li
            key={ch.number}
            className="chapter-row"
            onClick={() => navigate(`/books/${book.id}/chapter/${ch.number}`)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                navigate(`/books/${book.id}/chapter/${ch.number}`);
              }
            }}
          >
            <span className="chapter-row__num">{ch.number}</span>
            <div className="chapter-row__body">
              <h3 className="chapter-row__title">{ch.title}</h3>
              {ch.summary && (
                <p className="chapter-row__summary">{ch.summary}</p>
              )}
              <div className="chapter-row__meta">
                <span>pp. {ch.pageStart}–{ch.pageEnd}</span>
                {ch.concepts.length > 0 && (
                  <>
                    <span aria-hidden>·</span>
                    <div className="chapter-row__concepts">
                      {ch.concepts.slice(0, 4).map(c => (
                        <span key={c} className="concept-chip">{c}</span>
                      ))}
                      {ch.concepts.length > 4 && (
                        <span className="muted" style={{ fontSize: 11 }}>
                          +{ch.concepts.length - 4} more
                        </span>
                      )}
                    </div>
                  </>
                )}
              </div>
            </div>
            <ChevronRight size={16} className="chapter-row__chevron" aria-hidden />
          </li>
        ))}
      </ol>
    </div>
  );
}
