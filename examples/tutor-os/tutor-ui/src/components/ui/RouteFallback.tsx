/**
 * RouteFallback — shown while a lazy route chunk is loading.
 *
 * Mirrors the .page-react layout (header strip + body blocks) so the
 * shell-to-content transition feels like content swap, not a flash.
 * Uses tokens so it adapts to every theme automatically.
 */
export function RouteFallback() {
  return (
    <div className="page-react" aria-busy="true" aria-live="polite">
      <div className="skeleton-header">
        <div className="skeleton skeleton--title" />
        <div className="skeleton skeleton--sub" />
      </div>
      <div className="skeleton-grid">
        <div className="skeleton skeleton--card" />
        <div className="skeleton skeleton--card" />
        <div className="skeleton skeleton--card" />
      </div>
      <span className="visually-hidden">Loading…</span>
    </div>
  );
}
