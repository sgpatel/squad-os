import { Link, useParams, Navigate } from 'react-router-dom';
import { Clock, ArrowLeft, Play, CheckCircle2 } from 'lucide-react';
import { useWorkspace } from '@/store/workspace';
import { useSettings } from '@/store/settings';
import { Button } from '@/components/ui/Button';
import { fmtMinutes, fmtPercent } from '@/lib/format';

/**
 * Course page — list of chapters with mastery + estimate.
 *
 * For Atlas (middle-school theme) the same data is rendered as a
 * Journey Map (zig-zag of node bubbles) below the standard list,
 * gated by the `--has-map-path` flag baked into the theme.
 */
export function CoursePage() {
  const { courseId } = useParams<{ courseId: string }>();
  const { courses, subjects, chapters } = useWorkspace();
  const { theme } = useSettings();

  const course = courses.find(c => c.id === courseId);
  if (!course) return <Navigate to="/subjects" replace />;
  const subject = subjects.find(s => s.id === course.subjectId);
  const courseChapters = course.chapterIds
    .map(id => chapters.find(c => c.id === id))
    .filter((c): c is NonNullable<typeof c> => Boolean(c));

  const totalMastery = courseChapters.reduce((s, c) => s + c.mastery, 0) / Math.max(1, courseChapters.length);

  return (
    <div className="page-react">
      <Link to="/subjects" className="small muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginBottom: 12 }}>
        <ArrowLeft size={12} /> Subjects
      </Link>
      <header className="page-header">
        <div>
          <h1>{course.name}</h1>
          <p>{subject?.name} · {fmtMinutes(course.estMinutes)} · Mastery {fmtPercent(totalMastery)}</p>
        </div>
        <div className="page-actions">
          <Button variant="primary" leading={<Play size={14} />}
            onClick={() => { /* would resume next-best chapter */ }}>
            Resume
          </Button>
        </div>
      </header>

      <p className="muted">{course.description}</p>

      <section className="card mt-7" style={{ padding: 0, overflow: 'hidden' }}>
        <ul className="chapter-list">
          {courseChapters.map((ch) => (
            <li key={ch.id}>
              <Link to={`/chapters/${ch.id}`} className="chapter-item">
                <span className="chapter-item__num">
                  {ch.mastery >= 0.85 ? <CheckCircle2 size={14} color="var(--color-success)" /> : ch.index}
                </span>
                <div>
                  <div className="chapter-item__title">{ch.name}</div>
                  <div className="chapter-item__meta">
                    {ch.blurb} · <Clock size={10} style={{ display: 'inline', verticalAlign: -1 }} /> {fmtMinutes(ch.estMinutes)}
                  </div>
                </div>
                <div className="chapter-item__bar">
                  <span style={{ ['--m' as any]: `${ch.mastery * 100}%`, width: `${ch.mastery * 100}%` }} />
                </div>
                <div className="chapter-item__meta" style={{ minWidth: 48, textAlign: 'right' }}>
                  {fmtPercent(ch.mastery)}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      {/* Atlas-only Journey Map view (CSS-gated by --has-map-path) */}
      {theme === 'atlas' && (
        <section className="journey mt-9">
          <h3 style={{ marginBottom: 'var(--space-5)' }}>Your journey</h3>
          <div className="journey__path">
            {courseChapters.map((ch) => (
              <Link
                key={ch.id}
                to={`/chapters/${ch.id}`}
                className={
                  'journey__node' +
                  (ch.mastery >= 0.85 ? ' is-done' : '') +
                  (ch.mastery > 0 && ch.mastery < 0.85 ? ' is-current' : '')
                }
              >
                <span className="journey__badge">{ch.index}</span>
                <div>
                  <div style={{ fontWeight: 600 }}>{ch.name}</div>
                  <div className="small muted">{fmtPercent(ch.mastery)} mastered</div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
