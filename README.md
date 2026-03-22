# SquadOS

> **Multi-Agent AI Framework for Java.**
> Spring Boot patterns for AI agents — write one class, get a working squad.

[![Tests](https://img.shields.io/badge/tests-340%20passing-brightgreen)]()
[![Java](https://img.shields.io/badge/java-21-blue)]()
[![Spring AI](https://img.shields.io/badge/spring--ai-1.0.0-green)]()
[![Version](https://img.shields.io/badge/version-3.2.0-orange)]()
[![Maven Central](https://img.shields.io/badge/Maven%20Central-3.2.0-blue)](https://central.sonatype.com/artifact/io.github.sgpatel/squad-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()

---

## What is SquadOS?

SquadOS is a Java framework for building teams of AI agents that work together.
Each agent has a **role**, its own **personality**, and can **talk to other agents**.
The framework handles everything: discovery, wiring, memory, parallel execution,
structured output, tool use, voting, approval pipelines, event triggers,
self-evaluation, agentic loops, observability, few-shot learning, security, and dynamic routing.

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

## Quickstart — Hello Squad

```java
@Agent(role = AgentRole.STRATEGIST, name = "Oracle",
       description = "You are a tactical planner. Be concise.")
public class OracleAgent { }
```

```yaml
# squad.yml
squad:
  name: my-squad
  llm:
    provider: ollama
    model: llama3.2
  agents:
    - class: com.example.OracleAgent
```

```java
@SpringBootApplication @SquadApplication
public class Main {
    @Bean LlmPort llmPort(ChatClient.Builder b) { return new SpringAiLlmAdapter(b); }
    @Bean SquadContext ctx(LlmPort llm) { return SquadRunner.run(Main.class, llm); }
    @Bean ApplicationRunner run(SquadContext ctx) {
        return args -> System.out.println(ctx.submit("Plan the mission").content());
    }
}
```

---

## Full feature set — 15 annotations, 20 phases, 340 tests

| Annotation | What it does | Since |
|-----------|-------------|-------|
| `@Agent` | Define an AI agent with role + personality prompt | v1.0 |
| `@SquadPlan` | Typed structured output — no more raw strings | v2.1 |
| `@SquadTool` | Agents that call real Java methods / APIs | v2.2 |
| `@AwaitApproval` | Pause execution, wait for human approval | v2.3 |
| `@AutoApproval` | Rule-based instant approval (no human needed) | v2.3 |
| `@SquadVote` | Multi-agent consensus voting | v2.4 |
| `@OnEvent` | Kafka / webhook / timer triggered agents | v2.5 |
| `@Eval` | Self-evaluation quality gate, auto-retry | v2.6 |
| `@AutoPlan` | Agentic plan-execute-reflect-replan loops | v2.7 |
| `@Traced` | OpenTelemetry spans, token tracking, latency | v2.8 |
| `@Improve` | Few-shot learning from human feedback | v2.9 |
| `@SecureAgent` | RBAC + JWT — authenticated and public modes | v3.0 |
| `@Delegate` | Dynamic agent routing at runtime | v3.1 |
| `@Memory` | Four-tier memory: Working/Episodic/Semantic/Procedural | v1.2 |
| `@OnMessage` | Agents message each other | v1.0 |

---

## Feature examples

### @SquadPlan — typed output
```java
@SquadPlan(description = "Daily task prioritisation")
public class DayPlan {
    @Required public List<String> doToday;
    public List<String> dropIt;
    public String verdict;
}
DayPlan plan = ctx.submit("Plan my day:\n" + tasks, DayPlan.class);
```

### @SquadTool — real API calls
```java
@SquadTool(description = "Get current stock price")
public String getStockPrice(@ToolParam(description = "Ticker") String ticker) {
    return stockService.getPrice(ticker);
}
```

### @SquadVote — consensus
```java
VoteCollector collector = new VoteCollector("fraud-check",
    VoteRule.UNANIMOUS, TieBreaker.ESCALATE, 3, 30);
collector.submit(Vote.approve("Clean", 1.0).withVoter("RiskAgent"), "RiskAgent");
collector.submit(Vote.reject("IP blocked", 1.0).withVoter("GeoAgent"), "GeoAgent");
VoteResult result = collector.resolve(); // REJECTED
```

### @AwaitApproval + @AutoApproval
```java
@AutoApproval(condition = "amount < 10000 AND riskScore < 0.3")
@AwaitApproval(reason = "Exceeds limit", escalateTo = "senior-underwriter")
public LoanDecision underwriteLoan(LoanApplication app) { ... }
```

### @Eval — quality gate
```java
@Eval(judge = AgentRole.CRITIC, minScore = 0.8f, retryOnFail = true, maxRetries = 3)
public String generateReport(String input) { ... }
```

### @AutoPlan — agentic loops
```java
@AutoPlan(goal = "Complete risk report with all sections",
          maxIterations = 5, stopCondition = "COMPLETE")
public String generateRiskReport(LoanApplication app) { return "## Report\n"; }
```

### @Improve — few-shot learning
```java
@Improve(label = "loan-underwriting", topK = 3, minExamples = 5)
public LoanDecision underwriteLoan(LoanApplication app) { ... }

// After human review — agent learns automatically:
engine.saveFeedback(method, input, output, FeedbackExample.Label.GOOD, "Caught the fraud signal");
```

### @SecureAgent — RBAC + JWT
```java
// Authenticated — compliance team only
@SecureAgent(roles = {"compliance"}, auditLog = true)
public CustomerPII getCustomerData(String id) { ... }

// Public — no auth needed
@SecureAgent(mode = AccessMode.PUBLIC)
public String getExchangeRates() { ... }

// Wire identity from JWT:
AgentIdentity identity = new JwtValidator().validate(token);
SecurityContext.set(identity);
```

### @Delegate — dynamic routing
```java
// LLM picks the best specialist at runtime
@Delegate(candidates = {AgentRole.ANALYST, AgentRole.RESEARCHER, AgentRole.EXECUTOR},
          strategy = DelegateStrategy.LLM_CHOICE, fallback = AgentRole.STRATEGIST)
public String routeTask(String input) { return input; }

// Rule-based routing — no LLM call needed
@Delegate(
    candidates  = {AgentRole.ANALYST,  AgentRole.RESEARCHER,      AgentRole.EXECUTOR},
    strategy    = DelegateStrategy.FIRST_MATCH,
    conditions  = {"data OR analysis", "research OR investigate", "build OR execute"}
)
public String ruleRoute(String input) { return input; }
```

### @Traced — observability
```java
@Traced(spanName = "fraud-detection", trackTokens = true)
public class FraudAgent { }

SquadTracer.configure(new LogTraceExporter());      // stdout
SquadTracer.configure(new InMemoryTraceExporter()); // testing
// Plug in: JaegerTraceExporter, DatadogTraceExporter, GrafanaTraceExporter
```

### Parallel execution (v1.1)
```java
SquadResult result = ctx.execute(
    SquadTask.of(input)
        .assignTo(AgentRole.STRATEGIST, AgentRole.ANALYST, AgentRole.SUPPORT)
);
System.out.println("Speedup: " + result.speedupRatio() + "x"); // ~2.3x
```

### Multi-node via Redis (v2.0)
```java
RedisAgentBus bus = new RedisAgentBus(jedisCommands, "my-squad");
SquadRegistry registry = new SquadRegistry(redis, "my-squad", "node-a");
registry.register(AgentRole.STRATEGIST, "Oracle");
// Agents on separate JVMs discover each other via Redis
```

---

## Examples

| Example | Features used |
|---------|--------------|
| **Daily Planner** | 3 agents, @SquadPlan, pgvector memory |
| **Code Review Squad** | Security + Quality + Suggestions, typed reports |
| **Multi-node Demo** | Node A (Oracle) + Node B (Blitz + NurseBot) via Redis |
| **Embedding Demo** | Mock vs real semantic similarity comparison |
| **Snack Thief Squad** | All 15 annotations — office pizza crime investigation 🍕 |

---

## Test suite

```
Phase 1-6   v1.0   Core framework                102/102 ✓
Phase 7     v1.1   Parallel agents                 17/17  ✓
Phase 8     v1.2   pgvector + Redis memory          17/17  ✓
Phase 9     v2.0   Multi-node Redis Pub/Sub         17/17  ✓
Phase 10    v2.1   @SquadPlan typed output          17/17  ✓
Phase 11    v2.2   @SquadTool real API calls         17/17  ✓
Phase 12    v2.3   @AwaitApproval + @AutoApproval   17/17  ✓
Phase 13    v2.4   @SquadVote consensus             17/17  ✓
Phase 14    v2.5   @OnEvent event-driven            17/17  ✓
Phase 15    v2.6   @Eval quality gate               17/17  ✓
Phase 16    v2.7   @AutoPlan agentic loops          17/17  ✓
Phase 17    v2.8   @Traced observability            17/17  ✓
Phase 18    v2.9   @Improve few-shot learning       17/17  ✓
Phase 19    v3.0   @SecureAgent RBAC + JWT          17/17  ✓
Phase 20    v3.1   @Delegate dynamic routing        17/17  ✓
────────────────────────────────────────────────────────
Total                                            340/340 ✓
```

```bash
mvn clean test  # run all 340 tests
```

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK  | 21+     | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+  | [maven.apache.org](https://maven.apache.org) |
| Ollama | latest | [ollama.ai](https://ollama.ai) — local LLM, no API key |
| Docker | latest | Optional — pgvector + Redis |

```bash
git clone https://github.com/sgpatel/squad-os
cd squad-os
mvn clean install -DskipTests
ollama pull llama3.2
cd squad-starter && mvn spring-boot:run
```

---

## Architecture decisions

**Why not just use Spring AI or LangChain4j directly?**
Both are excellent — SquadOS uses them under the hood.
SquadOS adds the **team layer**: 15 annotations covering everything from
role-typed agents to few-shot learning, security, and dynamic routing.
Zero runtime dependencies in squad-core.

**Why zero deps in squad-core?**
No runtime dependencies. Spring AI adapters live in consumer modules.
Use SquadOS with any LLM provider.

**Single-node vs multi-node — zero code change?**
Yes. Swap `AgentMessageBus` for `RedisAgentBus` in one `@Bean`.

---

## Published to Maven Central

| Version | Features |
|---------|---------|
| 1.2.0 | Core + parallel + pgvector |
| 2.0.0 | + Multi-node Redis Pub/Sub |
| 2.1.0 | + @SquadPlan typed output |

## Roadmap

- [x] v1.0 — Core @Agent framework
- [x] v1.1 — Parallel agents (2.3x speedup)
- [x] v1.2 — pgvector persistent memory
- [x] v2.0 — Multi-node Squads via Redis
- [x] v2.1 — @SquadPlan structured output
- [x] v2.2 — @SquadTool real API calls
- [x] v2.3 — @AwaitApproval + @AutoApproval
- [x] v2.4 — @SquadVote consensus
- [x] v2.5 — @OnEvent Kafka/webhook triggers
- [x] v2.6 — @Eval quality gate
- [x] v2.7 — @AutoPlan agentic loops
- [x] v2.8 — @Traced OpenTelemetry
- [x] v2.9 — @Improve few-shot learning
- [x] v3.0 — @SecureAgent RBAC + JWT
- [x] v3.1 — @Delegate dynamic routing
- [ ] v3.2 — Fraud Detection Squad (all 15 features end-to-end)

---

## License

Apache 2.0 · Built with SquadOS v3.1 · Java 21 · Spring AI 1.0 · Ollama · pgvector · Redis
