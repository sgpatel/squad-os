import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { generateQuiz, type GenerateParams } from '@/lib/generate';
import type { Quiz, QuizQuestion } from '@/lib/types';

/**
 * Quiz store — holds quizzes generated on demand by the QuizAgent.
 *
 * Replaces the hardcoded `seedQuizzes` import that the Quiz page used to
 * read from. Persisted to localStorage so a refresh keeps the learner
 * inside an in-progress quiz.
 */
interface QuizState {
  quizzes: Record<string, Quiz>;
  questions: Record<string, QuizQuestion>;
  isGenerating: boolean;
  lastError: string | null;

  generate: (params: GenerateParams) => Promise<Quiz>;
  get: (id: string) => Quiz | undefined;
  questionsFor: (id: string) => QuizQuestion[];
  remove: (id: string) => void;
  recent: (n?: number) => Quiz[];
}

export const useQuizStore = create<QuizState>()(
  persist(
    (set, get) => ({
      quizzes: {},
      questions: {},
      isGenerating: false,
      lastError: null,

      generate: async (params) => {
        set({ isGenerating: true, lastError: null });
        try {
          const { quiz, questions } = await generateQuiz(params);
          set(s => ({
            quizzes: { ...s.quizzes, [quiz.id]: quiz },
            questions: {
              ...s.questions,
              ...Object.fromEntries(questions.map(q => [q.id, q])),
            },
            isGenerating: false,
          }));
          return quiz;
        } catch (e) {
          set({
            isGenerating: false,
            lastError: e instanceof Error ? e.message : String(e),
          });
          throw e;
        }
      },

      get: (id) => get().quizzes[id],
      questionsFor: (id) => {
        const quiz = get().quizzes[id];
        if (!quiz) return [];
        return quiz.questionIds
          .map(qid => get().questions[qid])
          .filter((q): q is QuizQuestion => Boolean(q));
      },
      remove: (id) => set(s => {
        const next = { ...s.quizzes };
        delete next[id];
        return { quizzes: next };
      }),
      recent: (n = 5) => {
        return Object.values(get().quizzes)
          .sort((a, b) => b.id.localeCompare(a.id))
          .slice(0, n);
      },
    }),
    {
      name: 'tutoros.quizzes',
      storage: createJSONStorage(() => localStorage),
      version: 1,
      partialize: (s) => ({ quizzes: s.quizzes, questions: s.questions }),
    }
  )
);
