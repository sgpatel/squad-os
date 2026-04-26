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
  activeSubjectId: string | null;
  setActiveWorkspace: (id: string) => void;
  setActiveSubject: (id: string | null) => void;

  /**
   * Add a user-created subject to the current workspace. Returns the
   * new Subject so the caller can immediately set it active / navigate.
   */
  addSubject: (input: {
    name: string;
    blurb?: string;
    color?: string;
    icon?: string;
  }) => Subject;

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
      activeSubjectId: null,

      setActiveWorkspace: (id) => set({ activeWorkspaceId: id }),
      setActiveSubject: (id) => set({ activeSubjectId: id }),

      addSubject: (input) => {
        const id = `s_user_${Date.now().toString(36)}`;
        const subject: Subject = {
          id,
          name:     input.name.trim(),
          blurb:    (input.blurb ?? '').trim() || 'Your custom subject.',
          color:    input.color ?? '#6366f1',
          icon:     input.icon  ?? 'Book',
          courseIds: []
        };
        set((state) => {
          // Attach to the currently-active workspace so it actually renders
          // in the subject grid (workspaceSubjects filters by workspace).
          const workspaces = state.workspaces.map(ws =>
            ws.id === state.activeWorkspaceId
              ? { ...ws, subjectIds: [...ws.subjectIds, id] }
              : ws
          );
          return {
            subjects: [...state.subjects, subject],
            workspaces
          };
        });
        return subject;
      },

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
      version: 2,
      // Persist chosen workspace/subject + user-added subjects and their
      // workspace bindings. Seeded subjects re-hydrate from mockData on
      // boot; user-added ones persist via `subjects` + `workspaces` below.
      // We merge seed + persisted on rehydrate in `onRehydrateStorage`.
      partialize: (s) => ({
        activeWorkspaceId: s.activeWorkspaceId,
        activeSubjectId:   s.activeSubjectId,
        // Only the user-added subjects (id prefix "s_user_"):
        userSubjects: s.subjects.filter(x => x.id.startsWith('s_user_')),
        // Workspace subjectIds (so user-added ones stay linked):
        workspaceSubjectIds: Object.fromEntries(
          s.workspaces.map(w => [w.id, w.subjectIds])
        )
      }),
      merge: (persisted, current) => {
        const p = persisted as Partial<{
          activeWorkspaceId: string;
          activeSubjectId:   string | null;
          userSubjects:      Subject[];
          workspaceSubjectIds: Record<string, string[]>;
        }> | undefined;
        if (!p) return current;
        return {
          ...current,
          activeWorkspaceId: p.activeWorkspaceId ?? current.activeWorkspaceId,
          activeSubjectId:   p.activeSubjectId   ?? current.activeSubjectId,
          subjects: [
            ...current.subjects,
            ...(p.userSubjects ?? []).filter(
              us => !current.subjects.some(s => s.id === us.id)
            )
          ],
          workspaces: current.workspaces.map(w => ({
            ...w,
            subjectIds: p.workspaceSubjectIds?.[w.id] ?? w.subjectIds
          }))
        };
      }
    }
  )
);
