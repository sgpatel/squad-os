const API = '/api';
const fmt = function(t) { return new Date(t).toLocaleTimeString(); };
let allSpans = [], allAudit = [], allData = {};
const charts = {};

function rb(role) {
  var m = {STRATEGIST:'bg',ANALYST:'bb',RESEARCHER:'bp',CRITIC:'bo',SUPPORT:'bt',WILDCARD:'bm'};
  return '<span class="bdg ' + (m[role] || 'bm') + '">' + role + '</span>';
}
function sb(s) {
  if (s === 'OK' || s === 'GRANTED' || s === 'APPROVED') return '<span class="bdg bg">' + s + '</span>';
  if (s === 'ERROR' || s === 'DENIED' || s === 'REJECTED') return '<span class="bdg br">' + s + '</span>';
  if (s === 'PENDING') return '<span class="bdg bo">' + s + '</span>';
  return '<span class="bdg bm">' + (s || '-') + '</span>';
}
function adc(t) {
  var c = {AGENT:'#39d353',VOTE:'#58a6ff',APPROVAL:'#e3a246',SYSTEM:'#bc8cff'};
  return c[t] || '#484f58';
}
function el(id) { return document.getElementById(id); }
function set(id, val) { var e = el(id); if (e) e.textContent = val; }
function html(id, val) { var e = el(id); if (e) e.innerHTML = val; }

function mkChart(id, type, data, opts) {
  var ctx = el(id);
  if (!ctx) return;
  if (charts[id]) charts[id].destroy();
  var def = {responsive:true, maintainAspectRatio:false, plugins:{legend:{labels:{color:'#484f58',font:{size:9}}}}};
  charts[id] = new Chart(ctx, {type:type, data:data, options:Object.assign({}, def, opts || {})});
}

function switchTab(name) {
  document.querySelectorAll('.content').forEach(function(p) { p.classList.remove('active'); });
  document.querySelectorAll('.tab').forEach(function(b) { b.classList.remove('active'); });
  var panel = el('panel-' + name);
  var btn = el('tab-btn-' + name);
  if (panel) panel.classList.add('active');
  if (btn) btn.classList.add('active');
  renderCharts();
}

function renderActivity(ac) {
  set('c-act', ac.length);
  html('t-act', ac.slice(0, 20).map(function(a) {
    var col = adc(a.type);
    return '<div class="ai">' +
      '<div class="ai-dot" style="background:' + col + '"></div>' +
      '<span class="ai-type" style="color:' + col + '">' + a.type + '</span>' +
      '<span class="ai-msg">' + a.agent + ': ' + a.message + '</span>' +
      '<span class="ai-time">' + fmt(a.time) + '</span></div>';
  }).join('') || '<div style="padding:20px;text-align:center;color:var(--muted)">No activity yet</div>');
}

function renderAgentCards(ag) {
  set('c-ag-ov', ag.length);
  html('ag-cards', ag.map(function(a) {
    return '<div class="acard">' +
      '<div class="ac-status"><div class="ac-dot"></div>ONLINE</div>' +
      '<div class="ac-name">' + a.name + '</div>' +
      '<div class="ac-role ' + a.role + '">' + a.role + '</div>' +
      '<div class="ac-stats">' +
        '<div><div class="ac-sv">' + a.temp + '</div><div class="ac-sl">temp</div></div>' +
        '<div><div class="ac-sv">' + a.maxTokens + '</div><div class="ac-sl">tokens</div></div>' +
      '</div></div>';
  }).join(''));
}

function renderAgentsTable(ag) {
  set('c-ag-tab', ag.length);
  html('t-ag-full', ag.map(function(a) {
    return '<tr><td style="color:#fff;font-weight:700">' + a.name + '</td>' +
      '<td>' + rb(a.role) + '</td><td>' + sb('OK') + '</td>' +
      '<td>' + a.temp + '</td><td>' + a.maxTokens + '</td></tr>';
  }).join('') || '<tr><td colspan="5" class="empty">No agents</td></tr>');
}

