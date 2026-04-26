/* ─────────────────────────────────────────────────────────────────
 * generate.ts — create quizzes and flashcards on demand.
 *
 * Single entry point so the UI never has to branch on LIVE_BACKEND.
 * Live path → api.generateQuiz (QuizAgent in tutor-core).
 * Mock path → a tiny local synthesiser so the "Generate" buttons still
 * feel real in demo mode without spinning up the backend.
 * ───────────────────────────────────────────────────────────────── */

import {
  api, LIVE_BACKEND, ApiError, ensureBackendSession,
  type BackendPracticeQuestion,
} from './api';
import type { Quiz, QuizQuestion, Flashcard } from './types';

// ── Public API ──────────────────────────────────────────────────────

export interface GenerateParams {
  learnerId: string;
  subject: string;
  topic: string;
  count: number;
  difficulty?: 'EASY' | 'MEDIUM' | 'HARD' | 'MIXED';
}

/**
 * Generate a quiz. Returns a fully-formed frontend Quiz + the expanded
 * QuizQuestion list (so the caller can store both without re-parsing).
 */
export async function generateQuiz(
  params: GenerateParams
): Promise<{ quiz: Quiz; questions: QuizQuestion[] }> {
  const { learnerId, subject, topic, count, difficulty = 'MEDIUM' } = params;

  if (LIVE_BACKEND) {
    // SessionManager.quiz requires an active session — bootstrap one if
    // we don't already have it cached. If the backend forgot us (e.g.
    // restart), a Session-not-found comes back as 500; we invalidate
    // the cached sessionId and retry exactly once.
    const res = await callWithSessionRetry(
      learnerId, subject,
      () => api.generateQuiz(learnerId, subject, { topic, difficulty, questionCount: count })
    );
    const backendQs = parseQuestionsJson(res.questionsJson);
    const questions = backendQs
      .map((q, i) => backendToQuizQuestion(q, `${res.quizId}_q${i + 1}`))
      .filter(q => q.prompt && q.prompt.trim().length > 0);
    if (questions.length === 0) {
      // eslint-disable-next-line no-console
      console.error('[generateQuiz] no usable questions', {
        rawQuestionsJson: res.questionsJson,
        parsedCount: backendQs.length,
        firstParsed: backendQs[0],
      });
      throw new Error(
        'Quiz generation returned no usable questions. ' +
        'The model output may be malformed — please try again.'
      );
    }
    const quiz: Quiz = {
      id: res.quizId,
      title: `${res.topic || topic} · ${res.difficulty}`,
      questionIds: questions.map(q => q.id),
      estMinutes: res.timeLimitMinutes || Math.max(4, count * 2),
    };
    return { quiz, questions };
  }

  // Mock fallback — synthesise a quiz locally.
  const id = `qz_gen_${Date.now().toString(36)}`;
  const questions = mockQuestions(topic, count, id);
  const quiz: Quiz = {
    id,
    title: `${topic} · ${difficulty}`,
    questionIds: questions.map(q => q.id),
    estMinutes: Math.max(4, count * 2),
  };
  return { quiz, questions };
}

/**
 * Generate flashcards by asking the quiz pipeline for short questions,
 * then distilling each into a Q/A card. Mock fallback mirrors the shape.
 */
export async function generateFlashcards(
  params: GenerateParams
): Promise<Flashcard[]> {
  const { learnerId, subject, topic, count, difficulty = 'MEDIUM' } = params;
  const now = new Date().toISOString();

  if (LIVE_BACKEND) {
    const res = await callWithSessionRetry(
      learnerId, subject,
      () => api.generateQuiz(learnerId, subject, { topic, difficulty, questionCount: count })
    );
    const backendQs = parseQuestionsJson(res.questionsJson);
    return backendQs.map((q, i) => ({
      id: `f_gen_${res.quizId}_${i + 1}`,
      conceptId: slugify(q.conceptTag ?? topic),
      q: q.question,
      a: q.answer || (q.workedSolution ?? ''),
      ease: 2.5,
      intervalDays: 1,
      due: now,
    }));
  }

  return Array.from({ length: count }).map((_, i) => ({
    id: `f_gen_${Date.now().toString(36)}_${i}`,
    conceptId: slugify(topic),
    q: `(${topic}) Question ${i + 1}`,
    a: `Model answer for "${topic}" #${i + 1}. Replace with a real tutor-generated card by enabling LIVE_BACKEND.`,
    ease: 2.5,
    intervalDays: 1,
    due: now,
  }));
}

// ── Internals ──────────────────────────────────────────────────────

/**
 * Wrap a backend call with "make sure a session exists first"; if it
 * fails in a way that smells like a missing session (500 containing
 * "Session not found", 404, etc.), invalidate the cache and retry once.
 */
