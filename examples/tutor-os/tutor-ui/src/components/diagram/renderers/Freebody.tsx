/**
 * `<Freebody />` — deterministic SVG renderer for the `freebody` domain.
 *
 * Why pure SVG: a free-body diagram is just a body, an optional surface,
 * and N labelled force vectors. No physics library needed — what helps
 * the learner is consistent visual encoding (arrowheads, color-by-force,
 * label outside the head).
 *
 * Spec (mirrors VisualisationAgent.visualPrompt):
 *   {
 *     body:    { shape: 'block'|'sphere'|'point', x, y, w, h, label? },
 *     surface: { type: 'ground', y } | { type: 'incline', angle, y } | null,
 *     forces:  [{ label, magnitude, angle (deg, math convention), color }]
 *   }
 *
 * Convention: force `angle` uses math (0°=east, 90°=north drawn UPWARD on
 * screen). This renderer flips the y-axis once at the call site so the
 * LLM never has to think about SVG coordinates.
 *
 * Vector scaling: the longest force is drawn at `MAX_VECTOR_PX`; others
 * scale linearly. Avoids tiny invisible vectors when one force dominates.
 */

const W = 480;
const H = 280;
const MAX_VECTOR_PX = 80;

export interface FreebodyBody {
  shape: 'block' | 'sphere' | 'point';
  x: number;
  y: number;
  w?: number;
  h?: number;
  label?: string;
}

export type FreebodySurface =
  | { type: 'ground';  y: number }
  | { type: 'incline'; angle: number; y: number }
  | null;

export interface FreebodyForce {
  label: string;
  magnitude: number;
  angle: number;
  color?: string;
}

export interface FreebodySpec {
  body:    FreebodyBody;
  surface: FreebodySurface;
  forces:  FreebodyForce[];
}

export function Freebody({ spec, altText }: { spec: FreebodySpec; altText: string }) {
  const body    = spec?.body;
  const surface = spec?.surface ?? null;
  const forces  = Array.isArray(spec?.forces) ? spec.forces : [];

  if (!body || !Number.isFinite(body.x) || !Number.isFinite(body.y)) {
    // Caller's <Diagram> already shows altText fallback when our parent
    // can't proceed; we mirror that here in case the spec is structurally
    // valid JSON but missing the body.
    return (
      <div className="diagram__fallback diagram__fallback--inline">
        {altText} <span className="muted">(invalid body)</span>
      </div>
    );
  }

  // Pre-compute the longest magnitude for proportional scaling.
  const maxMag = forces.reduce(
    (m, f) => Math.max(m, Number.isFinite(f.magnitude) ? Math.abs(f.magnitude) : 0),
    0
  ) || 1;

  return (
    <svg
      className="diagram__svg diagram__svg--freebody"
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={altText}
    >
      <defs>
        {/* One <marker> per force color — defined inline so we don't need
            global ids that could collide across multiple <Freebody>s. */}
        {dedupeColors(forces).map(c => (
          <marker
            key={c}
            id={markerId(c)}
            viewBox="0 0 10 10"
            refX={9}
            refY={5}
            markerWidth={7}
            markerHeight={7}
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill={c} />
          </marker>
        ))}
      </defs>

      {/* Surface — drawn behind the body */}
      {renderSurface(surface)}

      {/* Body */}
      {renderBody(body)}

      {/* Force vectors */}
      {forces.map((f, i) => renderForce(f, body, maxMag, i))}
    </svg>
  );
}

// ── Surface ────────────────────────────────────────────────────────────

