/* ─────────────────────────────────────────────────────────────────
 * mockData.ts — seed data for the entire learner UI.
 *
 * One file, deliberately. Keeps the example "open and read it" friendly.
 * Swap each `seed*` helper for a fetch() call to wire up a real backend.
 * ───────────────────────────────────────────────────────────────── */

import type {
  User, Workspace, Subject, Course, Chapter, Topic, Concept,
  Note, Flashcard, Quiz, QuizQuestion, Source, PlanItem, Doubt,
  PipelineStageDef, ChatMessage, DebateRound
} from './types';

// ── User ────────────────────────────────────────────────────────────
export const seedUser: User = {
  id: 'u_priya',
  name: 'Priya',
  initials: 'P',
  tier: 'higher-sec',
  xpToday: 620,
  xpGoal:  1000,
  streak:  3
};

// ── Workspaces ──────────────────────────────────────────────────────
export const seedWorkspaces: Workspace[] = [
  {
    id: 'ws_personal',
    name: 'Personal',
    description: 'Curiosity-led learning. No deadlines.',
    defaultTheme: 'lumen',
    subjectIds: ['s_bio', 's_chem', 's_cs']
  },
  {
    id: 'ws_jee',
    name: 'JEE Main 2026',
    description: 'Exam prep · physics, chem, math.',
    defaultTheme: 'studio',
    subjectIds: ['s_phy', 's_chem', 's_math'],
    targetDate: '2026-04-04'
  },
  {
    id: 'ws_aws',
    name: 'AWS Solutions Architect',
    description: 'SAA-C03 cert prep.',
    defaultTheme: 'graphite',
    subjectIds: ['s_cloud']
  }
];

// ── Subjects ────────────────────────────────────────────────────────
export const seedSubjects: Subject[] = [
  { id: 's_bio',   name: 'Biology',    color: '#16a34a', icon: 'Leaf',     blurb: 'Cells, systems, evolution.',                courseIds: ['c_cell', 'c_genetics'] },
  { id: 's_chem',  name: 'Chemistry',  color: '#0ea5e9', icon: 'FlaskConical', blurb: 'Atoms to organics.',                    courseIds: ['c_atomic'] },
  { id: 's_phy',   name: 'Physics',    color: '#f59e0b', icon: 'Atom',     blurb: 'Mechanics, waves, modern physics.',         courseIds: ['c_mechanics'] },
  { id: 's_math',  name: 'Mathematics',color: '#8b5cf6', icon: 'Sigma',    blurb: 'Calculus, algebra, probability.',           courseIds: ['c_calc'] },
  { id: 's_cs',    name: 'Computer Science', color: '#dc2626', icon: 'Code', blurb: 'Algorithms, systems, languages.',         courseIds: ['c_algo'] },
  { id: 's_cloud', name: 'Cloud (AWS)', color: '#2563eb', icon: 'Cloud',  blurb: 'AWS Solutions Architect SAA-C03.',           courseIds: ['c_aws'] }
];

// ── Courses ─────────────────────────────────────────────────────────
export const seedCourses: Course[] = [
  { id: 'c_cell',     subjectId: 's_bio',  name: 'Cell Biology — Class XI',          description: 'Structure, function, division.',   chapterIds: ['ch_cell_intro','ch_cell_org','ch_photosynth','ch_respiration'], estMinutes: 480 },
  { id: 'c_genetics', subjectId: 's_bio',  name: 'Genetics — Class XII',             description: 'Heredity, DNA, expression.',       chapterIds: ['ch_mendel'], estMinutes: 240 },
  { id: 'c_atomic',   subjectId: 's_chem', name: 'Atomic Structure',                  description: 'Models, orbitals, periodicity.',   chapterIds: ['ch_bohr','ch_orbital'], estMinutes: 200 },
  { id: 'c_mechanics',subjectId: 's_phy',  name: 'Newtonian Mechanics',               description: 'Force, momentum, energy.',         chapterIds: ['ch_kinematics','ch_laws'], estMinutes: 360 },
  { id: 'c_calc',     subjectId: 's_math', name: 'Single-variable Calculus',          description: 'Limits, derivatives, integrals.',  chapterIds: ['ch_limits','ch_deriv','ch_integ'], estMinutes: 600 },
  { id: 'c_algo',     subjectId: 's_cs',   name: 'Algorithms 101',                    description: 'Big-O, sorting, graphs.',          chapterIds: ['ch_bigo'], estMinutes: 480 },
  { id: 'c_aws',      subjectId: 's_cloud',name: 'AWS Architect SAA-C03',             description: 'Compute · Storage · IAM · Net.',   chapterIds: ['ch_iam','ch_s3'], estMinutes: 1200 }
];

