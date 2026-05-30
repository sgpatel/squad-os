import { useMemo, type ReactNode } from 'react';

/**
 * Circuit schematic renderer.
 *
 * Auto-layout of an arbitrary netlist is a hard problem LLMs get wrong, so
 * we constrain the contract: the model lists the components IN SERIES ORDER
 * around a single loop, and we walk a rectangular perimeter placing each
 * symbol on the wire. This covers the overwhelming majority of intro
 * physics / EE circuits (a source plus elements in a loop) with a spec the
 * model emits perfectly.
 *
 * Each symbol is drawn in local space (leads along +x, centred at origin)
 * then transformed onto its perimeter slot — so the same symbol code works
 * on the top, side, and bottom edges via rotation. The underlying wire is a
 * full rectangle; a background-matched mask rect under each body breaks the
 * wire so the symbol reads cleanly.
 */

type ElType = 'resistor' | 'battery' | 'cell' | 'capacitor' | 'inductor'
  | 'lamp' | 'bulb' | 'switch' | 'source' | 'ac' | 'wire' | 'ground' | 'voltmeter' | 'ammeter';

interface Element { type: ElType | string; label?: string }
export interface CircuitSpec { elements?: Element[]; title?: string }

const W = 480, H = 300;
const M = 56; // margin → rectangle inset
const L = 46;  // symbol body length

export function Circuit({ spec, altText }: { spec: CircuitSpec; altText: string }) {
  const slots = useMemo(() => layout(spec?.elements ?? []), [spec]);

  if (!slots) {
    return <div className="diagram__fallback diagram__fallback--inline">{altText} <span className="muted">(need at least 2 components)</span></div>;
  }

  return (
    <svg className="diagram__svg diagram__svg--circuit" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={altText}>
      {/* loop wire */}
      <rect className="diagram__circuit-wire" x={M} y={M} width={W - 2 * M} height={H - 2 * M} fill="none" rx={2} />
      {slots.map((s, i) => (
        <g key={i} transform={`translate(${s.x} ${s.y}) rotate(${s.rot})`}>
          {/* mask the wire under the body */}
          <rect className="diagram__circuit-mask" x={-L / 2} y={-13} width={L} height={26} />
          {symbol(s.el.type)}
          {s.el.label && (
            <text className="diagram__circuit-label" x={0} y={s.below ? 26 : -18} textAnchor="middle"
                  transform={`rotate(${-s.rot})`}>{s.el.label}</text>
          )}
        </g>
      ))}
    </svg>
  );
}

interface Slot { x: number; y: number; rot: number; below: boolean; el: Element }

function layout(elements: Element[]): Slot[] | null {
  const els = elements.filter((e) => e && e.type);
  if (els.length < 2) return null;

  // Rectangle perimeter as 4 directed edges (clockwise from top-left).
  const x1 = M, y1 = M, x2 = W - M, y2 = H - M;
  const edges: { from: [number, number]; to: [number, number]; rot: number; below: boolean }[] = [
    { from: [x1, y1], to: [x2, y1], rot: 0,   below: false }, // top  →
    { from: [x2, y1], to: [x2, y2], rot: 90,  below: false }, // right ↓
    { from: [x2, y2], to: [x1, y2], rot: 180, below: true  }, // bottom ←
    { from: [x1, y2], to: [x1, y1], rot: 270, below: false }, // left ↑
  ];
  const lens = edges.map((e) => Math.hypot(e.to[0] - e.from[0], e.to[1] - e.from[1]));
  const perim = lens.reduce((a, b) => a + b, 0);

  const slots: Slot[] = [];
  for (let k = 0; k < els.length; k++) {
    let d = (perim * (k + 0.5)) / els.length; // centre of k-th equal arc
    let ei = 0;
    while (ei < edges.length && d > lens[ei]!) { d -= lens[ei]!; ei++; }
    if (ei >= edges.length) ei = edges.length - 1;
    const e = edges[ei]!;
    const len = lens[ei]!;
    const t = len === 0 ? 0 : d / len;
    slots.push({
      x: e.from[0]! + (e.to[0]! - e.from[0]!) * t,
      y: e.from[1]! + (e.to[1]! - e.from[1]!) * t,
      rot: e.rot,
      below: e.below,
      el: els[k]!,
    });
  }
  return slots;
}

