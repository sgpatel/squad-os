import { useState, useEffect, useRef, useCallback } from "react";
import { AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { useAuth } from "./auth/AuthContext";
import { LoginPage } from "./auth/LoginPage";
import { Toast, useToast } from "./components/Toast";
import { MentionSkeleton } from "./components/Skeleton";
import { CopyButton } from "./components/CopyButton";
import { InlineReplyEditor } from "./components/InlineReplyEditor";
import { KeyboardHelp } from "./components/KeyboardHelp";
import { useTheme } from "./theme/ThemeContext";
import { useKeyboard } from "./hooks/useKeyboard";
import { useInfiniteScroll } from "./hooks/useInfiniteScroll";
import { useMentions, useLiveEvents, useAnalytics, useTrend,
  useTickets, usePendingReplies, useAlerts,
  approveReply, rejectReply, resolveTicket, ingestMention } from "./hooks/useSentinel";
import { timeAgo, fmtFollowers } from "./utils/timeAgo";
import type { Mention } from "./types";

const C = { green:"#22c55e", blue:"#3b82f6", purple:"#a855f7",
  orange:"#f97316", red:"#ef4444", teal:"#14b8a6", yellow:"#eab308" };
const EMOTION_ICON: Record<string,string> = {
  FRUSTRATION:"😤", ANGER:"😡", SADNESS:"😢", JOY:"😊",
  SURPRISE:"😮", FEAR:"😰", NEUTRAL:"😐", SARCASM:"🙃"
};
const PLATFORM_ICON: Record<string,string> = {
  TWITTER:"🐦", FACEBOOK:"📘", INSTAGRAM:"📷", LINKEDIN:"💼"
};

// ── Shared UI helpers ─────────────────────────────────────────────
function Badge({ label, color }:{ label:string; color:string }) {
  return <span style={{background:color+"1a",color,border:"1px solid "+color+"33",
    borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700,
    display:"inline-block",whiteSpace:"nowrap"}}>{label}</span>;
}
function SentBadge({ s }:{ s?:string }) {
  if (!s) return <Badge label="PENDING" color="#475569"/>;
  return <Badge label={s} color={s==="POSITIVE"?C.green:s==="NEGATIVE"?C.red:C.yellow}/>;
}
function PrioBadge({ p }:{ p?:string }) {
  if (!p) return null;
  return <Badge label={p} color={p==="P1"?C.red:p==="P2"?C.orange:p==="P3"?C.yellow:C.teal}/>;
}
function Dot({ color=C.green }:{ color?:string }) {
  return <div style={{width:7,height:7,borderRadius:"50%",background:color,animation:"pulse 2s infinite",flexShrink:0}}/>;
}
const TT = ({ active, payload, label }:any) => {
  if (!active||!payload?.length) return null;
  return <div style={{background:"var(--bg2)",border:"1px solid var(--border)",borderRadius:8,padding:"10px 14px"}}>
    {label&&<div style={{color:"var(--text2)",fontSize:10,marginBottom:6}}>{label}</div>}
    {payload.map((p:any,i:number)=><div key={i} style={{color:p.color,fontSize:12,fontWeight:600}}>{p.name}: {p.value}</div>)}
  </div>;
};

// ── Brand Health Gauge ────────────────────────────────────────────
function BrandHealthGauge({ score }:{ score:number }) {
  const safe = (!score||isNaN(score)) ? 75 : Math.max(0,Math.min(100,score));
  const color = safe>=70?C.green:safe>=40?C.yellow:C.red;
  const rad = ((safe/100)*180-90)*Math.PI/180;
  return <div style={{textAlign:"center",padding:"12px 0"}}>
    <svg width="180" height="105" viewBox="0 0 180 105">
      <path d="M 20 90 A 70 70 0 0 1 160 90" stroke="var(--border)" strokeWidth="14" fill="none" strokeLinecap="round"/>
      <path d="M 20 90 A 70 70 0 0 1 160 90" stroke={color} strokeWidth="14" fill="none"
        strokeDasharray={(safe/100)*220+" 220"} strokeLinecap="round"/>
      <line x1="90" y1="90" x2={90+55*Math.cos(rad)} y2={90+55*Math.sin(rad)}
        stroke={color} strokeWidth="2.5" strokeLinecap="round"/>
      <circle cx="90" cy="90" r="5" fill={color}/>
      <text x="90" y="72" textAnchor="middle" fill={color} fontSize="26"
        fontWeight="700" fontFamily="JetBrains Mono">{safe.toFixed(0)}</text>
      <text x="90" y="85" textAnchor="middle" fill="var(--muted)" fontSize="9">BRAND HEALTH</text>
    </svg>
    <div style={{fontSize:10,color,fontWeight:700,marginTop:-4}}>
      {safe>=70?"HEALTHY":safe>=40?"MODERATE":"AT RISK"}
    </div>
  </div>;
}

// ── Improved MentionCard with inline editor + copy button ─────────
function MentionCard({ m, onDone, isNew, focused }:
  { m:Mention; onDone?:()=>void; isNew?:boolean; focused?:boolean }) {
  const [exp, setExp] = useState(focused || false);
  const [editReply, setEditReply] = useState(false);
  const sc = m.sentimentLabel==="POSITIVE"?C.green:m.sentimentLabel==="NEGATIVE"?C.red:
    m.sentimentLabel==="NEUTRAL"?C.yellow:"#475569";
  const uc = m.urgency==="CRITICAL"?C.red:m.urgency==="HIGH"?C.orange:
    m.urgency==="MEDIUM"?C.yellow:C.teal;
  const emo = EMOTION_ICON[m.primaryEmotion||""]||"";

  useEffect(() => { if (focused) setExp(true); }, [focused]);

  return <div style={{
    background:"var(--bg2)", border:"1px solid "+(focused?"#3b82f688":isNew?"#3b82f644":"var(--border)"),
    borderLeft:"3px solid "+sc, borderRadius:8, padding:12, marginBottom:8,
    transition:"border-color .2s", animation:isNew?"slideIn .4s ease":"none",
    boxShadow:focused?"0 0 0 2px #3b82f622":isNew?"0 0 14px rgba(59,130,246,.12)":"none",
    outline:"none"
  }} tabIndex={0} onClick={()=>setExp(!exp)}>
    {/* Row 1: author + badges */}
    <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start",gap:8}}>
      <div style={{flex:1,minWidth:0}}>
        <div style={{display:"flex",alignItems:"center",gap:5,marginBottom:4,flexWrap:"wrap" as const}}>
          <span style={{fontSize:12}}>{PLATFORM_ICON[m.platform]||"📱"}</span>
          <span style={{color:"var(--text2)",fontSize:11,fontWeight:600}}>@{m.authorUsername}</span>
          <span style={{color:"var(--dim)",fontSize:9}}>{fmtFollowers(m.authorFollowers)} followers</span>
          {m.isViral&&<Badge label="🔥 VIRAL" color={C.orange}/>}
          <span style={{color:"var(--dim)",fontSize:9,marginLeft:"auto"}}>{timeAgo(m.postedAt)}</span>
        </div>
        <div style={{color:"var(--text)",fontSize:12,lineHeight:1.5,
          overflow:exp?"visible":"hidden",textOverflow:"ellipsis",
          whiteSpace:exp?"normal":"nowrap"}}>{m.text}</div>
        {m.summary&&<div style={{color:"var(--muted)",fontSize:10,marginTop:4,fontStyle:"italic"}}>{m.summary}</div>}
      </div>
      <div style={{display:"flex",flexDirection:"column" as const,gap:3,flexShrink:0,alignItems:"flex-end"}}>
        <SentBadge s={m.sentimentLabel}/>
        {m.priority&&<PrioBadge p={m.priority}/>}
        {m.urgency&&<Badge label={emo+" "+m.urgency} color={uc}/>}
      </div>
    </div>

    {/* Expanded section */}
    {exp&&<div style={{marginTop:10,borderTop:"1px solid var(--border)",paddingTop:10}}
      onClick={e=>e.stopPropagation()}>
      {/* Meta grid */}
      <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:8,marginBottom:8}}>
        {[["Topic",m.topic],["Team",m.assignedTeam],["Priority",m.priority],
          ["Emotion",m.primaryEmotion],["Ticket",m.ticketId],["Status",m.processingStatus]]
          .filter(([,v])=>v).map(([l,v])=>
          <div key={String(l)}>
            <div style={{color:"var(--dim)",fontSize:9,textTransform:"uppercase" as const,letterSpacing:1}}>{l}</div>
            <div style={{color:"var(--text2)",fontSize:11,fontWeight:500}}>{v}</div>
          </div>)}
      </div>

      {/* Action row */}
      <div style={{display:"flex",gap:6,marginBottom:m.replyText?8:0,flexWrap:"wrap" as const}}>
        {m.url&&<a href={m.url} target="_blank" rel="noreferrer"
          style={{background:"none",color:"var(--muted)",border:"1px solid var(--border)",
            borderRadius:5,padding:"3px 8px",fontSize:10,textDecoration:"none",cursor:"pointer"}}>
          🔗 View tweet
        </a>}
        <CopyButton text={m.text} label="Copy text"/>
        {m.url&&<CopyButton text={m.url} label="Copy URL"/>}
      </div>

      {/* Reply section */}
      {m.replyText&&(
        editReply
          ? <InlineReplyEditor mentionId={m.id} originalReply={m.replyText}
              onDone={()=>{ setEditReply(false); onDone?.(); }}/>
          : <div style={{background:"#0a1020",borderRadius:6,padding:10}}>
              <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:6}}>
                <div style={{color:"var(--dim)",fontSize:9,textTransform:"uppercase" as const,letterSpacing:1}}>
                  AI Reply
                  {m.replyStatus&&<span style={{color:m.replyStatus==="APPROVED"?C.green:
                    m.replyStatus==="REJECTED"?C.red:C.orange,marginLeft:6}}>· {m.replyStatus}</span>}
                </div>
                <CopyButton text={m.replyText} label="Copy"/>
              </div>
              <div style={{color:"var(--text)",fontSize:12,lineHeight:1.5,marginBottom:8}}>{m.replyText}</div>
              {m.replyStatus==="PENDING"&&<div style={{display:"flex",gap:6}}>
                <button onClick={()=>setEditReply(true)} style={{
                  background:"#3b82f622",color:C.blue,border:"1px solid #3b82f644",
                  borderRadius:6,padding:"4px 12px",cursor:"pointer",fontSize:11,fontFamily:"Inter",fontWeight:600}}>
                  ✎ Edit & Approve
                </button>
                <button onClick={async(e)=>{e.stopPropagation();await approveReply(m.id);onDone?.();}} style={{
                  background:C.green+"22",color:C.green,border:"1px solid "+C.green+"44",
                  borderRadius:6,padding:"4px 12px",cursor:"pointer",fontSize:11,fontFamily:"Inter"}}>
                  ✓ Approve
                </button>
                <button onClick={async(e)=>{e.stopPropagation();await rejectReply(m.id);onDone?.();}} style={{
                  background:C.red+"22",color:C.red,border:"1px solid "+C.red+"44",
                  borderRadius:6,padding:"4px 12px",cursor:"pointer",fontSize:11,fontFamily:"Inter"}}>
                  ✗ Reject
                </button>
              </div>}
            </div>
      )}
    </div>}
  </div>;
}

