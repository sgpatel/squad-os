import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  /** Optional render override; receives the error + a reset callback. */
  fallback?: (error: Error, reset: () => void) => ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * ErrorBoundary — last line of defense around any subtree.
 *
 * Wrapped at the top of the app so a single thrown error in a route
 * doesn't blank the whole UI. The fallback offers a "Try again" that
 * resets state and re-renders children — useful for transient failures.
 *
 * In production you would also wire `componentDidCatch` to a logger
 * (Sentry / OTel error events / your own /api/errors).
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Surface to the console; in prod, forward to a logger here.
    // eslint-disable-next-line no-console
    console.error('[TutorOS] ErrorBoundary caught:', error, info);
  }

  reset = (): void => this.setState({ error: null });

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;
    if (this.props.fallback) return this.props.fallback(error, this.reset);
    return <DefaultErrorFallback error={error} reset={this.reset} />;
  }
}

function DefaultErrorFallback({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div className="error-fallback" role="alert">
      <h2>Something broke</h2>
      <p>
        The tutor hit an unexpected error and couldn't render this view.
        Reloading the page is usually safe — your notes, plan and progress
        are persisted locally.
      </p>
      <pre>{error.message}{error.stack ? '\n\n' + error.stack.split('\n').slice(1, 4).join('\n') : ''}</pre>
      <div style={{ display: 'flex', gap: 8 }}>
        <button className="btn btn--primary btn--sm" onClick={reset}>Try again</button>
        <button className="btn btn--sm" onClick={() => location.reload()}>Reload page</button>
      </div>
    </div>
  );
}
