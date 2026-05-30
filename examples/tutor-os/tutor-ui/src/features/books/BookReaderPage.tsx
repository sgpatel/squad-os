import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft, ChevronLeft, ChevronRight, RefreshCw, AlertCircle, Search,
  Sparkles, BookOpen, FileText, StickyNote, Highlighter, X,
  MessageSquare,
} from 'lucide-react';
import * as pdfjsLib from 'pdfjs-dist';
// Vite turns this into a static asset URL the browser can fetch on demand.
// pdfjs-dist v4 requires the worker to be loaded as a separate module —
// pointing GlobalWorkerOptions.workerSrc at this URL is the standard
// Vite-idiomatic wiring.
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import { api, DEMO_LEARNER_ID } from '@/lib/api';
import { useAuth } from '@/store/auth';
import { useNotes } from '@/store/notes';
import { usePipeline } from '@/store/pipeline';
import { stashReturnTo } from '@/lib/returnTo';
import { Markdown } from '@/components/ui/Markdown';
import {
  useBookHighlights, HIGHLIGHT_COLORS,
  type HighlightColor,
} from './useBookHighlights';

// Worker setup runs once at module load. Safe — pdfjs caches the
// assignment internally.
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

// ── Local types ───────────────────────────────────────────────────

/**
 * Snapshot of the active text selection used to drive the floating
 * toolbar + downstream AI actions. The {@code rects} array preserves
 * each client rect at capture time so the "Highlight" action can
 * persist multi-line selections accurately.
 */
interface SelectionState {
  text: string;
  /** Anchor in container-relative coordinates for toolbar positioning. */
  anchor: { left: number; top: number };
  /** Per-line rectangles in page-wrapper coordinates (CSS pixels). */
  rects: { left: number; top: number; width: number; height: number }[];
  /** Page number the selection lives on. */
  page: number;
  /** Render scale at capture time — needed to rescale on redraw. */
  scale: number;
}

interface AiPanelState {
  mode: 'explain' | 'simplify' | 'define';
  selection: string;
  loading: boolean;
  markdown: string;
  error: string | null;
}

/**
 * `<BookReaderPage />` — in-app PDF reader for uploaded textbooks.
 *
 * <p>Advanced features layered on the base reader:
 *   • Selectable text via pdfjs TextLayer.
 *   • Floating selection toolbar (Explain / Simplify / Define /
 *     Highlight / Save note / Ask tutor).
 *   • Inline AI popover that calls {@code /chapter/:n/explain} and
 *     renders the markdown response without leaving the reader.
 *   • Persistent manual highlights (localStorage) layered over the
 *     page, palette-aware, removable on click.
 *
 * <h2>URL contract</h2>
 *   /books/:bookId/read?page=N&chapter=M&highlight=concept
 */