// ── Chapters ────────────────────────────────────────────────────────
export const seedChapters: Chapter[] = [
  { id: 'ch_cell_intro', courseId: 'c_cell', index: 1, name: 'What is a cell?',          blurb: 'The unit of life. Prokaryotes vs eukaryotes.', estMinutes: 25, topicIds: ['t_cell_def','t_prok_euk'], mastery: 0.92 },
  { id: 'ch_cell_org',   courseId: 'c_cell', index: 2, name: 'Cell organelles',          blurb: 'Nucleus, ER, Golgi, mitochondria…',            estMinutes: 45, topicIds: ['t_organelles'], mastery: 0.78 },
  { id: 'ch_photosynth', courseId: 'c_cell', index: 3, name: 'Photosynthesis',           blurb: 'Light reactions + Calvin cycle.',              estMinutes: 50, topicIds: ['t_light','t_calvin'], mastery: 0.34 },
  { id: 'ch_respiration',courseId: 'c_cell', index: 4, name: 'Cellular respiration',     blurb: 'Glycolysis, Krebs, ETC.',                       estMinutes: 50, topicIds: ['t_glyco'], mastery: 0.10 },
  { id: 'ch_mendel',     courseId: 'c_genetics', index: 1, name: "Mendel's laws",        blurb: 'Segregation, assortment.',                      estMinutes: 30, topicIds: ['t_mendel'], mastery: 0.20 },
  { id: 'ch_bohr',       courseId: 'c_atomic',   index: 1, name: 'Bohr model',           blurb: 'Quantized orbits.',                             estMinutes: 25, topicIds: ['t_bohr'], mastery: 0.66 },
  { id: 'ch_orbital',    courseId: 'c_atomic',   index: 2, name: 'Orbitals & quantum #s',blurb: 'n, l, m_l, m_s.',                               estMinutes: 30, topicIds: ['t_orbital'], mastery: 0.42 },
  { id: 'ch_kinematics', courseId: 'c_mechanics',index: 1, name: 'Kinematics',           blurb: 'Position, velocity, acceleration.',             estMinutes: 35, topicIds: ['t_kin'], mastery: 0.55 },
  { id: 'ch_laws',       courseId: 'c_mechanics',index: 2, name: "Newton's three laws",  blurb: 'Inertia · F=ma · action–reaction.',             estMinutes: 35, topicIds: ['t_laws'], mastery: 0.48 },
  { id: 'ch_limits',     courseId: 'c_calc',     index: 1, name: 'Limits',               blurb: 'Epsilon-delta, continuity.',                    estMinutes: 40, topicIds: ['t_limits'], mastery: 0.30 },
  { id: 'ch_deriv',      courseId: 'c_calc',     index: 2, name: 'Derivatives',          blurb: 'Rules, applications.',                          estMinutes: 50, topicIds: ['t_deriv'], mastery: 0.25 },
  { id: 'ch_integ',      courseId: 'c_calc',     index: 3, name: 'Integrals',            blurb: 'Antiderivatives, FTC.',                         estMinutes: 60, topicIds: ['t_integ'], mastery: 0.10 },
  { id: 'ch_bigo',       courseId: 'c_algo',     index: 1, name: 'Big-O notation',       blurb: 'Asymptotic complexity.',                        estMinutes: 30, topicIds: ['t_bigo'], mastery: 0.80 },
  { id: 'ch_iam',        courseId: 'c_aws',      index: 1, name: 'IAM',                  blurb: 'Users, roles, policies.',                       estMinutes: 60, topicIds: ['t_iam'], mastery: 0.65 },
  { id: 'ch_s3',         courseId: 'c_aws',      index: 2, name: 'S3 — Object storage',  blurb: 'Buckets, classes, lifecycle.',                  estMinutes: 60, topicIds: ['t_s3'], mastery: 0.40 }
];

