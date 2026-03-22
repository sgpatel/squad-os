const { useState, useEffect, useCallback } = React;
const { AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis,
  CartesianGrid, Tooltip, Legend, ResponsiveContainer, LineChart, Line } = Recharts;

const API = "/api";
const C = {
  green:"#22c55e", blue:"#3b82f6", purple:"#a855f7",
  orange:"#f97316", red:"#ef4444", teal:"#14b8a6",
  pink:"#ec4899", yellow:"#eab308"
};
const ROLE_C = {
  STRATEGIST:"#22c55e", ANALYST:"#3b82f6", RESEARCHER:"#a855f7",
  CRITIC:"#f97316", SUPPORT:"#14b8a6", WILDCARD:"#475569"
};
const PIE_C = ["#22c55e","#3b82f6","#a855f7","#f97316","#14b8a6","#ec4899"];

function Badge({label, color}){
  color = color || C.blue;
  return React.createElement("span",{
    style:{background:color+"1a",color:color,border:"1px solid "+color+"33",
      borderRadius:20,padding:"2px 9px",fontSize:9,fontWeight:700,
      whiteSpace:"nowrap",display:"inline-block"}},
    label);
}

function RoleBadge({role}){
  return React.createElement(Badge,{label:role,color:ROLE_C[role]||"#475569"});
}

function StatusBadge({status}){
  var m={OK:C.green,GRANTED:C.green,APPROVED:C.green,
    ERROR:C.red,DENIED:C.red,REJECTED:C.red,PENDING:C.orange};
  return React.createElement(Badge,{label:status||"—",color:m[status]||"#475569"});
}

function LiveDot({color}){
  color = color || C.green;
  return React.createElement("div",{
    style:{width:7,height:7,borderRadius:"50%",background:color,
      animation:"pulse 2s infinite",flexShrink:0}});
}

function Empty({msg}){
  return React.createElement("div",{
    style:{padding:32,textAlign:"center",color:"#1e3a5f"}},
    React.createElement("div",{style:{fontSize:24,marginBottom:8}},"🔍"),
    React.createElement("div",{style:{fontSize:12}},msg||"No data yet"));
}

var TT = function(props){
  if(!props.active||!props.payload||!props.payload.length) return null;
  return React.createElement("div",{
    style:{background:"#0d1424",border:"1px solid #1e293b",
      borderRadius:8,padding:"10px 14px"}},
    props.label&&React.createElement("div",{style:{color:"#94a3b8",fontSize:10,marginBottom:6}},props.label),
    props.payload.map(function(p,i){
      return React.createElement("div",{key:i,
        style:{color:p.color,fontSize:12,fontWeight:600}},
        p.name+": "+(typeof p.value==="number"?p.value.toLocaleString():p.value));
    })
  );
};

function Card(props){
  return React.createElement("div",{className:"card"},props.children);
}

function CardHeader(props){
  return React.createElement("div",{className:"card-header"},
    React.createElement("div",{className:"card-title"},
      props.icon&&React.createElement("span",{style:{fontSize:14}},props.icon),
      props.title),
    props.badge!==undefined&&React.createElement(Badge,{label:String(props.badge),color:props.badgeColor||C.blue})
  );
}
function DataTable({headers, rows, emptyMsg, maxH}){
  return React.createElement("div",{
    className:"scroll-y",style:{maxHeight:maxH||280}},
    React.createElement("table",{className:"tbl"},
      React.createElement("thead",null,
        React.createElement("tr",null,
          headers.map(function(h,i){return React.createElement("th",{key:i},h);}))),
      React.createElement("tbody",null,
        rows.length===0
          ?React.createElement("tr",null,React.createElement("td",{colSpan:headers.length},React.createElement(Empty,{msg:emptyMsg})))
          :rows.map(function(row,i){
            return React.createElement("tr",{key:i},
              row.map(function(cell,j){return React.createElement("td",{key:j},cell);})
            );
          })
      )
    )
  );
}

function SearchBox({value,onChange,ph}){
  return React.createElement("div",{style:{padding:"10px 14px",borderBottom:"1px solid #1e293b"}},
    React.createElement("input",{
      className:"search-box",value:value,
      onChange:function(e){onChange(e.target.value);},
      placeholder:ph}));
}