export function BookReaderPage() {
  const { bookId } = useParams<{ bookId: string }>();
  const [search, setSearch] = useSearchParams();
  const navigate = useNavigate();
  const learnerId = useAuth(s => s.currentUser()?.id ?? DEMO_LEARNER_ID);

  const initialPage      = Number(search.get('page')) || 1;
  const highlight        = (search.get('highlight') ?? '').trim();
  // Chapter number is what the chapter-coach page passed in stash;
  // selection-explain needs it to scope the LLM grounding to the
  // right chapter body. Falls back to 1 when the reader is opened
  // standalone (no chapter context).
  const chapterNumber = Number(search.get('chapter')) || 1;

  const [pageNum, setPageNum] = useState(initialPage);
  const [totalPages, setTotalPages] = useState(0);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState<string | null>(null);
  const [pdfTitle,   setPdfTitle]   = useState<string>('');

  // pdf.js document handle — kept across page changes so we don't
  // re-fetch the whole PDF when the learner clicks "next page."
  const docRef = useRef<pdfjsLib.PDFDocumentProxy | null>(null);
  const canvasRef         = useRef<HTMLCanvasElement | null>(null);
  const textLayerRef      = useRef<HTMLDivElement | null>(null);    // selectable text spans
  const conceptLayerRef   = useRef<HTMLDivElement | null>(null);    // auto-highlight (concept)
  const manualLayerRef    = useRef<HTMLDivElement | null>(null);    // persisted user highlights
  const pageWrapperRef    = useRef<HTMLDivElement | null>(null);    // selection-anchor reference
  const containerRef      = useRef<HTMLDivElement | null>(null);

  // Last render scale — captured at render time so a highlight saved
  // here can be rescaled correctly when the page redraws at a
  // different zoom.
  const [renderScale, setRenderScale] = useState(1);

  // Selection + AI panel state.
  const [selection, setSelection] = useState<SelectionState | null>(null);
  const [aiPanel,   setAiPanel]   = useState<AiPanelState | null>(null);

  // Manual highlights store (localStorage-backed).
  const highlights = useBookHighlights(bookId);
  const [color, setColor] = useState<HighlightColor>('yellow');

  // Cross-feature integrations.
  const createNote   = useNotes(s => s.create);
  const startTutor   = usePipeline(s => s.start);

  // ── Effects ─────────────────────────────────────────────────────

  // Sync URL when pageNum changes so the back button works as expected.
  useEffect(() => {
    const current = Number(search.get('page')) || 1;
    if (current !== pageNum) {
      const next = new URLSearchParams(search);
      next.set('page', String(pageNum));
      setSearch(next, { replace: true });
    }
  }, [pageNum]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load the document once.
  useEffect(() => {
    if (!bookId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    const url = api.books.pdfRawUrl(learnerId, bookId);
    const loadingTask = pdfjsLib.getDocument({ url, withCredentials: false });
    loadingTask.promise.then((doc) => {
      if (cancelled) { doc.destroy(); return; }
      docRef.current = doc;
      setTotalPages(doc.numPages);
      doc.getMetadata().then((m) => {
        const info = m?.info as { Title?: string } | undefined;
        if (!cancelled && info?.Title) setPdfTitle(info.Title);
      }).catch(() => { /* ignore */ });
      setLoading(false);
    }).catch((e) => {
      if (cancelled) return;
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg.includes('404')
        ? 'This book was uploaded before PDF storage shipped — re-upload it to view the original.'
        : msg);
      setLoading(false);
    });
    return () => {
      cancelled = true;
      loadingTask.destroy();
      docRef.current?.destroy();
      docRef.current = null;
    };
  }, [bookId, learnerId]);

  // Render the active page.
  useEffect(() => {
    if (!docRef.current || loading || error) return;
    const doc = docRef.current;
    let cancelled = false;
    const draw = async () => {
      try {
        const page = await doc.getPage(pageNum);
        if (cancelled) return;

        const container = containerRef.current;
        const targetWidth = container ? container.clientWidth - 16 : 800;
        const baseViewport = page.getViewport({ scale: 1 });
        const scale = Math.min(2, Math.max(0.5, targetWidth / baseViewport.width));
        const viewport = page.getViewport({ scale });
        setRenderScale(scale);

        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const dpr = Math.min(2, window.devicePixelRatio || 1);
        canvas.width  = Math.floor(viewport.width  * dpr);
        canvas.height = Math.floor(viewport.height * dpr);
        canvas.style.width  = `${viewport.width}px`;
        canvas.style.height = `${viewport.height}px`;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

        await page.render({ canvasContext: ctx, viewport }).promise;
        if (cancelled) return;

        // Size the page wrapper so absolute children share its origin.
        if (pageWrapperRef.current) {
          pageWrapperRef.current.style.width  = `${viewport.width}px`;
          pageWrapperRef.current.style.height = `${viewport.height}px`;
        }

        // Selectable text layer — built via pdfjs's TextLayer helper.
        const textLayer = textLayerRef.current;
        if (textLayer) {
          while (textLayer.firstChild) textLayer.removeChild(textLayer.firstChild);
          textLayer.style.width  = `${viewport.width}px`;
          textLayer.style.height = `${viewport.height}px`;
          const textContent = await page.getTextContent();
          const tl = new pdfjsLib.TextLayer({
            textContentSource: textContent,
            container: textLayer,
            viewport,
          });
          await tl.render();
        }
        if (cancelled) return;

        // Concept-highlight layer (auto highlight of the concept that
        // arrived via #highlight=...).
        await drawConceptHighlights(conceptLayerRef.current, page, viewport, highlight);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      }
    };
    void draw();
    return () => { cancelled = true; };
  }, [pageNum, highlight, loading, error]); // eslint-disable-line

  // Keyboard shortcuts.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement) return;
      if (e.key === 'Escape') { setSelection(null); setAiPanel(null); }
      if (e.key === 'ArrowLeft')  setPageNum(p => Math.max(1, p - 1));
      if (e.key === 'ArrowRight') setPageNum(p => Math.min(totalPages || 1, p + 1));
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [totalPages]);

  // Capture text selections inside the text layer and surface them as
  // a {@code SelectionState} so the toolbar can position itself.
  const captureSelection = useCallback(() => {
    const sel = window.getSelection();
    if (!sel || sel.isCollapsed) { setSelection(null); return; }
    const wrapper = pageWrapperRef.current;
    const textLayer = textLayerRef.current;
    if (!wrapper || !textLayer) return;
    // Only capture selections that originate inside the text layer.
    const range = sel.getRangeAt(0);
    if (!textLayer.contains(range.commonAncestorContainer)) {
      setSelection(null);
      return;
    }
    const text = sel.toString().trim();
    if (text.length < 2) { setSelection(null); return; }

    const wrapperRect = wrapper.getBoundingClientRect();
    const clientRects = Array.from(range.getClientRects())
      .filter(r => r.width > 0 && r.height > 0);
    if (clientRects.length === 0) { setSelection(null); return; }
    const rects = clientRects.map(r => ({
      left:   r.left - wrapperRect.left,
      top:    r.top  - wrapperRect.top,
      width:  r.width,
      height: r.height,
    }));
    // Anchor toolbar to the top of the selection — fall back gracefully
    // if mid-line. Subtract a chunk so the toolbar floats above text.
    const first = rects[0]!;
    const anchor = {
      left: first.left + first.width / 2,
      top:  Math.max(8, first.top - 8),
    };
    setSelection({ text, anchor, rects, page: pageNum, scale: renderScale });
  }, [pageNum, renderScale]);

  // mouseup → recompute selection. Done as a window listener so a
  // selection that ends outside the text layer (mouse released over
  // toolbar etc.) still updates correctly.
  useEffect(() => {
    const onUp = () => {
      // Defer to next frame so the browser has finalised the selection.
      window.requestAnimationFrame(captureSelection);
    };
    window.addEventListener('mouseup', onUp);
    return () => window.removeEventListener('mouseup', onUp);
  }, [captureSelection]);

  // ── Selection actions ───────────────────────────────────────────

  const clearSelection = () => {
    window.getSelection()?.removeAllRanges();
    setSelection(null);
  };

  const runExplain = async (mode: 'explain' | 'simplify' | 'define') => {
    if (!selection || !bookId) return;
    const sel = selection.text;
    setAiPanel({ mode, selection: sel, loading: true, markdown: '', error: null });
    try {
      const res = await api.books.explain(learnerId, bookId, chapterNumber, {
        selection: sel,
        mode,
      });
      setAiPanel({ mode, selection: sel, loading: false, markdown: res.markdown, error: null });
    } catch (e) {
      setAiPanel({
        mode, selection: sel, loading: false, markdown: '',
        error: e instanceof Error ? e.message : String(e),
      });
    }
  };

  const persistHighlight = () => {
    if (!selection) return;
    highlights.add({
      page:  selection.page,
      text:  selection.text,
      color,
      boxes: selection.rects,
      scale: selection.scale,
    });
    clearSelection();
  };

  const saveAsNote = () => {
    if (!selection) return;
    createNote({
      title: `Highlight · ${pdfTitle || 'Reading'} · p.${selection.page}`,
      body:  `> ${selection.text}\n\n_From ${pdfTitle || 'the book'}, page ${selection.page}._`,
      tags:  ['book', 'highlight'],
    });
    clearSelection();
  };

  const askTutor = async () => {
    if (!selection || !bookId) return;
    // Drop a breadcrumb so the tutor surface shows a "Back to reading"
    // chip — keeps the learner in flow.
    stashReturnTo({
      label:  `Back to ${pdfTitle || 'reading'} · p.${selection.page}`,
      url:    `/books/${bookId}/read?page=${selection.page}&chapter=${chapterNumber}`,
      source: 'book',
    });
    const prompt =
      `From the textbook (${pdfTitle || 'current chapter'}, page ${selection.page}):\n\n` +
      `> ${selection.text}\n\nPlease explain this and quiz me to check I understand.`;
    clearSelection();
    void startTutor(prompt);
    navigate('/tutor');
  };

  // ── Render helpers ──────────────────────────────────────────────

  const goBack = () => {
    if (window.history.length > 1) navigate(-1);
    else if (bookId) navigate(`/books/${bookId}`);
    else navigate('/books');
  };

  // Manual highlights on the current page (rescaled to current render).
  const pageHighlights = highlights.items.filter(h => h.page === pageNum);

  return (
    <div className="reader">
      <header className="reader__head">
        <button type="button" className="btn btn--ghost btn--sm" onClick={goBack}>
          <ArrowLeft size={13} /> Back
        </button>
        <h1 className="reader__title">{pdfTitle || 'Reading'}</h1>
        {highlight && (
          <span className="reader__highlight-info" title="Concept currently highlighted">
            <Search size={12} /> {highlight}
          </span>
        )}
        <div className="reader__pager">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setPageNum(p => Math.max(1, p - 1))}
            disabled={pageNum <= 1}
            aria-label="Previous page"
          >
            <ChevronLeft size={14} />
          </button>
          <span className="reader__page-indicator">
            {totalPages > 0 ? (<>Page {pageNum} of {totalPages}</>) : '—'}
          </span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setPageNum(p => Math.min(totalPages || 1, p + 1))}
            disabled={totalPages > 0 && pageNum >= totalPages}
            aria-label="Next page"
          >
            <ChevronRight size={14} />
          </button>
        </div>
        {/* Palette — sticks in the header so it's always available */}
        <div className="reader__palette" role="group" aria-label="Highlight color">
          {HIGHLIGHT_COLORS.map(c => (
            <button
              key={c}
              type="button"
              className={`reader__swatch reader__swatch--${c}${c === color ? ' is-active' : ''}`}
              onClick={() => setColor(c)}
              aria-label={`Use ${c} highlighter`}
              aria-pressed={c === color}
            />
          ))}
        </div>
      </header>

      <main className="reader__stage" ref={containerRef}>
        {loading ? (
          <div className="reader__empty">
            <RefreshCw size={26} className="spin" />
            <p>Loading PDF…</p>
          </div>
        ) : error ? (
          <div className="reader__empty">
            <AlertCircle size={26} />
            <p>{error}</p>
            <button type="button" className="btn" onClick={goBack}>
              <ArrowLeft size={13} /> Go back
            </button>
          </div>
        ) : (
          <div className="reader__page" ref={pageWrapperRef}>
            <canvas ref={canvasRef} className="reader__canvas" />
            {/* Manual highlights sit beneath the text layer so the
                user can still re-select text over an existing one. */}
            <div ref={manualLayerRef} className="reader__manual-layer" aria-hidden>
              {pageHighlights.flatMap(h => {
                const k = renderScale / (h.scale || 1);
                return h.boxes.map((b, i) => (
                  <button
                    key={`${h.id}-${i}`}
                    type="button"
                    className={`reader__manual-hl reader__manual-hl--${h.color}`}
                    style={{
                      left:   `${b.left   * k}px`,
                      top:    `${b.top    * k}px`,
                      width:  `${b.width  * k}px`,
                      height: `${b.height * k}px`,
                    }}
                    title={`Click to remove highlight: "${h.text.slice(0, 60)}"`}
                    onClick={(e) => { e.stopPropagation(); highlights.remove(h.id); }}
                  />
                ));
              })}
            </div>
            <div ref={conceptLayerRef} className="reader__highlights" aria-hidden />
            <div ref={textLayerRef} className="reader__text-layer" />

            {selection && (
              <SelectionToolbar
                anchor={selection.anchor}
                onExplain={() => runExplain('explain')}
                onSimplify={() => runExplain('simplify')}
                onDefine={() => runExplain('define')}
                onHighlight={persistHighlight}
                onSaveNote={saveAsNote}
                onAsk={askTutor}
                onClose={clearSelection}
              />
            )}
          </div>
        )}

        {aiPanel && (
          <ExplainPopover
            state={aiPanel}
            onClose={() => setAiPanel(null)}
            onRetry={() => runExplain(aiPanel.mode)}
          />
        )}
      </main>
    </div>
  );
}