function renderSurface(s: FreebodySurface) {
  if (!s) return null;
  if (s.type === 'ground') {
    return (
      <g>
        <line x1={20} y1={s.y} x2={W - 20} y2={s.y} stroke="#374151" strokeWidth={1.6} />
        {/* Hatch marks below ground line */}
        {Array.from({ length: 18 }).map((_, i) => {
          const x = 24 + i * 26;
          return (
            <line
              key={i}
              x1={x}
              y1={s.y}
              x2={x - 6}
              y2={s.y + 8}
              stroke="#374151"
              strokeWidth={1}
            />
          );
        })}
      </g>
    );
  }
  // Incline: triangle from (20, s.y) up at the given angle.
  const len = W - 60;
  const a = Math.max(1, Math.min(60, s.angle));
  const dx = len * Math.cos((a * Math.PI) / 180);
  const dy = len * Math.sin((a * Math.PI) / 180);
  const x1 = 20;
  const y1 = s.y;
  const x2 = x1 + dx;
  const y2 = y1 - dy;
  return (
    <g>
      <polygon
        points={`${x1},${y1} ${x2},${y2} ${x2},${y1}`}
        fill="#f3f4f6"
        stroke="#374151"
        strokeWidth={1.4}
      />
      <text x={x2 - 30} y={y1 - 6} fontSize={11} fill="currentColor">
        {a}°
      </text>
    </g>
  );
}

// ── Body ───────────────────────────────────────────────────────────────

function renderBody(b: FreebodyBody) {
  if (b.shape === 'sphere') {
    const r = (b.w ?? b.h ?? 40) / 2;
    return (
      <g>
        <circle cx={b.x} cy={b.y} r={r} fill="#fde68a" stroke="#92400e" strokeWidth={1.4} />
        {b.label && (
          <text x={b.x} y={b.y + 4} fontSize={12} textAnchor="middle" fontWeight={600}>
            {b.label}
          </text>
        )}
      </g>
    );
  }
  if (b.shape === 'point') {
    return (
      <g>
        <circle cx={b.x} cy={b.y} r={4} fill="#1f2937" />
        {b.label && (
          <text x={b.x + 8} y={b.y - 8} fontSize={12} fontWeight={600} fill="currentColor">
            {b.label}
          </text>
        )}
      </g>
    );
  }
  // block (default)
  const w = b.w ?? 80;
  const h = b.h ?? 60;
  return (
    <g>
      <rect
        x={b.x - w / 2}
        y={b.y - h / 2}
        width={w}
        height={h}
        rx={4}
        fill="#fde68a"
        stroke="#92400e"
        strokeWidth={1.4}
      />
      {b.label && (
        <text
          x={b.x}
          y={b.y + 4}
          fontSize={13}
          textAnchor="middle"
          fontWeight={600}
        >
          {b.label}
        </text>
      )}
    </g>
  );
}

// ── Force vector ───────────────────────────────────────────────────────

function renderForce(f: FreebodyForce, body: FreebodyBody, maxMag: number, key: number) {
  if (!Number.isFinite(f.magnitude) || f.magnitude === 0) return null;
  const color = f.color || '#dc2626';
  // Math angle → SVG coords (flip y).
  const rad = (f.angle * Math.PI) / 180;
  const len = (Math.abs(f.magnitude) / maxMag) * MAX_VECTOR_PX;
  const dx = len * Math.cos(rad);
  const dy = -len * Math.sin(rad);
  const x1 = body.x;
  const y1 = body.y;
  const x2 = x1 + dx;
  const y2 = y1 + dy;
  // Label sits ~10px past the arrow head, biased away from the body.
  const lx = x2 + Math.cos(rad) * 12;
  const ly = y2 - Math.sin(rad) * 12;

  return (
    <g key={`f-${key}`}>
      <line
        x1={x1}
        y1={y1}
        x2={x2}
        y2={y2}
        stroke={color}
        strokeWidth={2.2}
        markerEnd={`url(#${markerId(color)})`}
      />
      <text
        x={lx}
        y={ly}
        fontSize={12}
        fontWeight={600}
        fill={color}
        textAnchor="middle"
        dominantBaseline="middle"
      >
        {f.label}
      </text>
    </g>
  );
}

// ── helpers ────────────────────────────────────────────────────────────

function dedupeColors(forces: FreebodyForce[]): string[] {
  const set = new Set<string>();
  for (const f of forces) set.add(f.color || '#dc2626');
  return Array.from(set);
}

/** Build a SVG-id-safe marker id from a CSS color. */
function markerId(color: string): string {
  return 'arrow-' + color.replace(/[^a-zA-Z0-9]/g, '');
}
