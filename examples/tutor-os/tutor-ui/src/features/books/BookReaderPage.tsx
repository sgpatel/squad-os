import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft, ChevronLeft, ChevronRight, RefreshCw, AlertCircle, Search,
  Sparkles, BookOpen, FileText, StickyNote, Highlighter, X,
  MessageSquare, Eraser, ZoomIn, ZoomOut, Maximize2,
  ChevronsLeft, ChevronsRight,
} from 'lucide-react';
import * as pdfjsLib from 'pdfjs-dist';
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

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

// ── Local types ───────────────────────────────────────────────────

interface PageRects {
  page: number;
  /** Per-line rects in that page's wrapper-relative CSS coordinates. */
  rects: { left: number; top: number; width: number; height: number }[];
}

/**
 * In continuous-scroll mode, a selection can span any number of pages,
 * so we capture rects keyed by page and remember the first page the
 * selection started on (drives the AI chapter binding + the "save as
 * note" page label).
 */
interface SelectionState {
  text: string;
  /** Toolbar anchor — absolute coords inside the scrolling content. */
  anchor: { left: number; top: number };
  /** Per-page rect groups; ordered by ascending page. */
  pages: PageRects[];
  firstPage: number;
  /** Scale at capture time — drives correct rescale on persisted highlights. */
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
 * `<BookReaderPage />` — continuous-scroll PDF reader for uploaded books.
 *
 * <h2>Layout</h2>
 * Pages stack vertically inside {@code .reader__stage} (the single
 * scroll container). Each page wrapper carries a stable fixed size
 * computed from the page's natural dimensions × the current scale,
 * so the scrollbar is honest even before a page has rendered.
 *
 * <h2>Virtualisation</h2>
 * An {@code IntersectionObserver} (rooted on the stage with a generous
 * rootMargin) marks each page as visible-or-not. The render effect
 * draws the canvas + text layer + concept highlights for visible
 * pages and clears them for pages that scroll far away — keeping
 * memory bounded on a 300-page textbook.
 *
 * <h2>Active page</h2>
 * Driven by a scroll handler that picks whichever page's wrapper
 * contains the upper-third of the viewport. That value:
 *   • drives the editable page-number input
 *   • is mirrored into the URL ({@code ?page=N})
 *   • is the chapter-binding hint passed to {@code /explain}
 *
 * <h2>Cross-page selection</h2>
 * The browser's native selection already spans across stacked DOM
 * nodes. We walk {@code range.getClientRects()} and bucket each rect
 * into the page whose wrapper contains its centre — so a paragraph
 * that wraps from page 12 → 13 produces two {@code BookHighlight}
 * records, one per page, both carrying the same text.
 */
export function BookReaderPage() {
  const { bookId } = useParams<{ bookId: string }>();
  const [search, setSearch] = useSearchParams();
  const navigate = useNavigate();
  const learnerId = useAuth(s => s.currentUser()?.id ?? DEMO_LEARNER_ID);

  const initialPage   = Number(search.get('page')) || 1;
  const highlight     = (search.get('highlight') ?? '').trim();
  const chapterNumber = Number(search.get('chapter')) || 1;

  // ── Document state ───────────────────────────────────────────
  const [totalPages, setTotalPages] = useState(0);
  // Natural width/height of page 1 at scale=1 — used as the placeholder
  // size for every page until its actual size is measured during
  // render. PDFs within one book are 95% uniform so this rarely needs
  // correcting; when it does the per-page override slips in.
  const [baseSize, setBaseSize] = useState<{ w: number; h: number }>({ w: 612, h: 792 });
  const [pageSizes, setPageSizes] = useState<Map<number, { w: number; h: number }>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState<string | null>(null);
  const [pdfTitle, setPdfTitle] = useState('');

  // ── View state ───────────────────────────────────────────────
  const [activePage, setActivePage] = useState(initialPage);
  const [pageInput,  setPageInput]  = useState(String(initialPage));
  const [zoom, setZoom] = useState(1);
  const [containerWidth, setContainerWidth] = useState(0);
  // Pages currently inside (or near) the viewport — the render set.
  const [visiblePages, setVisiblePages] = useState<Set<number>>(new Set([initialPage]));

  // ── Refs ─────────────────────────────────────────────────────
  const docRef          = useRef<pdfjsLib.PDFDocumentProxy | null>(null);
  const stageRef        = useRef<HTMLDivElement | null>(null);
  const pageWrapRefs    = useRef<Map<number, HTMLDivElement>>(new Map());
  const pageRenderRefs  = useRef<Map<number, HTMLDivElement>>(new Map());
  /** pageNum → scale at which it's currently rendered. Triggers re-render when scale changes. */
  const renderedAt      = useRef<Map<number, number>>(new Map());
  const observerRef     = useRef<IntersectionObserver | null>(null);
  const didInitialScroll = useRef(false);

  // ── Selection / AI ───────────────────────────────────────────
  const [selection, setSelection] = useState<SelectionState | null>(null);
  const [aiPanel,   setAiPanel]   = useState<AiPanelState | null>(null);
  const highlights = useBookHighlights(bookId);
  const [color, setColor] = useState<HighlightColor>('yellow');

  // ── Cross-feature integrations ───────────────────────────────
  const createNote = useNotes(s => s.create);
  const startTutor = usePipeline(s => s.start);

  // ── Derived scale ────────────────────────────────────────────
  // fit-width scale (auto) × user zoom multiplier = effective scale.
  const fitScale = baseSize.w > 0 && containerWidth > 0
    ? Math.max(0.4, (containerWidth - 32) / baseSize.w)
    : 1;
  const scale = Math.min(4, Math.max(0.3, fitScale * zoom));

  // ── Sync URL + page input on active-page changes ─────────────
  useEffect(() => {
    const current = Number(search.get('page')) || 1;
    if (current !== activePage) {
      const next = new URLSearchParams(search);
      next.set('page', String(activePage));
      setSearch(next, { replace: true });
    }
    setPageInput(String(activePage));
  }, [activePage]); // eslint-disable-line

  // ── Load the document ────────────────────────────────────────
  useEffect(() => {
    if (!bookId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    didInitialScroll.current = false;
    renderedAt.current.clear();

    const url = api.books.pdfRawUrl(learnerId, bookId);
    const task = pdfjsLib.getDocument({ url, withCredentials: false });
    task.promise.then(async (doc) => {
      if (cancelled) { doc.destroy(); return; }
      docRef.current = doc;
      setTotalPages(doc.numPages);
      try {
        const p1 = await doc.getPage(1);
        const vp = p1.getViewport({ scale: 1 });
        if (!cancelled) setBaseSize({ w: vp.width, h: vp.height });
      } catch { /* fall back to default placeholder */ }
      doc.getMetadata().then((m) => {
        const info = m?.info as { Title?: string } | undefined;
        if (!cancelled && info?.Title) setPdfTitle(info.Title);
      }).catch(() => { /* ignore */ });
      if (!cancelled) setLoading(false);
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
      task.destroy();
      docRef.current?.destroy();
      docRef.current = null;
      renderedAt.current.clear();
    };
  }, [bookId, learnerId]);

  // ── Track the stage's content width ──────────────────────────
  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    setContainerWidth(stage.clientWidth);
    const ro = new ResizeObserver((entries) => {
      for (const e of entries) {
        const w = e.contentRect.width;
        setContainerWidth(prev => Math.abs(prev - w) > 1 ? w : prev);
      }
    });
    ro.observe(stage);
    return () => ro.disconnect();
  }, [loading]);

  // ── IntersectionObserver — bucket pages into visible / not ───
  useEffect(() => {
    const stage = stageRef.current;
    if (!stage || totalPages === 0) return;
    const obs = new IntersectionObserver((entries) => {
      setVisiblePages(prev => {
        const next = new Set(prev);
        let changed = false;
        for (const e of entries) {
          const n = Number((e.target as HTMLElement).dataset.page);
          if (e.isIntersecting) {
            if (!next.has(n)) { next.add(n); changed = true; }
          } else if (next.has(n)) {
            next.delete(n); changed = true;
          }
        }
        return changed ? next : prev;
      });
    }, { root: stage, rootMargin: '600px 0px' }); // one screen of buffer each way
    observerRef.current = obs;
    for (const el of pageWrapRefs.current.values()) obs.observe(el);
    return () => { obs.disconnect(); observerRef.current = null; };
  }, [totalPages, loading]);

  // ── Active-page from scroll ──────────────────────────────────
  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    let raf = 0;
    const onScroll = () => {
      if (raf) return;
      raf = window.requestAnimationFrame(() => {
        raf = 0;
        // Bias the upper third: a page is "active" as soon as the top
        // of its body crosses the viewport's upper third. Same trick
        // long-form reader apps use to avoid flicker between pages.
        const probe = stage.scrollTop + stage.clientHeight / 3;
        for (const [n, el] of pageWrapRefs.current) {
          const top = el.offsetTop;
          const bot = top + el.offsetHeight;
          if (probe >= top && probe < bot) {
            setActivePage(prev => prev === n ? prev : n);
            return;
          }
        }
      });
    };
    stage.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      stage.removeEventListener('scroll', onScroll);
      if (raf) window.cancelAnimationFrame(raf);
    };
  }, [totalPages, loading]);

  // ── Initial scroll to ?page=N once layout is ready ──────────
  useEffect(() => {
    if (loading || error || totalPages === 0) return;
    if (didInitialScroll.current) return;
    if (containerWidth === 0) return;
    const wrap = pageWrapRefs.current.get(initialPage);
    const stage = stageRef.current;
    if (!wrap || !stage) return;
    stage.scrollTo({ top: Math.max(0, wrap.offsetTop - 16), behavior: 'auto' });
    didInitialScroll.current = true;
  }, [loading, error, totalPages, containerWidth, scale, initialPage]);

  // ── Render visible pages (and GC distant ones) ──────────────
  useEffect(() => {
    if (loading || error) return;
    const doc = docRef.current;
    if (!doc) return;
    let cancelled = false;
    const work = async () => {
      // Render anything visible that isn't already at the current scale.
      for (const n of visiblePages) {
        if (cancelled) return;
        if (renderedAt.current.get(n) === scale) continue;
        try {
          await renderOnePage(doc, n, scale);
        } catch {
          // A single page failing shouldn't take the whole reader down.
        }
      }
      // GC: clear pages that scrolled out so the canvas memory drops.
      for (const n of Array.from(renderedAt.current.keys())) {
        if (!visiblePages.has(n)) {
          clearOnePage(n);
          renderedAt.current.delete(n);
        }
      }
    };
    void work();
    return () => { cancelled = true; };
  }, [visiblePages, scale, highlight, loading, error]); // eslint-disable-line

  // When the scale changes (zoom, resize) we need every currently-
  // rendered page to redraw — flush the rendered-at cache so the
  // render effect above re-renders them on its next pass.
  useEffect(() => {
    renderedAt.current.clear();
    // Force the render effect to re-run by touching visiblePages.
    setVisiblePages(prev => new Set(prev));
  }, [scale]);

  // ── Window resize → re-measure (ResizeObserver picks it up) ──

  // ── renderOnePage / clearOnePage ─────────────────────────────

  const renderOnePage = useCallback(async (
    doc: pdfjsLib.PDFDocumentProxy, n: number, atScale: number,
  ) => {
    const wrap = pageWrapRefs.current.get(n);
    const render = pageRenderRefs.current.get(n);
    if (!wrap || !render) return;
    const page = await doc.getPage(n);
    const vp = page.getViewport({ scale: atScale });

    // Lock dimensions on the wrapper (drives the document height +
    // the scrollbar accuracy) and the render slot.
    wrap.style.width   = `${vp.width}px`;
    wrap.style.height  = `${vp.height}px`;
    render.style.width  = `${vp.width}px`;
    render.style.height = `${vp.height}px`;

    // If this page's natural size differs from page 1 (common in
    // mixed-orientation books), record the override so its placeholder
    // is correct the next time the user scrolls past it before render.
    const naturalW = vp.width / atScale;
    const naturalH = vp.height / atScale;
    setPageSizes(prev => {
      const existing = prev.get(n);
      const same = existing
        ? Math.abs(existing.w - naturalW) < 0.5 && Math.abs(existing.h - naturalH) < 0.5
        : Math.abs(baseSize.w - naturalW) < 0.5 && Math.abs(baseSize.h - naturalH) < 0.5;
      if (same) return prev;
      const m = new Map(prev);
      m.set(n, { w: naturalW, h: naturalH });
      return m;
    });

    // canvas (raster fill)
    let canvas = render.querySelector(':scope > canvas.reader__canvas') as HTMLCanvasElement | null;
    if (!canvas) {
      canvas = document.createElement('canvas');
      canvas.className = 'reader__canvas';
      render.appendChild(canvas);
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    canvas.width  = Math.floor(vp.width  * dpr);
    canvas.height = Math.floor(vp.height * dpr);
    canvas.style.width  = `${vp.width}px`;
    canvas.style.height = `${vp.height}px`;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    await page.render({ canvasContext: ctx, viewport: vp }).promise;

    // concept-highlight layer
    let conceptLayer = render.querySelector(':scope > .reader__highlights') as HTMLDivElement | null;
    if (!conceptLayer) {
      conceptLayer = document.createElement('div');
      conceptLayer.className = 'reader__highlights';
      conceptLayer.setAttribute('aria-hidden', 'true');
      render.appendChild(conceptLayer);
    }
    await drawConceptHighlights(conceptLayer, page, vp, highlight);

    // Selectable text layer.
    let textLayer = render.querySelector(':scope > .reader__text-layer') as HTMLDivElement | null;
    if (!textLayer) {
      textLayer = document.createElement('div');
      textLayer.className = 'reader__text-layer';
      textLayer.dataset.page = String(n);
      render.appendChild(textLayer);
    }
    while (textLayer.firstChild) textLayer.removeChild(textLayer.firstChild);
    textLayer.style.width  = `${vp.width}px`;
    textLayer.style.height = `${vp.height}px`;
    textLayer.style.setProperty('--scale-factor', String(atScale));
    const textContent = await page.getTextContent();
    const tl = new pdfjsLib.TextLayer({
      textContentSource: textContent,
      container: textLayer,
      viewport: vp,
    });
    await tl.render();

    renderedAt.current.set(n, atScale);
  }, [baseSize, highlight]);

  const clearOnePage = useCallback((n: number) => {
    const render = pageRenderRefs.current.get(n);
    if (!render) return;
    const canvas = render.querySelector(':scope > canvas.reader__canvas') as HTMLCanvasElement | null;
    if (canvas) { canvas.width = 1; canvas.height = 1; canvas.style.width = '0px'; canvas.style.height = '0px'; }
    const t = render.querySelector(':scope > .reader__text-layer');
    if (t) while (t.firstChild) t.removeChild(t.firstChild);
    const c = render.querySelector(':scope > .reader__highlights');
    if (c) while (c.firstChild) c.removeChild(c.firstChild);
  }, []);

  // ── Jump-to-page (smooth scroll) ────────────────────────────
  const goToPage = useCallback((nRaw: number) => {
    const n = Math.min(totalPages || nRaw, Math.max(1, Math.floor(nRaw)));
    const wrap = pageWrapRefs.current.get(n);
    const stage = stageRef.current;
    if (wrap && stage) stage.scrollTo({ top: Math.max(0, wrap.offsetTop - 16), behavior: 'smooth' });
  }, [totalPages]);

  // ── Keyboard shortcuts ──────────────────────────────────────
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement) return;
      const stage = stageRef.current;
      switch (e.key) {
        case 'Escape':    setSelection(null); setAiPanel(null); break;
        case 'ArrowLeft':
        case 'PageUp':    e.preventDefault(); goToPage(activePage - 1); break;
        case 'ArrowRight':
        case 'PageDown':  e.preventDefault(); goToPage(activePage + 1); break;
        case 'Home':      e.preventDefault(); goToPage(1); break;
        case 'End':       e.preventDefault(); goToPage(totalPages || 1); break;
        case '+':
        case '=':         e.preventDefault(); setZoom(z => Math.min(3, +(z + 0.1).toFixed(2))); break;
        case '-':
        case '_':         e.preventDefault(); setZoom(z => Math.max(0.5, +(z - 0.1).toFixed(2))); break;
        case '0':         e.preventDefault(); setZoom(1); break;
        case ' ': {
          if (!stage) break;
          e.preventDefault();
          stage.scrollBy({ top: (e.shiftKey ? -1 : 1) * stage.clientHeight * 0.9, behavior: 'smooth' });
          break;
        }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [activePage, totalPages, goToPage]);

  // ── Ctrl/Cmd-wheel zoom ─────────────────────────────────────
  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    const onWheel = (e: WheelEvent) => {
      if (!(e.ctrlKey || e.metaKey)) return;
      e.preventDefault();
      const step = e.deltaY < 0 ? 0.08 : -0.08;
      setZoom(z => Math.min(3, Math.max(0.5, +(z + step).toFixed(2))));
    };
    stage.addEventListener('wheel', onWheel, { passive: false });
    return () => stage.removeEventListener('wheel', onWheel);
  }, [loading, error]);

  // ── Selection capture (multi-page aware) ───────────────────
  const captureSelection = useCallback(() => {
    const sel = window.getSelection();
    if (!sel || sel.isCollapsed) { setSelection(null); return; }
    const range = sel.getRangeAt(0);
    const text = sel.toString().trim();
    if (text.length < 2) { setSelection(null); return; }

    const stage = stageRef.current;
    if (!stage) { setSelection(null); return; }

    // Selection has to overlap at least one text-layer to count.
    let inTextLayer = false;
    for (const render of pageRenderRefs.current.values()) {
      const tl = render.querySelector(':scope > .reader__text-layer');
      if (tl && (tl.contains(range.startContainer) || tl.contains(range.endContainer)
                 || tl.contains(range.commonAncestorContainer))) {
        inTextLayer = true;
        break;
      }
    }
    if (!inTextLayer) { setSelection(null); return; }

    const clientRects = Array.from(range.getClientRects())
      .filter(r => r.width > 0.5 && r.height > 0.5);
    if (clientRects.length === 0) { setSelection(null); return; }

    // Snapshot wrapper rects once — every clientRect is bucketed by
    // its centre against these.
    const wrapperRects: { page: number; r: DOMRect }[] = [];
    for (const [page, wrap] of pageWrapRefs.current) {
      wrapperRects.push({ page, r: wrap.getBoundingClientRect() });
    }
    const perPage = new Map<number, PageRects['rects']>();
    for (const r of clientRects) {
      const cx = r.left + r.width / 2;
      const cy = r.top  + r.height / 2;
      for (const { page, r: wr } of wrapperRects) {
        if (cx >= wr.left && cx <= wr.right && cy >= wr.top && cy <= wr.bottom) {
          const arr = perPage.get(page) ?? [];
          arr.push({
            left:   r.left - wr.left,
            top:    r.top  - wr.top,
            width:  r.width,
            height: r.height,
          });
          perPage.set(page, arr);
          break;
        }
      }
    }
    if (perPage.size === 0) { setSelection(null); return; }

    const pages: PageRects[] = Array.from(perPage.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([page, rects]) => ({ page, rects }));
    const firstPage = pages[0]!.page;

    // Toolbar anchor in scroll-content coordinates.
    const stageRect = stage.getBoundingClientRect();
    const first = clientRects[0]!;
    const anchor = {
      left: first.left - stageRect.left + first.width / 2 + stage.scrollLeft,
      top:  Math.max(8, first.top - stageRect.top + stage.scrollTop - 8),
    };

    setSelection({ text, anchor, pages, firstPage, scale });
  }, [scale]);

  // mouseup → recompute (deferred a frame so the browser has finalised
  // the selection range).
  useEffect(() => {
    const onUp = () => window.requestAnimationFrame(captureSelection);
    window.addEventListener('mouseup', onUp);
    return () => window.removeEventListener('mouseup', onUp);
  }, [captureSelection]);

  // ── Selection actions ──────────────────────────────────────
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

  /**
   * Persist the active selection as one {@code BookHighlight} per
   * intersected page — so multi-page selections render correctly on
   * each page they crossed.
   */
  const persistHighlight = () => {
    if (!selection) return;
    for (const { page, rects } of selection.pages) {
      highlights.add({
        page,
        text:  selection.text,
        color,
        boxes: rects,
        scale: selection.scale,
      });
    }
    clearSelection();
  };

  const saveAsNote = () => {
    if (!selection) return;
    const last = selection.pages[selection.pages.length - 1]!.page;
    const pageLabel = selection.pages.length > 1
      ? `pp. ${selection.firstPage}–${last}`
      : `p.${selection.firstPage}`;
    createNote({
      title: `Highlight · ${pdfTitle || 'Reading'} · ${pageLabel}`,
      body:  `> ${selection.text}\n\n_From ${pdfTitle || 'the book'}, ${pageLabel}._`,
      tags:  ['book', 'highlight'],
    });
    clearSelection();
  };

  const askTutor = () => {
    if (!selection || !bookId) return;
    stashReturnTo({
      label:  `Back to ${pdfTitle || 'reading'} · p.${selection.firstPage}`,
      url:    `/books/${bookId}/read?page=${selection.firstPage}&chapter=${chapterNumber}`,
      source: 'book',
    });
    const prompt =
      `From the textbook (${pdfTitle || 'current chapter'}, page ${selection.firstPage}):\n\n` +
      `> ${selection.text}\n\nPlease explain this and quiz me to check I understand.`;
    clearSelection();
    void startTutor(prompt);
    navigate('/tutor');
  };

  // ── Page-wrapper ref registration (so the observer can watch) ──
  const registerWrap = useCallback((n: number) => (el: HTMLDivElement | null) => {
    const prev = pageWrapRefs.current.get(n);
    const obs = observerRef.current;
    if (prev && obs) obs.unobserve(prev);
    if (el) {
      pageWrapRefs.current.set(n, el);
      if (obs) obs.observe(el);
    } else {
      pageWrapRefs.current.delete(n);
    }
  }, []);
  const registerRender = useCallback((n: number) => (el: HTMLDivElement | null) => {
    if (el) pageRenderRefs.current.set(n, el);
    else pageRenderRefs.current.delete(n);
  }, []);

  // ── Misc ────────────────────────────────────────────────────
  const goBack = () => {
    if (window.history.length > 1) navigate(-1);
    else if (bookId) navigate(`/books/${bookId}`);
    else navigate('/books');
  };

  // Highlights on the active page (used by the header "Clear (N)" pill).
  const activePageHighlights = useMemo(
    () => highlights.items.filter(h => h.page === activePage),
    [highlights.items, activePage],
  );

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
          <button type="button" className="btn btn--ghost btn--sm"
                  onClick={() => goToPage(1)}
                  disabled={activePage <= 1}
                  aria-label="First page" title="First page (Home)">
            <ChevronsLeft size={14} />
          </button>
          <button type="button" className="btn btn--ghost btn--sm"
                  onClick={() => goToPage(activePage - 1)}
                  disabled={activePage <= 1}
                  aria-label="Previous page" title="Previous page (←)">
            <ChevronLeft size={14} />
          </button>
          <form
            className="reader__page-jump"
            onSubmit={(e) => {
              e.preventDefault();
              const n = Number(pageInput);
              if (Number.isFinite(n) && n >= 1) goToPage(n);
              else setPageInput(String(activePage));
            }}
          >
            <input
              type="text"
              inputMode="numeric"
              className="reader__page-input"
              value={pageInput}
              onChange={(e) => setPageInput(e.target.value.replace(/[^0-9]/g, ''))}
              onFocus={(e) => e.currentTarget.select()}
              onBlur={() => setPageInput(String(activePage))}
              aria-label="Page number"
            />
            <span className="reader__page-of">/ {totalPages || '—'}</span>
          </form>
          <button type="button" className="btn btn--ghost btn--sm"
                  onClick={() => goToPage(activePage + 1)}
                  disabled={totalPages > 0 && activePage >= totalPages}
                  aria-label="Next page" title="Next page (→)">
            <ChevronRight size={14} />
          </button>
          <button type="button" className="btn btn--ghost btn--sm"
                  onClick={() => goToPage(totalPages || 1)}
                  disabled={totalPages > 0 && activePage >= totalPages}
                  aria-label="Last page" title="Last page (End)">
            <ChevronsRight size={14} />
          </button>
        </div>

        <div className="reader__zoom" role="group" aria-label="Zoom">
          <button type="button" className="btn btn--ghost btn--sm"
                  onClick={() => setZoom(z => Math.max(0.5, +(z - 0.1).toFixed(2)))}
                  aria-label="Zoom out" title="Zoom out (−)">
            <ZoomOut size={13} />
          </button>
          <button type="button" className="reader__zoom-level"
                  onClick={() => setZoom(1)} title="Reset to fit width (0)">
            {Math.round(zoom * 100)}%
          </button>
          <button type="button" className="btn btn--ghost btn--sm"
                  onClick={() => setZoom(z => Math.min(3, +(z + 0.1).toFixed(2)))}
                  aria-label="Zoom in" title="Zoom in (+)">
            <ZoomIn size={13} />
          </button>
          <button type="button" className="btn btn--ghost btn--sm"
                  onClick={() => setZoom(1)}
                  aria-label="Fit width" title="Fit width">
            <Maximize2 size={13} />
          </button>
        </div>

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
          {activePageHighlights.length > 0 && (
            <button
              type="button"
              className="btn btn--ghost btn--sm reader__clear-hl"
              onClick={() => {
                activePageHighlights.forEach(h => highlights.remove(h.id));
              }}
              title={`Clear highlights on page ${activePage}`}
            >
              <Eraser size={12} /> Clear ({activePageHighlights.length})
            </button>
          )}
        </div>
      </header>

      <main className="reader__stage" ref={stageRef}>
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
          <>
            <div className="reader__doc">
              {Array.from({ length: totalPages }, (_, i) => {
                const n = i + 1;
                const sz = pageSizes.get(n) ?? baseSize;
                const w = Math.round(sz.w * scale);
                const h = Math.round(sz.h * scale);
                const pageHighlights = highlights.items.filter(hl => hl.page === n);
                return (
                  <div
                    key={n}
                    ref={registerWrap(n)}
                    className={`reader__page${n === activePage ? ' is-active' : ''}`}
                    data-page={n}
                    style={{ width: `${w}px`, height: `${h}px` }}
                  >
                    {/* Imperative render slot (canvas + text-layer + concept). */}
                    <div
                      className="reader__page-render"
                      ref={registerRender(n)}
                      style={{ width: `${w}px`, height: `${h}px` }}
                    />
                    {/* React-owned manual highlight layer. Sits behind
                        the text layer (which lives inside the render
                        slot) so it never eats selection. */}
                    <div className="reader__manual-layer" aria-hidden>
                      {pageHighlights.flatMap(h => {
                        const k = scale / (h.scale || 1);
                        return h.boxes.map((b, i) => (
                          <div
                            key={`${h.id}-${i}`}
                            className={`reader__manual-hl reader__manual-hl--${h.color}`}
                            style={{
                              left:   `${b.left   * k}px`,
                              top:    `${b.top    * k}px`,
                              width:  `${b.width  * k}px`,
                              height: `${b.height * k}px`,
                            }}
                          />
                        ));
                      })}
                    </div>
                    <div className="reader__page-label">{n}</div>
                  </div>
                );
              })}
            </div>

            {selection && (
              <SelectionToolbar
                anchor={selection.anchor}
                multiPage={selection.pages.length > 1}
                onExplain={() => runExplain('explain')}
                onSimplify={() => runExplain('simplify')}
                onDefine={() => runExplain('define')}
                onHighlight={persistHighlight}
                onSaveNote={saveAsNote}
                onAsk={askTutor}
                onClose={clearSelection}
              />
            )}
          </>
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
  multiPage: boolean;
  onExplain:   () => void;
  onSimplify:  () => void;
  onDefine:    () => void;
  onHighlight: () => void;
  onSaveNote:  () => void;
  onAsk:       () => void;
  onClose:     () => void;
}

function SelectionToolbar({
  anchor, multiPage, onExplain, onSimplify, onDefine,
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
      {multiPage && (
        <span className="reader__tool-badge" title="Selection spans multiple pages">
          multi-page
        </span>
      )}
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
