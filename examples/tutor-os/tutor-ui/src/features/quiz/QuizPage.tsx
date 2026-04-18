import { useMemo, useState } from 'react';
import { useParams, Navigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { seedQuestions, seedQuizzes } from '@/lib/mockData';
import { Button } from '@/components/ui/Button';
import { Textarea } from '@/components/ui/Input';
import { Tag, SectionLabel } from '@/components/ui/Misc';
import { fmtMinutes } from '@/lib/format';

type AnswerState =
  | { kind: 'unanswered' }
  | { kind: 'mcq';   choice: number }
  | { kind: 'multi'; choices: number[] }
  | { kind: 'short'; text: string };

interface Result {
  correct: boolean;
  /** rubric for short-answer */
  rubric?: { claim: number; evidence: number; clarity: number };
}

/**
 * Quiz Player — supports MCQ, multi-select, and short-answer.
 *
 * For short answers we mock a rubric ("claim · evidence · clarity")
 * — exactly the shape an assessment agent would emit. In production
 * the agent fills these scores; here a mock heuristic does it.
 */
export function QuizPage() {
  const { quizId } = useParams<{ quizId: string }>();
  const quiz = seedQuizzes.find(q => q.id === quizId);
  if (!quiz) return <Navigate to="/" replace />;

  const questions = useMemo(
    () => quiz.questionIds.map(id => seedQuestions.find(q => q.id === id)).filter(Boolean) as typeof seedQuestions,
    [quiz]
  );

  const [idx, setIdx] = useState(0);
  const [answers, setAnswers] = useState<Record<string, AnswerState>>({});
  const [results, setResults] = useState<Record<string, Result>>({});
  const [done, setDone] = useState(false);

  const q = questions[idx]!;
  const ans = answers[q.id] ?? ({ kind: 'unanswered' } as AnswerState);
  const res = results[q.id];

  const submit = () => {
    let result: Result;
    if (q.kind === 'mcq' && ans.kind === 'mcq') {
      result = { correct: q.correct?.[0] === ans.choice };
    } else if (q.kind === 'multi' && ans.kind === 'multi') {
      const want = new Set(q.correct ?? []);
      const got  = new Set(ans.choices);
      result = { correct: want.size === got.size && [...want].every(i => got.has(i)) };
    } else if (q.kind === 'short' && ans.kind === 'short') {
      result = { correct: ans.text.trim().length > 30, rubric: gradeShort(ans.text, q.ideal ?? '') };
    } else {
      return;
    }
    setResults({ ...results, [q.id]: result });
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
        </header>
        <div className="card">
          <SectionLabel>Where you struggled</SectionLabel>
          {questions.filter(q => !results[q.id]?.correct).map(q => (
            <div key={q.id} style={{ marginTop: 'var(--space-3)' }}>
              <p style={{ fontWeight: 500 }}>{q.prompt}</p>
              <p className="small muted">{q.explanation}</p>
            </div>
          ))}
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
            disabled={!!res}
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
                <span className="small muted">{q.explanation}</span>
              </div>
            )}
            {res.rubric && (
              <p className="small muted" style={{ gridColumn: '1 / -1', marginTop: 4 }}>{q.explanation}</p>
            )}
          </div>
        )}

        <div className="row mt-5" style={{ justifyContent: 'flex-end' }}>
          {!res && <Button variant="primary" onClick={submit} disabled={ans.kind === 'unanswered'}>Submit</Button>}
          {res  && <Button variant="primary" onClick={next}>{idx === questions.length - 1 ? 'Finish' : 'Next'}</Button>}
        </div>
      </div>
    </div>
  );
}

/** Toy rubric — keyword overlap. Real backend would call the assessment agent. */
function gradeShort(answer: string, ideal: string): { claim: number; evidence: number; clarity: number } {
  const norm = (s: string) => new Set(s.toLowerCase().match(/[a-z]+/g) ?? []);
  const a = norm(answer); const i = norm(ideal);
  const overlap = [...a].filter(t => i.has(t)).length;
  const recall = i.size === 0 ? 0 : Math.min(1, overlap / Math.max(3, i.size * 0.4));
  const length = Math.min(1, answer.split(/\s+/).filter(Boolean).length / 25);
  return { claim: recall, evidence: recall * 0.9, clarity: length };
}