function FilterBar({ search, onSearch, filter, onFilter }:
  { search:string; onSearch:(v:string)=>void; filter:string; onFilter:(v:string)=>void }) {
  const FILTERS = ["ALL","POSITIVE","NEGATIVE","NEUTRAL","PENDING","P1","P2"];
  return <div style={{padding:"10px 14px",borderBottom:"1px solid var(--border)",
    display:"flex",gap:8,alignItems:"center",flexWrap:"wrap" as const}}>
    <input value={search} onChange={e=>onSearch(e.target.value)}
      placeholder="Search mentions, authors..."
      style={{background:"var(--bg)",border:"1px solid var(--border)",color:"var(--text)",
        padding:"6px 12px",borderRadius:7,fontSize:12,flex:1,minWidth:140,outline:"none"}}/>
    <div style={{display:"flex",gap:4,flexWrap:"wrap" as const}}>
      {FILTERS.map(f=><button key={f} onClick={()=>onFilter(f===filter?"ALL":f)} style={{
        background:filter===f?C.blue+"22":"none",color:filter===f?C.blue:"var(--muted)",
        border:"1px solid "+(filter===f?C.blue+"44":"var(--border)"),
        borderRadius:6,padding:"4px 10px",cursor:"pointer",fontSize:10,fontFamily:"Inter",
        fontWeight:filter===f?700:400}}>{f}</button>)}
    </div>
  </div>;
}

