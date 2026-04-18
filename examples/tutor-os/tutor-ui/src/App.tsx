import { useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { useSettings } from '@/store/settings';

import { HomePage } from '@/features/home/HomePage';
import { TutorPage } from '@/features/tutor/TutorPage';
import { SubjectsPage } from '@/features/subjects/SubjectsPage';
import { CoursePage } from '@/features/subjects/CoursePage';
import { ChapterPlayer } from '@/features/subjects/ChapterPlayer';
import { NotesPage } from '@/features/notes/NotesPage';
import { NoteEditor } from '@/features/notes/NoteEditor';
import { RoughWorkPage } from '@/features/roughwork/RoughWorkPage';
import { PracticePage } from '@/features/practice/PracticePage';
import { QuizPage } from '@/features/quiz/QuizPage';
import { ProgressPage } from '@/features/progress/ProgressPage';
import { PlanPage } from '@/features/plan/PlanPage';
import { LibraryPage } from '@/features/library/LibraryPage';
import { CommunityPage } from '@/features/community/CommunityPage';
import { SettingsPage } from '@/features/settings/SettingsPage';

/**
 * App — top-level router and theme bootstrapper.
 *
 * The route table mirrors the learner's mental model: Home is the calm
 * starting point, Tutor is the active "do work" canvas, everything else
 * is reachable from the side rail or the ⌘K command palette.
 */
export function App() {
  const { theme, mode, density, fontScale } = useSettings();

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
        <Route path="tutor" element={<TutorPage />} />
        <Route path="tutor/:sessionId" element={<TutorPage />} />

        <Route path="subjects" element={<SubjectsPage />} />
        <Route path="subjects/:subjectId" element={<SubjectsPage />} />
        <Route path="courses/:courseId" element={<CoursePage />} />
        <Route path="chapters/:chapterId" element={<ChapterPlayer />} />

        <Route path="notes" element={<NotesPage />} />
        <Route path="notes/:noteId" element={<NoteEditor />} />

        <Route path="scratch" element={<RoughWorkPage />} />
        <Route path="practice" element={<PracticePage />} />
        <Route path="quiz/:quizId" element={<QuizPage />} />

        <Route path="progress" element={<ProgressPage />} />
        <Route path="plan" element={<PlanPage />} />
        <Route path="library" element={<LibraryPage />} />
        <Route path="community" element={<CommunityPage />} />
        <Route path="settings" element={<SettingsPage />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
