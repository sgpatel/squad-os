import type { ReactNode } from 'react';

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  hint?: ReactNode;
  action?: ReactNode;
}

/**
 * EmptyState — the "no data yet" card.
 *
 * Three jobs:
 *  1) Tell the learner the page isn't broken — there's just nothing here.
 *  2) Explain what would *make* it non-empty.
 *  3) Offer a single primary action to fix that.
 *
 * Used by NotesPage, LibraryPage, PracticePage, PlanPage, etc.
 */
export function EmptyState({ icon, title, hint, action }: EmptyStateProps) {
  return (
    <div className="empty">
      {icon && <div className="empty__icon" aria-hidden="true">{icon}</div>}
      <div className="empty__title">{title}</div>
      {hint && <div className="empty__hint">{hint}</div>}
      {action}
    </div>
  );
}