function renderTimeline(ac) {
  html('ag-tl', ac.slice(0, 12).map(function(a) {
    return '<div class="tli">' +
      '<div class="tld" style="background:' + adc(a.type) + '"></div>' +
      '<div><div class="tlt">' + a.agent + ': ' + a.message + '</div>' +
      '<div class="tltime">' + fmt(a.time) + '</div></div></div>';
  }).join('') || '<div style="color:var(--muted);font-size:11px">No activity</div>');
}

function renderSpanTable(sp) {
  set('c-sp-ov', sp.length);
  set('c-sp-tab', sp.length);
  html('t-sp-ov', sp.slice(0, 8).map(function(s) {
    return '<tr><td style="color:#fff">' + s.spanName + '</td>' +
      '<td class="' + s.agentRole + '">' + s.agentName + '</td>' +
      '<td style="color:var(--teal)">' + s.durationMs + 'ms</td>' +
      '<td>' + sb(s.status) + '</td></tr>';
  }).join('') || '<tr><td colspan="4" class="empty">No spans yet</td></tr>');
  html('t-sp-full', sp.slice(0, 50).map(function(s) {
    return '<tr><td style="color:#fff">' + s.spanName + '</td>' +
      '<td>' + s.agentName + '</td><td>' + rb(s.agentRole) + '</td>' +
      '<td style="color:var(--teal)">' + s.durationMs + 'ms</td>' +
      '<td>' + (s.tokens || 0) + '</td><td>' + sb(s.status) + '</td>' +
      '<td style="color:var(--red);font-size:10px">' + (s.error || '-') + '</td>' +
      '<td style="color:var(--muted)">' + fmt(s.timestamp) + '</td></tr>';
  }).join('') || '<tr><td colspan="8" class="empty">No spans yet</td></tr>');
}

function renderVotes(vt) {
  set('c-vt-ov', vt.length);
  set('c-vt-list', vt.length);
  set('c-vt-tab', vt.length);
  var voteHtml = vt.map(function(v) {
    var tot = v.approve + v.reject + v.abstain || 1;
    var pct = Math.round(v.approve / tot * 100);
    var col = v.outcome === 'APPROVED' ? 'var(--green)' : v.outcome === 'REJECTED' ? 'var(--red)' : 'var(--orange)';
    return '<div class="vi"><div class="vr"><span class="vtopic">' + v.topic + '</span>' +
      '<span class="bdg" style="background:rgba(0,0,0,.3);color:' + col + ';border:1px solid ' + col + '40">' + v.outcome + '</span></div>' +
      '<div class="vbar"><div class="vfill" style="width:' + pct + '%;background:' + col + '"></div></div>' +
      '<div class="vcnts"><span style="color:var(--green)">▲ ' + v.approve + '</span>' +
      '<span style="color:var(--red)">▼ ' + v.reject + '</span>' +
      '<span style="color:var(--muted)">○ ' + v.abstain + '</span></div></div>';
  }).join('') || '<div style="padding:20px;text-align:center;color:var(--muted)">No votes yet</div>';
  html('t-vt-ov', voteHtml);
  html('t-vt-full', voteHtml);
}

function renderApprovals(ap) {
  var pend = ap.filter(function(a) { return a.status === 'PENDING'; }).length;
  set('c-pend', pend + ' pending');
  set('c-appr', ap.filter(function(a) { return a.status === 'APPROVED'; }).length + ' approved');
  set('c-rejt', ap.filter(function(a) { return a.status === 'REJECTED'; }).length + ' rejected');
  html('t-ap-full', ap.map(function(a) {
    var act = a.status === 'PENDING'
      ? '<button class="ba" data-approve="' + a.id + '">&#10003;</button><button class="bj" data-reject="' + a.id + '">&#10007;</button>'
      : '-';
    return '<tr><td style="color:#fff">' + (a.method || '-').split('.').pop() + '</td>' +
      '<td style="color:var(--muted);font-size:10px">' + (a.reason || '-') + '</td>' +
      '<td>' + sb(a.status) + '</td><td><span class="bdg bo">' + (a.priority || 'NORMAL') + '</span></td>' +
      '<td style="color:var(--muted)">' + (a.escalateTo || '-') + '</td>' +
      '<td style="color:var(--muted)">' + (a.createdAt ? fmt(a.createdAt) : '-') + '</td>' +
      '<td>' + act + '</td></tr>';
  }).join('') || '<tr><td colspan="7" class="empty">No approvals</td></tr>');
}

