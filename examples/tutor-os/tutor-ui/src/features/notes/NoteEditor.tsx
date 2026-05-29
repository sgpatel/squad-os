import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import {
  ArrowLeft, Trash2, Tag as TagIcon, Save,
  Heading1, Heading2, Heading3,
  Bold, Italic, Code, Code2, Quote,
  List, ListOrdered, CheckSquare, Link2, Minus,
  Eye, Pencil, Columns, Sparkles, Brain, Zap,
  HelpCircle, Image as ImageIcon, Download, Copy,
  Plus, Clock, ListTree, Table, Sigma, Info, ChevronDown
} from 'lucide-react';
import { useNotes } from '@/store/notes';
import { useWorkspace } from '@/store/workspace';
import { usePipeline } from '@/store/pipeline';
import { Button } from '@/components/ui/Button';
import { Tag } from '@/components/ui/Misc';
import { Markdown } from '@/components/ui/Markdown';
import { fmtRelative } from '@/lib/format';

/**
 * Note editor — title + markdown-source textarea + live preview + AI actions.
 *
 * Modes:
 *   edit    → just the textarea
 *   split   → textarea on the left, preview on the right (desktop only)
 *   preview → just the rendered preview
 *
 * Sidebar: an auto-generated outline (# ## ### headings). Clicking a
 *   heading scrolls the textarea to that line.
 *
 * AI actions dock: wires the note into the existing pipeline (Summarize,
 *   Simplify, Deep explain, Quiz me, Make flashcards, Visualize). Each
 *   action crafts a prompt, fires `pipeline.start`, and navigates to the
 *   home thread so the learner can watch the agent run live and save the
 *   result back as a linked note.
 *
 * Export: copy as markdown or download .md. No round-trip — lives in
 *   localStorage just like the rest of the notes store.
 */
type MdAction =
  | { kind: 'wrap';       before: string; after: string; placeholder: string }
  | { kind: 'linePrefix'; prefix: string; placeholder: string }
  | { kind: 'block';      before: string; after: string; placeholder: string }
  | { kind: 'insert';     text: string }
  | { kind: 'hr' };

type Mode = 'edit' | 'split' | 'preview';

