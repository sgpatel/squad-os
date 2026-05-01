import { create } from 'zustand';
import { runPipelineMock, type PipelineEvent } from '@/lib/pipeline';
import { LIVE_BACKEND, api, connectStream, FrameTranslator, ApiError, DEMO_LEARNER_ID, type StreamFrame } from '@/lib/api';
import { useSettings } from '@/store/settings';
import { useAuth } from '@/store/auth';
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
  /** Inject a tutor message with a hardcoded VisualAsset — for FE demos
   *  while the live agent path that populates `visualAsset` is still TODO. */
  pushDemoVisual: (kind: 'chem' | 'plot' | 'geometry' | 'freebody') => void;
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

/**
 * Build a demo VisualAsset for the command-palette "Insert demo …" entries.
 *
 * These specs are the same shape the VisualisationAgent emits in production
 * — they double as visual regression tests for each renderer. When you add
 * a new domain, add a demo here so the command palette can preview it
 * without spending an LLM call.
 */
function buildDemoVisual(kind: 'chem' | 'plot' | 'geometry' | 'freebody'): {
  visualAsset: NonNullable<ChatMessage['visualAsset']>;
  body: string;
} {
  switch (kind) {
    case 'chem':
      return {
        visualAsset: {
          type: 'chem',
          concept: 'Benzene',
          title: 'Benzene (C₆H₆)',
          caption: 'Aromatic ring — six sp² carbons, delocalised π electrons.',
          specJson: JSON.stringify({ smiles: 'c1ccccc1' }),
          altText: 'Skeletal structure of benzene: a regular hexagon of six carbons with alternating double bonds.',
        },
        body: 'Here is the structure of **benzene** — a planar hexagonal ring of six carbons with delocalised π electrons.',
      };

    case 'plot':
      return {
        visualAsset: {
          type: 'plot',
          concept: 'Sine wave',
          title: 'y = sin(x)',
          caption: 'One full period from 0 to 2π — note zeros at 0, π, 2π.',
          specJson: JSON.stringify({
            $schema: 'https://vega.github.io/schema/vega-lite/v5.json',
            width: 420, height: 220,
            data: {
              values: Array.from({ length: 81 }, (_, i) => {
                const x = (i / 80) * 2 * Math.PI;
                return { x, y: Math.sin(x) };
              }),
            },
            mark: { type: 'line', strokeWidth: 2 },
            encoding: {
              x: { field: 'x', type: 'quantitative', title: 'x (radians)' },
              y: { field: 'y', type: 'quantitative', title: 'sin(x)' },
            },
          }),
          altText: 'Line plot of y = sin(x) from 0 to 2π.',
        },
        body: 'Here is **y = sin(x)** plotted across one full period (0 → 2π). Notice the zeros at 0, π, and 2π.',
      };

    case 'geometry':
      // 3-4-5 right triangle with hypotenuse labelled and the right angle marked.
      return {
        visualAsset: {
          type: 'geometry',
          concept: '3-4-5 triangle',
          title: 'Pythagorean triangle',
          caption: 'A right triangle with legs 3 and 4 has hypotenuse 5 — 3² + 4² = 5².',
          specJson: JSON.stringify({
            points: [
              { id: 'A', x: 110, y: 220, label: 'A' },
              { id: 'B', x: 350, y: 220, label: 'B' },
              { id: 'C', x: 110, y:  60, label: 'C' },
            ],
            polygons: [{ points: ['A', 'B', 'C'], fill: '#eef2ff' }],
            segments: [
              { from: 'A', to: 'B', label: '4' },
              { from: 'A', to: 'C', label: '3' },
              { from: 'B', to: 'C', label: '5' },
            ],
            arcs: [
              // Right-angle marker at A: a small 0–90° arc.
              { cx: 110, cy: 220, r: 18, startDeg: 0, endDeg: 90, label: '90°' },
            ],
          }),
          altText: 'Right triangle with vertices A bottom-left, B bottom-right, C top-left; legs 3 and 4, hypotenuse 5.',
        },
        body: 'Here is a classic **3-4-5 right triangle** — the simplest Pythagorean triple. The right angle sits at A; the hypotenuse BC has length 5.',
      };

    case 'freebody':
      // Block on a horizontal surface with weight, normal, applied force, friction.
      return {
        visualAsset: {
          type: 'freebody',
          concept: 'Block on ground',
          title: 'Free-body: block sliding right',
          caption: 'Weight balances normal force; applied force overcomes kinetic friction.',
          specJson: JSON.stringify({
            body:    { shape: 'block', x: 240, y: 150, w: 90, h: 60, label: 'm' },
            surface: { type: 'ground', y: 200 },
            forces: [
              { label: 'W',  magnitude: 80, angle: 270, color: '#dc2626' },  // weight, down
              { label: 'N',  magnitude: 80, angle:  90, color: '#2563eb' },  // normal, up
              { label: 'F',  magnitude: 70, angle:   0, color: '#16a34a' },  // applied, right
              { label: 'fk', magnitude: 35, angle: 180, color: '#9333ea' },  // friction, left
            ],
          }),
          altText: 'Free-body diagram of a block on the ground with weight (down), normal (up), applied force (right), and kinetic friction (left).',
        },
        body: 'Here is a **free-body diagram** of a block being pushed along the ground. Weight (W) and the normal force (N) cancel vertically; the applied force (F) is partly opposed by kinetic friction (fk).',
      };
  }
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

  pushDemoVisual: (kind) => {
    const now = Date.now();
    const id = `msg_demo_${now.toString(36)}`;
    const { visualAsset, body } = buildDemoVisual(kind);

    set(s => ({
      messages: [...s.messages, {
        id, role: 'tutor', body, createdAt: now, visualAsset,
      }],
    }));
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

  // Get the selected subject from workspace store
  const { activeSubjectId, getSubject } = await import('@/store/workspace').then(m => m.useWorkspace.getState());
  const subject = activeSubjectId ? getSubject(activeSubjectId) : null;

  // Default to Biology/Photosynthesis if no subject selected
  const subjectName = subject?.name || 'Biology';
  const topic = subject ? `Introduction to ${subject.name}` : 'Photosynthesis';

  // Pull identity + profile from the logged-in user. If called before
  // auth is ready (shouldn't happen — RequireAuth gates the app), fall
  // back to the demo constants so the request at least succeeds.
  const me = useAuth.getState().currentUser();

  const res = await api.startSession({
    learnerId: me?.id ?? DEMO_LEARNER_ID,
    name:      me?.name ?? 'Learner',
    level:     me?.level ?? 'higher-sec',
    subjects: [subjectName],
    topic: topic,
    goal:  me?.goal ?? 'Build a strong conceptual foundation.',
    sessionsPerWeek: me?.sessionsPerWeek ?? 4,
    analogyDomain:   me?.analogyDomain   ?? 'everyday life',
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
