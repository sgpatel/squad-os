# SquadOS

> Role-based multi-agent AI framework for Java.  
> Spring Boot for AI agents — write one class, get a working AI squad.

[![Tests](https://img.shields.io/badge/tests-119%20passing-brightgreen)]()
[![Java](https://img.shields.io/badge/java-21-blue)]()
[![Spring AI](https://img.shields.io/badge/spring--ai-1.0.0-green)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()

---

## What is SquadOS?

SquadOS is a Java framework that lets you build teams of AI agents that work together.

Each agent has a **role** (Strategist, Analyst, Support, etc.), its own **personality** (via a prompt), and can **talk to other agents** via a message bus. The framework handles everything else: discovery, wiring, memory, circuit breaking, health monitoring.

Think of it like this:

| Spring Boot | SquadOS |
|---|---|
| `@Service` | `@Agent(role = AgentRole.STRATEGIST)` |
| `@Autowired` | `@OnMessage(from = AgentRole.TANK)` |
| `ApplicationContext` | `SquadContext` |
| `application.properties` | `squad.yml` |
| `@SpringBootApplication` | `@SquadApplication` |

---

## Quickstart — Hello Squad in 3 files

**1. Write an agent** (the only class you write):

```java
@Agent(
    role        = AgentRole.STRATEGIST,
    name        = "Oracle",
    description = "You are a tactical planner. Be concise and decisive."
)
public class OracleAgent {
    @PostConstruct
    public void init() {
        System.out.println("Oracle online.");
    }
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

Paste your messy to-do brain dump. Three agents organise your day in ~10 seconds.

```
> reply to sarah's email, fix the auth bug, buy groceries, 
> prepare slides for friday, learn kubernetes, call mum...

📋 YOUR PLAN FOR TODAY
──────────────────────
DO TODAY:
1. Fix the auth bug
2. Reply to Sarah's email  
3. Call mum

DO LATER: Prepare Friday slides, review John's PR

DROP IT: Learn Kubernetes (not today), Organise desk

⏱  TIME REALITY CHECK
──────────────────────
Fix auth bug — 90 min | Reply Sarah — 15 min | Call mum — 30 min
TOTAL: 135 minutes  VERDICT: Realistic ✓

💪 YOUR COACH SAYS
──────────────────────
QUICK WIN: Reply to Sarah right now — 10 minutes, done.
WATCH OUT: The auth bug. Set a 25-min timer and just start.
START WITH: Open Sarah's email, reply, close inbox. Then the bug.
```

Run it:
```bash
ollama pull llama3.2
cd squad-examples/daily-planner
mvn spring-boot:run
```

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK  | 21+     | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+  | [maven.apache.org](https://maven.apache.org) |
| Ollama | latest | [ollama.ai](https://ollama.ai) — for local LLM (no API key) |

---

## Installation

```bash
git clone https://github.com/your-org/squad-os
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

Add the matching Spring AI starter to `pom.xml`:
```xml
<!-- Ollama -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>

<!-- Anthropic -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
</dependency>
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

### @OnMessage — agents talk to each other

```java
// NurseBot automatically heals when Tank's HP drops
@OnMessage(from = AgentRole.TANK, type = MessageType.HP_CRITICAL)
public void emergencyHeal(AgentMessage msg) {
    int hp = msg.getPayload(Integer.class);
    System.out.println("Healing — HP was: " + hp);
}
```

### @Memory — agents remember past sessions

```java
// Oracle stores and retrieves past battle plans automatically
@Memory(type = MemoryType.EPISODIC, scope = MemoryScope.SQUAD,
        op = MemoryOp.READ_WRITE, topK = 3, importance = Importance.HIGH)
public SquadPlan buildPlan(TaskContext ctx) { ... }
```

### AgentCircuitBreaker — agents self-heal

If an agent fails 3 times in a row, its circuit opens automatically.  
The framework routes to the next healthy agent.  
Recovery is automatic when the agent succeeds again.

---

## Agent roles

| Role | Personality | Default temp | Use for |
|------|-------------|-------------|---------|
| STRATEGIST | Balanced, big-picture | 0.5 | Planning, coordination |
| ANALYST | Data-driven, precise | 0.4 | Research, analysis |
| EXECUTOR | Structured, low variance | 0.3 | Building, coding |
| SUPPORT | Stable, consistent | 0.2 | Healing, assisting |
| TANK | Deterministic, defensive | 0.3 | Gatekeeping, validation |
| DPS | Creative, aggressive | 0.8 | Generation, ideation |
| RESEARCHER | Factual, conservative | 0.2 | Fact-finding |
| WRITER | Long-form, moderate | 0.6 | Content creation |
| VISIONARY | Bold, high creativity | 0.9 | Concept generation |

---

## Test suite

```bash
mvn clean test
```

```
Phase 1 — Core framework          17/17 ✓
Phase 2 — Memory layer            17/17 ✓
Phase 3 — Message bus             17/17 ✓
Phase 4 — Circuit breaker         17/17 ✓
Phase 5 — Concurrent hardening    17/17 ✓
Phase 6 — Spring AI wiring        17/17 ✓
─────────────────────────────────
Total: 102 tests  0 failed
```

---

## Project structure

```
squad-os/
├── squad-core/                  The framework (zero runtime dependencies)
│   └── src/main/java/io/squados/
│       ├── annotation/          @Agent, @OnMessage, @Memory, @MissionProfile
│       ├── context/             SquadContext, AgentWrapper, MissionState, TokenBudget
│       ├── bus/                 AgentMessageBus, AgentMessage, MessageType
│       ├── memory/              MemoryManager, four-tier storage, EmbeddingPort
│       ├── health/              AgentCircuitBreaker, AgentHealth
│       ├── llm/                 LlmPort, LlmOptions, MockLlmPort
│       └── config/              SquadConfigParser, SquadConfigBridge
│
├── squad-starter/               Hello Squad — Ollama quickstart
│
└── squad-examples/
    └── daily-planner/           3-agent daily planning assistant
```

---


## Parallel execution — v1.1

Run multiple agents simultaneously with `SquadContext.execute()`:

```java
SquadResult result = ctx.execute(
    SquadTask.of(brainDump)
        .assignTo(AgentRole.STRATEGIST, AgentRole.ANALYST, AgentRole.SUPPORT)
        .withLabel("Daily Planning")
);

// Each agent's response — produced concurrently
System.out.println(result.get(AgentRole.STRATEGIST).content()); // Plan
System.out.println(result.get(AgentRole.ANALYST).content());    // Time estimates
System.out.println(result.get(AgentRole.SUPPORT).content());    // Coaching

// Real performance metrics
System.out.println("Wall clock: " + result.wallClockMs() + "ms");
System.out.println("Speedup:    " + result.speedupRatio() + "x");
```

**Measured on Daily Planner (3 × llama3.2 agents):**

| Mode | Time | 
|------|------|
| Sequential (v1.0) | ~31 seconds |
| Parallel (v1.1) | ~14 seconds |
| Speedup | **2.3×** |

Uses Java 21 virtual threads — one per LLM call, no thread pool sizing needed.

## Architecture decisions

**Why not LangChain4j or Spring AI directly?**  
Both are excellent libraries that SquadOS uses under the hood. SquadOS adds the **team layer** on top — role-typed agents, inter-agent messaging, memory that survives sessions, and circuit breaking. Spring AI handles the LLM calls; SquadOS handles the squad.

**Why Spring Boot style?**  
Developers already know `@Service`, `@Autowired`, and `application.properties`. The cognitive overhead of learning SquadOS is close to zero.

**Why zero deps in squad-core?**  
`squad-core` has no runtime dependencies. Spring AI and LangChain4j adapters live in consumer modules. This means you can use SquadOS with any LLM provider without pulling in providers you don't need.

---

## Roadmap

- [x] Parallel multi-agent execution (`CompletableFuture.allOf`) — **v1.1 ✓**
- [ ] pgvector production backend for episodic memory  
  
- [ ] `@SquadPlan` structured output annotation  
- [ ] Maven Central publishing  
- [ ] More examples: code review squad, research assistant, customer support  

---

## License

Apache 2.0 — see [LICENSE](LICENSE)

Built with SquadOS v1.1.0 · Java 21 · Spring AI 1.0.0 · Ollama llama3.2
