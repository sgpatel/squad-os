import { useSettings } from '@/store/settings';
import { SectionLabel } from '@/components/ui/Misc';
import type { ThemeName, ColorMode, Density } from '@/lib/types';

const THEMES: { id: ThemeName; name: string; tier: string; swatches: string[] }[] = [
  { id: 'lumen',    name: 'Lumen',    tier: 'Higher Sec.',  swatches: ['#fafaf7', '#d97706', '#1a1a1a'] },
  { id: 'crayon',   name: 'Crayon',   tier: 'Primary',      swatches: ['#fff8f0', '#e8478b', '#332518'] },
  { id: 'atlas',    name: 'Atlas',    tier: 'Middle',       swatches: ['#f3f6fc', '#2563eb', '#0f172a'] },
  { id: 'studio',   name: 'Studio',   tier: 'College',      swatches: ['#0f1117', '#818cf8', '#e5e7ec'] },
  { id: 'graphite', name: 'Graphite', tier: 'Professional', swatches: ['#0a0a0a', '#a78bfa', '#e6e6e6'] }
];

const MODES: { id: ColorMode; label: string }[] = [
  { id: null,    label: 'System' },
  { id: 'light', label: 'Light'  },
  { id: 'dark',  label: 'Dark'   }
];

const DENSITIES: { id: Density; label: string }[] = [
  { id: 'compact',     label: 'Compact'     },
  { id: 'comfortable', label: 'Comfortable' },
  { id: 'loose',       label: 'Loose'       }
];

/**
 * Settings — theme, mode, density, font scale, a11y.
 *
 * All controls write directly to the persisted Zustand store; the
 * App.tsx effect mirrors them onto :root so the entire UI re-skins
 * in one paint.
 */
export function SettingsPage() {
  const { theme, mode, density, fontScale, setTheme, setMode, setDensity, setFontScale } = useSettings();

  return (
    <div className="page-react">
      <header className="page-header">
        <div>
          <h1>Settings</h1>
          <p>Personalize how the tutor looks and feels.</p>
        </div>
      </header>

      <div className="settings-section">
        <SectionLabel>Theme</SectionLabel>
        <p className="muted small">Pick the visual register for your age and context. The structural layout is identical across themes — only the visual mood changes.</p>
        <div className="theme-pick mt-3">
          {THEMES.map(t => (
            <button
              key={t.id}
              className={'theme-pick__opt' + (theme === t.id ? ' is-active' : '')}
              onClick={() => setTheme(t.id)}
            >
              <div className="theme-pick__swatches">
                {t.swatches.map((c, i) => <span key={i} style={{ background: c }} />)}
              </div>
              <div className="theme-pick__name">{t.name}</div>
              <div className="theme-pick__tier">{t.tier}</div>
            </button>
          ))}
        </div>
      </div>

      <div className="settings-section">
        <SectionLabel>Color mode</SectionLabel>
        <div className="row mt-3">
          {MODES.map(m => (
            <button
              key={String(m.id)}
              className={'btn btn--sm' + (mode === m.id ? ' btn--primary' : '')}
              onClick={() => setMode(m.id)}
            >
              {m.label}
            </button>
          ))}
        </div>
      </div>

      <div className="settings-section">
        <SectionLabel>Density</SectionLabel>
        <p className="muted small">Adjust spacing — useful for small screens or quick scanning.</p>
        <div className="row mt-3">
          {DENSITIES.map(d => (
            <button
              key={d.id}
              className={'btn btn--sm' + (density === d.id ? ' btn--primary' : '')}
              onClick={() => setDensity(d.id)}
            >
              {d.label}
            </button>
          ))}
        </div>
      </div>

      <div className="settings-section">
        <SectionLabel>Accessibility</SectionLabel>
        <div className="settings-row">
          <div>
            <div className="settings-row__label">Font size</div>
            <div className="settings-row__hint">Multiplier on the base body font ({Math.round(fontScale * 100)}%).</div>
          </div>
          <input
            type="range" min={0.85} max={1.4} step={0.05}
            value={fontScale}
            onChange={e => setFontScale(Number(e.target.value))}
            style={{ width: 200 }}
          />
        </div>
        <div className="settings-row">
          <div>
            <div className="settings-row__label">Reduced motion</div>
            <div className="settings-row__hint">We always respect <code>prefers-reduced-motion</code> from the OS.</div>
          </div>
          <code className="small muted">OS-controlled</code>
        </div>
      </div>

      <div className="settings-section">
        <SectionLabel>About</SectionLabel>
        <p className="muted small">
          TutorOS is an open agent pipeline.
          Every answer carries citations and a confidence score.
          You can see the agents working — pipeline strip in the topbar, full timeline below.
        </p>
      </div>
    </div>
  );
}
