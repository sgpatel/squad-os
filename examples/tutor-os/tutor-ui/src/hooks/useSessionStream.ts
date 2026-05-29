import { useEffect, useRef, useState } from 'react';
import { connectStream, type StreamFrame, type StreamConnection } from '@/lib/api';

/**
 * useSessionStream — React-friendly subscriber for the tutor-api WS stream.
 *
 *   const { status, last } = useSessionStream(sessionId, frame => { ... });
 *
 * Behaviour:
 *   - Opens once per sessionId; tears down on unmount or sessionId change.
 *   - Heartbeats every 25s with a PING (backend replies PONG).
 *   - Auto-reconnects with exponential backoff (1s → 30s) on unexpected close.
 *   - `status` reflects connecting / open / closed / error so UIs can render
 *     a connection chip without spelunking into the socket.
 */
export type StreamStatus = 'idle' | 'connecting' | 'open' | 'closed' | 'error';

export interface UseSessionStream {
  status: StreamStatus;
  last: StreamFrame | null;
  /** Send a PING; useful for "test connection" buttons. */
  ping: () => void;
  /** Force-close — the next render with a sessionId will reconnect. */
  close: () => void;
}

export function useSessionStream(
  sessionId: string | null,
  onFrame: (f: StreamFrame) => void
): UseSessionStream {
  const [status, setStatus] = useState<StreamStatus>('idle');
  const [last, setLast]     = useState<StreamFrame | null>(null);

  // Keep the latest callback in a ref so we don't tear down the socket on every render.
  const cbRef = useRef(onFrame);
  cbRef.current = onFrame;

  const connRef     = useRef<StreamConnection | null>(null);
  const retryRef    = useRef<number>(0);
  const timersRef   = useRef<{ heartbeat?: number; reconnect?: number }>({});
  const closedByUs  = useRef<boolean>(false);

  useEffect(() => {
    if (!sessionId) { setStatus('idle'); return; }
    closedByUs.current = false;

    const open = () => {
      setStatus('connecting');
      const conn = connectStream(sessionId, {
        onOpen: () => {
          retryRef.current = 0;
          setStatus('open');
          // Start heartbeat
          timersRef.current.heartbeat = window.setInterval(() => conn.ping(), 25_000);
        },
        onFrame: (f) => {
          setLast(f);
          cbRef.current(f);
        },
        onError: () => setStatus('error'),
        onClose: () => {
          clearTimers();
          setStatus('closed');
          if (closedByUs.current) return;
          // Exponential backoff: 1s, 2s, 4s, …, capped at 30s
          const delay = Math.min(30_000, 1_000 * Math.pow(2, retryRef.current++));
          timersRef.current.reconnect = window.setTimeout(open, delay);
        }
      });
      connRef.current = conn;
    };

    open();

    return () => {
      closedByUs.current = true;
      clearTimers();
      connRef.current?.close();
      connRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  function clearTimers() {
    if (timersRef.current.heartbeat)  window.clearInterval(timersRef.current.heartbeat);
    if (timersRef.current.reconnect)  window.clearTimeout(timersRef.current.reconnect);
    timersRef.current = {};
  }

  return {
    status,
    last,
    ping:  () => connRef.current?.ping(),
    close: () => { closedByUs.current = true; connRef.current?.close(); }
  };
}