async function callWithSessionRetry<T>(
  learnerId: string,
  subject: string,
  op: () => Promise<T>
): Promise<T> {
  await ensureBackendSession(learnerId, subject);
  try {
    return await op();
  } catch (e) {
    if (looksLikeMissingSession(e)) {
      // Clear our cached sessionId for this pair so the next call
      // actually hits /session/start instead of replaying the ghost id.
      try {
        const raw = localStorage.getItem('tutoros.sessionsBySubject');
        if (raw) {
          const m = JSON.parse(raw) as Record<string, string>;
          delete m[`${learnerId}:${subject}`];
          localStorage.setItem('tutoros.sessionsBySubject', JSON.stringify(m));
        }
      } catch { /* noop */ }
      await ensureBackendSession(learnerId, subject);
      return await op();
    }
    throw e;
  }
}

function looksLikeMissingSession(e: unknown): boolean {
  if (!(e instanceof ApiError)) return false;
  if (e.status === 404) return true;
  const body = e.body as { message?: string; text?: string } | null | undefined;
  const msg = (body?.message || body?.text || '').toString().toLowerCase();
  return e.status >= 500 && (msg.includes('session not found') || msg.includes('illegalargument'));
}


function parseQuestionsJson(raw: string): BackendPracticeQuestion[] {
  if (!raw) return [];

  const tryParse = (s: string): BackendPracticeQuestion[] | null => {
    try {
      const parsed = JSON.parse(s);
      return Array.isArray(parsed) ? (parsed as BackendPracticeQuestion[]) : null;
    } catch {
      return null;
    }
  };

  // Attempt 1: as-is.
  let out = tryParse(raw);
  if (out) return out;

  // Attempt 2: strip ```json ... ``` fences some models emit.
  const stripped = raw.replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/, '').trim();
  out = tryParse(stripped);
  if (out) return out;

  // Attempt 3: the squad-core StructuredOutputParser doesn't JSON-decode
  // string-typed fields, so questionsJson arrives with literal \" escape
  // sequences in places where bare " is expected. Undo one level of
  // backslash-escaping and retry.
  const unescaped = stripped
    .replace(/\\"/g, '"')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t')
    .replace(/\\\\/g, '\\');
  out = tryParse(unescaped);
  if (out) return out;

  return [];
}

export function backendToQuizQuestion(
  q: BackendPracticeQuestion,
  id: string
): QuizQuestion {
  // Backend "should" use { question, type, options, answer, conceptTag } per
  // PracticeQuestion.java, but LLMs occasionally emit synonyms. Be lenient:
  // accept questionText/questionType/modelAnswer/concept and array options.
  const anyQ = q as unknown as Record<string, unknown>;
  const prompt = (q.question ?? anyQ.questionText ?? '') as string;
  const rawType = (q.type ?? anyQ.questionType ?? 'SHORT_ANSWER') as string;
  const rawAnswer = (q.answer ?? anyQ.modelAnswer ?? '') as string;
  const conceptTag = (q.conceptTag ?? anyQ.concept ?? '') as string;
  const rawOptions = q.options ?? anyQ.options;

  const choices: string[] = Array.isArray(rawOptions)
    ? (rawOptions as unknown[]).map(s => String(s).trim()).filter(Boolean)
    : typeof rawOptions === 'string'
      ? rawOptions.split('|').map(s => s.trim()).filter(Boolean)
      : [];

  const kind: QuizQuestion['kind'] =
    rawType === 'MULTIPLE_CHOICE' ? 'mcq' : 'short';
  if (kind === 'mcq') {
    const correctIdx = Math.max(0,
      choices.findIndex(c => c.toLowerCase() === rawAnswer.toLowerCase()));
    return {
      id,
      kind: 'mcq',
      prompt,
      choices: choices.length ? choices : ['Option A', 'Option B', 'Option C', 'Option D'],
      correct: [correctIdx],
      conceptId: slugify(conceptTag),
      explanation: q.workedSolution || rawAnswer || '',
    };
  }
  return {
    id,
    kind: 'short',
    prompt,
    ideal: rawAnswer,
    conceptId: slugify(conceptTag),
    explanation: q.workedSolution || rawAnswer || '',
  };
}

function slugify(s: string): string {
  return (s || 'concept').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '') || 'concept';
}

function mockQuestions(topic: string, count: number, quizId: string): QuizQuestion[] {
  const n = Math.max(1, Math.min(10, count));
  const out: QuizQuestion[] = [];
  for (let i = 0; i < n; i++) {
    const id = `${quizId}_q${i + 1}`;
    // Alternate mcq and short so the UI exercises both paths.
    if (i % 2 === 0) {
      out.push({
        id, kind: 'mcq',
        prompt: `${topic}: which of the following is most accurate? (#${i + 1})`,
        choices: [
          `A correct-sounding answer about ${topic}.`,
          `A plausible-but-wrong answer.`,
          `A clearly-wrong answer.`,
          `A definition from a different topic.`
        ],
        correct: [0],
        conceptId: slugify(topic),
        explanation: `Choice A is correct because it matches the core definition of ${topic}.`,
      });
    } else {
      out.push({
        id, kind: 'short',
        prompt: `Explain one key property of ${topic} in 1–2 sentences. (#${i + 1})`,
        ideal: `A good answer references the main mechanism or outcome in ${topic}.`,
        conceptId: slugify(topic),
        explanation: `Look for: mechanism, outcome, and a correct cause/effect link for ${topic}.`,
      });
    }
  }
  return out;
}