function renderAuditTable(au) {
  set('c-au-tab', au.length);
  html('t-au-full', au.slice(0, 50).map(function(e) {
    return '<tr><td style="color:#fff">' + (e.subject || '-') + '</td>' +
      '<td>' + (e.method || '-').split('.').pop() + '</td>' +
      '<td>' + sb(e.decision) + '</td>' +
      '<td style="color:var(--muted);font-size:10px">' + ((e.roles || []).join(', ') || '-') + '</td>' +
      '<td style="color:var(--teal)">' + (e.duration || 0) + 'ms</td>' +
      '<td style="color:var(--muted)">' + fmt(e.time) + '</td></tr>';
  }).join('') || '<tr><td colspan="6" class="empty">No audit entries</td></tr>');
  var g = au.filter(function(e) { return e.decision === 'GRANTED'; }).length;
  var d = au.length - g;
  html('au-met',
    '<div class="mr"><span class="mn">Granted</span><div class="mbw"><div class="mb" style="width:' + (au.length ? g/au.length*100 : 0) + '%;background:var(--green)"></div></div><span class="mv" style="color:var(--green)">' + g + '</span></div>' +
    '<div class="mr"><span class="mn">Denied</span><div class="mbw"><div class="mb" style="width:' + (au.length ? d/au.length*100 : 0) + '%;background:var(--red)"></div></div><span class="mv" style="color:var(--red)">' + d + '</span></div>');
}

function renderFeedback(fb) {
  set('c-gd', (fb.good || 0) + ' good');
  set('c-bd', (fb.bad || 0) + ' bad');
  html('t-fb-full', (fb.recent || []).map(function(f) {
    return '<tr><td>' + (f.label === 'GOOD' ? '<span class="bdg bg">GOOD</span>' : '<span class="bdg br">BAD</span>') + '</td>' +
      '<td style="color:#fff">' + (f.method || '-').split('.').pop() + '</td>' +
      '<td style="color:var(--muted);font-size:10px">' + (f.note || '-') + '</td>' +
      '<td style="color:var(--muted)">' + (f.time ? fmt(f.time) : '-') + '</td></tr>';
  }).join('') || '<tr><td colspan="4" class="empty">No feedback yet</td></tr>');
  var tot = fb.total || 0, g = fb.good || 0, b = fb.bad || 0;
  html('fb-met',
    '<div class="mr"><span class="mn">Total examples</span><span class="mv">' + tot + '</span></div>' +
    '<div class="mr"><span class="mn">Good signals</span><div class="mbw"><div class="mb" style="width:' + (tot?g/tot*100:0) + '%;background:var(--green)"></div></div><span class="mv" style="color:var(--green)">' + g + '</span></div>' +
    '<div class="mr"><span class="mn">Bad signals</span><div class="mbw"><div class="mb" style="width:' + (tot?b/tot*100:0) + '%;background:var(--red)"></div></div><span class="mv" style="color:var(--red)">' + b + '</span></div>');
}

