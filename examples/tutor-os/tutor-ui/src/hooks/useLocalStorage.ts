import { useEffect, useState } from 'react';

/**
 * useState that persists to localStorage. For one-off bits of UI state
 * that don't deserve a Zustand store of their own (e.g. canvas brush color).
 */
export function useLocalStorage<T>(key: string, initial: T): [T, (v: T) => void] {
  const [val, setVal] = useState<T>(() => {
    try {
      const raw = localStorage.getItem(key);
      return raw == null ? initial : (JSON.parse(raw) as T);
    } catch { return initial; }
  });
  useEffect(() => {
    try { localStorage.setItem(key, JSON.stringify(val)); } catch { /* quota / private mode */ }
  }, [key, val]);
  return [val, setVal];
}