function StatCard({label, value, color, sub, icon}){
  return React.createElement("div",{
    className:"stat-card",
    style:{borderColor:"#1e293b"},
    onMouseEnter:function(e){e.currentTarget.style.borderColor=color+"55";e.currentTarget.style.transform="translateY(-1px)";},
    onMouseLeave:function(e){e.currentTarget.style.borderColor="#1e293b";e.currentTarget.style.transform="translateY(0)";}
  },
    React.createElement("div",{style:{display:"flex",justifyContent:"space-between",alignItems:"flex-start"}},
      React.createElement("div",null,
        React.createElement("div",{className:"stat-val",style:{color:color}},value||"—"),
        React.createElement("div",{className:"stat-lbl"},label),
        React.createElement("div",{className:"stat-sub"},sub)
      ),
      React.createElement("span",{style:{fontSize:20,opacity:.3}},icon)
    )
  );
}
function VoteItem({v}){
  var tot=v.approve+v.reject+v.abstain||1;
  var pct=Math.round(v.approve/tot*100);
  var col=v.outcome==="APPROVED"?C.green:v.outcome==="REJECTED"?C.red:C.orange;
  return React.createElement("div",{className:"vote-item"},
    React.createElement("div",{style:{display:"flex",justifyContent:"space-between",marginBottom:6}},
      React.createElement("span",{style:{color:"#f1f5f9",fontWeight:600,fontSize:12}},v.topic),
      React.createElement(Badge,{label:v.outcome,color:col})
    ),
    React.createElement("div",{className:"vote-bar-bg"},
      React.createElement("div",{className:"vote-bar-fill",style:{width:pct+"%",background:col}})
    ),
    React.createElement("div",{style:{display:"flex",gap:12,fontSize:10}},
      React.createElement("span",{style:{color:C.green}},"▲ "+v.approve),
      React.createElement("span",{style:{color:C.red}},"▼ "+v.reject),
      React.createElement("span",{style:{color:"#334155"}},"○ "+v.abstain)
    )
  );
}

var ACT_COLORS = {AGENT:C.green,VOTE:C.blue,APPROVAL:C.orange,SYSTEM:C.purple};

