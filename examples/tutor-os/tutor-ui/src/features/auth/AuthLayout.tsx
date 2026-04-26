import type { ReactNode } from 'react';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer?: ReactNode;
}

/**
 * Shared chrome for Login + Register — centred card, brand mark,
 * quiet background. Deliberately minimal; all action lives in the form.
 */
export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps) {
  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="auth-brand__mark" aria-hidden="true">T</span>
          <span className="auth-brand__name">TutorOS</span>
        </div>
        <h1 className="auth-title">{title}</h1>
        <p className="auth-subtitle">{subtitle}</p>
        <div className="auth-body">{children}</div>
        {footer && <div className="auth-footer">{footer}</div>}
      </div>
    </div>
  );
}
