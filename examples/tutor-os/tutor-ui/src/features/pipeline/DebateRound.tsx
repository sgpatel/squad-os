import { useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { DebateRound as DebateRoundData } from '@/lib/types';
import { fmtPercent } from '@/lib/format';

interface Props {
  data: DebateRoundData;
}

/**
 * Debate Round — the signature view.
 *
 * Two cards (For / Against) per round, navigation pips at the bottom,
 * judge verdict block at the bottom. The point is to make the model's
 * dissent legible to a learner: "here's both sides, here's who won, why."
 */
export function DebateRound({ data }: Props) {
  const [round, setRound] = useState(0);
  const total = data.rounds.length;
  const cur = data.rounds[round]!;

  return (
    <div className="debate" role="region" aria-label="Debate round">
      <div className="debate__head">
        <div>
          <p className="debate__title">{data.topic}</p>
          <p className="small muted">Two agents argue. The judge picks the supported claim.</p>
        </div>
        <span className="debate__round">Round {round + 1} of {total}</span>
      </div>

      <div className="debate__cards">
        <article className="debate__card" data-side="for">
          <p className="debate__side">For — Advocate</p>
          <p className="debate__claim">{cur.forClaim}</p>
          <p className="debate__support">Citation: {cur.forSource}</p>
        </article>
        <article className="debate__card" data-side="against">
          <p className="debate__side">Against — Skeptic</p>
          <p className="debate__claim">{cur.againstClaim}</p>
          <p className="debate__support">Citation: {cur.againstSource}</p>
        </article>
      </div>

      <div className="debate__verdict">
        <strong>Judge:</strong> {data.verdict} {' '}
        <span className="small muted">(Confidence {fmtPercent(data.verdictConfidence)})</span>
      </div>

      <div className="debate__rounds" aria-label="Debate progress">
        {data.rounds.map((_, i) => (
          <button
            key={i}
            className="pip"
            data-active={i <= round ? 'true' : 'false'}
            onClick={() => setRound(i)}
            aria-label={`Round ${i + 1}`}
            aria-current={i === round}
            style={{ border: 'none', cursor: 'pointer' }}
          />
        ))}
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 4 }}>
          <button
            type="button"
            onClick={() => setRound(r => Math.max(0, r - 1))}
            disabled={round === 0}
            style={btnStyle}
            aria-label="Previous round"
          ><ChevronLeft size={14} /></button>
          <button
            type="button"
            onClick={() => setRound(r => Math.min(total - 1, r + 1))}
            disabled={round === total - 1}
            style={btnStyle}
            aria-label="Next round"
          ><ChevronRight size={14} /></button>
        </div>
      </div>
    </div>
  );
}

const btnStyle: React.CSSProperties = {
  width: 28, height: 20, display: 'grid', placeItems: 'center',
  border: '1px solid var(--color-border)', borderRadius: 'var(--radius-xs)',
  background: 'var(--color-surface)', color: 'var(--color-text-muted)', cursor: 'pointer'
};