function ActivityRow({a}){
  var col=ACT_COLORS[a.type]||"#475569";
  var t=new Date(a.time).toLocaleTimeString();
  return React.createElement("div",{className:"activity-row"},
    React.createElement(LiveDot,{color:col}),
    React.createElement("span",{
      style:{fontSize:10,fontWeight:700,color:col,minWidth:65}},a.type),
    React.createElement("span",{
      style:{fontSize:12,color:"#94a3b8",flex:1,overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"}},
      React.createElement("span",{style:{color:"#e2e8f0",fontWeight:500}},a.agent),
      ": "+a.message),
    React.createElement("span",{style:{fontSize:10,color:"#334155",flexShrink:0}},t)
  );
}

function MetricRow({label,value,pct,color}){
  return React.createElement("div",{className:"metric-row"},
    React.createElement("span",{style:{color:"#475569",fontSize:11,flex:1}},label),
    React.createElement("div",{className:"metric-bar-track"},
      React.createElement("div",{className:"metric-bar-fill",style:{width:(pct||0)+"%",background:color||C.blue}})
    ),
    React.createElement("span",{
      style:{color:color||C.blue,fontWeight:700,fontFamily:"JetBrains Mono",fontSize:13}},
      value)
  );
}
function OverviewTab({data}){
  var ag=data.agents||[], sp=data.spans||[], vt=data.votes||[], ac=data.activity||[];
  var appr=vt.filter(function(v){return v.outcome==="APPROVED";}).length;
  var rejt=vt.filter(function(v){return v.outcome==="REJECTED";}).length;
  var voteD=[
    {name:"Approved",value:appr,fill:C.green},
    {name:"Rejected",value:rejt,fill:C.red},
    {name:"Tie",value:vt.length-appr-rejt,fill:C.orange}
  ].filter(function(d){return d.value>0;});
  var latD=sp.slice(0,8).map(function(s){return{name:s.spanName.substring(0,10),ms:s.durationMs};});

  return React.createElement("div",null,
    React.createElement("div",{className:"g21"},
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"⚡",title:"Live Activity Feed",badge:ac.length,badgeColor:C.green}),
        React.createElement("div",{className:"scroll-y",style:{maxHeight:290}},
          ac.length===0?React.createElement(Empty,{msg:"No activity yet"})
          :ac.slice(0,20).map(function(a,i){return React.createElement(ActivityRow,{key:i,a:a});})
        )
      ),
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"⚖️",title:"Vote Summary",badge:vt.length+" votes",badgeColor:C.blue}),
        React.createElement("div",{style:{height:200,padding:12}},
          vt.length===0?React.createElement(Empty,{msg:"No votes yet"}):
          React.createElement(ResponsiveContainer,{width:"100%",height:"100%"},
            React.createElement(PieChart,null,
              React.createElement(Pie,{data:voteD,cx:"50%",cy:"50%",innerRadius:45,outerRadius:75,paddingAngle:3,dataKey:"value"},
                voteD.map(function(d,i){return React.createElement(Cell,{key:i,fill:d.fill,strokeWidth:0});})
              ),
              React.createElement(Tooltip,{content:React.createElement(TT)}),
              React.createElement(Legend,{iconType:"circle",iconSize:8,wrapperStyle:{fontSize:10,color:"#64748b"}})
            )
          )
        )
      )
    ),
    React.createElement("div",{className:"g3"},
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"🤖",title:"Squad Roster",badge:ag.length,badgeColor:C.green}),
        React.createElement("div",{style:{display:"grid",gridTemplateColumns:"repeat(auto-fill,minmax(140px,1fr))",gap:8,padding:12}},
          ag.map(function(a,i){
            return React.createElement("div",{key:i,className:"agent-card",
              onMouseEnter:function(e){e.currentTarget.style.borderColor=(ROLE_C[a.role]||C.blue)+"66";},
              onMouseLeave:function(e){e.currentTarget.style.borderColor="#1e293b";}},
              React.createElement("div",{style:{display:"flex",alignItems:"center",gap:4,marginBottom:5}},
                React.createElement(LiveDot),
                React.createElement("span",{style:{color:"#475569",fontSize:9}},"ONLINE")
              ),
              React.createElement("div",{style:{color:"#f1f5f9",fontWeight:700,fontSize:12,marginBottom:3}},a.name),
              React.createElement("div",{style:{color:ROLE_C[a.role],fontSize:10,marginBottom:7}},a.role),
              React.createElement("div",{style:{display:"flex",justifyContent:"space-between"}},
                React.createElement("div",{style:{textAlign:"center"}},
                  React.createElement("div",{style:{color:"#f1f5f9",fontWeight:700,fontSize:13}},a.temp),
                  React.createElement("div",{style:{color:"#334155",fontSize:8,textTransform:"uppercase"}},"temp")
                ),
                React.createElement("div",{style:{textAlign:"center"}},
                  React.createElement("div",{style:{color:"#f1f5f9",fontWeight:700,fontSize:13}},a.maxTokens),
                  React.createElement("div",{style:{color:"#334155",fontSize:8,textTransform:"uppercase"}},"tokens")
                )
              )
            );
          })
        )
      ),
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"⏱️",title:"Span Latency",badgeColor:C.teal}),
        React.createElement("div",{style:{height:200,paddingTop:8}},
          latD.length===0?React.createElement(Empty,{msg:"No spans yet"}):
          React.createElement(ResponsiveContainer,{width:"100%",height:"100%"},
            React.createElement(BarChart,{data:latD,margin:{top:5,right:12,bottom:18,left:0}},
              React.createElement(CartesianGrid,{strokeDasharray:"3 3",stroke:"#1e293b"}),
              React.createElement(XAxis,{dataKey:"name",tick:{fill:"#475569",fontSize:9}}),
              React.createElement(YAxis,{tick:{fill:"#475569",fontSize:9}}),
              React.createElement(Tooltip,{content:React.createElement(TT)}),
              React.createElement(Bar,{dataKey:"ms",fill:C.teal,radius:[4,4,0,0]})
            )
          )
        )
      ),
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"🔍",title:"Recent Traces",badge:sp.length,badgeColor:C.blue}),
        React.createElement(DataTable,{
          headers:["Span","Agent","ms","Status"],emptyMsg:"No spans yet",maxH:200,
          rows:sp.slice(0,8).map(function(s){return[
            React.createElement("span",{style:{color:"#f1f5f9",fontFamily:"JetBrains Mono",fontSize:11}},s.spanName),
            React.createElement("span",{style:{color:ROLE_C[s.agentRole],fontSize:11}},s.agentName),
            React.createElement("span",{style:{color:C.teal,fontFamily:"JetBrains Mono"}},s.durationMs),
            React.createElement(StatusBadge,{status:s.status})
          ];})
        })
      )
    ),
    React.createElement("div",{className:"g2"},
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"🗳️",title:"Vote History",badge:vt.length,badgeColor:C.blue}),
        React.createElement("div",{className:"scroll-y",style:{maxHeight:240}},
          vt.length===0?React.createElement(Empty,{msg:"No votes yet"})
          :vt.slice(0,6).map(function(v,i){return React.createElement(VoteItem,{key:i,v:v});})
        )
      ),
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"📊",title:"Token Usage",badgeColor:C.purple}),
        React.createElement("div",{style:{padding:"12px 14px 0"}},
          React.createElement(MetricRow,{label:"Total Spans",value:sp.length,pct:sp.length?Math.min(sp.length*10,100):0,color:C.blue}),
          React.createElement(MetricRow,{label:"OK spans",value:sp.filter(function(s){return s.status==="OK";}).length,pct:sp.length?sp.filter(function(s){return s.status==="OK";}).length/sp.length*100:0,color:C.green}),
          React.createElement(MetricRow,{label:"Error spans",value:sp.filter(function(s){return s.status==="ERROR";}).length,pct:sp.length?sp.filter(function(s){return s.status==="ERROR";}).length/sp.length*100:0,color:C.red})
        )
      )
    )
  );
}
function AgentsTab({data}){
  var ag=data.agents||[], ac=data.activity||[];
  var rc={};ag.forEach(function(a){rc[a.role]=(rc[a.role]||0)+1;});
  var roleD=Object.entries(rc).map(function(e){return{name:e[0],value:e[1]};});
  return React.createElement("div",null,
    React.createElement("div",{className:"mb"},
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"🤖",title:"Registered Agents",badge:ag.length,badgeColor:C.green}),
        React.createElement(DataTable,{maxH:300,
          headers:["Name","Role","Status","Temperature","Max Tokens"],
          emptyMsg:"No agents registered",
          rows:ag.map(function(a){return[
            React.createElement("span",{style:{color:"#f1f5f9",fontWeight:600}},a.name),
            React.createElement(RoleBadge,{role:a.role}),
            React.createElement("div",{style:{display:"flex",alignItems:"center",gap:5}},React.createElement(LiveDot),React.createElement("span",{style:{color:C.green,fontSize:11}},"ONLINE")),
            React.createElement("span",{style:{fontFamily:"JetBrains Mono",color:"#94a3b8"}},a.temp),
            React.createElement("span",{style:{fontFamily:"JetBrains Mono",color:"#94a3b8"}},a.maxTokens)
          ];})
        })
      )
    ),
    React.createElement("div",{className:"g2"},
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"📈",title:"Role Distribution"}),
        React.createElement("div",{style:{height:200,paddingTop:8}},
          roleD.length===0?React.createElement(Empty,{msg:"No agents"}):
          React.createElement(ResponsiveContainer,{width:"100%",height:"100%"},
            React.createElement(PieChart,null,
              React.createElement(Pie,{data:roleD,cx:"50%",cy:"50%",outerRadius:75,dataKey:"value",paddingAngle:3},
                roleD.map(function(d,i){return React.createElement(Cell,{key:i,fill:PIE_C[i%PIE_C.length],strokeWidth:0});})
              ),
              React.createElement(Tooltip,{content:React.createElement(TT)}),
              React.createElement(Legend,{iconType:"circle",iconSize:8,wrapperStyle:{fontSize:10,color:"#64748b"}})
            )
          )
        )
      ),
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"⚡",title:"Activity Timeline"}),
        React.createElement("div",{style:{maxHeight:220,overflowY:"auto",padding:"6px 0"}},
          ac.length===0?React.createElement(Empty,{msg:"No activity"}):
          ac.slice(0,15).map(function(a,i){
            return React.createElement("div",{key:i,className:"tl-item"},
              React.createElement("div",{style:{width:10,height:10,borderRadius:"50%",
                background:ACT_COLORS[a.type]||"#334155",flexShrink:0,marginTop:3,
                border:"2px solid #0a0e17",zIndex:1}}),
              React.createElement("div",null,
                React.createElement("div",{style:{fontSize:12,color:"#94a3b8"}},
                  React.createElement("span",{style:{color:"#e2e8f0",fontWeight:500}},a.agent),
                  ": "+a.message),
                React.createElement("div",{style:{fontSize:10,color:"#334155"}},new Date(a.time).toLocaleTimeString())
              )
            );
          })
        )
      )
    )
  );
}

