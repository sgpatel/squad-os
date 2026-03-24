import { useState } from "react";

const SHORTCUTS = [
  { keys: ["1-7"],   desc: "Switch to tab 1-7" },
  { keys: ["A"],        desc: "Approve focused reply" },
  { keys: ["R"],        desc: "Reject focused reply" },
  { keys: ["J"],        desc: "Next mention" },
  { keys: ["K"],        desc: "Previous mention" },
  { keys: ["?"],        desc: "Show this help" },
  { keys: ["Esc"],      desc: "Close / collapse" },
  { keys: ["⌘/Ctrl","K"], desc: "Command palette (coming soon)" },
];

export function KeyboardHelp() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)} style={{
        background: "none", border: "1px solid #1e293b",
        color: "#334155", borderRadius: 6,
        padding: "4px 8px", cursor: "pointer",
        fontSize: 10, fontFamily: "Inter"
      }}>⌨ ?</button>
      {open && (
        <div onClick={() => setOpen(false)} style={{
          position: "fixed", inset: 0, background: "rgba(0,0,0,.7)",
          zIndex: 9998, display: "flex", alignItems: "center", justifyContent: "center"
        }}>
          <div onClick={e => e.stopPropagation()} style={{
            background: "#0d1424", border: "1px solid #1e293b",
            borderRadius: 12, padding: "24px 28px", minWidth: 340
          }}>
            <div style={{ color: "#f1f5f9", fontWeight: 700, fontSize: 14,
              marginBottom: 16, display: "flex", justifyContent: "space-between" }}>
              ⌨️ Keyboard Shortcuts
              <button onClick={() => setOpen(false)} style={{
                background: "none", border: "none", color: "#475569",
                cursor: "pointer", fontSize: 18 }}>×</button>
            </div>
            {SHORTCUTS.map((s, i) => (
              <div key={i} style={{ display: "flex", justifyContent: "space-between",
                padding: "7px 0", borderBottom: "1px solid #1e293b",
                alignItems: "center" }}>
                <span style={{ color: "#94a3b8", fontSize: 12 }}>{s.desc}</span>
                <div style={{ display: "flex", gap: 4 }}>
                  {s.keys.map((k, j) => (
                    <kbd key={j} style={{
                      background: "#1e293b", color: "#e2e8f0",
                      borderRadius: 4, padding: "2px 7px",
                      fontSize: 11, fontFamily: "JetBrains Mono",
                      border: "1px solid #334155"
                    }}>{k}</kbd>
                  ))}
                </div>
              </div>
            ))}
            <div style={{ color: "#334155", fontSize: 10, marginTop: 12, textAlign: "center" }}>
              Press <kbd style={{ background: "#1e293b", borderRadius: 3, padding: "1px 5px",
                fontSize: 10 }}>?</kbd> anytime to show this</div>
          </div>
        </div>
      )}
    </>
  );
}