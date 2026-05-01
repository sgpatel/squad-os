/**
 * `<Geometry />` — deterministic SVG renderer for the `geometry` domain.
 *
 * Why pure SVG (no library):
 *   - The spec is tiny (points, segments, polygons, circles, arcs).
 *   - We control coordinate semantics — the LLM emits inside a fixed
 *     480×280 viewBox so we don't need camera/viewport math.
 *   - JSXGraph / Mafs are 100–300 KB gz; this renderer is ~3 KB and
 *     produces real, screen-reader-friendly inline SVG.
 *
 * Spec (mirrors the prompt in VisualisationAgent.visualPrompt):
 *   {
 *     points:   [{ id, x, y, label? }],
 *     segments: [{ from, to, label? }],
 *     polygons: [{ points: [pid, ...], fill?, stroke? }],
 *     circles:  [{ cx, cy, r, label? }],
 *     arcs:     [{ cx, cy, r, startDeg, endDeg, label? }]
 *   }
 *
 * Defensive: any malformed field is skipped, not thrown — partial diagrams
 * are still useful, total failure is not.
 */

const W = 480;
const H = 280;

export interface GeoPoint    { id: string; x: number; y: number; label?: string }
export interface GeoSegment  { from: string; to: string; label?: string }
export interface GeoPolygon  { points: string[]; fill?: string; stroke?: string }
export interface GeoCircle   { cx: number; cy: number; r: number; label?: string }
export interface GeoArc      { cx: number; cy: number; r: number; startDeg: number; endDeg: number; label?: string }

export interface GeometrySpec {
  points?:   GeoPoint[];
  segments?: GeoSegment[];
  polygons?: GeoPolygon[];
  circles?:  GeoCircle[];
  arcs?:     GeoArc[];
}

export function Geometry({ spec, altText }: { spec: GeometrySpec; altText: string }) {
  const points  = Array.isArray(spec?.points)   ? spec.points   : [];
  const segs    = Array.isArray(spec?.segments) ? spec.segments : [];
  const polys   = Array.isArray(spec?.polygons) ? spec.polygons : [];
  const circles = Array.isArray(spec?.circles)  ? spec.circles  : [];
  const arcs    = Array.isArray(spec?.arcs)     ? spec.arcs     : [];

  // O(1) lookup by id for segment/polygon resolution.
  const byId = new Map<string, GeoPoint>();
  for (const p of points) if (p && typeof p.id === 'string') byId.set(p.id, p);

  return (
    <svg
      className="diagram__svg diagram__svg--geometry"
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={altText}
    >
      {/* Polygons render first so segments + points sit on top */}
      {polys.map((poly, i) => {
        const pts = (poly.points || [])
          .map(id => byId.get(id))
          .filter((p): p is GeoPoint => !!p);
        if (pts.length < 3) return null;
        const d = pts.map(p => `${p.x},${p.y}`).join(' ');
        return (
          <polygon
            key={`poly-${i}`}
            points={d}
            fill={poly.fill ?? '#eef2ff'}
            stroke={poly.stroke ?? '#4f46e5'}
            strokeWidth={1.2}
            opacity={0.75}
          />
        );
      })}

      {/* Circles */}
      {circles.map((c, i) => {
        if (!Number.isFinite(c.cx) || !Number.isFinite(c.cy) || !(c.r > 0)) return null;
        return (
          <g key={`c-${i}`}>
            <circle cx={c.cx} cy={c.cy} r={c.r} fill="none" stroke="#4f46e5" strokeWidth={1.4} />
            <circle cx={c.cx} cy={c.cy} r={2} fill="#4f46e5" />
            {c.label && (
              <text x={c.cx + 6} y={c.cy - 6} fontSize={12} fill="currentColor">
                {c.label}
              </text>
            )}
          </g>
        );
      })}

      {/* Arcs (used for angle markers) */}
      {arcs.map((a, i) => {
        if (!Number.isFinite(a.cx) || !Number.isFinite(a.cy) || !(a.r > 0)) return null;
        const path = arcPath(a.cx, a.cy, a.r, a.startDeg, a.endDeg);
        const midDeg = (a.startDeg + a.endDeg) / 2;
        const lx = a.cx + (a.r + 10) * Math.cos(toRad(midDeg));
        const ly = a.cy - (a.r + 10) * Math.sin(toRad(midDeg)); // y flipped
        return (
          <g key={`a-${i}`}>
            <path d={path} fill="none" stroke="#9333ea" strokeWidth={1.2} />
            {a.label && (
              <text x={lx} y={ly} fontSize={11} fill="currentColor" textAnchor="middle">
                {a.label}
              </text>
            )}
          </g>
        );
      })}

      {/* Segments */}
      {segs.map((s, i) => {
        const a = byId.get(s.from);
        const b = byId.get(s.to);
        if (!a || !b) return null;
        const mx = (a.x + b.x) / 2;
        const my = (a.y + b.y) / 2;
        return (
          <g key={`s-${i}`}>
            <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke="#1f2937" strokeWidth={1.6} />
            {s.label && (
              <text x={mx} y={my - 4} fontSize={11} fill="currentColor" textAnchor="middle">
                {s.label}
              </text>
            )}
          </g>
        );
      })}

      {/* Points + labels (top of z-stack) */}
      {points.map((p, i) => {
        if (!Number.isFinite(p?.x) || !Number.isFinite(p?.y)) return null;
        return (
          <g key={`p-${i}`}>
            <circle cx={p.x} cy={p.y} r={3.2} fill="#1f2937" />
            {p.label && (
              <text
                x={p.x + 6}
                y={p.y - 6}
                fontSize={12}
                fontWeight={600}
                fill="currentColor"
              >
                {p.label}
              </text>
            )}
          </g>
        );
      })}
    </svg>
  );
}

/** Degrees → radians. */
function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/**
 * Build an SVG path for a circular arc.
 *
 * Math convention in: 0° = east, increasing counter-clockwise.
 * SVG y-axis is flipped, so we negate sin() and flip the sweep flag.
 */
function arcPath(cx: number, cy: number, r: number, startDeg: number, endDeg: number): string {
  const s = toRad(startDeg);
  const e = toRad(endDeg);
  const x1 = cx + r * Math.cos(s);
  const y1 = cy - r * Math.sin(s);
  const x2 = cx + r * Math.cos(e);
  const y2 = cy - r * Math.sin(e);
  const delta = ((endDeg - startDeg) + 360) % 360;
  const largeArc = delta > 180 ? 1 : 0;
  // sweepFlag=0 → counter-clockwise in math, but SVG y is flipped, so we use 0
  return `M ${x1} ${y1} A ${r} ${r} 0 ${largeArc} 0 ${x2} ${y2}`;
}
