import { useState, useCallback } from "react";
export interface ToastMsg { id:string; type:"success"|"error"|"warning"|"info"; title:string; message:string; }
const COLORS = { success:"#22c55e", error:"#ef4444", warning:"#f97316", info:"#3b82f6" };
const ICONS  = { success:"✅", error:"🚨", warning:"⚠️", info:"💬" };
export function Toast({ toasts, onRemove }:{ toasts:ToastMsg[]; onRemove:(id:string)=>void }) {
  return (
    <div style={{position:"fixed",bottom:24,right:24,zIndex:9999,display:"flex",flexDirection:"column",gap:8,pointerEvents:"none"}}>
      {toasts.map(t => (
        <div key={t.id} style={{background:"#0d1424",border:`1px solid ${COLORS[t.type]}44`,
          borderLeft:`3px solid ${COLORS[t.type]}`,borderRadius:10,padding:"12px 16px",
          minWidth:280,maxWidth:360,boxShadow:"0 8px 32px rgba(0,0,0,.6)",
          animation:"fadeUp .25s ease",pointerEvents:"all",display:"flex",gap:10,alignItems:"flex-start"}}>
          <span style={{fontSize:16,flexShrink:0}}>{ICONS[t.type]}</span>
          <div style={{flex:1}}>
            <div style={{color:"#f1f5f9",fontWeight:600,fontSize:12,marginBottom:2}}>{t.title}</div>
            <div style={{color:"#64748b",fontSize:11,lineHeight:1.4}}>{t.message}</div>
          </div>
          <button onClick={()=>onRemove(t.id)} style={{background:"none",border:"none",color:"#334155",cursor:"pointer",fontSize:16,padding:0,lineHeight:1}}>×</button>
        </div>
      ))}
    </div>
  );
}
export function useToast() {
  const [toasts, setToasts] = useState<ToastMsg[]>([]);
  const add = useCallback((t: Omit<ToastMsg,"id">) => {
    const id = Math.random().toString(36).slice(2);
    setToasts(prev => [...prev.slice(-3), {...t, id}]);
    setTimeout(() => setToasts(prev => prev.filter(x => x.id !== id)), 5000);
  }, []);
  const remove = useCallback((id:string) => setToasts(prev => prev.filter(x=>x.id!==id)), []);
  return { toasts, add, remove };
}