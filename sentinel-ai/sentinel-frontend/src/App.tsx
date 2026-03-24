import { useState } from "react";
import { useAuth } from "./auth/AuthContext";
import { LoginPage } from "./auth/LoginPage";
import { BarChart, Bar, AreaChart, Area, PieChart, Pie, Cell, XAxis, YAxis,
  CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { useMentions, useLiveMentions, useAnalytics, useTrend,
  useTickets, usePendingReplies, useAlerts,
  approveReply, rejectReply, resolveTicket, ingestMention } from "./hooks/useSentinel";
import type { Mention } from "./types";

// ── Constants ────────────────────────────────────────────────────
const C = {
  green:"#22c55e", blue:"#3b82f6", purple:"#a855f7",
  orange:"#f97316", red:"#ef4444", teal:"#14b8a6", yellow:"#eab308"
};

// ── Shared UI Components ──────────────────────────────────────────
function Badge({ label, color }: { label: string; color: string }) {
  return (
    <span style={{ background: color+"1a", color, border: "1px solid "+color+"33",
      borderRadius: 20, padding: "2px 9px", fontSize: 9, fontWeight: 700,
      display: "inline-block", whiteSpace: "nowrap" }}>{label}</span>
  );
}

function SentimentBadge({ s }: { s?: string }) {
  if (!s) return <Badge label="PENDING" color="#475569" />;
  return <Badge label={s} color={s==="POSITIVE"?C.green:s==="NEGATIVE"?C.red:C.yellow} />;
}

function PriorityBadge({ p }: { p?: string }) {
  if (!p) return null;
  const c = p==="P1"?C.red:p==="P2"?C.orange:p==="P3"?C.yellow:C.teal;
  return <Badge label={p} color={c} />;
}

function LiveDot({ color = C.green }: { color?: string }) {
  return (
    <div style={{ width: 7, height: 7, borderRadius: "50%",
      background: color, animation: "pulse 2s infinite", flexShrink: 0 }} />
  );
}

function Card({ children, style }: { children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <div style={{ background: "linear-gradient(135deg,#0d1424,#111827)",
      border: "1px solid #1e293b", borderRadius: 10, overflow: "hidden",
      animation: "fadeUp .25s ease", ...style }}>
      {children}
    </div>
  );
}

function CardHeader({ icon, title, badge, badgeColor = C.blue }:
  { icon: string; title: string; badge?: string|number; badgeColor?: string }) {
  return (
    <div style={{ padding: "11px 16px", borderBottom: "1px solid #1e293b",
      background: "linear-gradient(90deg,#0a1020,#0d1424)",
      display: "flex", alignItems: "center", justifyContent: "space-between" }}>
      <div style={{ color: "#f1f5f9", fontWeight: 600, fontSize: 11,
        display: "flex", alignItems: "center", gap: 7 }}>
        <span style={{ fontSize: 14 }}>{icon}</span>{title}
      </div>
      {badge !== undefined && <Badge label={String(badge)} color={badgeColor} />}
    </div>
  );
}

const TT = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div style={{ background: "#0d1424", border: "1px solid #1e293b",
      borderRadius: 8, padding: "10px 14px" }}>
      {label && <div style={{ color: "#94a3b8", fontSize: 10, marginBottom: 6 }}>{label}</div>}
      {payload.map((p: any, i: number) => (
        <div key={i} style={{ color: p.color, fontSize: 12, fontWeight: 600 }}>
          {p.name}: {typeof p.value === "number" ? p.value.toLocaleString() : p.value}
        </div>
      ))}
    </div>
  );
};

