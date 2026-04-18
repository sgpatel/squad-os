import { usePractice } from '@/store/practice';
import { useWorkspace } from '@/store/workspace';
import { Button } from '@/components/ui/Button';
import { SectionLabel, Tag } from '@/components/ui/Misc';

/**
 * Practice — flashcard reviewer with SM-2-style scheduling.
 *
 * One card at a time. Tap (or press Space) to flip. Then grade
 * yourself: Again · Hard · Good · Easy → next card.
 */
export function PracticePage() {
  const queue   = usePractice(s => s.dueQueue());
  const cursor  = usePractice(s => s.cursor);
  const flipped = usePractice(s => s.flipped);
  const flip    = usePractice(s => s.flip);
  const grade   = usePractice(s => s.grade);
  const concept = useWorkspace(s => s.getConcept);

  const card = queue[cursor];
  const remaining = queue.length - cursor;

  if (!card) {
    return (
      <div className="page-react">
        <header className="page-header">
          <div><h1>Practice</h1><p>Spaced repetition queue.</p></div>
        </header>
        <div className="card text-center" style={{ padding: 'var(--space-9)' }}>
          <h3>You're all caught up 🎉</h3>
          <p className="muted mt-3">No cards due right now. Come back later — or generate practice from a chapter.</p>
        </div>
      </div>
    );
  }

  const k = concept(card.conceptId);

  return (
    <div className="page-react">
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
    </div>
  );
}
