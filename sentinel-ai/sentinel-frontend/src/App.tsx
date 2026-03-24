import { useState, useEffect, useRef } from "react";
import { AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { useAuth } from "./auth/AuthContext";
import { LoginPage } from "./auth/LoginPage";
import { Toast, useToast } from "./components/Toast";
import { MentionSkeleton } from "./components/Skeleton";
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

function Badge({ label, color }:{ label:string; color:string }) {
  return <span style={{background:color+"1a",color,border:"1px solid "+color+"33",borderRadius:20,
    padding:"2px 9px",fontSize:9,fontWeight:700,display:"inline-block",whiteSpace:"nowrap"}}>{label}</span>;
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
  return <div style={{width:7,height:7,borderRadius:"50%",background:color,
    animation:"pulse 2s infinite",flexShrink:0}}/>;
}
function Card({ children, style }:{ children:React.ReactNode; style?:React.CSSProperties }) {
  return <div style={{background:"linear-gradient(135deg,#0d1424,#111827)",
    border:"1px solid #1e293b",borderRadius:10,overflow:"hidden",
    animation:"fadeUp .25s ease",...style}}>{children}</div>;
}
function CardHeader({ icon, title, badge, badgeColor=C.blue }:
  { icon:string; title:string; badge?:string|number; badgeColor?:string }) {
  return <div style={{padding:"11px 16px",borderBottom:"1px solid #1e293b",
    background:"linear-gradient(90deg,#0a1020,#0d1424)",
    display:"flex",alignItems:"center",justifyContent:"space-between"}}>
    <div style={{color:"#f1f5f9",fontWeight:600,fontSize:11,display:"flex",alignItems:"center",gap:7}}>
      <span style={{fontSize:14}}>{icon}</span>{title}</div>
    {badge!==undefined&&<Badge label={String(badge)} color={badgeColor}/>}
  </div>;
}
const TT = ({ active, payload, label }:any) => {
  if (!active||!payload?.length) return null;
  return <div style={{background:"#0d1424",border:"1px solid #1e293b",borderRadius:8,padding:"10px 14px"}}>
    {label&&<div style={{color:"#94a3b8",fontSize:10,marginBottom:6}}>{label}</div>}
    {payload.map((p:any,i:number)=>(
      <div key={i} style={{color:p.color,fontSize:12,fontWeight:600}}>{p.name}: {p.value}</div>
    ))}
  </div>;
};
function StatCell({ label, value, color, icon }:
  { label:string; value:string|number; color:string; icon:string }) {
  return <div style={{background:"#070b12",padding:"12px 16px"}}>
    <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start"}}>
      <div>
        <div style={{fontSize:22,fontWeight:700,color,fontFamily:"JetBrains Mono",marginBottom:2}}>{value??"-"}</div>
        <div style={{fontSize:9,color:"#475569",textTransform:"uppercase",letterSpacing:"1px",fontWeight:600}}>{label}</div>
      </div>
      <span style={{fontSize:18,opacity:.25}}>{icon}</span>
    </div>
  </div>;
}

function BrandHealthGauge({ score }:{ score:number }) {
  const safe = (!score||isNaN(score)) ? 75 : Math.max(0,Math.min(100,score));
  const color = safe>=70?C.green:safe>=40?C.yellow:C.red;
  const rad = ((safe/100)*180-90)*Math.PI/180;
  return <div style={{textAlign:"center",padding:"12px 0"}}>
    <svg width="180" height="105" viewBox="0 0 180 105">
      <path d="M 20 90 A 70 70 0 0 1 160 90" stroke="#1e293b" strokeWidth="14" fill="none" strokeLinecap="round"/>
      <path d="M 20 90 A 70 70 0 0 1 160 90" stroke={color} strokeWidth="14" fill="none"
        strokeDasharray={(safe/100)*220+" 220"} strokeLinecap="round"/>
      <line x1="90" y1="90" x2={90+55*Math.cos(rad)} y2={90+55*Math.sin(rad)}
        stroke={color} strokeWidth="2.5" strokeLinecap="round"/>
      <circle cx="90" cy="90" r="5" fill={color}/>
      <text x="90" y="72" textAnchor="middle" fill={color} fontSize="26"
        fontWeight="700" fontFamily="JetBrains Mono">{safe.toFixed(0)}</text>
      <text x="90" y="85" textAnchor="middle" fill="#334155" fontSize="9">BRAND HEALTH</text>
      <text x="28" y="100" fill="#334155" fontSize="8">0</text>
      <text x="152" y="100" fill="#334155" fontSize="8">100</text>
    </svg>
    <div style={{fontSize:10,color,fontWeight:700,marginTop:-4}}>
      {safe>=70?"HEALTHY":safe>=40?"MODERATE":"AT RISK"}
    </div>
  </div>;
}

function MentionCard({ m, onApprove, onReject, isNew }:
  { m:Mention; onApprove?:()=>void; onReject?:()=>void; isNew?:boolean }) {
  const [exp, setExp] = useState(false);
  const sc = m.sentimentLabel==="POSITIVE"?C.green:m.sentimentLabel==="NEGATIVE"?C.red:
    m.sentimentLabel==="NEUTRAL"?C.yellow:"#475569";
  const uc = m.urgency==="CRITICAL"?C.red:m.urgency==="HIGH"?C.orange:
    m.urgency==="MEDIUM"?C.yellow:C.teal;
  const emo = EMOTION_ICON[m.primaryEmotion||""]||"";
  return <div onClick={()=>setExp(!exp)} style={{
    background:"#080e1a", border:"1px solid "+(isNew?"#3b82f644":"#1e293b"),
    borderLeft:"3px solid "+sc, borderRadius:8, padding:12, marginBottom:8,
    cursor:"pointer", transition:"border-color .2s",
    animation:isNew?"slideIn .4s ease":"none",
    boxShadow:isNew?"0 0 14px rgba(59,130,246,.12)":"none"}}>
    <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start",gap:8}}>
      <div style={{flex:1,minWidth:0}}>
        <div style={{display:"flex",alignItems:"center",gap:5,marginBottom:4,flexWrap:"wrap" as const}}>
          {isNew&&<span style={{background:C.blue+"22",color:C.blue,borderRadius:4,
            padding:"1px 6px",fontSize:9,fontWeight:700}}>NEW</span>}
          <span style={{color:"#94a3b8",fontSize:11,fontWeight:600}}>@{m.authorUsername}</span>
          <span style={{color:"#334155",fontSize:9}}>{fmtFollowers(m.authorFollowers)} followers</span>
          {m.isViral&&<Badge label="🔥 VIRAL" color={C.orange}/>}
          <span style={{color:"#1e3a5f",fontSize:9,marginLeft:"auto"}}>{timeAgo(m.postedAt)}</span>
        </div>
        <div style={{color:"#e2e8f0",fontSize:12,lineHeight:1.5,
          overflow:exp?"visible":"hidden",textOverflow:"ellipsis",
          whiteSpace:exp?"normal":"nowrap"}}>{m.text}</div>
        {m.summary&&<div style={{color:"#475569",fontSize:10,marginTop:4,fontStyle:"italic"}}>{m.summary}</div>}
      </div>
      <div style={{display:"flex",flexDirection:"column" as const,gap:3,flexShrink:0,alignItems:"flex-end"}}>
        <SentBadge s={m.sentimentLabel}/>
        {m.priority&&<PrioBadge p={m.priority}/>}
        {m.urgency&&<Badge label={emo+" "+m.urgency} color={uc}/>}
      </div>
    </div>
    {exp&&<div style={{marginTop:10,borderTop:"1px solid #1e293b",paddingTop:10}}>
      <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:8,marginBottom:8}}>
        {[["Topic",m.topic],["Team",m.assignedTeam],["Status",m.processingStatus],
          ["Emotion",m.primaryEmotion],["Ticket",m.ticketId],["Platform",m.platform]]
          .filter(([,v])=>v).map(([l,v])=>
          <div key={String(l)}>
            <div style={{color:"#334155",fontSize:9,textTransform:"uppercase" as const,letterSpacing:1}}>{l}</div>
            <div style={{color:"#94a3b8",fontSize:11,fontWeight:500}}>{v}</div>
          </div>)}
      </div>
      {m.replyText&&<div style={{background:"#0a1020",borderRadius:6,padding:10}}>
        <div style={{color:"#334155",fontSize:9,textTransform:"uppercase" as const,letterSpacing:1,marginBottom:4}}>
          AI Reply {m.replyStatus&&<span style={{color:m.replyStatus==="APPROVED"?C.green:
            m.replyStatus==="REJECTED"?C.red:C.orange}}>· {m.replyStatus}</span>}</div>
        <div style={{color:"#e2e8f0",fontSize:12,lineHeight:1.5,
          marginBottom:m.replyStatus==="PENDING"?8:0}}>{m.replyText}</div>
        {m.replyStatus==="PENDING"&&onApprove&&<div style={{display:"flex",gap:6}}>
          <button onClick={e=>{e.stopPropagation();onApprove();}} style={{
            background:C.green+"22",color:C.green,border:"1px solid "+C.green+"44",
            borderRadius:6,padding:"4px 12px",cursor:"pointer",fontSize:11,fontFamily:"Inter"}}>
            ✓ Approve</button>
          <button onClick={e=>{e.stopPropagation();onReject?.();}} style={{
            background:C.red+"22",color:C.red,border:"1px solid "+C.red+"44",
            borderRadius:6,padding:"4px 12px",cursor:"pointer",fontSize:11,fontFamily:"Inter"}}>
            ✗ Reject</button>
        </div>}
      </div>}
    </div>}
  </div>;
}

