import { Link } from 'react-router-dom';
import * as Icons from 'lucide-react';
import { useWorkspace } from '@/store/workspace';
import { fmtMinutes } from '@/lib/format';

/**
 * Subjects browser — workspace-scoped grid of subject cards.
 * Clicking a subject jumps to its first course.
 */
export function SubjectsPage() {
  const { workspaceSubjects, courses, chapters } = useWorkspace();
  const subjects = workspaceSubjects();

  return (
    <div className="page-react">
      <header className="page-header">
        <div>
          <h1>Subjects</h1>
          <p>Pick a track. Each subject has one or more courses, broken into chapters.</p>
        </div>
      </header>

      <div className="subj-grid">
        {subjects.map(s => {
          const Icon = (Icons as any)[s.icon] ?? Icons.Book;
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
            <Link
              key={s.id}
              to={subjCourses[0] ? `/courses/${subjCourses[0].id}` : '#'}
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
            </Link>
          );
        })}
      </div>
    </div>
  );
}
