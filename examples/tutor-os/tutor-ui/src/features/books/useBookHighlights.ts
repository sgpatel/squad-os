import { useCallback, useEffect, useState } from 'react';

/**
 * Persistent manual highlights for the BookReader.
 *
 * <p>localStorage-backed for v1 (no backend roundtrip — instant). The
 * hook returns the highlights for the requested {@code bookId} and a
 * pair of mutators that write through to localStorage. Cross-tab sync
 * via the {@code storage} event so a highlight added in one tab
 * appears in another viewing the same book.
 *
 * <p>A future enhancement promotes this to a server-side annotations
 * store ({@code POST/GET/DELETE /api/books/.../annotations}); the
 * hook surface stays the same.
 */

/** One persisted highlight on one page. */
export interface BookHighlight {
  /** Stable client-generated id. */
  id: string;
  /** PDF page number (1-based). */
  page: number;
  /** Plain text content of the selection (used for "Notes" view). */
  text: string;
  /** Highlight palette key. */
  color: HighlightColor;
  /**
   * One or more bounding boxes covering the selected range, in the
   * PDF page's CSS pixel coordinate space at the scale the highlight
   * was drawn. The reader rescales to current viewport on render.
   */
  boxes: HighlightBox[];
  /**
   * Scale at which the boxes were captured. The reader divides
   * current-page CSS dimensions by this number to rescale to the
   * active viewport (so a highlight from a 1.2× scale render still
   * lands correctly at 1.5×).
   */
  scale: number;
  createdAt: number;
}

export interface HighlightBox {
  left: number;
  top:  number;
  width:  number;
  height: number;
}

export type HighlightColor = 'yellow' | 'green' | 'blue' | 'pink';

export const HIGHLIGHT_COLORS: ReadonlyArray<HighlightColor> =
  ['yellow', 'green', 'blue', 'pink'];

const KEY = (bookId: string) => `tutoros.book.highlights.${bookId}`;

function safeRead(bookId: string): BookHighlight[] {
  try {
    const raw = localStorage.getItem(KEY(bookId));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as BookHighlight[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function safeWrite(bookId: string, items: BookHighlight[]): void {
  try { localStorage.setItem(KEY(bookId), JSON.stringify(items)); }
  catch { /* quota / private browsing — fail soft */ }
}

export function useBookHighlights(bookId: string | undefined) {
  const [items, setItems] = useState<BookHighlight[]>(
    () => bookId ? safeRead(bookId) : []
  );

  // Re-read on bookId change.
  useEffect(() => {
    setItems(bookId ? safeRead(bookId) : []);
  }, [bookId]);

  // Cross-tab sync — when localStorage changes in another tab, mirror
  // the update here.
  useEffect(() => {
    if (!bookId) return;
    const k = KEY(bookId);
    const onStorage = (e: StorageEvent) => {
      if (e.key !== k) return;
      setItems(safeRead(bookId));
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [bookId]);

  const add = useCallback((h: Omit<BookHighlight, 'id' | 'createdAt'>) => {
    if (!bookId) return null;
    const created: BookHighlight = {
      ...h,
      id: `hl_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`,
      createdAt: Date.now(),
    };
    setItems(prev => {
      const next = [...prev, created];
      safeWrite(bookId, next);
      return next;
    });
    return created;
  }, [bookId]);

  const remove = useCallback((id: string) => {
    if (!bookId) return;
    setItems(prev => {
      const next = prev.filter(h => h.id !== id);
      safeWrite(bookId, next);
      return next;
    });
  }, [bookId]);

  const recolor = useCallback((id: string, color: HighlightColor) => {
    if (!bookId) return;
    setItems(prev => {
      const next = prev.map(h => h.id === id ? { ...h, color } : h);
      safeWrite(bookId, next);
      return next;
    });
  }, [bookId]);

  return { items, add, remove, recolor };
}