// ── Topics + concepts (lighter, only the ones actively used) ────────
export const seedTopics: Topic[] = [
  { id: 't_light',  chapterId: 'ch_photosynth', name: 'Light reactions',
    content: [
      'Light-dependent reactions occur in the thylakoid membranes of chloroplasts.',
      'Photosystem II splits water, releasing oxygen and producing electrons that travel down the electron transport chain.',
      'The chain pumps protons into the thylakoid lumen, building a gradient that ATP synthase uses to generate ATP.',
      'NADP⁺ is reduced to NADPH at the end of the chain. ATP and NADPH then power the Calvin cycle.'
    ],
    conceptIds: ['k_pii','k_etc','k_atpsy','k_nadph']
  },
  { id: 't_calvin', chapterId: 'ch_photosynth', name: 'Calvin cycle',
    content: [
      'The Calvin cycle uses ATP and NADPH from the light reactions to fix CO₂ into glucose.',
      'Three phases: carbon fixation (RuBisCO + RuBP → 3-PGA), reduction (3-PGA → G3P), regeneration (G3P → RuBP).'
    ],
    conceptIds: ['k_rubisco','k_g3p']
  }
];

export const seedConcepts: Concept[] = [
  { id: 'k_pii',     topicId: 't_light',  name: 'Photosystem II',                 mastery: 0.45, lastSeen: new Date(Date.now() - 86400e3).toISOString() },
  { id: 'k_etc',     topicId: 't_light',  name: 'Electron transport chain',       mastery: 0.30, lastSeen: new Date(Date.now() - 2*86400e3).toISOString() },
  { id: 'k_atpsy',   topicId: 't_light',  name: 'ATP synthase',                   mastery: 0.55 },
  { id: 'k_nadph',   topicId: 't_light',  name: 'NADPH',                          mastery: 0.40 },
  { id: 'k_rubisco', topicId: 't_calvin', name: 'RuBisCO',                        mastery: 0.20 },
  { id: 'k_g3p',     topicId: 't_calvin', name: 'G3P (Glyceraldehyde 3-phosphate)', mastery: 0.15 }
];

// ── Notes ───────────────────────────────────────────────────────────
export const seedNotes: Note[] = [
  {
    id: 'n_1',
    title: 'Photosynthesis — light reactions',
    body: 'Two photosystems: PSII (P680) → ETC → PSI (P700) → NADP+.\nWater-splitting at PSII releases O2.\nProton gradient drives ATP synthase.\n\nKey equation: 2H2O + 2NADP+ + 3ADP + 3Pi → O2 + 2NADPH + 3ATP',
    chapterId: 'ch_photosynth',
    tags: ['biology', 'class-xi'],
    createdAt: new Date(Date.now() - 3*86400e3).toISOString(),
    updatedAt: new Date(Date.now() - 86400e3).toISOString()
  },
  {
    id: 'n_2',
    title: 'IAM mental model',
    body: 'Principal → Action → Resource (under Conditions).\nPolicies are JSON. Effect: Allow / Deny.\nDeny always wins. Implicit deny is the default.\nRoles are assumable identities — used for cross-account & EC2.',
    chapterId: 'ch_iam',
    tags: ['aws', 'cert'],
    createdAt: new Date(Date.now() - 86400e3).toISOString(),
    updatedAt: new Date().toISOString()
  },
  {
    id: 'n_3',
    title: 'Big-O cheat sheet',
    body: 'O(1), O(log n), O(n), O(n log n), O(n²), O(2ⁿ), O(n!).\nDrop constants and lower terms.\nWorst-case unless otherwise stated.',
    chapterId: 'ch_bigo',
    tags: ['cs'],
    createdAt: new Date(Date.now() - 7*86400e3).toISOString(),
    updatedAt: new Date(Date.now() - 5*86400e3).toISOString()
  }
];

