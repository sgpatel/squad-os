import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, AlertCircle, RefreshCw, Eye, X, ThumbsUp, Zap,
  BookOpen, Lightbulb, Hammer, Brain, Activity, Clock, Compass,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { api, DEMO_LEARNER_ID } from '@/lib/api';
import { useAuth } from '@/store/auth';
import { Markdown } from '@/components/ui/Markdown';
import { masteryToast } from '@/components/ui/Toaster';
import type { BookChapter, BookCoachMode, BookSummary, LearningInsight } from '@/lib/types';

/**
 * `<ChapterCoachPage />` — six-lens learning panel for one chapter.
 *
 * Tabs across the top map 1:1 to {@link BookCoachMode}. Each tab
 * fetches a fresh {@link LearningInsight} from
 * {@code POST /api/books/.../chapter/{n}/ask?mode=...} so the learner
 * always sees the freshest take (the agent reruns prompts on every
 * call rather than caching — costs are bounded by chapter body size).
 *
 * Quiz lenses (basic / intermediate / advanced) render the question +
 * show-answer flow + 4-button SM-2 grade, mirroring {@code ReviewPage}
 * exactly so the muscle memory carries over. Grade buttons hit
 * {@code POST /api/books/.../chapter/{n}/answer}, which routes the
 * outcome through {@code MasteryService.recordOutcome(source="book")}
 * — same write path as quiz + review, so the M3-B "+12% concept"
 * toast fires identically.
 *
 * Context lenses (usage / history / future) render the body as
 * markdown with the follow-ups list in a collapsible section.
 *
 * Concept selector lives in the header — chapters with multiple
 * extracted concepts let the learner re-anchor each tab on the
 * concept they want to study.
 */
