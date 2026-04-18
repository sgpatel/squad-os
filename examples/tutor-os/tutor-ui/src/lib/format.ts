/* Tiny presentational helpers — kept in one place so components stay declarative. */

export function fmtMinutes(min: number): string {
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}

export function fmtRelative(iso: string | number): string {
  const t = typeof iso === 'number' ? iso : new Date(iso).getTime();
  const diff = Date.now() - t;
  const sec = Math.round(diff / 1000);
  if (sec < 60) return `${sec}s ago`;
  if (sec < 3600) return `${Math.round(sec/60)}m ago`;
  if (sec < 86400) return `${Math.round(sec/3600)}h ago`;
  return `${Math.round(sec/86400)}d ago`;
}

export function fmtPercent(n: number, digits = 0): string {
  return `${(n * 100).toFixed(digits)}%`;
}

export function clamp(n: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, n));
}

// (renderMarkdownLite removed — see components/ui/Markdown.tsx for the
//  real renderer with GFM, math, and code-block support.)
