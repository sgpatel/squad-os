import { useState, useEffect, useCallback, useRef } from "react";
import type { Mention, AnalyticsSummary, Ticket, TrendPoint } from "../types";
import { getAuthHeader } from "../auth/AuthContext";

const API = import.meta.env.VITE_API_URL || "http://localhost:8090";
const WS  = import.meta.env.VITE_WS_URL  || "ws://localhost:8090/ws/mentions";

// ── Authenticated fetch helper ────────────────────────────────────
async function authFetch(url: string, options: RequestInit = {}) {
  const res = await fetch(url, {
    ...options,
    headers: {
      ...getAuthHeader(),
      ...(options.headers || {}),
    },
  });
  if (res.status === 401) {
    // Token expired — clear local storage and reload to show login page
    localStorage.removeItem("sentinel_auth");
    window.location.reload();
    throw new Error("Session expired");
  }
  return res;
}

// ── Mention hooks ─────────────────────────────────────────────────
export function useMentions(limit = 50, sentiment?: string) {
  const [data, setData]       = useState<Mention[]>([]);
  const [loading, setLoading] = useState(true);
  const fetch_ = useCallback(async () => {
    try {
      const p = new URLSearchParams({ limit: String(limit) });
      if (sentiment) p.set("sentiment", sentiment);
      const r = await authFetch(`${API}/api/mentions?${p}`);
      setData(await r.json());
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  }, [limit, sentiment]);
  useEffect(() => {
    fetch_();
    const t = setInterval(fetch_, 10000);
    return () => clearInterval(t);
  }, [fetch_]);
  return { data, loading, refetch: fetch_ };
}

// ── WebSocket live feed (no auth — WS uses same CORS config) ─────
// useLiveEvents — returns only real-time WebSocket events (NEW/PROCESSED)
// App merges these with the polled list to avoid replacing history
export function useLiveEvents() {
  const [events, setEvents] = useState<{type:string; data:Mention}[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  useEffect(() => {
    const connect = () => {
      const ws = new WebSocket(WS);
      wsRef.current = ws;
      ws.onmessage = (e) => {
        try {
          const event = JSON.parse(e.data);
          if (event.type === "mention.processed" || event.type === "mention.new") {
            setEvents(prev => [event, ...prev].slice(0, 20));
          }
        } catch {}
      };
      ws.onclose = () => setTimeout(connect, 3000);
      ws.onerror  = () => ws.close();
    };
    connect();
    return () => wsRef.current?.close();
  }, []);
  return events;
}
// Keep old name as alias for backward compat
export function useLiveMentions() {
  const events = useLiveEvents();
  return events.map(e => e.data);
}

// ── Analytics hooks ───────────────────────────────────────────────
export function useAnalytics(hours = 24) {
  const [data, setData]       = useState<AnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const fetch_ = useCallback(async () => {
    try {
      const r = await authFetch(`${API}/api/analytics/summary?hours=${hours}`);
      setData(await r.json());
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  }, [hours]);
  useEffect(() => {
    fetch_();
    const t = setInterval(fetch_, 15000);
    return () => clearInterval(t);
  }, [fetch_]);
  return { data, loading, refetch: fetch_ };
}

export function useTrend(hours = 24) {
  const [data, setData] = useState<TrendPoint[]>([]);
  const fetch_ = useCallback(async () => {
    try {
      const r = await authFetch(`${API}/api/analytics/trend?hours=${hours}`);
      setData(await r.json());
    } catch(e) { console.error(e); }
  }, [hours]);
  useEffect(() => {
    fetch_();
    const t = setInterval(fetch_, 30000);
    return () => clearInterval(t);
  }, [fetch_]);
  return data;
}

export function useTickets() {
  const [data, setData]       = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const fetch_ = useCallback(async () => {
    try {
      const r = await authFetch(`${API}/api/tickets`);
      setData(await r.json());
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    fetch_();
    const t = setInterval(fetch_, 10000);
    return () => clearInterval(t);
  }, [fetch_]);
  return { data, loading, refetch: fetch_ };
}

export function usePendingReplies() {
  const [data, setData] = useState<Mention[]>([]);
  const fetch_ = useCallback(async () => {
    try {
      const r = await authFetch(`${API}/api/pending-replies`);
      setData(await r.json());
    } catch(e) { console.error(e); }
  }, []);
  useEffect(() => {
    fetch_();
    const t = setInterval(fetch_, 8000);
    return () => clearInterval(t);
  }, [fetch_]);
  return { data, refetch: fetch_ };
}

export function useAlerts() {
  const [data, setData] = useState<Mention[]>([]);
  const fetch_ = useCallback(async () => {
    try {
      const r = await authFetch(`${API}/api/alerts`);
      setData(await r.json());
    } catch(e) { console.error(e); }
  }, []);
  useEffect(() => {
    fetch_();
    const t = setInterval(fetch_, 5000);
    return () => clearInterval(t);
  }, [fetch_]);
  return data;
}

// ── Action helpers ────────────────────────────────────────────────
export async function approveReply(id: string) {
  return authFetch(`${API}/api/mentions/${id}/reply/approve`, { method: "POST" });
}
export async function rejectReply(id: string, revisedReply?: string) {
  return authFetch(`${API}/api/mentions/${id}/reply/reject`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ revisedReply }),
  });
}
export async function resolveTicket(id: string, resolution: string) {
  return authFetch(`${API}/api/tickets/${id}/resolve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ resolution }),
  });
}
export async function ingestMention(text: string, author: string, followers: number, platform = "TWITTER") {
  return authFetch(`${API}/api/mentions/ingest`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text, author, followers, platform }),
  });
}