function renderCharts() {
  var sp = allSpans, au = allAudit;
  var vt = allData.votes || [], ag = allData.agents || [], fb = allData.feedback || {};
  var co = ['rgba(57,211,83,.7)','rgba(88,166,255,.7)','rgba(188,140,255,.7)','rgba(227,162,70,.7)','rgba(45,212,191,.7)'];
  var SX = {x:{ticks:{color:'#484f58',font:{size:9}}},y:{ticks:{color:'#484f58',font:{size:9}},grid:{color:'rgba(255,255,255,.03)'}}};

  var tok = sp.reduce(function(s,x){return s+(x.tokens||0);},0);
  mkChart('ch-tok','doughnut',{labels:['Tokens','Input'],datasets:[{data:[tok,sp.reduce(function(s,x){return s+(x.inputLen||0);},0)],backgroundColor:['rgba(188,140,255,.7)','rgba(88,166,255,.7)'],borderColor:['#bc8cff','#58a6ff'],borderWidth:1}]},{plugins:{legend:{position:'bottom',labels:{color:'#484f58',font:{size:9}}}}});
  html('tok-met','<div class="mr"><span class="mn">Total tokens</span><span class="mv" style="color:var(--purple)">' + tok + '</span></div><div class="mr"><span class="mn">Spans</span><span class="mv" style="color:var(--blue)">' + sp.length + '</span></div>');

  var appr=vt.filter(function(v){return v.outcome==='APPROVED';}).length;
  var rejt=vt.filter(function(v){return v.outcome==='REJECTED';}).length;
  mkChart('ch-vot','doughnut',{labels:['Approved','Rejected','Tie'],datasets:[{data:[appr,rejt,vt.length-appr-rejt],backgroundColor:['rgba(57,211,83,.7)','rgba(248,81,73,.7)','rgba(227,162,70,.7)'],borderWidth:1}]},{plugins:{legend:{position:'bottom',labels:{color:'#484f58',font:{size:9}}}}});

  var ts = sp.slice(0,6);
  mkChart('ch-lat','bar',{labels:ts.map(function(s){return s.spanName.substring(0,10);}),datasets:[{label:'ms',data:ts.map(function(s){return s.durationMs;}),backgroundColor:'rgba(45,212,191,.5)',borderColor:'#2dd4bf',borderWidth:1}]},{plugins:{legend:{display:false}},scales:SX});

  var rc={};ag.forEach(function(a){rc[a.role]=(rc[a.role]||0)+1;});
  mkChart('ch-roles','doughnut',{labels:Object.keys(rc),datasets:[{data:Object.values(rc),backgroundColor:co,borderWidth:1}]},{plugins:{legend:{position:'right',labels:{color:'#484f58',font:{size:9}}}}});

  var bk=[0,0,0,0,0];sp.forEach(function(s){if(s.durationMs<100)bk[0]++;else if(s.durationMs<500)bk[1]++;else if(s.durationMs<1000)bk[2]++;else if(s.durationMs<5000)bk[3]++;else bk[4]++;});
  mkChart('ch-dur','bar',{labels:['<100ms','100-500ms','500ms-1s','1-5s','>5s'],datasets:[{data:bk,backgroundColor:'rgba(88,166,255,.5)',borderColor:'#58a6ff',borderWidth:1}]},{plugins:{legend:{display:false}},scales:SX});

  var ok=sp.filter(function(s){return s.status==='OK';}).length;
  mkChart('ch-stat','doughnut',{labels:['OK','ERROR'],datasets:[{data:[ok,sp.length-ok],backgroundColor:['rgba(57,211,83,.7)','rgba(248,81,73,.7)'],borderWidth:1}]},{plugins:{legend:{position:'bottom',labels:{color:'#484f58',font:{size:9}}}}});

  mkChart('ch-vot-full','bar',{labels:vt.slice(0,8).map(function(v){return v.topic.substring(0,18);}),datasets:[{label:'Approve',data:vt.slice(0,8).map(function(v){return v.approve;}),backgroundColor:'rgba(57,211,83,.6)',borderColor:'#39d353',borderWidth:1},{label:'Reject',data:vt.slice(0,8).map(function(v){return v.reject;}),backgroundColor:'rgba(248,81,73,.6)',borderColor:'#f85149',borderWidth:1}]},{plugins:{legend:{labels:{color:'#484f58',font:{size:9}}}},scales:SX});

  var gau=au.filter(function(e){return e.decision==='GRANTED';}).length;
  mkChart('ch-aud','doughnut',{labels:['Granted','Denied'],datasets:[{data:[gau,au.length-gau],backgroundColor:['rgba(57,211,83,.7)','rgba(248,81,73,.7)'],borderWidth:1}]},{plugins:{legend:{position:'bottom',labels:{color:'#484f58',font:{size:9}}}}});

  mkChart('ch-fb','doughnut',{labels:['Good','Bad'],datasets:[{data:[fb.good||0,fb.bad||0],backgroundColor:['rgba(57,211,83,.7)','rgba(248,81,73,.7)'],borderWidth:1}]},{plugins:{legend:{position:'bottom',labels:{color:'#484f58',font:{size:9}}}}});
}