function TracesTab({data}){
  var sp=data.spans||[];
  var srch=useState(""); var search=srch[0],setSearch=srch[1];
  var filtered=sp.filter(function(s){
    return(s.spanName||"").toLowerCase().indexOf(search.toLowerCase())>-1||
      (s.agentName||"").toLowerCase().indexOf(search.toLowerCase())>-1||
      (s.status||"").toLowerCase().indexOf(search.toLowerCase())>-1;
  });
  var bk=[0,0,0,0,0];
  sp.forEach(function(s){
    if(s.durationMs<100)bk[0]++;
    else if(s.durationMs<500)bk[1]++;
    else if(s.durationMs<1000)bk[2]++;
    else if(s.durationMs<5000)bk[3]++;
    else bk[4]++;
  });
  var durD=["<100ms","100-500ms","500ms-1s","1-5s",">5s"].map(function(n,i){return{name:n,count:bk[i]};});
  var ok=sp.filter(function(s){return s.status==="OK";}).length;
  var statD=[{name:"OK",value:ok,fill:C.green},{name:"ERROR",value:sp.length-ok,fill:C.red}].filter(function(d){return d.value>0;});
  return React.createElement("div",null,
    React.createElement("div",{className:"mb"},
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"🔍",title:"All Trace Spans",badge:filtered.length,badgeColor:C.blue}),
        React.createElement(SearchBox,{value:search,onChange:setSearch,ph:"Search spans by name, agent, status..."}),
        React.createElement(DataTable,{maxH:360,
          headers:["Span","Agent","Role","Duration","Tokens","Status","Error","Time"],
          emptyMsg:"No spans yet — run some agent calls",
          rows:filtered.slice(0,50).map(function(s){return[
            React.createElement("span",{style:{color:"#f1f5f9",fontFamily:"JetBrains Mono",fontSize:11}},s.spanName),
            React.createElement("span",null,s.agentName),
            React.createElement(RoleBadge,{role:s.agentRole}),
            React.createElement("span",{style:{color:C.teal,fontFamily:"JetBrains Mono"}},s.durationMs+"ms"),
            React.createElement("span",{style:{color:"#94a3b8",fontFamily:"JetBrains Mono"}},s.tokens||0),
            React.createElement(StatusBadge,{status:s.status}),
            React.createElement("span",{style:{color:C.red,fontSize:11}},s.error||"—"),
            React.createElement("span",{style:{color:"#334155",fontSize:11}},new Date(s.timestamp).toLocaleTimeString())
          ];})
        })
      )
    ),
    React.createElement("div",{className:"g2"},
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"📊",title:"Duration Distribution"}),
        React.createElement("div",{style:{height:200,paddingTop:8}},
          React.createElement(ResponsiveContainer,{width:"100%",height:"100%"},
            React.createElement(BarChart,{data:durD,margin:{top:5,right:12,bottom:18,left:0}},
              React.createElement(CartesianGrid,{strokeDasharray:"3 3",stroke:"#1e293b"}),
              React.createElement(XAxis,{dataKey:"name",tick:{fill:"#475569",fontSize:9}}),
              React.createElement(YAxis,{tick:{fill:"#475569",fontSize:9},allowDecimals:false}),
              React.createElement(Tooltip,{content:React.createElement(TT)}),
              React.createElement(Bar,{dataKey:"count",fill:C.blue,radius:[4,4,0,0]})
            )
          )
        )
      ),
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"🎯",title:"Status Breakdown"}),
        React.createElement("div",{style:{height:200,paddingTop:8}},
          statD.length===0?React.createElement(Empty,{msg:"No spans"}):
          React.createElement(ResponsiveContainer,{width:"100%",height:"100%"},
            React.createElement(PieChart,null,
              React.createElement(Pie,{data:statD,cx:"50%",cy:"50%",innerRadius:48,outerRadius:72,paddingAngle:4,dataKey:"value"},
                statD.map(function(d,i){return React.createElement(Cell,{key:i,fill:d.fill,strokeWidth:0});})
              ),
              React.createElement(Tooltip,{content:React.createElement(TT)}),
              React.createElement(Legend,{iconType:"circle",iconSize:8,wrapperStyle:{fontSize:10,color:"#64748b"}})
            )
          )
        )
      )
    )
  );
}
function VotesTab({data}){
  var vt=data.votes||[];
  var appr=vt.filter(function(v){return v.outcome==="APPROVED";}).length;
  var rejt=vt.filter(function(v){return v.outcome==="REJECTED";}).length;
  var barD=vt.slice(0,8).map(function(v){return{name:v.topic.substring(0,16),A:v.approve,R:v.reject,Ab:v.abstain};});
  return React.createElement("div",null,
    React.createElement("div",{className:"g12"},
      React.createElement("div",null,
        React.createElement(Card,null,
          React.createElement(CardHeader,{icon:"📊",title:"Outcomes"}),
          React.createElement("div",{style:{height:220,paddingTop:8}},
            vt.length===0?React.createElement(Empty,{msg:"No votes yet"}):
            React.createElement(ResponsiveContainer,{width:"100%",height:"100%"},
              React.createElement(PieChart,null,
                React.createElement(Pie,{
                  data:[{name:"Approved",value:appr,fill:C.green},{name:"Rejected",value:rejt,fill:C.red},{name:"Tie",value:vt.length-appr-rejt,fill:C.orange}].filter(function(d){return d.value>0;}),
                  cx:"50%",cy:"50%",innerRadius:52,outerRadius:80,dataKey:"value",paddingAngle:3},
                  [C.green,C.red,C.orange].map(function(c,i){return React.createElement(Cell,{key:i,fill:c,strokeWidth:0});})
                ),
                React.createElement(Tooltip,{content:React.createElement(TT)}),
                React.createElement(Legend,{iconType:"circle",iconSize:8,wrapperStyle:{fontSize:10,color:"#64748b"}})
              )
            )
          ),
          React.createElement("div",null,
            React.createElement(MetricRow,{label:"Total",value:vt.length,pct:100,color:"#94a3b8"}),
            React.createElement(MetricRow,{label:"Approved",value:appr,pct:vt.length?appr/vt.length*100:0,color:C.green}),
            React.createElement(MetricRow,{label:"Rejected",value:rejt,pct:vt.length?rejt/vt.length*100:0,color:C.red})
          )
        )
      ),
      React.createElement("div",null,
        React.createElement("div",{className:"mb"},
          React.createElement(Card,null,
            React.createElement(CardHeader,{icon:"📈",title:"Vote Breakdown"}),
            React.createElement("div",{style:{height:180,paddingTop:8}},
              barD.length===0?React.createElement(Empty,{msg:"No votes"}):
              React.createElement(ResponsiveContainer,{width:"100%",height:"100%"},
                React.createElement(BarChart,{data:barD,margin:{top:5,right:12,bottom:18,left:0}},
                  React.createElement(CartesianGrid,{strokeDasharray:"3 3",stroke:"#1e293b"}),
                  React.createElement(XAxis,{dataKey:"name",tick:{fill:"#475569",fontSize:9}}),
                  React.createElement(YAxis,{tick:{fill:"#475569",fontSize:9},allowDecimals:false}),
                  React.createElement(Tooltip,{content:React.createElement(TT)}),
                  React.createElement(Bar,{dataKey:"A",name:"Approve",fill:C.green,radius:[3,3,0,0],stackId:"a"}),
                  React.createElement(Bar,{dataKey:"R",name:"Reject",fill:C.red,radius:[0,0,0,0],stackId:"a"}),
                  React.createElement(Bar,{dataKey:"Ab",name:"Abstain",fill:C.orange,radius:[0,0,3,3],stackId:"a"})
                )
              )
            )
          )
        ),
        React.createElement(Card,null,
          React.createElement(CardHeader,{icon:"🗳️",title:"All Votes",badge:vt.length,badgeColor:C.blue}),
          React.createElement("div",{className:"scroll-y",style:{maxHeight:240}},
            vt.length===0?React.createElement(Empty,{msg:"No votes yet"})
            :vt.map(function(v,i){return React.createElement(VoteItem,{key:i,v:v});})
          )
        )
      )
    )
  );
}

