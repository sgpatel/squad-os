import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ExternalLink, Bookmark, Check, Copy,
  Zap, Brain, HelpCircle, ListTree, ShieldCheck, Sparkles, Loader2
} from 'lucide-react';
import type { ChatMessage as Msg } from '@/lib/types';
import { fmtPercent, fmtRelative } from '@/lib/format';
import { useWorkspace } from '@/store/workspace';
import { useAuth } from '@/store/auth';
import { usePipeline } from '@/store/pipeline';
import { useQuizStore } from '@/store/quiz';
import { usePractice } from '@/store/practice';
import { seedSources } from '@/lib/mockData';
import { DEMO_LEARNER_ID } from '@/lib/api';
import { Markdown } from '@/components/ui/Markdown';
import { DebateRound } from '@/features/pipeline/DebateRound';
import { SaveNotePopover } from '@/features/notes/SaveNotePopover';
import { Diagram } from '@/components/diagram/Diagram';

interface Props { msg: Msg }

/**
 * One message bubble. Tutor messages carry citations, a confidence
 * meter, and (when applicable) the Debate Round that produced them.
 */
export function ChatMessage({ msg }: Props) {
  // Identity comes from the authenticated user — avatar/initials/name
  // and learnerId for any backend calls we fan out from this message.
  const authUser = useAuth(s => s.currentUser());
  const user = authUser ?? { name: 'You', initials: 'YO', id: DEMO_LEARNER_ID };
  const learnerId = user.id;
  const navigate = useNavigate();
  const startPipeline = usePipeline(s => s.start);
  const isRunning     = usePipeline(s => s.isRunning);
  const activeSubjectId = useWorkspace(s => s.activeSubjectId);
  const getSubject      = useWorkspace(s => s.getSubject);
  const generateQuiz     = useQuizStore(s => s.generate);
  const quizBusy         = useQuizStore(s => s.isGenerating);
  const generateFlash    = usePractice(s => s.generate);
  const practiceBusy     = usePractice(s => s.isGenerating);
  const isTutor = msg.role === 'tutor';
  const [saveOpen, setSaveOpen] = useState(false);
  const [justSaved, setJustSaved] = useState(false);
  const [copied, setCopied] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  // Cross-check: if a debate ran, we treat the message as "verified" —
  // the verdict summary becomes a human-readable trust badge analogous to
  // the multi-model verification badge GPAI shows on solved problems.
  const verified = !!msg.debate;

  // AI follow-ups: re-dispatch the tutor's own answer as context for a
  // focused next turn (simpler / deeper / quiz / flashcards).
  const excerpt = () => {
    const t = msg.body.trim();
    return t.length > 1800 ? t.slice(0, 1800) + '\n…' : t;
  };
  const followUp = (prompt: string) => {
    if (isRunning) return;
    void startPipeline(prompt);
  };

  /**
   * Pick a sensible subject + topic for quiz/practice generation from the
   * tutor message. The topic is the message's first sentence (capped), so
   * the generator has something concrete to target. Subject falls back to
   * the active subject in workspace, then "General".
   */
  const topicFromMsg = (): string => {
    const t = msg.body.replace(/^#+\s*/, '').split(/[.!?\n]/)[0]?.trim() || 'this topic';
    return t.length > 80 ? t.slice(0, 80) : t;
  };
  const subjectName = (): string =>
    (activeSubjectId && getSubject(activeSubjectId)?.name) || 'General';

  const quizFromMessage = async () => {
    if (quizBusy) return;
    setActionError(null);
    try {
      const quiz = await generateQuiz({
        learnerId,
        subject: subjectName(),
        topic: topicFromMsg(),
        count: 5,
        difficulty: 'MIXED',
      });
      navigate(`/quiz/${quiz.id}`);
    } catch (e) {
      setActionError(e instanceof Error ? e.message : String(e));
    }
  };

  const flashcardsFromMessage = async () => {
    if (practiceBusy) return;
    setActionError(null);
    try {
      await generateFlash({
        learnerId,
        subject: subjectName(),
        topic: topicFromMsg(),
        count: 6,
        difficulty: 'MEDIUM',
      });
      navigate('/practice');
    } catch (e) {
      setActionError(e instanceof Error ? e.message : String(e));
    }
  };

  const copyBody = async () => {
    try {
      await navigator.clipboard.writeText(msg.body);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    } catch { /* clipboard blocked — silent */ }
  };

  // Gentle "Saved ✓" pulse on the Save button after a successful save.
  const markSaved = () => {
    setJustSaved(true);
    window.setTimeout(() => setJustSaved(false), 1800);
  };

  return (
    <div className="msg" data-role={msg.role}>
      <div className="msg__avatar">{isTutor ? 'T' : user.initials}</div>
      <div className="msg__body">
        <div className="msg__role">{isTutor ? 'Tutor' : user.name}</div>
        <div className="msg__content">
          <Markdown>{msg.body}</Markdown>
          {msg.visualAsset && <Diagram asset={msg.visualAsset} />}
        </div>

        {/* Per-message actions. Tutor messages get Save + Copy;
            user messages get just Copy. Hover-revealed on desktop,
            always visible on touch devices. */}
        {isTutor ? (
          <div className="msg__actions" role="toolbar" aria-label="Message actions">
            <button
              type="button"
              className={'msg__action' + (justSaved ? ' msg__action--ok' : '')}
              onClick={() => setSaveOpen(true)}
              aria-label="Save as note"
              title="Save as note (pick subject + tags)"
            >
              {justSaved ? <Check size={12} /> : <Bookmark size={12} />}
              <span>{justSaved ? 'Saved' : 'Save note'}</span>
            </button>
            <button
              type="button"
              className={'msg__action' + (copied ? ' msg__action--ok' : '')}
              onClick={copyBody}
              aria-label="Copy message"
              title="Copy message to clipboard"
            >
              {copied ? <Check size={12} /> : <Copy size={12} />}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>
          </div>
        ) : null}

        {isTutor && (msg.citations?.length || msg.confidence != null || verified) && (
          <div className="msg__meta">
            {verified && (
              <span
                className="msg__verified"
                title={`Cross-checked by debate (verdict confidence ${fmtPercent(msg.debate!.verdictConfidence)})`}
              >
                <ShieldCheck size={11} /> Cross-checked
              </span>
            )}
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

        {/* Follow-up AI actions on tutor messages — depth tiers, quiz,
            flashcards — analogous to GPAI's "Explain deeper" + "Quiz me"
            affordances on every solved problem. */}
        {isTutor && (
          <div className="msg__followups" role="toolbar" aria-label="Follow-up actions">
            <button
              type="button" className="msg__followup" disabled={isRunning}
              onClick={() => followUp(`Re-explain this as if I'm 14 — simpler language, everyday analogies, no jargon.\n\n---\n\n${excerpt()}`)}
              title="Simpler explanation"
            >
              <Zap size={11} /> Simpler
            </button>
            <button
              type="button" className="msg__followup" disabled={isRunning}
              onClick={() => followUp(`Take the response below one level deeper — formal definitions, derivations, edge cases, and two citations.\n\n---\n\n${excerpt()}`)}
              title="Deeper explanation"
            >
              <Brain size={11} /> Deeper
            </button>
            <button
              type="button" className="msg__followup" disabled={quizBusy}
              onClick={quizFromMessage}
              title="Generate a structured quiz from this message and open the player"
            >
              {quizBusy ? <Loader2 size={11} className="spin" /> : <HelpCircle size={11} />}
              {quizBusy ? ' Generating…' : ' Quiz me'}
            </button>
            <button
              type="button" className="msg__followup" disabled={practiceBusy}
              onClick={flashcardsFromMessage}
              title="Generate spaced-repetition flashcards and open Practice"
            >
              {practiceBusy ? <Loader2 size={11} className="spin" /> : <ListTree size={11} />}
              {practiceBusy ? ' Generating…' : ' Flashcards'}
            </button>
            <button
              type="button" className="msg__followup" disabled={isRunning}
              onClick={() => followUp(`Produce a clear labelled SVG diagram that visualises the single most important concept in the material below. Describe the diagram structure step-by-step.\n\n---\n\n${excerpt()}`)}
              title="Visualize"
            >
              <Sparkles size={11} /> Visualize
            </button>
          </div>
        )}

        {actionError && (
          <p className="small" style={{ color: 'var(--color-danger)', marginTop: 'var(--space-2)' }}>
            Couldn't generate: {actionError}
          </p>
        )}

        {msg.debate && (
          <div style={{ marginTop: 'var(--space-4)' }}>
            <DebateRound data={msg.debate} />
          </div>
        )}
      </div>

      {/* Save-as-note popover — only mounted when triggered, and only
          for tutor messages. Receives the raw message body so the user
          can trim the quoted text before committing. */}
      {isTutor && (
        <SaveNotePopover
          open={saveOpen}
          sourceBody={msg.body}
          sourceMessageId={msg.id}
          onClose={() => setSaveOpen(false)}
          onSaved={markSaved}
        />
      )}
    </div>
  );
}