// ── Alert Banner ──────────────────────────────────────────────────
function AlertBanner({ count }:{ count:number }) {
  if (!count) return null;
  return <div style={{background:"#1a0808",borderBottom:"1px solid #ef444444",
    padding:"8px 24px",display:"flex",alignItems:"center",gap:8}}>
    <span style={{fontSize:14}}>🚨</span>
    <span style={{color:C.red,fontWeight:700,fontSize:12}}>{count} P1 Alert{count>1?"s":""} requiring immediate attention</span>
    <span style={{color:"#475569",fontSize:11}}>→ Check the Alerts tab</span>
    <div style={{width:7,height:7,borderRadius:"50%",background:C.red,animation:"pulse 1s infinite",marginLeft:4}}/>
  </div>;
}

// ── Search + Filter chips ─────────────────────────────────────────
function FilterBar({ search, onSearch, filter, onFilter }:
  { search:string; onSearch:(v:string)=>void; filter:string; onFilter:(v:string)=>void }) {
  const filters = ["ALL","POSITIVE","NEGATIVE","NEUTRAL","PENDING","P1","P2"];
  return <div style={{padding:"10px 14px",borderBottom:"1px solid #1e293b",
    display:"flex",gap:8,alignItems:"center",flexWrap:"wrap" as const}}>
    <input value={search} onChange={e=>onSearch(e.target.value)}
      placeholder="Search mentions..."
      style={{background:"#060a10",border:"1px solid #1e293b",color:"#e2e8f0",
        padding:"6px 12px",borderRadius:7,fontSize:12,fontFamily:"Inter",
        outline:"none",flex:1,minWidth:160}}/>
    <div style={{display:"flex",gap:4,flexWrap:"wrap" as const}}>
      {filters.map(f=>(
        <button key={f} onClick={()=>onFilter(f)} style={{
          background:filter===f?C.blue+"22":"none",
          color:filter===f?C.blue:"#475569",
          border:"1px solid "+(filter===f?C.blue+"44":"#1e293b"),
          borderRadius:6,padding:"4px 10px",cursor:"pointer",
          fontSize:10,fontFamily:"Inter",fontWeight:filter===f?700:400}}>
          {f}
        </button>
      ))}
    </div>
  </div>;
}

