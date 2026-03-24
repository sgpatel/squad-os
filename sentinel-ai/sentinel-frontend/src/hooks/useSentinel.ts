import { useState, useEffect, useCallback, useRef } from "react";
import type { Mention, AnalyticsSummary, Ticket, TrendPoint } from "../types";

const API = import.meta.env.VITE_API_URL || "http://localhost:8090";
const WS  = import.meta.env.VITE_WS_URL  || "ws://localhost:8090/ws/mentions";

export function useMentions(limit = 50, sentiment?: string) {
  const [data, setData] = useState<Mention[]>([]);
  const [loading, setLoading] = useState(true);
  const fetch_ = useCallback(async () => {
    const p = new URLSearchParams({ limit: String(limit) });
    if (sentiment) p.set("sentiment", sentiment);
    const r = await fetch(`${API}/api/mentions?${p}`);
    setData(await r.json());
    setLoading(false);
  }, [limit, sentiment]);
  useEffect(() => { fetch_(); const t = setInterval(fetch_, 10000); return () => clearInterval(t); }, [fetch_]);
  return { data, loading, refetch: fetch_ };
}

export function useLiveMentions() {
  const [mentions, setMentions] = useState<Mention[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  useEffect(() => {
    const connect = () => {
      const ws = new WebSocket(WS);
      wsRef.current = ws;
      ws.onmessage = (e) => {
        const { type, data } = JSON.parse(e.data);
        if (type === "mention.processed" || type === "mention.new") {
          setMentions(prev => [data, ...prev.filter(m => m.id !== data.id)].slice(0, 100));
        }
      };
      ws.onclose = () => setTimeout(connect, 3000);
      ws.onerror = () => ws.close();
    };
    connect();
    return () => wsRef.current?.close();
  }, []);
  return mentions;
}

export function useAnalytics(hours = 24) {
  const [data, setData] = useState<AnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const fetch_ = useCallback(async () => {
    const r = await fetch(`${API}/api/analytics/summary?hours=${hours}`);
    setData(await r.json()); setLoading(false);
  }, [hours]);
  useEffect(() => { fetch_(); const t = setInterval(fetch_, 15000); return () => clearInterval(t); }, [fetch_]);
  return { data, loading, refetch: fetch_ };
}

export function useTrend(hours = 24) {
  const [data, setData] = useState<TrendPoint[]>([]);
  const fetch_ = useCallback(async () => {
    const r = await fetch(`${API}/api/analytics/trend?hours=${hours}`);
    setData(await r.json());
  }, [hours]);
  useEffect(() => { fetch_(); const t = setInterval(fetch_, 30000); return () => clearInterval(t); }, [fetch_]);
  return data;
}

export function useTickets() {
  const [data, setData] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const fetch_ = useCallback(async () => {
    const r = await fetch(`${API}/api/tickets`);
    setData(await r.json()); setLoading(false);
  }, []);
  useEffect(() => { fetch_(); const t = setInterval(fetch_, 10000); return () => clearInterval(t); }, [fetch_]);
  return { data, loading, refetch: fetch_ };
}

export function usePendingReplies() {
  const [data, setData] = useState<Mention[]>([]);
  const fetch_ = useCallback(async () => {
    const r = await fetch(`${API}/api/pending-replies`);
    setData(await r.json());
  }, []);
  useEffect(() => { fetch_(); const t = setInterval(fetch_, 8000); return () => clearInterval(t); }, [fetch_]);
  return { data, refetch: fetch_ };
}

export function useAlerts() {
  const [data, setData] = useState<Mention[]>([]);
  const fetch_ = useCallback(async () => {
    const r = await fetch(`${API}/api/alerts`);
    setData(await r.json());
  }, []);
  useEffect(() => { fetch_(); const t = setInterval(fetch_, 5000); return () => clearInterval(t); }, [fetch_]);
  return data;
}

export async function approveReply(id: string) {
  return fetch(`${API}/api/mentions/${id}/reply/approve`, { method: "POST" });
}
export async function rejectReply(id: string, revisedReply?: string) {
  return fetch(`${API}/api/mentions/${id}/reply/reject`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ revisedReply }),
  });
}
export async function resolveTicket(id: string, resolution: string) {
  return fetch(`${API}/api/tickets/${id}/resolve`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ resolution }),
  });
}
export async function ingestMention(text: string, author: string, followers: number) {
  return fetch(`${API}/api/mentions/ingest`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text, author, followers }),
  });
}