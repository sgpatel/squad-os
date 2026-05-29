import { useEffect, useRef, useState } from 'react';

export type PomodoroPhase = 'focus' | 'short-break' | 'long-break';

export interface PomodoroState {
  phase: PomodoroPhase;
  remaining: number;     // seconds left in current phase
  running: boolean;
  cycle: number;         // how many focus phases completed
  start: () => void;
  pause: () => void;
  reset: () => void;
  skip:  () => void;
}

const PHASE_DURATIONS: Record<PomodoroPhase, number> = {
  focus:        25 * 60,
  'short-break': 5 * 60,
  'long-break': 15 * 60
};

export function usePomodoro(): PomodoroState {
  const [phase, setPhase] = useState<PomodoroPhase>('focus');
  const [remaining, setRemaining] = useState(PHASE_DURATIONS.focus);
  const [running, setRunning] = useState(false);
  const [cycle, setCycle] = useState(0);
  const tickRef = useRef<number | null>(null);

  useEffect(() => {
    if (!running) return;
    tickRef.current = window.setInterval(() => {
      setRemaining(r => {
        if (r > 0) return r - 1;
        // Phase complete
        const nextCycle  = phase === 'focus' ? cycle + 1 : cycle;
        const nextPhase: PomodoroPhase =
          phase !== 'focus' ? 'focus' :
          (nextCycle % 4 === 0) ? 'long-break' : 'short-break';
        setCycle(nextCycle);
        setPhase(nextPhase);
        return PHASE_DURATIONS[nextPhase];
      });
    }, 1000);
    return () => { if (tickRef.current != null) clearInterval(tickRef.current); };
  }, [running, phase, cycle]);

  return {
    phase, remaining, running, cycle,
    start: () => setRunning(true),
    pause: () => setRunning(false),
    reset: () => { setRunning(false); setPhase('focus'); setRemaining(PHASE_DURATIONS.focus); setCycle(0); },
    skip:  () => setRemaining(0)
  };
}
