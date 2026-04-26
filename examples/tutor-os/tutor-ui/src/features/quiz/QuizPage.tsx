import { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Check, Loader2 } from 'lucide-react';
import { useQuizStore } from '@/store/quiz';
import { useWorkspace } from '@/store/workspace';
import { useAuth } from '@/store/auth';
import { Button } from '@/components/ui/Button';
import { Textarea } from '@/components/ui/Input';
import { Tag, SectionLabel } from '@/components/ui/Misc';
import { fmtMinutes } from '@/lib/format';
import { LIVE_BACKEND, api, DEMO_LEARNER_ID } from '@/lib/api';
import type { QuizQuestion } from '@/lib/types';
import { QuizHome } from './QuizHome';

type AnswerState =
  | { kind: 'unanswered' }
  | { kind: 'mcq';   choice: number }
  | { kind: 'multi'; choices: number[] }
  | { kind: 'short'; text: string };

interface Result {
  correct: boolean;
  /** rubric for short-answer */
  rubric?: { claim: number; evidence: number; clarity: number };
  explanation?: string;
}

/**
 * Quiz Player — supports MCQ, multi-select, and short-answer.
 *
 * Questions come from the quiz store (populated by `generate()`, which
 * routes through the backend QuizAgent when LIVE_BACKEND is set, or a
 * local mock generator otherwise).
 *
 * Short-answer grading uses the backend AssessmentAgent when live; in
 * mock mode a lightweight keyword-overlap heuristic stands in so the
 * rubric shape still lights up.
 */
