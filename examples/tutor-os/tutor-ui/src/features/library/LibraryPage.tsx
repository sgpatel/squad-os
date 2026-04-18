import { Book, Video, Globe, FileText, Plus, ExternalLink, Library as LibraryIcon } from 'lucide-react';
import { seedSources } from '@/lib/mockData';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Tag } from '@/components/ui/Misc';
import type { SourceKind } from '@/lib/types';
import { fmtPercent } from '@/lib/format';

const ICON: Record<SourceKind, React.ComponentType<{ size?: string | number }>> = {
  book:  Book,
  video: Video,
  web:   Globe,
  pdf:   FileText
};

/**
 * Library — the citation pool.
 *
 * Every tutor answer's `citations[]` references entries here.
 * Showing this page makes the provenance system tangible: the learner
 * can see exactly which textbook / video / doc the agents are pulling from.
 */
export function LibraryPage() {
  return (
    <div className="page-react">
      <header className="page-header">
        <div>
          <h1>Library</h1>
          <p>Sources the tutor cites from. Drop in a PDF, paste a URL, or import a video.</p>
        </div>
        <div className="page-actions">
          <Button variant="primary" leading={<Plus size={14} />}>Add source</Button>
        </div>
      </header>

      {seedSources.length === 0 ? (
        <EmptyState
          icon={<LibraryIcon size={20} />}
          title="Your library is empty"
          hint="Drop in a PDF, paste a URL, or import a video. Every tutor answer cites back to entries here, so the more you add, the richer the explanations."
          action={<Button variant="primary" leading={<Plus size={14} />}>Add your first source</Button>}
        />
      ) : (
      <div className="source-grid">
        {seedSources.map(s => {
          const Icon = ICON[s.kind];
          return (
            <article key={s.id} className="source-card">
              <span className="source-card__type"><Icon size={12} /> {s.kind}</span>
              <div className="source-card__title">{s.title}</div>
              <p className="muted small">{s.author ?? '—'}</p>
              <div className="source-card__cited">
                <span>Cited in:</span>
                {s.cited.map((c, i) => <Tag key={i}>{c}</Tag>)}
              </div>
              <div className="row" style={{ justifyContent: 'space-between', marginTop: 'var(--space-3)' }}>
                <Tag kind={s.trust >= 0.9 ? 'success' : s.trust >= 0.75 ? 'warn' : 'danger'}>
                  Trust {fmtPercent(s.trust)}
                </Tag>
                {s.url && (
                  <a className="btn btn--sm btn--ghost" href={s.url} target="_blank" rel="noreferrer">
                    Open <ExternalLink size={12} />
                  </a>
                )}
              </div>
            </article>
          );
        })}
      </div>
      )}
    </div>
  );
}
