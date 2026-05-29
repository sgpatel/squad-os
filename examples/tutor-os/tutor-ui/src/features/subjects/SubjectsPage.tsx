import { useNavigate } from 'react-router-dom';
import { Atom, Book, Cloud, Code, FlaskConical, Leaf, Sigma, BookOpen } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useWorkspace } from '@/store/workspace';
import { usePipeline } from '@/store/pipeline';
import { EmptyState } from '@/components/ui/EmptyState';
import { fmtMinutes } from '@/lib/format';
import { SubTabs } from '@/components/ui/SubTabs';
import { LIBRARY_TABS } from '@/components/layout/hubTabs';

// Explicit icon lookup. We used to do `import * as Icons from 'lucide-react'`
// + `Icons[s.icon]`, which defeats tree-shaking — Rollup ships the entire
// 750 kB lucide bundle. The seed data uses a tiny fixed set of names, so
// hand-maintaining this map keeps the route chunk small (~2 kB instead).
const SUBJECT_ICONS: Record<string, LucideIcon> = {
  Atom, Book, Cloud, Code, FlaskConical, Leaf, Sigma
};

/**
 * Subjects browser — workspace-scoped grid of subject cards.
 * Clicking a subject jumps to its first course.
 */
export function SubjectsPage() {
  const { workspaceSubjects, courses, chapters, setActiveSubject } = useWorkspace();
  const { reset: resetPipeline } = usePipeline();
  const navigate = useNavigate();
  const subjects = workspaceSubjects();

  const handleSubjectClick = (subjectId: string, firstCourseId?: string) => {
    // Set the active subject and reset the session so a new one is created
    setActiveSubject(subjectId);
    resetPipeline();
    
    // Navigate to the course or tutor page
    if (firstCourseId) {
      navigate(`/courses/${firstCourseId}`);
    } else {
      navigate('/tutor');
    }
  };

  return (
    <div className="page-react">
      <SubTabs tabs={LIBRARY_TABS} ariaLabel="Library hub" />
      <header className="page-header">
        <div>
          <h1>Subjects</h1>
          <p>Pick a track. Each subject has one or more courses, broken into chapters.</p>
        </div>
      </header>

      {subjects.length === 0 ? (
        <EmptyState
          icon={<BookOpen size={20} />}
          title="No subjects in this workspace"
          hint="Switch workspaces from the topbar, or ask the tutor to seed a syllabus from a PDF or topic name."
        />
      ) : (
      <div className="subj-grid">
        {subjects.map(s => {
          const Icon = SUBJECT_ICONS[s.icon] ?? Book;
          const subjCourses = courses.filter(c => c.subjectId === s.id);
          const totalChapters = subjCourses.reduce((sum, c) => sum + c.chapterIds.length, 0);
          const totalMinutes  = subjCourses.reduce((sum, c) => sum + c.estMinutes,        0);
          const allChapters   = subjCourses.flatMap(c => c.chapterIds.map(id => chapters.find(ch => ch.id === id)).filter(Boolean));
          const avgMastery    = allChapters.length
            ? allChapters.reduce((sum, ch) => sum + (ch?.mastery ?? 0), 0) / allChapters.length
            : 0;

          // Tint the icon background with the subject's brand color.
          const tint = s.color + '22';
          return (
            <button
              key={s.id}
              onClick={() => handleSubjectClick(s.id, subjCourses[0]?.id)}
              className="subj-card"
              style={{ ['--subj-tint' as any]: tint, ['--subj-color' as any]: s.color }}
            >
              <span className="subj-card__icon"><Icon size={18} /></span>
              <div>
                <div className="subj-card__name">{s.name}</div>
                <div className="subj-card__meta">
                  {subjCourses.length} course{subjCourses.length === 1 ? '' : 's'} ·
                  {' '}{totalChapters} chapter{totalChapters === 1 ? '' : 's'} ·
                  {' '}{fmtMinutes(totalMinutes)}
                </div>
              </div>
              <p className="muted small">{s.blurb}</p>
              <div className="chapter-item__bar" aria-label={`Mastery ${(avgMastery * 100).toFixed(0)}%`}>
                <span style={{ ['--m' as any]: `${avgMastery * 100}%`, width: `${avgMastery * 100}%` }} />
              </div>
            </button>
          );
        })}
      </div>
      )}
    </div>
  );
}
