import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  RefreshCw, ArrowLeft, AlertCircle, CheckCircle2,
  Sparkles, X, ThumbsUp, Zap
} from 'lucide-react';
import { api } from '@/lib/api';
import { useReviewQueue } from './useReviewQueue';
import type { MasteryGrade, ReviewQueueItem } from '@/lib/types';

/**
 * `<ReviewPage />` — the spaced-repetition surface backed by the M3-A
 * mastery graph. Pulls due concepts from
 * {@code GET /api/review/queue/{learnerId}/{subject}} and lets the
 * learner grade each card with the standard 4-button SM-2 scale; each
 * grade roundtrips through {@code POST /api/review/.../answer} so the
 * SM-2 schedule advances server-side and the next pull excludes the card.
 *
 * Design choices
 *   - The queue is fetched once on mount, then we consume it locally to
 *     avoid a round-trip per card. After grading we trust the server's
 *     "this card is no longer due" view by simply popping the front of
 *     our local list — a refresh button is one click away if anything
 *     diverges.
 *   - Cards show concept + score band only. Question content is OUT OF
 *     SCOPE for this PR; M3-C will materialise per-card questions via
 *     the existing PracticeAgent. Grading the concept directly is still
 *     useful (Anki-style "did I remember it?") and matches how learners
 *     do flashcard review today.
 *   - Grade buttons match the {@link MasteryGrade} enum 1:1 so the wire
 *     payload stays trivial and the keyboard shortcuts (1/2/3/4) are a
 *     muscle-memory match for Anki users.
 */
