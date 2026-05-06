/// <reference types="vite/client" />
/* ─────────────────────────────────────────────────────────────────
 * api.ts — typed client for tutor-api (REST) + WebSocket stream.
 *
 * All shapes mirror the Java records in:
 *   tutor-api/src/main/java/io/tutoros/api/SessionController.java
 *   tutor-api/src/main/java/io/tutoros/websocket/TutoringWebSocketHandler.java
 *
 * The UI never imports this module directly — the pipeline store does.
 * Components stay declarative and do not know whether they're driven
 * by mock data or a live backend.
 * ───────────────────────────────────────────────────────────────── */

import type {
  PipelineEvent,
} from './pipeline';
import type {
  ChatMessage, ConceptMasteryRow, DebateRound, PipelineRun, PipelineStep,
  PipelineStageKey, PracticeCardQuestion, ReviewAnswer, ReviewQueueItem,
  Syllabus, VisualAsset
} from './types';
import { PIPELINE_STAGES } from './mockData';
import type { PipelineStageDef } from './types';

// ── Env / feature flag ──────────────────────────────────────────────

/**
 * Two env knobs, deliberately separate:
 *
 *   VITE_API_BASE_URL   — absolute origin of the backend ('http://host:8080'),
 *                         used for direct cross-origin calls in prod.
 *   VITE_LIVE_BACKEND   — 'true' to force the live path without supplying an
 *                         origin, so dev can hit the Vite proxy (same-origin,
 *                         no CORS). Ignored when VITE_API_BASE_URL is set.
 *
 * Keeping them separate means you can develop against the real backend
 * without chasing CORS: leave the origin blank, set VITE_LIVE_BACKEND=true,
 * and `fetch('/session/start')` is proxied by Vite to tutor-api.
 */
const RAW_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').trim();
const LIVE_FLAG = String(import.meta.env.VITE_LIVE_BACKEND ?? '').trim().toLowerCase();

/** True iff we should hit the real tutor-api. Otherwise the mock pipeline runs. */
export const LIVE_BACKEND: boolean =
  RAW_BASE.length > 0 || LIVE_FLAG === 'true' || LIVE_FLAG === '1';

/** REST base — '' means "use same-origin / Vite proxy". */
const HTTP_BASE: string = RAW_BASE.replace(/\/+$/, '');

