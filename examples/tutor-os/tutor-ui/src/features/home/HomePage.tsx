import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Mic, RotateCw, Sparkles, CalendarDays, ArrowRight, Flame } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Chip } from '@/components/ui/Chip';
import { Card, CardMeta, CardTitle } from '@/components/ui/Card';
import { Kbd, SectionLabel, Tag } from '@/components/ui/Misc';
import { useWorkspace } from '@/store/workspace';
import { usePlan } from '@/store/plan';
import { usePipeline } from '@/store/pipeline';
import { usePractice } from '@/store/practice';
import { PipelineReveal } from '@/features/pipeline/PipelineReveal';
import { DebateRound } from '@/features/pipeline/DebateRound';
import { fmtMinutes, fmtRelative } from '@/lib/format';

/**
 * Home — the calm starting point.
 *
 *   ┌────────────────────────────────────────────┐
 *   │   Greeting (one line, name in accent)      │
 *   │   ┌──────────────────────────────┐  Mic ↩ │  ← single input
 *   │   │ Ask anything…                │         │
 *   │   └──────────────────────────────┘         │
 *   │   [chip] [chip] [chip]                     │
 *   ├────────────────────────────────────────────┤
 *   │  Pipeline Reveal (auto-shown when running) │
 *   │  Debate Round    (auto-shown after debate) │
 *   ├────────────────────────────────────────────┤
 *   │  Today  ·  Continue  ·  Up next            │  3-card grid
 *   └────────────────────────────────────────────┘
 */
export function HomePage() {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const [q, setQ] = useState('');

  const { user } = useWorkspace();
  const todayItems = usePlan(s => s.forDate(new Date().toISOString().slice(0, 10)));
  const dueCount = usePractice(s => s.dueQueue().length);
  const { start, isRunning, messages } = usePipeline();

  // Most recent tutor message that has a debate attached → render it inline.
  const lastDebate = [...messages].reverse().find(m => m.role === 'tutor' && m.debate)?.debate;

  const submit = (prompt: string) => {
    if (!prompt.trim() || isRunning) return;
    setQ('');
    void start(prompt);
  };

  const continueWith = todayItems.find(p => !p.done);
  const greeting = greetingByHour();

  return (
    <div className="page-react">
      {/* ── HERO ──────────────────────────────────────────────── */}
      <section className="hero" aria-labelledby="greeting">
        <h1 className="hero__greeting" id="greeting">
          {greeting}, <em>{user.name}</em> — what shall we learn?
        </h1>

        <form
          className="hero__input"
          onSubmit={(e) => { e.preventDefault(); submit(q); }}
          role="search"
        >
          <input
            ref={inputRef}
            placeholder="Ask anything · paste a topic, syllabus, or PDF link"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Ask the tutor"
          />
          <Button variant="ghost" iconOnly aria-label="Voice input" type="button">
            <Mic size={14} />
          </Button>
          <Button variant="primary" size="sm" type="submit" disabled={isRunning} trailing={<Kbd>↩</Kbd>}>
            {isRunning ? 'Thinking…' : 'Start'}
          </Button>
        </form>

        <div className="hero__chips" role="list" aria-label="Quick actions">
          {continueWith && (
            <Chip icon={<RotateCw size={12} />} onClick={() => continueWith.chapterId
              ? navigate(`/chapters/${continueWith.chapterId}`)
              : continueWith.quizId ? navigate(`/quiz/${continueWith.quizId}`) : navigate('/plan')}>
              Continue: {truncate(continueWith.title, 38)}
            </Chip>
          )}
          <Chip icon={<Sparkles size={12} />} onClick={() => submit('Quiz me on yesterday\'s chapter')}>
            Quiz me
          </Chip>
          <Chip icon={<CalendarDays size={12} />} onClick={() => navigate('/plan')}>
            Plan revision
          </Chip>
        </div>
      </section>

      {/* ── Pipeline reveal (only shows when run is active) ───── */}
      <div className="column">
        <PipelineReveal />
      </div>

      {/* ── Debate Round (only shows after debate stage runs) ── */}
      {lastDebate && (
        <div className="column mt-9">
          <SectionLabel>Signature view</SectionLabel>
          <DebateRound data={lastDebate} />
        </div>
      )}

      {/* ── Today / Continue / Up next ────────────────────────── */}
      <section className="column mt-9 stack-lg">
        <div>
          <SectionLabel>Today</SectionLabel>
          <div className="grid-3">
            <Card>
              <CardMeta>Plan</CardMeta>
              <CardTitle>{todayItems.length} item{todayItems.length === 1 ? '' : 's'} today</CardTitle>
              <p className="muted small">
                {todayItems.filter(i => i.done).length} done · {todayItems.filter(i => !i.done).length} left
              </p>
              <div className="row mt-3">
                <Button variant="primary" size="sm" onClick={() => navigate('/plan')}
                  trailing={<ArrowRight size={12} />}>
                  Open plan
                </Button>
              </div>
            </Card>

            <Card>
              <CardMeta>Practice</CardMeta>
              <CardTitle>{dueCount} card{dueCount === 1 ? '' : 's'} due</CardTitle>
              <p className="muted small">
                Spaced repetition · ~{fmtMinutes(Math.max(2, dueCount * 1))}
              </p>
              <div className="row mt-3">
                <Button size="sm" onClick={() => navigate('/practice')}
                  trailing={<ArrowRight size={12} />}>Review now</Button>
                {user.streak > 0 && <Tag kind="warn"><Flame size={11} /> {user.streak}-day streak</Tag>}
              </div>
            </Card>

            <Card>
              <CardMeta>Recent</CardMeta>
              <CardTitle>Photosynthesis</CardTitle>
              <p className="muted small">Mastery 0.34 · last seen {fmtRelative(Date.now() - 4 * 3600e3)}</p>
              <div className="row mt-3">
                <Link className="btn btn--sm" to="/chapters/ch_photosynth">
                  Resume <ArrowRight size={12} />
                </Link>
              </div>
            </Card>
          </div>
        </div>
      </section>

      <footer className="column mt-9 text-center">
        <p className="small subtle">
          Open pipeline · every answer carries citations + confidence.
          Press <Kbd>⌘</Kbd><Kbd>K</Kbd> to ask, jump, or create anything.
        </p>
      </footer>
    </div>
  );
}

function greetingByHour() {
  const h = new Date().getHours();
  if (h < 5)  return 'Late night';
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  if (h < 21) return 'Good evening';
  return 'Good night';
}

function truncate(s: string, n: number) {
  return s.length <= n ? s : s.slice(0, n - 1) + '…';
}
