import { createContext, useContext, useState, useEffect, useCallback } from "react";

export interface AuthUser {
  id:       string;
  username: string;
  email:    string;
  role:     string;
  fullName: string;
  tenantId: string;
  token:    string;
  expiresAt:string;
}

interface AuthCtx {
  user:     AuthUser | null;
  login:    (username: string, password: string) => Promise<void>;
  logout:   () => void;
  isAdmin:  boolean;
  isLoading:boolean;
  error:    string | null;
}

const AuthContext = createContext<AuthCtx>({} as AuthCtx);

const TOKEN_KEY = "sentinel_auth";
const API = import.meta.env.VITE_API_URL || "http://localhost:8090";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user,      setUser]      = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error,     setError]     = useState<string | null>(null);

  // Restore session on mount
  useEffect(() => {
    try {
      const stored = localStorage.getItem(TOKEN_KEY);
      if (stored) {
        const parsed: AuthUser = JSON.parse(stored);
        // Check token not expired
        if (new Date(parsed.expiresAt) > new Date()) {
          setUser(parsed);
        } else {
          localStorage.removeItem(TOKEN_KEY);
        }
      }
    } catch { localStorage.removeItem(TOKEN_KEY); }
    setIsLoading(false);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    setError(null); setIsLoading(true);
    try {
      const r = await fetch(`${API}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const data = await r.json();
      if (!r.ok) throw new Error(data.error || "Login failed");
      // Fetch full profile
      const meR = await fetch(`${API}/api/auth/me`, {
        headers: { "Authorization": `Bearer ${data.token}` }
      });
      const me = await meR.json();
      const authUser: AuthUser = {
        id: me.id, username: me.username, email: me.email,
        role: me.role, fullName: me.fullName || me.username,
        tenantId: me.tenantId, token: data.token, expiresAt: data.expiresAt,
      };
      localStorage.setItem(TOKEN_KEY, JSON.stringify(authUser));
      setUser(authUser);
    } catch (e: any) {
      setError(e.message || "Login failed");
      throw e;
    } finally { setIsLoading(false); }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{
      user, login, logout,
      isAdmin: user?.role === "ADMIN",
      isLoading, error,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);

// Convenience — get auth header for fetch calls
export function getAuthHeader(): Record<string, string> {
  try {
    const stored = localStorage.getItem(TOKEN_KEY);
    if (!stored) return {};
    const user: AuthUser = JSON.parse(stored);
    return { "Authorization": `Bearer ${user.token}` };
  } catch { return {}; }
}