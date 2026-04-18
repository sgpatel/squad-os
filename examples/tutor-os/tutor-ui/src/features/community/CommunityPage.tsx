import { ThumbsUp, Check, MessageCircle, Plus } from 'lucide-react';
import { seedDoubts } from '@/lib/mockData';
import { Button } from '@/components/ui/Button';
import { Tag } from '@/components/ui/Misc';
import { fmtRelative } from '@/lib/format';

/**
 * Community — doubt threads (peer + tutor + AI answers).
 *
 * Lightweight in this example — one mock thread, but the data shape
 * (DoubtAnswer[] with role + accepted flag) supports a full Q&A surface.
 */
export function CommunityPage() {
  return (
    <div className="page-react">
      <header className="page-header">
        <div>
          <h1>Community</h1>
          <p>Ask, help, get unstuck. Tutor + peer + AI answers in one thread.</p>
        </div>
        <div className="page-actions">
          <Button variant="primary" leading={<Plus size={14} />}>Ask a doubt</Button>
        </div>
      </header>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
        {seedDoubts.map(d => (
          <article key={d.id} className="doubt">
            <div className="doubt__head">
              <div style={{ flex: 1 }}>
                <p className="doubt__title">{d.title}</p>
                <p className="small muted">
                  by {d.authorName} · {fmtRelative(d.createdAt)} ·{' '}
                  {d.tags.map(t => <Tag key={t}>{t}</Tag>)}
                </p>
              </div>
              <span className="small muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                <MessageCircle size={12} /> {d.answers.length}
              </span>
            </div>
            <p>{d.body}</p>

            <div className="doubt__answers">
              {d.answers.map(a => (
                <div key={a.id} className={'doubt__answer' + (a.accepted ? ' doubt__answer--accepted' : '')}>
                  <div className="row" style={{ justifyContent: 'space-between', marginBottom: 4 }}>
                    <span className="small muted">
                      <strong>{a.authorName}</strong> · <Tag>{a.authorRole}</Tag> · {fmtRelative(a.createdAt)}
                    </span>
                    <span className="small muted">
                      {a.accepted && <Tag kind="success"><Check size={11} /> accepted</Tag>}
                      {' '}<ThumbsUp size={11} /> {a.upvotes}
                    </span>
                  </div>
                  <p>{a.body}</p>
                </div>
              ))}
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
