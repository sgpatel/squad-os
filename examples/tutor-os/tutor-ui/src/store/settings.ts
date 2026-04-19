import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { ThemeName, ColorMode, Density } from '@/lib/types';

/**
 * AssistMode — how heavy the tutor pipeline runs per message.
 *
 *   'agentic' → Full multi-agent pipeline (guardian → diagnostic/planner →
 *               content → debate → tutor → output check). Streams stage
 *               events so the reveal panel + debate round animate.
 *               Higher latency, citations + debate available.
 *
 *   'direct'  → Slim path: input guardrail → DirectTutorAgent (token-
 *               streamed) → output guardrail. No debate, no content fetch,
 *               no diagnostic. Lower latency, no debate panel.
 *
 * The mode is per-message and chosen by the learner via a topbar toggle.
 * Persisted so it survives reloads.
 */
export type AssistMode = 'agentic' | 'direct';

/**
 * Settings store — persisted to localStorage.
 *
 * Held separately from data stores because (a) it changes the entire
 * page paint via :root attributes, (b) it's the only thing we want
 * persisted on a fresh load (the rest is mock data, regenerated).
 */
interface SettingsState {
  theme: ThemeName;
  mode: ColorMode;        // null = follow system
  density: Density;
  fontScale: number;       // 1 = default, a11y users can bump up
  assistMode: AssistMode;

  setTheme:      (t: ThemeName) => void;
  setMode:       (m: ColorMode) => void;
  cycleMode:     () => void;
  setDensity:    (d: Density)   => void;
  setFontScale:  (n: number)    => void;
  setAssistMode: (m: AssistMode) => void;
  toggleAssistMode: () => void;
}

export const useSettings = create<SettingsState>()(
  persist(
    (set) => ({
      theme: 'lumen',
      mode: null,
      density: 'comfortable',
      fontScale: 1,
      assistMode: 'agentic',

      setTheme:      (theme) => set({ theme }),
      setMode:       (mode)  => set({ mode }),
      cycleMode:     ()      => set(s => ({
        mode: s.mode === null ? 'dark' : s.mode === 'dark' ? 'light' : null
      })),
      setDensity:    (density)   => set({ density }),
      setFontScale:  (fontScale) => set({ fontScale }),
      setAssistMode: (assistMode) => set({ assistMode }),
      toggleAssistMode: () => set(s => ({
        assistMode: s.assistMode === 'agentic' ? 'direct' : 'agentic'
      }))
    }),
    {
      name: 'tutoros.settings',
      storage: createJSONStorage(() => localStorage),
      // New fields (e.g. assistMode) are merged with store defaults on rehydrate,
      // so we don't need to bump the version and wipe existing user preferences.
      version: 1
    }
  )
);