// ── Selection toolbar ─────────────────────────────────────────────

interface SelectionToolbarProps {
  anchor: { left: number; top: number };
  onExplain:   () => void;
  onSimplify:  () => void;
  onDefine:    () => void;
  onHighlight: () => void;
  onSaveNote:  () => void;
  onAsk:       () => void;
  onClose:     () => void;
}

/**
 * Floating action menu pinned above the active text selection — same
 * pattern as iBooks / Notion / Apple Books. Translated -50% on X so
 * the toolbar centers on the anchor, then -100% on Y so it sits above.
 *
 * <p>Buttons stop propagation on mousedown — without that the browser
 * collapses the selection before the click event fires.
 */
function SelectionToolbar({
  anchor, onExplain, onSimplify, onDefine,
  onHighlight, onSaveNote, onAsk, onClose,
}: SelectionToolbarProps) {
  const swallow = (e: React.MouseEvent) => { e.preventDefault(); };
  return (
    <div
      className="reader__selection-toolbar"
      style={{ left: `${anchor.left}px`, top: `${anchor.top}px` }}
      onMouseDown={swallow}
      role="toolbar"
      aria-label="Selection actions"
    >
      <button type="button" className="reader__tool" onClick={onExplain} title="Explain (AI)">
        <Sparkles size={13} /> Explain
      </button>
      <button type="button" className="reader__tool" onClick={onSimplify} title="Simplify (AI)">
        <BookOpen size={13} /> Simplify
      </button>
      <button type="button" className="reader__tool" onClick={onDefine} title="Define (AI)">
        <FileText size={13} /> Define
      </button>
      <span className="reader__tool-sep" aria-hidden />
      <button type="button" className="reader__tool" onClick={onHighlight} title="Highlight selection">
        <Highlighter size={13} /> Highlight
      </button>
      <button type="button" className="reader__tool" onClick={onSaveNote} title="Save as note">
        <StickyNote size={13} /> Note
      </button>
      <button type="button" className="reader__tool" onClick={onAsk} title="Ask the tutor about this">
        <MessageSquare size={13} /> Ask
      </button>
      <span className="reader__tool-sep" aria-hidden />
      <button type="button" className="reader__tool reader__tool--icon" onClick={onClose} aria-label="Close">
        <X size={12} />
      </button>
    </div>
  );
}