async function loadAll() {
  try {
    var results = await Promise.all([
      fetch(API+'/status').then(function(r){return r.json();}),
      fetch(API+'/agents').then(function(r){return r.json();}),
      fetch(API+'/traces').then(function(r){return r.json();}),
      fetch(API+'/votes').then(function(r){return r.json();}),
      fetch(API+'/approvals').then(function(r){return r.json();}),
      fetch(API+'/feedback').then(function(r){return r.json();}),
      fetch(API+'/audit').then(function(r){return r.json();}),
      fetch(API+'/activity').then(function(r){return r.json();}),
    ]);
    var st=results[0], ag=results[1], sp=results[2], vt=results[3];
    var ap=results[4], fb=results[5], au=results[6], ac=results[7];

    allSpans = sp; allAudit = au;
    allData = {votes:vt, agents:ag, feedback:fb};

    set('s-ag', st.agentCount); set('s-sp', st.totalSpans);
    set('s-tk', st.totalTokens.toLocaleString()); set('s-lt', st.avgLatencyMs);
    set('s-pa', st.pendingApprovals); set('s-er', st.errorCount);
    set('sq-name', st.squadName);
    set('d-ag', ag.length + ' registered');
    set('d-tk', st.totalTokens > 0 ? st.totalTokens + ' used' : 'no calls yet');
    set('d-er', st.totalSpans > 0 ? (st.errorCount/st.totalSpans*100).toFixed(1)+'% rate' : 'no spans');

    renderActivity(ac); renderAgentCards(ag); renderAgentsTable(ag);
    renderTimeline(ac); renderSpanTable(sp); renderVotes(vt);
    renderApprovals(ap); renderAuditTable(au); renderFeedback(fb);
    renderCharts();

    set('ft-l', 'Updated: ' + new Date().toLocaleTimeString() + ' · Errors: ' + st.errorCount);
    set('ft-r', 'Squad: ' + st.squadName + ' · Feedback: ' + st.feedbackCount);
  } catch(e) {
    set('ft-l', 'Connection error: ' + e.message);
  }
}

async function doApprove(id) { await fetch(API+'/approvals/'+id+'/approve',{method:'POST'}); loadAll(); }
async function doReject(id)  { await fetch(API+'/approvals/'+id+'/reject', {method:'POST'}); loadAll(); }

function filterSpans() {
  var q = el('sp-srch').value.toLowerCase();
  renderSpanTable(allSpans.filter(function(s) {
    return (s.spanName||'').toLowerCase().includes(q) || (s.agentName||'').toLowerCase().includes(q) || (s.status||'').toLowerCase().includes(q);
  }));
}

function filterAudit() {
  var q = el('au-srch').value.toLowerCase();
  renderAuditTable(allAudit.filter(function(e) {
    return (e.subject||'').toLowerCase().includes(q) || (e.method||'').toLowerCase().includes(q) || (e.decision||'').toLowerCase().includes(q);
  }));
}

// Wire up events after DOM ready
document.addEventListener('DOMContentLoaded', function() {
  ['overview','agents','traces','votes','approvals','security','improve'].forEach(function(name) {
    var btn = el('tab-btn-' + name);
    if (btn) btn.addEventListener('click', function() { switchTab(name); });
  });
  var rbtn = el('refresh-btn');
  if (rbtn) rbtn.addEventListener('click', loadAll);

  var spSrch = el('sp-srch');
  if (spSrch) spSrch.addEventListener('input', filterSpans);
  var auSrch = el('au-srch');
  if (auSrch) auSrch.addEventListener('input', filterAudit);

  // Approval buttons via event delegation
  document.addEventListener('click', function(e) {
    if (e.target.dataset.approve) doApprove(e.target.dataset.approve);
    if (e.target.dataset.reject)  doReject(e.target.dataset.reject);
  });

  loadAll();
  setInterval(loadAll, 5000);
});
