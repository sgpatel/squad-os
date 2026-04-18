import { ExternalLink } from 'lucide-react';
import type { ChatMessage as Msg } from '@/lib/types';
import { fmtPercent, fmtRelative } from '@/lib/format';
import { useWorkspace } from '@/store/workspace';
import { seedSources } from '@/lib/mockData';
import { Markdown } from '@/components/ui/Markdown';
import { DebateRound } from '@/features/pipeline/DebateRound';

interface Props { msg: Msg }

/**
 * One message bubble. Tutor messages carry citations, a confidence
 * meter, and (when applicable) the Debate Round that produced them.
 */
export function ChatMessage({ msg }: Props) {
  const { user } = useWorkspace();
  const isTutor = msg.role === 'tutor';

  return (
    <div className="msg" data-role={msg.role}>
      <div className="msg__avatar">{isTutor ? 'T' : user.initials}</div>
      <div className="msg__body">
        <div className="msg__role">{isTutor ? 'Tutor' : user.name}</div>
        <div className="msg__content">
          <Markdown>{msg.body}</Markdown>
        </div>

        {isTutor && (msg.citations?.length || msg.confidence != null) && (
          <div className="msg__meta">
            {msg.citations?.map((c, i) => {
              const src = seedSources.find(s => s.id === c.sourceId);
              return (
                <span key={i} className="msg__cite" title={src?.title}>
                  <ExternalLink size={10} />
                  {src?.title.split(' ').slice(0, 3).join(' ') ?? c.sourceId} · {c.locator}
                </span>
              );
            })}
            {msg.confidence != null && (
              <span className="msg__confidence">
                Confidence {fmtPercent(msg.confidence)}
                <span className="msg__confidence-bar" style={{ ['--c' as any]: `${msg.confidence * 100}%` }} />
              </span>
            )}
            <span className="msg__confidence">{fmtRelative(msg.createdAt)}</span>
          </div>
        )}

        {msg.debate && (
          <div style={{ marginTop: 'var(--space-4)' }}>
            <DebateRound data={msg.debate} />
          </div>
        )}
      </div>
    </div>
  );
}
