import { Link, useParams, Navigate, useNavigate } from 'react-router-dom';
import { ArrowLeft, MessageSquare, Layers, ListChecks, FileText } from 'lucide-react';
import { useWorkspace } from '@/store/workspace';
import { useNotes } from '@/store/notes';
import { usePipeline } from '@/store/pipeline';
import { Button } from '@/components/ui/Button';
import { Markdown } from '@/components/ui/Markdown';
import { Tag, SectionLabel } from '@/components/ui/Misc';
import { fmtMinutes, fmtPercent } from '@/lib/format';

/**
 * Chapter Player — read · do · check.
 *
 *   Header: title, mastery, est time
 *   Body:   one card per topic, each topic shows its content paragraphs
 *           and concept chips with mastery
 *   Footer: 4 actions — Ask tutor about this · Make a note · Practice · Quiz
 */
export function ChapterPlayer() {
  const { chapterId } = useParams<{ chapterId: string }>();
  const navigate = useNavigate();
  const { chapters, courses, topics, concepts } = useWorkspace();
  const createNote  = useNotes(s => s.create);
  const startTutor  = usePipeline(s => s.start);

  const chapter = chapters.find(c => c.id === chapterId);
  if (!chapter) return <Navigate to="/subjects" replace />;
  const course = courses.find(c => c.id === chapter.courseId);
  const chapterTopics = chapter.topicIds
    .map(id => topics.find(t => t.id === id))
    .filter((t): t is NonNullable<typeof t> => Boolean(t));

  const askTutor = () => {
    void startTutor(`Explain "${chapter.name}" — give me an outline I can follow.`);
    navigate('/tutor');
  };

  const makeNote = () => {
    const n = createNote({
      title: chapter.name,
      body: `# ${chapter.name}\n\n${chapter.blurb}\n\n— `,
      chapterId: chapter.id,
      tags: ['from-chapter']
    });
    navigate(`/notes/${n.id}`);
  };

  return (
    <div className="page-react">
      <Link to={course ? `/courses/${course.id}` : '/subjects'} className="small muted"
        style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginBottom: 12 }}>
        <ArrowLeft size={12} /> {course?.name ?? 'Back'}
      </Link>
      <header className="page-header">
        <div>
          <h1>{chapter.name}</h1>
          <p>{chapter.blurb} · {fmtMinutes(chapter.estMinutes)} · Mastery {fmtPercent(chapter.mastery)}</p>
        </div>
        <div className="page-actions">
          <Button variant="primary" leading={<MessageSquare size={14} />} onClick={askTutor}>Ask tutor</Button>
        </div>
      </header>

      {chapterTopics.map(topic => (
        <article key={topic.id} className="card mt-5">
          <SectionLabel>Topic</SectionLabel>
          <h2 style={{ fontSize: 'var(--text-xl)', marginBottom: 'var(--space-4)' }}>{topic.name}</h2>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            {/* Topic content is markdown — supports **bold**, `code`, $math$,
                tables, lists, etc. Each paragraph entry in the seed is rendered
                independently so authoring stays paragraph-oriented. */}
            <Markdown>{topic.content.join('\n\n')}</Markdown>
          </div>

          {topic.conceptIds.length > 0 && (
            <div className="row mt-5">
              <span className="small muted">Concepts:</span>
              {topic.conceptIds.map(id => {
                const k = concepts.find(c => c.id === id);
                if (!k) return null;
                const kind: 'success' | 'warn' | 'danger' =
                  k.mastery >= 0.7 ? 'success' :
                  k.mastery >= 0.3 ? 'warn'    : 'danger';
                return <Tag key={id} kind={kind}>{k.name} · {fmtPercent(k.mastery)}</Tag>;
              })}
            </div>
          )}
        </article>
      ))}

      <section className="card mt-7">
        <SectionLabel>What's next?</SectionLabel>
        <div className="row" style={{ gap: 'var(--space-3)' }}>
          <Button leading={<MessageSquare size={14} />} onClick={askTutor}>Ask tutor about this</Button>
          <Button leading={<FileText size={14} />} onClick={makeNote}>Make a note</Button>
          <Button leading={<Layers size={14} />} onClick={() => navigate('/practice')}>Practice flashcards</Button>
          <Button leading={<ListChecks size={14} />} onClick={() => navigate('/quiz')}>Take quiz</Button>
        </div>
      </section>
    </div>
  );
}
