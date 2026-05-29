import { useCallback, useEffect, useState } from 'react';
import { api, DEMO_LEARNER_ID } from '@/lib/api';
import { useAuth } from '@/store/auth';
import { useWorkspace } from '@/store/workspace';
import type { ReviewQueueItem } from '@/lib/types';

/**
 * useReviewQueue — small hook that mirrors the backend's
 * {@code GET /api/review/queue/{learnerId}/{subject}} response and exposes
 * a stable {@code refresh} fn the UI can call after grading a card or
 * after a tutor turn that may have produced a new mastery write.
 *
 * Returns:
 *   - items     : the full queue (most-overdue first)
 *   - count     : items.length, the pill's displayed number
 *   - loading   : true while a fetch is in flight (for spinners)
 *   - error     : last error message, or null
 *   - refresh() : explicit re-fetch (always read-through; no client cache)
 *   - subject   : resolved active subject name (or null) for callers that
 *                 want to render "Review — Mathematics" without re-deriving
 *
 * No persistence to localStorage — the queue is short-lived per session
 * and the source of truth lives on the backend. Re-fetching is cheap
 * because the queue is bounded (default limit=20).
 */
export function useReviewQueue() {
  const learnerId       = useAuth(s => s.currentUser()?.id ?? DEMO_LEARNER_ID);
  const activeSubjectId = useWorkspace(s => s.activeSubjectId);
  const getSubject      = useWorkspace(s => s.getSubject);

  const subject = activeSubjectId ? getSubject(activeSubjectId)?.name ?? null : null;

  const [items,   setItems]   = useState<ReviewQueueItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!learnerId || !subject) {
      setItems([]);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const queue = await api.review.queue(learnerId, subject);
      setItems(queue);
    } catch (e) {
      // 404 unknown learner+subject is benign — UI shows "0 due".
      setError(e instanceof Error ? e.message : String(e));
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [learnerId, subject]);

  useEffect(() => { void refresh(); }, [refresh]);

  return {
    items,
    count: items.length,
    loading,
    error,
    refresh,
    subject,
    learnerId,
  };
}
