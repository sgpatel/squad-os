import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { seedPlan } from '@/lib/mockData';
import type { PlanItem } from '@/lib/types';

interface PlanState {
  items: PlanItem[];
  toggle: (id: string) => void;
  add: (item: Omit<PlanItem, 'id' | 'done'>) => void;
  remove: (id: string) => void;
  forDate: (isoDate: string) => PlanItem[];
}

export const usePlan = create<PlanState>()(
  persist(
    (set, get) => ({
      items: seedPlan,
      toggle: (id) => set(s => ({
        items: s.items.map(i => i.id === id ? { ...i, done: !i.done } : i)
      })),
      add: (item) => set(s => ({
        items: [...s.items, { ...item, id: `p_${Date.now().toString(36)}`, done: false }]
      })),
      remove: (id) => set(s => ({ items: s.items.filter(i => i.id !== id) })),
      forDate: (d) => get().items.filter(i => i.date === d).sort((a,b) =>
        (a.startTime ?? '').localeCompare(b.startTime ?? '')
      )
    }),
    { name: 'tutoros.plan', storage: createJSONStorage(() => localStorage), version: 1 }
  )
);