function ApprovalsTab({data,onApprove,onReject}){
  var ap=data.approvals||[];
  var pend=ap.filter(function(a){return a.status==="PENDING";}).length;
  var appr=ap.filter(function(a){return a.status==="APPROVED";}).length;
  var rejt=ap.filter(function(a){return a.status==="REJECTED";}).length;
  return React.createElement("div",null,
    React.createElement("div",{className:"g3 mb"},
      React.createElement(StatCard,{label:"Pending",value:pend,color:C.orange,icon:"⏳",sub:"awaiting review"}),
      React.createElement(StatCard,{label:"Approved",value:appr,color:C.green,icon:"✅",sub:"decisions made"}),
      React.createElement(StatCard,{label:"Rejected",value:rejt,color:C.red,icon:"❌",sub:"blocked"})
    ),
    React.createElement(Card,null,
      React.createElement(CardHeader,{icon:"✅",title:"Approval Queue",badge:ap.length,badgeColor:C.orange}),
      React.createElement(DataTable,{maxH:480,
        headers:["Method","Reason","Status","Priority","Escalate To","Created","Action"],
        emptyMsg:"No approvals",
        rows:ap.map(function(a){return[
          React.createElement("span",{style:{color:"#f1f5f9",fontWeight:600,fontFamily:"JetBrains Mono",fontSize:11}},(a.method||"-").split(".").pop()),
          React.createElement("span",{style:{color:"#475569",fontSize:11}},a.reason||"-"),
          React.createElement(StatusBadge,{status:a.status}),
          React.createElement(Badge,{label:a.priority||"NORMAL",color:C.orange}),
          React.createElement("span",{style:{color:"#475569"}},a.escalateTo||"-"),
          React.createElement("span",{style:{color:"#334155",fontSize:11}},a.createdAt?new Date(a.createdAt).toLocaleTimeString():"-"),
          a.status==="PENDING"
            ?React.createElement("div",{style:{display:"flex",gap:5}},
              React.createElement("button",{className:"approve-btn",onClick:function(){onApprove(a.id);}},"✓ Approve"),
              React.createElement("button",{className:"reject-btn",onClick:function(){onReject(a.id);}},"✗ Reject")
            ):React.createElement("span",{style:{color:"#334155"}},"—")
        ];})
      })
    )
  );
}

