import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChevronDown, Search, Sun, Moon, Contrast, Flame, Zap, Brain, LogOut, Settings as SettingsIcon } from 'lucide-react';
import { useSettings } from '@/store/settings';
import { useWorkspace } from '@/store/workspace';
import { useAuth } from '@/store/auth';
import { Avatar, ProgressBar } from '@/components/ui/Misc';
import { Button } from '@/components/ui/Button';
import { Kbd } from '@/components/ui/Misc';
import { PipelineStrip } from '@/features/pipeline/PipelineStrip';

interface TopbarProps {
  onOpenCommand: () => void;
}

/**
 * Topbar — brand · workspace switcher · pipeline strip · streak/xp · mode · avatar.
 *
 * Pipeline strip slides in only while a run is active, occupying the
 * center column. Theme/mode controls live in /settings; here we only
 * expose the mode toggle as a one-tap convenience.
 */
export function Topbar({ onOpenCommand }: TopbarProps) {
  const mode          = useSettings(s => s.mode);
  const cycleMode     = useSettings(s => s.cycleMode);
  const assistMode    = useSettings(s => s.assistMode);
  const setAssistMode = useSettings(s => s.setAssistMode);
  const { user: seedUser, workspaces, activeWorkspaceId, setActiveWorkspace } = useWorkspace();
  const authUser   = useAuth(s => s.currentUser());
  const logout     = useAuth(s => s.logout);
  const navigate   = useNavigate();

  // Prefer the authenticated profile; fall back to the seed user for
  // xp/streak numbers the auth record doesn't carry yet so the header
  // stays populated. Name + initials always come from auth when present.
  const user = authUser
    ? { ...seedUser, name: authUser.name, initials: authUser.initials, id: authUser.id }
    : seedUser;

  const [wsOpen, setWsOpen]       = useState(false);
  const [menuOpen, setMenuOpen]   = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Close the profile menu on outside click.
  useEffect(() => {
    if (!menuOpen) return;
    const onDoc = (e: MouseEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [menuOpen]);

  const activeWs = workspaces.find(w => w.id === activeWorkspaceId) ?? workspaces[0]!;

  const onLogout = () => {
    setMenuOpen(false);
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="topbar shell__topbar" role="banner">
      <Link to="/" className="topbar__brand" aria-label="TutorOS home">
        <span className="topbar__brand-mark" aria-hidden="true">T</span>
        <span>TutorOS</span>
      </Link>

      {/* Workspace switcher (very simple dropdown — keeps the example self-contained) */}
      <div style={{ position: 'relative' }}>
        <button className="topbar__pill" onClick={() => setWsOpen(o => !o)} aria-haspopup="menu" aria-expanded={wsOpen}>
          <span>{activeWs.name}</span>
          <ChevronDown size={12} />
        </button>
        {wsOpen && (
          <div role="menu" style={{
            position: 'absolute', top: '100%', left: 0, marginTop: 4, minWidth: 240,
            background: 'var(--color-surface)', border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-md)', padding: 4, zIndex: 30
          }}>
            {workspaces.map(w => (
              <button
                key={w.id}
                role="menuitem"
                onClick={() => { setActiveWorkspace(w.id); setWsOpen(false); }}
                style={{
                  display: 'block', width: '100%', textAlign: 'left',
                  padding: '8px 12px', borderRadius: 'var(--radius-sm)',
                  background: w.id === activeWs.id ? 'var(--color-accent-soft)' : 'transparent',
                  fontSize: 'var(--text-sm)', color: 'var(--color-text)'
                }}
              >
                <div style={{ fontWeight: 500 }}>{w.name}</div>
                <div style={{ fontSize: 11, color: 'var(--color-text-subtle)', marginTop: 2 }}>{w.description}</div>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Pipeline strip — auto-renders when a run is active */}
      <div className="flex-1 flex items-center" style={{ justifyContent: 'center' }}>
        <PipelineStrip />
      </div>

      {/* Assist mode toggle — Direct ⚡ vs Agentic 🧠 */}
      <div
        role="group"
        aria-label="Assist mode"
        className="mode-toggle"
      >
        <button
          type="button"
          className={'mode-toggle__btn' + (assistMode === 'direct' ? ' is-active' : '')}
          onClick={() => setAssistMode('direct')}
          title="Direct — fast single-agent reply, no debate"
          aria-pressed={assistMode === 'direct'}
        >
          <Zap size={12} /> Direct
        </button>
        <button
          type="button"
          className={'mode-toggle__btn' + (assistMode === 'agentic' ? ' is-active' : '')}
          onClick={() => setAssistMode('agentic')}
          title="Agentic — full pipeline with debate + citations"
          aria-pressed={assistMode === 'agentic'}
        >
          <Brain size={12} /> Agentic
        </button>
      </div>

      {/* Search / command palette trigger */}
      <Button variant="ghost" size="sm" onClick={onOpenCommand} leading={<Search size={14} />}>
        Search <Kbd>⌘</Kbd><Kbd>K</Kbd>
      </Button>

      <span className="streak" title={`${user.streak}-day streak`}>
        <Flame size={14} /> {user.streak}
      </span>

      <span className="xp" title="Today's XP">
        <ProgressBar value={user.xpToday} max={user.xpGoal} />
        <span>{user.xpToday}/{user.xpGoal} XP</span>
      </span>

      <Button
        variant="ghost"
        iconOnly
        onClick={cycleMode}
        aria-label={`Color mode: ${mode ?? 'system'}`}
        title={`Color mode: ${mode ?? 'system'}`}
      >
        {mode === 'dark' ? <Moon size={14} /> : mode === 'light' ? <Sun size={14} /> : <Contrast size={14} />}
      </Button>

      {/* Profile menu — avatar opens a small dropdown with Settings + Logout. */}
      <div ref={menuRef} style={{ position: 'relative' }}>
        <button
          type="button"
          onClick={() => setMenuOpen(o => !o)}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          aria-label={`Account menu for ${user.name}`}
          title={user.name}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: 2, border: 'none', background: 'transparent', cursor: 'pointer',
            borderRadius: 'var(--radius-full)',
          }}
        >
          <Avatar initials={user.initials} label={user.name} />
          <ChevronDown size={12} />
        </button>
        {menuOpen && (
          <div role="menu" style={{
            position: 'absolute', top: '100%', right: 0, marginTop: 6, minWidth: 220,
            background: 'var(--color-surface)', border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-md)', padding: 4, zIndex: 30,
          }}>
            <div style={{ padding: '8px 12px', borderBottom: '1px solid var(--color-border)', marginBottom: 4 }}>
              <div style={{ fontWeight: 600, fontSize: 'var(--text-sm)' }}>{user.name}</div>
              {authUser?.email && (
                <div style={{ fontSize: 11, color: 'var(--color-text-subtle)', marginTop: 2 }}>
                  {authUser.email}
                </div>
              )}
            </div>
            <Link
              to="/settings"
              role="menuitem"
              onClick={() => setMenuOpen(false)}
              style={{
                display: 'flex', alignItems: 'center', gap: 8,
                padding: '8px 12px', borderRadius: 'var(--radius-sm)',
                fontSize: 'var(--text-sm)', color: 'var(--color-text)',
                textDecoration: 'none',
              }}
            >
              <SettingsIcon size={14} /> Settings
            </Link>
            <button
              type="button"
              role="menuitem"
              onClick={onLogout}
              style={{
                display: 'flex', alignItems: 'center', gap: 8,
                width: '100%', textAlign: 'left',
                padding: '8px 12px', borderRadius: 'var(--radius-sm)',
                fontSize: 'var(--text-sm)', color: 'var(--color-danger, #c0392b)',
                background: 'transparent', border: 'none', cursor: 'pointer',
              }}
            >
              <LogOut size={14} /> Sign out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
