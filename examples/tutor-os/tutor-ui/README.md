# TutorOS — Learner UI

The reference React app on top of the TutorOS open agent pipeline.
A learner can ask anything, watch the agents work, capture their thinking,
practice with spaced-repetition, and track mastery — all on top of a
**five-tier theme system** that re-skins for Primary → Professional learners
without changing a single component.

```bash
cd examples/tutor-os/tutor-ui
npm install
npm run dev          # http://localhost:5173
npm run build        # type-checks + production bundle
```

---

## Why this exists

Most "AI tutor" UIs are either chat boxes that hide the model, or JSON
firehoses that scare learners. TutorOS takes a third path:

- **Calm at the surface** — Google-cut homepage. Greeting, one input, three chips.
- **Honest underneath** — every tutor answer carries citations, a confidence
  meter, and (when the agents disagree) a *Debate Round* showing both sides.
- **Adaptive by tier** — the same screens look like Crayon for a 9-year-old
  and like Graphite for a working professional. Same code, different theme.

---

## Architecture (in one paragraph)

A **token-driven design system** (`src/styles/tokens.css` + 5 theme files)
sets the visual register; **vanilla CSS** in `base.css` + `app.css` lays out
the components against those tokens; **React + TypeScript** components
consume the tokens; a thin **Zustand** layer holds shell + domain state;
a **mock pipeline service** (`src/lib/pipeline.ts`) emits events shaped
exactly like a real SSE stream so the visualization components can be
swapped to a real backend with no UI changes.

```
┌─────────────────────────────────────────────────────────┐
│  index.html                                             │
│  └─ main.tsx                                            │
│     └─ <App>            (theme/mode → :root attrs)      │
│        └─ <BrowserRouter>                               │
│           └─ <AppShell>                                 │
│              ├─ Topbar (workspace · pipeline strip)     │
│              ├─ SideNav (Learn · Capture · Track)       │
│              ├─ <Outlet/>  ← active route                │
│              └─ CommandPalette (⌘K)                     │
└─────────────────────────────────────────────────────────┘
```

---

## Routes (information architecture)

| Path | Purpose |
|------|---------|
| `/`              | **Home** — greeting · ask · today's plan |
| `/tutor`         | **Tutor chat** — streaming pipeline + citations + debate |
| `/subjects`      | Subject browser (workspace-scoped grid) |
| `/courses/:id`   | Course → ordered chapters (+ Atlas Journey Map) |
| `/chapters/:id`  | Chapter player — read · do · check |
| `/notes`, `/notes/:id` | Notes — index + editor (autosave) |
| `/scratch`       | **Rough work** — canvas scratchpad (vector strokes, undo, export PNG) |
| `/practice`      | Spaced-repetition flashcards (light SM-2) |
| `/quiz/:id`      | Quiz — MCQ · multi · short-answer with rubric |
| `/progress`      | Mastery heatmap · streak · sparkline · concept buckets |
| `/plan`          | Today + tomorrow + Pomodoro + suggested |
| `/library`       | Source pool — every citation in one place |
| `/community`     | Doubt threads (peer · tutor · AI) |
| `/settings`      | Theme · mode · density · font scale · a11y |

⌘K from anywhere → command palette (jump · ask · create note · run quiz).

---

## The five themes

The same screens, five visual registers — each tuned for a learner tier.
Theme picks just set `data-theme="..."` on `<html>`; the CSS does the rest.

| Theme    | Tier            | Vibe                                     | Feature flags |
|----------|-----------------|------------------------------------------|---------------|
| Crayon   | Primary (5–10)  | Pastel · Quicksand · spring motion       | mascot, large radii, celebration-2 |
| Atlas    | Middle (11–13)  | Vivid blue + fuchsia · journey map       | streak, xp, map-path |
| **Lumen**| **Higher Sec.** *(default)* | Warm minimal · Inter        | streak |
| Studio   | College (18–22) | Dark indigo · Geist · compact            | density: compact |
| Graphite | Pro (22+)       | Near-black · lavender · zero ornament    | no celebrations / no streak |

