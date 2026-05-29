import { usePipeline } from '@/store/pipeline';
import { PIPELINE_STAGES } from '@/lib/mockData';

/**
 * Pipeline Strip — Layer A (ambient).
 *
 * Lives in the topbar. One dot per agent role. Active dot pulses,
 * completed dots dim. Click "Show steps" to expand the reveal panel.
 *
 * Renders nothing when there's no active or recently-completed run.
 */
export function PipelineStrip() {
  const { currentRun, isRunning, revealOpen, toggleReveal } = usePipeline();
  if (!currentRun) return null;

  const active = currentRun.active;
  const stage = active >= 0 ? PIPELINE_STAGES[active] : null;
  const total = PIPELINE_STAGES.length;

  return (
    <div className="pipeline-strip" role="status" aria-live="polite">
      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        {PIPELINE_STAGES.map((s, i) => {
          const cls =
            i === active ? 'pipeline-strip__dot pipeline-strip__dot--active' :
            i < active   ? 'pipeline-strip__dot pipeline-strip__dot--done'   :
                           'pipeline-strip__dot';
          return (
            <span
              key={s.key}
              className={cls}
              style={{ ['--stage-color' as any]: `var(--color-stage-${s.key})` }}
              title={s.label}
            />
          );
        })}
      </div>
      <span className="pipeline-strip__label">
        {isRunning && stage
          ? <><strong>{stage.label}</strong> · {active + 1} / {total}</>
          : <><strong>Done</strong> · {total} / {total}</>}
      </span>
      <button
        className="pipeline-strip__expand"
        onClick={() => toggleReveal()}
        aria-expanded={revealOpen}
      >
        {revealOpen ? 'Hide steps' : 'Show steps'}
      </button>
    </div>
  );
}