function SecurityTab({data}){
  var au=data.audit||[];
  var srch=useState(""); var search=srch[0],setSearch=srch[1];
  var filtered=au.filter(function(e){
    return(e.subject||"").toLowerCase().indexOf(search.toLowerCase())>-1||
      (e.method||"").toLowerCase().indexOf(search.toLowerCase())>-1||
      (e.decision||"").toLowerCase().indexOf(search.toLowerCase())>-1;
  });
  var g=au.filter(function(e){return e.decision==="GRANTED";}).length;
  var d=au.length-g;
  return React.createElement("div",{className:"g31"},
    React.createElement(Card,null,
      React.createElement(CardHeader,{icon:"🔐",title:"Audit Log",badge:filtered.length,badgeColor:C.blue}),
      React.createElement(SearchBox,{value:search,onChange:setSearch,ph:"Search by subject, method, decision..."}),
      React.createElement(DataTable,{maxH:420,
        headers:["Subject","Method","Decision","Roles","Duration","Time"],
        emptyMsg:"No audit entries yet",
        rows:filtered.slice(0,50).map(function(e){return[
          React.createElement("span",{style:{color:"#f1f5f9",fontWeight:600}},e.subject||"-"),
          React.createElement("span",{style:{fontFamily:"JetBrains Mono",fontSize:11}},(e.method||"-").split(".").pop()),
          React.createElement(StatusBadge,{status:e.decision}),
          React.createElement("span",{style:{color:"#475569",fontSize:10}},((e.roles||[]).join(", "))||"-"),
          React.createElement("span",{style:{color:C.teal,fontFamily:"JetBrains Mono"}},(e.duration||0)+"ms"),
          React.createElement("span",{style:{color:"#334155",fontSize:11}},new Date(e.time).toLocaleTimeString())
        ];})
      })
    ),
    React.createElement("div",null,
      React.createElement("div",{className:"mb"},
        React.createElement(Card,null,
          React.createElement(CardHeader,{icon:"📊",title:"Access Decisions"}),
          React.createElement("div",{style:{height:200,paddingTop:8}},
            au.length===0?React.createElement(Empty,{msg:"No audit entries"}):
            React.createElement(ResponsiveContainer,{width:"100%",height:"100%"},
              React.createElement(PieChart,null,
                React.createElement(Pie,{
                  data:[{name:"Granted",value:g,fill:C.green},{name:"Denied",value:d,fill:C.red}].filter(function(x){return x.value>0;}),
                  cx:"50%",cy:"50%",innerRadius:48,outerRadius:72,dataKey:"value",paddingAngle:4},
                  [C.green,C.red].map(function(c,i){return React.createElement(Cell,{key:i,fill:c,strokeWidth:0});})
                ),
                React.createElement(Tooltip,{content:React.createElement(TT)}),
                React.createElement(Legend,{iconType:"circle",iconSize:8,wrapperStyle:{fontSize:10,color:"#64748b"}})
              )
            )
          )
        )
      ),
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"📈",title:"Access Stats"}),
        React.createElement(MetricRow,{label:"Total calls",value:au.length,pct:100,color:"#94a3b8"}),
        React.createElement(MetricRow,{label:"Granted",value:g,pct:au.length?g/au.length*100:0,color:C.green}),
        React.createElement(MetricRow,{label:"Denied",value:d,pct:au.length?d/au.length*100:0,color:C.red}),
        React.createElement(MetricRow,{label:"Grant rate",value:au.length?(g/au.length*100).toFixed(0)+"%":"—",pct:au.length?g/au.length*100:0,color:C.teal})
      )
    )
  );
}

