/* ─────────────────────────────────────────────────────────────────
 * app.js — TutorOS UI shell.
 *
 * Responsibilities (kept small; each component is one function):
 *   1. Theme + mode switcher        → wireThemeSwitcher()
 *   2. Pipeline definition (mock)    → PIPELINE
 *   3. Pipeline strip (Layer A)      → mountPipelineStrip()
 *   4. Pipeline reveal (Layer B)     → renderTimeline()
 *   5. Debate update                 → updateDebate()
 *   6. Run simulator                 → runPipeline()
 *   7. Hero input + chip wiring      → wireHero()
 *   8. ⌘K shortcut                   → wireShortcuts()
 *
 * No framework. The whole point of this example is to show the design
 * system + agent-pipeline pattern with the smallest possible code.
 * ───────────────────────────────────────────────────────────────── */

// ── 1. Theme + mode ─────────────────────────────────────────────────
const THEMES = ['lumen', 'crayon', 'atlas', 'studio', 'graphite'];
const STORAGE_KEY_THEME = 'tutoros.theme';
const STORAGE_KEY_MODE  = 'tutoros.mode';

function applyTheme(theme) {
  if (!THEMES.includes(theme)) theme = 'lumen';
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem(STORAGE_KEY_THEME, theme);
  document.dispatchEvent(new CustomEvent('themechange', { detail: { theme } }));
}

function applyMode(mode) {
  // mode: 'light' | 'dark' | null (system)
  if (mode) {
    document.documentElement.setAttribute('data-mode', mode);
    localStorage.setItem(STORAGE_KEY_MODE, mode);
  } else {
    document.documentElement.removeAttribute('data-mode');
    localStorage.removeItem(STORAGE_KEY_MODE);
  }
  const icon = document.getElementById('modeIcon');
  if (icon) icon.textContent = mode === 'dark' ? '☾' : mode === 'light' ? '☀' : '◐';
}

function wireThemeSwitcher() {
  const select = document.getElementById('themeSelect');
  const stored = localStorage.getItem(STORAGE_KEY_THEME) || 'lumen';
  applyTheme(stored);
  select.value = stored;
  select.addEventListener('change', e => applyTheme(e.target.value));

  const storedMode = localStorage.getItem(STORAGE_KEY_MODE);
  applyMode(storedMode);
  document.getElementById('modeToggle').addEventListener('click', () => {
    const cur = document.documentElement.getAttribute('data-mode');
    // cycle: system → dark → light → system
    const next = cur == null ? 'dark' : cur === 'dark' ? 'light' : null;
    applyMode(next);
  });
}

// ── 2. Pipeline definition ──────────────────────────────────────────
// Each stage is one agent role. The stage key drives the color via
// the --color-stage-* tokens in tokens.css.
const PIPELINE = [
  { key: 'guardian',   label: 'Safety check',         detail: 'Validating prompt scope + safety policy.' },
  { key: 'diagnostic', label: 'Diagnose level',       detail: 'Estimating prior knowledge & gaps.' },
  { key: 'planner',    label: 'Plan lesson',          detail: 'Sequencing concepts: light reactions → Calvin cycle.' },
  { key: 'content',    label: 'Fetch sources',        detail: 'Pulling 3 textbook excerpts + 1 diagram.' },
  { key: 'debate',     label: 'Debate the answer',    detail: 'Advocate vs Skeptic — 3 rounds, judge selects.' },
  { key: 'tutor',      label: 'Compose response',     detail: 'Inline citations, confidence 0.86.' },
  { key: 'practice',   label: 'Generate practice',    detail: '4 cards · 1 short-answer · 1 misconception trap.' },
  { key: 'assessment', label: 'Score answer',         detail: 'Rubric: claim 0.9 · evidence 0.8 · clarity 0.85.' },
  { key: 'progress',   label: 'Update mastery',       detail: 'Mole concept 0.74 → 0.78. Streak +1.' }
];

// ── 3. Pipeline strip (Layer A — ambient, lives in topbar) ──────────
function mountPipelineStrip() {
  const mount = document.getElementById('pipelineStripMount');
  mount.innerHTML = `
    <div class="pipeline-strip" id="pipelineStrip" hidden>
      <div class="pipeline-strip__dots" id="pipelineDots" style="display:flex;gap:6px;align-items:center;"></div>
      <span class="pipeline-strip__label" id="pipelineStripLabel"><strong>Idle</strong></span>
      <button class="pipeline-strip__expand" id="pipelineExpand" aria-expanded="false">Show steps</button>
    </div>`;
  const dots = document.getElementById('pipelineDots');
  dots.innerHTML = PIPELINE.map((s, i) =>
    `<span class="pipeline-strip__dot" data-i="${i}" style="--stage-color: var(--color-stage-${s.key})"></span>`
  ).join('');

  document.getElementById('pipelineExpand').addEventListener('click', () => {
    const reveal = document.getElementById('pipelineReveal');
    const open = reveal.getAttribute('data-open') === 'true';
    reveal.setAttribute('data-open', String(!open));
    document.getElementById('pipelineExpand').textContent = open ? 'Show steps' : 'Hide steps';
    document.getElementById('pipelineExpand').setAttribute('aria-expanded', String(!open));
  });
}

