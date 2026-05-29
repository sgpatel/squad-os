import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Atom, Book, BookOpen, Cloud, Code, FlaskConical, Leaf, Sigma,
  Plus, Sparkles, HelpCircle, BrainCircuit, CalendarDays, ArrowRight, Flame,
  MessageSquare, X, Mic, Send, Paperclip, FileText as FileIcon, Link as LinkIcon
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card, CardMeta, CardTitle } from '@/components/ui/Card';
import { Kbd, SectionLabel, Tag } from '@/components/ui/Misc';
import { useWorkspace } from '@/store/workspace';
import { useAuth } from '@/store/auth';
import { usePlan } from '@/store/plan';
import { usePipeline } from '@/store/pipeline';
import { usePractice } from '@/store/practice';
import { ChatMessage } from '@/features/tutor/ChatMessage';
import { AgentActivity } from '@/features/pipeline/AgentActivity';
import { AddSubjectModal } from './AddSubjectModal';
import { fmtMinutes, fmtRelative } from '@/lib/format';

/**
 * Home — a chat-first landing, modelled on ChatGPT / Claude.
 *
 *   ┌───────────────────────────────────────────────────────────┐
 *   │                   Good morning, Priya                     │
 *   │              What shall we learn today?                   │
 *   │                                                           │
 *   │   ┌─────────────────────────────────────────────────┐     │
 *   │   │  Ask anything — topic, syllabus, PDF link …  ↩  │     │
 *   │   └─────────────────────────────────────────────────┘     │
 *   │                                                           │
 *   │  Subjects:  [Biology] [Physics] … [+ Add subject]         │
 *   │  Quick:     [Quiz me] [Explain] [Practice]                │
 *   ├───────────────────────────────────────────────────────────┤
 *   │  (A) When a run is active OR there are messages:          │
 *   │      chat thread renders inline — composer is still at    │
 *   │      the top so the input never jumps. AgentActivity      │
 *   │      sits inline ChatGPT-style.                           │
 *   │  (B) Otherwise: Today / Continue / Practice / Recent      │
 *   └───────────────────────────────────────────────────────────┘
 */

const SUBJECT_ICONS: Record<string, LucideIcon> = {
  Atom, Book, Cloud, Code, FlaskConical, Leaf, Sigma, BookOpen
};