export function ChapterCoachPage() {
  const { bookId, n: nParam } = useParams<{ bookId: string; n: string }>();
  const navigate              = useNavigate();
  const learnerId             = useAuth(s => s.currentUser()?.id ?? DEMO_LEARNER_ID);
  const userLevel             = useAuth(s => s.currentUser()?.level ?? 'higher-sec');

  const n = parseInt(nParam ?? '1', 10) || 1;

  const [book,      setBook]      = useState<BookSummary | null>(null);
  const [chapter,   setChapter]   = useState<BookChapter | null>(null);
  const [bootError, setBootError] = useState<string | null>(null);
  const [bootLoading, setBootLoading] = useState(true);

  // Active lens + active concept tag (when the chapter has many,
  // the learner can flip between them to drill into specific ones).
  const [mode,    setMode]    = useState<BookCoachMode>('basic');
  const [concept, setConcept] = useState<string>('');

  // Per-lens fetch state. Keyed by mode so flipping tabs doesn't
  // discard in-flight data for the previous one (helps the learner
  // skim multiple lenses without losing earlier results).
  const [insight, setInsight] = useState<LearningInsight | null>(null);
  const [insightLoading, setInsightLoading] = useState(false);
  const [insightError, setInsightError]     = useState<string | null>(null);
  const [revealed, setRevealed]             = useState(false);

  const [grading, setGrading] = useState(false);

  // ── Boot: load book + chapter ─────────────────────────────────────

  useEffect(() => {
    if (!bookId) return;
    setBootLoading(true);
    Promise.all([
      api.books.get(learnerId, bookId),
      api.books.chapter(learnerId, bookId, n),
    ]).then(([b, ch]) => {
      setBook(b);
      setChapter(ch);
      // Anchor on the chapter's first concept by default; falls back
      // to the chapter title when no concepts were extracted.
      setConcept(ch.concepts[0] ?? ch.title);
      setBootError(null);
    }).catch((e) => {
      setBootError(e instanceof Error ? e.message : String(e));
    }).finally(() => setBootLoading(false));
  }, [bookId, learnerId, n]);

  // ── Per-mode insight fetch ────────────────────────────────────────

  const fetchInsight = useMemo(
    () => async (m: BookCoachMode, c: string) => {
      if (!bookId || !c) return;
      setInsightLoading(true);
      setInsightError(null);
      setInsight(null);
      setRevealed(false);
      try {
        const out = await api.books.ask(learnerId, bookId, n, {
          mode:    m,
          concept: c,
          level:   mapLevelToBackend(userLevel),
        });
        setInsight(out);
      } catch (e) {
        setInsightError(e instanceof Error ? e.message : String(e));
      } finally {
        setInsightLoading(false);
      }
    },
    [bookId, learnerId, n, userLevel]
  );

  // Re-fetch whenever the lens or concept changes (and once the
  // chapter has loaded so we have a real concept to pin to).
  useEffect(() => {
    if (!chapter || !concept) return;
    void fetchInsight(mode, concept);
  }, [mode, concept, chapter, fetchInsight]);

  // ── Self-grade (quiz lenses only) ─────────────────────────────────

  const isQuizLens = mode === 'basic' || mode === 'intermediate' || mode === 'advanced';

  const grade = async (g: 'AGAIN' | 'HARD' | 'GOOD' | 'EASY') => {
    if (!bookId || !insight || !insight.concept || grading) return;
    setGrading(true);
    try {
      await api.books.answer(learnerId, bookId, n, {
        concept: insight.concept,
        grade:   g,
      });
      // Sign-aware delta — bookkeeping for the toast. The exact
      // numbers mirror the backend's MasteryService.scoreDelta:
      //   GOOD +10, EASY +15, HARD +3, AGAIN -10.
      const delta = g === 'EASY' ? 15
                  : g === 'GOOD' ? 10
                  : g === 'HARD' ?  3
                  :                -10;
      masteryToast({ concept: insight.concept, deltaPct: delta });
      // Move to the next lens automatically so the loop feels alive.
      const next: BookCoachMode | null =
        mode === 'basic'         ? 'intermediate'
        : mode === 'intermediate' ? 'advanced'
        : null;
      if (next) setMode(next);
    } catch (e) {
      setInsightError(e instanceof Error ? e.message : String(e));
    } finally {
      setGrading(false);
    }
  };

  // ── Render ─────────────────────────────────────────────────────────

  if (bootLoading) {
    return (
      <div className="coach"><div className="review__empty"><RefreshCw size={28} className="spin" /><p>Loading chapter…</p></div></div>
    );
  }
  if (bootError || !book || !chapter) {
    return (
      <div className="coach">
        <header className="coach__head">
          <button className="btn btn--ghost btn--sm" onClick={() => navigate(`/books/${bookId ?? ''}`)}>
            <ArrowLeft size={13} /> Back to chapters
          </button>
        </header>
        <div className="review__empty">
          <AlertCircle size={28} />
          <p>{bootError || 'Chapter not found.'}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="coach">
      <header className="coach__head">
        <button
          className="btn btn--ghost btn--sm"
          onClick={() => navigate(`/books/${book.id}`)}
        >
          <ArrowLeft size={13} /> {book.title}
        </button>
        <div className="coach__title-block">
          <h1 className="coach__title">
            <BookOpen size={16} /> {chapter.title}
          </h1>
          <p className="muted" style={{ fontSize: 12 }}>
            pp. {chapter.pageStart}–{chapter.pageEnd} · {book.subject}
          </p>
        </div>
      </header>

      {/* Concept selector — only when the chapter has more than one. */}
      {chapter.concepts.length > 1 && (
        <div className="coach__concepts">
          <span className="muted" style={{ fontSize: 11 }}>Anchored on:</span>
          {chapter.concepts.map(c => (
            <button
              key={c}
              type="button"
              className={'concept-chip' + (c === concept ? ' concept-chip--on' : '')}
              onClick={() => setConcept(c)}
            >
              {c}
            </button>
          ))}
        </div>
      )}

      {/* Lens tabs */}
      <nav className="lens-tabs" role="tablist" aria-label="Learning lens">
        <LensTab id="basic"        label="Basic"        icon={Lightbulb} on={mode === 'basic'}        onClick={() => setMode('basic')} />
        <LensTab id="intermediate" label="Intermediate" icon={Hammer}    on={mode === 'intermediate'} onClick={() => setMode('intermediate')} />
        <LensTab id="advanced"     label="Advanced"     icon={Brain}     on={mode === 'advanced'}     onClick={() => setMode('advanced')} />
        <LensTab id="usage"        label="Usage"        icon={Activity}  on={mode === 'usage'}        onClick={() => setMode('usage')} />
        <LensTab id="history"      label="History"      icon={Clock}     on={mode === 'history'}      onClick={() => setMode('history')} />
        <LensTab id="future"       label="Future"       icon={Compass}   on={mode === 'future'}       onClick={() => setMode('future')} />
      </nav>

      <main className="lens-panel" role="tabpanel" aria-labelledby={mode}>
        {insightLoading ? (
          <div className="review__empty" style={{ padding: 'var(--space-5) 0' }}>
            <RefreshCw size={20} className="spin" />
            <p style={{ fontSize: 13 }}>Drafting the {mode} insight…</p>
          </div>
        ) : insightError ? (
          <div className="review__error" role="alert">
            <AlertCircle size={14} /> {insightError}
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => void fetchInsight(mode, concept)}
            >
              <RefreshCw size={12} /> Retry
            </button>
          </div>
        ) : insight ? (
          <>
            {/* Concept + difficulty header (when present) */}
            <div className="lens-panel__head">
              <span className="lens-panel__concept">{insight.concept}</span>
              {insight.difficulty && (
                <span className={'lens-panel__diff lens-panel__diff--' + insight.difficulty.toLowerCase()}>
                  {insight.difficulty}
                </span>
              )}
              {insight.sourcePages && (
                <span className="muted" style={{ fontSize: 11 }}>{insight.sourcePages}</span>
              )}
            </div>

            {/* Body */}
            <div className="lens-panel__body">
              <Markdown>{insight.body}</Markdown>
            </div>

            {/* Quiz lens: question + show-answer + grade */}
            {isQuizLens && insight.question ? (
              <section className="lens-panel__quiz">
                <h3 className="lens-panel__q-label">Question</h3>
                <div className="lens-panel__question">
                  <Markdown>{insight.question}</Markdown>
                </div>

                {revealed && insight.modelAnswer && (
                  <div className="lens-panel__answer">
                    <h3 className="lens-panel__a-label">Model answer</h3>
                    <Markdown>{insight.modelAnswer}</Markdown>
                  </div>
                )}

                {!revealed ? (
                  <div className="lens-panel__reveal">
                    <button
                      type="button"
                      className="btn btn--primary"
                      onClick={() => setRevealed(true)}
                    >
                      <Eye size={14} /> Show answer
                    </button>
                    <p className="muted" style={{ fontSize: 11, marginTop: 4 }}>
                      Try to recall before peeking — that's the bit that builds memory.
                    </p>
                  </div>
                ) : (
                  <div className="review__grades" role="group" aria-label="How well did you know this?">
                    <button type="button" className="grade grade--again" disabled={grading} onClick={() => void grade('AGAIN')}>
                      <X size={14} /><span className="grade__label">Again</span>
                    </button>
                    <button type="button" className="grade grade--hard" disabled={grading} onClick={() => void grade('HARD')}>
                      <ThumbsUp size={14} style={{ transform: 'rotate(-25deg)' }} /><span className="grade__label">Hard</span>
                    </button>
                    <button type="button" className="grade grade--good" disabled={grading} onClick={() => void grade('GOOD')}>
                      <ThumbsUp size={14} /><span className="grade__label">Good</span>
                    </button>
                    <button type="button" className="grade grade--easy" disabled={grading} onClick={() => void grade('EASY')}>
                      <Zap size={14} /><span className="grade__label">Easy</span>
                    </button>
                  </div>
                )}
              </section>
            ) : null}

            {/* Follow-ups (collapsed by default — keeps the panel tight) */}
            {insight.followUps && insight.followUps.trim() && (
              <details className="lens-panel__followups">
                <summary>Go deeper</summary>
                <ul>
                  {insight.followUps.split(/\r?\n/).filter(s => s.trim()).map((q, i) => (
                    <li key={i}>{q}</li>
                  ))}
                </ul>
              </details>
            )}
          </>
        ) : null}
      </main>
    </div>
  );
}

// ── Tab button ────────────────────────────────────────────────────

function LensTab(props: {
  id: string;
  label: string;
  icon: LucideIcon;
  on: boolean;
  onClick: () => void;
}) {
  const { label, icon: Icon, on, onClick } = props;
  return (
    <button
      type="button"
      role="tab"
      aria-selected={on}
      className={'lens-tab' + (on ? ' lens-tab--on' : '')}
      onClick={onClick}
    >
      <Icon size={13} />
      <span>{label}</span>
    </button>
  );
}

// ── helpers ──────────────────────────────────────────────────────

/**
 * Same translation as ReviewPage + SyllabusSheet — kept inline here
 * rather than centralised so the books feature can be deleted as a
 * unit without touching shared utilities. Worth deduping once we
 * have a third caller.
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
