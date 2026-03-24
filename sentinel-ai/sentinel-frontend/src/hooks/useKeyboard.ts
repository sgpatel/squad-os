import { useEffect } from "react";

interface Shortcut { key: string; ctrl?: boolean; meta?: boolean; shift?: boolean; action: () => void; }

export function useKeyboard(shortcuts: Shortcut[]) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      // Skip if typing in input/textarea
      const tag = (e.target as HTMLElement).tagName;
      if (tag === "INPUT" || tag === "TEXTAREA") return;
      for (const s of shortcuts) {
        const keyMatch  = e.key.toLowerCase() === s.key.toLowerCase();
        const ctrlMatch = !s.ctrl  || e.ctrlKey;
        const metaMatch = !s.meta  || e.metaKey;
        const shiftMatch= !s.shift || e.shiftKey;
        if (keyMatch && ctrlMatch && metaMatch && shiftMatch) {
          e.preventDefault();
          s.action();
          return;
        }
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [shortcuts]);
}