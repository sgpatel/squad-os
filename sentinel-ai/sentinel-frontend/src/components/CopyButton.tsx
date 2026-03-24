import { useState } from "react";

export function CopyButton({ text, label = "Copy" }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false);

  const copy = async (e: React.MouseEvent) => {
    e.stopPropagation();
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <button onClick={copy} style={{
      background: copied ? "#22c55e22" : "none",
      color:      copied ? "#22c55e"   : "#475569",
      border:     "1px solid " + (copied ? "#22c55e44" : "#1e293b"),
      borderRadius: 5, padding: "3px 8px",
      cursor: "pointer", fontSize: 10,
      fontFamily: "Inter", transition: "all .2s",
      display: "flex", alignItems: "center", gap: 4
    }}>
      {copied ? "✓ Copied" : "📋 " + label}
    </button>
  );
}