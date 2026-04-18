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
  createdAt: number;
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
