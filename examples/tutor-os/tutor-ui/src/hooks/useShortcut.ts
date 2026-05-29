import { useEffect } from 'react';

/**
 * Bind a keyboard shortcut. Pass combos like `meta+k`, `ctrl+enter`, `escape`.
 * Modifier order doesn't matter; matches both meta and ctrl when you write `mod+`.
 */
export function useShortcut(combo: string, handler: (e: KeyboardEvent) => void): void {
  useEffect(() => {
    const parts = combo.toLowerCase().split('+').map(s => s.trim());
    const wantMod   = parts.includes('mod');
    const wantMeta  = parts.includes('meta');
    const wantCtrl  = parts.includes('ctrl');
    const wantShift = parts.includes('shift');
    const wantAlt   = parts.includes('alt');
    const key = parts.filter(p => !['mod','meta','ctrl','shift','alt'].includes(p))[0] ?? '';

    const onKey = (e: KeyboardEvent) => {
      const k = e.key.toLowerCase();
      if (k !== key && !(key === 'enter' && k === 'enter')) return;
      const modOK   = wantMod ? (e.metaKey || e.ctrlKey) : true;
      const metaOK  = wantMeta ? e.metaKey : true;
      const ctrlOK  = wantCtrl ? e.ctrlKey : true;
      const shiftOK = wantShift ? e.shiftKey : !e.shiftKey || wantShift === false;
      const altOK   = wantAlt ? e.altKey   : true;
      if (!modOK || !metaOK || !ctrlOK || !shiftOK || !altOK) return;
      handler(e);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [combo, handler]);
}
