import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';

type Variant = 'default' | 'primary' | 'ghost';
type Size    = 'sm' | 'md';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  /** Use when the button is icon-only (renders square). */
  iconOnly?: boolean;
  leading?: ReactNode;
  trailing?: ReactNode;
}

/**
 * Token-driven button. Maps to the `.btn` family in base.css.
 * No variants are added here that aren't in the design system.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'default', size = 'md', iconOnly = false, leading, trailing, className, children, ...rest },
  ref
) {
  const cls = [
    'btn',
    variant === 'primary' && 'btn--primary',
    variant === 'ghost'   && 'btn--ghost',
    size === 'sm'         && 'btn--sm',
    iconOnly              && 'btn--icon',
    className
  ].filter(Boolean).join(' ');

  return (
    <button ref={ref} className={cls} {...rest}>
      {leading}
      {children}
      {trailing}
    </button>
  );
});