// ── Explain popover ───────────────────────────────────────────────

interface ExplainPopoverProps {
  state: AiPanelState;
  onClose: () => void;
  onRetry: () => void;
}

/**
 * Side panel anchored to the reader stage. Shows the selection at top
 * as a quoted block so the learner remembers what they asked about,
 * then the markdown response below. Mode is reflected in the header
 * label.
 */
function ExplainPopover({ state, onClose, onRetry }: ExplainPopoverProps) {
  const label =
    state.mode === 'simplify' ? 'Simplified' :
    state.mode === 'define'   ? 'Definition' :
                                'Explanation';
  return (
    <aside className="reader__ai-panel" aria-label={`AI ${label}`}>
      <header className="reader__ai-head">
        <Sparkles size={13} />
        <span>{label}</span>
        <button type="button" className="reader__ai-close" onClick={onClose} aria-label="Close">
          <X size={12} />
        </button>
      </header>
      <blockquote className="reader__ai-selection">{state.selection}</blockquote>
      <div className="reader__ai-body">
        {state.loading ? (
          <div className="reader__ai-loading">
            <RefreshCw size={14} className="spin" /> Thinking…
          </div>
        ) : state.error ? (
          <div className="reader__ai-error">
            <AlertCircle size={14} /> {state.error}
            <button type="button" className="btn btn--ghost btn--sm" onClick={onRetry}>
              <RefreshCw size={12} /> Retry
            </button>
          </div>
        ) : (
          <Markdown>{state.markdown}</Markdown>
        )}
      </div>
    </aside>
  );
}

