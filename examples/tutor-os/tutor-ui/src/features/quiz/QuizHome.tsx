import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Sparkles, Loader2, Play } from 'lucide-react';
import { useQuizStore } from '@/store/quiz';
import { useWorkspace } from '@/store/workspace';
import { useAuth } from '@/store/auth';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { SectionLabel, Tag } from '@/components/ui/Misc';
import { DEMO_LEARNER_ID } from '@/lib/api';
import { SubTabs } from '@/components/ui/SubTabs';
import { PRACTICE_TABS } from '@/components/layout/hubTabs';

type Difficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'MIXED';

interface QuizHomeProps {
  /** When the learner landed here because /quiz/:id didn't match anything. */
  missingId?: string;
}

/**
 * Quiz home — start screen for the quiz section.
 *
 * Replaces the old hardcoded "qz_photo" landing. Shows:
 *   - a "Create quiz" form (topic + subject + count + difficulty)
 *   - a list of recently generated quizzes the learner can resume
 */
export function QuizHome({ missingId }: QuizHomeProps) {
  const navigate = useNavigate();
  const generate     = useQuizStore(s => s.generate);
  const isGenerating = useQuizStore(s => s.isGenerating);
  const lastError    = useQuizStore(s => s.lastError);
  // Subscribe to the dict and derive the sorted list locally — calling
  // s.recent(8) inside the selector returns a new array every render
  // and trips zustand's snapshot equality check.
  const quizzesDict  = useQuizStore(s => s.quizzes);
  const recent = useMemo(
    () => Object.values(quizzesDict).sort((a, b) => b.id.localeCompare(a.id)).slice(0, 8),
    [quizzesDict]
  );
  const activeSubjectId = useWorkspace(s => s.activeSubjectId);
  const getSubject      = useWorkspace(s => s.getSubject);
  const learnerId = useAuth(s => s.currentUserId) ?? DEMO_LEARNER_ID;

  const defaultSubject = (activeSubjectId && getSubject(activeSubjectId)?.name) || 'General';
  const [topic, setTopic]   = useState('');
  const [subject, setSubject] = useState(defaultSubject);
  const [count, setCount]   = useState(5);
  const [difficulty, setDifficulty] = useState<Difficulty>('MEDIUM');

  const onGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    const t = topic.trim();
    if (!t) return;
    try {
      const quiz = await generate({
        learnerId,
        subject: subject || 'General',
        topic: t,
        count: Math.max(1, Math.min(20, count)),
        difficulty,
      });
      navigate(`/quiz/${quiz.id}`);
    } catch {
      /* surfaced via lastError */
    }
  };

  return (
    <div className="page-react">
      <SubTabs tabs={PRACTICE_TABS} ariaLabel="Practice hub" />
      <header className="page-header">
        <div>
          <h1>Quizzes</h1>
          <p>Generate a fresh quiz calibrated to your level — any topic.</p>
        </div>
      </header>

      {missingId && (
        <div className="card mt-0" style={{ padding: 'var(--space-4)', marginBottom: 'var(--space-5)' }}>
          <div className="row" style={{ alignItems: 'center', gap: 8 }}>
            <Tag kind="warn">Not found</Tag>
            <span className="small muted">Quiz <code>{missingId}</code> hasn't been generated on this device. Create a new one below or pick from recent quizzes.</span>
          </div>
        </div>
      )}

      <form className="card" onSubmit={onGenerate} style={{ padding: 'var(--space-6)' }}>
        <SectionLabel>Create quiz</SectionLabel>
        <p className="muted mt-2" style={{ marginBottom: 'var(--space-4)' }}>
          The tutor generates questions across Bloom's levels and targets your current gaps.
        </p>
        <div className="row" style={{ gap: 'var(--space-3)', flexWrap: 'wrap' }}>
          <Input
            placeholder="Topic (e.g. Photosynthesis, Newton's laws, IAM policies)"
            value={topic}
            onChange={e => setTopic(e.target.value)}
            style={{ flex: '1 1 260px', minWidth: 200 }}
            autoFocus
          />
          <Input
            placeholder="Subject"
            value={subject}
            onChange={e => setSubject(e.target.value)}
            style={{ flex: '0 0 160px' }}
          />
          <Input
            type="number"
            min={1}
            max={20}
            value={count}
            onChange={e => setCount(parseInt(e.target.value, 10) || 5)}
            style={{ flex: '0 0 90px' }}
            aria-label="Question count"
          />
          <select
            className="input"
            value={difficulty}
            onChange={e => setDifficulty(e.target.value as Difficulty)}
            style={{ flex: '0 0 130px' }}
            aria-label="Difficulty"
          >
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
            <option value="MIXED">Mixed</option>
          </select>
          <Button variant="primary" type="submit" disabled={!topic.trim() || isGenerating}>
            {isGenerating
              ? <><Loader2 size={14} className="spin" /> Generating…</>
              : <><Sparkles size={14} /> Generate</>}
          </Button>
        </div>
        {lastError && (
          <p className="small" style={{ color: 'var(--color-danger)', marginTop: 'var(--space-3)' }}>
            Couldn't generate: {lastError}
          </p>
        )}
      </form>

      {recent.length > 0 && (
        <div className="card mt-6" style={{ padding: 'var(--space-6)' }}>
          <SectionLabel>Recent quizzes</SectionLabel>
          <div className="mt-3" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
            {recent.map(q => (
              <button
                key={q.id}
                className="row"
                onClick={() => navigate(`/quiz/${q.id}`)}
                style={{
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: 'var(--space-3)',
                  padding: 'var(--space-3) var(--space-4)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  background: 'transparent',
                  cursor: 'pointer',
                  textAlign: 'left',
                }}
              >
                <div>
                  <div style={{ fontWeight: 500 }}>{q.title}</div>
                  <div className="small muted">{q.questionIds.length} questions · ~{q.estMinutes}m</div>
                </div>
                <Play size={14} />
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