export function QuizPage() {
  const { quizId } = useParams<{ quizId: string }>();
  const navigate = useNavigate();
  // IMPORTANT: subscribe to the stable dicts and derive the questions
  // array locally. Selecting s.questionsFor(id) returns a fresh array
  // every render → zustand's snapshot equality fails → infinite loop.
  const quiz         = useQuizStore(s => (quizId ? s.quizzes[quizId] : undefined));
  const questionDict = useQuizStore(s => s.questions);
  const questions = useMemo(
    () => (quiz ? quiz.questionIds.map(id => questionDict[id]).filter(Boolean) as QuizQuestion[] : []),
    [quiz, questionDict]
  );
  const activeSubjectId = useWorkspace(s => s.activeSubjectId);
  const getSubject      = useWorkspace(s => s.getSubject);
  const learnerId = useAuth(s => s.currentUserId) ?? DEMO_LEARNER_ID;

  const [idx, setIdx] = useState(0);
  const [answers, setAnswers] = useState<Record<string, AnswerState>>({});
  const [results, setResults] = useState<Record<string, Result>>({});
  const [done, setDone] = useState(false);
  const [grading, setGrading] = useState(false);

  useEffect(() => { setIdx(0); setAnswers({}); setResults({}); setDone(false); }, [quizId]);

  // No id in the URL (or id points at nothing) → show the quiz home.
  if (!quizId) return <QuizHome />;
  if (!quiz)   return <QuizHome missingId={quizId} />;
  if (questions.length === 0) return <QuizHome missingId={quizId} />;

  const q = questions[idx]!;
  const ans = answers[q.id] ?? ({ kind: 'unanswered' } as AnswerState);
  const res = results[q.id];

  const submit = async () => {
    const r = await gradeAnswer(q, ans, {
      learnerId,
      subject: (activeSubjectId && getSubject(activeSubjectId)?.name) || quiz.title.split(' · ')[0] || 'General',
      setGrading,
    });
    if (r) setResults({ ...results, [q.id]: r });
  };

  const next = () => {
    if (idx < questions.length - 1) setIdx(idx + 1);
    else setDone(true);
  };

  const correctCount = Object.values(results).filter(r => r.correct).length;

  if (done) {
    return (
      <div className="page-react">
        <header className="page-header">
          <div><h1>{quiz.title} — results</h1>
          <p>{correctCount} / {questions.length} correct</p></div>
          <Button onClick={() => navigate('/quiz')}>Create another</Button>
        </header>
        <div className="card">
          <SectionLabel>Where you struggled</SectionLabel>
          {questions.filter(qq => !results[qq.id]?.correct).map(qq => (
            <div key={qq.id} style={{ marginTop: 'var(--space-3)' }}>
              <p style={{ fontWeight: 500 }}>{qq.prompt}</p>
              <p className="small muted">{results[qq.id]?.explanation ?? qq.explanation}</p>
            </div>
          ))}
          {questions.every(qq => results[qq.id]?.correct) && (
            <p className="muted">Nothing to revisit — nice work.</p>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="page-react">
      <header className="page-header">
        <div><h1>{quiz.title}</h1>
        <p>~{fmtMinutes(quiz.estMinutes)} · question {idx + 1} of {questions.length}</p></div>
      </header>

      <div className="quiz">
        <div className="quiz__progress">
          {questions.map((qq, i) => {
            const r = results[qq.id];
            const state = i === idx ? 'current' : r ? (r.correct ? 'done' : 'wrong') : 'pending';
            return <span key={qq.id} className="quiz__progress-pip" data-state={state} />;
          })}
        </div>

        <p className="quiz__q">{q.prompt}</p>

        {q.kind === 'mcq' && q.choices && (
          <div className="quiz__choices">
            {q.choices.map((c, i) => {
              const selected = ans.kind === 'mcq' && ans.choice === i;
              const correct  = res && q.correct?.[0] === i;
              const wrong    = res && selected && q.correct?.[0] !== i;
              const cls = ['quiz__choice',
                selected && !res && 'is-selected',
                correct && 'is-correct',
                wrong && 'is-wrong'
              ].filter(Boolean).join(' ');
              return (
                <button key={i} className={cls}
                  onClick={() => !res && setAnswers({ ...answers, [q.id]: { kind: 'mcq', choice: i } })}>
                  <span className="quiz__choice-key">{String.fromCharCode(65 + i)}</span>
                  <span>{c}</span>
                </button>
              );
            })}
          </div>
        )}

        {q.kind === 'multi' && q.choices && (
          <div className="quiz__choices">
            {q.choices.map((c, i) => {
              const sel = ans.kind === 'multi' && ans.choices.includes(i);
              const correct = res && q.correct?.includes(i);
              const wrong   = res && sel && !q.correct?.includes(i);
              const cls = ['quiz__choice',
                sel && !res && 'is-selected',
                correct && 'is-correct',
                wrong && 'is-wrong'
              ].filter(Boolean).join(' ');
              return (
                <button key={i} className={cls}
                  onClick={() => {
                    if (res) return;
                    const cur = ans.kind === 'multi' ? ans.choices : [];
                    const nextSel = cur.includes(i) ? cur.filter(x => x !== i) : [...cur, i];
                    setAnswers({ ...answers, [q.id]: { kind: 'multi', choices: nextSel } });
                  }}>
                  <span className="quiz__choice-key" style={{ borderRadius: 'var(--radius-pill)' }}>
                    {sel ? <Check size={12} /> : ''}
                  </span>
                  <span>{c}</span>
                </button>
              );
            })}
          </div>
        )}

        {q.kind === 'short' && (
          <Textarea
            rows={4}
            placeholder="Type a 1–2 sentence answer."
            value={ans.kind === 'short' ? ans.text : ''}
            disabled={!!res || grading}
            onChange={(e) => setAnswers({ ...answers, [q.id]: { kind: 'short', text: e.target.value } })}
          />
        )}

        {res && (
          <div className="quiz__rubric mt-5">
            {res.rubric ? (
              <>
                <div className="quiz__rubric-item">
                  <span className="quiz__rubric-label">Claim</span>
                  <span className="quiz__rubric-value">{(res.rubric.claim * 100).toFixed(0)}</span>
                </div>
                <div className="quiz__rubric-item">
                  <span className="quiz__rubric-label">Evidence</span>
                  <span className="quiz__rubric-value">{(res.rubric.evidence * 100).toFixed(0)}</span>
                </div>
                <div className="quiz__rubric-item">
                  <span className="quiz__rubric-label">Clarity</span>
                  <span className="quiz__rubric-value">{(res.rubric.clarity * 100).toFixed(0)}</span>
                </div>
              </>
            ) : (
              <div style={{ gridColumn: '1 / -1', display: 'flex', gap: 12, alignItems: 'center' }}>
                {res.correct
                  ? <Tag kind="success">Correct</Tag>
                  : <Tag kind="danger">Wrong</Tag>}
                <span className="small muted">{res.explanation ?? q.explanation}</span>
              </div>
            )}
            {res.rubric && (
              <p className="small muted" style={{ gridColumn: '1 / -1', marginTop: 4 }}>
                {res.explanation ?? q.explanation}
              </p>
            )}
          </div>
        )}

        <div className="row mt-5" style={{ justifyContent: 'flex-end' }}>
          {!res && (
            <Button variant="primary" onClick={submit}
              disabled={ans.kind === 'unanswered' || grading}>
              {grading
                ? <><Loader2 size={14} className="spin" /> Grading…</>
                : 'Submit'}
            </Button>
          )}
          {res && <Button variant="primary" onClick={next}>{idx === questions.length - 1 ? 'Finish' : 'Next'}</Button>}
        </div>
      </div>
    </div>
  );
}

// ── Grading ─────────────────────────────────────────────────────────

async function gradeAnswer(
  q: QuizQuestion,
  ans: AnswerState,
  ctx: { learnerId: string; subject: string; setGrading: (b: boolean) => void }
): Promise<Result | null> {
  if (q.kind === 'mcq' && ans.kind === 'mcq') {
    return { correct: q.correct?.[0] === ans.choice, explanation: q.explanation };
  }
  if (q.kind === 'multi' && ans.kind === 'multi') {
    const want = new Set(q.correct ?? []);
    const got  = new Set(ans.choices);
    const correct = want.size === got.size && [...want].every(i => got.has(i));
    return { correct, explanation: q.explanation };
  }
  if (q.kind === 'short' && ans.kind === 'short') {
    if (LIVE_BACKEND) {
      ctx.setGrading(true);
      try {
        const sessionId = `${ctx.learnerId}:${ctx.subject}`;
        const { feedback } = await api.submitAnswer(
          sessionId,
          {
            question: q.prompt,
            answer: q.ideal ?? '',
            conceptTag: q.conceptId,
            type: 'SHORT_ANSWER',
            difficulty: 'MEDIUM',
            bloomsLevel: 'APPLY',
            marks: 1,
          },
          ans.text,
          1
        );
        const score01 = Math.max(0, Math.min(1, (feedback.score ?? 0) / 100));
        return {
          correct: feedback.correct ?? score01 >= 0.7,
          rubric: {
            claim: score01,
            evidence: score01 * 0.9,
            clarity: Math.max(0, Math.min(1, ans.text.split(/\s+/).filter(Boolean).length / 25)),
          },
          explanation: [feedback.correctParts, feedback.incorrectParts, feedback.hint, feedback.modelAnswer]
            .filter(Boolean).join(' · ') || q.explanation,
        };
      } catch (e) {
        // Fall back to heuristic so the quiz doesn't get stuck.
        return mockShortAnswer(ans.text, q, e instanceof Error ? e.message : String(e));
      } finally {
        ctx.setGrading(false);
      }
    }
    return mockShortAnswer(ans.text, q);
  }
  return null;
}

function mockShortAnswer(text: string, q: QuizQuestion, warn?: string): Result {
  const ideal = q.ideal ?? '';
  const norm = (s: string) => new Set(s.toLowerCase().match(/[a-z]+/g) ?? []);
  const a = norm(text); const i = norm(ideal);
  const overlap = [...a].filter(t => i.has(t)).length;
  const recall = i.size === 0 ? 0 : Math.min(1, overlap / Math.max(3, i.size * 0.4));
  const length = Math.min(1, text.split(/\s+/).filter(Boolean).length / 25);
  return {
    correct: recall >= 0.6,
    rubric: { claim: recall, evidence: recall * 0.9, clarity: length },
    explanation: warn ? `(offline grading — ${warn}) ${q.explanation}` : q.explanation,
  };
}