// ── MentionCard ───────────────────────────────────────────────────
function MentionCard({ m, onApprove, onReject }:
  { m: Mention; onApprove?: () => void; onReject?: (r?: string) => void }) {
  const [expanded, setExpanded] = useState(false);
  const statusColor = m.processingStatus === "DONE" ? C.green :
    m.processingStatus === "ERROR" ? C.red :
    m.processingStatus === "ANALYSING" ? C.blue : "#475569";
  const urgColor = m.urgency === "CRITICAL" ? C.red :
    m.urgency === "HIGH" ? C.orange : m.urgency === "MEDIUM" ? C.yellow : C.teal;

  return (
    <div style={{ background: "#080e1a", border: "1px solid #1e293b",
      borderLeft: "3px solid " + (m.sentimentLabel === "NEGATIVE" ? C.red :
        m.sentimentLabel === "POSITIVE" ? C.green : C.yellow),
      borderRadius: 8, padding: 12, marginBottom: 8,
      transition: "all .2s", cursor: "pointer",
      animation: "slideIn .3s ease" }}
      onClick={() => setExpanded(!expanded)}>
      <div style={{ display: "flex", justifyContent: "space-between",
        alignItems: "flex-start", gap: 8 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 4 }}>
            <span style={{ color: "#94a3b8", fontSize: 11, fontWeight: 600 }}>
              @{m.authorUsername}</span>
            <span style={{ color: "#334155", fontSize: 9 }}>
              {m.authorFollowers.toLocaleString()} followers</span>
            {m.isViral && <Badge label="🔥 VIRAL" color={C.orange} />}
          </div>
          <div style={{ color: "#e2e8f0", fontSize: 12, lineHeight: 1.5,
            overflow: expanded ? "visible" : "hidden",
            textOverflow: "ellipsis", whiteSpace: expanded ? "normal" : "nowrap" }}>
            {m.text}
          </div>
          {m.summary && (
            <div style={{ color: "#475569", fontSize: 10, marginTop: 4, fontStyle: "italic" }}>
              {m.summary}
            </div>
          )}
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 4, flexShrink: 0, alignItems: "flex-end" }}>
          <SentimentBadge s={m.sentimentLabel} />
          {m.priority && <PriorityBadge p={m.priority} />}
          {m.urgency && <Badge label={m.urgency} color={urgColor} />}
        </div>
      </div>
      {expanded && (
        <div style={{ marginTop: 10, borderTop: "1px solid #1e293b", paddingTop: 10 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
            <InfoRow label="Topic" value={m.topic} />
            <InfoRow label="Emotion" value={m.primaryEmotion} />
            <InfoRow label="Team" value={m.assignedTeam} />
            <InfoRow label="Status" value={m.processingStatus} />
            <InfoRow label="Ticket" value={m.ticketId} />
            <InfoRow label="Platform" value={m.platform} />
          </div>
          {m.replyText && (
            <div style={{ background: "#0a1020", borderRadius: 6, padding: 10, marginTop: 6 }}>
              <div style={{ color: "#475569", fontSize: 9, textTransform: "uppercase", letterSpacing: 1, marginBottom: 4 }}>
                AI Generated Reply</div>
              <div style={{ color: "#e2e8f0", fontSize: 12, lineHeight: 1.5 }}>{m.replyText}</div>
              {m.replyStatus === "PENDING" && onApprove && (
                <div style={{ display: "flex", gap: 6, marginTop: 8 }}>
                  <button onClick={(e) => { e.stopPropagation(); onApprove(); }}
                    style={{ background: C.green+"22", color: C.green, border: "1px solid "+C.green+"44",
                      borderRadius: 6, padding: "4px 12px", cursor: "pointer", fontSize: 11, fontFamily: "Inter" }}>
                    ✓ Approve Reply
                  </button>
                  <button onClick={(e) => { e.stopPropagation(); onReject?.(); }}
                    style={{ background: C.red+"22", color: C.red, border: "1px solid "+C.red+"44",
                      borderRadius: 6, padding: "4px 12px", cursor: "pointer", fontSize: 11, fontFamily: "Inter" }}>
                    ✗ Reject
                  </button>
                  <Badge label={m.replyStatus || "PENDING"} color={C.orange} />
                </div>
              )}
              {m.replyStatus !== "PENDING" && (
                <Badge label={m.replyStatus || ""} color={m.replyStatus==="APPROVED"?C.green:C.red} />
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null;
  return (
    <div>
      <div style={{ color: "#334155", fontSize: 9, textTransform: "uppercase", letterSpacing: 1 }}>{label}</div>
      <div style={{ color: "#94a3b8", fontSize: 11, fontWeight: 500 }}>{value}</div>
    </div>
  );
}

// ── BrandHealthGauge ──────────────────────────────────────────────
function BrandHealthGauge({ score }: { score: number }) {
  const color = score >= 70 ? C.green : score >= 40 ? C.yellow : C.red;
  const angle = (score / 100) * 180 - 90;
  return (
    <div style={{ textAlign: "center", padding: "16px 0" }}>
      <svg width="180" height="100" viewBox="0 0 180 100">
        <path d="M 20 90 A 70 70 0 0 1 160 90" stroke="#1e293b" strokeWidth="12" fill="none"/>
        <path d="M 20 90 A 70 70 0 0 1 160 90" stroke={color}
          strokeWidth="12" fill="none" strokeDasharray={`${(score/100)*220} 220`}
          strokeLinecap="round"/>
        <line x1="90" y1="90"
          x2={90 + 55 * Math.cos((angle * Math.PI) / 180)}
          y2={90 + 55 * Math.sin((angle * Math.PI) / 180)}
          stroke={color} strokeWidth="2" strokeLinecap="round"/>
        <circle cx="90" cy="90" r="4" fill={color}/>
        <text x="90" y="75" textAnchor="middle" fill={color}
          fontSize="24" fontWeight="700" fontFamily="JetBrains Mono">{score.toFixed(0)}</text>
        <text x="90" y="86" textAnchor="middle" fill="#475569" fontSize="9">BRAND HEALTH</text>
      </svg>
    </div>
  );
}

// ── StatCard ──────────────────────────────────────────────────────
function StatCard({ label, value, color, sub, icon }:
  { label: string; value: string|number; color: string; sub?: string; icon: string }) {
  return (
    <div style={{ background: "linear-gradient(135deg,#0d1424,#111827)",
      border: "1px solid #1e293b", borderRadius: 10, padding: "14px 18px",
      transition: "all .2s" }}>
      <div style={{ display: "flex", justifyContent: "space-between" }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, color,
            fontFamily: "JetBrains Mono", marginBottom: 3 }}>{value}</div>
          <div style={{ color: "#475569", fontSize: 9, textTransform: "uppercase",
            letterSpacing: "1.2px", fontWeight: 600 }}>{label}</div>
          {sub && <div style={{ color: "#1e3a5f", fontSize: 10, marginTop: 3 }}>{sub}</div>}
        </div>
        <span style={{ fontSize: 22, opacity: .3 }}>{icon}</span>
      </div>
    </div>
  );
}

// ── Main App ──────────────────────────────────────────────────────
export default function App() {
  const { user, logout, isAdmin } = useAuth();
    // Auth guard — show login page if not authenticated
    if (!user) return <LoginPage />;
    const [tab, setTab] = useState("overview");
  const [testText, setTestText] = useState("");
  const [testAuthor, setTestAuthor] = useState("test_user");
  const [testFollowers, setTestFollowers] = useState("500");
  const [submitting, setSubmitting] = useState(false);

  const { data: analytics } = useAnalytics(24);
  const trendData = useTrend(24);
  const liveMentions = useLiveMentions();
  const { data: allMentions, refetch: refetchMentions } = useMentions(100);
  const { data: tickets, refetch: refetchTickets } = useTickets();
  const { data: pendingReplies, refetch: refetchReplies } = usePendingReplies();
  const alerts = useAlerts();

  const mentions = liveMentions.length > 0 ? liveMentions : allMentions;

  const TABS = [
    { id: "overview",   label: "Overview",      icon: "🏠" },
    { id: "feed",       label: "Mention Feed",  icon: "📡" },
    { id: "analytics",  label: "Analytics",     icon: "📊" },
    { id: "tickets",    label: "Tickets",       icon: "🎫" },
    { id: "replies",    label: "Reply Queue",   icon: "💬" },
    { id: "alerts",     label: "Alerts",        icon: "🚨", badge: alerts.length },
    { id: "test",       label: "Test",          icon: "🧪" },
  ];

  const handleApprove = async (id: string) => {
    await approveReply(id); refetchReplies(); refetchMentions();
  };
  const handleReject = async (id: string) => {
    await rejectReply(id); refetchReplies(); refetchMentions();
  };
  const handleSubmitTest = async () => {
    if (!testText.trim()) return;
    setSubmitting(true);
    await ingestMention(testText, testAuthor, parseInt(testFollowers));
    setTestText("");
    setSubmitting(false);
    setTimeout(() => { refetchMentions(); }, 3000);
  };

  const negMentions = mentions.filter(m => m.sentimentLabel === "NEGATIVE");
  const posMentions = mentions.filter(m => m.sentimentLabel === "POSITIVE");

  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column" }}>
      {/* Header */}
      <div style={{ background: "linear-gradient(90deg,#070b12,#0a1020)",
        borderBottom: "1px solid #1e293b", padding: "0 24px", height: 58,
        display: "flex", alignItems: "center", justifyContent: "space-between",
        position: "sticky", top: 0, zIndex: 100,
        boxShadow: "0 2px 24px rgba(0,0,0,.6)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <span style={{ fontSize: 24 }}>🛡️</span>
          <div>
            <div style={{ color: "#f1f5f9", fontWeight: 700, fontSize: 16 }}>SentinelAI</div>
            <div style={{ color: "#334155", fontSize: 10 }}>
              Social Mention Analyser · @AirtelPaymentsBank</div>
          </div>
        </div>
        <div style={{ display: "flex", gap: 3 }}>
          {TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)} style={{
              background: tab===t.id ? C.blue+"1a" : "none",
              border: tab===t.id ? "1px solid "+C.blue+"44" : "1px solid transparent",
              color: tab===t.id ? C.blue : "#475569",
              padding: "6px 12px", borderRadius: 7, cursor: "pointer",
              fontSize: 11, fontFamily: "Inter", fontWeight: tab===t.id ? 600 : 400,
              display: "flex", alignItems: "center", gap: 4 }}>
              {t.icon} {t.label}
              {t.badge && t.badge > 0 && (
                <span style={{ background: C.red, color: "white", borderRadius: 10,
                  padding: "0 5px", fontSize: 9, fontWeight: 700 }}>{t.badge}</span>
              )}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6,
            background: "#0d1424", border: "1px solid #1e293b",
            borderRadius: 8, padding: "5px 10px" }}>
            <div style={{ width: 24, height: 24, borderRadius: "50%",
              background: "linear-gradient(135deg,#3b82f6,#2563eb)",
              display: "flex", alignItems: "center", justifyContent: "center",
              fontSize: 11, fontWeight: 700, color: "white" }}>
              {user.username[0].toUpperCase()}
            </div>
            <div>
              <div style={{ color: "#f1f5f9", fontSize: 11, fontWeight: 600 }}>
                {user.fullName || user.username}
              </div>
              <div style={{ color: "#334155", fontSize: 9 }}>{user.role}</div>
            </div>
          </div>
          <button onClick={logout} style={{
            background: "none", border: "1px solid #1e293b",
            color: "#475569", padding: "5px 12px", borderRadius: 7,
            cursor: "pointer", fontSize: 11, fontFamily: "Inter",
            transition: "all .2s" }}
            onMouseEnter={e => { e.currentTarget.style.borderColor = "#ef4444"; e.currentTarget.style.color = "#ef4444"; }}
            onMouseLeave={e => { e.currentTarget.style.borderColor = "#1e293b"; e.currentTarget.style.color = "#475569"; }}>
            Sign Out
          </button>
          <LiveDot />
          <span style={{ color: "#22c55e", fontSize: 11 }}>LIVE</span>
          <span style={{ color: "#334155", fontSize: 11 }}>
            {mentions.length} mentions</span>
        </div>
      </div>

      {/* Stats Bar */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(7,1fr)",
        gap: 1, background: "#1e293b", borderBottom: "1px solid #1e293b" }}>
        {[
          { l:"Total Mentions", v: analytics?.totalMentions ?? "-", c: C.blue, i: "📡" },
          { l:"Positive", v: analytics?.positiveMentions ?? "-", c: C.green, i: "😊" },
          { l:"Negative", v: analytics?.negativeMentions ?? "-", c: C.red, i: "😡" },
          { l:"Neutral", v: analytics?.neutralMentions ?? "-", c: C.yellow, i: "😐" },
          { l:"Brand Health", v: analytics ? analytics.brandHealthScore.toFixed(0)+"%" : "-", c: C.teal, i: "💚" },
          { l:"Open Tickets", v: analytics?.openTickets ?? "-", c: C.orange, i: "🎫" },
          { l:"Pending Replies", v: analytics?.pendingReplies ?? "-", c: C.purple, i: "💬" },
        ].map((s, i) => (
          <div key={i} style={{ background: "#070b12", padding: "12px 16px", transition: "background .2s" }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: s.c,
              fontFamily: "JetBrains Mono", marginBottom: 2 }}>{s.v}</div>
            <div style={{ fontSize: 9, color: "#475569", textTransform: "uppercase",
              letterSpacing: "1px", fontWeight: 600 }}>{s.l}</div>
          </div>
        ))}
      </div>

      {/* Content */}
      <div style={{ flex: 1, padding: "16px 20px", overflow: "auto" }}>

        {/* OVERVIEW */}
        {tab === "overview" && (
          <div>
            <div style={{ display: "grid", gridTemplateColumns: "220px 1fr", gap: 12, marginBottom: 12 }}>
              <Card>
                <CardHeader icon="💚" title="Brand Health" />
                <BrandHealthGauge score={analytics?.brandHealthScore ?? 75} />
                <div style={{ padding: "0 16px 12px" }}>
                  {[
                    ["Positive Rate", analytics ? Math.round(analytics.positiveMentions/(analytics.totalMentions||1)*100)+"%" : "—", C.green],
                    ["Negative Rate", analytics ? Math.round(analytics.negativeMentions/(analytics.totalMentions||1)*100)+"%" : "—", C.red],
                    ["Critical Alerts", analytics?.criticalAlerts ?? 0, C.red],
                    ["Avg Sentiment", analytics ? analytics.avgSentimentScore.toFixed(2) : "—", C.blue],
                  ].map(([l,v,c]) => (
                    <div key={String(l)} style={{ display:"flex",justifyContent:"space-between",
                      padding:"5px 0",borderBottom:"1px solid #0f1929" }}>
                      <span style={{color:"#475569",fontSize:11}}>{l}</span>
                      <span style={{color:c as string,fontWeight:700,
                        fontFamily:"JetBrains Mono",fontSize:12}}>{v}</span>
                    </div>
                  ))}
                </div>
              </Card>
              <Card>
                <CardHeader icon="📈" title="Sentiment Trend (24h)" />
                <div style={{ height: 220, padding: "8px 0" }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={trendData} margin={{top:8,right:12,bottom:8,left:0}}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                      <XAxis dataKey="hour" tick={{fill:"#475569",fontSize:9}}
                        tickFormatter={v => `${v}h`} />
                      <YAxis tick={{fill:"#475569",fontSize:9}} />
                      <Tooltip content={<TT />} />
                      <Legend iconType="circle" iconSize={8}
                        wrapperStyle={{fontSize:10,color:"#64748b"}} />
                      <Area type="monotone" dataKey="positive" stackId="1"
                        stroke={C.green} fill={C.green+"33"} name="Positive" />
                      <Area type="monotone" dataKey="neutral" stackId="1"
                        stroke={C.yellow} fill={C.yellow+"22"} name="Neutral" />
                      <Area type="monotone" dataKey="negative" stackId="1"
                        stroke={C.red} fill={C.red+"33"} name="Negative" />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              </Card>
            </div>
            <div style={{ display:"grid",gridTemplateColumns:"1fr 1fr",gap:12 }}>
              <Card>
                <CardHeader icon="🔴" title="Recent Negative Mentions"
                  badge={negMentions.length} badgeColor={C.red} />
                <div style={{ maxHeight: 320, overflowY: "auto", padding: 10 }}>
                  {negMentions.slice(0,5).map(m => (
                    <MentionCard key={m.id} m={m}
                      onApprove={() => handleApprove(m.id)}
                      onReject={() => handleReject(m.id)} />
                  ))}
                  {negMentions.length === 0 && (
                    <div style={{padding:24,textAlign:"center",color:"#334155"}}>No negative mentions</div>
                  )}
                </div>
              </Card>
              <Card>
                <CardHeader icon="🟢" title="Recent Positive Mentions"
                  badge={posMentions.length} badgeColor={C.green} />
                <div style={{ maxHeight: 320, overflowY: "auto", padding: 10 }}>
                  {posMentions.slice(0,5).map(m => (
                    <MentionCard key={m.id} m={m} />
                  ))}
                  {posMentions.length === 0 && (
                    <div style={{padding:24,textAlign:"center",color:"#334155"}}>No positive mentions yet</div>
                  )}
                </div>
              </Card>
            </div>
          </div>
        )}

        {/* MENTION FEED */}
        {tab === "feed" && (
          <Card>
            <CardHeader icon="📡" title="All Mentions" badge={mentions.length} />
            <div style={{ padding: "10px 12px", borderBottom: "1px solid #1e293b",
              display: "flex", gap: 6 }}>
              {["ALL","POSITIVE","NEGATIVE","NEUTRAL"].map(f => (
                <button key={f} style={{ background:"#111827",border:"1px solid #1e293b",
                  color:"#94a3b8",padding:"4px 12px",borderRadius:6,cursor:"pointer",
                  fontSize:10,fontFamily:"Inter" }}>{f}</button>
              ))}
            </div>
            <div style={{ maxHeight: "calc(100vh - 280px)", overflowY: "auto", padding: 12 }}>
              {mentions.map(m => (
                <MentionCard key={m.id} m={m}
                  onApprove={() => handleApprove(m.id)}
                  onReject={() => handleReject(m.id)} />
              ))}
              {mentions.length === 0 && (
                <div style={{padding:40,textAlign:"center",color:"#334155"}}>
                  <div style={{fontSize:32,marginBottom:12}}>📡</div>
                  <div>No mentions yet. Waiting for data...</div>
                </div>
              )}
            </div>
          </Card>
        )}

        {/* ANALYTICS */}
        {tab === "analytics" && (
          <div>
            <div style={{display:"grid",gridTemplateColumns:"repeat(3,1fr)",gap:12,marginBottom:12}}>
              <StatCard label="Total Mentions (24h)" value={analytics?.totalMentions??0} color={C.blue} icon="📡" />
              <StatCard label="Brand Health Score" value={(analytics?.brandHealthScore??0).toFixed(1)+"%"} color={C.teal} icon="💚" />
              <StatCard label="Critical Alerts" value={analytics?.criticalAlerts??0} color={C.red} icon="🚨" />
            </div>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12}}>
              <Card>
                <CardHeader icon="📊" title="Sentiment Distribution" />
                <div style={{height:240,padding:"8px 0"}}>
                  <ResponsiveContainer>
                    <PieChart>
                      <Pie data={[
                        {name:"Positive",value:analytics?.positiveMentions??0,fill:C.green},
                        {name:"Negative",value:analytics?.negativeMentions??0,fill:C.red},
                        {name:"Neutral", value:analytics?.neutralMentions??0, fill:C.yellow},
                      ].filter(d=>d.value>0)}
                        cx="50%" cy="50%" innerRadius={55} outerRadius={90}
                        dataKey="value" paddingAngle={3}>
                        {[C.green,C.red,C.yellow].map((c,i)=>(<Cell key={i} fill={c} strokeWidth={0}/>))}
                      </Pie>
                      <Tooltip content={<TT />} />
                      <Legend iconType="circle" iconSize={8}
                        wrapperStyle={{fontSize:10,color:"#64748b"}} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              </Card>
              <Card>
                <CardHeader icon="📈" title="24h Trend" />
                <div style={{height:240,paddingTop:8}}>
                  <ResponsiveContainer>
                    <BarChart data={trendData} margin={{top:8,right:12,bottom:8,left:0}}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                      <XAxis dataKey="hour" tick={{fill:"#475569",fontSize:9}} tickFormatter={v=>`${v}h`}/>
                      <YAxis tick={{fill:"#475569",fontSize:9}} allowDecimals={false}/>
                      <Tooltip content={<TT />}/>
                      <Bar dataKey="positive" name="Positive" fill={C.green} radius={[3,3,0,0]} stackId="a"/>
                      <Bar dataKey="neutral"  name="Neutral"  fill={C.yellow} radius={[0,0,0,0]} stackId="a"/>
                      <Bar dataKey="negative" name="Negative" fill={C.red} radius={[0,0,3,3]} stackId="a"/>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </Card>
            </div>
          </div>
        )}

        {/* TICKETS */}
        {tab === "tickets" && (
          <div>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:12,marginBottom:12}}>
              <StatCard label="Open Tickets" value={analytics?.openTickets??0} color={C.orange} icon="🎫" />
              <StatCard label="Resolved Tickets" value={analytics?.resolvedTickets??0} color={C.green} icon="✅" />
              <StatCard label="Total Tickets" value={tickets.length} color={C.blue} icon="📋" />
            </div>
            <Card>
              <CardHeader icon="🎫" title="Ticket Pipeline" badge={tickets.length} />
              <div style={{overflowX:"auto"}}>
                <table style={{width:"100%",borderCollapse:"collapse"}}>
                  <thead><tr>
                    {["Ticket ID","Title","Status","Priority","Team","Created","Action"]
                      .map(h=><th key={h} style={{padding:"8px 14px",textAlign:"left",
                        fontSize:9,textTransform:"uppercase",letterSpacing:1,
                        color:"#475569",borderBottom:"1px solid #1e293b",
                        background:"#080c14",fontWeight:600}}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {tickets.map(t=>(
                      <tr key={t.id} style={{borderBottom:"1px solid #0c1525"}}>
                        <td style={{padding:"9px 14px",fontFamily:"JetBrains Mono",
                          fontSize:11,color:"#3b82f6"}}>{t.id}</td>
                        <td style={{padding:"9px 14px",fontSize:12,color:"#f1f5f9",
                          fontWeight:600,maxWidth:300,overflow:"hidden",
                          textOverflow:"ellipsis",whiteSpace:"nowrap"}}>{t.title}</td>
                        <td style={{padding:"9px 14px"}}>
                          <Badge label={t.status}
                            color={t.status==="RESOLVED"?C.green:t.status==="OPEN"?C.orange:C.blue}/></td>
                        <td style={{padding:"9px 14px"}}>
                          <PriorityBadge p={t.priority}/></td>
                        <td style={{padding:"9px 14px",fontSize:11,color:"#64748b"}}>{t.team}</td>
                        <td style={{padding:"9px 14px",fontSize:10,color:"#334155"}}>
                          {new Date(t.createdAt).toLocaleTimeString()}</td>
                        <td style={{padding:"9px 14px"}}>
                          {t.status==="OPEN"&&(
                            <button onClick={()=>{resolveTicket(t.id,"Resolved via dashboard");refetchTickets();}}
                              style={{background:C.green+"22",color:C.green,
                                border:"1px solid "+C.green+"44",borderRadius:5,
                                padding:"3px 10px",cursor:"pointer",fontSize:10,
                                fontFamily:"Inter"}}>Resolve</button>
                          )}
                        </td>
                      </tr>
                    ))}
                    {tickets.length===0&&(
                      <tr><td colSpan={7}
                        style={{padding:32,textAlign:"center",color:"#334155"}}>
                        No tickets yet</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </Card>
          </div>
        )}

        {/* REPLY QUEUE */}
        {tab === "replies" && (
          <Card>
            <CardHeader icon="💬" title="Pending Reply Approvals"
              badge={pendingReplies.length} badgeColor={C.purple} />
            <div style={{padding:12,maxHeight:"calc(100vh-280px)",overflowY:"auto"}}>
              {pendingReplies.map(m => (
                <MentionCard key={m.id} m={m}
                  onApprove={() => handleApprove(m.id)}
                  onReject={() => handleReject(m.id)} />
              ))}
              {pendingReplies.length===0&&(
                <div style={{padding:40,textAlign:"center",color:"#334155"}}>
                  <div style={{fontSize:32,marginBottom:12}}>✅</div>
                  <div>All replies reviewed. Queue is empty.</div>
                </div>
              )}
            </div>
          </Card>
        )}

        {/* ALERTS */}
        {tab === "alerts" && (
          <Card>
            <CardHeader icon="🚨" title="Critical Alerts"
              badge={alerts.length} badgeColor={C.red} />
            <div style={{padding:12}}>
              {alerts.map(m=>(
                <div key={m.id} style={{background:"#1a0808",border:"1px solid "+C.red+"44",
                  borderRadius:8,padding:12,marginBottom:8,animation:"slideIn .3s ease"}}>
                  <div style={{display:"flex",gap:8,alignItems:"center",marginBottom:6}}>
                    <span style={{fontSize:16}}>🚨</span>
                    <span style={{color:C.red,fontWeight:700,fontSize:12}}>{m.priority} ALERT</span>
                    <SentimentBadge s={m.sentimentLabel}/>
                    <Badge label={m.urgency||"HIGH"} color={C.red}/>
                  </div>
                  <div style={{color:"#f1f5f9",fontSize:12,marginBottom:6}}>{m.text}</div>
                  <div style={{color:"#64748b",fontSize:10}}>
                    @{m.authorUsername} · {m.authorFollowers.toLocaleString()} followers
                    {m.assignedTeam&&<> · Routed to: <span style={{color:C.orange}}>{m.assignedTeam}</span></>}
                  </div>
                </div>
              ))}
              {alerts.length===0&&(
                <div style={{padding:40,textAlign:"center",color:"#334155"}}>
                  <div style={{fontSize:32,marginBottom:12}}>✅</div>
                  <div>No critical alerts</div>
                </div>
              )}
            </div>
          </Card>
        )}

        {/* TEST MENTION */}
        {tab === "test" && (
          <div style={{maxWidth:700}}>
            <Card>
              <CardHeader icon="🧪" title="Test Mention Injection" />
              <div style={{padding:20}}>
                <div style={{marginBottom:14}}>
                  <label style={{color:"#64748b",fontSize:11,display:"block",marginBottom:6}}>
                    Mention Text (simulate a tweet)</label>
                  <textarea
                    value={testText}
                    onChange={e=>setTestText(e.target.value)}
                    placeholder="@AirtelPaymentsBank my UPI payment failed and money got deducted! Please help urgently!"
                    style={{width:"100%",background:"#060a10",border:"1px solid #1e293b",
                      color:"#e2e8f0",padding:"10px 12px",borderRadius:8,
                      fontSize:12,fontFamily:"Inter",resize:"vertical",minHeight:80,outline:"none"}}
                  />
                </div>
                <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12,marginBottom:16}}>
                  <div>
                    <label style={{color:"#64748b",fontSize:11,display:"block",marginBottom:6}}>
                      Author Username</label>
                    <input value={testAuthor} onChange={e=>setTestAuthor(e.target.value)}
                      style={{width:"100%",background:"#060a10",border:"1px solid #1e293b",
                        color:"#e2e8f0",padding:"8px 12px",borderRadius:8,fontSize:12,
                        fontFamily:"Inter",outline:"none"}} />
                  </div>
                  <div>
                    <label style={{color:"#64748b",fontSize:11,display:"block",marginBottom:6}}>
                      Followers Count</label>
                    <input value={testFollowers} onChange={e=>setTestFollowers(e.target.value)}
                      style={{width:"100%",background:"#060a10",border:"1px solid #1e293b",
                        color:"#e2e8f0",padding:"8px 12px",borderRadius:8,fontSize:12,
                        fontFamily:"Inter",outline:"none"}} />
                  </div>
                </div>
                <button onClick={handleSubmitTest} disabled={!testText.trim()||submitting}
                  style={{background:submitting?"#1e293b":C.blue+"22",
                    color:submitting?"#475569":C.blue,
                    border:"1px solid "+(submitting?"#1e293b":C.blue+"44"),
                    borderRadius:8,padding:"10px 24px",cursor:submitting?"not-allowed":"pointer",
                    fontSize:12,fontFamily:"Inter",fontWeight:600,width:"100%"}}>
                  {submitting?"⏳ Processing..." : "🚀 Submit for AI Analysis"}
                </button>
                <div style={{marginTop:16,padding:12,background:"#0a0e14",
                  borderRadius:8,border:"1px solid #1e293b"}}>
                  <div style={{color:"#334155",fontSize:10,marginBottom:6}}>
                    Example test cases:</div>
                  {[
                    "@AirtelPaymentsBank scam! ₹10,000 deducted but payment failed. Going to police!",
                    "@AirtelPaymentsBank your new FD feature is amazing! Got great rates 🎉",
                    "@AirtelPaymentsBank how do I check my KYC status?",
                  ].map((ex,i)=>(
                    <div key={i} onClick={()=>setTestText(ex)}
                      style={{color:"#94a3b8",fontSize:11,padding:"6px 0",
                        borderBottom:"1px solid #1e293b",cursor:"pointer",lineHeight:1.4}}>
                      {ex}
                    </div>
                  ))}
                </div>
              </div>
            </Card>
          </div>
        )}
      </div>

      {/* Footer */}
      <div style={{padding:"8px 24px",borderTop:"1px solid #1e293b",
        background:"#070b12",display:"flex",justifyContent:"space-between",
        color:"#1e3a5f",fontSize:10}}>
        <span>SentinelAI v1.0.0 · Powered by SquadOS v3.4.0 · 7 AI Agents</span>
        <span>React 18 · Recharts · WebSocket · Spring Boot 3.3</span>
        <span>Auto-refresh: 10s mentions · 15s analytics · 5s alerts</span>
      </div>
    </div>
  );
}