import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { seedNotes } from '@/lib/mockData';
import type { Note } from '@/lib/types';

interface NotesState {
  notes: Note[];
  create: (partial?: Partial<Note>) => Note;
  update: (id: string, patch: Partial<Note>) => void;
  remove: (id: string) => void;
  get:    (id: string) => Note | undefined;
}

export const useNotes = create<NotesState>()(
  persist(
    (set, get) => ({
      notes: seedNotes,
      create: (partial) => {
        const now = new Date().toISOString();
        const note: Note = {
          id: `n_${Date.now().toString(36)}`,
          title: 'Untitled note',
          body: '',
          tags: [],
          createdAt: now,
          updatedAt: now,
          ...partial
        };
        set(s => ({ notes: [note, ...s.notes] }));
        return note;
      },
      update: (id, patch) => set(s => ({
        notes: s.notes.map(n =>
          n.id === id ? { ...n, ...patch, updatedAt: new Date().toISOString() } : n
        )
      })),
      remove: (id) => set(s => ({ notes: s.notes.filter(n => n.id !== id) })),
      get:    (id) => get().notes.find(n => n.id === id)
    }),
    { name: 'tutoros.notes', storage: createJSONStorage(() => localStorage), version: 1 }
  )
);
