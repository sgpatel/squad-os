import { create } from 'zustand';
import { runPipelineMock, type PipelineEvent } from '@/lib/pipeline';
import type { PipelineRun, PipelineStep, ChatMessage } from '@/lib/types';

/**
 * Pipeline store — the live agent run state + on-screen chat thread.
 *
 * Anyone (Home, Tutor, side-rail) can call `start(prompt)` and watch
 * `currentRun` update step-by-step. The reveal panel reads `currentRun.steps`,
 * the strip reads `currentRun.active`, the debate panel watches the
 * 'debate' step's `output`.
 */
interface PipelineState {
  currentRun: PipelineRun | null;
  history: PipelineRun[];
  messages: ChatMessage[];
  /** True while a run is in flight */
  isRunning: boolean;
  /** UI state — whether the reveal panel is expanded */
  revealOpen: boolean;
  toggleReveal: (open?: boolean) => void;

  start: (prompt: string) => Promise<void>;
  pushUserMessage: (body: string) => void;
  reset: () => void;
}

export const usePipeline = create<PipelineState>()((set, get) => ({
  currentRun: null,
  history: [],
  messages: [],
  isRunning: false,
  revealOpen: false,

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
    set({ isRunning: true, revealOpen: true });

    for await (const evt of runPipelineMock(prompt)) {
      applyEvent(set, evt);
    }
    set({ isRunning: false });
  },

  reset: () => set({ currentRun: null, messages: [], isRunning: false, revealOpen: false })
}));

// ── Event reducer ───────────────────────────────────────────────────
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
