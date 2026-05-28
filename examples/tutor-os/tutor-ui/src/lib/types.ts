/* ─────────────────────────────────────────────────────────────────
 * types.ts — the shared domain model.
 *
 * Every store, mock, and component in TutorOS speaks these types.
 * Hierarchy:
 *   Workspace ▶ Subject ▶ Course ▶ Chapter ▶ Topic ▶ Concept
 * Orthogonal:
 *   Note · Flashcard · Quiz · Source · MasteryRecord · PlanItem · Doubt
 * Live:
 *   PipelineRun ▶ PipelineStep · ChatMessage · DebateRound
 * ───────────────────────────────────────────────────────────────── */

// ── Identity ────────────────────────────────────────────────────────
export type LearnerTier = 'primary' | 'middle' | 'higher-sec' | 'college' | 'pro';
export type ThemeName  = 'lumen' | 'crayon' | 'atlas' | 'studio' | 'graphite';
export type ColorMode  = 'light' | 'dark' | null;
export type Density    = 'compact' | 'comfortable' | 'loose';

export interface User {
  id: string;
  name: string;
  initials: string;
  tier: LearnerTier;
  /** xp earned today */
  xpToday: number;
  /** xp goal for today */
  xpGoal: number;
  /** current daily-streak count */
  streak: number;
}

// ── Workspace & content hierarchy ───────────────────────────────────
export interface Workspace {
  id: string;
  name: string;          // "Class XI Biology", "JEE Main 2026", "Personal"
  description: string;
  /** Default theme proposed when this workspace is opened */
  defaultTheme: ThemeName;
  subjectIds: string[];
  /** Optional exam date the workspace is targeting */
  targetDate?: string;   // ISO date
}

export interface Subject {
  id: string;
  name: string;          // "Biology", "Calculus", "AWS Architect"
  /** Brand color hex used for subject card tint */
  color: string;
  icon: string;          // lucide icon name
  blurb: string;
  courseIds: string[];
}

export interface Course {
  id: string;
  subjectId: string;
  name: string;          // "Cell Biology — Class XI"
  description: string;
  /** ordered chapter list */
  chapterIds: string[];
  estMinutes: number;
}

export interface Chapter {
  id: string;
  courseId: string;
  index: number;         // ordering within course (1-based)
  name: string;
  blurb: string;
  estMinutes: number;
  topicIds: string[];
  /** mastery 0..1 */
  mastery: number;
}

export interface Topic {
  id: string;
  chapterId: string;
  name: string;
  /** body content as paragraphs (mock) */
  content: string[];
  conceptIds: string[];
}

/** A leaf "atom" the SRS engine tracks individually. */
export interface Concept {
  id: string;
  topicId: string;
  name: string;
  /** mastery 0..1 — the only thing the progress dashboard cares about */
  mastery: number;
  /** Last seen ISO timestamp */
  lastSeen?: string;
}

