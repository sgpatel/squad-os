import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { seedUser, seedWorkspaces, seedSubjects, seedCourses, seedChapters, seedTopics, seedConcepts } from '@/lib/mockData';
import type { User, Workspace, Subject, Course, Chapter, Topic, Concept } from '@/lib/types';

/**
 * Workspace store — currently-active workspace + lookup helpers.
 *
 * The data itself is mock-seeded once. In a real app this is where
 * we'd `useQuery` against an API; for the example we just hold it
 * in memory and persist `activeWorkspaceId`.
 */
interface WorkspaceState {
  user: User;
  workspaces: Workspace[];
  subjects: Subject[];
  courses: Course[];
  chapters: Chapter[];
  topics: Topic[];
  concepts: Concept[];

  activeWorkspaceId: string;
  setActiveWorkspace: (id: string) => void;

  // Lookups
  getSubject: (id: string) => Subject | undefined;
  getCourse:  (id: string) => Course  | undefined;
  getChapter: (id: string) => Chapter | undefined;
  getTopic:   (id: string) => Topic   | undefined;
  getConcept: (id: string) => Concept | undefined;

  // Derived collections for the active workspace
  workspaceSubjects: () => Subject[];
}

export const useWorkspace = create<WorkspaceState>()(
  persist(
    (set, get) => ({
      user: seedUser,
      workspaces: seedWorkspaces,
      subjects:   seedSubjects,
      courses:    seedCourses,
      chapters:   seedChapters,
      topics:     seedTopics,
      concepts:   seedConcepts,

      activeWorkspaceId: seedWorkspaces[0]!.id,

      setActiveWorkspace: (id) => set({ activeWorkspaceId: id }),

      getSubject: (id) => get().subjects.find(s => s.id === id),
      getCourse:  (id) => get().courses.find(c  => c.id === id),
      getChapter: (id) => get().chapters.find(c => c.id === id),
      getTopic:   (id) => get().topics.find(t   => t.id === id),
      getConcept: (id) => get().concepts.find(c => c.id === id),

      workspaceSubjects: () => {
        const { activeWorkspaceId, workspaces, subjects } = get();
        const ws = workspaces.find(w => w.id === activeWorkspaceId);
        if (!ws) return subjects;
        return subjects.filter(s => ws.subjectIds.includes(s.id));
      }
    }),
    {
      name: 'tutoros.workspace',
      storage: createJSONStorage(() => localStorage),
      version: 1,
      // We only persist the chosen workspace; data re-hydrates from mockData.
      partialize: (s) => ({ activeWorkspaceId: s.activeWorkspaceId })
    }
  )
);