function setStripState(activeIdx) {
  const strip = document.getElementById('pipelineStrip');
  strip.hidden = false;
  document.querySelectorAll('.pipeline-strip__dot').forEach((dot, i) => {
    dot.classList.toggle('pipeline-strip__dot--active', i === activeIdx);
    dot.classList.toggle('pipeline-strip__dot--done',   i  <  activeIdx);
  });
  const stage = PIPELINE[activeIdx] ?? PIPELINE.at(-1);
  document.getElementById('pipelineStripLabel').innerHTML =
    activeIdx >= PIPELINE.length
      ? `<strong>Done</strong> · 9 / 9`
      : `<strong>${stage.label}</strong> · ${activeIdx + 1} / ${PIPELINE.length}`;
}

// ── 4. Pipeline reveal (Layer B — the timeline) ─────────────────────
function renderTimeline() {
  const ol = document.getElementById('timeline');
  ol.innerHTML = PIPELINE.map((s, i) => `
    <li class="timeline__row" data-i="${i}" data-state="pending"
        style="--stage-color: var(--color-stage-${s.key})">
      <span class="timeline__bullet" aria-hidden="true"></span>
      <div class="timeline__body">
        <p class="timeline__stage">${s.key}</p>
        <p class="timeline__title">${s.label}</p>
        <p class="timeline__detail">${s.detail}</p>
      </div>
      <span class="timeline__time" data-time></span>
    </li>
  `).join('');
}

function setTimelineState(idx, state, ms) {
  const row = document.querySelector(`.timeline__row[data-i="${idx}"]`);
  if (!row) return;
  row.setAttribute('data-state', state);
  if (ms != null) {
    const t = row.querySelector('[data-time]');
    if (t) t.textContent = `${(ms/1000).toFixed(1)}s`;
  }
}

// ── 5. Debate Round panel ───────────────────────────────────────────
function showDebate(topic) {
  const mount = document.getElementById('debateMount');
  mount.classList.remove('hidden');
  mount.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  document.getElementById('debateTopic').textContent = topic;
}

// ── 6. Run simulator ────────────────────────────────────────────────
async function runPipeline(prompt) {
  const reveal  = document.getElementById('pipelineReveal');
  const expand  = document.getElementById('pipelineExpand');
  const summary = document.getElementById('pipelineSummary');
  const elapsed = document.getElementById('pipelineElapsed');

  // Reset
  renderTimeline();
  reveal.setAttribute('data-open', 'true');
  expand.setAttribute('aria-expanded', 'true');
  expand.textContent = 'Hide steps';
  summary.textContent = `Running: "${prompt}"`;

  const started = performance.now();
  const elapsedTimer = setInterval(() => {
    elapsed.textContent = `${((performance.now() - started)/1000).toFixed(1)}s`;
  }, 100);

  for (let i = 0; i < PIPELINE.length; i++) {
    setStripState(i);
    setTimelineState(i, 'running');

    // Promote debate panel into view as soon as the debate stage starts
    if (PIPELINE[i].key === 'debate') showDebate(prompt);

    const dur = 350 + Math.random() * 700;
    await new Promise(r => setTimeout(r, dur));
    setTimelineState(i, 'done', dur);
  }

  clearInterval(elapsedTimer);
  setStripState(PIPELINE.length);
  summary.textContent = `Done — answer composed with citations + 1 debate round.`;
}

// ── 7. Hero input + chips ───────────────────────────────────────────
function wireHero() {
  const form  = document.getElementById('askForm');
  const input = document.getElementById('askInput');

  form.addEventListener('submit', e => {
    e.preventDefault();
    const q = input.value.trim();
    if (!q) return;
    runPipeline(q);
  });

  document.querySelectorAll('.chip[data-prompt]').forEach(chip => {
    chip.addEventListener('click', () => {
      const q = chip.getAttribute('data-prompt');
      input.value = q;
      runPipeline(q);
    });
  });
}

// ── 8. ⌘K shortcut ──────────────────────────────────────────────────
function wireShortcuts() {
  document.addEventListener('keydown', e => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      const input = document.getElementById('askInput');
      input.focus();
      input.select();
    }
  });
}

// ── Boot ────────────────────────────────────────────────────────────
wireThemeSwitcher();
mountPipelineStrip();
renderTimeline();
wireHero();
wireShortcuts();