export function NoteEditor() {
  const { noteId } = useParams<{ noteId: string }>();
  const navigate = useNavigate();
  const noteList = useNotes(s => s.notes);
  const update   = useNotes(s => s.update);
  const remove   = useNotes(s => s.remove);

  const subjects      = useWorkspace(s => s.workspaceSubjects)();
  const startPipeline = usePipeline(s => s.start);
  const isRunning     = usePipeline(s => s.isRunning);

  const note = noteList.find(n => n.id === noteId);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [title,     setTitle]     = useState(note?.title ?? '');
  const [body,      setBody]      = useState(note?.body  ?? '');
  const [tagInput,  setTagInput]  = useState('');
  const [tags,      setTags]      = useState(note?.tags  ?? []);
  const [subjectId, setSubjectId] = useState<string | ''>(note?.subjectId ?? '');
  const [savedAt,   setSavedAt]   = useState<number | null>(null);
  const [mode,      setMode]      = useState<Mode>('split');

  // Overlay menus — closed unless toggled.
  const [paletteOpen,  setPaletteOpen]  = useState(false);
  const [aiOpen,       setAiOpen]       = useState(false);
  const [exportOpen,   setExportOpen]   = useState(false);
  const [paletteQuery, setPaletteQuery] = useState('');

  // Re-sync when switching notes.
  useEffect(() => {
    if (!note) return;
    setTitle(note.title);
    setBody(note.body);
    setTags(note.tags);
    setSubjectId(note.subjectId ?? '');
  }, [note?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Responsive: collapse to single pane on narrow screens.
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 900px)');
    const fix = () => { if (mq.matches && mode === 'split') setMode('edit'); };
    fix();
    mq.addEventListener('change', fix);
    return () => mq.removeEventListener('change', fix);
  }, [mode]);

  // Debounced autosave.
  useEffect(() => {
    if (!note) return;
    const handle = window.setTimeout(() => {
      update(note.id, { title, body, tags, subjectId: subjectId || undefined });
      setSavedAt(Date.now());
    }, 1500);
    return () => clearTimeout(handle);
  }, [title, body, tags, subjectId, note, update]);

  // Close any popover on escape.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { setPaletteOpen(false); setAiOpen(false); setExportOpen(false); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // Derived: outline (flat list of headings with indent levels).
  const outline = useMemo(() => {
    const out: Array<{ level: number; text: string; line: number }> = [];
    body.split('\n').forEach((l, i) => {
      const m = /^(#{1,6})\s+(.+)$/.exec(l);
      if (m && m[1] && m[2]) out.push({ level: m[1].length, text: m[2].trim(), line: i });
    });
    return out;
  }, [body]);

  if (!note) {
    return (
      <div className="page-react">
        <p className="muted">Note not found.</p>
        <Link to="/notes" className="btn btn--sm mt-3">Back to notes</Link>
      </div>
    );
  }

  const wordCount   = body.trim().split(/\s+/).filter(Boolean).length;
  const readMinutes = Math.max(1, Math.round(wordCount / 220)); // ~220 wpm

  const addTag = () => {
    const t = tagInput.trim().toLowerCase();
    if (!t || tags.includes(t)) return;
    setTags([...tags, t]); setTagInput('');
  };

  /**
   * Apply a markdown transform at the current textarea selection.
   *
   *   wrap       → `**selection**`
   *   linePrefix → `# ` / `- ` / `> ` on each selected line
   *   block      → fenced on own lines
   *   insert     → literal string at the caret
   *   hr         → horizontal rule on a new line
   */
  const applyMd = (action: MdAction) => {
    const ta = textareaRef.current;
    if (!ta) return;
    if (mode === 'preview') setMode('split');
    const start = ta.selectionStart;
    const end   = ta.selectionEnd;
    const value = ta.value;
    const selected = value.slice(start, end);

    let next = value;
    let newStart = start;
    let newEnd   = end;

    if (action.kind === 'wrap') {
      const inner = selected || action.placeholder;
      const insertion = action.before + inner + action.after;
      next = value.slice(0, start) + insertion + value.slice(end);
      newStart = start + action.before.length;
      newEnd   = newStart + inner.length;
    } else if (action.kind === 'linePrefix') {
      const lineStart = value.lastIndexOf('\n', start - 1) + 1;
      const lineEndIdx = value.indexOf('\n', end);
      const lineEnd = lineEndIdx === -1 ? value.length : lineEndIdx;
      const block = value.slice(lineStart, lineEnd) || action.placeholder;
      const prefixed = block.split('\n').map(l => action.prefix + l).join('\n');
      next = value.slice(0, lineStart) + prefixed + value.slice(lineEnd);
      newStart = lineStart;
      newEnd   = lineStart + prefixed.length;
    } else if (action.kind === 'block') {
      const inner = selected || action.placeholder;
      const leadingNl  = (start === 0 || value[start - 1] === '\n') ? '' : '\n';
      const trailingNl = (end === value.length || value[end] === '\n') ? '' : '\n';
      const insertion = `${leadingNl}${action.before}\n${inner}\n${action.after}${trailingNl}`;
      next = value.slice(0, start) + insertion + value.slice(end);
      const innerStart = start + leadingNl.length + action.before.length + 1;
      newStart = innerStart;
      newEnd   = innerStart + inner.length;
    } else if (action.kind === 'insert') {
      const leadingNl = (start === 0 || value[start - 1] === '\n') ? '' : '\n';
      const insertion = leadingNl + action.text;
      next = value.slice(0, start) + insertion + value.slice(end);
      newStart = newEnd = start + insertion.length;
    } else if (action.kind === 'hr') {
      const leadingNl = (start === 0 || value[start - 1] === '\n') ? '' : '\n';
      const insertion = `${leadingNl}\n---\n`;
      next = value.slice(0, start) + insertion + value.slice(end);
      newStart = newEnd = start + insertion.length;
    }

    setBody(next);
    requestAnimationFrame(() => {
      ta.focus();
      ta.setSelectionRange(newStart, newEnd);
    });
  };

  /** Cmd/Ctrl shortcuts + slash-key opens the block palette. */
  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    const mod = e.metaKey || e.ctrlKey;
    if (mod) {
      const key = e.key.toLowerCase();
      if (key === 'b') { e.preventDefault(); applyMd({ kind: 'wrap', before: '**', after: '**', placeholder: 'bold text' }); }
      else if (key === 'i') { e.preventDefault(); applyMd({ kind: 'wrap', before: '_', after: '_', placeholder: 'italic' }); }
      else if (key === 'e') { e.preventDefault(); applyMd({ kind: 'wrap', before: '`', after: '`', placeholder: 'code' }); }
      else if (key === 'k') { e.preventDefault(); applyMd({ kind: 'wrap', before: '[', after: '](https://)', placeholder: 'link text' }); }
      else if (key === 'p') { e.preventDefault(); setPaletteOpen(true); setPaletteQuery(''); }
      return;
    }
    // A lone "/" at the start of a line or after whitespace opens the palette.
    if (e.key === '/') {
      const ta = e.currentTarget;
      const at = ta.selectionStart;
      const prev = at > 0 ? ta.value[at - 1] : '\n';
      if (!prev || prev === '\n' || prev === ' ') {
        e.preventDefault();
        setPaletteOpen(true);
        setPaletteQuery('');
      }
    }
  };

  /** Scroll textarea so a given line sits near the top. */
  const jumpToLine = (line: number) => {
    const ta = textareaRef.current;
    if (!ta) return;
    if (mode === 'preview') setMode('split');
    const lines = ta.value.split('\n');
    let charPos = 0;
    for (let i = 0; i < line && i < lines.length; i++) charPos += (lines[i] ?? '').length + 1;
    ta.focus();
    ta.setSelectionRange(charPos, charPos);
    // Approximate scroll — textarea has line-height × lines.
    const lineHeightPx = parseFloat(getComputedStyle(ta).lineHeight) || 22;
    ta.scrollTop = Math.max(0, line * lineHeightPx - 60);
  };

  // ── AI actions ────────────────────────────────────────────────────────
  const triggerAi = (prompt: string) => {
    void startPipeline(prompt);
    setAiOpen(false);
    navigate('/'); // home thread renders the stream
  };

  const noteContext = () => {
    const trimmed = body.trim();
    const clipped = trimmed.length > 4000 ? trimmed.slice(0, 4000) + '\n…' : trimmed;
    return `Title: ${title || 'Untitled note'}\n\n${clipped}`;
  };

  const aiActions = [
    {
      icon: <Sparkles size={13} />, label: 'Summarize',
      hint:  'TL;DR + key points',
      run:   () => triggerAi(`Summarize this note in a concise TL;DR followed by 5 key bullet points.\n\n---\n\n${noteContext()}`)
    },
    {
      icon: <Zap size={13} />, label: 'Simplify',
      hint:  'Explain like I’m 14',
      run:   () => triggerAi(`Explain the core ideas in this note as if I'm 14 years old — plain language, everyday analogies, no jargon.\n\n---\n\n${noteContext()}`)
    },
    {
      icon: <Brain size={13} />, label: 'Deep explain',
      hint:  'Rigorous deep dive',
      run:   () => triggerAi(`Give a rigorous, graduate-level deep dive on the concepts in this note. Include derivations, edge cases, and at least two cited sources.\n\n---\n\n${noteContext()}`)
    },
    {
      icon: <HelpCircle size={13} />, label: 'Quiz me',
      hint:  '5 Q mixed-difficulty',
      run:   () => triggerAi(`Build me a 5-question mixed-difficulty quiz covering the concepts in this note. Number each question and include the answer key at the bottom.\n\n---\n\n${noteContext()}`)
    },
    {
      icon: <ListTree size={13} />, label: 'Flashcards',
      hint:  '8 Q/A pairs',
      run:   () => triggerAi(`Generate 8 spaced-repetition flashcards (Q/A pairs) from this note. Format each as "Q: …\\nA: …" separated by blank lines.\n\n---\n\n${noteContext()}`)
    },
    {
      icon: <ImageIcon size={13} />, label: 'Visualize',
      hint:  'Diagram the key concept',
      run:   () => triggerAi(`Produce a clear labelled SVG diagram that visualises the single most important concept in this note. Describe the diagram structure step-by-step so it can be rendered.\n\n---\n\n${noteContext()}`)
    },
  ];

  // ── Export ────────────────────────────────────────────────────────────
  const toMarkdown = () => {
    const subj = subjects.find(s => s.id === subjectId);
    const header = [
      `# ${title || 'Untitled note'}`,
      subj ? `_Subject: ${subj.name}_` : null,
      tags.length ? `_Tags: ${tags.map(t => `#${t}`).join(' ')}_` : null,
      ''
    ].filter(Boolean).join('\n');
    return `${header}\n${body}`;
  };

  const copyMarkdown = async () => {
    try {
      await navigator.clipboard.writeText(toMarkdown());
      setSavedAt(Date.now()); // piggyback on existing "Saved" indicator
    } catch { /* clipboard blocked */ }
    setExportOpen(false);
  };

  const downloadMarkdown = () => {
    const blob = new Blob([toMarkdown()], { type: 'text/markdown;charset=utf-8' });
    const url  = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const slug = (title || 'note').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'note';
    a.href = url;
    a.download = `${slug}.md`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    setExportOpen(false);
  };

  // ── Insert-block palette items ────────────────────────────────────────
  const paletteItems: Array<{ label: string; hint: string; icon: React.ReactNode; action: MdAction }> = [
    { label: 'Heading 1',  hint: '# Big title',                 icon: <Heading1 size={13} />,
      action: { kind: 'linePrefix', prefix: '# ',   placeholder: 'Heading' } },
    { label: 'Heading 2',  hint: '## Section',                  icon: <Heading2 size={13} />,
      action: { kind: 'linePrefix', prefix: '## ',  placeholder: 'Heading' } },
    { label: 'Heading 3',  hint: '### Sub-section',             icon: <Heading3 size={13} />,
      action: { kind: 'linePrefix', prefix: '### ', placeholder: 'Heading' } },
    { label: 'Bulleted list', hint: '- item',                   icon: <List size={13} />,
      action: { kind: 'linePrefix', prefix: '- ',   placeholder: 'Item' } },
    { label: 'Numbered list', hint: '1. item',                  icon: <ListOrdered size={13} />,
      action: { kind: 'linePrefix', prefix: '1. ',  placeholder: 'Item' } },
    { label: 'Task list',  hint: '- [ ] todo',                  icon: <CheckSquare size={13} />,
      action: { kind: 'linePrefix', prefix: '- [ ] ', placeholder: 'Task' } },
    { label: 'Quote',      hint: '> blockquote',                icon: <Quote size={13} />,
      action: { kind: 'linePrefix', prefix: '> ',   placeholder: 'Quote' } },
    { label: 'Code block', hint: '```fenced code```',           icon: <Code2 size={13} />,
      action: { kind: 'block', before: '```', after: '```', placeholder: '// your code' } },
    { label: 'Math block', hint: '$$ LaTeX $$',                 icon: <Sigma size={13} />,
      action: { kind: 'block', before: '$$', after: '$$', placeholder: 'E = mc^2' } },
    { label: 'Table',      hint: '3-col starter',               icon: <Table size={13} />,
      action: { kind: 'insert',
        text: '| Column A | Column B | Column C |\n|---|---|---|\n| …        | …        | …        |\n' } },
    { label: 'Callout',    hint: '> [!note] Important',         icon: <Info size={13} />,
      action: { kind: 'insert',
        text: '> [!note]\n> Write your callout body here.\n' } },
    { label: 'Divider',    hint: '---',                         icon: <Minus size={13} />,
      action: { kind: 'hr' } },
    { label: 'Link',       hint: '[text](url)',                 icon: <Link2 size={13} />,
      action: { kind: 'wrap', before: '[', after: '](https://)', placeholder: 'link text' } },
  ];
  const filteredPalette = paletteItems.filter(i =>
    !paletteQuery ||
    i.label.toLowerCase().includes(paletteQuery.toLowerCase()) ||
    i.hint.toLowerCase().includes(paletteQuery.toLowerCase())
  );
  const runPalette = (action: MdAction) => {
    setPaletteOpen(false);
    requestAnimationFrame(() => applyMd(action));
  };

  // ── Toolbar button shortcuts (compact) ───────────────────────────────
  const quickButtons: Array<{ icon: React.ReactNode; title: string; action: MdAction }> = [
    { icon: <Heading1 size={13} />, title: 'H1',     action: { kind: 'linePrefix', prefix: '# ',   placeholder: 'Heading' } },
    { icon: <Heading2 size={13} />, title: 'H2',     action: { kind: 'linePrefix', prefix: '## ',  placeholder: 'Heading' } },
    { icon: <Heading3 size={13} />, title: 'H3',     action: { kind: 'linePrefix', prefix: '### ', placeholder: 'Heading' } },
    { icon: <Bold size={13} />,     title: 'Bold (⌘B)',   action: { kind: 'wrap', before: '**', after: '**', placeholder: 'bold text' } },
    { icon: <Italic size={13} />,   title: 'Italic (⌘I)', action: { kind: 'wrap', before: '_',  after: '_',  placeholder: 'italic' } },
    { icon: <Code size={13} />,     title: 'Inline code (⌘E)', action: { kind: 'wrap', before: '`',  after: '`',  placeholder: 'code' } },
    { icon: <Code2 size={13} />,    title: 'Code block',  action: { kind: 'block', before: '```', after: '```', placeholder: '// your code' } },
    { icon: <Quote size={13} />,    title: 'Quote',       action: { kind: 'linePrefix', prefix: '> ', placeholder: 'Quoted text' } },
    { icon: <List size={13} />,     title: 'List',        action: { kind: 'linePrefix', prefix: '- ', placeholder: 'Item' } },
    { icon: <CheckSquare size={13} />, title: 'Task',     action: { kind: 'linePrefix', prefix: '- [ ] ', placeholder: 'Task' } },
    { icon: <Link2 size={13} />,    title: 'Link (⌘K)',   action: { kind: 'wrap', before: '[', after: '](https://)', placeholder: 'link text' } },
  ];

  return (
    <div className="page-react note-page">
      <Link to="/notes" className="small muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginBottom: 12 }}>
        <ArrowLeft size={12} /> Notes
      </Link>

      <div className="note-editor">
        {/* Top status + actions bar */}
        <div className="note-editor__toolbar">
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Save size={12} />
            {savedAt ? `Saved ${fmtRelative(savedAt)}` : 'Autosaves on type'}
          </span>
          <span style={{ flex: 1 }} />

          {/* Mode switcher */}
          <div className="md-mode-toggle" role="tablist" aria-label="Editor mode">
            <button type="button" role="tab" aria-selected={mode === 'edit'}
              className={'md-mode-toggle__btn' + (mode === 'edit' ? ' md-mode-toggle__btn--on' : '')}
              onClick={() => setMode('edit')}>
              <Pencil size={12} /> Edit
            </button>
            <button type="button" role="tab" aria-selected={mode === 'split'}
              className={'md-mode-toggle__btn' + (mode === 'split' ? ' md-mode-toggle__btn--on' : '')}
              onClick={() => setMode('split')}>
              <Columns size={12} /> Split
            </button>
            <button type="button" role="tab" aria-selected={mode === 'preview'}
              className={'md-mode-toggle__btn' + (mode === 'preview' ? ' md-mode-toggle__btn--on' : '')}
              onClick={() => setMode('preview')}>
              <Eye size={12} /> Preview
            </button>
          </div>

          {/* AI actions menu */}
          <div className="menu-anchor">
            <Button
              size="sm"
              variant="primary"
              leading={<Sparkles size={12} />}
              trailing={<ChevronDown size={12} />}
              onClick={() => { setAiOpen(o => !o); setExportOpen(false); }}
              disabled={isRunning}
            >
              AI
            </Button>
            {aiOpen && (
              <div className="menu-pop" role="menu" aria-label="AI actions">
                <div className="menu-pop__label">Use this note to…</div>
                {aiActions.map(a => (
                  <button
                    key={a.label}
                    type="button"
                    role="menuitem"
                    className="menu-pop__item"
                    onClick={a.run}
                    disabled={isRunning || !body.trim()}
                  >
                    <span className="menu-pop__icon">{a.icon}</span>
                    <span className="menu-pop__text">
                      <span className="menu-pop__title">{a.label}</span>
                      <span className="menu-pop__hint">{a.hint}</span>
                    </span>
                  </button>
                ))}
                {!body.trim() && <div className="menu-pop__label">Add some content first.</div>}
              </div>
            )}
          </div>

          {/* Export menu */}
          <div className="menu-anchor">
            <Button
              size="sm"
              variant="ghost"
              leading={<Download size={12} />}
              trailing={<ChevronDown size={12} />}
              onClick={() => { setExportOpen(o => !o); setAiOpen(false); }}
            >
              Export
            </Button>
            {exportOpen && (
              <div className="menu-pop" role="menu" aria-label="Export">
                <button type="button" role="menuitem" className="menu-pop__item" onClick={copyMarkdown}>
                  <span className="menu-pop__icon"><Copy size={13} /></span>
                  <span className="menu-pop__text">
                    <span className="menu-pop__title">Copy as Markdown</span>
                    <span className="menu-pop__hint">Ready to paste</span>
                  </span>
                </button>
                <button type="button" role="menuitem" className="menu-pop__item" onClick={downloadMarkdown}>
                  <span className="menu-pop__icon"><Download size={13} /></span>
                  <span className="menu-pop__text">
                    <span className="menu-pop__title">Download .md</span>
                    <span className="menu-pop__hint">Plain markdown file</span>
                  </span>
                </button>
              </div>
            )}
          </div>

          <Button variant="ghost" size="sm" leading={<Trash2 size={12} />}
            onClick={() => { if (confirm('Delete this note?')) { remove(note.id); navigate('/notes'); } }}>
            Delete
          </Button>
        </div>

        <input
          className="note-editor__title"
          value={title}
          onChange={e => setTitle(e.target.value)}
          placeholder="Title"
          aria-label="Note title"
        />

        <div className="savenote__subjects" role="radiogroup" aria-label="Subject" style={{ marginBottom: 'var(--space-3)' }}>
          <button
            type="button"
            role="radio"
            aria-checked={!subjectId}
            className={'subj-chip' + (!subjectId ? ' subj-chip--on' : '')}
            style={{ ['--subj-color' as any]: 'var(--color-text-subtle)' }}
            onClick={() => setSubjectId('')}
          >
            No subject
          </button>
          {subjects.map(s => {
            const on = subjectId === s.id;
            return (
              <button
                key={s.id}
                type="button"
                role="radio"
                aria-checked={on}
                className={'subj-chip' + (on ? ' subj-chip--on' : '')}
                style={{ ['--subj-color' as any]: s.color }}
                onClick={() => setSubjectId(s.id)}
              >
                {s.name}
              </button>
            );
          })}
        </div>

        {/* Formatting toolbar (hidden in preview) */}
        {mode !== 'preview' && (
          <div className="md-toolbar" role="toolbar" aria-label="Formatting">
            {quickButtons.map((b, i) => (
              <button
                key={i}
                type="button"
                className="md-toolbar__btn"
                title={b.title}
                aria-label={b.title}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => applyMd(b.action)}
              >
                {b.icon}
              </button>
            ))}
            <span className="md-toolbar__divider" />
            <button
              type="button"
              className="md-toolbar__btn md-toolbar__btn--wide"
              title="Insert block (⌘P or type / on a new line)"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => { setPaletteOpen(true); setPaletteQuery(''); }}
            >
              <Plus size={13} /> <span>Insert block</span>
            </button>
          </div>
        )}

        {/* Main body: outline sidebar + editor/preview surface */}
        <div className={'note-editor__surface note-editor__surface--' + mode}>
          {/* Outline sidebar */}
          <aside className="note-outline" aria-label="Outline">
            <div className="note-outline__label">
              <ListTree size={12} /> Outline
            </div>
            {outline.length === 0 ? (
              <p className="note-outline__empty small muted">
                Add a heading (# Heading) to see your outline here.
              </p>
            ) : (
              <ul className="note-outline__list">
                {outline.map((h, i) => (
                  <li key={i} style={{ paddingLeft: (h.level - 1) * 10 }}>
                    <button
                      type="button"
                      className={'note-outline__item note-outline__item--l' + h.level}
                      onClick={() => jumpToLine(h.line)}
                      title={h.text}
                    >
                      {h.text}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </aside>

          {/* Editor / preview panes */}
          <div className="note-editor__panes">
            {mode !== 'preview' && (
              <textarea
                ref={textareaRef}
                className="note-editor__textarea"
                value={body}
                onChange={(e) => setBody(e.target.value)}
                onKeyDown={onKeyDown}
                placeholder={
                  '# Heading\n\nWrite in markdown. Press / to insert a block,\n' +
                  '⌘B for bold, ⌘I for italic, ⌘E for code, ⌘K for links.\n\n' +
                  'Use the AI menu above to summarize, quiz, or visualize this note.'
                }
                spellCheck
              />
            )}
            {mode !== 'edit' && (
              <div className="note-editor__preview">
                {body.trim()
                  ? <Markdown>{body}</Markdown>
                  : <p className="muted small">Nothing to preview yet. Switch to Edit and start writing.</p>}
              </div>
            )}
          </div>
        </div>

        {/* Footer: tags + stats */}
        <div className="note-editor__status">
          <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
            <TagIcon size={12} />
            {tags.map(t => (
              <Tag key={t}>
                {t}
                <button onClick={() => setTags(tags.filter(x => x !== t))} aria-label={`Remove ${t}`}
                  style={{ marginLeft: 4, background: 'none', border: 'none', color: 'inherit', cursor: 'pointer' }}>×</button>
              </Tag>
            ))}
            <input
              value={tagInput}
              onChange={e => setTagInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
              placeholder="add tag"
              style={{ background: 'transparent', border: 'none', outline: 'none', color: 'var(--color-text-muted)', fontSize: 11, width: 80 }}
            />
          </div>
          <div className="row" style={{ gap: 10 }}>
            <span className="small muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <Clock size={11} /> {readMinutes} min read
            </span>
            <span>{wordCount} word{wordCount === 1 ? '' : 's'}</span>
          </div>
        </div>
      </div>

      {/* Insert-block palette */}
      {paletteOpen && (
        <div
          className="savenote-backdrop"
          onClick={(e) => { if (e.target === e.currentTarget) setPaletteOpen(false); }}
          role="dialog" aria-modal="true" aria-label="Insert block"
        >
          <div className="palette">
            <div className="palette__head">
              <Plus size={14} />
              <input
                autoFocus
                className="palette__input"
                value={paletteQuery}
                placeholder="Search blocks — heading, list, code, math, table…"
                onChange={(e) => setPaletteQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    const first = filteredPalette[0];
                    if (first) runPalette(first.action);
                  }
                }}
              />
            </div>
            <ul className="palette__list">
              {filteredPalette.length === 0 ? (
                <li className="palette__empty muted small">No matches.</li>
              ) : filteredPalette.map((p, i) => (
                <li key={i}>
                  <button
                    type="button"
                    className="palette__item"
                    onClick={() => runPalette(p.action)}
                  >
                    <span className="palette__icon">{p.icon}</span>
                    <span className="palette__text">
                      <span className="palette__title">{p.label}</span>
                      <span className="palette__hint">{p.hint}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}
