import { useEffect, useState } from 'react';
import { usePipeline } from '@/store/pipeline';

/**
 * Pipeline Reveal — Layer B (timeline).
 *
 * The expandable per-step list. Reads `currentRun.steps` from the
 * pipeline store and renders state-aware rows. Self-contained — drop
 * it anywhere a route wants to expose the run details.
 */
export function PipelineReveal() {
  const { currentRun, isRunning, revealOpen } = usePipeline();
  const [elapsed, setElapsed] = useState(0);

  // Tick a 100ms timer while running so the elapsed counter feels live.
  useEffect(() => {
    if (!isRunning || !currentRun) return;
    const id = window.setInterval(() => {
      setElapsed((performance.now() - currentRun.startedAt) / 1000);
    }, 100);
    return () => clearInterval(id);
  }, [isRunning, currentRun]);

  if (!currentRun) return null;

  const totalMs = currentRun.finishedAt != null
    ? (currentRun.finishedAt - currentRun.startedAt)
    : (performance.now() - currentRun.startedAt);

  return (
    <section className="reveal" data-open={revealOpen ? 'true' : 'false'} aria-live="polite">
      <div className="reveal__head">
        <div>
          <h4>Pipeline run</h4>
          <p className="small muted">
            {isRunning ? `Running: "${currentRun.prompt}"` : `Done — answer composed with citations + 1 debate round.`}
          </p>
        </div>
        <div className="small">
          <span style={{ fontVariantNumeric: 'tabular-nums' }}>
            {(isRunning ? elapsed : totalMs / 1000).toFixed(1)}s
          </span>
        </div>
      </div>
      <ol className="timeline" role="list">
        {currentRun.steps.map((step, i) => (
          <li
            key={step.key}
            className="timeline__row"
            data-state={step.state}
            style={{ ['--stage-color' as any]: `var(--color-stage-${step.key})` }}
          >
            <span className="timeline__bullet" aria-hidden="true" />
            <div className="timeline__body">
              <p className="timeline__stage">{step.key}</p>
              <p className="timeline__title">{step.label}</p>
              <p className="timeline__detail">{step.detail}</p>
            </div>
            <span className="timeline__time">
              {step.ms != null ? `${(step.ms / 1000).toFixed(1)}s` : i === currentRun.active ? '…' : ''}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