function ImproveTab({data}){
  var fb=data.feedback||{};
  var g=fb.good||0,b=fb.bad||0,tot=fb.total||0;
  var fbD=[{name:"Good",value:g,fill:C.green},{name:"Bad",value:b,fill:C.red}].filter(function(d){return d.value>0;});
  return React.createElement("div",{className:"g31"},
    React.createElement(Card,null,
      React.createElement(CardHeader,{icon:"🧠",title:"Feedback Store",
        badge:React.createElement("div",{style:{display:"flex",gap:6}},
          React.createElement(Badge,{label:g+" good",color:C.green}),
          React.createElement(Badge,{label:b+" bad",color:C.red})
        )
      }),
      React.createElement(DataTable,{maxH:420,
        headers:["Label","Method","Note","Time"],
        emptyMsg:"No feedback yet — run the fraud-detection example",
        rows:(fb.recent||[]).map(function(f){return[
          f.label==="GOOD"?React.createElement(Badge,{label:"GOOD",color:C.green}):React.createElement(Badge,{label:"BAD",color:C.red}),
          React.createElement("span",{style:{fontFamily:"JetBrains Mono",fontSize:11}},(f.method||"-").split(".").pop()),
          React.createElement("span",{style:{color:"#475569",fontSize:11}},f.note||"-"),
          React.createElement("span",{style:{color:"#334155",fontSize:11}},f.time?new Date(f.time).toLocaleTimeString():"-")
        ];})
      })
    ),
    React.createElement("div",null,
      React.createElement("div",{className:"mb"},
        React.createElement(Card,null,
          React.createElement(CardHeader,{icon:"📊",title:"Learning Progress"}),
          React.createElement("div",{style:{height:200,paddingTop:8}},
            fbD.length===0?React.createElement(Empty,{msg:"No feedback yet"}):
            React.createElement(ResponsiveContainer,{width:"100%",height:"100%"},
              React.createElement(PieChart,null,
                React.createElement(Pie,{data:fbD,cx:"50%",cy:"50%",innerRadius:48,outerRadius:72,paddingAngle:4,dataKey:"value"},
                  fbD.map(function(d,i){return React.createElement(Cell,{key:i,fill:d.fill,strokeWidth:0});})
                ),
                React.createElement(Tooltip,{content:React.createElement(TT)}),
                React.createElement(Legend,{iconType:"circle",iconSize:8,wrapperStyle:{fontSize:10,color:"#64748b"}})
              )
            )
          )
        )
      ),
      React.createElement(Card,null,
        React.createElement(CardHeader,{icon:"📈",title:"Example Stats"}),
        React.createElement(MetricRow,{label:"Total examples",value:tot,pct:100,color:"#94a3b8"}),
        React.createElement(MetricRow,{label:"Good signals",value:g,pct:tot?g/tot*100:0,color:C.green}),
        React.createElement(MetricRow,{label:"Bad signals",value:b,pct:tot?b/tot*100:0,color:C.red}),
        React.createElement(MetricRow,{label:"Quality ratio",value:tot?(g/tot*100).toFixed(0)+"%":"—",pct:tot?g/tot*100:0,color:C.teal})
      )
    )
  );
}
var TABS = [
  {id:"overview",  label:"Overview",   icon:"🏠"},
  {id:"agents",    label:"Agents",     icon:"🤖"},
  {id:"traces",    label:"Traces",     icon:"🔍"},
  {id:"votes",     label:"Votes",      icon:"⚖️"},
  {id:"approvals", label:"Approvals",  icon:"✅"},
  {id:"security",  label:"Security",   icon:"🔐"},
  {id:"improve",   label:"Improve",    icon:"🧠"}
];