// ── Flashcards (due now) ────────────────────────────────────────────
export const seedFlashcards: Flashcard[] = [
  { id: 'f_1', conceptId: 'k_pii',     q: 'What does Photosystem II split?', a: 'Water (H₂O), releasing O₂ and freeing electrons + protons.', ease: 2.4, intervalDays: 1, due: new Date().toISOString() },
  { id: 'f_2', conceptId: 'k_etc',     q: 'What does the electron transport chain build a gradient of?', a: 'A proton (H⁺) gradient across the thylakoid membrane.', ease: 2.5, intervalDays: 2, due: new Date().toISOString() },
  { id: 'f_3', conceptId: 'k_rubisco', q: 'What enzyme fixes CO₂ in the Calvin cycle?', a: 'RuBisCO (Ribulose-1,5-bisphosphate carboxylase/oxygenase).', ease: 2.2, intervalDays: 1, due: new Date().toISOString() },
  { id: 'f_4', conceptId: 'k_atpsy',   q: 'How does ATP synthase use the proton gradient?', a: 'Protons flow back through it, driving rotation that phosphorylates ADP → ATP.', ease: 2.5, intervalDays: 3, due: new Date().toISOString() },
  { id: 'f_5', conceptId: 'k_nadph',   q: 'What carries reducing power from light reactions to the Calvin cycle?', a: 'NADPH.', ease: 2.6, intervalDays: 4, due: new Date().toISOString() }
];

// ── Quiz ────────────────────────────────────────────────────────────
export const seedQuestions: QuizQuestion[] = [
  {
    id: 'q_1', kind: 'mcq', conceptId: 'k_pii',
    prompt: 'In photosynthesis, where does the splitting of water occur?',
    choices: ['Photosystem I', 'Photosystem II', 'ATP synthase', 'Calvin cycle'],
    correct: [1],
    explanation: 'PSII contains the oxygen-evolving complex that splits H₂O to replace electrons lost from P680.'
  },
  {
    id: 'q_2', kind: 'mcq', conceptId: 'k_etc',
    prompt: 'The proton gradient built across the thylakoid membrane drives:',
    choices: ['CO₂ fixation', 'ATP synthesis via ATP synthase', 'NADP⁺ → NADPH', 'Water splitting'],
    correct: [1],
    explanation: 'Protons re-enter the stroma through ATP synthase, phosphorylating ADP → ATP (chemiosmosis).'
  },
  {
    id: 'q_3', kind: 'multi', conceptId: 'k_rubisco',
    prompt: 'Select all that are produced by the Calvin cycle:',
    choices: ['G3P', 'O₂', 'ADP + Pi', 'NADP⁺'],
    correct: [0, 2, 3],
    explanation: 'Calvin cycle outputs G3P (sugar precursor) and recycles ADP+Pi and NADP⁺ back to the light reactions. O₂ comes from the light reactions, not Calvin.'
  },
  {
    id: 'q_4', kind: 'short', conceptId: 'k_g3p',
    prompt: 'In one sentence, what is the role of G3P in photosynthesis?',
    ideal: 'G3P is the three-carbon sugar product of the Calvin cycle that is used to build glucose and other organic molecules.',
    explanation: 'Two G3P molecules combine to form one glucose; G3P is also the cycle’s exit point.'
  }
];

