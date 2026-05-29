import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, AlertCircle, RefreshCw, Eye, X, ThumbsUp, Zap,
  BookOpen, Lightbulb, Hammer, Brain, Activity, Clock, Compass,
  Quote, ChevronDown, ChevronUp, ExternalLink, MessageSquare,
  StickyNote, Sparkles as SparkleIcon,
} from 'lucide-react';
import { useNotes } from '@/store/notes';
import { usePipeline } from '@/store/pipeline';
import { stashReturnTo } from '@/lib/returnTo';
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

            {/* STEP 1 — read the source.
                Pedagogical anchor: the learner sees Murphy's actual
                words first, BEFORE any generated summary. Closes the
                gap where the previous flow jumped straight to a quiz
                without ever surfacing the source material. */}
            {insight.sourceExcerpt && insight.sourceExcerpt.trim() && (
              <CoachSourcePanel
                excerpt={insight.sourceExcerpt}
                bookTitle={book.title}
                chapterTitle={chapter.title}
                sourcePages={insight.sourcePages || `pp. ${chapter.pageStart}–${chapter.pageEnd}`}
                pdfUrl={api.books.pdfUrl(learnerId, book.id, {
                  page:      chapter.pageStart,
                  highlight: insight.concept,
                })}
              />
            )}

            {/* STEP 2 — tutor's take.
                Now framed as a SUMMARY/take on the excerpt above, not
                a standalone definition. */}
            <div className="lens-panel__body">
              <div className="lens-panel__body-label">
                {isQuizLens ? "Tutor's take" : 'Tutor view'}
              </div>
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

            {/* STEP 4 — momentum.
                Lens-aware recommendations so the screen exit always
                has a 1-click next move. See RecommendedActions
                comment for the pedagogical rationale. */}
            <RecommendedActions
              mode={mode}
              concept={insight.concept}
              bookId={book.id}
              chapterNumber={chapter.number}
              bookTitle={book.title}
              chapterTitle={chapter.title}
              sourceExcerpt={insight.sourceExcerpt}
              sourcePages={insight.sourcePages}
              pdfUrl={api.books.pdfUrl(learnerId, book.id, {
                page:      chapter.pageStart,
                highlight: insight.concept,
              })}
              onSwitchMode={setMode}
            />
          </>
        ) : null}
      </main>
    </div>
  );
}

// ── "From the book" source panel ──────────────────────────────────

/**
 * `<CoachSourcePanel />` — the "read the source first" anchor at the
 * top of the lens panel.
 *
 * <p>Renders the focused excerpt the backend extracted around the
 * active concept, framed as a quoted passage with book + chapter
 * citation. Collapsible: short excerpts (≤600 chars) show in full;
 * longer ones get a "Read more" toggle so the panel doesn't dominate
 * the page.
 *
 * <p>Why this matters pedagogically: humans don't learn from
 * generated summaries; they learn from primary text + reflection.
 * This panel is the primary text. The generated body below is the
 * reflection.
 */