/** WS base — derived from HTTP base, or window.location for same-origin proxy. */
function wsBase(): string {
  if (HTTP_BASE) return HTTP_BASE.replace(/^http/, 'ws');
  if (typeof window === 'undefined') return 'ws://localhost:8080';
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${window.location.host}`;
}

// ── REST request/response types (mirrored from Java records) ────────

export interface StartSessionRequest {
  learnerId: string;
  name: string;
  level: string;
  profession?: string;
  subjects: string[];
  topic?: string;
  goal: string;
  analogyDomain?: string;
  sessionsPerWeek: number;
}

export interface LearnerProfileDTO {
  name: string;
  level: string;
  profession: string;
  subject: string;
  topic: string;
  goal: string;
  learningStyle: string;
  bloomsLevel: string;
  analogyDomain: string;
  preferredTeachingStyle: string;
  sessionsPerWeek: number;
  avgSessionMinutes: number;
}

export interface StartSessionResponse {
  sessionId: string;
  profile: LearnerProfileDTO;
  firstMessage: string;
  currentChapter: string;
  masteryPct: number;
}

export type MessageResponseType =
  | 'TUTOR' | 'SAFE_REFUSAL' | 'BLOCKED' | 'DIAGNOSTIC'
  | 'PLAN'  | 'ESCALATION'   | 'FEEDBACK';

export interface MessageResponse {
  type: MessageResponseType;
  text: string | null;
  teachingStyle: string | null;
  visualQueued: boolean;
  payload: unknown;
}

// ── HTTP plumbing ───────────────────────────────────────────────────

export class ApiError extends Error {
  status: number;
  body: unknown;
  constructor(status: number, body: unknown, msg?: string) {
    super(msg ?? `HTTP ${status}`);
    this.status = status;
    this.body   = body;
  }
}

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${HTTP_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    const body = await safeJson(res);
    throw new ApiError(res.status, body, `${init?.method ?? 'GET'} ${path} → ${res.status}`);
  }
  // Some endpoints (e.g. /session/{id}/answer error path) return empty body
  if (res.status === 204) return undefined as unknown as T;
  return (await res.json()) as T;
}

async function safeJson(res: Response): Promise<unknown> {
  try { return await res.json(); } catch { return null; }
}

// ── REST endpoints ──────────────────────────────────────────────────

export const api = {
  startSession(req: StartSessionRequest): Promise<StartSessionResponse> {
    return apiFetch('/session/start', { method: 'POST', body: JSON.stringify(req) });
  },

  /**
   * Send a message to the active session.
   *
   * @param sessionId  session returned by startSession
   * @param message    learner text
   * @param mode       'agentic' (full pipeline) | 'direct' (slim path).
   *                   Sent both as ?mode=… query and as body.mode so
   *                   older backends ignoring one still see the other.
   */
  sendMessage(
    sessionId: string,
    message: string,
    mode: 'agentic' | 'direct' = 'agentic'
  ): Promise<MessageResponse> {
    return apiFetch(`/session/${encodeURIComponent(sessionId)}/message?mode=${mode}`, {
      method: 'POST',
      body: JSON.stringify({ message, mode }),
    });
  },

  endSession(sessionId: string): Promise<unknown> {
    return apiFetch(`/session/${encodeURIComponent(sessionId)}/end`, { method: 'POST' });
  },

  // ── Quiz / Practice generation ─────────────────────────────────────
  // Backend: QuizController (POST /quiz/{learnerId}/{subject}/...)
  // and SessionController answer endpoint for rubric-based grading.

  generateQuiz(
    learnerId: string,
    subject: string,
    req: { topic?: string; difficulty?: string; questionCount?: number }
  ): Promise<QuizGenerateResponse> {
    return apiFetch(
      `/quiz/${encodeURIComponent(learnerId)}/${encodeURIComponent(subject)}/generate`,
      { method: 'POST', body: JSON.stringify(req) }
    );
  },

  submitQuiz(
    learnerId: string,
    subject: string,
    quizId: string,
    answers: QuizAnswerEntry[]
  ): Promise<QuizSubmitResponse> {
    return apiFetch(
      `/quiz/${encodeURIComponent(learnerId)}/${encodeURIComponent(subject)}/${encodeURIComponent(quizId)}/submit`,
      { method: 'POST', body: JSON.stringify({ answers }) }
    );
  },

  submitAnswer(
    sessionId: string,
    question: BackendPracticeQuestion,
    answer: string,
    attemptNumber = 1
  ): Promise<{ feedback: BackendAssessmentFeedback }> {
    return apiFetch(
      `/session/${encodeURIComponent(sessionId)}/answer`,
      { method: 'POST', body: JSON.stringify({ question, answer, attemptNumber }) }
    );
  },

  // ── Syllabus (PR-B) ──────────────────────────────────────────────
  // Backend: SyllabusController.
  //   POST /api/syllabus/suggest   — generate via SyllabusSuggesterAgent
  //   POST /api/syllabus/save      — persist onto LearnerProfile
  //   GET  /api/syllabus/{sid}     — read back saved syllabus
  //
  // The /suggest body accepts EITHER a sessionId (server resolves the
  // active subject/topic/level from the profile) OR all three explicit
  // fields for a session-less preview.

  syllabus: {
    suggest(
      req: { sessionId?: string; subject?: string; topic?: string; level?: string }
    ): Promise<Syllabus> {
      return apiFetch('/api/syllabus/suggest', {
        method: 'POST',
        body: JSON.stringify(req),
      });
    },

    save(sessionId: string, syllabus: Partial<Syllabus>): Promise<Syllabus> {
      return apiFetch('/api/syllabus/save', {
        method: 'POST',
        body: JSON.stringify({ sessionId, syllabus }),
      });
    },

    /**
     * Returns null when the backend reports 204 (session exists but no
     * syllabus saved yet) so callers don't have to special-case it.
     * Throws on 404 (unknown session) and other non-2xx via apiFetch.
     */
    async get(sessionId: string): Promise<Syllabus | null> {
      try {
        return await apiFetch<Syllabus>(
          `/api/syllabus/${encodeURIComponent(sessionId)}`,
          { method: 'GET' });
      } catch (err) {
        // apiFetch throws ApiError on non-2xx; treat 204 (no body) as null.
        if (err instanceof ApiError && err.status === 204) return null;
        throw err;
      }
    },

    /**
     * POST /api/syllabus/extract — upload a PDF or image and get a
     * Syllabus back. Bypasses {@link apiFetch} because multipart needs
     * the browser to set the Content-Type with the right boundary;
     * forcing application/json would break the upload.
     *
     * Throws {@link ApiError} on non-2xx, including 415 when the
     * backend has no vision-capable ChatModel for image uploads.
     */
    async extract(opts: {
      file: File;
      sessionId?: string;
      subject?: string;
    }): Promise<Syllabus> {
      const form = new FormData();
      form.append('file', opts.file);
      if (opts.sessionId) form.append('sessionId', opts.sessionId);
      if (opts.subject)   form.append('subject',   opts.subject);

      const res = await fetch(`${HTTP_BASE}/api/syllabus/extract`, {
        method: 'POST',
        body: form,
        // NOTE: do not set Content-Type. Browsers add multipart boundary.
      });
      if (!res.ok) {
        const body = await safeJson(res);
        const hint =
          res.status === 415
            ? 'Image uploads require a vision-capable LLM. Try a PDF, or set OPENAI_API_KEY with a vision model.'
            : res.status === 400
              ? 'The file looked unsupported, empty, or unreadable. Try a clearer PDF or image.'
              : '';
        throw new ApiError(
          res.status,
          body,
          `POST /api/syllabus/extract → ${res.status}${hint ? ' — ' + hint : ''}`);
      }
      return (await res.json()) as Syllabus;
    },
  },

  // ── Review queue (M3-B) ──────────────────────────────────────────
  // Backend: ReviewController.
  //   GET  /api/review/queue/{learnerId}/{subject}?limit=N
  //        → ReviewQueueItem[]  (most overdue first, never-seen first of all)
  //   POST /api/review/{learnerId}/{subject}/answer
  //        → ConceptMasteryRow  (full row after the SM-2 step)
  //
  // No client-side caching — the queue is short-lived per session and
  // the answer call mutates server state, so we always read-through.

  review: {
    queue(learnerId: string, subject: string, limit = 20): Promise<ReviewQueueItem[]> {
      return apiFetch<ReviewQueueItem[]>(
        `/api/review/queue/${encodeURIComponent(learnerId)}/${encodeURIComponent(subject)}?limit=${limit}`,
        { method: 'GET' });
    },

    answer(
      learnerId: string,
      subject: string,
      body: ReviewAnswer
    ): Promise<ConceptMasteryRow> {
      return apiFetch<ConceptMasteryRow>(
        `/api/review/${encodeURIComponent(learnerId)}/${encodeURIComponent(subject)}/answer`,
        { method: 'POST', body: JSON.stringify(body) });
    },

    /**
     * POST /api/review/{learnerId}/{subject}/card — materialise a
     * PracticeQuestion for the supplied concept via PracticeAgent.
     * Difficulty + Bloom's level calibrate from the row's current
     * score. Returns a stub (with `answer` containing an error note)
     * if generation fails so the UI can still show a card.
     */
    card(
      learnerId: string,
      subject: string,
      body: { concept: string; level?: string; bloomsLevel?: string;
              learningStyle?: string; goal?: string }
    ): Promise<PracticeCardQuestion> {
      return apiFetch<PracticeCardQuestion>(
        `/api/review/${encodeURIComponent(learnerId)}/${encodeURIComponent(subject)}/card`,
        { method: 'POST', body: JSON.stringify(body) });
    },
  },
};

// ── Session bootstrap (shared by chat + quiz + practice) ───────────

/**
 * Stable learner id used across the whole UI. The backend keys session
 * state on `{learnerId}:{subject}`; all features must agree on this
 * value or they'll generate ghost sessions that can't be reconciled.
 *
 * In a real app this comes from auth. For the example it's a constant.
 */
export const DEMO_LEARNER_ID = 'demo-learner';

const SESSIONS_CACHE_KEY = 'tutoros.sessionsBySubject';

function loadSessionMap(): Record<string, string> {
  try {
    const raw = localStorage.getItem(SESSIONS_CACHE_KEY);
    return raw ? JSON.parse(raw) as Record<string, string> : {};
  } catch { return {}; }
}
function saveSessionMap(m: Record<string, string>): void {
  try { localStorage.setItem(SESSIONS_CACHE_KEY, JSON.stringify(m)); }
  catch { /* noop */ }
}

/**
 * Ensure a backend session exists for a (learnerId, subject) pair and
 * return the sessionId. Safe to call many times — the cache keeps us
 * from spamming `/session/start`, and the backend's `startOrResume` is
 * idempotent for the same learner anyway.
 */
export async function ensureBackendSession(
  learnerId: string,
  subject: string,
  opts?: Partial<StartSessionRequest>
): Promise<string> {
  const key = `${learnerId}:${subject}`;
  const cache = loadSessionMap();
  if (cache[key]) return cache[key];

  const res = await api.startSession({
    learnerId,
    name: opts?.name ?? 'Priya',
    level: opts?.level ?? 'higher-sec',
    subjects: [subject],
    topic: opts?.topic ?? `Introduction to ${subject}`,
    goal: opts?.goal ?? 'Build a strong conceptual foundation.',
    sessionsPerWeek: opts?.sessionsPerWeek ?? 4,
    analogyDomain: opts?.analogyDomain ?? 'everyday life',
    profession: opts?.profession,
  });

  cache[key] = res.sessionId;
  saveSessionMap(cache);
  return res.sessionId;
}

// ── Quiz/practice request+response shapes (mirror Java records) ─────

export interface BackendPracticeQuestion {
  question: string;
  type: 'MULTIPLE_CHOICE' | 'SHORT_ANSWER' | 'CALCULATION' | 'DIAGRAM_LABEL' | 'ESSAY' | string;
  options?: string;          // pipe-separated for MC
  answer: string;
  bloomsLevel?: string;
  difficulty?: string;
  conceptTag?: string;
  hints?: string;
  workedSolution?: string;
  diagramDescription?: string;
  marks?: number;
}

export interface BackendAssessmentFeedback {
  score: number;
  correct: boolean;
  correctParts?: string;
  incorrectParts?: string;
  hint?: string;
  encouragement?: string;
  modelAnswer?: string;
  bloomsDemonstrated?: string;
  masteryDelta?: number;
  suggestRetry?: boolean;
  nextConcept?: string;
}

export interface QuizGenerateResponse {
  quizId: string;
  subject: string;
  topic: string;
  difficulty: string;
  questionCount: number;
  timeLimitMinutes: number;
  totalMarks: number;
  bloomsLevelsCovered: string;
  targetGaps: string;
  /** JSON array of BackendPracticeQuestion, serialised as a string */
  questionsJson: string;
  message: string;
}

export interface QuizAnswerEntry {
  question: string;
  correctAnswer: string;
  studentAnswer: string;
  conceptTag: string;
}

export interface QuizSubmitResponse {
  quizId: string;
  totalScore: number;
  maxScore: number;
  percentScore: number;
  grade: string;
  masteredConcepts: string;
  revisitConcepts: string;
  encouragement: string;
  questionResults: unknown[];
  bloomsBreakdown: Record<string, string>;
}

// ── WebSocket frame protocol ────────────────────────────────────────

/**
 * Wire frame as produced by TutoringWebSocketHandler.
 *
 * Today the backend only emits CONNECTED / TOKEN / DONE / ERROR / PONG.
 * The new fields (`stage`, `payload`) are reserved for the upgraded
 * protocol that emits per-stage events; both old and new shapes are
 * tolerated by `translateFrame` below.
 */
export interface StreamFrame {
  type: 'CONNECTED' | 'TOKEN' | 'DONE' | 'ERROR' | 'PONG'
      | 'STAGE_START' | 'STAGE_DONE' | 'STAGE_ERROR'
      | 'DEBATE_ROUND' | 'MESSAGE' | 'MASTERY_DELTA' | 'VISUAL';
  stage?: PipelineStageKey;
  content?: string;
  payload?: unknown;
}

export interface StreamHandlers {
  onFrame?:   (f: StreamFrame) => void;
  onOpen?:    () => void;
  onClose?:   (ev: CloseEvent) => void;
  onError?:   (err: Event) => void;
}

export interface StreamConnection {
  /** Underlying socket (use sparingly; prefer the helpers). */
  socket: WebSocket;
  /** Send a heartbeat. The backend replies with a PONG frame. */
  ping: () => void;
  /** Cleanly close the stream. */
  close: () => void;
}

/**
 * Open a WebSocket against /ws/session/{sessionId}/stream and dispatch
 * parsed frames to the handlers. Reconnection logic is intentionally
 * left to the React hook layer (useSessionStream) — this function is
 * a thin, testable wrapper.
 */
export function connectStream(
  sessionId: string,
  handlers: StreamHandlers = {}
): StreamConnection {
  const url = `${wsBase()}/ws/session/${encodeURIComponent(sessionId)}/stream`;
  const socket = new WebSocket(url);

  socket.addEventListener('open',  () => handlers.onOpen?.());
  socket.addEventListener('close', (ev) => handlers.onClose?.(ev));
  socket.addEventListener('error', (ev) => handlers.onError?.(ev));
  socket.addEventListener('message', (ev) => {
    let frame: StreamFrame | null = null;
    try { frame = JSON.parse(ev.data) as StreamFrame; } catch {
      handlers.onFrame?.({ type: 'ERROR', content: 'Malformed frame: ' + String(ev.data).slice(0, 80) });
      return;
    }
    if (frame) handlers.onFrame?.(frame);
  });

  return {
    socket,
    ping:  () => { if (socket.readyState === WebSocket.OPEN) socket.send('PING'); },
    close: () => {
      try { socket.close(1000, 'client closed'); } catch { /* noop */ }
    },
  };
}

// ── Frame → PipelineEvent translator ────────────────────────────────

/**
 * Turn a sequence of WebSocket frames into the existing `PipelineEvent`
 * stream the store already knows how to consume. Keeps the mock and live
 * paths shape-compatible so components don't need to branch.
 *
 * Behaviour:
 *   - First frame seeds a PipelineRun with all stages 'pending'.
 *   - STAGE_START / STAGE_DONE advance the corresponding step.
 *   - TOKEN frames buffer into the current tutor message; a partial
 *     tutor message is published on first TOKEN, then patched in place
 *     via 'message' events.
 *   - DEBATE_ROUND attaches to the 'debate' step output.
 *   - On legacy backends that only emit TOKEN/DONE, all stages before
 *     'tutor' are auto-marked done at run start so the strip animates.
 */
export class FrameTranslator {
  private readonly runId: string;
  private readonly emit: (evt: PipelineEvent) => void;
  private run: PipelineRun;
  private tutorMsg: ChatMessage | null = null;
  private startedWallMs: number;
  private stageStartWall: Record<string, number> = {};
  private debateRounds: DebateRound['rounds'] = [];
  private hasReceivedStageEvent = false;
  /**
   * Visual asset received via a VISUAL frame. Stashed here so that whatever
   * tutor-message event fires next (TOKEN-seeded message or final MESSAGE)
   * can attach it. If the tutor message already exists when VISUAL arrives,
   * we patch + re-emit immediately.
   */
  private pendingVisual: VisualAsset | null = null;

  constructor(
    prompt: string,
    emit: (evt: PipelineEvent) => void,
    /** Stage template; pass PIPELINE_STAGES_DIRECT for a 2-stage direct-mode view. */
    stages: PipelineStageDef[] = PIPELINE_STAGES
  ) {
    this.runId = `run_live_${Date.now().toString(36)}`;
    this.emit = emit;
    this.startedWallMs = performance.now();

    const steps: PipelineStep[] = stages.map(s => ({
      key: s.key, label: s.label, detail: s.detail, state: 'pending'
    }));
    this.run = { id: this.runId, prompt, startedAt: this.startedWallMs, steps, active: -1 };
    this.emit({ kind: 'started', run: this.run });
  }

  ingest(frame: StreamFrame): void {
    switch (frame.type) {
      case 'CONNECTED':
        // Informational only — no UI event.
        return;

      case 'STAGE_START':
        this.hasReceivedStageEvent = true;
        if (frame.stage) this.startStage(frame.stage);
        return;

      case 'STAGE_DONE':
        this.hasReceivedStageEvent = true;
        if (frame.stage) this.finishStage(frame.stage, 'done', frame.payload);
        return;

      case 'STAGE_ERROR':
        this.hasReceivedStageEvent = true;
        if (frame.stage) this.finishStage(frame.stage, 'error', frame.content ?? 'unknown error');
        return;

      case 'DEBATE_ROUND': {
        const round = frame.payload as DebateRound['rounds'][number];
        if (round) this.debateRounds.push(round);
        return;
      }

      case 'TOKEN': {
        // First token: if we never got STAGE_START events, fast-forward
        // the pre-tutor stages so the reveal panel doesn't look frozen.
        if (!this.hasReceivedStageEvent && !this.tutorMsg) {
          this.fastForwardThroughTutor();
        }
        this.appendTutorToken(frame.content ?? '');
        return;
      }

      case 'MESSAGE': {
        const p = frame.payload as Partial<ChatMessage> | undefined;
        if (p) this.publishFinalTutor(p);
        return;
      }

      case 'VISUAL': {
        const asset = frame.payload as VisualAsset | undefined;
        if (!asset || !asset.type || !asset.specJson) return;
        this.pendingVisual = asset;
        // If the tutor message has already been seeded (token streaming or a
        // prior MESSAGE), patch it in place so the renderer mounts now.
        if (this.tutorMsg) {
          this.tutorMsg = { ...this.tutorMsg, visualAsset: asset };
          this.emit({ kind: 'message', runId: this.runId, message: this.tutorMsg });
        }
        return;
      }

      case 'MASTERY_DELTA':
        // Surface as a 'progress' step output so the reveal panel can show it.
        this.appendStepOutput('progress', frame.payload);
        return;

      case 'DONE':
        this.finalize();
        return;

      case 'ERROR':
        // Mark current active step (if any) as error, but DO NOT finalize —
        // an intermediate-stage failure (e.g. AssessmentFeedback parse fail)
        // is often recovered from by a downstream fallback agent, and the
        // run still produces a valid tutor reply. We finalize only on DONE
        // or an explicit onClose from the stream.
        if (this.run.active >= 0) {
          const cur = this.run.steps[this.run.active];
          if (cur) this.finishStage(cur.key, 'error', frame.content ?? 'stream error');
        }
        return;

      case 'PONG':
        return;
    }
  }

  // ── private helpers ────────────────────────────────────────────────

  private indexOf(stage: PipelineStageKey): number {
    return this.run.steps.findIndex(s => s.key === stage);
  }

  private startStage(stage: PipelineStageKey): void {
    const idx = this.indexOf(stage);
    if (idx < 0) return;
    const step: PipelineStep = { ...this.run.steps[idx]!, state: 'running' };
    this.run.steps[idx] = step;
    this.run.active = idx;
    this.stageStartWall[stage] = performance.now();
    this.emit({ kind: 'step:run', runId: this.runId, index: idx, step });
  }

  private finishStage(
    stage: PipelineStageKey,
    state: 'done' | 'error',
    output: unknown
  ): void {
    const idx = this.indexOf(stage);
    if (idx < 0) return;
    const startedAt = this.stageStartWall[stage] ?? performance.now();
    const ms = Math.max(0, Math.round(performance.now() - startedAt));
    let stepOutput: unknown = output;
    if (stage === 'debate' && this.debateRounds.length > 0) {
      stepOutput = {
        topic: 'Live debate',
        rounds: this.debateRounds,
        verdict: typeof output === 'string' ? output : 'Decided.',
        verdictConfidence: 0.85
      } satisfies DebateRound;
    }
    const step: PipelineStep = {
      ...this.run.steps[idx]!,
      state,
      ms,
      output: stepOutput,
    };
    this.run.steps[idx] = step;
    if (this.run.active === idx) this.run.active = -1;
    this.emit({ kind: 'step:done', runId: this.runId, index: idx, step });
  }

  private appendStepOutput(stage: PipelineStageKey, output: unknown): void {
    const idx = this.indexOf(stage);
    if (idx < 0) return;
    const cur = this.run.steps[idx]!;
    const merged: PipelineStep = { ...cur, output };
    this.run.steps[idx] = merged;
    this.emit({ kind: 'step:done', runId: this.runId, index: idx, step: merged });
  }

  private fastForwardThroughTutor(): void {
    const tutorIdx = this.indexOf('tutor');
    for (let i = 0; i < tutorIdx; i++) {
      const cur = this.run.steps[i]!;
      if (cur.state === 'pending') {
        const step: PipelineStep = { ...cur, state: 'done', ms: 0 };
        this.run.steps[i] = step;
        this.emit({ kind: 'step:done', runId: this.runId, index: i, step });
      }
    }
    this.startStage('tutor');
  }

  private appendTutorToken(token: string): void {
    if (!this.tutorMsg) {
      this.tutorMsg = {
        id: `msg_${Date.now().toString(36)}`,
        role: 'tutor',
        body: token,
        pipelineRunId: this.runId,
        createdAt: Date.now(),
        // VISUAL frames may arrive before the first TOKEN — attach now.
        visualAsset: this.pendingVisual ?? undefined,
      };
      this.emit({ kind: 'message', runId: this.runId, message: this.tutorMsg });
      return;
    }
    // Patch in place by emitting a new message with the same id; the store
    // appends; reducers dedupe by id below in the live path.
    this.tutorMsg = { ...this.tutorMsg, body: this.tutorMsg.body + token };
    this.emit({ kind: 'message', runId: this.runId, message: this.tutorMsg });
  }

  private publishFinalTutor(p: Partial<ChatMessage>): void {
    const id = this.tutorMsg?.id ?? `msg_${Date.now().toString(36)}`;
    // visualAsset preference order:
    //   1. payload.visualAsset (belt-and-suspenders from the MESSAGE frame)
    //   2. pendingVisual stashed by an earlier VISUAL frame
    //   3. whatever was already on the streamed tutor message
    const visualAsset = p.visualAsset ?? this.pendingVisual ?? this.tutorMsg?.visualAsset;
    const merged: ChatMessage = {
      id,
      role: 'tutor',
      body: p.body ?? this.tutorMsg?.body ?? '',
      confidence: p.confidence,
      citations: p.citations,
      debate: p.debate,
      visualAsset,
      // Forward backend disambiguation flags so the UI can render
      // teachingStyle === 'CLARIFY' messages as ClarificationCards
      // instead of plain markdown bubbles.
      teachingStyle: p.teachingStyle ?? this.tutorMsg?.teachingStyle,
      clarification: p.clarification ?? this.tutorMsg?.clarification,
      pipelineRunId: this.runId,
      createdAt: this.tutorMsg?.createdAt ?? Date.now(),
    };
    this.tutorMsg = merged;
    this.emit({ kind: 'message', runId: this.runId, message: merged });
  }

  private finalize(): void {
    // Auto-finish any remaining running step
    if (this.run.active >= 0) {
      const cur = this.run.steps[this.run.active];
      if (cur && cur.state === 'running') this.finishStage(cur.key, 'done', undefined);
    }
    // Auto-mark any still-pending steps as done so the run looks clean
    for (let i = 0; i < this.run.steps.length; i++) {
      const s = this.run.steps[i]!;
      if (s.state === 'pending') {
        const step: PipelineStep = { ...s, state: 'done', ms: 0 };
        this.run.steps[i] = step;
        this.emit({ kind: 'step:done', runId: this.runId, index: i, step });
      }
    }
    const durationMs = performance.now() - this.startedWallMs;
    this.emit({ kind: 'finished', runId: this.runId, durationMs });
  }
}
