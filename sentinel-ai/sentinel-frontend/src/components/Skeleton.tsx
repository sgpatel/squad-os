export function Skeleton({ width="100%", height=14, radius=6 }:{ width?:string|number; height?:number; radius?:number }) {
  return <div style={{width,height,borderRadius:radius,background:"linear-gradient(90deg,#1e293b 25%,#263548 50%,#1e293b 75%)",
    backgroundSize:"200% 100%",animation:"shimmer 1.5s infinite"}} />;
}
export function MentionSkeleton() {
  return (
    <div style={{background:"#080e1a",border:"1px solid #1e293b",borderRadius:8,padding:12,marginBottom:8}}>
      <style>{`@keyframes shimmer{0%{background-position:200% 0}100%{background-position:-200% 0}}`}</style>
      <div style={{display:"flex",justifyContent:"space-between",marginBottom:8}}><Skeleton width={140} height={11}/><Skeleton width={60} height={18} radius={20}/></div>
      <Skeleton height={11}/><div style={{marginTop:5}}><Skeleton width="75%" height={11}/></div>
    </div>
  );
}