import { useMemo } from 'react';
import { tryCompile } from '../mathEval';

/**
 * Function plotter — graphs y=f(x), parametric (x(t), y(t)), or polar
 * r(θ) curves from FORMULA STRINGS, sampled and drawn as crisp SVG paths.
 *
 * Why a dedicated renderer (vs. asking the LLM for a Vega `data.values`
 * table): for a pure mathematical function the model is far more reliable
 * emitting the closed-form `sin(x)/x` than 40 hand-computed samples (which
 * it routinely gets wrong). We sample the formula ourselves so the curve
 * is exact and dense, and the spec stays a few bytes.
 *
 * Output is themed SVG (currentColor axes, accent curves) so it sits in a
 * chat bubble and inherits light/dark like the other renderers.
 */

interface Curve { expr?: string; xExpr?: string; yExpr?: string; rExpr?: string; label?: string; color?: string }
export interface Function2DSpec {
  kind?: 'cartesian' | 'parametric' | 'polar';
  curves?: Curve[];
  // single-curve shorthand
  expr?: string; xExpr?: string; yExpr?: string; rExpr?: string;
  xRange?: [number, number];
  yRange?: [number, number];
  tRange?: [number, number];
  thetaRange?: [number, number];
  xLabel?: string;
  yLabel?: string;
  samples?: number;
}

const W = 480, H = 300, PAD = 34;
const PALETTE = ['#6366f1', '#16a34a', '#dc2626', '#d97706', '#0891b2', '#9333ea'];

export function Function2D({ spec, altText }: { spec: Function2DSpec; altText: string }) {
  const model = useMemo(() => buildModel(spec), [spec]);

  if (model.error) {
    return <div className="diagram__fallback diagram__fallback--inline">{altText} <span className="muted">({model.error})</span></div>;
  }
  const { paths, xticks, yticks, sx, sy, x0, y0, legend } = model;

  return (
    <svg className="diagram__svg diagram__svg--fn" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={altText}>
      {/* grid */}
      <g className="diagram__fn-grid">
        {xticks.map((t) => <line key={`gx${t.v}`} x1={t.px} y1={PAD} x2={t.px} y2={H - PAD} />)}
        {yticks.map((t) => <line key={`gy${t.v}`} x1={PAD} y1={t.px} x2={W - PAD} y2={t.px} />)}
      </g>
      {/* axes (only if 0 is in range) */}
      <g className="diagram__fn-axis">
        {y0 != null && <line x1={PAD} y1={y0} x2={W - PAD} y2={y0} />}
        {x0 != null && <line x1={x0} y1={PAD} x2={x0} y2={H - PAD} />}
      </g>
      {/* tick labels */}
      <g className="diagram__fn-label">
        {xticks.map((t) => <text key={`lx${t.v}`} x={t.px} y={H - PAD + 14} textAnchor="middle">{t.label}</text>)}
        {yticks.map((t) => <text key={`ly${t.v}`} x={PAD - 6} y={t.px + 3} textAnchor="end">{t.label}</text>)}
      </g>
      {/* curves */}
      <g fill="none" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round">
        {paths.map((p, i) => <path key={i} d={p.d} stroke={p.color} />)}
      </g>
      {/* legend */}
      {legend.length > 1 && (
        <g className="diagram__fn-legend">
          {legend.map((l, i) => (
            <g key={i} transform={`translate(${W - PAD - 96}, ${PAD + 4 + i * 16})`}>
              <rect x={0} y={-8} width={12} height={3} fill={l.color} />
              <text x={18} y={-4}>{l.label}</text>
            </g>
          ))}
        </g>
      )}
      <text className="diagram__fn-axistitle" x={W / 2} y={H - 4} textAnchor="middle">{sx}</text>
      <text className="diagram__fn-axistitle" x={12} y={H / 2} textAnchor="middle" transform={`rotate(-90 12 ${H / 2})`}>{sy}</text>
    </svg>
  );
}

interface Model {
  error?: string;
  paths: { d: string; color: string }[];
  xticks: { v: number; px: number; label: string }[];
  yticks: { v: number; px: number; label: string }[];
  sx: string; sy: string;
  x0: number | null; y0: number | null;
  legend: { label: string; color: string }[];
}

