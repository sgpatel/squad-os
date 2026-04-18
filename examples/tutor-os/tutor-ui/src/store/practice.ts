import { create } from 'zustand';
import { seedFlashcards } from '@/lib/mockData';
import type { Flashcard } from '@/lib/types';

/** Reviewer rating for SM-2-style scheduling. */
export type Grade = 'again' | 'hard' | 'good' | 'easy';

interface PracticeState {
  cards: Flashcard[];
  /** Index of current card in `dueQueue` */
  cursor: number;
  flipped: boolean;

  flip: () => void;
  next: () => void;
  grade: (g: Grade) => void;

  /** All cards whose `due` timestamp is now-or-earlier */
  dueQueue: () => Flashcard[];
}

export const usePractice = create<PracticeState>()((set, get) => ({
  cards: seedFlashcards,
  cursor: 0,
  flipped: false,

  flip: () => set(s => ({ flipped: !s.flipped })),
  next: () => set(s => ({
    cursor: Math.min(s.cursor + 1, get().dueQueue().length),
    flipped: false
  })),
  grade: (g) => {
    const queue = get().dueQueue();
    const card = queue[get().cursor];
    if (!card) return;
    set(s => ({
      cards: s.cards.map(c => c.id === card.id ? scheduleSM2(c, g) : c)
    }));
    get().next();
  },

  dueQueue: () => {
    const now = Date.now();
    return get().cards
      .filter(c => new Date(c.due).getTime() <= now)
      .sort((a, b) => new Date(a.due).getTime() - new Date(b.due).getTime());
  }
}));

/** Light SM-2 implementation — keeps the example honest about what SRS does. */
function scheduleSM2(c: Flashcard, g: Grade): Flashcard {
  const ease = Math.max(1.3,
    g === 'again' ? c.ease - 0.2 :
    g === 'hard'  ? c.ease - 0.15:
    g === 'good'  ? c.ease       :
                    c.ease + 0.1
  );
  const interval =
    g === 'again' ? 1 :
    g === 'hard'  ? Math.max(1, Math.round(c.intervalDays * 1.2)) :
    g === 'good'  ? Math.round(c.intervalDays * ease) :
                    Math.round(c.intervalDays * ease * 1.3);
  const due = new Date(Date.now() + interval * 86400e3).toISOString();
  return { ...c, ease, intervalDays: interval, due };
}
