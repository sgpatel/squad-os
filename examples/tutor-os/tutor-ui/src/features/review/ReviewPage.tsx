import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  RefreshCw, ArrowLeft, AlertCircle, CheckCircle2,
  Sparkles, X, ThumbsUp, Zap, Eye
} from 'lucide-react';
import { api } from '@/lib/api';
import { useAuth } from '@/store/auth';
import { Markdown } from '@/components/ui/Markdown';
import { masteryToast } from '@/components/ui/Toaster';
import { useReviewQueue } from './useReviewQueue';
import type { MasteryGrade, PracticeCardQuestion, ReviewQueueItem } from '@/lib/types';

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

  // M3-C card-question state. `card` is the materialised question for
  // the front-of-queue concept (null while loading or before fetch).
  // `revealed` toggles when the learner clicks "Show answer".
  const [card, setCard] = useState<PracticeCardQuestion | null>(null);
  const [cardLoading, setCardLoading] = useState(false);
  const [revealed, setRevealed] = useState(false);
  const userLevel = useAuth(s => s.currentUser()?.level ?? 'higher-sec');

  useEffect(() => { setQueue(items); setDoneCount(0); }, [items]);

  const current = queue[0] ?? null;

  // Fetch a question for the current card whenever the front of the
  // queue changes. Bypasses if subject/learnerId aren't ready yet —
  // the empty-state branches will render a friendly fallback.
  useEffect(() => {
    if (!current || !subject || !learnerId) {
      setCard(null);
      setRevealed(false);
      return;
    }
    let cancelled = false;
    setCard(null);
    setRevealed(false);
    setCardLoading(true);
    api.review.card(learnerId, subject, {
      concept: current.concept,
      level:   mapLevelToBackend(userLevel),
    }).then((q) => {
      if (!cancelled) setCard(q);
    }).catch((e) => {
      if (cancelled) return;
      // Stub a card so the learner can still self-grade against the
      // concept name when generation fails.
      setCard({
        question: `Recall what you know about: ${current.concept}`,
        answer:   `(Could not generate a question — ${e instanceof Error ? e.message : String(e)})`,
        conceptTag: current.concept,
      });
    }).finally(() => {
      if (!cancelled) setCardLoading(false);
    });
    return () => { cancelled = true; };
  }, [current?.concept, subject, learnerId, userLevel]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Grade handler ───────────────────────────────────────────────

  const grade = async (g: MasteryGrade) => {
    if (!current || !subject || busy) return;
    setBusy(true);
    setPostError(null);
    // Capture the pre-grade score so we can show a real delta in the
    // toast — the answer endpoint returns the AFTER score; the delta
    // is the diff against this snapshot.
    const beforePct = Math.round(current.score * 100);
    try {
      const updated = await api.review.answer(learnerId, subject, {
        concept: current.concept,
        grade:   g,
        // Source defaults to "review" server-side; we omit it here to
        // keep the request body minimal.
      });
      setQueue(q => q.slice(1));
      setDoneCount(n => n + 1);
      // Mastery announcement — sign-aware delta, current absolute
      // score. Skipped silently when the row is brand-new (the AGAIN
      // path on a never-seen concept can produce a 0→0 delta which
      // isn't worth a toast).
      const afterPct = Math.round((updated?.score ?? current.score) * 100);
      const delta = afterPct - beforePct;
      if (delta !== 0) {
        masteryToast({
          concept:  current.concept,
          deltaPct: delta,
          scorePct: afterPct,
        });
      }
    } catch (e) {
      setPostError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  // Keyboard:
  //   space    → Show answer (when hidden)
  //   1/2/3/4  → AGAIN / HARD / GOOD / EASY (Anki muscle memory)
  // Grading is gated until the answer has been revealed so the learner
  // can't accidentally mark themselves correct without seeing it.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (busy || !current) return;
      if (e.key === ' ' || e.key === 'Spacebar') {
        if (!revealed) { e.preventDefault(); setRevealed(true); }
        return;
      }
      if (!revealed) return;
      if (e.key === '1') void grade('AGAIN');
      else if (e.key === '2') void grade('HARD');
      else if (e.key === '3') void grade('GOOD');
      else if (e.key === '4') void grade('EASY');
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // grade closes over current+busy+revealed — re-bind when those change.
  }, [busy, current, revealed]); // eslint-disable-line react-hooks/exhaustive-deps

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
          <ReviewCard
            item={current!}
            subject={subject}
            card={card}
            cardLoading={cardLoading}
            revealed={revealed}
            onReveal={() => setRevealed(true)}
          />

          {postError && (
            <div className="review__error" role="alert">
              <AlertCircle size={14} /> {postError}
            </div>
          )}

          {/* Grade buttons gated on revealed — peeking would defeat
              the point of self-grading. Show-answer is the only
              affordance until the learner reveals. */}
          {revealed ? (
            <>
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
            </>
          ) : (
            <div className="review__reveal">
              <button
                type="button"
                className="btn btn--primary"
                onClick={() => setRevealed(true)}
                disabled={cardLoading}
              >
                <Eye size={14} /> Show answer
                <kbd style={{ marginLeft: 8 }}>Space</kbd>
              </button>
              <p className="muted" style={{ fontSize: 11, marginTop: 4 }}>
                Try to recall before peeking — that's the bit that builds memory.
              </p>
            </div>
          )}
        </main>
      )}
    </div>
  );
}

