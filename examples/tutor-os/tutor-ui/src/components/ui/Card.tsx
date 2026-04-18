import type { HTMLAttributes, ReactNode } from 'react';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Show a sunken background instead of the default surface. */
  inset?: boolean;
  flat?: boolean;
}

export function Card({ inset, flat, className, children, ...rest }: CardProps) {
  const cls = [
    'card',
    inset && 'card--inset',
    flat  && 'card--flat',
    className
  ].filter(Boolean).join(' ');
  return <div className={cls} {...rest}>{children}</div>;
}

export function CardMeta({ children }: { children: ReactNode }) {
  return <p className="card__meta">{children}</p>;
}
export function CardTitle({ children }: { children: ReactNode }) {
  return <h3 className="card__title">{children}</h3>;
}