// ── Concept-highlight drawing (auto, from ?highlight=…) ───────────

/**
 * Position absolutely-placed highlight rectangles over every span of
 * the page's text content that case-insensitively matches the
 * highlight string. Cleared on every call so re-rendering with a
 * different highlight doesn't accumulate stale boxes.
 */
async function drawConceptHighlights(
  layer: HTMLDivElement | null,
  page: pdfjsLib.PDFPageProxy,
  viewport: pdfjsLib.PageViewport,
  needle: string,
): Promise<void> {
  if (!layer) return;
  while (layer.firstChild) layer.removeChild(layer.firstChild);
  layer.style.width  = `${viewport.width}px`;
  layer.style.height = `${viewport.height}px`;
  if (!needle) return;

  const target = needle.toLowerCase();
  const content = await page.getTextContent();
  type Item = { str: string; transform: number[]; width: number; height?: number };
  const items = (content.items as Item[]).filter(i => typeof i.str === 'string');

  for (const item of items) {
    if (!item.str) continue;
    const lower = item.str.toLowerCase();
    let idx = lower.indexOf(target);
    while (idx >= 0) {
      const t = item.transform;
      const a = t[0] ?? 0, d = t[3] ?? 0, e = t[4] ?? 0, f = t[5] ?? 0;
      const fontSize = Math.hypot(a, d) || 12;
      const perChar = item.width / Math.max(1, item.str.length);
      const x = e + perChar * idx;
      const y = f;
      const w = perChar * target.length;
      const h = fontSize;
      const p1 = viewport.convertToViewportPoint(x, y);
      const p2 = viewport.convertToViewportPoint(x + w, y + h);
      const vx  = p1[0] ?? 0, vy  = p1[1] ?? 0;
      const vx2 = p2[0] ?? 0, vy2 = p2[1] ?? 0;
      const left   = Math.min(vx, vx2);
      const top    = Math.min(vy, vy2);
      const width  = Math.abs(vx2 - vx);
      const height = Math.abs(vy2 - vy);

      const box = document.createElement('div');
      box.className = 'reader__hl';
      box.style.left   = `${left}px`;
      box.style.top    = `${top}px`;
      box.style.width  = `${width}px`;
      box.style.height = `${height}px`;
      layer.appendChild(box);

      idx = lower.indexOf(target, idx + target.length);
    }
  }
}
