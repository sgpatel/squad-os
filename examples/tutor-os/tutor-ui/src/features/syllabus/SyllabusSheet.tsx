import { useEffect, useRef, useState } from 'react';
import { Sparkles, ClipboardPaste, Save, X, RefreshCw, BookOpen } from 'lucide-react';
import { api } from '@/lib/api';
import { usePipeline } from '@/store/pipeline';
import { useWorkspace } from '@/store/workspace';
import { useAuth } from '@/store/auth';

/**
 * `<SyllabusSheet />` — modal sheet that lets the learner either ask
 * the tutor to <b>suggest</b> a syllabus for the active topic or
 * <b>paste</b> their own. Saving routes through {@code POST /api/syllabus/save},
 * which writes the syllabus onto the backend session profile so the
 * CurriculumPlannerAgent respects it on the next planning step.
 *
 * The two-tab layout mirrors the gate the user actually faces — "I want
 * one" vs. "I have one" — and shares the save button so flows converge.
 *
 * Modal pattern follows {@link SaveNotePopover}: `.savenote-backdrop` +
 * `.savenote` form, click-outside / Escape to close.
 */
export function SyllabusSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const sessionId         = usePipeline(s => s.sessionId);
  const activeSubjectId   = useWorkspace(s => s.activeSubjectId);
  const getSubject        = useWorkspace(s => s.getSubject);
  const cachedSyllabus    = useWorkspace(s => s.activeSyllabus);
  const setActiveSyllabus = useWorkspace(s => s.setActiveSyllabus);
  const userLevel         = useAuth(s => s.currentUser()?.level ?? 'higher-sec');

  const subject = activeSubjectId ? getSubject(activeSubjectId)?.name ?? '' : '';

  // Tab state — local; we deliberately don't push it to the URL so that
  // closing + reopening the sheet always lands on Suggest first.
  const [tab, setTab] = useState<'suggest' | 'paste'>('suggest');

  // Form state. Topic and level are editable — pasting a syllabus from
  // a different course is a legitimate use case, and the learner may
  // want to re-suggest at a different level.
  const [topic,    setTopic]    = useState('');
  const [level,    setLevel]    = useState(mapLevelToBackend(userLevel));
  const [chapters, setChapters] = useState('');
  const [rationale,setRationale]= useState('');
  const [busy,     setBusy]     = useState(false);
  const [error,    setError]    = useState<string | null>(null);

  // Hydrate from the cached/active syllabus on open.
  useEffect(() => {
    if (!open) return;
    if (cachedSyllabus) {
      setTopic(cachedSyllabus.topic ?? '');
      setLevel(cachedSyllabus.level ?? mapLevelToBackend(userLevel));
      setChapters(cachedSyllabus.chapters ?? '');
      setRationale(cachedSyllabus.rationale ?? '');
      return;
    }
    if (sessionId) {
      // Pull whatever the backend has stored for this session so we
      // don't clobber an earlier save by opening with an empty form.
      api.syllabus.get(sessionId)
        .then((s) => {
          if (!s) return;
          setActiveSyllabus(s);
          setTopic(s.topic);
          setLevel(s.level);
          setChapters(s.chapters);
          setRationale(s.rationale ?? '');
        })
        .catch(() => { /* 404 / network: just stay with the empty form */ });
    }
  }, [open, sessionId, cachedSyllabus, setActiveSyllabus, userLevel]);

  // Close on Escape — same affordance as SaveNotePopover.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const firstFieldRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (open) firstFieldRef.current?.focus();
  }, [open]);

  if (!open) return null;

  // ── Actions ──────────────────────────────────────────────────────

  const runSuggest = async () => {
    setError(null);
    setBusy(true);
    try {
      const s = await api.syllabus.suggest({
        sessionId: sessionId ?? undefined,
        subject:   subject || undefined,
        topic:     topic || undefined,
        level:     level || undefined,
      });
      setTopic(s.topic);
      setLevel(s.level);
      setChapters(s.chapters);
      setRationale(s.rationale ?? '');
      setTab('paste'); // jump to the editable view so the learner can refine
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const saveSyllabus = async () => {
    if (!sessionId) {
      setError('No active session — start a chat first, then save your syllabus.');
      return;
    }
    if (!chapters.trim()) {
      setError('Please add at least one chapter before saving.');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const saved = await api.syllabus.save(sessionId, {
        subject: subject || undefined,
        topic, level, chapters, rationale,
        // Source intentionally omitted — backend stamps CUSTOM when the
        // learner pasted, SUGGESTED stays if it came from the agent.
      });
      setActiveSyllabus(saved);
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  // ── Render ───────────────────────────────────────────────────────

  return (
    <div
      className="savenote-backdrop"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="syllabus-title"
    >
      <div className="savenote" style={{ maxWidth: 640 }}>
        <header className="savenote__head">
          <div className="savenote__head-title" id="syllabus-title">
            <BookOpen size={14} /> Syllabus — {subject || '(no subject)'}
          </div>
          <button
            type="button"
            className="savenote__close"
            onClick={onClose}
            aria-label="Close"
          >
            <X size={14} />
          </button>
        </header>

        {/* Tabs */}
        <div className="savenote__body" style={{ paddingBottom: 0 }}>
          <div className="syllabus-tabs" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'suggest'}
              className={'syllabus-tab' + (tab === 'suggest' ? ' syllabus-tab--on' : '')}
              onClick={() => setTab('suggest')}
            >
              <Sparkles size={13} /> Suggest a syllabus
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'paste'}
              className={'syllabus-tab' + (tab === 'paste' ? ' syllabus-tab--on' : '')}
              onClick={() => setTab('paste')}
            >
              <ClipboardPaste size={13} /> Paste my syllabus
            </button>
          </div>
        </div>

        <div className="savenote__body">
          {/* Topic + level — shared between both tabs */}
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 12 }}>
            <label className="field">
              <span className="field__label">Topic</span>
              <input
                ref={firstFieldRef}
                className="field__input"
                value={topic}
                onChange={(e) => setTopic(e.target.value)}
                placeholder={subject ? `e.g. Trigonometry` : 'Pick a subject first'}
                autoComplete="off"
              />
            </label>
            <label className="field">
              <span className="field__label">Level</span>
              <select
                className="field__input"
                value={level}
                onChange={(e) => setLevel(e.target.value)}
              >
                <option value="PRIMARY">Primary</option>
                <option value="MIDDLE_SCHOOL">Middle school</option>
                <option value="SENIOR_SCHOOL">Senior school</option>
                <option value="UNIVERSITY">University</option>
                <option value="EDUCATOR">Educator</option>
                <option value="PROFESSIONAL">Professional</option>
              </select>
            </label>
          </div>

          {/* Tab body */}
          {tab === 'suggest' ? (
            <div className="syllabus-suggest" style={{ marginTop: 8 }}>
              <p className="muted" style={{ fontSize: 12, lineHeight: 1.5 }}>
                The tutor will draft 4–8 chapters with bullet sub-points,
                strictly inside <b>{subject || 'the active subject'}</b> at the
                level above. You'll be able to review and edit before saving.
              </p>
              <button
                type="button"
                className="btn btn--primary"
                onClick={runSuggest}
                disabled={busy || !subject || !topic.trim()}
              >
                <RefreshCw size={13} /> {busy ? 'Drafting…' : 'Draft a syllabus'}
              </button>
              {chapters && (
                <p className="muted" style={{ fontSize: 11, marginTop: 8 }}>
                  Draft ready — switch to <i>Paste my syllabus</i> to review and save.
                </p>
              )}
            </div>
          ) : (
            <>
              <label className="field">
                <span className="field__label">Chapters (one per line, "- " for bullets)</span>
                <textarea
                  className="field__textarea"
                  value={chapters}
                  onChange={(e) => setChapters(e.target.value)}
                  rows={12}
                  placeholder={
                    'Chapter 1: Introduction\n- Key idea 1\n- Key idea 2\nChapter 2: …'
                  }
                  spellCheck={false}
                />
              </label>
              <label className="field">
                <span className="field__label">Rationale (optional)</span>
                <input
                  className="field__input"
                  value={rationale}
                  onChange={(e) => setRationale(e.target.value)}
                  placeholder="One-sentence reason this scope fits the learner."
                />
              </label>
            </>
          )}

          {error && (
            <div role="alert" className="muted" style={{ color: 'var(--color-danger)', fontSize: 12 }}>
              {error}
            </div>
          )}
        </div>

        <footer className="savenote__foot">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn--primary"
            onClick={saveSyllabus}
            disabled={busy || !chapters.trim() || !sessionId}
            title={!sessionId ? 'Start a chat first to save a syllabus to it' : undefined}
          >
            <Save size={13} /> {busy ? 'Saving…' : 'Save syllabus'}
          </button>
        </footer>
      </div>
    </div>
  );
}

/**
 * Translate the auth store's casual level vocabulary
 * ("primary" / "middle" / "higher-sec" / "college" / "pro") into the
 * backend's enum strings the LearnerProfile expects.
 */
function mapLevelToBackend(level: string): string {
  switch (level) {
    case 'primary':    return 'PRIMARY';
    case 'middle':     return 'MIDDLE_SCHOOL';
    case 'higher-sec': return 'SENIOR_SCHOOL';
    case 'college':    return 'UNIVERSITY';
    case 'pro':        return 'PROFESSIONAL';
    default:           return 'SENIOR_SCHOOL';
  }
}
