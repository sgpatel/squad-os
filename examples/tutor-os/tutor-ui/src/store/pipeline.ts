import { create } from 'zustand';
import { runPipelineMock, type PipelineEvent } from '@/lib/pipeline';
import { LIVE_BACKEND, api, connectStream, FrameTranslator, ApiError, type StreamFrame } from '@/lib/api';
import { useSettings } from '@/store/settings';
import { stagesForMode } from '@/lib/mockData';
import type { PipelineRun, PipelineStep, ChatMessage } from '@/lib/types';

/**
 * Pipeline store — the live agent run state + on-screen chat thread.
 *
 * Anyone (Home, Tutor, side-rail) can call `start(prompt)` and watch
 * `currentRun` update step-by-step. The reveal panel reads `currentRun.steps`,
 * the strip reads `currentRun.active`, the debate panel watches the
 * 'debate' step's `output`.
 *
 * Two execution paths, selected at module-load:
 *   - LIVE_BACKEND=true   → POST /session/{id}/message + WS stream
 *                           (translated into the same PipelineEvent shape)
 *   - LIVE_BACKEND=false  → in-memory mock (runPipelineMock)
 *
 * The session id is created lazily on first `start()` and persisted to
 * localStorage so a refresh resumes the same backend session.
 */
interface PipelineState {
  currentRun: PipelineRun | null;
  history: PipelineRun[];
  messages: ChatMessage[];
  /** True while a run is in flight */
  isRunning: boolean;
  /** UI state — whether the reveal panel is expanded */
  revealOpen: boolean;
  /** Current backend session id (null in mock mode) */
  sessionId: string | null;
  /** Last live-stream error, if any (null when healthy) */
  lastError: string | null;
  toggleReveal: (open?: boolean) => void;

  start: (prompt: string) => Promise<void>;
  pushUserMessage: (body: string) => void;
  reset: () => void;
}

const SESSION_ID_KEY = 'tutoros.sessionId';

function loadSessionId(): string | null {
  try { return localStorage.getItem(SESSION_ID_KEY); } catch { return null; }
}
function saveSessionId(id: string): void {
  try { localStorage.setItem(SESSION_ID_KEY, id); } catch { /* noop */ }
}
function clearSessionId(): void {
  try { localStorage.removeItem(SESSION_ID_KEY); } catch { /* noop */ }
}

