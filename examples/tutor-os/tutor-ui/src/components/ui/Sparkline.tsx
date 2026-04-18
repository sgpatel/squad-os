interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  /** CSS color or token reference like `var(--color-accent)`. */
  stroke?: string;
}

/** Pure-SVG mini chart, no library. Used in the Progress dashboard. */
export function Sparkline({ data, width = 120, height = 32, stroke = 'var(--color-accent)' }: SparklineProps) {
  if (data.length === 0) return null;
  const max = Math.max(...data, 1);
  const min = Math.min(...data, 0);
  const range = max - min || 1;
  const stepX = data.length > 1 ? width / (data.length - 1) : 0;
  const points = data.map((v, i) => {
    const x = i * stepX;
    const y = height - ((v - min) / range) * height;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="trend">
      <polyline points={points} fill="none" stroke={stroke} strokeWidth={1.5} strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}
