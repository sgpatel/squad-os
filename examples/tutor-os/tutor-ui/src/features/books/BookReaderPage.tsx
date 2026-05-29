import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft, ChevronLeft, ChevronRight, RefreshCw, AlertCircle, Search,
} from 'lucide-react';
import * as pdfjsLib from 'pdfjs-dist';
// Vite turns this into a static asset URL the browser can fetch on demand.
// pdfjs-dist v4 requires the worker to be loaded as a separate module —
// pointing GlobalWorkerOptions.workerSrc at this URL is the standard
// Vite-idiomatic wiring.
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import { api, DEMO_LEARNER_ID } from '@/lib/api';
import { useAuth } from '@/store/auth';

// Worker setup runs once at module load. Safe — pdfjs caches the
// assignment internally.
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

/**
 * `<BookReaderPage />` — in-app PDF reader for uploaded textbooks.
 *
 * <p>Replaces the previous "Open PDF" → browser viewer path with an
 * embedded PDF.js renderer that fits the TutorOS chrome. The win:
 *
 *   • Highlights all instances of the active concept on the current
 *     page so the learner's eye lands on the right passage instantly.
 *   • Keeps the chapter navigation pattern (prev/next, jump to page)
 *     consistent with the rest of the BookCoach surface — no jarring
 *     handoff to the browser's PDF viewer with its own toolbar.
 *   • Works offline / self-hosted because pdfjs-dist + worker ship as
 *     part of the UI bundle (no Mozilla CDN dependency).
 *
 * <h2>URL contract</h2>
 *   /books/:bookId/read?page=N&highlight=concept
 *
 * Both query params optional. {@code page} defaults to 1.
 * {@code highlight} accepts any string; the renderer normalises and
 * matches case-insensitively against the page's text content.
 *
 * <h2>Rendering pipeline</h2>
 * <ol>
 *   <li>{@code pdfjs.getDocument(url)} streams the PDF from
 *       {@code /api/books/.../pdf} — same endpoint the browser-viewer
 *       path uses, so existing PDF persistence works unchanged.</li>
 *   <li>For the active page: render to a canvas, get text content,
 *       overlay an absolutely-positioned highlight div for each
 *       matching span.</li>
 *   <li>Re-render on page change / highlight change / resize.</li>
 * </ol>
 */
export function BookReaderPage() {
  const { bookId } = useParams<{ bookId: string }>();
  const [search, setSearch] = useSearchParams();
  const navigate = useNavigate();
  const learnerId = useAuth(s => s.currentUser()?.id ?? DEMO_LEARNER_ID);

  const initialPage      = Number(search.get('page')) || 1;
  const highlight        = (search.get('highlight') ?? '').trim();

  const [pageNum, setPageNum] = useState(initialPage);
  const [totalPages, setTotalPages] = useState(0);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState<string | null>(null);
  const [pdfTitle,   setPdfTitle]   = useState<string>('');

  // pdf.js document handle — kept across page changes so we don't
  // re-fetch the whole PDF when the learner clicks "next page."
  const docRef = useRef<pdfjsLib.PDFDocumentProxy | null>(null);
  const canvasRef    = useRef<HTMLCanvasElement | null>(null);
  const textLayerRef = useRef<HTMLDivElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  // Sync URL when pageNum changes so the back button works as expected.
  useEffect(() => {
    const current = Number(search.get('page')) || 1;
    if (current !== pageNum) {
      const next = new URLSearchParams(search);
      next.set('page', String(pageNum));
      setSearch(next, { replace: true });
    }
  }, [pageNum]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load the document once. Cleanup destroys the pdf.js instance so
  // we don't leak workers when the learner navigates away.
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
      // Pull title from metadata for the toolbar (falls back gracefully).
      doc.getMetadata().then((m) => {
        const info = m?.info as { Title?: string } | undefined;
        if (!cancelled && info?.Title) setPdfTitle(info.Title);
      }).catch(() => { /* ignore */ });
      setLoading(false);
    }).catch((e) => {
      if (cancelled) return;
      const msg = e instanceof Error ? e.message : String(e);
      // Older books uploaded before the pdf-bytes feature shipped
      // return 404 from the bytes endpoint — surface that clearly.
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

  // Render the active page. Re-runs on page change, on highlight
  // change (we redraw the text layer), and when the document loads.
  useEffect(() => {
    if (!docRef.current || loading || error) return;
    const doc = docRef.current;
    let cancelled = false;
    const draw = async () => {
      try {
        const page = await doc.getPage(pageNum);
        if (cancelled) return;

        // Choose a render scale that produces a readable page within
        // the available container width (DPR-aware so retina displays
        // look crisp).
        const container = containerRef.current;
        const targetWidth = container ? container.clientWidth - 16 : 800;
        const baseViewport = page.getViewport({ scale: 1 });
        const scale = Math.min(2, Math.max(0.5, targetWidth / baseViewport.width));
        const viewport = page.getViewport({ scale });

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

        // Text layer for highlights. Built by hand instead of using
        // pdfjsLib.renderTextLayer because we only need positions for
        // overlay rectangles, not selectable text.
        await drawHighlights(textLayerRef.current, page, viewport, highlight);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      }
    };
    void draw();
    return () => { cancelled = true; };
  }, [pageNum, highlight, loading, error]); // eslint-disable-line

  // Keyboard shortcuts — left/right to flip pages.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement) return;
      if (e.key === 'ArrowLeft')  setPageNum(p => Math.max(1, p - 1));
      if (e.key === 'ArrowRight') setPageNum(p => Math.min(totalPages || 1, p + 1));
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [totalPages]);

  const goBack = () => {
    // History-aware back: if the previous entry is a chapter coach
    // page in this tab, browser back is best (preserves lens / scroll).
    // Otherwise navigate to the book detail.
    if (window.history.length > 1) navigate(-1);
    else if (bookId) navigate(`/books/${bookId}`);
    else navigate('/books');
  };

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
          <div className="reader__page">
            <canvas ref={canvasRef} className="reader__canvas" />
            <div ref={textLayerRef} className="reader__highlights" aria-hidden />
          </div>
        )}
      </main>
    </div>
  );
}