// ── Card ──────────────────────────────────────────────────────────

/**
 * Card front (always visible): subject + band + concept name + meta.
 *
 * M3-C extends this with a question body materialised by PracticeAgent.
 * The {@code revealed} flag toggles between two surfaces:
 *
 *   - hidden  → question + hint of an answer block, "Show answer" CTA below
 *   - revealed → question + model answer + worked solution (markdown)
 *
 * Self-grading lives in the parent so the four grade buttons can stay
 * adjacent to the answer block without prop-drilling state through here.
 */
function ReviewCard({
  item, subject, card, cardLoading, revealed, onReveal,
}: {
  item: ReviewQueueItem;
  subject: string;
  card: PracticeCardQuestion | null;
  cardLoading: boolean;
  revealed: boolean;
  onReveal: () => void;
}) {
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

      {/* Question / answer block (M3-C). Falls back to nothing while
          loading so the card stays the same height across reveal. */}
      {cardLoading && !card ? (
        <div className="review-card__question review-card__question--loading">
          <RefreshCw size={14} className="spin" />
          <span>Drafting a question for {item.concept}…</span>
        </div>
      ) : card ? (
        <section className="review-card__question">
          <h3 className="review-card__q-label">Question</h3>
          <Markdown>{card.question}</Markdown>
          {card.options && (
            <ul className="review-card__options">
              {card.options.split('|').map((opt, i) => (
                <li key={i}>{opt.trim()}</li>
              ))}
            </ul>
          )}
          {revealed && (
            <div className="review-card__answer">
              <h3 className="review-card__a-label">Answer</h3>
              <Markdown>{card.answer}</Markdown>
              {card.workedSolution && card.workedSolution !== card.answer && (
                <details className="review-card__worked">
                  <summary>Worked solution</summary>
                  <Markdown>{card.workedSolution}</Markdown>
                </details>
              )}
            </div>
          )}
          {!revealed && (
            <button
              type="button"
              className="review-card__hint-show"
              onClick={onReveal}
            >
              Tap to reveal answer
            </button>
          )}
        </section>
      ) : null}

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

/**
 * Translate the auth store's casual level vocabulary
 * ("primary" / "middle" / "higher-sec" / "college" / "pro") into the
 * backend's enum strings the LearnerProfile + PracticeAgent expect.
 * Mirrors {@code SyllabusSheet.mapLevelToBackend}; once we have a
 * shared mapping helper we'll dedupe.
 */
function mapLevelToBackend(level: string): string {
  switch (level) {
    case 'primary':    return 'PRIMARY';
    case 'middle':     return 'MIDDLE_SCHOOL';
    case 'higher-sec': return 'SENIOR_SCHOOL';
    case 'college':    return 'UNIVERSITY';
    case 'pro':        return 'PROFESSIONAL';
    default:           return 'SENIOR_SCHOOL';
  }
}

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
