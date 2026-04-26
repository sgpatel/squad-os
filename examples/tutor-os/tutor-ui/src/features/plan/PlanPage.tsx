import { useNavigate } from 'react-router-dom';
import { Check, Play, Pause, RotateCcw, SkipForward, Clock } from 'lucide-react';
import { usePlan } from '@/store/plan';
import { Button } from '@/components/ui/Button';
import { Card, CardMeta, CardTitle } from '@/components/ui/Card';
import { SectionLabel, Tag } from '@/components/ui/Misc';
import { usePomodoro } from '@/hooks/usePomodoro';
import { fmtMinutes } from '@/lib/format';
import type { PlanItemKind } from '@/lib/types';
import { SubTabs } from '@/components/ui/SubTabs';
import { PROGRESS_TABS } from '@/components/layout/hubTabs';

const KIND_TONE: Record<PlanItemKind, 'success' | 'warn' | 'danger' | 'info' | undefined> = {
  study:    'info',
  practice: undefined,
  quiz:     'warn',
  review:   'success',
  break:    undefined
};

/**
 * Plan — today + this week + Pomodoro.
 * The Pomodoro timer is a self-contained hook (usePomodoro).
 */
export function PlanPage() {
  const navigate = useNavigate();
  const items   = usePlan(s => s.items);
  const toggle  = usePlan(s => s.toggle);

  const today    = new Date().toISOString().slice(0, 10);
  const tomorrow = new Date(Date.now() + 86400e3).toISOString().slice(0, 10);
  const todayItems = items.filter(i => i.date === today).sort(byTime);
  const tomItems   = items.filter(i => i.date === tomorrow).sort(byTime);

  const pomo = usePomodoro();
  const mm = String(Math.floor(pomo.remaining / 60)).padStart(2, '0');
  const ss = String(pomo.remaining % 60).padStart(2, '0');

  return (
    <div className="page-react">
      <SubTabs tabs={PROGRESS_TABS} ariaLabel="Progress hub" />
      <header className="page-header">
        <div>
          <h1>Plan</h1>
          <p>{todayItems.filter(i => !i.done).length} task{todayItems.filter(i => !i.done).length === 1 ? '' : 's'} left today.</p>
        </div>
      </header>

      {/* Pomodoro */}
      <section className="pomodoro mt-3">
        <div className="pomodoro__time" aria-live="polite">{mm}:{ss}</div>
        <div>
          <div className="row">
            <Tag kind={pomo.phase === 'focus' ? 'info' : 'success'}>{pomo.phase}</Tag>
            <span className="small muted">Cycle {pomo.cycle}</span>
          </div>
          <div className="row mt-3">
            {!pomo.running
              ? <Button variant="primary" leading={<Play size={14} />}  onClick={pomo.start}>Start</Button>
              : <Button             leading={<Pause size={14} />} onClick={pomo.pause}>Pause</Button>}
            <Button leading={<SkipForward size={14} />} onClick={pomo.skip}>Skip phase</Button>
            <Button variant="ghost" leading={<RotateCcw size={14} />} onClick={pomo.reset}>Reset</Button>
          </div>
        </div>
      </section>

      {/* Today */}
      <section className="mt-9">
        <SectionLabel>Today</SectionLabel>
        <div className="plan-list">
          {todayItems.length === 0 && <p className="muted small">No items planned. The tutor will suggest a plan tonight.</p>}
          {todayItems.map(p => (
            <div key={p.id} className={'plan-row' + (p.done ? ' is-done' : '')}>
              <button className="plan-row__check" onClick={() => toggle(p.id)} aria-label={p.done ? 'Mark not done' : 'Mark done'}>
                <Check size={12} />
              </button>
              <div style={{ flex: 1 }}>
                <div className="plan-row__title">{p.title}</div>
                <div className="small muted" style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 2 }}>
                  {KIND_TONE[p.kind] !== undefined ? <Tag kind={KIND_TONE[p.kind]!}>{p.kind}</Tag> : <Tag>{p.kind}</Tag>}
                  <Clock size={11} /> {fmtMinutes(p.durationMin)}
                </div>
              </div>
              <span className="plan-row__time">{p.startTime ?? ''}</span>
              {p.chapterId && <Button size="sm" onClick={() => navigate(`/chapters/${p.chapterId}`)}>Open</Button>}
              {p.quizId    && <Button size="sm" onClick={() => navigate(`/quiz/${p.quizId}`)}>Start</Button>}
            </div>
          ))}
        </div>
      </section>

      {/* Tomorrow */}
      <section className="mt-9">
        <SectionLabel>Tomorrow</SectionLabel>
        <div className="plan-list">
          {tomItems.length === 0
            ? <p className="muted small">Nothing planned yet — drag from the suggested list when ready.</p>
            : tomItems.map(p => (
              <div key={p.id} className="plan-row">
                <span className="plan-row__check" />
                <div style={{ flex: 1 }}>
                  <div className="plan-row__title">{p.title}</div>
                  <div className="small muted">{p.kind} · {fmtMinutes(p.durationMin)}</div>
                </div>
                <span className="plan-row__time">{p.startTime ?? ''}</span>
              </div>
            ))}
        </div>
      </section>

      <section className="mt-9">
        <SectionLabel>Suggested for you</SectionLabel>
        <div className="grid-3">
          <Card>
            <CardMeta>From last quiz</CardMeta>
            <CardTitle>Re-read photosynthesis</CardTitle>
            <p className="muted small">You missed Q3 (Calvin cycle outputs). 12 min of reading.</p>
          </Card>
          <Card>
            <CardMeta>Spaced repetition</CardMeta>
            <CardTitle>5 cards due now</CardTitle>
            <p className="muted small">Run a 5-min review before the streak resets at midnight.</p>
          </Card>
          <Card>
            <CardMeta>Coming exam</CardMeta>
            <CardTitle>JEE — 14 days</CardTitle>
            <p className="muted small">Weekly mock recommended. Saturday 10:00.</p>
          </Card>
        </div>
      </section>
    </div>
  );
}

function byTime(a: { startTime?: string }, b: { startTime?: string }) {
  return (a.startTime ?? '').localeCompare(b.startTime ?? '');
}