/** Symbol artwork in local space: leads run along x from -L/2 to +L/2. */
function symbol(typeRaw: string): ReactNode {
  const type = typeRaw.toLowerCase();
  const lead = (
    <>
      <line className="diagram__circuit-lead" x1={-L / 2} y1={0} x2={-12} y2={0} />
      <line className="diagram__circuit-lead" x1={12} y1={0} x2={L / 2} y2={0} />
    </>
  );

  switch (type) {
    case 'resistor':
      return (
        <g className="diagram__circuit-sym">
          {lead}
          <polyline points="-12,0 -9,-7 -3,7 3,-7 9,7 12,0" fill="none" />
        </g>
      );
    case 'capacitor':
      return (
        <g className="diagram__circuit-sym">
          <line className="diagram__circuit-lead" x1={-L / 2} y1={0} x2={-4} y2={0} />
          <line className="diagram__circuit-lead" x1={4} y1={0} x2={L / 2} y2={0} />
          <line x1={-4} y1={-11} x2={-4} y2={11} />
          <line x1={4} y1={-11} x2={4} y2={11} />
        </g>
      );
    case 'battery':
    case 'cell':
      return (
        <g className="diagram__circuit-sym">
          <line className="diagram__circuit-lead" x1={-L / 2} y1={0} x2={-8} y2={0} />
          <line className="diagram__circuit-lead" x1={8} y1={0} x2={L / 2} y2={0} />
          <line x1={-8} y1={-12} x2={-8} y2={12} /> {/* long: + */}
          <line x1={-2} y1={-6} x2={-2} y2={6} />   {/* short: − */}
          <line x1={2} y1={-12} x2={2} y2={12} />
          <line x1={8} y1={-6} x2={8} y2={6} />
        </g>
      );
    case 'inductor':
      return (
        <g className="diagram__circuit-sym">
          {lead}
          <path d="M-12,0 a3,3 0 0 1 6,0 a3,3 0 0 1 6,0 a3,3 0 0 1 6,0 a3,3 0 0 1 6,0" fill="none" />
        </g>
      );
    case 'lamp':
    case 'bulb':
      return (
        <g className="diagram__circuit-sym">
          {lead}
          <circle cx={0} cy={0} r={11} fill="none" />
          <line x1={-7.8} y1={-7.8} x2={7.8} y2={7.8} />
          <line x1={-7.8} y1={7.8} x2={7.8} y2={-7.8} />
        </g>
      );
    case 'switch':
      return (
        <g className="diagram__circuit-sym">
          {lead}
          <circle cx={-12} cy={0} r={2} className="diagram__circuit-dot" />
          <circle cx={12} cy={0} r={2} className="diagram__circuit-dot" />
          <line x1={-12} y1={0} x2={9} y2={-9} />
        </g>
      );
    case 'source':
    case 'ac':
      return (
        <g className="diagram__circuit-sym">
          {lead}
          <circle cx={0} cy={0} r={12} fill="none" />
          <path d="M-7,0 a3.5,3.5 0 0 1 7,0 a3.5,3.5 0 0 0 7,0" fill="none" />
        </g>
      );
    case 'voltmeter':
    case 'ammeter':
      return (
        <g className="diagram__circuit-sym">
          {lead}
          <circle cx={0} cy={0} r={12} fill="none" />
          <text x={0} y={4} textAnchor="middle" className="diagram__circuit-meter">{type === 'voltmeter' ? 'V' : 'A'}</text>
        </g>
      );
    case 'ground':
      return (
        <g className="diagram__circuit-sym">
          {lead}
          <line x1={0} y1={0} x2={0} y2={8} />
          <line x1={-9} y1={8} x2={9} y2={8} />
          <line x1={-6} y1={12} x2={6} y2={12} />
          <line x1={-3} y1={16} x2={3} y2={16} />
        </g>
      );
    default: // wire / unknown → straight through
      return <line className="diagram__circuit-lead" x1={-L / 2} y1={0} x2={L / 2} y2={0} />;
  }
}
