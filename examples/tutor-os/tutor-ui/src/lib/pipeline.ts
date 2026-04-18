/* ─────────────────────────────────────────────────────────────────
 * pipeline.ts — mock agent pipeline with streaming events.
 *
 * Real backend would be an SSE / WebSocket stream of pipeline events.
 * Here we simulate the same shape of stream so the UI components
 * (PipelineStrip, PipelineReveal, DebateRound) can be written
 * exactly as they would be against a real server.
 *
 * Swap `runPipelineMock` with a `runPipelineStream` that yields
 * events from `EventSource('/api/runs/{id}/events')` and the UI
 * keeps working with no changes.
 * ───────────────────────────────────────────────────────────────── */

import type {
  PipelineRun, PipelineStep, PipelineStageKey, ChatMessage, DebateRound
} from './types';
import { PIPELINE_STAGES, seedDebate } from './mockData';

let runIdCounter = 0;

export type PipelineEvent =
  | { kind: 'started';   run: PipelineRun }
  | { kind: 'step:run';  runId: string; index: number; step: PipelineStep }
  | { kind: 'step:done'; runId: string; index: number; step: PipelineStep }
  | { kind: 'message';   runId: string; message: ChatMessage }
  | { kind: 'finished';  runId: string; durationMs: number };

/**
 * Drive the pipeline; yields events as it advances.
 * Caller can `for await (const evt of runPipelineMock(prompt)) { ... }`.
 */
export async function* runPipelineMock(prompt: string): AsyncGenerator<PipelineEvent, void, void> {
  const id = `run_${++runIdCounter}_${Date.now().toString(36)}`;
  const startedAt = performance.now();

  const steps: PipelineStep[] = PIPELINE_STAGES.map(s => ({
    key: s.key, label: s.label, detail: s.detail, state: 'pending'
  }));

  const run: PipelineRun = { id, prompt, startedAt, steps, active: -1 };
  yield { kind: 'started', run };

  for (let i = 0; i < steps.length; i++) {
    const step = steps[i]!;
    step.state = 'running';
    run.active = i;
    yield { kind: 'step:run', runId: id, index: i, step };

    // Simulate variable per-stage latency. Debate is the slowest because
    // it actually runs N rounds on a real backend.
    const ms = stageLatency(step.key);
    await new Promise(r => setTimeout(r, ms));

    step.state = 'done';
    step.ms = ms;
    if (step.key === 'debate') step.output = seedDebate;
    yield { kind: 'step:done', runId: id, index: i, step };
  }

  // Synthesize the final tutor message.
  const message = composeAnswer(prompt, id);
  yield { kind: 'message', runId: id, message };

  const durationMs = performance.now() - startedAt;
  run.finishedAt = performance.now();
  run.active = -1;
  yield { kind: 'finished', runId: id, durationMs };
}

function stageLatency(key: PipelineStageKey): number {
  // ms — base + jitter. Tuned so the whole pipeline feels alive
  // without being annoying in the example.
  const base: Record<PipelineStageKey, number> = {
    guardian:   200,
    diagnostic: 350,
    planner:    400,
    content:    550,
    debate:     900,
    tutor:      750,
    practice:   400,
    assessment: 350,
    progress:   250
  };
  return base[key] + Math.random() * 250;
}

/** A simple, deterministic-ish answer composer (mock). */
function composeAnswer(prompt: string, runId: string): ChatMessage {
  const lower = prompt.toLowerCase();
  let body: string;
  let debate: DebateRound | undefined;

  if (/photo|light reaction|calvin/.test(lower)) {
    body = [
      'Photosynthesis runs in two stages.',
      '**Light reactions** (thylakoid): light splits water at PSII, electrons flow through the ETC, protons accumulate, and ATP synthase makes ATP. NADP⁺ → NADPH at the end.',
      '**Calvin cycle** (stroma): RuBisCO fixes CO₂ onto RuBP. The cycle uses ATP + NADPH to reduce 3-PGA to G3P. Two G3P → one glucose.'
    ].join('\n\n');
    debate = seedDebate;
  } else if (/iam|policy|role/.test(lower)) {
    body = [
      'Think IAM as a 4-tuple: **Principal · Action · Resource · Condition**.',
      'Policies are JSON. Effect is `Allow` or `Deny`. Deny always wins. Implicit deny is the default.',
      'Roles are *assumable* identities — used for cross-account access and EC2/Lambda execution.'
    ].join('\n\n');
  } else if (/big.?o|complexity|algorithm/.test(lower)) {
    body = [
      'Big-O describes how a function\'s cost grows as input grows.',
      'Drop constants and lower-order terms: `3n² + 5n + 100` → `O(n²)`.',
      'Common ranks: `O(1) < O(log n) < O(n) < O(n log n) < O(n²) < O(2ⁿ) < O(n!)`.'
    ].join('\n\n');
  } else {
    body = `Here\'s a structured answer for "${prompt}":\n\n1. **Definition** — what it is, in one sentence.\n2. **Why it matters** — the principle behind it.\n3. **Example** — a worked case.\n4. **Common mistake** — what to watch out for.`;
  }

  return {
    id: `msg_${Date.now().toString(36)}`,
    role: 'tutor',
    body,
    confidence: 0.84,
    citations: [
      { sourceId: 'src_alberts',   locator: 'ch.14' },
      { sourceId: 'src_lehninger', locator: 'ch.19' }
    ],
    debate,
    pipelineRunId: runId,
    createdAt: Date.now()
  };
}