function App(){
  var tabState=useState("overview");var tab=tabState[0],setTab=tabState[1];
  var dataState=useState({
    status:{agentCount:0,totalSpans:0,totalTokens:0,avgLatencyMs:0,pendingApprovals:0,errorCount:0,squadName:"loading...",feedbackCount:0,auditEntries:0},
    agents:[],spans:[],votes:[],approvals:[],feedback:{},audit:[],activity:[]
  });
  var data=dataState[0],setData=dataState[1];
  var luState=useState(null);var lastUpdate=luState[0],setLastUpdate=luState[1];

  var load=useCallback(async function(){
    try{
      var results=await Promise.all([
        fetch(API+"/status").then(function(r){return r.json();}),
        fetch(API+"/agents").then(function(r){return r.json();}),
        fetch(API+"/traces").then(function(r){return r.json();}),
        fetch(API+"/votes").then(function(r){return r.json();}),
        fetch(API+"/approvals").then(function(r){return r.json();}),
        fetch(API+"/feedback").then(function(r){return r.json();}),
        fetch(API+"/audit").then(function(r){return r.json();}),
        fetch(API+"/activity").then(function(r){return r.json();})
      ]);
      setData({status:results[0],agents:results[1],spans:results[2],votes:results[3],
               approvals:results[4],feedback:results[5],audit:results[6],activity:results[7]});
      setLastUpdate(new Date().toLocaleTimeString());
    }catch(e){console.error("Load error:",e);}
  },[]);

  useEffect(function(){load();var t=setInterval(load,5000);return function(){clearInterval(t);};},[load]);

  var doApprove=useCallback(async function(id){
    await fetch(API+"/approvals/"+id+"/approve",{method:"POST"});load();
  },[load]);
  var doReject=useCallback(async function(id){
    await fetch(API+"/approvals/"+id+"/reject",{method:"POST"});load();
  },[load]);

  var st=data.status;
  var statCards=[
    {label:"Agents Online",value:st.agentCount,color:C.green,icon:"🤖",sub:data.agents.length+" registered"},
    {label:"Total Spans",value:st.totalSpans,color:C.blue,icon:"🔍",sub:"@Traced calls"},
    {label:"Total Tokens",value:(st.totalTokens||0).toLocaleString(),color:C.purple,icon:"💬",sub:"LLM usage"},
    {label:"Avg Latency ms",value:st.avgLatencyMs,color:C.teal,icon:"⚡",sub:"response time"},
    {label:"Pending",value:st.pendingApprovals,color:C.orange,icon:"⏳",sub:"awaiting review"},
    {label:"Errors",value:st.errorCount,color:C.red,icon:"⚠️",sub:st.totalSpans?(st.errorCount/st.totalSpans*100).toFixed(1)+"% rate":"no spans"}
  ];

  return React.createElement("div",{style:{minHeight:"100vh",display:"flex",flexDirection:"column"}},
    React.createElement("div",{className:"header"},
      React.createElement("div",{style:{display:"flex",alignItems:"center",gap:12}},
        React.createElement("span",{style:{fontSize:24}},"🛡️"),
        React.createElement("div",null,
          React.createElement("div",{style:{color:"#f1f5f9",fontWeight:700,fontSize:16,letterSpacing:".3px"}},"SquadOS Dashboard"),
          React.createElement("div",{style:{color:"#1e3a5f",fontSize:10}},"v3.3.0 · Multi-Agent AI Framework for Java")
        )
      ),
      React.createElement("div",{style:{display:"flex",gap:3}},
        TABS.map(function(t){
          return React.createElement("button",{
            key:t.id,
            className:"tab-btn"+(tab===t.id?" active":""),
            onClick:function(){setTab(t.id);}
          },
          React.createElement("span",null,t.icon),t.label);
        })
      ),
      React.createElement("div",{style:{display:"flex",alignItems:"center",gap:16}},
        React.createElement("div",{style:{display:"flex",alignItems:"center",gap:6,fontSize:11,color:C.green}},
          React.createElement(LiveDot,{color:C.green}),
          React.createElement("span",null,"LIVE")
        ),
        React.createElement("span",{style:{color:"#1e3a5f",fontSize:11}},st.squadName),
        React.createElement("button",{
          className:"icon-btn",
          onClick:load
        },"↻ Refresh")
      )
    ),
    React.createElement("div",{className:"stats-bar"},
      statCards.map(function(s,i){
        return React.createElement("div",{key:i,className:"stats-bar-cell"},
          React.createElement("div",{style:{display:"flex",justifyContent:"space-between",alignItems:"flex-start"}},
            React.createElement("div",null,
              React.createElement("div",{className:"stat-val",style:{color:s.color}},s.value||"—"),
              React.createElement("div",{className:"stat-lbl"},s.label),
              React.createElement("div",{className:"stat-sub"},s.sub)
            ),
            React.createElement("span",{style:{fontSize:20,opacity:.25}},s.icon)
          )
        );
      })
    ),
    React.createElement("div",{className:"content",style:{flex:1}},
      tab==="overview"  &&React.createElement(OverviewTab,  {data:data}),
      tab==="agents"    &&React.createElement(AgentsTab,    {data:data}),
      tab==="traces"    &&React.createElement(TracesTab,    {data:data}),
      tab==="votes"     &&React.createElement(VotesTab,     {data:data}),
      tab==="approvals" &&React.createElement(ApprovalsTab, {data:data,onApprove:doApprove,onReject:doReject}),
      tab==="security"  &&React.createElement(SecurityTab,  {data:data}),
      tab==="improve"   &&React.createElement(ImproveTab,   {data:data})
    ),
    React.createElement("div",{className:"footer"},
      React.createElement("span",null,"Updated: "+(lastUpdate||"loading...")+" · Errors: "+st.errorCount),
      React.createElement("span",null,"SquadOS v3.3.0 · React 18 + Recharts · Auto-refresh 5s"),
      React.createElement("span",null,"Feedback: "+st.feedbackCount+" · Audit: "+st.auditEntries)
    )
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(
  React.createElement(App)
);