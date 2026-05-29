import { usePipeline } from '@/store/pipeline';
import { PIPELINE_STAGES } from '@/lib/mockData';

/**
 * Pipeline Graph — Layer C (topology).
 *
 * The "agents talking to each other" diagram. Pure SVG.
 * Stages are arranged left→right; the debate stage forks into two
 * sub-nodes (Advocate / Skeptic) that meet at the Judge.
 */
export function PipelineGraph() {
  const { currentRun } = usePipeline();
  const active = currentRun?.active ?? -1;

  // Layout: 9 stages along x, row 0; debate (idx 4) has two satellites + judge.
  const W = 760, H = 220;
  const padX = 40;
  const stepX = (W - padX * 2) / (PIPELINE_STAGES.length - 1);
  const cy = H / 2;
  const nodes = PIPELINE_STAGES.map((s, i) => ({
    key: s.key, label: s.label,
    x: padX + i * stepX, y: cy,
    color: `var(--color-stage-${s.key})`
  }));

  const debateIdx = nodes.findIndex(n => n.key === 'debate');
  const debateNode = nodes[debateIdx]!;
  const adv = { x: debateNode.x, y: cy - 50, label: 'Advocate' };
  const skp = { x: debateNode.x, y: cy + 50, label: 'Skeptic' };

  return (
    <div className="graph">
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid meet">
        {/* Spine: connect stages */}
        {nodes.slice(0, -1).map((n, i) => {
          const next = nodes[i + 1]!;
          return (
            <line
              key={`l-${i}`}
              x1={n.x} y1={n.y} x2={next.x} y2={next.y}
              stroke="var(--color-border)" strokeWidth={1.5}
              strokeDasharray={i < active ? '0' : '3 3'}
            />
          );
        })}

        {/* Debate fork lines */}
        <line x1={debateNode.x} y1={debateNode.y} x2={adv.x} y2={adv.y} stroke="var(--color-stage-debate)" strokeWidth={1.5} opacity={0.6} />
        <line x1={debateNode.x} y1={debateNode.y} x2={skp.x} y2={skp.y} stroke="var(--color-stage-debate)" strokeWidth={1.5} opacity={0.6} />

        {/* Nodes */}
        {nodes.map((n, i) => (
          <g key={n.key} className="graph__node" transform={`translate(${n.x} ${n.y})`}>
            <circle
              r={i === active ? 11 : i < active ? 8 : 6}
              fill={i <= active ? n.color : 'var(--color-surface-2)'}
              stroke={i === active ? n.color : 'var(--color-border)'}
              strokeWidth={1.5}
            />
            <text
              y={26}
              textAnchor="middle"
              fill="var(--color-text-muted)"
              fontSize={10}
              fontFamily="var(--font-mono)"
            >
              {n.key}
            </text>
          </g>
        ))}

        {/* Debate satellites */}
        {[adv, skp].map((s, idx) => (
          <g key={idx} transform={`translate(${s.x} ${s.y})`}>
            <circle r={5} fill="var(--color-stage-debate)" opacity={debateIdx <= active ? 0.85 : 0.3} />
            <text y={idx === 0 ? -10 : 16} textAnchor="middle" fill="var(--color-text-muted)" fontSize={10}>
              {s.label}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
}
