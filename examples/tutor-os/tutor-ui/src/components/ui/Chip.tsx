import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon?: ReactNode;
  accent?: boolean;
}

export function Chip({ icon, accent, className, children, ...rest }: ChipProps) {
  const cls = ['chip', accent && 'chip--accent', className].filter(Boolean).join(' ');
  return (
    <button className={cls} type="button" {...rest}>
      {icon && <span className="chip__icon">{icon}</span>}
      {children}
    </button>
  );
}
