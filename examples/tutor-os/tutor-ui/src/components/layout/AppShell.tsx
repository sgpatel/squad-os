import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Topbar } from './Topbar';
import { SideNav } from './SideNav';
import { CommandPalette } from './CommandPalette';
import { Toaster } from '@/components/ui/Toaster';
import { useShortcut } from '@/hooks/useShortcut';

/**
 * AppShell — the persistent chrome around every route.
 *   ┌──────────────────────────────────────────────┐
 *   │              Topbar (sticky)                 │
 *   ├─────────┬────────────────────────────────────┤
 *   │ SideNav │ <Outlet /> (the active route)      │
 *   └─────────┴────────────────────────────────────┘
 * ⌘K opens the command palette from anywhere.
 */
export function AppShell() {
  const [cmdOpen, setCmdOpen] = useState(false);

  useShortcut('mod+k', (e) => { e.preventDefault(); setCmdOpen(o => !o); });
  useShortcut('escape',  () => setCmdOpen(false));

  return (
    <div className="shell">
      <Topbar onOpenCommand={() => setCmdOpen(true)} />
      <aside className="shell__side">
        <SideNav />
      </aside>
      <main className="shell__main">
        <Outlet />
      </main>
      <CommandPalette open={cmdOpen} onClose={() => setCmdOpen(false)} />
      {/* Global toast region — mounted once at the shell level so every
          route (Review, Quiz, Tutor, …) can fire mastery announcements
          without prop-drilling. */}
      <Toaster />
    </div>
  );
}
