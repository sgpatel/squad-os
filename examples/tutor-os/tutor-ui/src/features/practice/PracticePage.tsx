import { useMemo, useState } from 'react';
import { Sparkles, Loader2 } from 'lucide-react';
import { usePractice } from '@/store/practice';
import { useWorkspace } from '@/store/workspace';
import { useAuth } from '@/store/auth';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { SectionLabel, Tag } from '@/components/ui/Misc';
import { DEMO_LEARNER_ID } from '@/lib/api';
import { SubTabs } from '@/components/ui/SubTabs';
import { PRACTICE_TABS } from '@/components/layout/hubTabs';

/**
 * Practice — flashcard reviewer with SM-2-style scheduling.
 *
 * One card at a time. Tap (or press Space) to flip. Then grade
 * yourself: Again · Hard · Good · Easy → next card.
 *
 * When the queue is empty, a "Generate practice" form prompts the
 * QuizAgent backend (or local mock) to synthesise a fresh batch of
 * cards from a topic the learner supplies.
 */
export function PracticePage() {
  const cards   = usePractice(s => s.cards);
  const cursor  = usePractice(s => s.cursor);
  const flipped = usePractice(s => s.flipped);
  const flip    = usePractice(s => s.flip);
  const grade   = usePractice(s => s.grade);
  const generate    = usePractice(s => s.generate);
  const isGenerating = usePractice(s => s.isGenerating);
  const lastError   = usePractice(s => s.lastError);
  const concept = useWorkspace(s => s.getConcept);
  const activeSubjectId = useWorkspace(s => s.activeSubjectId);
  const getSubject      = useWorkspace(s => s.getSubject);
  const learnerId = useAuth(s => s.currentUserId) ?? DEMO_LEARNER_ID;

  const defaultSubject = (activeSubjectId && getSubject(activeSubjectId)?.name) || 'General';
  const [topic, setTopic]       = useState('');
  const [count, setCount]       = useState(5);
  const [subject, setSubject]   = useState(defaultSubject);

  const queue = useMemo(() => {
    const now = Date.now();
    return cards
      .filter(c => new Date(c.due).getTime() <= now)
      .sort((a, b) => new Date(a.due).getTime() - new Date(b.due).getTime());
  }, [cards]);

  const card = queue[cursor];
  const remaining = queue.length - cursor;

  const submitGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    const t = topic.trim();
    if (!t) return;
    try {
      await generate({
        learnerId,
        subject: subject || 'General',
        topic: t,
        count: Math.max(1, Math.min(20, count)),
        difficulty: 'MEDIUM',
      });
      setTopic('');
    } catch {
      /* surfaced via lastError */
    }
  };

  const GenerateForm = (
    <form className="card" onSubmit={submitGenerate} style={{ padding: 'var(--space-6)' }}>
      <SectionLabel>Generate practice</SectionLabel>
      <p className="muted mt-2" style={{ marginBottom: 'var(--space-4)' }}>
        The tutor will produce flashcards calibrated to your level.
      </p>
      <div className="row" style={{ gap: 'var(--space-3)', flexWrap: 'wrap' }}>
        <Input
          placeholder="Topic (e.g. Photosynthesis, IAM policies)"
          value={topic}
          onChange={e => setTopic(e.target.value)}
          style={{ flex: '1 1 220px', minWidth: 200 }}
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
        />
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
  );

  if (!card) {
    return (
      <div className="page-react">
        <SubTabs tabs={PRACTICE_TABS} ariaLabel="Practice hub" />
        <header className="page-header">
          <div><h1>Practice</h1><p>Spaced repetition queue.</p></div>
        </header>
        <div className="card text-center" style={{ padding: 'var(--space-9)' }}>
          <h3>{cards.length === 0 ? 'No cards yet' : "You're all caught up 🎉"}</h3>
          <p className="muted mt-3">
            {cards.length === 0
              ? 'Generate a practice batch to get started.'
              : 'No cards due right now. Generate a fresh batch below.'}
          </p>
        </div>
        <div className="mt-5">{GenerateForm}</div>
      </div>
    );
  }

  const k = concept(card.conceptId);

  return (
    <div className="page-react">
      <SubTabs tabs={PRACTICE_TABS} ariaLabel="Practice hub" />
      <header className="page-header">
        <div>
          <h1>Practice</h1>
          <p>{remaining} card{remaining === 1 ? '' : 's'} due · ease {card.ease.toFixed(2)} · interval {card.intervalDays}d</p>
        </div>
      </header>

      <div className="deck">
        <div className={'flashcard' + (flipped ? ' is-flipped' : '')} onClick={flip}>
          <div className="flashcard__inner">
            <div className="flashcard__face">
              {k && <SectionLabel>{k.name}</SectionLabel>}
              <p className="flashcard__q">{card.q}</p>
              <p className="flashcard__hint">Click to flip · then grade yourself</p>
            </div>
            <div className="flashcard__face flashcard__face--back">
              <p className="flashcard__a">{card.a}</p>
            </div>
          </div>
        </div>
      </div>

      <div className="deck-controls">
        <Button onClick={() => grade('again')} disabled={!flipped}>
          <Tag kind="danger">Again</Tag>
        </Button>
        <Button onClick={() => grade('hard')} disabled={!flipped}>
          <Tag kind="warn">Hard</Tag>
        </Button>
        <Button variant="primary" onClick={() => grade('good')} disabled={!flipped}>
          Good
        </Button>
        <Button onClick={() => grade('easy')} disabled={!flipped}>
          <Tag kind="success">Easy</Tag>
        </Button>
      </div>

      <div className="mt-8">{GenerateForm}</div>
    </div>
  );
}