// ── Notes ───────────────────────────────────────────────────────────
export interface Note {
  id: string;
  title: string;
  /** Plaintext / lightweight markdown body */
  body: string;
  /** Anchored to a course/chapter when created from there */
  chapterId?: string;
  /** Subject the note belongs to — set when saved from a tutor message
      while a subject is active, or picked manually in the editor. */
  subjectId?: string;
  /** Optional back-reference to the tutor message the note was saved from. */
  sourceMessageId?: string;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

// ── Practice (spaced repetition) ────────────────────────────────────
export interface Flashcard {
  id: string;
  conceptId: string;
  q: string;
  a: string;
  /** SM-2-style ease factor */
  ease: number;
  /** review interval in days */
  intervalDays: number;
  /** ISO timestamp due */
  due: string;
}

// ── Quiz ────────────────────────────────────────────────────────────
export type QuestionKind = 'mcq' | 'short' | 'multi';

export interface QuizQuestion {
  id: string;
  kind: QuestionKind;
  prompt: string;
  /** for mcq/multi: choices  */
  choices?: string[];
  /** index(es) of correct choice(s) for mcq/multi */
  correct?: number[];
  /** ideal answer for short */
  ideal?: string;
  /** which concept this exercises */
  conceptId: string;
  explanation: string;
}

export interface Quiz {
  id: string;
  title: string;
  questionIds: string[];
  /** estimated minutes to complete */
  estMinutes: number;
}

// ── Library / Sources (RAG citations) ───────────────────────────────
export type SourceKind = 'pdf' | 'video' | 'web' | 'book';

export interface Source {
  id: string;
  kind: SourceKind;
  title: string;
  author?: string;
  url?: string;
  /** Pages cited or video timestamps */
  cited: string[];
  /** Self-rated provenance score 0..1 */
  trust: number;
}

// ── Plan ────────────────────────────────────────────────────────────
export type PlanItemKind = 'study' | 'practice' | 'quiz' | 'review' | 'break';

export interface PlanItem {
  id: string;
  date: string;          // ISO date
  startTime?: string;    // "09:30"
  durationMin: number;
  kind: PlanItemKind;
  title: string;
  /** linked content if any */
  chapterId?: string;
  quizId?: string;
  done: boolean;
}

// ── Community / Doubts ──────────────────────────────────────────────
export interface DoubtAnswer {
  id: string;
  authorName: string;
  authorRole: 'tutor' | 'peer' | 'ai';
  body: string;
  upvotes: number;
  accepted: boolean;
  createdAt: string;
}
export interface Doubt {
  id: string;
  title: string;
  body: string;
  conceptId?: string;
  tags: string[];
  authorName: string;
  createdAt: string;
  answers: DoubtAnswer[];
}

// ── Live: pipeline + chat ───────────────────────────────────────────
export type PipelineStageKey =
  | 'guardian' | 'diagnostic' | 'planner' | 'content'
  | 'debate'   | 'tutor'      | 'practice' | 'assessment' | 'progress';

export interface PipelineStageDef {
  key: PipelineStageKey;
  label: string;
  detail: string;
}

export type StepState = 'pending' | 'running' | 'done' | 'error';

export interface PipelineStep {
  key: PipelineStageKey;
  label: string;
  detail: string;
  state: StepState;
  /** ms taken when state == done */
  ms?: number;
  /** Optional structured output (e.g., debate rounds for the debate stage) */
  output?: unknown;
}

export interface PipelineRun {
  id: string;
  prompt: string;
  startedAt: number;     // performance.now() ms
  finishedAt?: number;
  steps: PipelineStep[];
  /** index of currently-running step, -1 if none/done */
  active: number;
}

// ── Chat ────────────────────────────────────────────────────────────
export type ChatRole = 'learner' | 'tutor';

export interface CitationRef {
  sourceId: string;
  /** Display label like "p. 314" or "0:45" */
  locator: string;
}

export interface ChatMessage {
  id: string;
  role: ChatRole;
  body: string;          // markdown-ish text (we render simply)
  /** confidence 0..1 (tutor only) */
  confidence?: number;
  citations?: CitationRef[];
  /** the pipeline run that produced this message */
  pipelineRunId?: string;
  /** debate produced for this message */
  debate?: DebateRound;
  /** A diagram emitted by VisualisationAgent (gpai-style domain spec). */
  visualAsset?: VisualAsset;
  /**
   * Teaching style this turn used. Free-form for normal turns
   * ("DIRECT" / "SOCRATIC" / etc.), but the special value "CLARIFY"
   * signals that the message is the disambiguation gate asking the
   * learner about subject/topic — the UI renders it differently
   * (ClarificationCard with quick replies) instead of plain markdown.
   */
  teachingStyle?: string;
  /**
   * Clarification kind, set by the backend when teachingStyle === "CLARIFY".
   * Today only "subject" is emitted (subject/topic gate). Future kinds
   * (level, syllabus-confirm, …) extend this discriminator.
   */
  clarification?: string;
  createdAt: number;
}

// ── BookCoach (PR-1/2/3) ────────────────────────────────────────────

/**
 * One chapter as returned by /api/books/{learnerId}/{bookId}/chapter/{n}.
 * Mirrors backend {@code io.tutoros.book.Chapter}.
 */
export interface BookChapter {
  number: number;
  title: string;
  summary: string;
  pageStart: number;
  pageEnd: number;
  /** Capped at MAX_BODY_CHARS (6000) on the backend. */
  body: string;
  concepts: string[];
}

/**
 * Compact chapter view used by list + detail endpoints (no body).
 * Mirrors backend {@code BookController.ChapterSummary}.
 */
export interface BookChapterSummary {
  number: number;
  title: string;
  summary: string;
  pageStart: number;
  pageEnd: number;
  concepts: string[];
}

/**
 * Book summary returned by /upload, /list, /detail. Chapter bodies
 * are NOT included here — fetch /chapter/{n} when the learner opens
 * a specific chapter (PR-1 design: keeps library payloads small).
 *
 * Mirrors backend {@code BookController.BookSummary}.
 */
export interface BookSummary {
  id: string;
  learnerId: string;
  subject: string;
  title: string;
  author: string;
  totalPages: number;
  /** ISO-8601 instant. */
  uploadedAt: string | null;
  chapters: BookChapterSummary[];
}

/**
 * The six BookCoach lenses. Quiz lenses (basic/intermediate/advanced)
 * carry a question + modelAnswer; context lenses (usage/history/future)
 * are markdown explanations with no quiz.
 */
export type BookCoachMode =
  | 'basic'
  | 'intermediate'
  | 'advanced'
  | 'usage'
  | 'history'
  | 'future';

/**
 * Structured insight returned by POST /chapter/{n}/ask.
 * Mirrors backend {@code io.tutoros.model.LearningInsight}.
 */
export interface LearningInsight {
  mode: BookCoachMode | string;
  concept: string;
  body: string;
  /** Populated only on quiz lenses. */
  question?: string;
  modelAnswer?: string;
  difficulty?: string;
  /** Newline-separated follow-up prompts. */
  followUps?: string;
  /** "pp. 42–47" or empty for history/future. */
  sourcePages?: string;
}

// ── Practice / review card question (mirrors backend PracticeQuestion) ─

/**
 * Wire shape for backend PracticeQuestion. Used by both the review
 * card materialisation path (POST /api/review/.../card) and any
 * future practice-page integration. Only the fields the review UI
 * actually renders are typed required; everything else is optional.
 */
export interface PracticeCardQuestion {
  question: string;
  type?: string;
  options?: string;
  answer: string;
  bloomsLevel?: string;
  difficulty?: string;
  conceptTag?: string;
  hints?: string;
  workedSolution?: string;
  diagramDescription?: string;
  marks?: number;
}

// ── Review queue (M3-B) ─────────────────────────────────────────────

/**
 * Anki-style 4-grade scale the backend's MasteryService uses.
 *   AGAIN  — wrong, or right with significant effort/hints
 *   HARD   — right but the learner struggled
 *   GOOD   — right with normal effort (default for "got it")
 *   EASY   — right effortlessly (longer interval + ease bump)
 */
export type MasteryGrade = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY';

/**
 * Compact concept-mastery row returned by GET /api/review/queue.
 * Mirrors {@code ReviewController.QueueItem} exactly — fields kept
 * minimal so the wire payload stays small even on long subjects.
 */
export interface ReviewQueueItem {
  concept: string;
  /** 0..1 smoothed mastery score (UI bands: <.40 weak / .40-.80 learning / >=.80 mastered). */
  score: number;
  /** SM-2 interval in days that produced the current schedule. */
  intervalDays: number;
  lastSeenAt: string | null;     // ISO-8601 instant or null
  nextReviewAt: string | null;
  /** Milliseconds past nextReviewAt as of the server's response. Sort key. */
  overdueMillis: number;
}

/**
 * Wire payload for POST /api/review/{learnerId}/{subject}/answer.
 */
export interface ReviewAnswer {
  concept: string;
  grade: MasteryGrade;
  /** Defaults to "review" on the server when omitted. */
  source?: string;
}

/**
 * Returned by POST /answer — the FULL ConceptMastery row after the
 * SM-2 step. Fields beyond ReviewQueueItem are read by the UI for
 * the "next due" pill that pops up on the card after grading.
 */
export interface ConceptMasteryRow {
  learnerId: string;
  subject: string;
  concept: string;
  score: number;
  ease: number;
  intervalDays: number;
  reps: number;
  lastSeenAt: string | null;
  nextReviewAt: string | null;
  /** Bounded history (newest at the end) — exposed for charts later. */
  history?: Array<{
    at: string;
    grade: MasteryGrade;
    score: number;
    source: string;
  }>;
}

/**
 * Syllabus — wire shape mirroring the backend Syllabus model.
 *
 * Lives client-side so the SyllabusSheet can edit it before /save and the
 * workspace store can cache the active one. The {@code source} field is
 * one of:
 *   - SUGGESTED  → emitted by SyllabusSuggesterAgent
 *   - CUSTOM     → pasted by the learner
 *   - LMS        → fetched from school LMS (server-only today)
 */
export interface Syllabus {
  subject: string;
  topic: string;
  level: string;
  /** Newline-separated chapter list with "- " bullets (see backend model) */
  chapters: string;
  rationale?: string;
  source: 'SUGGESTED' | 'CUSTOM' | 'LMS' | string;
}

/**
 * Wire shape for VisualisationAgent output.
 *
 * The agent picks a `type` and emits a tiny `specJson` for a deterministic
 * renderer (SmilesDrawer for chem, Vega-Lite for plot, …). The frontend
 * `<Diagram />` component routes by `type` and parses `specJson` once.
 */
export interface VisualAsset {
  type: 'chem' | 'plot' | 'geometry' | 'freebody' | 'flow' | 'circuit';
  concept: string;
  title: string;
  caption?: string;
  /** JSON spec serialised as a string — parsed by the renderer. */
  specJson: string;
  altText: string;
}

// ── Debate Round (signature view) ───────────────────────────────────
export interface DebateRound {
  topic: string;
  rounds: DebateExchange[];
  verdict: string;
  verdictConfidence: number;
}
export interface DebateExchange {
  forClaim: string;
  forSource: string;
  againstClaim: string;
  againstSource: string;
}