// ── Main App ──────────────────────────────────────────────────────
export default function App() {
  const { user, logout } = useAuth();
  if (!user) return <LoginPage/>;

  const [tab,        setTab]        = useState("overview");
  const [search,     setSearch]     = useState("");
  const [filter,     setFilter]     = useState("ALL");
  const [testText,   setTestText]   = useState("");
  const [testAuthor, setTestAuthor] = useState("test_user");
  const [testFoll,   setTestFoll]   = useState("500");
  const [submitting, setSubmitting] = useState(false);
  const { toasts, add: addToast, remove: removeToast } = useToast();

  const { data: analytics }              = useAnalytics(24);
  const trendData                        = useTrend(24);
  const { data: allMentions, loading: mentionsLoading, refetch: refetchMentions } = useMentions(100);
  const liveEvents                       = useLiveEvents();
  const { data: tickets,  refetch: refetchTickets  } = useTickets();
  const { data: pending,  refetch: refetchPending  } = usePendingReplies();
  const alerts                           = useAlerts();

  // Merge live events into historical mentions (no more override)
  const prevEventIds = useRef<Set<string>>(new Set());
  useEffect(() => {
    liveEvents.forEach(ev => {
      if (!prevEventIds.current.has(ev.data.id)) {
        prevEventIds.current.add(ev.data.id);
        if (ev.type === "mention.processed") {
          const s = ev.data.sentimentLabel;
          addToast({
            type: s==="NEGATIVE"?"error":s==="POSITIVE"?"success":"info",
            title: s==="NEGATIVE"?"⚠️ Negative Mention":"✅ "+s+" Mention",
            message: "@"+(ev.data.authorUsername||"user")+": "+(ev.data.text||"").substring(0,60)+"..."
          });
          refetchMentions();
        }
      }
    });
  }, [liveEvents]);

  // Merge: put live NEW mentions at top, rest from historical
  const liveMap = new Map(liveEvents.map(e=>[e.data.id, e.data]));
  const merged: Mention[] = [
    ...liveEvents.filter(e=>e.type==="mention.new").map(e=>e.data),
    ...allMentions.filter(m => !liveMap.has(m.id) || liveMap.get(m.id)!.processingStatus !== "NEW"),
  ].filter((m,i,arr)=>arr.findIndex(x=>x.id===m.id)===i);

  // Apply search + filter
  const filteredMentions = merged.filter(m => {
    const matchSearch = !search ||
      (m.text||"").toLowerCase().includes(search.toLowerCase()) ||
      (m.authorUsername||"").toLowerCase().includes(search.toLowerCase());
    const matchFilter = filter==="ALL" ||
      (["POSITIVE","NEGATIVE","NEUTRAL"].includes(filter) && m.sentimentLabel===filter) ||
      (filter==="PENDING" && m.replyStatus==="PENDING") ||
      (["P1","P2"].includes(filter) && m.priority===filter);
    return matchSearch && matchFilter;
  });

  const negMentions = merged.filter(m=>m.sentimentLabel==="NEGATIVE");
  const posMentions = merged.filter(m=>m.sentimentLabel==="POSITIVE");
  const newIds      = new Set(liveEvents.filter(e=>e.type==="mention.new").map(e=>e.data.id));

  const TABS = [
    {id:"overview",  label:"Overview",     icon:"🏠"},
    {id:"feed",      label:"Mention Feed", icon:"📡"},
    {id:"analytics", label:"Analytics",    icon:"📊"},
    {id:"tickets",   label:"Tickets",      icon:"🎫"},
    {id:"replies",   label:"Reply Queue",  icon:"💬", badge:pending.length},
    {id:"alerts",    label:"Alerts",       icon:"🚨", badge:alerts.length},
    {id:"test",      label:"Test",         icon:"🧪"},
  ];

  const doApprove = async (id:string) => {
    await approveReply(id); refetchPending(); refetchMentions();
    addToast({type:"success",title:"Reply Approved",message:"Reply marked as approved."});
  };
  const doReject = async (id:string) => {
    await rejectReply(id); refetchPending(); refetchMentions();
    addToast({type:"warning",title:"Reply Rejected",message:"Reply has been rejected."});
  };
  const doTest = async () => {
    if (!testText.trim()) return;
    setSubmitting(true);
    await ingestMention(testText, testAuthor, parseInt(testFoll)||500);
    setTestText("");
    setSubmitting(false);
    addToast({type:"info",title:"Mention Submitted",message:"AI pipeline is processing your mention..."});
    setTimeout(refetchMentions, 5000);
  };

  const health = analytics?.brandHealthScore ?? 0;

  return <div style={{minHeight:"100vh",display:"flex",flexDirection:"column" as const}}>
    {/* Header */}
    <div style={{background:"linear-gradient(90deg,#070b12,#0a1020)",
      borderBottom:"1px solid #1e293b",padding:"0 24px",height:58,
      display:"flex",alignItems:"center",justifyContent:"space-between",
      position:"sticky",top:0,zIndex:100,boxShadow:"0 2px 24px rgba(0,0,0,.6)"}}>
      <div style={{display:"flex",alignItems:"center",gap:12}}>
        <span style={{fontSize:24}}>🛡️</span>
        <div>
          <div style={{color:"#f1f5f9",fontWeight:700,fontSize:16}}>SentinelAI</div>
          <div style={{color:"#334155",fontSize:10}}>Social Mention Analyser · @AirtelPaymentsBank</div>
        </div>
      </div>
      <div style={{display:"flex",gap:3}}>
        {TABS.map(t=>(
          <button key={t.id} onClick={()=>setTab(t.id)} style={{
            background:tab===t.id?C.blue+"1a":"none",
            border:"1px solid "+(tab===t.id?C.blue+"44":"transparent"),
            color:tab===t.id?C.blue:"#475569",padding:"6px 12px",borderRadius:7,
            cursor:"pointer",fontSize:11,fontFamily:"Inter",
            display:"flex",alignItems:"center",gap:4}}>
            {t.icon} {t.label}
            {t.badge&&t.badge>0&&<span style={{background:C.red,color:"white",
              borderRadius:10,padding:"0 5px",fontSize:9,fontWeight:700}}>{t.badge}</span>}
          </button>
        ))}
      </div>
      <div style={{display:"flex",alignItems:"center",gap:10}}>
        <div style={{display:"flex",alignItems:"center",gap:6}}>
          <Dot/><span style={{color:C.green,fontSize:11}}>LIVE</span>
          <span style={{color:"#334155",fontSize:11}}>{merged.length} mention{merged.length!==1?"s":""}</span>
        </div>
        {/* User pill */}
        <div style={{display:"flex",alignItems:"center",gap:6,background:"#0d1424",
          border:"1px solid #1e293b",borderRadius:8,padding:"4px 10px"}}>
          <div style={{width:24,height:24,borderRadius:"50%",
            background:"linear-gradient(135deg,#3b82f6,#2563eb)",
            display:"flex",alignItems:"center",justifyContent:"center",
            fontSize:11,fontWeight:700,color:"white"}}>
            {user.username[0].toUpperCase()}
          </div>
          <div>
            <div style={{color:"#f1f5f9",fontSize:11,fontWeight:600}}>{user.fullName||user.username}</div>
            <div style={{color:"#334155",fontSize:9}}>{user.role}</div>
          </div>
        </div>
        <button onClick={logout} style={{background:"none",border:"1px solid #1e293b",
          color:"#475569",padding:"5px 12px",borderRadius:7,cursor:"pointer",
          fontSize:11,fontFamily:"Inter",transition:"all .2s"}}
          onMouseEnter={e=>{e.currentTarget.style.borderColor="#ef4444";e.currentTarget.style.color="#ef4444"}}
          onMouseLeave={e=>{e.currentTarget.style.borderColor="#1e293b";e.currentTarget.style.color="#475569"}}>
          Sign Out
        </button>
      </div>
    </div>

    {/* P1 alert banner */}
    <AlertBanner count={alerts.length}/>

    {/* Stats bar */}
    <div style={{display:"grid",gridTemplateColumns:"repeat(7,1fr)",gap:1,
      background:"#1e293b",borderBottom:"1px solid #1e293b"}}>
      <StatCell label="Total Mentions" value={analytics?.totalMentions??"-"} color={C.blue} icon="📡"/>
      <StatCell label="Positive" value={analytics?.positiveMentions??"-"} color={C.green} icon="😊"/>
      <StatCell label="Negative" value={analytics?.negativeMentions??"-"} color={C.red} icon="😡"/>
      <StatCell label="Neutral" value={analytics?.neutralMentions??"-"} color={C.yellow} icon="😐"/>
      <StatCell label="Brand Health" value={health>0?health.toFixed(0)+"%":"—"} color={C.teal} icon="💚"/>
      <StatCell label="Open Tickets" value={analytics?.openTickets??"-"} color={C.orange} icon="🎫"/>
      <StatCell label="Pending Replies" value={analytics?.pendingReplies??"-"} color={C.purple} icon="💬"/>
    </div>

    {/* Content */}
    <div style={{flex:1,padding:"16px 20px",overflow:"auto"}}>

      {/* OVERVIEW */}
      {tab==="overview"&&<div>
        <div style={{display:"grid",gridTemplateColumns:"220px 1fr",gap:12,marginBottom:12}}>
          <Card>
            <CardHeader icon="💚" title="Brand Health"/>
            <BrandHealthGauge score={health}/>
            <div style={{padding:"0 16px 12px"}}>
              {[["Positive Rate", analytics?Math.round(analytics.positiveMentions/(analytics.totalMentions||1)*100)+"%":"—", C.green],
                ["Negative Rate", analytics?Math.round(analytics.negativeMentions/(analytics.totalMentions||1)*100)+"%":"—", C.red],
                ["Avg Sentiment", analytics?analytics.avgSentimentScore.toFixed(2):"—", C.blue],
                ["Critical Alerts", analytics?.criticalAlerts??0, C.red]]
                .map(([l,v,c])=>(
                <div key={String(l)} style={{display:"flex",justifyContent:"space-between",
                  padding:"5px 0",borderBottom:"1px solid #0f1929"}}>
                  <span style={{color:"#475569",fontSize:11}}>{l}</span>
                  <span style={{color:c as string,fontWeight:700,fontFamily:"JetBrains Mono",fontSize:12}}>{v}</span>
                </div>
              ))}
            </div>
          </Card>
          <Card>
            <CardHeader icon="📈" title="Sentiment Trend (24h)"/>
            <div style={{height:220,paddingTop:8}}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={trendData} margin={{top:8,right:12,bottom:8,left:0}}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b"/>
                  <XAxis dataKey="hour" tick={{fill:"#475569",fontSize:9}} tickFormatter={v=>v+"h"}/>
                  <YAxis tick={{fill:"#475569",fontSize:9}}/>
                  <Tooltip content={<TT/>}/>
                  <Legend iconType="circle" iconSize={8} wrapperStyle={{fontSize:10,color:"#64748b"}}/>
                  <Area type="monotone" dataKey="positive" stackId="1"
                    stroke={C.green} fill={C.green+"33"} name="Positive"/>
                  <Area type="monotone" dataKey="neutral" stackId="1"
                    stroke={C.yellow} fill={C.yellow+"22"} name="Neutral"/>
                  <Area type="monotone" dataKey="negative" stackId="1"
                    stroke={C.red} fill={C.red+"33"} name="Negative"/>
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </Card>
        </div>
        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12}}>
          <Card>
            <CardHeader icon="🔴" title="Recent Negative Mentions" badge={negMentions.length} badgeColor={C.red}/>
            <div style={{maxHeight:320,overflowY:"auto",padding:10}}>
              {mentionsLoading?[0,1,2].map(i=><MentionSkeleton key={i}/>):
               negMentions.length===0?<div style={{padding:32,textAlign:"center",color:"#334155"}}>
                 <div style={{fontSize:28,marginBottom:8}}>✅</div>No negative mentions right now</div>:
               negMentions.slice(0,5).map(m=><MentionCard key={m.id} m={m}
                 isNew={newIds.has(m.id)} onApprove={()=>doApprove(m.id)} onReject={()=>doReject(m.id)}/>)}
            </div>
          </Card>
          <Card>
            <CardHeader icon="🟢" title="Recent Positive Mentions" badge={posMentions.length} badgeColor={C.green}/>
            <div style={{maxHeight:320,overflowY:"auto",padding:10}}>
              {mentionsLoading?[0,1].map(i=><MentionSkeleton key={i}/>):
               posMentions.length===0?<div style={{padding:32,textAlign:"center",color:"#334155"}}>
                 <div style={{fontSize:28,marginBottom:8}}>⏳</div>No positive mentions yet</div>:
               posMentions.slice(0,5).map(m=><MentionCard key={m.id} m={m} isNew={newIds.has(m.id)}/>)}
            </div>
          </Card>
        </div>
      </div>}

      {/* MENTION FEED */}
      {tab==="feed"&&<Card>
        <CardHeader icon="📡" title="All Mentions" badge={filteredMentions.length}/>
        <FilterBar search={search} onSearch={setSearch} filter={filter} onFilter={setFilter}/>
        <div style={{maxHeight:"calc(100vh - 320px)",overflowY:"auto",padding:12}}>
          {mentionsLoading?[0,1,2,3].map(i=><MentionSkeleton key={i}/>):
           filteredMentions.length===0?<div style={{padding:40,textAlign:"center",color:"#334155"}}>
             <div style={{fontSize:32,marginBottom:12}}>🔍</div>
             <div>No mentions match your filter</div></div>:
           filteredMentions.map(m=><MentionCard key={m.id} m={m}
             isNew={newIds.has(m.id)} onApprove={()=>doApprove(m.id)} onReject={()=>doReject(m.id)}/>)}
        </div>
      </Card>}

      {/* ANALYTICS */}
      {tab==="analytics"&&<div>
        <div style={{display:"grid",gridTemplateColumns:"repeat(3,1fr)",gap:12,marginBottom:12}}>
          {[{l:"Total Mentions",v:analytics?.totalMentions??0,c:C.blue,i:"📡"},
            {l:"Brand Health",v:health>0?health.toFixed(1)+"%":"—",c:C.teal,i:"💚"},
            {l:"Critical Alerts",v:analytics?.criticalAlerts??0,c:C.red,i:"🚨"}]
            .map((s,i)=><div key={i} style={{background:"linear-gradient(135deg,#0d1424,#111827)",
              border:"1px solid #1e293b",borderRadius:10,padding:"14px 18px"}}>
              <div style={{display:"flex",justifyContent:"space-between"}}>
                <div>
                  <div style={{fontSize:26,fontWeight:700,color:s.c,fontFamily:"JetBrains Mono",marginBottom:3}}>{s.v}</div>
                  <div style={{color:"#475569",fontSize:9,textTransform:"uppercase" as const,letterSpacing:"1.2px",fontWeight:600}}>{s.l}</div>
                </div>
                <span style={{fontSize:22,opacity:.3}}>{s.i}</span>
              </div>
            </div>)}
        </div>
        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12}}>
          <Card>
            <CardHeader icon="📊" title="Sentiment Distribution"/>
            <div style={{height:240,paddingTop:8}}>
              <ResponsiveContainer><PieChart>
                <Pie data={[
                  {name:"Positive",value:analytics?.positiveMentions??0,fill:C.green},
                  {name:"Negative",value:analytics?.negativeMentions??0,fill:C.red},
                  {name:"Neutral", value:analytics?.neutralMentions??0, fill:C.yellow}
                ].filter(d=>d.value>0)} cx="50%" cy="50%" innerRadius={55} outerRadius={90}
                  dataKey="value" paddingAngle={3}>
                  {[C.green,C.red,C.yellow].map((c,i)=><Cell key={i} fill={c} strokeWidth={0}/>)}
                </Pie>
                <Tooltip content={<TT/>}/>
                <Legend iconType="circle" iconSize={8} wrapperStyle={{fontSize:10,color:"#64748b"}}/>
              </PieChart></ResponsiveContainer>
            </div>
          </Card>
          <Card>
            <CardHeader icon="📈" title="24h Trend"/>
            <div style={{height:240,paddingTop:8}}>
              <ResponsiveContainer><BarChart data={trendData} margin={{top:8,right:12,bottom:8,left:0}}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b"/>
                <XAxis dataKey="hour" tick={{fill:"#475569",fontSize:9}} tickFormatter={v=>v+"h"}/>
                <YAxis tick={{fill:"#475569",fontSize:9}} allowDecimals={false}/>
                <Tooltip content={<TT/>}/>
                <Bar dataKey="positive" name="Positive" fill={C.green} radius={[3,3,0,0]} stackId="a"/>
                <Bar dataKey="neutral"  name="Neutral"  fill={C.yellow} stackId="a"/>
                <Bar dataKey="negative" name="Negative" fill={C.red} radius={[0,0,3,3]} stackId="a"/>
              </BarChart></ResponsiveContainer>
            </div>
          </Card>
        </div>
      </div>}

      {/* TICKETS */}
      {tab==="tickets"&&<div>
        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:12,marginBottom:12}}>
          {[{l:"Open Tickets",v:analytics?.openTickets??0,c:C.orange,i:"🎫"},
            {l:"Resolved",v:analytics?.resolvedTickets??0,c:C.green,i:"✅"},
            {l:"Total",v:tickets.length,c:C.blue,i:"📋"}]
            .map((s,i)=><div key={i} style={{background:"linear-gradient(135deg,#0d1424,#111827)",
              border:"1px solid #1e293b",borderRadius:10,padding:"14px 18px"}}>
              <div style={{display:"flex",justifyContent:"space-between"}}>
                <div>
                  <div style={{fontSize:26,fontWeight:700,color:s.c,fontFamily:"JetBrains Mono",marginBottom:3}}>{s.v}</div>
                  <div style={{color:"#475569",fontSize:9,textTransform:"uppercase" as const,letterSpacing:"1.2px",fontWeight:600}}>{s.l}</div>
                </div>
                <span style={{fontSize:22,opacity:.3}}>{s.i}</span>
              </div>
            </div>)}
        </div>
        <Card>
          <CardHeader icon="🎫" title="Ticket Pipeline" badge={tickets.length}/>
          <div style={{overflowX:"auto"}}>
            <table style={{width:"100%",borderCollapse:"collapse"}}>
              <thead><tr>{["Ticket ID","Title","Status","Priority","Team","Created","Action"]
                .map(h=><th key={h} style={{padding:"8px 14px",textAlign:"left",fontSize:9,
                  textTransform:"uppercase" as const,letterSpacing:1,color:"#475569",
                  borderBottom:"1px solid #1e293b",background:"#080c14",fontWeight:600}}>{h}</th>)}</tr></thead>
              <tbody>
                {tickets.map((t:any)=><tr key={t.id} style={{borderBottom:"1px solid #0c1525"}}>
                  <td style={{padding:"9px 14px",fontFamily:"JetBrains Mono",fontSize:11,color:C.blue}}>{t.id}</td>
                  <td style={{padding:"9px 14px",fontSize:12,color:"#f1f5f9",fontWeight:600,
                    maxWidth:280,overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"}}>{t.title}</td>
                  <td style={{padding:"9px 14px"}}><Badge label={t.status}
                    color={t.status==="RESOLVED"?C.green:t.status==="OPEN"?C.orange:C.blue}/></td>
                  <td style={{padding:"9px 14px"}}><PrioBadge p={t.priority}/></td>
                  <td style={{padding:"9px 14px",fontSize:11,color:"#64748b"}}>{t.team}</td>
                  <td style={{padding:"9px 14px",fontSize:10,color:"#334155"}}>
                    {new Date(t.createdAt).toLocaleTimeString()}</td>
                  <td style={{padding:"9px 14px"}}>
                    {t.status==="OPEN"&&<button onClick={()=>{
                      resolveTicket(t.id,"Resolved via dashboard");refetchTickets();
                      addToast({type:"success",title:"Ticket Resolved",message:t.id+" marked resolved"});}}
                      style={{background:C.green+"22",color:C.green,border:"1px solid "+C.green+"44",
                        borderRadius:5,padding:"3px 10px",cursor:"pointer",fontSize:10,fontFamily:"Inter"}}>
                      Resolve</button>}
                  </td>
                </tr>)}
                {!tickets.length&&<tr><td colSpan={7} style={{padding:32,textAlign:"center",color:"#334155"}}>
                  No tickets yet</td></tr>}
              </tbody>
            </table>
          </div>
        </Card>
      </div>}

      {/* REPLY QUEUE */}
      {tab==="replies"&&<Card>
        <CardHeader icon="💬" title="Pending Approvals" badge={pending.length} badgeColor={C.purple}/>
        <div style={{padding:12,maxHeight:"calc(100vh-280px)",overflowY:"auto"}}>
          {pending.length===0?<div style={{padding:40,textAlign:"center",color:"#334155"}}>
            <div style={{fontSize:32,marginBottom:12}}>✅</div><div>Queue is empty — all reviewed</div></div>:
           pending.map(m=><MentionCard key={m.id} m={m}
             onApprove={()=>doApprove(m.id)} onReject={()=>doReject(m.id)}/>)}
        </div>
      </Card>}

      {/* ALERTS */}
      {tab==="alerts"&&<Card>
        <CardHeader icon="🚨" title="Critical Alerts" badge={alerts.length} badgeColor={C.red}/>
        <div style={{padding:12}}>
          {alerts.map(m=><div key={m.id} style={{background:"#1a0808",
            border:"1px solid #ef444444",borderLeft:"3px solid #ef4444",
            borderRadius:8,padding:12,marginBottom:8,animation:"slideIn .3s ease"}}>
            <div style={{display:"flex",gap:8,alignItems:"center",marginBottom:6}}>
              <span style={{fontSize:16}}>🚨</span>
              <span style={{color:C.red,fontWeight:700,fontSize:12}}>{m.priority} ALERT</span>
              <SentBadge s={m.sentimentLabel}/>
              {m.urgency&&<Badge label={m.urgency} color={C.red}/>}
              <span style={{color:"#334155",fontSize:10,marginLeft:"auto"}}>{timeAgo(m.postedAt)}</span>
            </div>
            <div style={{color:"#f1f5f9",fontSize:12,marginBottom:6}}>{m.text}</div>
            <div style={{color:"#64748b",fontSize:10}}>
              @{m.authorUsername} · {fmtFollowers(m.authorFollowers)} followers
              {m.assignedTeam&&<> · <span style={{color:C.orange}}>{m.assignedTeam}</span></>}
            </div>
          </div>)}
          {!alerts.length&&<div style={{padding:40,textAlign:"center",color:"#334155"}}>
            <div style={{fontSize:32,marginBottom:12}}>✅</div><div>No critical alerts</div></div>}
        </div>
      </Card>}

      {/* TEST */}
      {tab==="test"&&<div style={{maxWidth:700}}>
        <Card>
          <CardHeader icon="🧪" title="Test Mention Injection"/>
          <div style={{padding:20}}>
            <div style={{marginBottom:14}}>
              <label style={{display:"block",color:"#64748b",fontSize:11,marginBottom:6}}>MENTION TEXT</label>
              <textarea value={testText} onChange={e=>setTestText(e.target.value)}
                placeholder="@AirtelPaymentsBank my UPI payment failed! Please help!"
                style={{width:"100%",background:"#060a10",border:"1px solid #1e293b",color:"#e2e8f0",
                  padding:"10px 12px",borderRadius:8,fontSize:12,fontFamily:"Inter",
                  resize:"vertical",minHeight:80,outline:"none"}}/>
            </div>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12,marginBottom:16}}>
              <div>
                <label style={{display:"block",color:"#64748b",fontSize:11,marginBottom:6}}>AUTHOR</label>
                <input value={testAuthor} onChange={e=>setTestAuthor(e.target.value)}
                  style={{width:"100%",background:"#060a10",border:"1px solid #1e293b",
                    color:"#e2e8f0",padding:"8px 12px",borderRadius:8,fontSize:12,
                    fontFamily:"Inter",outline:"none"}}/>
              </div>
              <div>
                <label style={{display:"block",color:"#64748b",fontSize:11,marginBottom:6}}>FOLLOWERS</label>
                <input value={testFoll} onChange={e=>setTestFoll(e.target.value)} type="number"
                  style={{width:"100%",background:"#060a10",border:"1px solid #1e293b",
                    color:"#e2e8f0",padding:"8px 12px",borderRadius:8,fontSize:12,
                    fontFamily:"Inter",outline:"none"}}/>
              </div>
            </div>
            <button onClick={doTest} disabled={!testText.trim()||submitting} style={{
              width:"100%",background:submitting||!testText.trim()?"#1e293b":"linear-gradient(135deg,#3b82f6,#2563eb)",
              color:submitting||!testText.trim()?"#475569":"white",border:"none",borderRadius:8,
              padding:"11px",fontSize:13,fontWeight:600,fontFamily:"Inter",
              cursor:submitting||!testText.trim()?"not-allowed":"pointer",letterSpacing:".3px"}}>
              {submitting?"⏳ Processing...":"🚀 Submit for AI Analysis"}
            </button>
            <div style={{marginTop:14,padding:12,background:"#0a0e14",borderRadius:8,border:"1px solid #1e293b"}}>
              <div style={{color:"#334155",fontSize:10,marginBottom:6}}>Quick examples:</div>
              {["@AirtelPaymentsBank scam! Rs10,000 deducted but payment failed. Going to consumer forum! @RBI_Informs",
                "@AirtelPaymentsBank the new FD feature is amazing! Great interest rates 🎉",
                "@AirtelPaymentsBank how do I check my KYC status?"]
                .map((ex,i)=><div key={i} onClick={()=>setTestText(ex)}
                  style={{color:"#94a3b8",fontSize:11,padding:"6px 0",borderBottom:"1px solid #1e293b",
                    cursor:"pointer",lineHeight:1.4}}>{ex}</div>)}
            </div>
          </div>
        </Card>
      </div>}

    </div>

    {/* Footer */}
    <div style={{padding:"8px 24px",borderTop:"1px solid #1e293b",background:"#070b12",
      display:"flex",justifyContent:"space-between",color:"#1e3a5f",fontSize:10}}>
      <span>SentinelAI v1.0.0 · SquadOS v3.4.0 · 7 AI Agents</span>
      <span>React 18 · Recharts · WebSocket · Spring Boot 3.3</span>
      <span>Mentions: {merged.length} · Pending: {pending.length} · Tickets: {tickets.length}</span>
    </div>

    {/* Toast notifications */}
    <Toast toasts={toasts} onRemove={removeToast}/>
  </div>;
}