const TABS = [
  {id:"overview",label:"Overview",icon:"🏠"},
  {id:"feed",label:"Mention Feed",icon:"📡"},
  {id:"analytics",label:"Analytics",icon:"📊"},
  {id:"tickets",label:"Tickets",icon:"🎫"},
  {id:"replies",label:"Reply Queue",icon:"💬"},
  {id:"alerts",label:"Alerts",icon:"🚨"},
  {id:"test",label:"Test",icon:"🧪"},
];

function Sidebar({ tab, setTab, collapsed, setCollapsed, pendingCount, alertCount }:
  { tab:string; setTab:(t:string)=>void; collapsed:boolean;
    setCollapsed:(v:boolean)=>void; pendingCount:number; alertCount:number }) {
  const { isDark, toggle } = useTheme();
  return <div className={"sidebar"+(collapsed?" collapsed":"")}>
    <div style={{padding:"14px 14px 10px",borderBottom:"1px solid var(--border)",
      display:"flex",alignItems:"center",gap:10,justifyContent:collapsed?"center":"flex-start"}}>
      <span style={{fontSize:22,flexShrink:0}}>🛡️</span>
      {!collapsed&&<div>
        <div style={{color:"var(--text)",fontWeight:700,fontSize:14}}>SentinelAI</div>
        <div style={{color:"var(--dim)",fontSize:9}}>Mention Analyser</div>
      </div>}
    </div>
    <nav style={{flex:1,paddingTop:8,overflow:"hidden"}}>
      {TABS.map(t=>{
        const badge = t.id==="replies"?pendingCount:t.id==="alerts"?alertCount:0;
        return <div key={t.id} className={"nav-item"+(tab===t.id?" active":"")}
          onClick={()=>setTab(t.id)} title={collapsed?t.label:undefined}
          style={{position:"relative"}}>
          <span className="icon">{t.icon}</span>
          {!collapsed&&<span style={{flex:1}}>{t.label}</span>}
          {!collapsed&&badge>0&&<span className="badge">{badge}</span>}
          {collapsed&&badge>0&&<span style={{position:"absolute",top:4,right:6,
            background:C.red,color:"white",borderRadius:"50%",width:14,height:14,
            fontSize:8,display:"flex",alignItems:"center",justifyContent:"center",fontWeight:700}}>{badge}</span>}
        </div>;
      })}
    </nav>
    <div style={{padding:"10px 8px",borderTop:"1px solid var(--border)",display:"flex",
      flexDirection:"column" as const,gap:4}}>
      <button onClick={toggle} className="nav-item" style={{margin:0,padding:"8px 10px",
        justifyContent:collapsed?"center":"flex-start"}}>
        <span className="icon">{isDark?"☀️":"🌙"}</span>
        {!collapsed&&<span>{isDark?"Light mode":"Dark mode"}</span>}
      </button>
      <button onClick={()=>setCollapsed(!collapsed)} className="nav-item"
        style={{margin:0,padding:"8px 10px",justifyContent:collapsed?"center":"flex-start"}}>
        <span className="icon">{collapsed?"›":"‹"}</span>
        {!collapsed&&<span>Collapse</span>}
      </button>
    </div>
  </div>;
}