// ── Highlight drawing ─────────────────────────────────────────────

/**
 * Position absolutely-placed highlight rectangles over every span of
 * the page's text content that case-insensitively matches the
 * highlight string. Multi-word highlights are matched as a substring
 * over a normalised text-content concat — handles concepts split
 * across two text items by checking adjacent items together.
 *
 * <p>Cleared on every call so re-rendering with a different highlight
 * doesn't accumulate stale boxes.
 */
async function drawHighlights(
  layer: HTMLDivElement | null,
  page: pdfjsLib.PDFPageProxy,
  viewport: pdfjsLib.PageViewport,
  needle: string,
): Promise<void> {
  if (!layer) return;
  // Reset.
  while (layer.firstChild) layer.removeChild(layer.firstChild);
  layer.style.width  = `${viewport.width}px`;
  layer.style.height = `${viewport.height}px`;
  if (!needle) return;

  const target = needle.toLowerCase();
  const content = await page.getTextContent();
  // Each item carries transform matrix [a, b, c, d, e, f] for its origin.
  // We only need (e, f) — the position in PDF user-space — to place a
  // bounding rectangle. Sizes come from item.width / item.height (the
  // latter often missing → fall back to transform's font size).
  type Item = {
    str: string;
    transform: number[];
    width: number;
    height?: number;
  };
  const items = (content.items as Item[]).filter(i => typeof i.str === 'string');

  for (const item of items) {
    if (!item.str) continue;
    const lower = item.str.toLowerCase();
    let idx = lower.indexOf(target);
    while (idx >= 0) {
      // Span position. transform = [a, b, c, d, e, f]; (e,f) is the
      // baseline origin in PDF coordinates. viewport.transform maps
      // PDF coordinates → CSS pixel coordinates (flipping Y).
      const t = item.transform;
      const a = t[0] ?? 0, d = t[3] ?? 0, e = t[4] ?? 0, f = t[5] ?? 0;
      const fontSize = Math.hypot(a, d) || 12;
      // Horizontal offset within the item — proportional to the
      // matched character index. Width per char approximated from
      // total width / total string length.
      const perChar = item.width / Math.max(1, item.str.length);
      const x = e + perChar * idx;
      const y = f;
      const w = perChar * target.length;
      const h = fontSize;
      // convertToViewportPoint returns number[] with two entries —
      // narrowing manually because the type lib doesn't pin length.
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