function CoachSourcePanel(props: {
  excerpt:      string;
  bookTitle:    string;
  chapterTitle: string;
  sourcePages:  string;
  /** When provided, an "Open PDF" button jumps to this URL in a new tab. */
  pdfUrl?:      string;
}) {
  const { excerpt, bookTitle, chapterTitle, sourcePages, pdfUrl } = props;
  const SHORT_LIMIT = 600;
  const [expanded, setExpanded] = useState(false);
  const isLong  = excerpt.length > SHORT_LIMIT;
  const visible = !isLong || expanded
    ? excerpt
    : excerpt.slice(0, SHORT_LIMIT).trim() + '…';

  return (
    <section className="coach-source" aria-label="Excerpt from the book">
      <header className="coach-source__head">
        <Quote size={14} aria-hidden />
        <span className="coach-source__label">From the book</span>
        <span className="coach-source__cite">
          {bookTitle} · {chapterTitle} · {sourcePages}
        </span>
        {pdfUrl && (
          <a
            href={pdfUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="coach-source__open-pdf"
            title="Open the original PDF at this page"
          >
            <ExternalLink size={12} /> Open PDF
          </a>
        )}
      </header>
      <blockquote className="coach-source__body">
        <Markdown>{visible}</Markdown>
      </blockquote>
      {isLong && (
        <button
          type="button"
          className="coach-source__toggle"
          onClick={() => setExpanded(v => !v)}
        >
          {expanded ? (<><ChevronUp size={12} /> Show less</>) :
                      (<><ChevronDown size={12} /> Read more</>)}
        </button>
      )}
    </section>
  );
}

// ── Recommended next actions ─────────────────────────────────────

/**
 * `<RecommendedActions />` — small horizontal strip below the lens
 * content suggesting where to go next. Three to four lens-aware
 * affordances:
 *
 *   • Try {next lens}      — quiz lens → next quiz lens; context
 *                             lens → first quiz lens to test recall
 *   • Open PDF at page N    — same target as the panel header
 *                             button, repeated here so the strip is
 *                             self-contained at the bottom of a
 *                             long page
 *   • Ask the tutor         — pipes the excerpt + concept into the
 *                             main /tutor chat as a starting prompt
 *   • Save as note          — calls the existing notes store so the
 *                             insight is captured for later study
 *
 * Pedagogical role: closes the learning loop. Without it, the learner
 * finishes a lens with nowhere obvious to go. With it, every screen
 * exit has 1-click momentum into the next study action.
 */
function RecommendedActions(props: {
  mode:         BookCoachMode;
  concept:      string;
  bookId:       string;
  chapterNumber: number;
  bookTitle:    string;
  chapterTitle: string;
  sourceExcerpt?: string;
  sourcePages?: string;
  pdfUrl?:      string;
  onSwitchMode: (next: BookCoachMode) => void;
}) {
  const { mode, concept, bookId, chapterNumber, bookTitle, chapterTitle,
          sourceExcerpt, pdfUrl, onSwitchMode } = props;
  const navigate     = useNavigate();
  const createNote   = useNotes(s => s.create);
  const startTutor   = usePipeline(s => s.start);
  const [savedNote, setSavedNote] = useState(false);

  const nextMode = NEXT_LENS[mode];

  const askTutor = () => {
    // Prefill the main tutor pipeline with the concept + excerpt as
    // context. The chat will then pick up where the lens left off —
    // perfect for "go deeper than the lens allowed" follow-ups.
    const prompt =
      `Help me understand "${concept}" from ${bookTitle} (${chapterTitle}). ` +
      `Here is the relevant passage I'm reading:\n\n` +
      (sourceExcerpt ? '> ' + sourceExcerpt.split('\n').join('\n> ') : '(no excerpt)') +
      `\n\nWhat are the most important things to take away?`;
    // Remember where we came from so the tutor page can render a
    // "← Back to <chapter>" breadcrumb. Without this the learner
    // would have to manually navigate back to /books → book → chapter
    // → lens after every cross-context jump. Survives page reload
    // via sessionStorage; expires after 30 min.
    stashReturnTo({
      url:    `/books/${bookId}/chapter/${chapterNumber}`,
      label:  `${chapterTitle} · ${bookTitle}`,
      source: 'book',
    });
    void startTutor(prompt);
    navigate('/tutor');
  };

  const saveAsNote = () => {
    if (savedNote) return;
    const title = `${concept} — ${chapterTitle}`;
    const body =
      `**From ${bookTitle} · ${chapterTitle}**\n\n` +
      (sourceExcerpt ? '> ' + sourceExcerpt.split('\n').join('\n> ') + '\n\n' : '') +
      `**Concept:** ${concept}`;
    createNote({ title, body });
    setSavedNote(true);
    window.setTimeout(() => setSavedNote(false), 2000);
  };

  return (
    <section className="recommended" aria-label="Recommended next actions">
      <div className="recommended__head">
        <SparkleIcon size={12} aria-hidden />
        <span>What to do next</span>
      </div>
      <div className="recommended__actions">
        {nextMode && (
          <button
            type="button"
            className="recommended__btn"
            onClick={() => onSwitchMode(nextMode)}
            title={`Move to the ${nextMode} lens`}
          >
            <Zap size={12} /> Try {capitalize(nextMode)}
          </button>
        )}
        {pdfUrl && (
          <a
            href={pdfUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="recommended__btn"
            title="Open the original PDF in a new tab"
          >
            <ExternalLink size={12} /> Read in PDF
          </a>
        )}
        <button
          type="button"
          className="recommended__btn"
          onClick={askTutor}
          title="Ask the main tutor about this passage"
        >
          <MessageSquare size={12} /> Ask the tutor
        </button>
        <button
          type="button"
          className={'recommended__btn' + (savedNote ? ' recommended__btn--ok' : '')}
          onClick={saveAsNote}
          title="Save this insight as a note for later review"
        >
          <StickyNote size={12} /> {savedNote ? 'Saved ✓' : 'Save as note'}
        </button>
      </div>
    </section>
  );
}

/**
 * Lens-progression map for the "Try {next}" button. Quiz lenses move
 * to the next harder lens; context lenses funnel back to Basic so
 * the learner closes the loop with a recall check.
 */
const NEXT_LENS: Record<BookCoachMode, BookCoachMode | null> = {
  basic:        'intermediate',
  intermediate: 'advanced',
  advanced:     'usage',
  usage:        'history',
  history:      'future',
  future:       'basic',
};

function capitalize(s: string): string {
  return s.length === 0 ? s : s[0]!.toUpperCase() + s.slice(1);
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
