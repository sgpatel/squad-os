import { useEffect, useRef, useState } from 'react';
import { Eraser, Pen, Undo, Trash2, Download } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useLocalStorage } from '@/hooks/useLocalStorage';

type Tool = 'pen' | 'eraser';
interface Stroke {
  tool: Tool;
  color: string;
  size: number;
  points: { x: number; y: number }[];
}

const COLORS = ['#1a1a1a', '#dc2626', '#2563eb', '#16a34a', '#d97706', '#8b5cf6'];
const SIZES  = [2, 4, 8, 14];

/**
 * Rough work — canvas scratchpad.
 *
 * A learner-grade whiteboard for working out math problems, drawing
 * diagrams, sketching free-body diagrams, etc. Strokes are stored as
 * vector paths so we can undo and re-render on resize.
 *
 * Persisted to localStorage so the workings survive a refresh.
 */
export function RoughWorkPage() {
  const canvasRef  = useRef<HTMLCanvasElement>(null);
  const drawing    = useRef(false);
  const currentStroke = useRef<Stroke | null>(null);

  const [strokes, setStrokes] = useLocalStorage<Stroke[]>('tutoros.scratch.strokes', []);
  const [tool,    setTool]    = useState<Tool>('pen');
  const [color,   setColor]   = useState<string>(COLORS[0]!);
  const [size,    setSize]    = useState<number>(SIZES[1]!);

  // Render strokes onto the canvas (full repaint, simple + correct).
  const repaint = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width  = rect.width  * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, rect.width, rect.height);
    for (const s of strokes) drawStroke(ctx, s);
    if (currentStroke.current) drawStroke(ctx, currentStroke.current);
  };

  useEffect(() => {
    repaint();
    const onResize = () => repaint();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [strokes]);

  const localPoint = (e: React.PointerEvent) => {
    const rect = canvasRef.current!.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  };

  const onDown = (e: React.PointerEvent) => {
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    drawing.current = true;
    currentStroke.current = {
      tool, color, size,
      points: [localPoint(e)]
    };
    repaint();
  };
  const onMove = (e: React.PointerEvent) => {
    if (!drawing.current || !currentStroke.current) return;
    currentStroke.current.points.push(localPoint(e));
    repaint();
  };
  const onUp = () => {
    if (currentStroke.current) {
      setStrokes([...strokes, currentStroke.current]);
    }
    drawing.current = false;
    currentStroke.current = null;
  };

  const undo = () => setStrokes(strokes.slice(0, -1));
  const clear = () => { if (strokes.length && confirm('Clear the page?')) setStrokes([]); };

  const exportPng = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const link = document.createElement('a');
    link.download = 'rough-work.png';
    link.href = canvas.toDataURL('image/png');
    link.click();
  };

  return (
    <div className="canvas-page">
      <div className="canvas-toolbar">
        <Button variant={tool === 'pen' ? 'primary' : 'default'} size="sm"
          leading={<Pen size={12} />} onClick={() => setTool('pen')}>Pen</Button>
        <Button variant={tool === 'eraser' ? 'primary' : 'default'} size="sm"
          leading={<Eraser size={12} />} onClick={() => setTool('eraser')}>Eraser</Button>

        <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginLeft: 'var(--space-3)' }}>
          {COLORS.map(c => (
            <button
              key={c}
              className={'swatch' + (c === color ? ' is-active' : '')}
              style={{ background: c }}
              onClick={() => { setColor(c); setTool('pen'); }}
              aria-label={`Color ${c}`}
            />
          ))}
        </div>

        <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginLeft: 'var(--space-3)' }}>
          {SIZES.map(s => (
            <button
              key={s}
              onClick={() => setSize(s)}
              aria-label={`Size ${s}`}
              style={{
                width: 28, height: 28, borderRadius: '50%',
                display: 'grid', placeItems: 'center',
                border: s === size ? '2px solid var(--color-text)' : '1px solid var(--color-border)',
                background: 'var(--color-surface)', cursor: 'pointer'
              }}
            >
              <span style={{ display: 'block', width: s, height: s, borderRadius: '50%', background: color }} />
            </button>
          ))}
        </div>

        <span style={{ flex: 1 }} />
        <Button variant="ghost" size="sm" leading={<Undo size={12} />} onClick={undo} disabled={strokes.length === 0}>Undo</Button>
        <Button variant="ghost" size="sm" leading={<Trash2 size={12} />} onClick={clear} disabled={strokes.length === 0}>Clear</Button>
        <Button variant="ghost" size="sm" leading={<Download size={12} />} onClick={exportPng} disabled={strokes.length === 0}>Export</Button>
      </div>

      <div className="canvas-host">
        <canvas
          ref={canvasRef}
          onPointerDown={onDown}
          onPointerMove={onMove}
          onPointerUp={onUp}
          onPointerCancel={onUp}
          style={{ touchAction: 'none' }}
        />
      </div>
    </div>
  );
}

function drawStroke(ctx: CanvasRenderingContext2D, s: Stroke) {
  if (s.points.length === 0) return;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.lineWidth = s.size;
  ctx.globalCompositeOperation = s.tool === 'eraser' ? 'destination-out' : 'source-over';
  ctx.strokeStyle = s.color;
  ctx.beginPath();
  ctx.moveTo(s.points[0]!.x, s.points[0]!.y);
  for (let i = 1; i < s.points.length; i++) {
    ctx.lineTo(s.points[i]!.x, s.points[i]!.y);
  }
  ctx.stroke();
  ctx.globalCompositeOperation = 'source-over';
}
