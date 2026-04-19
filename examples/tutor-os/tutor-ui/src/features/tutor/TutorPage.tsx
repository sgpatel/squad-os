import { useEffect, useRef } from 'react';
import { usePipeline } from '@/store/pipeline';
import { useSettings } from '@/store/settings';
import { ChatMessage } from './ChatMessage';
import { ChatComposer } from './ChatComposer';
import { AgentActivity } from '@/features/pipeline/AgentActivity';
import { seedChat } from '@/lib/mockData';

/**
 * Tutor — the active chat canvas.
 *
 * Layout: vertical split → scrollable thread on top, sticky composer
 * on the bottom. Pipeline reveal + graph render below the thread when
 * a run is active so the learner can watch what the agents are doing.
 */
export function TutorPage() {
  const { messages, start, isRunning, currentRun } = usePipeline();
  const assistMode = useSettings(s => s.assistMode);
  const threadEndRef = useRef<HTMLDivElement>(null);

  // Seed an example exchange on first visit so the page isn't empty.
  // (One-shot — won't re-seed if you've already chatted.)
  useEffect(() => {
    if (messages.length === 0) {
      usePipeline.setState({ messages: seedChat });
    }
  }, [messages.length]);

  // Pin scroll to bottom on new messages.
  useEffect(() => {
    threadEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages.length, currentRun?.active]);

  return (
    <div className="chat">
      <div className="chat__thread">
        <div className="chat__thread-inner">
          {messages.map(m => <ChatMessage key={m.id} msg={m} />)}

          {/* Inline agent-activity block — renders at the end of the thread
              where the tutor reply will land, mimicking the tool-use /
              thinking affordances in ChatGPT, Claude, and Perplexity.
              Agentic mode only; direct mode stays minimal. */}
          {currentRun && assistMode === 'agentic' && <AgentActivity />}

          <div ref={threadEndRef} />
        </div>
      </div>
      <ChatComposer onSubmit={(body) => void start(body)} disabled={isRunning} />
    </div>
  );
}
