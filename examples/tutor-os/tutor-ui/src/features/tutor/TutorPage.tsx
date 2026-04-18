import { useEffect, useRef } from 'react';
import { usePipeline } from '@/store/pipeline';
import { ChatMessage } from './ChatMessage';
import { ChatComposer } from './ChatComposer';
import { PipelineReveal } from '@/features/pipeline/PipelineReveal';
import { PipelineGraph } from '@/features/pipeline/PipelineGraph';
import { seedChat } from '@/lib/mockData';
import { SectionLabel } from '@/components/ui/Misc';

/**
 * Tutor — the active chat canvas.
 *
 * Layout: vertical split → scrollable thread on top, sticky composer
 * on the bottom. Pipeline reveal + graph render below the thread when
 * a run is active so the learner can watch what the agents are doing.
 */
export function TutorPage() {
  const { messages, start, isRunning, currentRun } = usePipeline();
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

          {/* Show the pipeline + graph below the thread while a run is live */}
          {currentRun && (
            <>
              <SectionLabel>Pipeline</SectionLabel>
              <PipelineReveal />
              <div className="mt-5">
                <SectionLabel>Topology</SectionLabel>
                <PipelineGraph />
              </div>
            </>
          )}
          <div ref={threadEndRef} />
        </div>
      </div>
      <ChatComposer onSubmit={(body) => void start(body)} disabled={isRunning} />
    </div>
  );
}
