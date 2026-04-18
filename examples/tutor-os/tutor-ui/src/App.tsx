import { lazy, Suspense, useEffect } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { RouteFallback } from '@/components/ui/RouteFallback';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { useSettings } from '@/store/settings';

// Home is eager — it's the most common first paint, no point hiding it
// behind a skeleton. Everything else is split out so the initial JS bundle
// only carries what's needed to render the landing screen + shell.
import { HomePage } from '@/features/home/HomePage';

const TutorPage     = lazy(() => import('@/features/tutor/TutorPage').then(m => ({ default: m.TutorPage })));
const SubjectsPage  = lazy(() => import('@/features/subjects/SubjectsPage').then(m => ({ default: m.SubjectsPage })));
const CoursePage    = lazy(() => import('@/features/subjects/CoursePage').then(m => ({ default: m.CoursePage })));
const ChapterPlayer = lazy(() => import('@/features/subjects/ChapterPlayer').then(m => ({ default: m.ChapterPlayer })));
const NotesPage     = lazy(() => import('@/features/notes/NotesPage').then(m => ({ default: m.NotesPage })));
const NoteEditor    = lazy(() => import('@/features/notes/NoteEditor').then(m => ({ default: m.NoteEditor })));
const RoughWorkPage = lazy(() => import('@/features/roughwork/RoughWorkPage').then(m => ({ default: m.RoughWorkPage })));
const PracticePage  = lazy(() => import('@/features/practice/PracticePage').then(m => ({ default: m.PracticePage })));
const QuizPage      = lazy(() => import('@/features/quiz/QuizPage').then(m => ({ default: m.QuizPage })));
const ProgressPage  = lazy(() => import('@/features/progress/ProgressPage').then(m => ({ default: m.ProgressPage })));
const PlanPage      = lazy(() => import('@/features/plan/PlanPage').then(m => ({ default: m.PlanPage })));
const LibraryPage   = lazy(() => import('@/features/library/LibraryPage').then(m => ({ default: m.LibraryPage })));
const CommunityPage = lazy(() => import('@/features/community/CommunityPage').then(m => ({ default: m.CommunityPage })));
const SettingsPage  = lazy(() => import('@/features/settings/SettingsPage').then(m => ({ default: m.SettingsPage })));

/**
 * App — top-level router and theme bootstrapper.
 *
 * The route table mirrors the learner's mental model: Home is the calm
 * starting point, Tutor is the active "do work" canvas, everything else
 * is reachable from the side rail or the ⌘K command palette.
 *
 * Every non-home route is loaded on demand via React.lazy + Suspense, so
 * the initial JS payload only carries Home + the shell.
 */
export function App() {
  const theme     = useSettings(s => s.theme);
  const mode      = useSettings(s => s.mode);
  const density   = useSettings(s => s.density);
  const fontScale = useSettings(s => s.fontScale);

  // Reflect settings onto :root so every CSS rule (token files + base.css)
  // can react in one paint. We never put theme classes on individual
  // components — single source of truth lives on <html>.
  useEffect(() => {
    const root = document.documentElement;
    root.setAttribute('data-theme', theme);
    if (mode) root.setAttribute('data-mode', mode);
    else root.removeAttribute('data-mode');
    root.setAttribute('data-density', density);
    root.style.setProperty('--font-scale', String(fontScale));
  }, [theme, mode, density, fontScale]);

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />

        {/* Lazy routes — wrapped in a single Suspense per route so the */}
        {/* shell (topbar, sidenav) stays mounted while the chunk loads. */}
        <Route path="tutor"               element={<Lazy><TutorPage /></Lazy>} />
        <Route path="tutor/:sessionId"    element={<Lazy><TutorPage /></Lazy>} />

        <Route path="subjects"            element={<Lazy><SubjectsPage /></Lazy>} />
        <Route path="subjects/:subjectId" element={<Lazy><SubjectsPage /></Lazy>} />
        <Route path="courses/:courseId"   element={<Lazy><CoursePage /></Lazy>} />
        <Route path="chapters/:chapterId" element={<Lazy><ChapterPlayer /></Lazy>} />

        <Route path="notes"               element={<Lazy><NotesPage /></Lazy>} />
        <Route path="notes/:noteId"       element={<Lazy><NoteEditor /></Lazy>} />

        <Route path="scratch"             element={<Lazy><RoughWorkPage /></Lazy>} />
        <Route path="practice"            element={<Lazy><PracticePage /></Lazy>} />
        <Route path="quiz/:quizId"        element={<Lazy><QuizPage /></Lazy>} />

        <Route path="progress"            element={<Lazy><ProgressPage /></Lazy>} />
        <Route path="plan"                element={<Lazy><PlanPage /></Lazy>} />
        <Route path="library"             element={<Lazy><LibraryPage /></Lazy>} />
        <Route path="community"           element={<Lazy><CommunityPage /></Lazy>} />
        <Route path="settings"            element={<Lazy><SettingsPage /></Lazy>} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

/**
 * Suspense + ErrorBoundary per route.
 *
 * Each lazy route gets its own ErrorBoundary keyed on the pathname so:
 *  - A single broken route can't blank the shell.
 *  - Navigating away resets the boundary (key change → fresh tree),
 *    so a "Try again" isn't required on every nav.
 */
function Lazy({ children }: { children: React.ReactNode }) {
  const { pathname } = useLocation();
  return (
    <ErrorBoundary key={pathname}>
      <Suspense fallback={<RouteFallback />}>{children}</Suspense>
    </ErrorBoundary>
  );
}
