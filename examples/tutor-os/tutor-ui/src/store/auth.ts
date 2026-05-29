import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { LearnerTier } from '@/lib/types';

/* ─────────────────────────────────────────────────────────────────
 * auth.ts — local-only user accounts for the TutorOS demo.
 *
 * Everything lives in `localStorage`; there is no remote auth server.
 * Passwords are salted + hashed with SHA-256 (SubtleCrypto) so the
 * stored record doesn't contain the plaintext, but this is a DEMO —
 * do not reuse this approach in a real product.
 *
 * A logged-in user's profile drives:
 *   - `learnerId` sent to tutor-api (session key = learnerId:subject)
 *   - name/initials shown in chat + topbar
 *   - level/goal passed into `/session/start`
 *   - sessionsPerWeek + analogy domain used by the LearnerProfile the
 *     backend composes when onboarding the session
 * ───────────────────────────────────────────────────────────────── */

// ── Types ───────────────────────────────────────────────────────────

export interface UserRecord {
  id: string;               // learnerId sent to the backend
  email: string;            // unique key
  name: string;
  initials: string;
  passwordHash: string;     // SHA-256(salt + password)
  salt: string;
  /** Display tier — used by the topbar + progress UI. */
  tier: LearnerTier;
  xpToday: number;
  xpGoal: number;
  streak: number;
  /** Mirrors `LearnerProfile.level` on the backend. */
  level: string;            // "primary" | "middle" | "higher-sec" | "college" | "pro"
  profession: string;
  goal: string;
  analogyDomain: string;
  sessionsPerWeek: number;
  createdAt: string;
  updatedAt: string;
}

export interface RegisterInput {
  email: string;
  name: string;
  password: string;
  level?: string;
  goal?: string;
  profession?: string;
  analogyDomain?: string;
  sessionsPerWeek?: number;
}

interface AuthState {
  users: Record<string, UserRecord>;   // keyed by email (lower-cased)
  currentUserId: string | null;
  lastError: string | null;

  register: (input: RegisterInput) => Promise<UserRecord>;
  login:    (email: string, password: string) => Promise<UserRecord>;
  logout:   () => void;
  updateProfile: (patch: Partial<UserRecord>) => void;

  currentUser: () => UserRecord | null;
  isAuthenticated: () => boolean;
}

// ── Crypto helpers ──────────────────────────────────────────────────

function randomSalt(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('');
}

async function hash(password: string, salt: string): Promise<string> {
  const data = new TextEncoder().encode(`${salt}:${password}`);
  const buf  = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(buf))
    .map(b => b.toString(16).padStart(2, '0')).join('');
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return (parts[0]![0]! + parts[parts.length - 1]![0]!).toUpperCase();
}

function slugId(email: string): string {
  const base = email.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
  // Prepend 'u_' so the id is recognisable and doesn't collide with seed ids.
  return `u_${base || Math.random().toString(36).slice(2, 8)}`;
}

// ── Store ───────────────────────────────────────────────────────────

export const useAuth = create<AuthState>()(
  persist(
    (set, get) => ({
      users: {},
      currentUserId: null,
      lastError: null,

      register: async (input) => {
        const email = input.email.trim().toLowerCase();
        if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
          const msg = 'Please enter a valid email.';
          set({ lastError: msg }); throw new Error(msg);
        }
        if (!input.name.trim()) {
          const msg = 'Please enter your name.';
          set({ lastError: msg }); throw new Error(msg);
        }
        if ((input.password ?? '').length < 6) {
          const msg = 'Password must be at least 6 characters.';
          set({ lastError: msg }); throw new Error(msg);
        }
        if (get().users[email]) {
          const msg = 'An account with that email already exists.';
          set({ lastError: msg }); throw new Error(msg);
        }

        const salt = randomSalt();
        const passwordHash = await hash(input.password, salt);
        const now = new Date().toISOString();
        const user: UserRecord = {
          id: slugId(email),
          email,
          name: input.name.trim(),
          initials: initialsOf(input.name),
          passwordHash, salt,
          tier: 'higher-sec',
          xpToday: 0,
          xpGoal: 1000,
          streak: 0,
          level: input.level ?? 'higher-sec',
          profession: input.profession ?? '',
          goal: input.goal ?? 'Build a strong conceptual foundation.',
          analogyDomain: input.analogyDomain ?? 'everyday life',
          sessionsPerWeek: input.sessionsPerWeek ?? 4,
          createdAt: now,
          updatedAt: now,
        };

        set(s => ({
          users: { ...s.users, [email]: user },
          currentUserId: user.id,
          lastError: null,
        }));
        return user;
      },

      login: async (rawEmail, password) => {
        const email = rawEmail.trim().toLowerCase();
        const u = get().users[email];
        if (!u) {
          const msg = 'No account with that email. Try registering first.';
          set({ lastError: msg }); throw new Error(msg);
        }
        const attempt = await hash(password, u.salt);
        if (attempt !== u.passwordHash) {
          const msg = 'Incorrect password.';
          set({ lastError: msg }); throw new Error(msg);
        }
        set({ currentUserId: u.id, lastError: null });
        return u;
      },

      logout: () => {
        // The cached backend sessionId + per-subject session map belong
        // to whoever was logged in — drop them so the next user starts
        // a fresh session instead of silently hijacking the old one.
        try {
          localStorage.removeItem('tutoros.sessionId');
          localStorage.removeItem('tutoros.sessionsBySubject');
        } catch { /* noop */ }
        set({ currentUserId: null });
      },

      updateProfile: (patch) => set(s => {
        const u = Object.values(s.users).find(x => x.id === s.currentUserId);
        if (!u) return {};
        const next: UserRecord = {
          ...u, ...patch,
          // Don't let the caller clobber security fields via updateProfile.
          id: u.id, email: u.email, passwordHash: u.passwordHash, salt: u.salt,
          initials: patch.name ? initialsOf(patch.name) : u.initials,
          updatedAt: new Date().toISOString(),
        };
        return { users: { ...s.users, [u.email]: next } };
      }),

      currentUser: () => {
        const s = get();
        if (!s.currentUserId) return null;
        return Object.values(s.users).find(u => u.id === s.currentUserId) ?? null;
      },
      isAuthenticated: () => get().currentUserId != null,
    }),
    {
      name: 'tutoros.auth',
      storage: createJSONStorage(() => localStorage),
      version: 1,
      // Note: password hashes live in localStorage. Fine for a demo;
      // never do this in a production app — use a real auth service.
      partialize: (s) => ({ users: s.users, currentUserId: s.currentUserId }),
    }
  )
);