export function HomePage() {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const threadEndRef = useRef<HTMLDivElement>(null);
  const [q, setQ] = useState('');
  const [addOpen, setAddOpen] = useState(false);

  // Attachments — paste-in context (long text, URL) that rides along with
  // the next prompt. Modelled on GPAI's "upload anything" affordance but
  // scoped to text paste for now (no backend upload wired yet).
  type Attachment = { id: string; label: string; body: string; kind: 'text' | 'url' };
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [attachOpen,  setAttachOpen]  = useState(false);
  const [attachText,  setAttachText]  = useState('');
  const [attachLabel, setAttachLabel] = useState('');

  const seedUser          = useWorkspace(s => s.user);
  const authUser          = useAuth(s => s.currentUser());
  const user              = authUser ?? seedUser;
  const workspaceSubjects = useWorkspace(s => s.workspaceSubjects);
  const activeSubjectId   = useWorkspace(s => s.activeSubjectId);
  const setActiveSubject  = useWorkspace(s => s.setActiveSubject);
  const addSubject        = useWorkspace(s => s.addSubject);

  const subjects = workspaceSubjects();
  const activeSubject = subjects.find(s => s.id === activeSubjectId) ?? subjects[0] ?? null;

  const planItems = usePlan(s => s.items);
  const todayKey  = new Date().toISOString().slice(0, 10);
  const todayItems = useMemo(
    () => planItems.filter(i => i.date === todayKey)
                   .sort((a, b) => (a.startTime ?? '').localeCompare(b.startTime ?? '')),
    [planItems, todayKey]
  );
  const dueCount  = usePractice(s => s.dueQueue().length);
  const start     = usePipeline(s => s.start);
  const isRunning = usePipeline(s => s.isRunning);
  const messages  = usePipeline(s => s.messages);
  const currentRun = usePipeline(s => s.currentRun);
  const reset     = usePipeline(s => s.reset);

  const hasConversation = messages.length > 0 || !!currentRun;

  // Auto-grow textarea.
  useEffect(() => {
    const ta = inputRef.current;
    if (!ta) return;
    ta.style.height = 'auto';
    ta.style.height = Math.min(ta.scrollHeight, 180) + 'px';
  }, [q]);

  // Pin thread to bottom while active.
  useEffect(() => {
    if (hasConversation) {
      threadEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }
  }, [messages.length, currentRun?.active, hasConversation]);

  const submit = (prompt: string) => {
    const text = prompt.trim();
    if (!text || isRunning) return;
    setQ('');

    // Weld any attached context into the outgoing prompt so the pipeline
    // sees it as first-class content (same agents, same stream — no
    // separate "ingest" endpoint required).
    let composed = text;
    if (attachments.length) {
      const ctx = attachments.map((a, i) => {
        const kindLabel = a.kind === 'url' ? 'URL' : 'Attached text';
        return `[Attachment ${i + 1} · ${kindLabel} · ${a.label}]\n${a.body}`;
      }).join('\n\n');
      composed = `${text}\n\n--- Context ---\n${ctx}`;
      setAttachments([]); // consumed
    }
    void start(composed);
  };

  const isUrl = (s: string) => /^https?:\/\/\S+$/i.test(s.trim());

  const addAttachment = () => {
    const body = attachText.trim();
    if (!body) return;
    const kind: Attachment['kind'] = isUrl(body) ? 'url' : 'text';
    const label = (attachLabel.trim() ||
      (kind === 'url'
        ? new URL(body).hostname
        : (body.split('\n')[0] ?? '').slice(0, 40))) || 'snippet';
    setAttachments(prev => [...prev, {
      id: `att_${Date.now().toString(36)}`,
      label, body, kind
    }]);
    setAttachText('');
    setAttachLabel('');
    setAttachOpen(false);
  };
  const removeAttachment = (id: string) =>
    setAttachments(prev => prev.filter(a => a.id !== id));

  const askAbout = (prefix: string) => {
    const label = activeSubject?.name ?? 'your current topic';
    submit(`${prefix} ${label}.`);
  };

  const handlePickSubject = (id: string) => {
    if (id === activeSubjectId) return;
    setActiveSubject(id);
    reset();            // start a fresh pipeline session scoped to new subject
    inputRef.current?.focus();
  };

  const continueWith = todayItems.find(p => !p.done);
  const greeting = greetingByHour();

  return (
    <div className="home page-react">
      {/* ── HERO ──────────────────────────────────────────────── */}
      {!hasConversation && (
        <section className="home__hero" aria-labelledby="greeting">
          <h1 className="home__greeting" id="greeting">
            {greeting}, <em>{user.name}</em> — what shall we learn?
          </h1>
          <p className="home__subtitle muted">
            {activeSubject
              ? <>You're in <strong style={{ color: activeSubject.color }}>{activeSubject.name}</strong>. Ask anything, or pick a different subject below.</>
              : <>Pick a subject, or just start typing — I'll build a plan from your first question.</>}
          </p>
        </section>
      )}

      {/* ── COMPOSER (sticky within the home section) ─────────── */}
      <section className={'home__composer-wrap' + (hasConversation ? ' home__composer-wrap--pinned' : '')}>
        <form
          className="home__composer"
          onSubmit={(e) => { e.preventDefault(); submit(q); }}
          role="search"
        >
          <textarea
            ref={inputRef}
            rows={1}
            placeholder={isRunning
              ? 'Tutor is thinking…'
              : `Ask about ${activeSubject?.name ?? 'any topic'} — Enter to send, Shift+Enter for newline`}
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(q); }
            }}
            disabled={isRunning}
            aria-label="Ask the tutor"
          />
          <Button
            variant="ghost"
            iconOnly
            aria-label="Attach context"
            type="button"
            disabled={isRunning}
            onClick={() => setAttachOpen(o => !o)}
            title="Paste text or a URL as context for the next message"
          >
            <Paperclip size={14} />
          </Button>
          <Button variant="ghost" iconOnly aria-label="Voice input" type="button" disabled={isRunning}>
            <Mic size={14} />
          </Button>
          <Button
            variant="primary"
            size="sm"
            type="submit"
            disabled={isRunning || !q.trim()}
            trailing={<Kbd>↩</Kbd>}
          >
            {isRunning ? 'Thinking…' : <><Send size={13} />&nbsp;Send</>}
          </Button>
        </form>

        {/* Attachment chips — shown when the user has pasted context
            that will ride along with the next submitted prompt. */}
        {attachments.length > 0 && (
          <div className="attach-strip" aria-label="Attached context">
            {attachments.map(a => (
              <span key={a.id} className="attach-chip" title={a.body.slice(0, 200)}>
                {a.kind === 'url' ? <LinkIcon size={11} /> : <FileIcon size={11} />}
                <span className="attach-chip__label">{a.label}</span>
                <button
                  type="button"
                  className="attach-chip__rm"
                  aria-label={`Remove ${a.label}`}
                  onClick={() => removeAttachment(a.id)}
                >
                  <X size={11} />
                </button>
              </span>
            ))}
            <span className="small muted">
              &nbsp;Attached context will be sent with your next message.
            </span>
          </div>
        )}

        {/* Paste-context popover */}
        {attachOpen && (
          <div className="attach-pop" role="dialog" aria-label="Attach context">
            <div className="attach-pop__label">
              Paste text or a URL — the next message will include it as context.
            </div>
            <input
              className="field__input"
              placeholder="Label (optional, e.g. 'Chapter 4 notes')"
              value={attachLabel}
              onChange={(e) => setAttachLabel(e.target.value)}
            />
            <textarea
              className="field__textarea"
              placeholder="Paste notes, a passage, or a URL here…"
              value={attachText}
              onChange={(e) => setAttachText(e.target.value)}
              rows={5}
              autoFocus
            />
            <div className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
              <Button size="sm" variant="ghost" onClick={() => setAttachOpen(false)}>Cancel</Button>
              <Button size="sm" variant="primary" onClick={addAttachment} disabled={!attachText.trim()}>
                <Paperclip size={12} />&nbsp;Attach
              </Button>
            </div>
          </div>
        )}

        {/* Subject chip strip with + Add */}
        <div className="home__chip-row" role="list" aria-label="Subjects">
          {subjects.map(s => {
            const Icon = SUBJECT_ICONS[s.icon] ?? Book;
            const on = activeSubject?.id === s.id;
            return (
              <button
                key={s.id}
                type="button"
                role="listitem"
                onClick={() => handlePickSubject(s.id)}
                className={'subj-chip' + (on ? ' subj-chip--on' : '')}
                style={{ ['--subj-color' as any]: s.color }}
                aria-pressed={on}
                title={s.blurb}
              >
                <Icon size={13} /> {s.name}
              </button>
            );
          })}
          <button
            type="button"
            onClick={() => setAddOpen(true)}
            className="subj-chip subj-chip--add"
            aria-label="Add a new subject"
          >
            <Plus size={13} /> Add subject
          </button>
        </div>

        {/* Intent chips — quick common actions */}
        <div className="home__chip-row home__chip-row--intents" role="list" aria-label="Quick actions">
          <button
            type="button"
            className="intent-chip"
            onClick={() => askAbout('Quiz me on')}
            disabled={isRunning}
          >
            <HelpCircle size={13} /> Quiz me
          </button>
          <button
            type="button"
            className="intent-chip"
            onClick={() => askAbout('Explain the key concepts in')}
            disabled={isRunning}
          >
            <Sparkles size={13} /> Explain
          </button>
          <button
            type="button"
            className="intent-chip"
            onClick={() => navigate('/practice')}
          >
            <BrainCircuit size={13} /> Practice
            {dueCount > 0 && <span className="intent-chip__badge">{dueCount}</span>}
          </button>
          <button
            type="button"
            className="intent-chip"
            onClick={() => navigate('/plan')}
          >
            <CalendarDays size={13} /> Plan
          </button>
          {hasConversation && (
            <button
              type="button"
              className="intent-chip intent-chip--danger"
              onClick={reset}
              title="Clear this conversation and start over"
            >
              <X size={13} /> New chat
            </button>
          )}
        </div>
      </section>

      {/* ── (A) CONVERSATION — renders inline, ChatGPT-style ─── */}
      {hasConversation ? (
        <section className="home__thread" aria-live="polite">
          <div className="home__thread-inner">
            {messages.map(m => <ChatMessage key={m.id} msg={m} />)}
            {/* Modern AI-chatbot-style thinking block */}
            {currentRun && <AgentActivity />}
            <div ref={threadEndRef} />
          </div>

          <div className="home__thread-footer">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => navigate('/tutor')}
              trailing={<ArrowRight size={12} />}
            >
              <MessageSquare size={13} />&nbsp;Open full chat
            </Button>
          </div>
        </section>
      ) : (
        // ── (B) EMPTY STATE — Today / Continue / Practice / Recent
        <section className="home__cards column mt-9 stack-lg">
          <div>
            <SectionLabel>Today</SectionLabel>
            <div className="grid-3">
              <Card>
                <CardMeta>Plan</CardMeta>
                <CardTitle>
                  {todayItems.length} item{todayItems.length === 1 ? '' : 's'} today
                </CardTitle>
                <p className="muted small">
                  {todayItems.filter(i => i.done).length} done · {todayItems.filter(i => !i.done).length} left
                </p>
                <div className="row mt-3">
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => navigate('/plan')}
                    trailing={<ArrowRight size={12} />}
                  >
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
                  <Button
                    size="sm"
                    onClick={() => navigate('/practice')}
                    trailing={<ArrowRight size={12} />}
                  >
                    Review now
                  </Button>
                  {user.streak > 0 && (
                    <Tag kind="warn"><Flame size={11} /> {user.streak}-day streak</Tag>
                  )}
                </div>
              </Card>

              <Card>
                <CardMeta>Continue</CardMeta>
                <CardTitle>
                  {continueWith?.title ?? activeSubject?.name ?? 'Pick a subject'}
                </CardTitle>
                <p className="muted small">
                  {continueWith
                    ? <>Next in plan · {fmtRelative(Date.now())}</>
                    : activeSubject
                      ? <>Start a session in {activeSubject.name}</>
                      : <>Tap a subject chip above to begin</>}
                </p>
                <div className="row mt-3">
                  <Button
                    size="sm"
                    onClick={() => {
                      if (continueWith?.chapterId)  navigate(`/chapters/${continueWith.chapterId}`);
                      else if (continueWith?.quizId) navigate(`/quiz/${continueWith.quizId}`);
                      else inputRef.current?.focus();
                    }}
                    trailing={<ArrowRight size={12} />}
                  >
                    {continueWith ? 'Resume' : 'Start'}
                  </Button>
                </div>
              </Card>
            </div>
          </div>
        </section>
      )}

      <footer className="column mt-9 text-center">
        <p className="small subtle">
          Every answer carries citations + confidence.&nbsp;
          Press <Kbd>⌘</Kbd><Kbd>K</Kbd> to ask, jump, or create anything.
        </p>
      </footer>

      <AddSubjectModal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onAdd={(input) => {
          const s = addSubject(input);
          setActiveSubject(s.id);
          reset();
          // Prefill the composer with a friendly starter so the learner
          // gets an immediate plan draft for the new subject.
          setQ(`I want to learn ${s.name}. Build me a study plan and let's start.`);
          requestAnimationFrame(() => inputRef.current?.focus());
        }}
      />
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
