import { useState, useCallback, useRef } from "react";

export function useInfiniteScroll<T>(items: T[], pageSize = 20) {
  const [page, setPage] = useState(1);
  const observer = useRef<IntersectionObserver | null>(null);

  const visible = items.slice(0, page * pageSize);
  const hasMore = visible.length < items.length;

  const sentinelRef = useCallback((node: HTMLDivElement | null) => {
    if (observer.current) observer.current.disconnect();
    if (!node) return;
    observer.current = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting && hasMore) {
        setPage(p => p + 1);
      }
    });
    observer.current.observe(node);
  }, [hasMore]);

  const reset = useCallback(() => setPage(1), []);

  return { visible, hasMore, sentinelRef, reset };
}