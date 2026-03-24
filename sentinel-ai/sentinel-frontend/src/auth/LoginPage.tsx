import { useState, FormEvent } from "react";
import { useAuth } from "./AuthContext";

export function LoginPage() {
  const { login, error, isLoading } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [localError, setLocalError] = useState("");

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLocalError("");
    if (!username.trim() || !password.trim()) {
      setLocalError("Please enter username and password");
      return;
    }
    try { await login(username, password); }
    catch (err: any) { setLocalError(err.message || "Login failed"); }
  }

  const err = localError || error;

  return (
    <div style={{ minHeight: "100vh", background: "#080b0f",
      display: "flex", alignItems: "center", justifyContent: "center",
      fontFamily: "Inter, system-ui, sans-serif" }}>

      {/* Background grid effect */}
      <div style={{ position: "fixed", inset: 0,
        backgroundImage: "linear-gradient(#1e293b22 1px, transparent 1px), linear-gradient(90deg, #1e293b22 1px, transparent 1px)",
        backgroundSize: "40px 40px", pointerEvents: "none" }} />

      <div style={{ position: "relative", width: "100%", maxWidth: 420, padding: "0 20px" }}>

        {/* Logo */}
        <div style={{ textAlign: "center", marginBottom: 36 }}>
          <div style={{ fontSize: 48, marginBottom: 12 }}>🛡️</div>
          <div style={{ color: "#f1f5f9", fontSize: 26, fontWeight: 700, letterSpacing: "-0.5px" }}>
            SentinelAI
          </div>
          <div style={{ color: "#334155", fontSize: 13, marginTop: 4 }}>
            Social Media Mention Analyser
          </div>
        </div>

        {/* Card */}
        <div style={{ background: "linear-gradient(135deg,#0d1424,#111827)",
          border: "1px solid #1e293b", borderRadius: 14,
          padding: "32px 32px", boxShadow: "0 24px 64px rgba(0,0,0,.6)" }}>

          <div style={{ color: "#f1f5f9", fontWeight: 700, fontSize: 18,
            marginBottom: 4 }}>Welcome back</div>
          <div style={{ color: "#475569", fontSize: 13, marginBottom: 28 }}>
            Sign in to your account
          </div>

          <form onSubmit={handleSubmit}>
            {/* Username */}
            <div style={{ marginBottom: 16 }}>
              <label style={{ display: "block", color: "#64748b",
                fontSize: 12, fontWeight: 500, marginBottom: 6, letterSpacing: ".3px" }}>
                USERNAME
              </label>
              <input
                type="text" value={username} autoFocus
                onChange={e => setUsername(e.target.value)}
                placeholder="admin"
                style={{ width: "100%", background: "#060a10",
                  border: `1px solid ${err && !username ? "#ef4444" : "#1e293b"}`,
                  color: "#e2e8f0", padding: "10px 14px",
                  borderRadius: 8, fontSize: 14, fontFamily: "Inter",
                  outline: "none", transition: "border-color .2s" }}
                onFocus={e => e.target.style.borderColor = "#3b82f6"}
                onBlur={e => e.target.style.borderColor = "#1e293b"}
              />
            </div>

            {/* Password */}
            <div style={{ marginBottom: 24 }}>
              <label style={{ display: "block", color: "#64748b",
                fontSize: 12, fontWeight: 500, marginBottom: 6, letterSpacing: ".3px" }}>
                PASSWORD
              </label>
              <input
                type="password" value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                style={{ width: "100%", background: "#060a10",
                  border: `1px solid ${err && !password ? "#ef4444" : "#1e293b"}`,
                  color: "#e2e8f0", padding: "10px 14px",
                  borderRadius: 8, fontSize: 14, fontFamily: "Inter",
                  outline: "none", transition: "border-color .2s" }}
                onFocus={e => e.target.style.borderColor = "#3b82f6"}
                onBlur={e => e.target.style.borderColor = "#1e293b"}
              />
            </div>

            {/* Error */}
            {err && (
              <div style={{ background: "#1a0808", border: "1px solid #ef444433",
                borderRadius: 8, padding: "10px 14px", marginBottom: 16,
                color: "#ef4444", fontSize: 13, display: "flex", gap: 8 }}>
                <span>⚠️</span><span>{err}</span>
              </div>
            )}

            {/* Submit */}
            <button type="submit" disabled={isLoading}
              style={{ width: "100%", background: isLoading ? "#1e293b" : "linear-gradient(135deg,#3b82f6,#2563eb)",
                color: isLoading ? "#475569" : "white",
                border: "none", borderRadius: 8, padding: "12px",
                fontSize: 14, fontWeight: 600, fontFamily: "Inter",
                cursor: isLoading ? "not-allowed" : "pointer",
                transition: "all .2s", letterSpacing: ".3px" }}>
              {isLoading ? "Signing in..." : "Sign In →"}
            </button>
          </form>

          {/* Dev hint */}
          <div style={{ marginTop: 20, padding: "10px 12px",
            background: "#060a10", borderRadius: 8, border: "1px solid #1e293b" }}>
            <div style={{ color: "#334155", fontSize: 11, marginBottom: 4 }}>
              Default credentials (dev):
            </div>
            <div style={{ color: "#475569", fontSize: 12, fontFamily: "JetBrains Mono" }}>
              admin / Admin@123
            </div>
          </div>
        </div>

        <div style={{ textAlign: "center", marginTop: 20, color: "#1e3a5f", fontSize: 11 }}>
          SentinelAI v1.0.0 · Powered by SquadOS
        </div>
      </div>
    </div>
  );
}