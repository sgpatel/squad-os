import { useEffect, useRef, useState } from 'react';
import { BookOpen } from 'lucide-react';
import { usePipeline } from '@/store/pipeline';
import { useSettings } from '@/store/settings';
import { useWorkspace } from '@/store/workspace';
import { ChatMessage } from './ChatMessage';
import { ChatComposer } from './ChatComposer';
import { AgentActivity } from '@/features/pipeline/AgentActivity';
import { SyllabusSheet } from '@/features/syllabus/SyllabusSheet';

/**
 * Tutor — the active chat canvas.
 *
 * Layout: vertical split → scrollable thread on top, sticky composer
 * on the bottom. Pipeline reveal + graph render below the thread when
 * a run is active so the learner can watch what the agents are doing.
 */
export function TutorPage() {
  const { messages, start, isRunning, currentRun } = usePipeline();
  const assistMode      = useSettings(s => s.assistMode);
  const activeSubjectId = useWorkspace(s => s.activeSubjectId);
  const getSubject      = useWorkspace(s => s.getSubject);
  const activeSyllabus  = useWorkspace(s => s.activeSyllabus);
  const threadEndRef    = useRef<HTMLDivElement>(null);
  const [syllabusOpen, setSyllabusOpen] = useState(false);

  const subjectName = activeSubjectId ? getSubject(activeSubjectId)?.name : null;

  // Pin scroll to bottom on new messages.
  useEffect(() => {
    threadEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages.length, currentRun?.active]);

  return (
    <div className="chat">
      {/* Header strip — shows the active subject + topic for this session
          and exposes the SyllabusSheet so the learner can shape the
          curriculum at any moment. Lives INSIDE TutorPage (not Topbar)
          because subject/topic/syllabus are session-scoped concepts that
          have no meaning on /home, /notes, /settings, etc. */}
      <header className="tutor-header">
        <div className="tutor-header__scope">
          {subjectName ? (
            <span className="tutor-header__pill" title="Active subject">
              {subjectName}
            </span>
          ) : (
            <span className="tutor-header__pill tutor-header__pill--muted">
              No subject
            </span>
          )}
          {activeSyllabus?.topic && (
            <>
              <span className="tutor-header__sep" aria-hidden>›</span>
              <span className="tutor-header__topic" title="Active topic">
                {activeSyllabus.topic}
              </span>
            </>
          )}
        </div>
        <button
          type="button"
          className="btn btn--ghost"
          onClick={() => setSyllabusOpen(true)}
        >
          <BookOpen size={13} />
          {activeSyllabus ? 'Syllabus' : 'Suggest a syllabus'}
        </button>
      </header>

      <div className="chat__thread">
        <div className="chat__thread-inner">
          {messages.map(m => (
            <ChatMessage
              key={m.id}
              msg={m}
              onOpenSyllabus={() => setSyllabusOpen(true)}
              onQuickReply={(text) => void start(text)}
            />
          ))}

          {/* Inline agent-activity block — renders at the end of the thread
              where the tutor reply will land, mimicking the tool-use /
              thinking affordances in ChatGPT, Claude, and Perplexity.
              Agentic mode only; direct mode stays minimal. */}
          {currentRun && assistMode === 'agentic' && <AgentActivity />}

          <div ref={threadEndRef} />
        </div>
      </div>
      <ChatComposer onSubmit={(body) => void start(body)} disabled={isRunning} />

      <SyllabusSheet open={syllabusOpen} onClose={() => setSyllabusOpen(false)} />
    </div>
  );
}
