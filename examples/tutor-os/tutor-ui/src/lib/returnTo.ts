/**
 * `returnTo` — tiny sessionStorage helper for cross-route "where I came
 * from" breadcrumbs.
 *
 * <p>Today the only writer is the BookCoach "Ask the tutor" action and
 * the only reader is {@code TutorPage}. The helper exists as its own
 * module so the shape and storage key live in ONE place — adding more
 * writers/readers later (e.g. "Ask the tutor from a note") doesn't
 * require copy-pasting the key string and risking a typo.
 *
 * <p>{@code sessionStorage} (not localStorage):
 *   - per-tab so opening multiple chapters in multiple tabs doesn't
 *     produce cross-talk
 *   - survives reload of the destination page (the immediate need)
 *   - clears on tab close (no zombie breadcrumbs)
 */

/** Where the user was BEFORE navigating to the consumer page. */
export interface ReturnToContext {
  /** Route to navigate back to (e.g. "/books/abc/chapter/2"). */
  url: string;
  /**
   * Short human label shown in the breadcrumb pill, e.g.
   * "Chapter 2 of Murphy". Kept short — long titles get ellipsised
   * by the CSS, but a 4–6 word label looks best.
   */
  label: string;
  /** Source area, used to render the right icon/copy in the breadcrumb. */
  source?: 'book' | 'note' | 'quiz' | 'review';
  /**
   * Set to {@code Date.now()} at write time. The reader ignores
   * payloads older than {@link MAX_AGE_MS} so a stale stash from
   * yesterday doesn't haunt today's session.
   */
  ts: number;
}

const KEY = 'tutoros.returnTo';
const MAX_AGE_MS = 30 * 60 * 1000; // 30 minutes — covers session reload + bathroom break

/**
 * Stash a return-to payload, then call the supplied {@code navigate}
 * (so writers don't have to import useNavigate themselves).
 */
export function stashReturnTo(ctx: Omit<ReturnToContext, 'ts'>): void {
  try {
    const payload: ReturnToContext = { ...ctx, ts: Date.now() };
    sessionStorage.setItem(KEY, JSON.stringify(payload));
  } catch {
    // Private browsing mode etc — fail soft; the consumer just
    // renders without a breadcrumb.
  }
}

/** Read + auto-expire. Returns null if absent / stale / malformed. */
export function readReturnTo(): ReturnToContext | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ReturnToContext;
    if (!parsed || typeof parsed.url !== 'string' || typeof parsed.label !== 'string') {
      sessionStorage.removeItem(KEY);
      return null;
    }
    if (typeof parsed.ts !== 'number' || Date.now() - parsed.ts > MAX_AGE_MS) {
      sessionStorage.removeItem(KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

/** Dismiss explicitly — e.g. when the breadcrumb's close button is clicked. */
export function clearReturnTo(): void {
  try { sessionStorage.removeItem(KEY); } catch { /* noop */ }
}