export const seedQuizzes: Quiz[] = [
  { id: 'qz_photo', title: 'Photosynthesis — quick check', questionIds: ['q_1','q_2','q_3','q_4'], estMinutes: 8 }
];

// ── Sources (library) ───────────────────────────────────────────────
export const seedSources: Source[] = [
  { id: 'src_alberts',  kind: 'book',  title: 'Molecular Biology of the Cell',         author: 'Alberts et al.',   cited: ['ch.14'],            trust: 0.95 },
  { id: 'src_lehninger',kind: 'book',  title: 'Principles of Biochemistry',            author: 'Lehninger',        cited: ['ch.19', 'ch.20'],   trust: 0.95 },
  { id: 'src_khan_photo',kind: 'video',title: 'Photosynthesis — Khan Academy',         author: 'Khan Academy',     cited: ['4:21', '11:08'],   trust: 0.85 },
  { id: 'src_aws_iam',  kind: 'web',   title: 'AWS IAM — Best practices',              author: 'AWS Docs',         url: 'https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html', cited: ['IAM-2024-04'], trust: 0.92 },
  { id: 'src_clrs',     kind: 'book',  title: 'Introduction to Algorithms (CLRS)',     author: 'Cormen et al.',    cited: ['ch.3'],            trust: 0.96 },
  { id: 'src_pdf_jee',  kind: 'pdf',   title: 'JEE 2024 — Physics paper (annotated)',                                cited: ['p.14','p.22'],     trust: 0.80 }
];

// ── Plan items (today + tomorrow) ───────────────────────────────────
const today = new Date().toISOString().slice(0,10);
const tomorrow = new Date(Date.now()+86400e3).toISOString().slice(0,10);
export const seedPlan: PlanItem[] = [
  { id: 'p_1', date: today,    startTime: '08:30', durationMin: 25, kind: 'study',    title: 'Photosynthesis — finish light reactions', chapterId: 'ch_photosynth', done: true },
  { id: 'p_2', date: today,    startTime: '09:05', durationMin: 10, kind: 'practice', title: 'Flashcards (5 due)',                                                done: true },
  { id: 'p_3', date: today,    startTime: '17:00', durationMin: 20, kind: 'review',   title: 'Yesterday\'s mistakes — IAM policies',     chapterId: 'ch_iam',       done: false },
  { id: 'p_4', date: today,    startTime: '17:30', durationMin: 15, kind: 'quiz',     title: 'Photosynthesis — quick check',             quizId: 'qz_photo',        done: false },
  { id: 'p_5', date: tomorrow, startTime: '08:30', durationMin: 30, kind: 'study',    title: 'Calvin cycle — full read',                 chapterId: 'ch_photosynth', done: false }
];

// ── Doubts ──────────────────────────────────────────────────────────
export const seedDoubts: Doubt[] = [
  {
    id: 'd_1',
    title: 'Why does the Calvin cycle not need light directly?',
    body: 'I get that it uses ATP + NADPH from the light reactions, but my textbook calls it the "dark reaction" — does it actually run only in the dark?',
    conceptId: 'k_rubisco',
    tags: ['biology', 'photosynthesis'],
    authorName: 'Aarav',
    createdAt: new Date(Date.now() - 4*3600e3).toISOString(),
    answers: [
      { id: 'a_1', authorName: 'Ms. Iyer', authorRole: 'tutor', body: 'It runs whenever ATP and NADPH are available — usually in the day, right alongside the light reactions. "Dark reaction" is a historical misnomer; "light-independent" is the better term.', upvotes: 12, accepted: true,  createdAt: new Date(Date.now() - 3*3600e3).toISOString() },
      { id: 'a_2', authorName: 'Riya',     authorRole: 'peer',  body: 'Same confusion! Our teacher said RuBisCO is actually inhibited at night because the stroma pH drops.',                                                                                                  upvotes: 4,  accepted: false, createdAt: new Date(Date.now() - 2*3600e3).toISOString() }
    ]
  }
];

