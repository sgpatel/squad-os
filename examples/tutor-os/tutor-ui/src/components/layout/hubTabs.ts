import type { SubTab } from '@/components/ui/SubTabs';

/**
 * Centralised tab definitions for the four merged hubs.
 *
 * The rail collapses sibling routes under a single primary entry; these
 * arrays let each member page render the SAME tab strip — change once,
 * propagate everywhere.
 */
export const LIBRARY_TABS: SubTab[] = [
  { to: '/subjects', label: 'Subjects', end: false }, // matches /subjects/:id too
  { to: '/library',  label: 'Sources' },
];

export const NOTES_TABS: SubTab[] = [
  { to: '/notes',      label: 'Notes', end: false },
  { to: '/cheatsheet', label: 'Cheatsheet' },
  { to: '/scratch',    label: 'Rough work' },
];

export const PRACTICE_TABS: SubTab[] = [
  { to: '/practice', label: 'Flashcards' },
  { to: '/quiz',     label: 'Quizzes', end: false }, // matches /quiz/:id too
];

export const PROGRESS_TABS: SubTab[] = [
  { to: '/progress', label: 'Stats' },
  { to: '/plan',     label: 'Plan' },
];
