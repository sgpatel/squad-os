import type { ReactNode, HTMLAttributes } from 'react';

/* Tiny primitives that don't deserve their own files. */

export function Kbd({ children }: { children: ReactNode }) {
  return <span className="kbd">{children}</span>;
}

export function Avatar({ initials, label }: { initials: string; label?: string }) {
  return (
    <span className="topbar__avatar" aria-label={label ?? initials}>
      {initials}
    </span>
  );
}

export function ProgressBar({ value, max = 1, className }: { value: number; max?: number; className?: string }) {
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  return (
    <div className={['xp__bar', className].filter(Boolean).join(' ')} role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={max}>
      <div className="xp__fill" style={{ width: `${pct}%`, ['--xp' as any]: `${pct}%` }} />
    </div>
  );
}

export function Tag({ children, kind, className }: { children: ReactNode; kind?: 'success' | 'warn' | 'danger' | 'info'; className?: string }) {
  const cls = ['tag', kind && `tag--${kind}`, className].filter(Boolean).join(' ');
  return <span className={cls}>{children}</span>;
}

export function SectionLabel({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={['section-label', className].filter(Boolean).join(' ')}>{children}</p>;
}

export function Divider(props: HTMLAttributes<HTMLDivElement>) {
  return <div className="divider" {...props} />;
}
