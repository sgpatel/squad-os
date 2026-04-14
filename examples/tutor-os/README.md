# TutorOS — Personalised AI Tutoring System

> **The complete SquadOS v3.9.0 reference application.**
> Every annotation. Every feature. One meaningful product.

[![SquadOS](https://img.shields.io/badge/SquadOS-3.9.0-blue)]()
[![Java](https://img.shields.io/badge/Java-21-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-green)]()
[![Features](https://img.shields.io/badge/SquadOS%20features-14%2F14-brightgreen)]()
[![Agents](https://img.shields.io/badge/agents-13-purple)]()
[![Endpoints](https://img.shields.io/badge/REST%20endpoints-22-yellow)]()

---

## What is TutorOS?

TutorOS is an AI-powered personalised tutoring platform that adapts to every learner —
from a Grade 1 child to a PhD researcher. It diagnoses knowledge gaps, builds a custom
study plan, teaches at the right Bloom's level, generates visualisations (SVG / D3.js / Manim),
quizzes on demand, tracks mastery over time, and escalates to a human teacher when needed.

It is also the **canonical reference application** for SquadOS v3.9.0 — every annotation
in the framework is demonstrated here with a realistic, non-trivial use case.

---

## Feature Map — All 14 SquadOS Features

| # | SquadOS Feature | Agent | What It Does |
|---|---|---|---|
| 1 | `@Guardrails` | GuardianAgent | PII / prompt-injection / toxicity filter on every single turn |
| 2 | `@AgentMemory` | DiagnosticAgent, Tutors, PracticeAgent, QuizAgent | Vector-similarity recall of previous sessions, profiles, mistakes |
| 3 | `@StructuredOutput` | DiagnosticAgent, Curriculum, Practice, Assessment, Visualisation, Todo, Quiz | Typed Java model output with retry-on-malformed |
| 4 | `@AutoPlan` | CurriculumPlannerAgent, TodoAgent | Plan-execute-reflect-replan loop until all gaps covered |
| 5 | `@Debate` | SocraticTutorAgent vs DirectTutorAgent | Two agents compete to win the best teaching style for this learner |
| 6 | `@DurableAgent` | CurriculumPlannerAgent, TutoringPipeline, ProgressAgent | Checkpoint-persist state across JVM restarts; resume mid-session |
| 7 | `@Streaming` | DirectTutorAgent | Token-by-token streaming to the frontend via WebSocket |
| 8 | `@AwaitApproval` | EscalationAgent | Block session until human teacher manually reviews and responds |
| 9 | `@SquadTool` | ContentAgent | 5 LLM-callable tools: Khan Academy, Wolfram Alpha, Wikipedia, Analogy finder, Visualisation requester |
| 10 | `@RemoteSquad` | CurriculumPlannerAgent, EscalationAgent, VisualisationAgent | Proxy injection for LMS API, Teacher Portal, Manim rendering service |
| 11 | `@Benchmark` | DirectTutorAgent, PracticeAgent, AssessmentAgent, QuizAgent | Golden dataset evaluation — agents reject their own low-quality outputs |
| 12 | `@OptimizePrompt` | SocraticTutorAgent, DirectTutorAgent | DSPy-style prompt self-improvement based on mastery-gain signal |
| 13 | `@Pipeline + @Step` | TutoringPipeline | 10-step declarative pipeline with failFast=false and typed result routing |
| 14 | `@Traced` | Every agent + pipeline | OpenTelemetry span on every agent call and pipeline step |

---

## Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                           TutorOS Frontend                             │
│  Onboarding → Dashboard → Chat → Quiz → Progress → Chapters → Todos   │
└────────────────────────┬───────────────────────────────────────────────┘
                         │  HTTP REST + WebSocket
┌────────────────────────▼───────────────────────────────────────────────┐
│                        tutor-api (Spring Boot :8080)                   │
│  SessionController  PlanController  ProgressController  QuizController │
│  WebSocket: /ws/session/{id}/stream                                    │
└────────────────────────┬───────────────────────────────────────────────┘
                         │  TutoringPipeline.process()
┌────────────────────────▼───────────────────────────────────────────────┐
│                         TutoringPipeline  (@Pipeline, @DurableAgent)   │
│                                                                        │
│  Step 1 ──► GuardianAgent      @Guardrails    input safety check       │
│  Step 2 ──► DiagnosticAgent    @StructuredOutput + @AgentMemory        │
│               └─ first session / re-diagnostic every 5 sessions        │
│  Step 3 ──► CurriculumPlannerAgent  @AutoPlan + @RemoteSquad(LMS)      │
│  Step 4 ──► ContentAgent       5 @SquadTool methods                    │
│  Step 5 ──► DebateEngine       SocraticTutorAgent vs DirectTutorAgent  │
│               @Debate ─ selects best teaching style per learner        │
│  Step 6 ──► Winning Tutor      @Streaming + @OptimizePrompt            │
│  Step 7 ──► PracticeAgent      ZPD question (@StructuredOutput)        │
│  Step 8 ──► AssessmentAgent    @Benchmark grading + escalation check   │
│  Step 9 ──► EscalationAgent    @AwaitApproval + @RemoteSquad(Teacher)  │
│               (triggered only when distress or repeated failures)      │
│  Step 10 ─► ProgressAgent      @DurableAgent mastery update (720h TTL) │
│                                                                        │
│  PipelineResult (sealed):                                              │
│    TutorResponse | DiagnosticResult | PlanResult | FeedbackResult      │
│    SafeRefusal   | BlockedResult    | EscalationResult                 │
└────────────────────────────────────────────────────────────────────────┘
          │               │               │
          ▼               ▼               ▼
   VisualisationAgent  TodoAgent      QuizAgent
   SVG / D3.js / Manim  @AutoPlan     @Benchmark
   (on-demand)          (on-demand)   (on-demand)
```

---

## Learner Profiles

TutorOS adapts **everything** — language, examples, Bloom's level, analogy domain,
teaching style, question type — based on the learner profile set during onboarding.

### Levels

| Level | Audience | Language Style | Default Bloom's Target |
|---|---|---|---|
| `PRIMARY` | Grades 1-5 | Simple words, fun analogies, emoji OK | REMEMBER → UNDERSTAND |
| `MIDDLE_SCHOOL` | Grades 6-10 | Clear, encouraging, relatable examples | UNDERSTAND → APPLY |
| `SENIOR_SCHOOL` | Grades 11-12 | Exam-focused, worked examples | APPLY → ANALYSE |
| `UNIVERSITY` | Undergrad / Postgrad | Technical, peer-to-peer | ANALYSE → EVALUATE |
| `EDUCATOR` | Teachers / Lecturers | Pedagogical framing, curriculum context | EVALUATE → CREATE |
| `PROFESSIONAL` | Working adults | ROI-focused, use-case-first | APPLY → EVALUATE |

### Learning Styles (auto-detected by DiagnosticAgent)

| Style | How Content Is Shaped |
|---|---|
| `VISUAL` | Diagrams, SVG charts, D3 visualisations |
| `NARRATIVE` | Stories, analogies, real-world scenarios |
| `ANALYTICAL` | Proofs, derivations, step-by-step logic |
| `HANDS_ON` | Code snippets, lab instructions, practice-first |
| `SOCRATIC` | Guided questioning, Socratic dialogue |

### Bloom's Taxonomy Progression

```
REMEMBER → UNDERSTAND → APPLY → ANALYSE → EVALUATE → CREATE
    ▲
    └── ZPD: PracticeAgent always targets ONE level above current mastery
```

---

## Module Structure

```
examples/tutor-os/
├── pom.xml                          ← parent POM (Java 21, Spring Boot 3.3.0)
│
├── tutor-core/                      ← pure domain, no Spring dependency
│   └── src/main/java/io/squados/examples/tutoros/
│       ├── model/
│       │   ├── LearnerProfile.java        @StructuredOutput target
│       │   ├── StudyPlan.java             @StructuredOutput target
│       │   ├── PracticeQuestion.java      @StructuredOutput target
│       │   ├── AssessmentFeedback.java    @StructuredOutput target
│       │   ├── SessionSummary.java        @StructuredOutput target
│       │   ├── Quiz.java                  @StructuredOutput target
│       │   ├── TodoList.java              @StructuredOutput target
│       │   └── VisualAsset.java           @StructuredOutput target
│       │
│       ├── agent/
│       │   ├── GuardianAgent.java         @Guardrails + @RateLimit
│       │   ├── DiagnosticAgent.java       @StructuredOutput + @AgentMemory
│       │   ├── CurriculumPlannerAgent.java @AutoPlan + @DurableAgent + @RemoteSquad
│       │   ├── ContentAgent.java          5× @SquadTool + @Retry + @RateLimit
│       │   ├── SocraticTutorAgent.java    @Debate + @AgentMemory + @OptimizePrompt
│       │   ├── DirectTutorAgent.java      @Streaming + @Benchmark + @OptimizePrompt + @Debate
│       │   ├── PracticeAgent.java         @StructuredOutput + @AgentMemory + @Benchmark
│       │   ├── AssessmentAgent.java       @StructuredOutput + @Benchmark + @AgentMemory
│       │   ├── ProgressAgent.java         @AgentMemory + @DurableAgent
│       │   ├── EscalationAgent.java       @AwaitApproval + @RemoteSquad + @Traced
│       │   ├── VisualisationAgent.java    @StructuredOutput + @Retry + @RemoteSquad
│       │   ├── TodoAgent.java             @AutoPlan + @StructuredOutput + @AgentMemory
│       │   └── QuizAgent.java             @StructuredOutput + @Benchmark + @AgentMemory
│       │
│       ├── pipeline/
│       │   ├── TutoringPipeline.java      @Pipeline (10 steps) + @DurableAgent + @Traced
│       │   ├── SessionState.java          mutable mastery/Bloom's state + checkpoint
│       │   ├── PipelineResult.java        sealed interface (7 subtypes)
│       │   └── SessionManager.java        session lifecycle (start/resume/message/end)
│       │
│       └── resources/
│           └── benchmarks/
│               ├── tutor-explanation-golden.json
│               ├── practice-question-golden.json
│               ├── assessment-grading-golden.json
│               └── quiz-golden.json
│
└── tutor-api/                       ← Spring Boot application
    └── src/main/java/io/squados/examples/tutoros/
        ├── api/
        │   ├── SessionController.java     /session/*
        │   ├── PlanController.java        /plan/*/*
        │   ├── ProgressController.java    /progress/*/*
        │   └── QuizController.java        /quiz/*/*
        ├── config/
        │   └── TutorBeansConfig.java      explicit @Bean wiring
        ├── websocket/
        │   ├── WebSocketConfig.java       /ws/session/{id}/stream
        │   └── TutoringWebSocketHandler.java
        ├── TutorOsApplication.java
        └── resources/
            └── application.properties
```

---

## REST API Reference

### Session — `/session`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/session/start` | Start or resume a session (returns sessionId) |
| `POST` | `/session/{id}/message` | Send a chat message, receive PipelineResult |
| `POST` | `/session/{id}/answer` | Submit answer to a practice question |
| `POST` | `/session/{id}/quiz` | Generate an on-demand quiz |
| `POST` | `/session/{id}/end` | End session, generate SessionSummary |

### Plan — `/plan/{learnerId}/{subject}`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/plan/{l}/{s}` | Get current StudyPlan |
| `POST` | `/plan/{l}/{s}/generate` | Run CurriculumPlannerAgent (@AutoPlan) |
| `GET` | `/plan/{l}/{s}/chapters` | List chapters with DONE/ACTIVE/LOCKED status |
| `PUT` | `/plan/{l}/{s}/advance` | Advance to next chapter |
| `GET` | `/plan/{l}/{s}/chapter/{n}` | Chapter detail with AI-generated resources |
| `POST` | `/plan/{l}/{s}/refresh` | Regenerate after re-diagnostic |

### Progress — `/progress/{learnerId}`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/progress/{l}` | Cross-subject dashboard summary |
| `GET` | `/progress/{l}/{s}` | Subject progress (mastery%, sessions, goal status) |
| `GET` | `/progress/{l}/{s}/concepts` | Concept mastery grid with IMPROVING/STABLE/DECLINING trends |
| `GET` | `/progress/{l}/{s}/sessions` | Session history with expandable summaries |
| `GET` | `/progress/{l}/{s}/streak` | Streak counter + 30-day calendar |
| `GET` | `/progress/{l}/{s}/blooms` | Bloom's level progression timeline |
| `GET` | `/progress/{l}/{s}/quiz-history` | Quiz scores over time |
| `GET` | `/progress/{l}/{s}/goals` | Goal tracking with estimatedWeeksRemaining |
| `GET` | `/progress/{l}/{s}/report` | Teacher/parent report (aggregated parentNotes) |

### Quiz — `/quiz/{learnerId}/{subject}`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/quiz/{l}/{s}/generate` | Generate a new quiz (Easy/Medium/Hard, N questions) |
| `POST` | `/quiz/{l}/{s}/{quizId}/submit` | Submit answers, receive per-question feedback |
| `GET` | `/quiz/{l}/{s}/history` | All past quiz results with Bloom's breakdown |

### WebSocket — `/ws/session/{id}/stream`

Streams token-by-token tutor responses using `@Streaming` on `DirectTutorAgent`.
Connect via `ws://localhost:8080/ws/session/{sessionId}/stream`.
Each frame is a JSON `StreamFrame` with `type` (TOKEN | DONE | ERROR) and `content`.

---

## Pipeline Deep-Dive

### The 10-Step Pipeline

```
Input: (SessionState, userMessage)

Step 1 — Guardian Check
  @Guardrails(filters=[PiiDetector, PromptInjectionDetector, ToxicityFilter])
  → BLOCKED? return BlockedResult / SafeRefusal

Step 2 — Route
  isNewSession?  → DiagnosticAgent (@StructuredOutput LearnerProfile)
  isReDiagnostic?→ DiagnosticAgent (update profile)
  else           → continue with existing profile

Step 3 — Plan
  noActivePlan?  → CurriculumPlannerAgent (@AutoPlan gaps → StudyPlan)
  else           → continue

Step 4 — Content
  ContentAgent (@SquadTool) enriches context:
    searchKhanAcademy() · wolframAlpha() · wikipediaSummary()
    findAnalogy() · requestVisualisation()

Step 5 — Debate
  DebateEngine: SocraticTutorAgent vs DirectTutorAgent (2 rounds)
  Winner selected by confidence score; fallback → profile.preferredTeachingStyle

Step 6 — Teach
  Winning agent responds with level-adapted content
  @Streaming streams tokens to WebSocket clients

Step 7 — Practice
  PracticeAgent generates ONE ZPD question (Bloom's level + 1 above current)
  Type chosen: MULTIPLE_CHOICE / SHORT_ANSWER / CALCULATION / DIAGRAM_LABEL / ESSAY

Step 8 — Assess
  AssessmentAgent grades submitted answer
  masteryDelta applied to SessionState.masteryMap
  shouldEscalate()? → Step 9, else → Step 10

Step 9 — Escalate (conditional)
  EscalationAgent sends teacher brief (@RemoteSquad teacherPortal)
  @AwaitApproval blocks session
  resumes with teacher guidance injected into context

Step 10 — Progress
  ProgressAgent updates mastery, detects plateau, checks readyToAdvance()
  @DurableAgent checkpoints entire session state
```

### Sealed PipelineResult

```java
public sealed interface PipelineResult permits
    TutorResponse,       // normal teaching response
    DiagnosticResult,    // fresh LearnerProfile produced
    PlanResult,          // new StudyPlan generated
    FeedbackResult,      // AssessmentFeedback on submitted answer
    SafeRefusal,         // guardrails soft-blocked (off-topic)
    BlockedResult,       // guardrails hard-blocked (toxicity/injection)
    EscalationResult     // awaiting teacher approval
```

Java 21 pattern-matching switch in `SessionController` routes each subtype
to the appropriate HTTP response without casting.

---

## Visualisation Engine

TutorOS generates three types of visual content based on the concept being taught:

| Render Type | When Auto-Selected | Output |
|---|---|---|
| `SVG` | Default — diagrams, flowcharts, anatomical labels | Inline SVG markup rendered in chat |
| `D3JS` | Keywords: "graph", "rate", "plot", "distribution", "timeline" | JavaScript + D3 v7 script embedded in response |
| `MANIM` | Keywords: "animation", "mechanism", "wave", "orbit", "transformation" | Video URL from Manim render service |

### Future Scope

The `VisualisationAgent` is designed for progressive enhancement:

- **SVG Phase (current)** — AI generates SVG markup for static diagrams
- **D3.js Phase** — Interactive charts; student can hover, zoom, filter data
- **Manim Phase** — Mathematical animations (3Blue1Brown-style); `@RemoteSquad` calls a
  Python Manim microservice that renders MP4 and returns a URL
- **Three.js / WebGL** — 3D molecular structures, physics simulations
- **Canvas API** — Real-time drawing for freehand geometry problems

The `VisualAsset` model stores `svgMarkup`, `d3Script`, and `manimScript` / `videoUrl`
so all three types can be returned simultaneously when required.

---

## Running Locally

### Prerequisites

```bash
# Java 21
java -version  # openjdk 21.x

# Ollama with llama3.2
ollama pull llama3.2
ollama serve   # starts on http://localhost:11434

# Maven 3.8+
mvn -version
```

### Start TutorOS

```bash
cd examples/tutor-os
mvn clean install -DskipTests

# Run the API server
mvn -pl tutor-api spring-boot:run
```

Server starts at **http://localhost:8080**

Open the SPA at `examples/tutor-os/tutor-ui/src/index.html` directly in a browser
(static HTML — no build step required).

### Quickstart via cURL

```bash
# 1. Start a session
curl -X POST http://localhost:8080/session/start \
  -H "Content-Type: application/json" \
  -d '{"learnerId":"alice","subject":"mathematics","topic":"quadratic equations","levelHint":"SENIOR_SCHOOL","goal":"EXAM_PREP"}'

# Response: {"sessionId":"alice:mathematics","message":"..."}

# 2. Chat
curl -X POST http://localhost:8080/session/alice:mathematics/message \
  -H "Content-Type: application/json" \
  -d '{"message":"I dont understand why we complete the square"}'

# 3. Generate a quiz
curl -X POST http://localhost:8080/session/alice:mathematics/quiz \
  -H "Content-Type: application/json" \
  -d '{"difficulty":"MEDIUM","questionCount":5}'

# 4. Check progress
curl http://localhost:8080/progress/alice/mathematics/concepts

# 5. Get study plan chapters
curl http://localhost:8080/plan/alice/mathematics/chapters
```

### WebSocket Streaming

```javascript
const ws = new WebSocket("ws://localhost:8080/ws/session/alice:mathematics/stream");

ws.onmessage = (event) => {
  const frame = JSON.parse(event.data);
  if (frame.type === "TOKEN")  process.stdout.write(frame.content);
  if (frame.type === "DONE")   console.log("\n[stream complete]");
  if (frame.type === "ERROR")  console.error("[stream error]", frame.content);
};

// Trigger a message that streams back
fetch("/session/alice:mathematics/message", {
  method: "POST",
  headers: {"Content-Type":"application/json"},
  body: JSON.stringify({message: "Explain completing the square step by step"})
});
```

---

## Configuration Reference

`tutor-api/src/main/resources/application.properties`

```properties
# ── Server ────────────────────────────────────────────────────────────
server.port=8080

# ── LLM (Ollama default) ──────────────────────────────────────────────
squad.llm.model=llama3.2
spring.ai.ollama.base-url=http://localhost:11434

# ── SquadOS features ──────────────────────────────────────────────────
squad.memory.enabled=true          # @AgentMemory vector store
squad.guardrails.enabled=true      # @Guardrails filter pipeline
squad.durable.enabled=true         # @DurableAgent checkpoint store
squad.otel.enabled=false           # @Traced OTel export (set true + provide endpoint)

# ── MCP server (expose agents as MCP tools for Claude Desktop) ────────
squad.mcp.server.enabled=false
squad.mcp.server.port=3100

# ── Remote services (optional — leave blank to run without) ──────────
tutor.lms.url=                     # @RemoteSquad CurriculumPlannerAgent
tutor.teacher-portal.url=          # @RemoteSquad EscalationAgent
tutor.manim.url=                   # @RemoteSquad VisualisationAgent (Manim render)
```

---

## Agent Design Principles

### 1. Agents are POJOs
SquadOS agents are plain Java classes — no framework base class, no interface to implement.
Annotations drive all framework behaviour. This makes them testable without a Spring context.

### 2. SquadOS manages lifecycle, Spring manages wiring
Agents are declared as Spring `@Bean`s in `TutorBeansConfig` so that Spring DI injects them
into `TutoringPipeline`. SquadOS's `AgentRegistry` picks them up from the `ApplicationContext`
at startup and handles annotation processing, `@RemoteSquad` proxy injection, etc.

### 3. Prompts are methods
Every agent has one or more prompt-builder methods (e.g. `teachingPrompt()`, `gradingPrompt()`).
These are plain Java `String` methods — fully testable, diffable in git, and eligible for
`@OptimizePrompt` auto-improvement.

### 4. Structured output is always retried
All `@StructuredOutput` annotations set `retryOnMalformed=true`. If the LLM returns
malformed JSON, SquadOS re-prompts up to `maxRetries` times with the validation error
injected into the retry prompt.

### 5. Benchmark before shipping
`DirectTutorAgent`, `PracticeAgent`, `AssessmentAgent`, and `QuizAgent` all carry
`@Benchmark` pointing to golden datasets in `src/main/resources/benchmarks/`.
Any prompt change that drops quality below the `minScore` threshold fails the build.

---

## Testing with squad-test

```java
@ExtendWith(SquadTestExtension.class)
class DiagnosticAgentTest {

    @InjectAgent
    DiagnosticAgent agent;

    @Test
    void shouldProduceLearnerProfileWithBloomsLevel() {
        AgentTestResult result = AgentTestHarness.run(agent)
            .withInput("I am a grade 10 student studying photosynthesis")
            .withGolden("bloomsLevel", "UNDERSTAND")
            .execute();

        SquadAssertions.assertThat(result)
            .hasStructuredOutput(LearnerProfile.class)
            .fieldEquals("bloomsLevel", "UNDERSTAND")
            .fieldNotNull("gapConcepts")
            .meetsMinScore(0.80);
    }
}
```

---

## MCP Integration (squad-mcp-server)

When `squad.mcp.server.enabled=true`, TutorOS exposes all 13 agents as MCP tools,
making them available to Claude Desktop, Cursor, and any MCP-compatible client.

```json
// Claude Desktop mcp_servers config
{
  "tutoros": {
    "url": "http://localhost:3100/mcp",
    "transport": "http"
  }
}
```

Available MCP tools: `diagnostic_assess`, `generate_plan`, `teach_concept`,
`grade_answer`, `generate_quiz`, `generate_todos`, `visualise_concept`,
`get_progress`, `escalate_to_teacher`

---

## Future Roadmap

| Feature | Description | SquadOS Feature Used |
|---|---|---|
| **Voice Mode** | Whisper STT → TutorOS → TTS response | `@Streaming` + custom `TokenWriter` |
| **Peer Learning** | Match learners studying same topic | `@RemoteSquad` peer-match service |
| **Parent Dashboard** | Separate React app consuming `/progress/*/report` | Existing REST endpoints |
| **Offline Mode** | GraalVM native image for embedded deployment | `squad-mcp-server` native profile |
| **Manim Cloud** | Auto-render Manim scripts via GPU cloud service | `@RemoteSquad` + `@Retry` |
| **3D Visualisation** | Three.js molecule / orbital viewer | `VisualAsset.renderType = THREEJS` |
| **Adaptive Exams** | Full CAT (Computer Adaptive Testing) engine | `@AutoPlan` + `@Benchmark` |
| **Multi-Language** | Teach in Hindi, Spanish, French | `LearnerProfile.preferredLanguage` |

---

## License

MIT — part of the SquadOS examples, free for commercial use.

---

*Built with SquadOS v3.9.0 · Java 21 · Spring Boot 3.3.0 · Spring AI 1.0.0*