export const usePipeline = create<PipelineState>()((set, get) => ({
  currentRun: null,
  history: [],
  messages: [],
  isRunning: false,
  revealOpen: false,
  sessionId: loadSessionId(),
  lastError: null,

  toggleReveal: (open) => set(s => ({ revealOpen: open ?? !s.revealOpen })),

  pushUserMessage: (body) => set(s => ({
    messages: [...s.messages, {
      id: `msg_${Date.now().toString(36)}`,
      role: 'learner',
      body,
      createdAt: Date.now()
    }]
  })),

  start: async (prompt) => {
    if (get().isRunning) return;
    get().pushUserMessage(prompt);
    set({ isRunning: true, revealOpen: true, lastError: null });

    // Choose the stage template based on assist mode. Direct mode only
    // runs guardian → tutor, so the activity block stays clean and honest.
    const assistMode = useSettings.getState().assistMode;
    const stages = stagesForMode(assistMode);

    try {
      if (LIVE_BACKEND) {
        // The cached sessionId may be stale if the backend restarted; in that
        // case runLive() returns 'stale-session' without publishing anything
        // to the UI. We clear the cache and try exactly once more — further
        // failures are genuine errors and surface normally.
        let result = await runLive(prompt, stages, set, get);
        if (result === 'stale-session') {
          clearSessionId();
          set({ sessionId: null });
          result = await runLive(prompt, stages, set, get);
        }
      } else {
        for await (const evt of runPipelineMock(prompt, stages)) {
          applyEvent(set, evt);
        }
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      set({ lastError: msg });
      // Surface as a tutor message so the chat doesn't go silent on failure.
      applyEvent(set, {
        kind: 'message',
        runId: get().currentRun?.id ?? 'err',
        message: {
          id: `msg_err_${Date.now().toString(36)}`,
          role: 'tutor',
          body: `_Sorry — the tutor backend errored: ${msg}_`,
          createdAt: Date.now(),
        }
      });
    } finally {
      set({ isRunning: false });
    }
  },

  reset: () => {
    clearSessionId();
    set({
      currentRun: null, messages: [], isRunning: false,
      revealOpen: false, sessionId: null, lastError: null
    });
  }
}));

// ── Live backend driver ─────────────────────────────────────────────

/**
 * Live execution path:
 *   1. Ensure a backend session exists (create lazily, cache in localStorage).
 *   2. Open the WS stream and pipe frames through FrameTranslator.
 *   3. POST /session/{id}/message in parallel.
 *   4. Resolve when the stream emits DONE (or after the message POST returns
 *      and stream falls silent for the grace window).
 */
async function runLive(
  prompt: string,
  stages: ReturnType<typeof stagesForMode>,
  set: (partial: Partial<PipelineState> | ((s: PipelineState) => Partial<PipelineState>)) => void,
  get: () => PipelineState
): Promise<'ok' | 'stale-session'> {
  const sessionId = await ensureSession(set, get);

  return await new Promise<'ok' | 'stale-session'>((resolve) => {
    let timeoutId: number | undefined;
    let conn: ReturnType<typeof connectStream> | null = null;
    let settled = false;

    const settle = (outcome: 'ok' | 'stale-session' = 'ok') => {
      if (settled) return;
      settled = true;
      if (timeoutId !== undefined) window.clearTimeout(timeoutId);
      conn?.close();
      resolve(outcome);
    };

    const translator = new FrameTranslator(prompt, (evt: PipelineEvent) => {
      applyLiveEvent(set, evt);
      if (evt.kind === 'finished') settle();
    }, stages);

    conn = connectStream(sessionId, {
      onOpen: async () => {
        try {
          // Read the mode *now* (at send time), not when the store was built,
          // so a topbar toggle flipped mid-conversation takes effect on the
          // very next message.
          const assistMode = useSettings.getState().assistMode;
          const res = await api.sendMessage(sessionId, prompt, assistMode);

          // Stale session caught here — the backend restarted and forgot
          // about our in-memory sessionId. Bail out WITHOUT publishing any
          // frames so the caller can silently retry with a fresh session.
          if (res && res.type === 'BLOCKED' && res.text === 'SESSION_NOT_FOUND') {
            settle('stale-session');
            return;
          }

          // For non-streaming reply types (DIAGNOSTIC, PLAN, FEEDBACK, BLOCKED, …)
          // the WS may stay quiet — synthesize a final MESSAGE frame so the
          // translator publishes the tutor reply and finalizes the run.
          if (res && res.type !== 'TUTOR') {
            translator.ingest({
              type: 'MESSAGE',
              payload: { body: res.text ?? messageFromNonTutor(res.type) }
            });
            translator.ingest({ type: 'DONE' });
          }
        } catch (err) {
          translator.ingest({
            type: 'ERROR',
            content: err instanceof ApiError ? err.message : String(err)
          });
        }
      },
      onFrame: (f: StreamFrame) => translator.ingest(f),
      onError: () => translator.ingest({ type: 'ERROR', content: 'WebSocket error' }),
      onClose:  () => settle(),
    });

    // Hard timeout safeguard — 60s after open.
    timeoutId = window.setTimeout(() => {
      translator.ingest({ type: 'ERROR', content: 'Backend timed out' });
      settle();
    }, 60_000);
  });
}

function messageFromNonTutor(type: string): string {
  switch (type) {
    case 'DIAGNOSTIC': return 'Started a quick diagnostic — answer a few questions to calibrate.';
    case 'PLAN':       return 'Drafted a study plan for you. Review it on the Subjects page.';
    case 'FEEDBACK':   return 'Submitted for feedback.';
    case 'BLOCKED':    return 'I can\'t help with that.';
    case 'SAFE_REFUSAL': return 'Let\'s take a different angle on that one.';
    default:           return 'Done.';
  }
}

/** Returns a usable backend sessionId, creating one lazily if needed. */
async function ensureSession(
  set: (partial: Partial<PipelineState> | ((s: PipelineState) => Partial<PipelineState>)) => void,
  get: () => PipelineState
): Promise<string> {
  const cached = get().sessionId;
  if (cached) return cached;

  const res = await api.startSession({
    learnerId: 'demo-learner',
    name: 'Aarav',
    level: 'higher-sec',
    subjects: ['Biology'],
    topic: 'Photosynthesis',
    goal: 'Build a strong conceptual foundation for board exams.',
    sessionsPerWeek: 4,
    analogyDomain: 'everyday life'
  });

  saveSessionId(res.sessionId);
  set({ sessionId: res.sessionId });
  return res.sessionId;
}

// ── Event reducer (mock + live share this) ──────────────────────────

function applyEvent(
  set: (partial: Partial<PipelineState> | ((s: PipelineState) => Partial<PipelineState>)) => void,
  evt: PipelineEvent
) {
  switch (evt.kind) {
    case 'started':
      set({ currentRun: evt.run });
      break;
    case 'step:run':
    case 'step:done':
      set(s => {
        if (!s.currentRun || s.currentRun.id !== evt.runId) return {};
        const steps: PipelineStep[] = s.currentRun.steps.map((st, i) =>
          i === evt.index ? evt.step : st
        );
        return {
          currentRun: {
            ...s.currentRun,
            steps,
            active: evt.kind === 'step:run' ? evt.index : s.currentRun.active
          }
        };
      });
      break;
    case 'message':
      set(s => ({ messages: [...s.messages, evt.message] }));
      break;
    case 'finished':
      set(s => {
        if (!s.currentRun || s.currentRun.id !== evt.runId) return {};
        const finished = { ...s.currentRun, finishedAt: performance.now(), active: -1 };
        return {
          currentRun: finished,
          history: [finished, ...s.history].slice(0, 20)
        };
      });
      break;
  }
}

/**
 * Live variant — same as applyEvent but dedupes streaming tutor messages
 * by id (the translator emits a fresh `message` event for every appended
 * token, all sharing the same id).
 */
function applyLiveEvent(
  set: (partial: Partial<PipelineState> | ((s: PipelineState) => Partial<PipelineState>)) => void,
  evt: PipelineEvent
) {
  if (evt.kind !== 'message') return applyEvent(set, evt);
  set(s => {
    const idx = s.messages.findIndex(m => m.id === evt.message.id);
    if (idx < 0) return { messages: [...s.messages, evt.message] };
    const next = s.messages.slice();
    next[idx] = evt.message;
    return { messages: next };
  });
}
