import { useMemo } from 'react';
import { Flame, BookOpen, Layers, Award } from 'lucide-react';
import { useWorkspace } from '@/store/workspace';
import { Card, CardMeta, CardTitle } from '@/components/ui/Card';
import { Sparkline } from '@/components/ui/Sparkline';
import { SectionLabel, Tag } from '@/components/ui/Misc';
import { fmtPercent } from '@/lib/format';
import { SubTabs } from '@/components/ui/SubTabs';
import { PROGRESS_TABS } from '@/components/layout/hubTabs';

/**
 * Progress dashboard.
 *   - Hero stats (streak · chapters · concepts · XP)
 *   - Mastery heatmap (each cell = a chapter, tinted by mastery)
 *   - Sparkline (last 14 days of XP — mock)
 *   - Concept breakdown (groups by mastery band)
 */
export function ProgressPage() {
  const { user, chapters, concepts } = useWorkspace();

  // Fake 14-day XP series
  const trend = useMemo(
    () => Array.from({ length: 14 }, (_, i) => 250 + Math.round(Math.sin(i / 2) * 200 + Math.random() * 200)),
    []
  );

  const masteryBucket = (m: number): 0 | 1 | 2 | 3 | 4 =>
    m === 0 ? 0 : m < 0.25 ? 1 : m < 0.5 ? 2 : m < 0.75 ? 3 : 4;

  // Pad to 60 cells so the heatmap looks like a real grid even with few chapters.
  const cells = [...chapters, ...Array(Math.max(0, 60 - chapters.length)).fill(null)];

  const weak     = concepts.filter(c => c.mastery < 0.4);
  const learning = concepts.filter(c => c.mastery >= 0.4 && c.mastery < 0.8);
  const mastered = concepts.filter(c => c.mastery >= 0.8);

  return (
    <div className="page-react">
      <SubTabs tabs={PROGRESS_TABS} ariaLabel="Progress hub" />
      <header className="page-header">
        <div>
          <h1>Progress</h1>
          <p>What you know · what's slipping · streak.</p>
        </div>
      </header>

      <section className="stat-grid">
        <div className="stat">
          <div className="stat__label"><Flame size={11} style={{ display: 'inline', marginRight: 4 }} />Streak</div>
          <div className="stat__value">{user.streak}d</div>
          <div className="stat__delta">+1 today</div>
        </div>
        <div className="stat">
          <div className="stat__label"><BookOpen size={11} style={{ display: 'inline', marginRight: 4 }} />Chapters touched</div>
          <div className="stat__value">{chapters.filter(c => c.mastery > 0).length}</div>
          <div className="stat__delta">+2 this week</div>
        </div>
        <div className="stat">
          <div className="stat__label"><Layers size={11} style={{ display: 'inline', marginRight: 4 }} />Concepts mastered</div>
          <div className="stat__value">{mastered.length}</div>
          <div className="stat__delta">{fmtPercent(mastered.length / Math.max(1, concepts.length))}</div>
        </div>
        <div className="stat">
          <div className="stat__label"><Award size={11} style={{ display: 'inline', marginRight: 4 }} />Today's XP</div>
          <div className="stat__value">{user.xpToday}</div>
          <div className="stat__delta">of {user.xpGoal}</div>
        </div>
      </section>

      <section className="mt-9">
        <SectionLabel>Last 14 days</SectionLabel>
        <Card>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <div>
              <CardMeta>XP per day</CardMeta>
              <CardTitle>{trend.reduce((s, n) => s + n, 0).toLocaleString()} XP</CardTitle>
            </div>
            <Sparkline data={trend} width={260} height={56} />
          </div>
        </Card>
      </section>

      <section className="mt-9">
        <SectionLabel>Mastery heatmap</SectionLabel>
        <Card>
          <div className="heatmap" aria-label="Chapter mastery heatmap">
            {cells.map((c, i) => (
              <div key={i} className="heatmap__cell"
                data-level={c ? masteryBucket(c.mastery) : 0}
                title={c ? `${c.name} · ${fmtPercent(c.mastery)}` : ''}
              />
            ))}
          </div>
          <p className="small muted mt-3">Each cell is a chapter. Darker = higher mastery.</p>
        </Card>
      </section>

      <section className="mt-9">
        <SectionLabel>Concept breakdown</SectionLabel>
        <Card>
          <div className="row mt-3" style={{ alignItems: 'flex-start', gap: 'var(--space-7)' }}>
            <div>
              <CardMeta>Weak</CardMeta>
              <p style={{ fontSize: 'var(--text-2xl)', fontWeight: 600 }}>{weak.length}</p>
              <div className="row" style={{ gap: 4 }}>
                {weak.slice(0, 4).map(c => <Tag key={c.id} kind="danger">{c.name}</Tag>)}
              </div>
            </div>
            <div>
              <CardMeta>Learning</CardMeta>
              <p style={{ fontSize: 'var(--text-2xl)', fontWeight: 600 }}>{learning.length}</p>
              <div className="row" style={{ gap: 4 }}>
                {learning.slice(0, 4).map(c => <Tag key={c.id} kind="warn">{c.name}</Tag>)}
              </div>
            </div>
            <div>
              <CardMeta>Mastered</CardMeta>
              <p style={{ fontSize: 'var(--text-2xl)', fontWeight: 600 }}>{mastered.length}</p>
              <div className="row" style={{ gap: 4 }}>
                {mastered.slice(0, 4).map(c => <Tag key={c.id} kind="success">{c.name}</Tag>)}
              </div>
            </div>
          </div>
        </Card>
      </section>
    </div>
  );
}
