import { useEffect, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown, Loader2, Sparkles, AlertTriangle } from 'lucide-react';
import { usePipeline } from '@/store/pipeline';

/**
 * AgentActivity — the inline "thinking" block.
 *
 * Modelled on the tool-use / thinking affordances in ChatGPT, Claude, and
 * Perplexity. Renders in the chat thread at the position where the tutor's
 * reply will land, so the learner sees work happening where the answer
 * will appear — not in a separate panel below.
 *
 * States:
 *   running  → expanded card; current stage animates; elapsed ticks live
 *   done     → collapses automatically to a one-line chip ("Thought for
 *              3.2s · 5 steps"). Click to re-expand; user preference is
 *              remembered for the rest of the run.
 *
 * Self-contained: reads `currentRun` + `isRunning` from the pipeline store.
 * Returns null when there is no run to show.
 */
export function AgentActivity() {
  const { currentRun, isRunning, messages } = usePipeline();

  // userExpanded overrides the auto behaviour (null = follow auto)
  const [userExpanded, setUserExpanded] = useState<boolean | null>(null);

  // Live-ticking elapsed counter while running.
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    if (!isRunning || !currentRun) return;
    const id = window.setInterval(() => {
      setElapsed((performance.now() - currentRun.startedAt) / 1000);
    }, 100);
    return () => window.clearInterval(id);
  }, [isRunning, currentRun]);

  // Reset user-override whenever a brand-new run starts, so the default
  // (expanded while running → auto-collapse on done) kicks in again.
  const prevRunIdRef = useRef<string | null>(null);
  useEffect(() => {
    if (currentRun && currentRun.id !== prevRunIdRef.current) {
      prevRunIdRef.current = currentRun.id;
      setUserExpanded(null);
    }
  }, [currentRun]);

  if (!currentRun) return null;

  const auto = isRunning;            // expand while running, collapse when done
  const expanded = userExpanded ?? auto;

  const totalMs = currentRun.finishedAt != null
    ? currentRun.finishedAt - currentRun.startedAt
    : performance.now() - currentRun.startedAt;

  const done    = currentRun.steps.filter(s => s.state === 'done').length;
  const total   = currentRun.steps.length;
  const anyErr  = currentRun.steps.some(s => s.state === 'error');
  const active  = currentRun.steps[currentRun.active];

  // A tutor reply landing in the thread after this run started means the
  // pipeline recovered — intermediate stage errors don't count as a failure.
  const tutorReplied = messages.some(m =>
    m.role === 'tutor' && m.createdAt >= currentRun.startedAt
  );
  const erred = anyErr && !tutorReplied && !isRunning;

  // Single-line status shown in the header.
  const headline = useMemo(() => {
    if (erred) return 'Something went wrong';
    if (isRunning) return active ? active.label : 'Thinking';
    return `Thought for ${(totalMs / 1000).toFixed(1)}s · ${total} step${total === 1 ? '' : 's'}`;
  }, [erred, isRunning, active, totalMs, total]);

  const timeText = isRunning
    ? `${elapsed.toFixed(1)}s`
    : `${done}/${total}`;

  return (
    <div
      className="agent-activity"
      data-state={erred ? 'error' : isRunning ? 'running' : 'done'}
      data-expanded={expanded ? 'true' : 'false'}
      aria-live="polite"
    >
      <button
        type="button"
        className="agent-activity__head"
        onClick={() => setUserExpanded(!expanded)}
        aria-expanded={expanded}
      >
        <span className="agent-activity__icon" aria-hidden="true">
          {erred      ? <AlertTriangle size={14} />
          : isRunning ? <Loader2 size={14} className="agent-activity__spin" />
                      : <Check size={14} />}
        </span>
        <span className="agent-activity__headline">{headline}</span>
        <span className="agent-activity__time">{timeText}</span>
        <ChevronDown size={14} className="agent-activity__chevron" aria-hidden="true" />
      </button>

      <div className="agent-activity__body" role="region">
        <ol className="agent-activity__steps" role="list">
          {currentRun.steps.map((step, i) => {
            const isActive = i === currentRun.active && isRunning;
            return (
              <li
                key={step.key}
                className="agent-activity__step"
                data-state={step.state}
                style={{ ['--stage-color' as string]: `var(--color-stage-${step.key})` }}
              >
                <span className="agent-activity__step-bullet" aria-hidden="true">
                  {step.state === 'done'    && <Check size={10} />}
                  {step.state === 'error'   && <AlertTriangle size={10} />}
                  {step.state === 'running' && <span className="agent-activity__pulse" />}
                  {step.state === 'pending' && <span className="agent-activity__dot" />}
                </span>
                <span className="agent-activity__step-body">
                  <span className="agent-activity__step-label">{step.label}</span>
                  {step.detail && !isActive && (
                    <span className="agent-activity__step-detail">{step.detail}</span>
                  )}
                  {isActive && (
                    <span className="agent-activity__step-detail agent-activity__step-detail--live">
                      <Sparkles size={10} /> {step.detail || 'working…'}
                    </span>
                  )}
                </span>
                <span className="agent-activity__step-time">
                  {step.ms != null ? `${(step.ms / 1000).toFixed(1)}s` : isActive ? '…' : ''}
                </span>
              </li>
            );
          })}
        </ol>
      </div>
    </div>
  );
}
