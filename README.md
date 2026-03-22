# SquadOS

> **Multi-Agent AI Framework for Java.**
> Spring Boot patterns for AI agents — write one class, get a working squad.

[![Tests](https://img.shields.io/badge/tests-136%20passing-brightgreen)]()
[![Java](https://img.shields.io/badge/java-21-blue)]()
[![Spring AI](https://img.shields.io/badge/spring--ai-1.0.0-green)]()
[![Version](https://img.shields.io/badge/version-1.2.0-orange)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()

---

## What is SquadOS?

SquadOS is a Java framework that lets you build teams of AI agents that work together.

Each agent has a **role** (Strategist, Analyst, Support, etc.), its own **personality** (via a prompt),
and can **talk to other agents** via a message bus. The framework handles everything else:
discovery, wiring, memory, circuit breaking, parallel execution.

| Spring Boot | SquadOS |
|---|---|
| `@Service` | `@Agent(role = AgentRole.STRATEGIST)` |
| `@Autowired` | `@OnMessage(from = AgentRole.TANK)` |
| `ApplicationContext` | `SquadContext` |
| `application.properties` | `squad.yml` |
| `@SpringBootApplication` | `@SquadApplication` |

---

## Quickstart — Hello Squad in 3 files

**1. Write an agent:**

```java
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "Oracle",
    description = "You are a tactical planner. Be concise and decisive."
)
public class OracleAgent {
    @PostConstruct
    public void init() { System.out.println("Oracle online."); }
}
```

**2. Configure** (`src/main/resources/squad.yml`):

```yaml
squad:
  name: my-squad
  llm:
    provider: ollama      # or: anthropic, openai, azure-openai
    model: llama3.2
  agents:
    - class: com.example.OracleAgent
```

**3. Run:**

```java
@SpringBootApplication
@SquadApplication
public class Main {
    public static void main(String[] args) {
        SquadConfigBridge.applyToSystemProperties();
        SpringApplication.run(Main.class, args);
    }

    @Bean LlmPort llmPort(ChatClient.Builder b) { return new SpringAiLlmAdapter(b); }
    @Bean SquadContext squadContext(LlmPort llm) { return SquadRunner.run(Main.class, llm); }

    @Bean ApplicationRunner runner(SquadContext ctx) {
        return args -> System.out.println(ctx.submit("Plan the mission").content());
    }
}
```

---

## Real-world example — Daily Planner

Paste your messy to-do brain dump. Three agents organise your day in ~14 seconds.
**Learns your patterns over time** — after a week, Oracle knows you always defer "learn kubernetes".

```
> reply to sarah, fix auth bug, buy groceries, learn kubernetes, call mum...

[Memory] Found patterns from past sessions:
• User repeatedly defers: learn kubernetes (dropped on 2026-03-20)
• User repeatedly defers: organise my desk (dropped on 2026-03-19)

YOUR PLAN FOR TODAY
────────────────────
DO TODAY:   Fix auth bug  Reply to Sarah  Call mum
DO LATER:   Prepare Friday slides  Review PR
DROP IT:    Learn Kubernetes  Organise desk (you never do these)

TIME REALITY CHECK  135 min  VERDICT: Realistic

YOUR COACH SAYS
────────────────────
QUICK WIN: Reply to Sarah (10 min, done).
WATCH OUT: Auth bug — set a 25-min timer and just start.
START WITH: Open Sarah email, reply, close inbox, then the bug.

Done in 13752ms (2.2x faster than sequential)
[Memory] Session saved to pgvector. Total memories: 5
```

**In-memory mode (no infrastructure needed):**
```bash
ollama pull llama3.2
cd squad-examples/daily-planner
mvn spring-boot:run
```

**Persistent mode (remembers across sessions):**
```bash
docker-compose up -d          # starts PostgreSQL + pgvector
mvn spring-boot:run -Dspring.profiles.active=pgvector
```

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK  | 21+     | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+  | [maven.apache.org](https://maven.apache.org) |
| Ollama | latest | [ollama.ai](https://ollama.ai) — local LLM, no API key |
| Docker | latest | Optional — only for pgvector persistent memory |

---

## Installation

```bash
git clone https://github.com/sgpatel/squad-os
cd squad-os
mvn clean install -DskipTests
```

---

## Switching LLM providers

Change two lines in `squad.yml` — **zero code changes**:

```yaml
# Local Ollama (no API key)
llm:
  provider: ollama
  model: llama3.2

# Anthropic Claude
llm:
  provider: anthropic
  model: claude-sonnet-4-6

# OpenAI
llm:
  provider: openai
  model: gpt-4o
```

---

## Framework features

### @Agent — define an AI agent

```java
@Agent(role = AgentRole.SUPPORT, name = "NurseBot",
       description = "You heal teammates. Be calm and fast.")
public class NurseBotAgent {
    @PostConstruct
    public void init() { System.out.println("NurseBot ready."); }
}
```

### Parallel execution — v1.1

Run multiple agents **simultaneously**. Wall-clock time = slowest agent, not sum:

```java
SquadResult result = ctx.execute(
    SquadTask.of(input)
        .assignTo(AgentRole.STRATEGIST, AgentRole.ANALYST, AgentRole.SUPPORT)
        .withLabel("Daily Planning")
);

result.get(AgentRole.STRATEGIST).content()  // plan
result.get(AgentRole.ANALYST).content()     // time estimates
result.get(AgentRole.SUPPORT).content()     // coaching

System.out.println("Done in " + result.wallClockMs() + "ms");
System.out.println("Speedup: " + result.speedupRatio() + "x");
```

**Measured on Daily Planner (3 x llama3.2 agents):**

| Mode | Time |
|------|------|
| Sequential (v1.0) | ~31 seconds |
| Parallel (v1.1) | ~14 seconds |
| **Speedup** | **2.3x** |

Uses Java 21 virtual threads — one per LLM call, no pool sizing needed.

### Persistent memory — v1.2

Agents remember across sessions using pgvector + Ebbinghaus decay:

```java
@Memory(type = MemoryType.EPISODIC, scope = MemoryScope.SQUAD,
        op = MemoryOp.READ_WRITE, topK = 3, importance = Importance.HIGH)
public SquadPlan buildPlan(TaskContext ctx) { ... }
```

**Memory tiers:**

| Tier | Backend | Survives restart | Use for |
|------|---------|-----------------|---------|
| WORKING | Redis / in-process | No (2h TTL) | Current task scratchpad |
| EPISODIC | pgvector HNSW | Yes | Past session history |
| SEMANTIC | Redis KV | Yes | Known facts |
| PROCEDURAL | PostgreSQL | Yes | Learned playbooks |

**Wire in Spring Boot:**

```java
// Development (no infrastructure)
@Bean
public MemoryRouter memoryRouter() {
    return MemoryStoreFactory.inProcess();
}

// Production (PostgreSQL + pgvector)
@Bean
public MemoryRouter memoryRouter(DataSource ds) {
    return MemoryStoreFactory.withPgVector(ds, 64);
}
```

**One-command PostgreSQL + pgvector setup:**

```bash
docker-compose up -d
```

**Toggle persistent memory — no code change:**

```properties
# Off (default) — in-memory, no Docker needed
squados.memory.pgvector.enabled=false

# On — persists to PostgreSQL, agents learn over time
squados.memory.pgvector.enabled=true
```

### @OnMessage — agents talk to each other

```java
@OnMessage(from = AgentRole.TANK, type = MessageType.HP_CRITICAL)
public void emergencyHeal(AgentMessage msg) {
    int hp = msg.getPayload(Integer.class);
    System.out.println("Healing — HP was: " + hp);
}
```

### AgentCircuitBreaker — agents self-heal

If an agent fails 3 times, circuit opens automatically.
Framework routes to next healthy agent.
Auto-recovers on success. Publishes CIRCUIT_OPEN event to bus.

---

## Agent roles

| Role | Default temp | Use for |
|------|-------------|---------|
| STRATEGIST | 0.5 | Planning, coordination |
| ANALYST | 0.4 | Research, analysis |
| EXECUTOR | 0.3 | Building, coding |
| SUPPORT | 0.2 | Healing, assisting |
| TANK | 0.3 | Gatekeeping, validation |
| DPS | 0.8 | Generation, ideation |
| RESEARCHER | 0.2 | Fact-finding |
| WRITER | 0.6 | Content creation |
| VISIONARY | 0.9 | Concept generation |

---

## Test suite

```bash
mvn clean test
```

```
Phase 1 — Core framework              17/17
Phase 2 — Memory layer                17/17
Phase 3 — Message bus                 17/17
Phase 4 — Circuit breaker             17/17
Phase 5 — Concurrent hardening        17/17
Phase 6 — Spring AI wiring            17/17
Phase 7 — Parallel agents             17/17
Phase 8 — pgvector memory stores      17/17
Total: 136 tests  0 failed  8 phases
```

---

## Project structure

```
squad-os/
├── squad-core/                   The framework (ZERO runtime dependencies)
│   └── src/main/java/io/squados/
│       ├── annotation/           @Agent @OnMessage @Memory @MissionProfile @PostConstruct
│       ├── context/              SquadContext AgentWrapper AgentRegistry
│       │                         MissionState TokenBudget SquadRunner
│       ├── execution/            SquadTask SquadResult ParallelExecutor
│       ├── bus/                  AgentMessageBus AgentMessage MessageType
│       ├── memory/
│       │   ├── store/            MemoryRecord MemoryStore
│       │   │                     InProcessMemoryStore PgVectorEpisodicStore
│       │   │                     RedisWorkingStore MemoryStoreFactory
│       │   ├── retrieval/        MemoryRouter EmbeddingPort MockEmbeddingPort
│       │   └── decay/            MemoryDecayService
│       ├── health/               AgentCircuitBreaker AgentHealth
│       ├── llm/                  LlmPort LlmOptions LlmResponse MockLlmPort
│       └── config/               SquadConfigParser SquadConfigBridge
│
├── squad-starter/                Hello Squad (Ollama quickstart, working live demo)
│
└── squad-examples/
    └── daily-planner/            3-agent planner with pgvector persistent memory
        ├── docker-compose.yml    One-command PostgreSQL + pgvector
        ├── PlannerMemoryService  Saves plans, retrieves past patterns
        └── MemoryConfig          @ConditionalOnProperty — pgvector on/off
```

---

## Architecture decisions

**Why not just use Spring AI or LangChain4j directly?**
Both are excellent — SquadOS uses them under the hood. SquadOS adds the **team layer**:
role-typed agents, inter-agent messaging, memory that survives sessions, and circuit breaking.
Spring AI handles LLM calls; SquadOS handles the squad.

**Why Spring Boot style?**
Developers already know @Service, @Autowired, application.properties.
The learning overhead is close to zero.

**Why zero deps in squad-core?**
No runtime dependencies. Adapters live in consumer modules.
Use SquadOS with any LLM provider without pulling in ones you don't need.

**Why pgvector for memory?**
Episodic memory needs semantic similarity search — "find past sessions similar to today's tasks".
pgvector's HNSW index makes this fast even with thousands of memories.
Ebbinghaus decay ensures old irrelevant memories fade while frequently-accessed ones stay sharp.

---

## Roadmap

- [x] Core framework with @Agent, LlmPort, SquadContext — **v1.0.0**
- [x] Parallel multi-agent execution — **v1.1.0** (2.3x speedup)
- [x] pgvector persistent memory — **v1.2.0** (agents learn your patterns)
- [ ] More examples: Code Review Squad, Research Assistant, Customer Support
- [ ] Maven Central (io.github.sgpatel:squad-core:1.2.0)
- [ ] Multi-node squads via Redis Pub/Sub

---

## License

Apache 2.0

Built with SquadOS v1.2.0 · Java 21 · Spring AI 1.0.0 · Ollama llama3.2 · pgvector