export default function App() {
  const { user, logout } = useAuth();
  if (!user) return <LoginPage/>;

  const { isDark } = useTheme();
  const [tab, setTab]               = useState("overview");
  const [collapsed, setCollapsed]   = useState(false);
  const [search, setSearch]         = useState("");
  const [filter, setFilter]         = useState("ALL");
  const [focusIdx, setFocusIdx]     = useState(0);
  const [testText, setTestText]     = useState("");
  const [testAuthor, setTestAuthor] = useState("test_user");
  const [testFoll, setTestFoll]     = useState("500");
  const [testPlatform, setTestPlatform] = useState("TWITTER");
  const [submitting, setSubmitting] = useState(false);
  const { toasts, add: addToast, remove: removeToast } = useToast();

  const { data: analytics }   = useAnalytics(24);
  const trendData             = useTrend(24);
  const { data: allMentions, loading: mentionsLoading, refetch: refetchMentions } = useMentions(100);
  const liveEvents            = useLiveEvents();
  const { data: tickets, refetch: refetchTickets } = useTickets();
  const { data: pending, refetch: refetchPending }  = usePendingReplies();
  const alerts                = useAlerts();

  // Merge live + historical
  const prevIds = useRef<Set<string>>(new Set());
  useEffect(() => {
    liveEvents.forEach(ev => {
      if (!prevIds.current.has(ev.data.id)) {
        prevIds.current.add(ev.data.id);
        if (ev.type==="mention.processed") {
          const s = ev.data.sentimentLabel;
          addToast({ type: s==="NEGATIVE"?"error":s==="POSITIVE"?"success":"info",
            title: (s||"NEW")+" Mention",
            message: "@"+(ev.data.authorUsername||"")+" · "+(ev.data.text||"").substring(0,60)+"..." });
          refetchMentions();
        }
      }
    });
  }, [liveEvents]);

  const liveMap = new Map(liveEvents.map(e=>[e.data.id,e.data]));
  const merged: Mention[] = [
    ...liveEvents.filter(e=>e.type==="mention.new").map(e=>e.data),
    ...allMentions.filter(m=>!liveMap.has(m.id)||liveMap.get(m.id)!.processingStatus!=="NEW"),
  ].filter((m,i,a)=>a.findIndex(x=>x.id===m.id)===i);

  const filteredMentions = merged.filter(m=>{
    const ms=!search||(m.text||"").toLowerCase().includes(search.toLowerCase())||
      (m.authorUsername||"").toLowerCase().includes(search.toLowerCase());
    const mf=filter==="ALL"||
      (["POSITIVE","NEGATIVE","NEUTRAL"].includes(filter)&&m.sentimentLabel===filter)||
      (filter==="PENDING"&&m.replyStatus==="PENDING")||
      (["P1","P2"].includes(filter)&&m.priority===filter);
    return ms&&mf;
  });

  const newIds = new Set(liveEvents.filter(e=>e.type==="mention.new").map(e=>e.data.id));
  const { visible, hasMore, sentinelRef, reset: resetScroll } = useInfiniteScroll(filteredMentions, 15);
  useEffect(()=>{ resetScroll(); setFocusIdx(0); }, [search, filter, tab]);

  // Keyboard shortcuts
  useKeyboard([
    { key:"1", action:()=>setTab("overview")  },
    { key:"2", action:()=>setTab("feed")      },
    { key:"3", action:()=>setTab("analytics") },
    { key:"4", action:()=>setTab("tickets")   },
    { key:"5", action:()=>setTab("replies")   },
    { key:"6", action:()=>setTab("alerts")    },
    { key:"7", action:()=>setTab("test")      },
    { key:"j", action:()=>setFocusIdx(i=>Math.min(i+1,filteredMentions.length-1)) },
    { key:"k", action:()=>setFocusIdx(i=>Math.max(i-1,0)) },
    { key:"a", action:async()=>{ const m=filteredMentions[focusIdx];
      if(m?.replyStatus==="PENDING"){ await approveReply(m.id); refetchPending(); refetchMentions();
        addToast({type:"success",title:"Approved",message:"Reply approved via keyboard"}); }}},
    { key:"r", action:async()=>{ const m=filteredMentions[focusIdx];
      if(m?.replyStatus==="PENDING"){ await rejectReply(m.id); refetchPending(); refetchMentions();
        addToast({type:"warning",title:"Rejected",message:"Reply rejected via keyboard"}); }}},
    { key:"Escape", action:()=>{ setSearch(""); setFilter("ALL"); } },
  ]);

  const doTest = async() => {
    if(!testText.trim()) return;
    setSubmitting(true);
    await ingestMention(testText, testAuthor, parseInt(testFoll)||500, testPlatform);
    setTestText(""); setSubmitting(false);
    addToast({type:"info",title:"Submitted",message:"AI pipeline is analysing your mention..."});
    setTimeout(refetchMentions, 5000);
  };

  const health = analytics?.brandHealthScore ?? 0;
  const negList = merged.filter(m=>m.sentimentLabel==="NEGATIVE");
  const posList = merged.filter(m=>m.sentimentLabel==="POSITIVE");

  const onDone = useCallback(()=>{ refetchPending(); refetchMentions(); }, []);

  return <div className="app-layout">
    <Sidebar tab={tab} setTab={setTab} collapsed={collapsed} setCollapsed={setCollapsed}
      pendingCount={pending.length} alertCount={alerts.length}/>

    <div className="main-area">
      {/* Header */}
      <div style={{background:"var(--header-bg)",borderBottom:"1px solid var(--border)",
        padding:"0 20px",height:50,display:"flex",alignItems:"center",
        justifyContent:"space-between",flexShrink:0}}>
        <div style={{color:"var(--text2)",fontSize:12,fontWeight:500}}>
          {TABS.find(t=>t.id===tab)?.icon}&nbsp;{TABS.find(t=>t.id===tab)?.label}
        </div>
        <div style={{display:"flex",alignItems:"center",gap:8}}>
          <Dot/><span style={{color:C.green,fontSize:11}}>LIVE</span>
          <span style={{color:"var(--dim)",fontSize:11}}>{merged.length} mention{merged.length!==1?"s":""}</span>
          <div style={{width:1,height:16,background:"var(--border)",margin:"0 2px"}}/>
          <KeyboardHelp/>
          <div style={{display:"flex",alignItems:"center",gap:6,background:"var(--bg2)",
            border:"1px solid var(--border)",borderRadius:8,padding:"4px 10px"}}>
            <div style={{width:22,height:22,borderRadius:"50%",
              background:"linear-gradient(135deg,#3b82f6,#2563eb)",
              display:"flex",alignItems:"center",justifyContent:"center",
              fontSize:10,fontWeight:700,color:"white"}}>{user.username[0].toUpperCase()}</div>
            <span style={{color:"var(--text)",fontSize:11,fontWeight:600}}>{user.fullName||user.username}</span>
            <span style={{background:C.blue+"22",color:C.blue,borderRadius:4,
              padding:"1px 6px",fontSize:9,fontWeight:700}}>{user.role}</span>
          </div>
          <button onClick={logout} style={{background:"none",border:"1px solid var(--border)",
            color:"var(--muted)",padding:"5px 10px",borderRadius:7,cursor:"pointer",
            fontSize:11,transition:"all .2s"}}
            onMouseEnter={e=>{e.currentTarget.style.borderColor="#ef4444";e.currentTarget.style.color="#ef4444"}}
            onMouseLeave={e=>{e.currentTarget.style.borderColor="var(--border)";e.currentTarget.style.color="var(--muted)"}}>
            Sign Out
          </button>
        </div>
      </div>

      {/* P1 alert banner */}
      {alerts.length>0&&<div style={{background:"#1a0808",borderBottom:"1px solid #ef444433",
        padding:"6px 20px",display:"flex",alignItems:"center",gap:8,flexShrink:0}}>
        <span>🚨</span>
        <span style={{color:C.red,fontWeight:700,fontSize:12}}>
          {alerts.length} P1 Alert{alerts.length>1?"s":""} — immediate action required</span>
        <button onClick={()=>setTab("alerts")} style={{background:C.red+"22",color:C.red,
          border:"1px solid "+C.red+"44",borderRadius:5,padding:"2px 10px",
          cursor:"pointer",fontSize:10,fontFamily:"Inter"}}>View →</button>
        <div style={{marginLeft:4}}><Dot color={C.red}/></div>
      </div>}

      {/* Stats bar */}
      <div style={{display:"grid",gridTemplateColumns:"repeat(7,1fr)",gap:1,
        background:"var(--border)",borderBottom:"1px solid var(--border)",flexShrink:0}}>
        {[{l:"Mentions",v:analytics?.totalMentions??"-",c:C.blue,i:"📡"},
          {l:"Positive",v:analytics?.positiveMentions??"-",c:C.green,i:"😊"},
          {l:"Negative",v:analytics?.negativeMentions??"-",c:C.red,i:"😡"},
          {l:"Neutral",v:analytics?.neutralMentions??"-",c:C.yellow,i:"😐"},
          {l:"Health",v:health>0?health.toFixed(0)+"%":"—",c:C.teal,i:"💚"},
          {l:"Tickets",v:analytics?.openTickets??"-",c:C.orange,i:"🎫"},
          {l:"Pending",v:analytics?.pendingReplies??"-",c:C.purple,i:"💬"}]
          .map((s,i)=><div key={i} style={{background:"var(--bg)",padding:"10px 14px"}}>
            <div style={{display:"flex",justifyContent:"space-between"}}>
              <div>
                <div style={{fontSize:20,fontWeight:700,color:s.c,fontFamily:"JetBrains Mono",marginBottom:1}}>{s.v}</div>
                <div style={{fontSize:9,color:"var(--muted)",textTransform:"uppercase" as const,letterSpacing:"1px",fontWeight:600}}>{s.l}</div>
              </div>
              <span style={{fontSize:16,opacity:.25}}>{s.i}</span>
            </div>
          </div>)}
      </div>

      {/* Main content */}
      <div className="content-area">

        {tab==="overview"&&<div>
          <div style={{display:"grid",gridTemplateColumns:"200px 1fr",gap:12,marginBottom:12}}>
            <div style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
              <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",color:"var(--text)",fontWeight:600,fontSize:11}}>💚 Brand Health</div>
              <BrandHealthGauge score={health}/>
              <div style={{padding:"0 14px 12px"}}>
                {[["Positive",analytics?Math.round(analytics.positiveMentions/(analytics.totalMentions||1)*100)+"%":"—",C.green],
                  ["Negative",analytics?Math.round(analytics.negativeMentions/(analytics.totalMentions||1)*100)+"%":"—",C.red],
                  ["Avg Score",analytics?analytics.avgSentimentScore.toFixed(2):"—",C.blue]]
                  .map(([l,v,c])=><div key={String(l)} style={{display:"flex",justifyContent:"space-between",
                    padding:"4px 0",borderBottom:"1px solid var(--border)"}}>
                    <span style={{color:"var(--muted)",fontSize:11}}>{l}</span>
                    <span style={{color:c as string,fontWeight:700,fontFamily:"JetBrains Mono",fontSize:12}}>{v}</span>
                  </div>)}
              </div>
            </div>
            <div style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
              <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",color:"var(--text)",fontWeight:600,fontSize:11}}>📈 Sentiment Trend (24h)</div>
              <div style={{height:220,paddingTop:8}}>
                <ResponsiveContainer><AreaChart data={trendData} margin={{top:8,right:12,bottom:8,left:0}}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)"/>
                  <XAxis dataKey="hour" tick={{fill:"var(--muted)",fontSize:9}} tickFormatter={v=>v+"h"}/>
                  <YAxis tick={{fill:"var(--muted)",fontSize:9}}/>
                  <Tooltip content={<TT/>}/><Legend iconType="circle" iconSize={8} wrapperStyle={{fontSize:10}}/>
                  <Area type="monotone" dataKey="positive" stackId="1" stroke={C.green} fill={C.green+"33"} name="Positive"/>
                  <Area type="monotone" dataKey="neutral"  stackId="1" stroke={C.yellow} fill={C.yellow+"22"} name="Neutral"/>
                  <Area type="monotone" dataKey="negative" stackId="1" stroke={C.red} fill={C.red+"33"} name="Negative"/>
                </AreaChart></ResponsiveContainer>
              </div>
            </div>
          </div>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12}}>
            {[[negList,"🔴 Negative",C.red],[posList,"🟢 Positive",C.green]].map(([list,title,color])=>(
              <div key={String(title)} style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
                <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",
                  display:"flex",justifyContent:"space-between"}}>
                  <span style={{color:"var(--text)",fontWeight:600,fontSize:11}}>{String(title)}</span>
                  <span style={{background:String(color)+"1a",color:String(color),border:"1px solid "+String(color)+"33",
                    borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700}}>{(list as Mention[]).length}</span>
                </div>
                <div style={{maxHeight:290,overflowY:"auto",padding:10}}>
                  {mentionsLoading?[0,1,2].map(i=><MentionSkeleton key={i}/>):
                   (list as Mention[]).length===0?<div style={{padding:32,textAlign:"center",color:"var(--dim)"}}>
                     <div style={{fontSize:28,marginBottom:8}}>{String(color)===C.red?"✅":"⏳"}</div>
                     No {String(title).split(" ")[1].toLowerCase()} mentions</div>:
                   (list as Mention[]).slice(0,5).map((m,i)=><MentionCard key={m.id} m={m}
                     isNew={newIds.has(m.id)} focused={focusIdx===i} onDone={onDone}/>)}
                </div>
              </div>
            ))}
          </div>
        </div>}

        {tab==="feed"&&<div style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
          <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",
            display:"flex",justifyContent:"space-between"}}>
            <span style={{color:"var(--text)",fontWeight:600,fontSize:11}}>📡 All Mentions</span>
            <span style={{background:C.blue+"1a",color:C.blue,border:"1px solid "+C.blue+"33",
              borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700}}>{filteredMentions.length}</span>
          </div>
          <FilterBar search={search} onSearch={setSearch} filter={filter} onFilter={setFilter}/>
          <div style={{maxHeight:"calc(100vh - 340px)",overflowY:"auto",padding:12}}>
            {mentionsLoading?[0,1,2,3].map(i=><MentionSkeleton key={i}/>):
             visible.length===0?<div style={{padding:40,textAlign:"center",color:"var(--dim)"}}>
               <div style={{fontSize:32,marginBottom:12}}>🔍</div>No mentions match filter</div>:
             visible.map((m,i)=><MentionCard key={m.id} m={m} isNew={newIds.has(m.id)}
               focused={focusIdx===i} onDone={onDone}/>)}
            {hasMore&&<div ref={sentinelRef} style={{padding:12,textAlign:"center",color:"var(--dim)",fontSize:11}}>↓ Loading more...</div>}
          </div>
        </div>}

        {tab==="analytics"&&<div>
          <div style={{display:"grid",gridTemplateColumns:"repeat(3,1fr)",gap:12,marginBottom:12}}>
            {[{l:"Total Mentions",v:analytics?.totalMentions??0,c:C.blue,i:"📡"},
              {l:"Brand Health",v:health>0?health.toFixed(1)+"%":"—",c:C.teal,i:"💚"},
              {l:"Critical Alerts",v:analytics?.criticalAlerts??0,c:C.red,i:"🚨"}]
              .map((s,i)=><div key={i} style={{background:"var(--card-bg)",border:"1px solid var(--border)",
                borderRadius:10,padding:"14px 18px"}}>
                <div style={{display:"flex",justifyContent:"space-between"}}>
                  <div><div style={{fontSize:28,fontWeight:700,color:s.c,fontFamily:"JetBrains Mono",marginBottom:3}}>{s.v}</div>
                    <div style={{color:"var(--muted)",fontSize:9,textTransform:"uppercase" as const,letterSpacing:"1.2px",fontWeight:600}}>{s.l}</div></div>
                  <span style={{fontSize:24,opacity:.3}}>{s.i}</span>
                </div>
              </div>)}
          </div>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12}}>
            <div style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
              <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",color:"var(--text)",fontWeight:600,fontSize:11}}>📊 Sentiment Distribution</div>
              <div style={{height:240,paddingTop:8}}>
                <ResponsiveContainer><PieChart>
                  <Pie data={[{name:"Positive",value:analytics?.positiveMentions??0,fill:C.green},
                    {name:"Negative",value:analytics?.negativeMentions??0,fill:C.red},
                    {name:"Neutral",value:analytics?.neutralMentions??0,fill:C.yellow}].filter(d=>d.value>0)}
                    cx="50%" cy="50%" innerRadius={55} outerRadius={90} dataKey="value" paddingAngle={3}>
                    {[C.green,C.red,C.yellow].map((c,i)=><Cell key={i} fill={c} strokeWidth={0}/>)}
                  </Pie><Tooltip content={<TT/>}/><Legend iconType="circle" iconSize={8} wrapperStyle={{fontSize:10}}/>
                </PieChart></ResponsiveContainer>
              </div>
            </div>
            <div style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
              <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",color:"var(--text)",fontWeight:600,fontSize:11}}>📈 24h Trend</div>
              <div style={{height:240,paddingTop:8}}>
                <ResponsiveContainer><BarChart data={trendData} margin={{top:8,right:12,bottom:8,left:0}}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)"/>
                  <XAxis dataKey="hour" tick={{fill:"var(--muted)",fontSize:9}} tickFormatter={v=>v+"h"}/>
                  <YAxis tick={{fill:"var(--muted)",fontSize:9}} allowDecimals={false}/>
                  <Tooltip content={<TT/>}/>
                  <Bar dataKey="positive" name="Positive" fill={C.green} radius={[3,3,0,0]} stackId="a"/>
                  <Bar dataKey="neutral"  name="Neutral"  fill={C.yellow} stackId="a"/>
                  <Bar dataKey="negative" name="Negative" fill={C.red} radius={[0,0,3,3]} stackId="a"/>
                </BarChart></ResponsiveContainer>
              </div>
            </div>
          </div>
        </div>}

        {tab==="tickets"&&<div>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:12,marginBottom:12}}>
            {[{l:"Open",v:analytics?.openTickets??0,c:C.orange,i:"🎫"},
              {l:"Resolved",v:analytics?.resolvedTickets??0,c:C.green,i:"✅"},
              {l:"Total",v:tickets.length,c:C.blue,i:"📋"}]
              .map((s,i)=><div key={i} style={{background:"var(--card-bg)",border:"1px solid var(--border)",
                borderRadius:10,padding:"14px 18px"}}>
                <div style={{display:"flex",justifyContent:"space-between"}}>
                  <div><div style={{fontSize:26,fontWeight:700,color:s.c,fontFamily:"JetBrains Mono",marginBottom:3}}>{s.v}</div>
                    <div style={{color:"var(--muted)",fontSize:9,textTransform:"uppercase" as const,letterSpacing:"1.2px",fontWeight:600}}>{s.l}</div></div>
                  <span style={{fontSize:22,opacity:.3}}>{s.i}</span>
                </div>
              </div>)}
          </div>
          <div style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
            <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",
              display:"flex",justifyContent:"space-between"}}>
              <span style={{color:"var(--text)",fontWeight:600,fontSize:11}}>🎫 Ticket Pipeline</span>
              <span style={{background:C.blue+"1a",color:C.blue,border:"1px solid "+C.blue+"33",
                borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700}}>{tickets.length}</span>
            </div>
            <div style={{overflowX:"auto"}}>
              <table style={{width:"100%",borderCollapse:"collapse"}}>
                <thead><tr>{["ID","Title","Status","Priority","Team","Created","Actions"].map(h=>(
                  <th key={h} style={{padding:"8px 14px",textAlign:"left",fontSize:9,
                    textTransform:"uppercase" as const,letterSpacing:1,color:"var(--muted)",
                    borderBottom:"1px solid var(--border)",background:"var(--bg)",fontWeight:600}}>{h}</th>
                ))}</tr></thead>
                <tbody>
                  {tickets.map((t:any)=><tr key={t.id} style={{borderBottom:"1px solid var(--border)"}}>
                    <td style={{padding:"9px 14px",fontFamily:"JetBrains Mono",fontSize:11,color:C.blue}}>{t.id}</td>
                    <td style={{padding:"9px 14px",fontSize:12,color:"var(--text)",fontWeight:600,
                      maxWidth:260,overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"}}>{t.title}</td>
                    <td style={{padding:"9px 14px"}}><span style={{background:t.status==="RESOLVED"?C.green+"1a":C.orange+"1a",
                      color:t.status==="RESOLVED"?C.green:C.orange,border:"1px solid "+(t.status==="RESOLVED"?C.green:C.orange)+"33",
                      borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700}}>{t.status}</span></td>
                    <td style={{padding:"9px 14px"}}>{t.priority&&<span style={{background:t.priority==="P1"?C.red+"1a":C.orange+"1a",
                      color:t.priority==="P1"?C.red:C.orange,border:"1px solid "+(t.priority==="P1"?C.red:C.orange)+"33",
                      borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700}}>{t.priority}</span>}</td>
                    <td style={{padding:"9px 14px",fontSize:11,color:"var(--text2)"}}>{t.team}</td>
                    <td style={{padding:"9px 14px",fontSize:10,color:"var(--dim)"}}>{new Date(t.createdAt).toLocaleTimeString()}</td>
                    <td style={{padding:"9px 14px"}}>
                      <div style={{display:"flex",gap:4}}>
                        {t.status==="OPEN"&&<button onClick={async()=>{
                          await resolveTicket(t.id,"Resolved via dashboard");
                          refetchTickets();
                          addToast({type:"success",title:"Resolved",message:t.id+" marked resolved"});}}
                          style={{background:C.green+"22",color:C.green,border:"1px solid "+C.green+"44",
                            borderRadius:5,padding:"3px 8px",cursor:"pointer",fontSize:10,fontFamily:"Inter"}}>
                          Resolve</button>}
                        <CopyButton text={t.mentionText||t.title} label="Copy"/>
                      </div>
                    </td>
                  </tr>)}
                  {!tickets.length&&<tr><td colSpan={7} style={{padding:32,textAlign:"center",color:"var(--dim)"}}>
                    No tickets yet</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </div>}

        {tab==="replies"&&<div style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
          <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",
            display:"flex",justifyContent:"space-between",alignItems:"center"}}>
            <span style={{color:"var(--text)",fontWeight:600,fontSize:11}}>💬 Reply Queue</span>
            <div style={{display:"flex",gap:8,alignItems:"center"}}>
              <span style={{color:"var(--dim)",fontSize:10}}>⌨ A=approve · R=reject · J/K=navigate</span>
              <span style={{background:C.purple+"1a",color:C.purple,border:"1px solid "+C.purple+"33",
                borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700}}>{pending.length}</span>
            </div>
          </div>
          <div style={{padding:12,maxHeight:"calc(100vh - 280px)",overflowY:"auto"}}>
            {pending.length===0?<div style={{padding:40,textAlign:"center",color:"var(--dim)"}}>
              <div style={{fontSize:32,marginBottom:12}}>✅</div><div>Queue empty</div></div>:
             pending.map((m,i)=><MentionCard key={m.id} m={m} focused={focusIdx===i} onDone={onDone}/>)}
          </div>
        </div>}

        {tab==="alerts"&&<div style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
          <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",
            display:"flex",justifyContent:"space-between"}}>
            <span style={{color:"var(--text)",fontWeight:600,fontSize:11}}>🚨 Critical Alerts</span>
            <span style={{background:C.red+"1a",color:C.red,border:"1px solid "+C.red+"33",
              borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700}}>{alerts.length}</span>
          </div>
          <div style={{padding:12}}>
            {alerts.map(m=><div key={m.id} style={{background:"var(--bg)",border:"1px solid #ef444444",
              borderLeft:"3px solid "+C.red,borderRadius:8,padding:12,marginBottom:8,animation:"slideIn .3s ease"}}>
              <div style={{display:"flex",gap:8,alignItems:"center",marginBottom:6}}>
                <span style={{fontSize:16}}>🚨</span>
                <span style={{color:C.red,fontWeight:700,fontSize:12}}>{m.priority} ALERT</span>
                <span style={{background:(m.sentimentLabel==="POSITIVE"?C.green:m.sentimentLabel==="NEGATIVE"?C.red:C.yellow)+"1a",
                  color:m.sentimentLabel==="POSITIVE"?C.green:m.sentimentLabel==="NEGATIVE"?C.red:C.yellow,
                  border:"1px solid "+(m.sentimentLabel==="POSITIVE"?C.green:m.sentimentLabel==="NEGATIVE"?C.red:C.yellow)+"33",
                  borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700}}>{m.sentimentLabel||"—"}</span>
                {m.urgency&&<span style={{background:C.red+"1a",color:C.red,border:"1px solid "+C.red+"33",
                  borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700}}>{m.urgency}</span>}
                <span style={{color:"var(--dim)",fontSize:10,marginLeft:"auto"}}>{timeAgo(m.postedAt)}</span>
                <CopyButton text={m.url||m.text} label="Copy URL"/>
              </div>
              <div style={{color:"var(--text)",fontSize:12,marginBottom:6,lineHeight:1.5}}>{m.text}</div>
              <div style={{color:"var(--text2)",fontSize:10}}>
                @{m.authorUsername} · {fmtFollowers(m.authorFollowers)} followers
                {m.assignedTeam&&<> · <span style={{color:C.orange}}>{m.assignedTeam}</span></>}
              </div>
            </div>)}
            {!alerts.length&&<div style={{padding:40,textAlign:"center",color:"var(--dim)"}}>
              <div style={{fontSize:32,marginBottom:12}}>✅</div><div>No critical alerts</div></div>}
          </div>
        </div>}

        {tab==="test"&&<div style={{maxWidth:660}}>
          <div style={{background:"var(--card-bg)",border:"1px solid var(--border)",borderRadius:10,overflow:"hidden"}}>
            <div style={{padding:"11px 16px",borderBottom:"1px solid var(--border)",color:"var(--text)",fontWeight:600,fontSize:11}}>🧪 Test Mention Injection</div>
            <div style={{padding:20}}>
              <div style={{marginBottom:14}}>
                <label style={{display:"block",color:"var(--muted)",fontSize:11,marginBottom:6}}>MENTION TEXT</label>
                <textarea value={testText} onChange={e=>setTestText(e.target.value)}
                  placeholder="@AirtelPaymentsBank my UPI payment failed! Please help!"
                  style={{width:"100%",background:"var(--bg)",border:"1px solid var(--border)",color:"var(--text)",
                    padding:"10px 12px",borderRadius:8,fontSize:12,resize:"vertical",minHeight:80,outline:"none"}}/>
              </div>
              <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:12,marginBottom:16}}>
                <div>
                  <label style={{display:"block",color:"var(--muted)",fontSize:11,marginBottom:6}}>PLATFORM</label>
                  <select value={testPlatform} onChange={e=>setTestPlatform(e.target.value)}
                    style={{width:"100%",background:"var(--bg)",border:"1px solid var(--border)",
                      color:"var(--text)",padding:"8px 12px",borderRadius:8,fontSize:12,outline:"none"}}>
                    <option value="TWITTER">🐦 Twitter</option>
                    <option value="FACEBOOK">📘 Facebook</option>
                    <option value="INSTAGRAM">📷 Instagram</option>
                    <option value="LINKEDIN">💼 LinkedIn</option>
                  </select>
                </div>
                <div>
                  <label style={{display:"block",color:"var(--muted)",fontSize:11,marginBottom:6}}>AUTHOR</label>
                  <input value={testAuthor} onChange={e=>setTestAuthor(e.target.value)}
                    style={{width:"100%",background:"var(--bg)",border:"1px solid var(--border)",
                      color:"var(--text)",padding:"8px 12px",borderRadius:8,fontSize:12,outline:"none"}}/>
                </div>
                <div>
                  <label style={{display:"block",color:"var(--muted)",fontSize:11,marginBottom:6}}>FOLLOWERS</label>
                  <input value={testFoll} onChange={e=>setTestFoll(e.target.value)} type="number"
                    style={{width:"100%",background:"var(--bg)",border:"1px solid var(--border)",
                      color:"var(--text)",padding:"8px 12px",borderRadius:8,fontSize:12,outline:"none"}}/>
                </div>
              </div>
              <button onClick={doTest} disabled={!testText.trim()||submitting} style={{
                width:"100%",background:submitting||!testText.trim()?"var(--border)":"linear-gradient(135deg,#3b82f6,#2563eb)",
                color:submitting||!testText.trim()?"var(--muted)":"white",
                border:"none",borderRadius:8,padding:"11px",fontSize:13,
                fontWeight:600,cursor:submitting||!testText.trim()?"not-allowed":"pointer"}}>
                {submitting?"⏳ Processing...":"🚀 Submit for AI Analysis"}
              </button>
              <div style={{marginTop:14,padding:12,background:"var(--bg)",borderRadius:8,border:"1px solid var(--border)"}}>
                <div style={{color:"var(--dim)",fontSize:10,marginBottom:6}}>Quick examples:</div>
                {["@AirtelPaymentsBank scam! Rs10,000 deducted! @RBI_Informs",
                  "@AirtelPaymentsBank the new FD feature is amazing! 🎉",
                  "@AirtelPaymentsBank how do I check my KYC status?"]
                  .map((ex,i)=><div key={i} onClick={()=>setTestText(ex)}
                    style={{color:"var(--text2)",fontSize:11,padding:"6px 0",
                      borderBottom:"1px solid var(--border)",cursor:"pointer",lineHeight:1.4}}>{ex}</div>)}
              </div>
            </div>
          </div>
        </div>}

      </div>

      {/* Footer */}
      <div style={{padding:"6px 20px",borderTop:"1px solid var(--border)",background:"var(--bg)",
        display:"flex",justifyContent:"space-between",color:"var(--dim)",fontSize:10,flexShrink:0}}>
        <span>SentinelAI v1.0.0 · SquadOS v3.4.0</span>
        <span>⌨ Press ? for shortcuts · 1-7 switch tabs · J/K navigate · A approve · R reject</span>
        <span>Mentions: {merged.length} · Pending: {pending.length} · Tickets: {tickets.length}</span>
      </div>
    </div>
    <Toast toasts={toasts} onRemove={removeToast}/>
  </div>;
}