// ── Pipeline stage definitions ──────────────────────────────────────
export const PIPELINE_STAGES: PipelineStageDef[] = [
  { key: 'guardian',   label: 'Safety check',      detail: 'Validating prompt scope + safety policy.' },
  { key: 'diagnostic', label: 'Diagnose level',    detail: 'Estimating prior knowledge & gaps.' },
  { key: 'planner',    label: 'Plan lesson',       detail: 'Sequencing concepts: light reactions → Calvin cycle.' },
  { key: 'content',    label: 'Fetch sources',     detail: 'Pulling 3 textbook excerpts + 1 diagram.' },
  { key: 'debate',     label: 'Debate the answer', detail: 'Advocate vs Skeptic — 3 rounds, judge selects.' },
  { key: 'tutor',      label: 'Compose response',  detail: 'Inline citations, confidence ≥ 0.8.' },
  { key: 'practice',   label: 'Generate practice', detail: '4 cards · 1 short-answer · 1 misconception trap.' },
  { key: 'assessment', label: 'Score answer',      detail: 'Rubric: claim · evidence · clarity.' },
  { key: 'progress',   label: 'Update mastery',    detail: 'Concept mastery delta + streak.' }
];

// ── A canned debate (used both by Home demo and Tutor reply) ────────
export const seedDebate: DebateRound = {
  topic: 'Is the mitochondrion the powerhouse of the cell?',
  rounds: [
    {
      forClaim:    'Yes — mitochondria run oxidative phosphorylation, producing the bulk of cellular ATP.',
      forSource:   'Alberts, MBOC ch.14',
      againstClaim:'Glycolysis (cytosolic) and chloroplasts also produce ATP — "the" oversimplifies.',
      againstSource:'Lehninger ch.19'
    },
    {
      forClaim:    'In aerobic eukaryotes, mitochondria account for >90% of ATP under normal conditions.',
      forSource:   'Lehninger ch.20',
      againstClaim:'Erythrocytes lack mitochondria and rely entirely on glycolysis.',
      againstSource:'Alberts MBOC ch.14'
    }
  ],
  verdict: 'Both partially correct. Refined claim — "Mitochondria are the dominant ATP producer in aerobic eukaryotes, not the only source." Confidence 0.86.',
  verdictConfidence: 0.86
};

// ── A canned tutor reply for /tutor seeding ─────────────────────────
export const seedChat: ChatMessage[] = [
  {
    id: 'm_1', role: 'learner', body: 'Explain photosynthesis like I\'m 16 — and prove it.',
    createdAt: Date.now() - 60_000
  },
  {
    id: 'm_2', role: 'tutor',
    body: 'Photosynthesis is how plants turn sunlight + water + CO₂ into sugar + oxygen. It runs in two stages.\n\n**1. Light reactions** (thylakoid membranes): light excites electrons in Photosystem II, water is split (releasing O₂), and the electrons travel down a chain that pumps protons across the membrane. ATP synthase uses that gradient to make ATP. NADP⁺ is reduced to NADPH at the end.\n\n**2. Calvin cycle** (stroma): RuBisCO grabs CO₂ and combines it with RuBP. Using the ATP and NADPH from stage 1, the cycle reduces 3-PGA to G3P — a 3-carbon sugar that builds glucose.',
    confidence: 0.86,
    citations: [
      { sourceId: 'src_alberts',   locator: 'ch.14' },
      { sourceId: 'src_lehninger', locator: 'ch.19' },
      { sourceId: 'src_khan_photo', locator: '4:21' }
    ],
    debate: seedDebate,
    createdAt: Date.now() - 30_000
  }
];
