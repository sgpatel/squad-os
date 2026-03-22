# SquadOS

> **Multi-Agent AI Framework for Java.**
> Spring Boot patterns for AI agents — write one class, get a working squad.

[![Tests](https://img.shields.io/badge/tests-170%20passing-brightgreen)]()
[![Java](https://img.shields.io/badge/java-21-blue)]()
[![Spring AI](https://img.shields.io/badge/spring--ai-1.0.0-green)]()
[![Version](https://img.shields.io/badge/version-2.1.0-orange)]()
[![Maven Central](https://img.shields.io/badge/Maven%20Central-2.1.0-blue)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()

---

## What is SquadOS?

SquadOS is a Java framework for building teams of AI agents that work together.

Each agent has a **role** (Strategist, Analyst, Support, etc.), its own **personality** (via a prompt),
and can **talk to other agents** via a message bus. The framework handles everything else:
discovery, wiring, memory, circuit breaking, parallel execution, and structured output.

| Spring Boot | SquadOS |
|---|---|
| `@Service` | `@Agent(role = AgentRole.STRATEGIST)` |
| `@Autowired` | `@OnMessage(from = AgentRole.TANK)` |
| `ApplicationContext` | `SquadContext` |
| `application.properties` | `squad.yml` |
| `@SpringBootApplication` | `@SquadApplication` |
| JPA `@Entity` | `@SquadPlan` |

---

## Add to your project

```xml
<dependency>
  <groupId>io.github.sgpatel</groupId>
  <artifactId>squad-core</artifactId>
  <version>2.1.0</version>
</dependency>
```

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
**Learns your patterns over time** via pgvector. **Returns typed objects** via @SquadPlan.

```
> reply to sarah, fix auth bug, learn kubernetes, call mum...

[Memory] Found patterns from past sessions:
• User repeatedly defers: learn kubernetes
• User repeatedly defers: organise my desk

YOUR PLAN FOR TODAY        TIME REALITY CHECK       YOUR COACH SAYS
────────────────────       ──────────────────       ────────────────
DO TODAY:                  fix auth bug — 90 min    QUICK WIN: Reply to Sarah now
  1. Fix auth bug          reply sarah — 15 min     WATCH OUT: Auth bug — timer it
  2. Reply to Sarah        TOTAL: 135 min           START WITH: Open Sarah's email
  3. Call mum              VERDICT: Realistic

DROP IT: Learn Kubernetes, Organise desk (you never do these)

Done in 14184ms  |  [Memory] Session saved. Total memories: 8
```

```bash
ollama pull llama3.2
cd squad-examples/daily-planner
mvn spring-boot:run

# With persistent memory (pgvector):
docker-compose up -d
export SPRING_PROFILES_ACTIVE=pgvector
mvn spring-boot:run
```

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK  | 21+     | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+  | [maven.apache.org](https://maven.apache.org) |
| Ollama | latest | [ollama.ai](https://ollama.ai) — local LLM, no API key |
| Docker | latest | Optional — pgvector memory + Redis multi-node |

---

## Installation

```bash
git clone https://github.com/sgpatel/squad-os
cd squad-os
mvn clean install -DskipTests
```

---

## Framework features

### @Agent — define an AI agent

```java
@Agent(role = AgentRole.SUPPORT, name = "NurseBot",
       description = "You heal teammates. Be calm and fast.")
public class NurseBotAgent { }
```

### @SquadPlan — typed structured output — v2.1

Get typed Java objects instead of raw strings from your agents:

```java
// Define your output schema
@SquadPlan(description = "Daily task prioritisation")
public class DayPlan {
    @Required public List<String> doToday;
    public List<String> doLater;
    public List<String> dropIt;
    public String verdict;
}

// Get typed output — one line
DayPlan plan = ctx.submit("Plan my day:\n" + tasks, DayPlan.class);

// Use typed fields directly
plan.doToday.forEach(task -> System.out.println("→ " + task));
System.out.println("Verdict: " + plan.verdict);
```

The framework automatically builds a JSON schema from the class, appends it to the
agent's system prompt, and deserialises the response into the typed object.
No Jackson. No Gson. Pure Java reflection.

### Parallel execution — v1.1

Run multiple agents simultaneously. Wall-clock = slowest agent, not sum:

```java
SquadResult result = ctx.execute(
    SquadTask.of(input)
        .assignTo(AgentRole.STRATEGIST, AgentRole.ANALYST, AgentRole.SUPPORT)
        .withLabel("Daily Planning")
);
result.get(AgentRole.STRATEGIST).content()   // plan
System.out.println("Speedup: " + result.speedupRatio() + "x");
```

| Mode | Time |
|------|------|
| Sequential (v1.0) | ~31 seconds |
| Parallel (v1.1) | ~14 seconds |
| **Speedup** | **2.3×** |

### Persistent memory — v1.2

Agents remember across sessions using pgvector + Ebbinghaus decay:

```java
@Memory(type = MemoryType.EPISODIC, scope = MemoryScope.SQUAD,
        op = MemoryOp.READ_WRITE, topK = 3, importance = Importance.HIGH)
public SquadPlan buildPlan(TaskContext ctx) { ... }
```

| Tier | Backend | Persists | Use for |
|------|---------|---------|---------|
| WORKING | Redis / in-process | No (2h TTL) | Session scratchpad |
| EPISODIC | pgvector HNSW | Yes | Past session history |
| SEMANTIC | Redis KV | Yes | Known facts |
| PROCEDURAL | PostgreSQL | Yes | Learned playbooks |

```java
// Development
MemoryStoreFactory.inProcess()

// Production
MemoryStoreFactory.withPgVector(dataSource, 64)   // PostgreSQL only
MemoryStoreFactory.production(dataSource, redis, 64) // Full stack
```

### Multi-node Squads — v2.0

Agents on separate machines via Redis Pub/Sub:

```java
// Drop-in replacement for AgentMessageBus
RedisAgentBus bus = new RedisAgentBus(redisCommands, "my-squad");

// Distributed agent discovery
SquadRegistry registry = new SquadRegistry(redis, "my-squad", "node-a");
registry.register(AgentRole.STRATEGIST, "Oracle");
List<AgentRegistration> analysts = registry.find(AgentRole.ANALYST);

// Distributed circuit breaker — shared state across all nodes
RedisCircuitBreakerStore circuit = new RedisCircuitBreakerStore(redis, "my-squad");
circuit.setState(AgentRole.ANALYST, CircuitState.OPEN); // visible to ALL nodes
```

### @OnMessage — agents talk to each other

```java
@OnMessage(from = AgentRole.TANK, type = MessageType.HP_CRITICAL)
public void emergencyHeal(AgentMessage msg) {
    System.out.println("Healing — HP was: " + msg.getPayload(Integer.class));
}
```

### AgentCircuitBreaker — agents self-heal

If an agent fails 3 times, circuit opens. Framework routes to next healthy agent.
Auto-recovers on success. In multi-node mode, circuit state is shared via Redis.

---

## Switching LLM providers

Change two lines in `squad.yml` — zero code changes:

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
| WILDCARD | — | Broadcast / subscribe-all |

---

## Test suite

```bash
mvn clean test
```

```
Phase 1  — Core @Agent framework           17/17
Phase 2  — Four-tier memory layer          17/17
Phase 3  — AgentMessageBus / @OnMessage    17/17
Phase 4  — AgentCircuitBreaker             17/17
Phase 5  — MissionState / TokenBudget      17/17
Phase 6  — Spring AI + Ollama wiring       17/17
Phase 7  — Parallel agents (v1.1)          17/17
Phase 8  — pgvector + Redis stores (v1.2)  17/17
Phase 9  — Multi-node Squads (v2.0)        17/17
Phase 10 — @SquadPlan typed output (v2.1)  17/17
──────────────────────────────────────────
Total: 170 tests  0 failed  10 phases
```

---

## Project structure

```
squad-os/
├── squad-core/                   The framework (ZERO runtime dependencies)
│   └── src/main/java/io/squados/
│       ├── annotation/           @Agent @OnMessage @Memory @SquadPlan @Required
│       │                         @MissionProfile @PostConstruct AgentRole
│       ├── context/              SquadContext AgentWrapper AgentRegistry
│       │                         MissionState TokenBudget SquadRunner SquadRegistry
│       ├── execution/            SquadTask SquadResult ParallelExecutor
│       ├── bus/                  AgentMessageBus RedisAgentBus AgentMessage MessageType
│       ├── agent/                AgentResponse TaskContext SquadPlanDeserialiser
│       ├── memory/
│       │   ├── store/            InProcessMemoryStore PgVectorEpisodicStore
│       │   │                     RedisWorkingStore MemoryStoreFactory
│       │   └── retrieval/        MemoryRouter EmbeddingPort MockEmbeddingPort
│       ├── health/               AgentCircuitBreaker AgentHealth
│       │                         RedisCircuitBreakerStore
│       ├── llm/                  LlmPort LlmOptions LlmResponse MockLlmPort
│       ├── config/               SquadConfigParser SquadConfigBridge
│       └── exception/            SquadPlanException AgentConfigException
│
├── squad-starter/                Hello Squad (Ollama quickstart)
│
└── squad-examples/
    ├── daily-planner/            @SquadPlan typed output + pgvector memory
    ├── code-review/              3 specialist agents: Security + Quality + Fixes
    └── embedding-demo/           Mock vs real semantic similarity comparison
```

---

## Architecture decisions

**Why not just use Spring AI or LangChain4j directly?**
Both are excellent — SquadOS uses them under the hood. SquadOS adds the **team layer**:
role-typed agents, inter-agent messaging, persistent memory, circuit breaking, parallel
execution, and structured output. Spring AI handles LLM calls; SquadOS handles the squad.

**Why @SquadPlan instead of manual JSON parsing?**
LLM responses are unpredictable — sometimes markdown, sometimes plain JSON, sometimes
with explanations. @SquadPlan automatically strips code fences, validates required fields,
and gives you a typed Java object. Your code never touches raw LLM text.

**Why zero deps in squad-core?**
No runtime dependencies. Spring AI and adapters live in consumer modules. Use SquadOS
with any LLM provider without pulling in ones you don't need.

**Why pgvector for episodic memory?**
Episodic memory needs semantic similarity — "fix login bug" should match "auth service
broken". pgvector's HNSW index makes this fast at scale. Ebbinghaus decay keeps memory
relevant; old irrelevant memories fade, frequently-accessed ones stay sharp.

**Single-node vs multi-node — zero code change?**
Yes. Swap `AgentMessageBus` for `RedisAgentBus` in one `@Bean`. Same `@Agent`,
same `@OnMessage`, same `@Memory` — the framework routes automatically.

---

## Roadmap

- [x] Core @Agent framework — **v1.0.0**
- [x] Parallel multi-agent execution — **v1.1.0** (2.3x speedup)
- [x] pgvector persistent memory — **v1.2.0**
- [x] Multi-node Squads via Redis Pub/Sub — **v2.0.0**
- [x] @SquadPlan structured typed output — **v2.1.0**
- [ ] Phase 10 multi-node live demo (two Spring Boot apps via Redis)
- [ ] Research Assistant example
- [ ] Customer Support Squad example
- [ ] Maven Central publishing automation

---

## License

Apache 2.0

Built with SquadOS v2.1.0 · Java 21 · Spring AI 1.0.0 · Ollama llama3.2 · pgvector · Redis