---

## The agent pipeline, made visible

Three layers of progressive disclosure — pick how much detail you want:

| Layer | Component | Where |
|-------|-----------|-------|
| **A — Ambient** | `PipelineStrip` | In the topbar; one dot per agent role; current pulses |
| **B — Timeline**| `PipelineReveal`| Expandable list of every step + per-step elapsed time |
| **C — Topology**| `PipelineGraph` | SVG of agents as nodes; debate forks into Advocate/Skeptic → Judge |

When the **debate** stage runs, `DebateRound` auto-promotes into view
with For / Against cards, citations, round pips and the judge's verdict.

The mock service in `src/lib/pipeline.ts` yields exactly the event shape
a real SSE backend would push:

```ts
type PipelineEvent =
  | { kind: 'started';   run: PipelineRun }
  | { kind: 'step:run';  runId; index; step }
  | { kind: 'step:done'; runId; index; step }
  | { kind: 'message';   runId; message }
  | { kind: 'finished';  runId; durationMs };
```

---

## Source layout

```
src/
├── App.tsx                  ← router + theme bootstrap
├── main.tsx                 ← React root + CSS imports
├── styles/
│   ├── tokens.css           design contract (single source of truth)
│   ├── base.css             primitives (button, card, hero, debate, …)
│   ├── app.css              React-shell layout (sidenav, chat, canvas, …)
│   └── themes/{lumen,crayon,atlas,studio,graphite}.css
├── lib/
│   ├── types.ts             every domain type
│   ├── mockData.ts          one-stop seed data
│   ├── pipeline.ts          mock streaming pipeline (matches real SSE shape)
│   └── format.ts            tiny presentational helpers
├── store/                   Zustand slices
│   ├── settings.ts          theme · mode · density · font scale (persisted)
│   ├── workspace.ts         user · workspaces · subjects/courses/chapters
│   ├── pipeline.ts          live run + chat thread
│   ├── notes.ts             persisted CRUD
│   ├── practice.ts          SM-2 SRS engine
│   └── plan.ts              persisted CRUD
├── hooks/
│   ├── useShortcut.ts       keyboard shortcuts (⌘K, Esc, …)
│   ├── useLocalStorage.ts   tiny persistence helper
│   └── usePomodoro.ts       focus / short / long break timer
├── components/
│   ├── ui/                  Button, Card, Chip, Input, Sparkline, Misc
│   └── layout/              AppShell, Topbar, SideNav, CommandPalette
└── features/                one folder per route group
    ├── home/        HomePage
    ├── tutor/       TutorPage · ChatThread (inline) · ChatMessage · ChatComposer
    ├── pipeline/    PipelineStrip · PipelineReveal · PipelineGraph · DebateRound
    ├── subjects/    SubjectsPage · CoursePage · ChapterPlayer
    ├── notes/       NotesPage · NoteEditor
    ├── roughwork/   RoughWorkPage   (canvas)
    ├── practice/    PracticePage    (flashcards)
    ├── quiz/        QuizPage        (MCQ · multi · short)
    ├── progress/    ProgressPage    (heatmap · sparkline · buckets)
    ├── plan/        PlanPage        (today · pomodoro · suggested)
    ├── library/     LibraryPage     (citation pool)
    ├── community/   CommunityPage   (doubts)
    └── settings/    SettingsPage
```

---

## Swapping mocks for the real backend

Two seams to wire:

1. **Read paths** — every `seed*` array in `mockData.ts`. Replace with
   `useQuery`/`fetch` against your TutorOS REST endpoints. The Zustand
   stores already have `partialize` set so persisted state stays minimal.

2. **Pipeline stream** — replace `runPipelineMock` in `lib/pipeline.ts`
   with an `EventSource('/api/runs/{id}/events')`. The components listen
   for `started/step:run/step:done/message/finished` and don't care
   whether they're driven by a setTimeout or a server.

---

## Legacy

The original vanilla HTML/CSS/JS prototype lives under `legacy/` for
reference. It has the same theme system — useful if you want to see the
design surface without React.