export function ReviewPage() {
  const navigate = useNavigate();
  const { items, count, loading, error, refresh, subject, learnerId } = useReviewQueue();

  // Local working copy. We splice from the FRONT as the learner grades
  // so the queue order (most-overdue first) is preserved without a
  // refetch per card.
  const [queue, setQueue] = useState<ReviewQueueItem[]>([]);
  const [busy,  setBusy]  = useState(false);
  const [doneCount, setDoneCount] = useState(0);
  const [postError, setPostError] = useState<string | null>(null);

  useEffect(() => { setQueue(items); setDoneCount(0); }, [items]);

  const current = queue[0] ?? null;

  // ── Grade handler ───────────────────────────────────────────────

  const grade = async (g: MasteryGrade) => {
    if (!current || !subject || busy) return;
    setBusy(true);
    setPostError(null);
    try {
      await api.review.answer(learnerId, subject, {
        concept: current.concept,
        grade:   g,
        // Source defaults to "review" server-side; we omit it here to
        // keep the request body minimal.
      });
      setQueue(q => q.slice(1));
      setDoneCount(n => n + 1);
    } catch (e) {
      setPostError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  // Keyboard: 1=AGAIN, 2=HARD, 3=GOOD, 4=EASY (matches Anki).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (busy || !current) return;
      if (e.key === '1') void grade('AGAIN');
      else if (e.key === '2') void grade('HARD');
      else if (e.key === '3') void grade('GOOD');
      else if (e.key === '4') void grade('EASY');
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // grade closes over current+busy — re-bind when those change.
  }, [busy, current]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Render ──────────────────────────────────────────────────────

  if (!subject) {
    return (
      <div className="review">
        <header className="review__head">
          <button className="btn btn--ghost btn--sm" onClick={() => navigate(-1)}>
            <ArrowLeft size={13} /> Back
          </button>
          <h1 className="review__title">Review</h1>
        </header>
        <div className="review__empty">
          <AlertCircle size={28} />
          <p>Pick a subject before reviewing — the queue is per-subject.</p>
          <button className="btn btn--primary" onClick={() => navigate('/subjects')}>
            Choose a subject
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="review">
      <header className="review__head">
        <button className="btn btn--ghost btn--sm" onClick={() => navigate(-1)}>
          <ArrowLeft size={13} /> Back
        </button>
        <h1 className="review__title">Review — {subject}</h1>
        <div className="review__meta">
          <span title="Cards remaining today">{queue.length} due</span>
          <span aria-hidden>·</span>
          <span title="Cards graded this session">{doneCount} done</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void refresh()}
            disabled={loading}
            title="Refresh queue from server"
          >
            <RefreshCw size={13} className={loading ? 'spin' : ''} />
          </button>
        </div>
      </header>

      {error && (
        <div className="review__error" role="alert">
          <AlertCircle size={14} /> {error}
        </div>
      )}

      {/* Empty state — initial load OR queue exhausted via grading */}
      {loading && queue.length === 0 ? (
        <div className="review__empty">
          <RefreshCw size={28} className="spin" />
          <p>Loading review queue…</p>
        </div>
      ) : queue.length === 0 ? (
        <div className="review__empty">
          <CheckCircle2 size={28} className="review__empty-icon--ok" />
          <p>
            {doneCount > 0
              ? `Nice — ${doneCount} card${doneCount === 1 ? '' : 's'} cleared.`
              : `No cards due in ${subject} right now.`}
          </p>
          <p className="muted" style={{ fontSize: 12 }}>
            New cards arrive automatically as you answer practice questions
            and quizzes. Come back tomorrow.
          </p>
          {count > 0 && (
            <button className="btn btn--ghost" onClick={() => void refresh()}>
              Refresh
            </button>
          )}
        </div>
      ) : (
        <main className="review__stage">
          <ReviewCard item={current!} subject={subject} />

          {postError && (
            <div className="review__error" role="alert">
              <AlertCircle size={14} /> {postError}
            </div>
          )}

          <div
            className="review__grades"
            role="group"
            aria-label="How well did you know this?"
          >
            <button
              type="button"
              className="grade grade--again"
              disabled={busy}
              onClick={() => void grade('AGAIN')}
            >
              <X size={14} />
              <span className="grade__label">Again</span>
              <kbd>1</kbd>
            </button>
            <button
              type="button"
              className="grade grade--hard"
              disabled={busy}
              onClick={() => void grade('HARD')}
            >
              <ThumbsUp size={14} style={{ transform: 'rotate(-25deg)' }} />
              <span className="grade__label">Hard</span>
              <kbd>2</kbd>
            </button>
            <button
              type="button"
              className="grade grade--good"
              disabled={busy}
              onClick={() => void grade('GOOD')}
            >
              <ThumbsUp size={14} />
              <span className="grade__label">Good</span>
              <kbd>3</kbd>
            </button>
            <button
              type="button"
              className="grade grade--easy"
              disabled={busy}
              onClick={() => void grade('EASY')}
            >
              <Zap size={14} />
              <span className="grade__label">Easy</span>
              <kbd>4</kbd>
            </button>
          </div>

          <p className="muted" style={{ fontSize: 11, marginTop: 6 }}>
            <Sparkles size={11} style={{ verticalAlign: 'middle' }} />{' '}
            Use 1/2/3/4 keys for fast review.
          </p>
        </main>
      )}
    </div>
  );
}

// ── Card ──────────────────────────────────────────────────────────

/**
 * One concept card. The wire shape is intentionally lean — this is a
 * spaced-repetition surface, not a quiz-question surface — so we lean
 * on the concept name + a band indicator + a humanised "last seen" /
 * "due" pair to give the learner enough signal to grade.
 */
function ReviewCard({ item, subject }: { item: ReviewQueueItem; subject: string }) {
  const band     = bandFor(item.score);
  const overdue  = humanizeOverdue(item.overdueMillis);
  const lastSeen = item.lastSeenAt ? humanizeAgo(item.lastSeenAt) : 'never seen';
  return (
    <article className={'review-card review-card--' + band}>
      <header className="review-card__head">
        <span className="review-card__subject">{subject}</span>
        <span className={'review-card__band review-card__band--' + band}>{band}</span>
      </header>
      <h2 className="review-card__concept">{item.concept}</h2>
      <dl className="review-card__meta">
        <div>
          <dt>Last seen</dt><dd>{lastSeen}</dd>
        </div>
        <div>
          <dt>Status</dt><dd>{overdue}</dd>
        </div>
        <div>
          <dt>Score</dt><dd>{Math.round(item.score * 100)}%</dd>
        </div>
      </dl>
    </article>
  );
}

// ── helpers ──────────────────────────────────────────────────────

/** UI mastery bands — match ProgressPage thresholds. */
function bandFor(score: number): 'weak' | 'learning' | 'mastered' {
  if (score < 0.40) return 'weak';
  if (score < 0.80) return 'learning';
  return 'mastered';
}

/**
 * Humanise the overdue magnitude for the card meta.
 *   <= 0       → "due now"
 *   under day  → "Xh overdue"
 *   under wk   → "Xd overdue"
 *   else       → "X+ days overdue"
 *
 * The server clamps overdueMillis to >= 0 and treats never-seen rows as
 * MAX_VALUE — we render that as "fresh" so the empty-history case has
 * a sensible label.
 */
function humanizeOverdue(ms: number): string {
  if (ms === Number.MAX_VALUE || ms === Number.MAX_SAFE_INTEGER) return 'Fresh — never reviewed';
  if (ms <= 0) return 'Due now';
  const hours = ms / 3_600_000;
  if (hours < 1)  return 'Due now';
  if (hours < 24) return `${Math.round(hours)}h overdue`;
  const days = Math.floor(hours / 24);
  return `${days}d overdue`;
}

/** "n minutes/hours/days ago" — coarse enough for review meta. */
function humanizeAgo(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime();
  if (!Number.isFinite(ms) || ms < 0) return iso;
  const min = ms / 60_000;
  if (min < 1)   return 'just now';
  if (min < 60)  return `${Math.round(min)} min ago`;
  const hr = min / 60;
  if (hr  < 24)  return `${Math.round(hr)} hr ago`;
  const day = hr / 24;
  if (day < 30)  return `${Math.round(day)}d ago`;
  return new Date(iso).toLocaleDateString();
}
