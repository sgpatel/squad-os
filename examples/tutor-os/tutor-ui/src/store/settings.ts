import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { ThemeName, ColorMode, Density } from '@/lib/types';

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

  setTheme:     (t: ThemeName) => void;
  setMode:      (m: ColorMode) => void;
  cycleMode:    () => void;
  setDensity:   (d: Density)   => void;
  setFontScale: (n: number)    => void;
}

export const useSettings = create<SettingsState>()(
  persist(
    (set) => ({
      theme: 'lumen',
      mode: null,
      density: 'comfortable',
      fontScale: 1,

      setTheme:     (theme) => set({ theme }),
      setMode:      (mode)  => set({ mode }),
      cycleMode:    ()      => set(s => ({
        mode: s.mode === null ? 'dark' : s.mode === 'dark' ? 'light' : null
      })),
      setDensity:   (density)   => set({ density }),
      setFontScale: (fontScale) => set({ fontScale })
    }),
    {
      name: 'tutoros.settings',
      storage: createJSONStorage(() => localStorage),
      version: 1
    }
  )
);
