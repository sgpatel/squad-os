import { useState } from "react";
import { approveReply, rejectReply } from "../hooks/useSentinel";
import { CopyButton } from "./CopyButton";

interface Props {
  mentionId: string;
  originalReply: string;
  onDone: () => void;
}

export function InlineReplyEditor({ mentionId, originalReply, onDone }: Props) {
  const [text, setText] = useState(originalReply || "");
  const [loading, setLoading] = useState(false);
  const charLimit = 280;
  const over = text.length > charLimit;

  const doApprove = async () => {
    setLoading(true);
    // If edited, reject original and we consider it approved with new text
    if (text !== originalReply) {
      await rejectReply(mentionId, text);
    } else {
      await approveReply(mentionId);
    }
    setLoading(false);
    onDone();
  };

  const doReject = async () => {
    setLoading(true);
    await rejectReply(mentionId);
    setLoading(false);
    onDone();
  };

  return (
    <div style={{ background: "#0a1020", borderRadius: 8, padding: 12, marginTop: 8 }}
      onClick={e => e.stopPropagation()}>
      <div style={{ display: "flex", justifyContent: "space-between",
        alignItems: "center", marginBottom: 6 }}>
        <div style={{ color: "#334155", fontSize: 10, textTransform: "uppercase",
          letterSpacing: 1 }}>Edit Reply</div>
        <div style={{ display: "flex", gap: 4 }}>
          <CopyButton text={text} label="Copy reply" />
          <span style={{ color: over ? "#ef4444" : "#334155", fontSize: 10 }}>
            {text.length}/{charLimit}
          </span>
        </div>
      </div>
      <textarea
        value={text}
        onChange={e => setText(e.target.value)}
        style={{
          width: "100%", background: "#060a10",
          border: "1px solid " + (over ? "#ef444466" : "#1e293b"),
          color: "#e2e8f0", padding: "8px 10px",
          borderRadius: 6, fontSize: 12,
          fontFamily: "Inter", resize: "vertical",
          minHeight: 72, outline: "none", lineHeight: 1.5
        }}
      />
      <div style={{ display: "flex", gap: 6, marginTop: 8 }}>
        <button onClick={doApprove} disabled={loading || over} style={{
          background: "#22c55e22", color: "#22c55e",
          border: "1px solid #22c55e44", borderRadius: 6,
          padding: "5px 14px", cursor: loading || over ? "not-allowed" : "pointer",
          fontSize: 11, fontFamily: "Inter", fontWeight: 600
        }}>
          {loading ? "..." : text !== originalReply ? "✓ Approve (edited)" : "✓ Approve"}
        </button>
        <button onClick={doReject} disabled={loading} style={{
          background: "#ef444422", color: "#ef4444",
          border: "1px solid #ef444444", borderRadius: 6,
          padding: "5px 14px", cursor: "pointer",
          fontSize: 11, fontFamily: "Inter"
        }}>
          ✗ Reject
        </button>
        {text !== originalReply && (
          <button onClick={() => setText(originalReply)} style={{
            background: "none", color: "#475569",
            border: "1px solid #1e293b", borderRadius: 6,
            padding: "5px 10px", cursor: "pointer",
            fontSize: 10, fontFamily: "Inter"
          }}>
            Reset
          </button>
        )}
      </div>
    </div>
  );
}