function buildModel(spec: Function2DSpec): Model {
  const empty: Model = { paths: [], xticks: [], yticks: [], sx: '', sy: '', x0: null, y0: null, legend: [] };
  if (!spec) return { ...empty, error: 'empty spec' };

  const kind = spec.kind ?? (spec.rExpr ? 'polar' : spec.xExpr && spec.yExpr ? 'parametric' : 'cartesian');
  const samples = clampInt(spec.samples ?? 240, 24, 2000);
  const curves: Curve[] = spec.curves?.length
    ? spec.curves
    : [{ expr: spec.expr, xExpr: spec.xExpr, yExpr: spec.yExpr, rExpr: spec.rExpr }];

  // Evaluate every curve into world-space points, tracking the data extent.
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  const series: { pts: (readonly [number, number] | null)[]; color: string; label: string }[] = [];

  for (let ci = 0; ci < curves.length; ci++) {
    const c = curves[ci]!;
    const color = c.color ?? PALETTE[ci % PALETTE.length]!;
    const pts: (readonly [number, number] | null)[] = [];

    if (kind === 'cartesian') {
      const f = tryCompile(c.expr);
      if (!f) return { ...empty, error: `bad expression "${c.expr ?? ''}"` };
      const [a, b] = spec.xRange ?? [-10, 10];
      for (let i = 0; i <= samples; i++) {
        const x = a + ((b - a) * i) / samples;
        const y = safe(() => f({ x }));
        pts.push(y == null ? null : [x, y]);
      }
    } else if (kind === 'parametric') {
      const fx = tryCompile(c.xExpr), fy = tryCompile(c.yExpr);
      if (!fx || !fy) return { ...empty, error: 'bad parametric expression' };
      const [a, b] = spec.tRange ?? [0, Math.PI * 2];
      for (let i = 0; i <= samples; i++) {
        const t = a + ((b - a) * i) / samples;
        const x = safe(() => fx({ t })), y = safe(() => fy({ t }));
        pts.push(x == null || y == null ? null : [x, y]);
      }
    } else { // polar
      const fr = tryCompile(c.rExpr);
      if (!fr) return { ...empty, error: 'bad polar expression' };
      const [a, b] = spec.thetaRange ?? [0, Math.PI * 2];
      for (let i = 0; i <= samples; i++) {
        const th = a + ((b - a) * i) / samples;
        const r = safe(() => fr({ theta: th, t: th }));
        pts.push(r == null ? null : [r * Math.cos(th), r * Math.sin(th)]);
      }
    }

    for (const p of pts) {
      if (!p) continue;
      if (p[0] < minX) minX = p[0]; if (p[0] > maxX) maxX = p[0];
      if (p[1] < minY) minY = p[1]; if (p[1] > maxY) maxY = p[1];
    }
    series.push({ pts, color, label: c.label ?? `f${ci + 1}` });
  }

  if (!Number.isFinite(minX)) return { ...empty, error: 'no finite samples' };

  // Domain: explicit xRange wins for cartesian; otherwise use data extent.
  if (kind === 'cartesian' && spec.xRange) { [minX, maxX] = spec.xRange; }
  // Y window: explicit yRange, else data extent clamped so a 1/x asymptote
  // doesn't squash everything into a flat line.
  let [yLo, yHi] = spec.yRange ?? [minY, maxY];
  if (!spec.yRange) {
    const span = yHi - yLo || 1;
    yLo -= span * 0.06; yHi += span * 0.06;
    // clamp pathological extents (asymptotes) to a sane multiple of the IQR-ish core
    const cap = 1e4;
    yLo = Math.max(yLo, -cap); yHi = Math.min(yHi, cap);
  }
  if (minX === maxX) maxX = minX + 1;
  if (yLo === yHi) yHi = yLo + 1;

  const px = (x: number) => PAD + ((x - minX) / (maxX - minX)) * (W - 2 * PAD);
  const py = (y: number) => H - PAD - ((y - yLo) / (yHi - yLo)) * (H - 2 * PAD);

  const paths = series.map((s) => {
    let d = '', pen = false;
    for (const p of s.pts) {
      if (!p || p[1] < yLo - (yHi - yLo) || p[1] > yHi + (yHi - yLo)) { pen = false; continue; }
      const X = px(p[0]), Y = py(p[1]);
      d += `${pen ? 'L' : 'M'}${X.toFixed(1)} ${Y.toFixed(1)} `;
      pen = true;
    }
    return { d: d.trim(), color: s.color };
  });

  const xticks = ticks(minX, maxX).map((v) => ({ v, px: px(v), label: fmt(v) }));
  const yticks = ticks(yLo, yHi).map((v) => ({ v, px: py(v), label: fmt(v) }));

  return {
    paths, xticks, yticks,
    sx: spec.xLabel ?? (kind === 'polar' ? 'x' : kind === 'parametric' ? 'x(t)' : 'x'),
    sy: spec.yLabel ?? (kind === 'polar' ? 'y' : kind === 'parametric' ? 'y(t)' : 'y'),
    x0: minX <= 0 && maxX >= 0 ? px(0) : null,
    y0: yLo <= 0 && yHi >= 0 ? py(0) : null,
    legend: series.map((s) => ({ label: s.label, color: s.color })),
  };
}

function safe(fn: () => number): number | null {
  try { const v = fn(); return Number.isFinite(v) ? v : null; } catch { return null; }
}
function clampInt(v: number, lo: number, hi: number) { return Math.max(lo, Math.min(hi, Math.round(v))); }
function ticks(lo: number, hi: number): number[] {
  const span = hi - lo;
  if (!(span > 0)) return [lo];
  const raw = span / 6;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  const step = [1, 2, 5, 10].find((s) => s * mag >= raw)! * mag;
  const out: number[] = [];
  for (let v = Math.ceil(lo / step) * step; v <= hi + 1e-9; v += step) out.push(Math.abs(v) < step / 1e6 ? 0 : v);
  return out;
}
function fmt(v: number): string {
  if (v === 0) return '0';
  const a = Math.abs(v);
  if (a >= 1e4 || a < 1e-3) return v.toExponential(1);
  return String(Math.round(v * 1000) / 1000);
}
