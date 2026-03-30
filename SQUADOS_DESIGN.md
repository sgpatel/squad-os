# SquadOS — Design Document

> **squad-core** `v3.4.0` · **squad-spring-boot-starter** `v3.4.0`  
> Multi-Agent AI Framework for Java  
> Maven Central: `io.github.sgpatel`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Design Goals](#2-design-goals)
3. [Architecture](#3-architecture)
4. [squad-core](#4-squad-core)
   - 4.1 [The 15 Annotations](#41-the-15-annotations)
   - 4.2 [Core Interfaces and Ports](#42-core-interfaces-and-ports)
   - 4.3 [The Agent Lifecycle](#43-the-agent-lifecycle)
   - 4.4 [Key Internal Components](#44-key-internal-components)
5. [squad-spring-boot-starter](#5-squad-spring-boot-starter)
   - 5.1 [Auto-Configuration](#51-auto-configuration)
   - 5.2 [application.properties Reference](#52-applicationproperties-reference)
6. [Data Flow](#6-data-flow)
7. [Extension Points](#7-extension-points)
8. [Dependency Policy](#8-dependency-policy)
9. [Examples](#9-examples)
10. [Version History](#10-version-history)

---

## 1. Overview

SquadOS is a **multi-agent AI framework for Java** that follows the same
convention-over-configuration philosophy as Spring Boot — but for orchestrating
LLM-powered agents.

The core idea: **annotate your classes, configure your LLM, and let SquadOS
handle the rest.** A squad is a group of specialised AI agents that collaborate,
vote, delegate, approve, and learn — all driven by 15 purpose-built annotations.

### What problem does it solve?

Building multi-agent AI systems in Java traditionally requires:
- Hand-wiring LLM prompts and responses
- Custom orchestration logic for agent-to-agent communication
- Ad-hoc approval workflows and escalation paths
- Manual observability and tracing

SquadOS eliminates this boilerplate. The same patterns that Spring Boot applied
to web services, SquadOS applies to AI agent pipelines.

### Module layout

```
io.github.sgpatel
├── squad-core                  ← Framework core (ZERO runtime dependencies)
└── squad-spring-boot-starter   ← Spring Boot auto-configuration
```

---

## 2. Design Goals

| Goal | How it is achieved |
|------|--------------------|
| **Zero runtime dependencies in squad-core** | No Spring, no LangChain4j, no Spring AI — pure Java 21 |
| **Annotation-driven** | 15 annotations cover the entire agent lifecycle |
| **Provider-agnostic LLM** | `LlmPort` interface — swap Ollama, OpenAI, Anthropic without changing agents |
| **Testable** | Agents are plain Java classes; `InMemoryTraceExporter` for testing |
| **Production-ready** | Redis trace store, circuit breakers, JWT security, approval workflows |
| **Spring-first DX** | One dependency + `@SquadApplication` = fully working squad |

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Your Application                             │
│                                                                     │
│  @SquadApplication          @Agent(role=ANALYST)                    │
│  public class App { ... }   public class MyAgent { ... }            │
└─────────────────────┬───────────────────────────────────────────────┘
                      │  uses
┌─────────────────────▼───────────────────────────────────────────────┐
│               squad-spring-boot-starter                             │
│                                                                     │
│  SquadAutoConfiguration — wires all beans on application start      │
│  SpringAiLlmAdapter     — bridges Spring AI → LlmPort               │
│  ConditionalOnMissingBean — every bean is overridable               │
└─────────────────────┬───────────────────────────────────────────────┘
                      │  depends on
┌─────────────────────▼───────────────────────────────────────────────┐
│                       squad-core                                    │
│                                                                     │
│  SquadRunner ──► SquadContext ──► AgentWrapper ──► LlmPort          │
│                                                                     │
│  Annotation processors: @SquadPlan, @SquadTool, @OnEvent, ...       │
│  Infrastructure:  VoteCollector, ApprovalStore, EventBus,           │
│                   FeedbackStore, SecurityGuard, SquadTracer         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. squad-core

**Maven coordinates:**
```xml
<dependency>
  <groupId>io.github.sgpatel</groupId>
  <artifactId>squad-core</artifactId>
  <version>3.4.0</version>
</dependency>
```

`squad-core` is the **pure Java framework** — no Spring, no Spring AI, no
external JSON library. It compiles and runs on any Java 21+ environment.

### 4.1 The 15 Annotations

#### Agent Declaration

##### `@Agent`
Declares a class as a SquadOS agent. Every agent must carry this annotation.

```java
@Agent(
    role        = AgentRole.ANALYST,      // STRATEGIST | ANALYST | RESEARCHER
                                          // SUPPORT | CRITIC | WILDCARD
    name        = "RiskAnalyst",          // display name used in logs + traces
    description = "You are a risk analyst...", // injected into every LLM system prompt
    profile     = "work"                  // optional: only active on this Spring profile
)
public class RiskAnalystAgent { }
```

**How it works internally:**  
`SquadRunner.run()` scans the classpath for classes annotated with `@Agent`,
wraps each one in an `AgentWrapper`, and registers them in the `SquadContext`.

---

##### `@SquadApplication`
Marks the Spring Boot entry point. Triggers classpath scanning for `@Agent`
classes within the same base package.

```java
@SpringBootApplication
@SquadApplication
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

---

##### `@MissionProfile`
Restricts an agent to a named Spring profile. The agent is registered and
active only when that profile is active.

```java
@Agent(role = AgentRole.RESEARCHER, name = "BehaviourAgent", description = "...")
@MissionProfile("work")
public class BehaviourAgent { }
```

---

#### LLM Interaction

##### `@SquadPlan`
Marks an inner static class as a **typed structured output** from the LLM.
Fields are populated by the LLM's JSON response and deserialised automatically.

```java
@SquadPlan(description = "Payment fraud risk assessment")
public static class RiskAssessment {
    @Required public String riskLevel;       // LOW | MEDIUM | HIGH | CRITICAL
    @Required public String riskScore;       // "0.0" to "1.0"
    public List<String>    riskFactors;
    public String          recommendation;   // APPROVE | REVIEW | BLOCK
}

// Usage:
RiskAssessment result = ctx.submitTo(
    AgentRole.ANALYST,
    "Assess this transaction: " + context,
    RiskAssessment.class
);
```

**How it works:**  
`AgentWrapper.execute()` builds a system prompt from `@Agent.description`,
appends the JSON schema derived from the `@SquadPlan` class fields, calls
`LlmPort.chatStructured()`, and returns the deserialised object.

---

##### `@Required`
Marks a field in a `@SquadPlan` class as mandatory. If the LLM returns null
for this field, the framework retries (up to the configured limit).

```java
@SquadPlan(description = "Decision")
public static class Decision {
    @Required public String outcome;   // must not be null
    public String           reason;    // optional
}
```

---

##### `@SquadTool`
Exposes a Java method as a callable tool for the LLM. The framework injects
tool definitions into the system prompt so the LLM can invoke them.

```java
@SquadTool(description = "Look up transaction history for a customer")
public String getTransactionHistory(
    @ToolParam(description = "Customer ID") String customerId,
    @ToolParam(description = "Number of days to look back") int days
) {
    return transactionService.getHistory(customerId, days);
}
```

---

##### `@ToolParam`
Annotates parameters of a `@SquadTool` method, providing the LLM with
descriptions of each parameter.

---

##### `@AutoPlan`
Triggers an **agentic plan-execute-reflect-replan loop**. The agent autonomously
iterates until the stop condition is met or `maxIterations` is reached.

```java
@AutoPlan(
    goal          = "Assess fraud risk until confidence > 0.85",
    maxIterations = 5,
    stopCondition = "COMPLETE",
    onMaxIterations = IterationPolicy.RETURN_BEST
)
public String investigateFraud() { return "started"; }
```

---

#### Agent Lifecycle

##### `@PostConstruct`
Lifecycle hook that runs once when the agent is registered and initialised.
Equivalent to Spring's `@PostConstruct` but works without Spring.

```java
@PostConstruct
public void init() {
    System.out.println("[RiskAnalyst] Agent online.");
}
```

---

##### `@OnMessage`
React to messages broadcast by other agents. The agent subscribes to messages
of a specific `MessageType` from a specific `AgentRole`.

```java
@OnMessage(from = AgentRole.ANALYST, type = MessageType.DIRECTIVE)
public void onRiskReport(AgentMessage message) {
    System.out.println("Received risk report: " + message.getPayload());
}
```

---

#### Workflow Orchestration

##### `@SquadVote`
Triggers a multi-agent consensus vote. Multiple agents submit `Vote` objects;
the `VoteCollector` resolves them according to the configured `VoteRule`.

```java
VoteCollector collector = new VoteCollector(
    "fraud-verdict",      // topic
    VoteRule.MAJORITY,    // MAJORITY | UNANIMOUS | WEIGHTED
    TieBreaker.ESCALATE,  // ESCALATE | REJECT | APPROVE
    3,                    // required voters
    30                    // timeout seconds
);
collector.submit(Vote.approve("Score clean", 1.0).withVoter("RiskAnalyst"), "RiskAnalyst");
collector.submit(Vote.reject("AML flag", 1.0).withVoter("ComplianceAgent"), "ComplianceAgent");
VoteResult result = collector.resolve();   // APPROVED | REJECTED | TIE | TIMEOUT
```

---

##### `@AwaitApproval`
Pauses execution and submits a request to the `ApprovalStore` for human
review. Execution resumes once a decision is made or the timeout elapses.

```java
@AwaitApproval(
    reason       = "High-risk transaction requires review",
    timeoutHours = 4,
    onTimeout    = TimeoutPolicy.REJECT,
    escalateTo   = "senior-fraud-analyst",
    priority     = ApprovalPriority.HIGH
)
public PaymentDecision underwrite(String context) { ... }
```

---

##### `@AutoApproval`
Evaluates a SpEL-style condition expression. If the condition is true,
automatically approves without human intervention.

```java
@AutoApproval(condition = "riskScore < 0.3 AND velocity < 5")
public PaymentDecision fastApprove(String context) { ... }
```

---

##### `@Delegate`
Routes a task to the most appropriate agent dynamically. Supports
`FIRST_MATCH`, `WEIGHTED`, and `ROUND_ROBIN` strategies.

```java
@Delegate(
    candidates  = {AgentRole.ANALYST, AgentRole.RESEARCHER},
    strategy    = DelegateStrategy.FIRST_MATCH,
    conditions  = {"urgent OR fraud OR P1", "analytics OR pattern OR batch"},
    fallback    = AgentRole.ANALYST,
    logDecision = true
)
public String routeTask(String taskContext) { return taskContext; }
```

---

##### `@OnEvent`
Makes an agent react to named events published on the `EventBus`.
Supports concurrent processing and automatic retry on error.

```java
@OnEvent(
    topic       = "payments.incoming",
    concurrency = 10,
    retryOnError = true,
    maxRetries  = 3
)
public void onPaymentReceived(SquadEvent event) {
    System.out.println("Payment: " + event.getPayload());
}
```

---

#### Quality and Learning

##### `@Eval`
Wraps a method with a quality gate. The LLM evaluates the output against
a rubric; if the score falls below `minScore`, the method is retried.

```java
@Eval(
    rubric      = "Is the compliance check thorough and accurate?",
    minScore    = 0.80,
    retryOnFail = true,
    maxRetries  = 2
)
public ComplianceReport check(String transaction) { ... }
```

---

##### `@Improve`
Enables few-shot learning from human feedback. When a `FeedbackExample`
is saved with label `GOOD` or `BAD`, the annotation retrieves the top-K
relevant examples and injects them into subsequent LLM prompts.

```java
@Improve(
    label                = "reply-quality",
    topK                 = 5,
    minExamples          = 3,
    includeNegativeExamples = true
)
public String generateReply(String mentionContext) { return mentionContext; }
```

**⚠️ Important:** `@Improve` is a **method-level** annotation only.
It cannot be placed on a class or inner static class declaration.

---

#### Observability and Security

##### `@Traced`
Records `AgentSpan` entries for every LLM call made by the annotated agent.
Spans include latency, token counts, and error details.

```java
@Traced(spanName = "fraud-detection", trackTokens = true)
public class FraudAnalystAgent { }
```

Spans are written to the configured `TraceExporter`:
- `InMemoryTraceExporter` — development, single JVM
- `RedisTraceExporter` — production, shared across JVMs

---

##### `@SecureAgent`
Enforces role-based access control (RBAC) using JWT identities. The
calling identity must carry one of the required roles, otherwise
`AccessDeniedException` is thrown and the audit log is updated.

```java
@SecureAgent(roles = {"compliance", "senior-risk"}, auditLog = true)
public ComplianceReport runCheck(String transaction) { ... }

// Before calling:
AgentIdentity id = new JwtValidator().validate(jwtToken);
SecurityContext.set(id);
```

---

### 4.2 Core Interfaces and Ports

#### `LlmPort` — The LLM abstraction

```java
public interface LlmPort {
    LlmResponse chat(String systemPrompt, String userMessage, LlmOptions opts);
    <T> T chatStructured(String systemPrompt, String userMessage,
                         Class<T> responseType, LlmOptions opts);
}
```

**Key rule:** `LlmPort` lives in `squad-core`. Its implementations live
**outside** squad-core (in the starter, or in your application). squad-core
never imports Spring AI, OpenAI SDK, or any LLM client library.

| Implementation | Location | Description |
|---------------|----------|-------------|
| `SpringAiLlmAdapter` | `squad-spring-boot-starter` | Bridges Spring AI ChatClient → LlmPort |
| `TokenTrackingLlmPort` | User application | Wraps any LlmPort, captures real token counts |
| Custom | Your code | Implement LlmPort to use any LLM |

---

#### `LlmResponse` — Record returned by every LLM call

```java
public record LlmResponse(
    String content,           // text output from the LLM
    int    promptTokens,      // tokens consumed by the prompt
    int    completionTokens,  // tokens in the response
    String model              // model that served the request
) {
    public LlmResponse(String content) {
        this(content, 0, 0, "unknown");
    }
    public int totalTokens() { return promptTokens + completionTokens; }
}
```

---

#### `TraceExporter` — Observability

```java
public interface TraceExporter {
    void export(AgentSpan span);
    default void exportBatch(List<AgentSpan> spans) { spans.forEach(this::export); }
    default void flush() {}
    String name();
}
```

| Implementation | Key | Description |
|---------------|-----|-------------|
| `InMemoryTraceExporter` | — | In-process list, for dev/testing |
| `RedisTraceExporter` | `squados:traces` (LIST) | Shared across JVMs via Redis |

**Redis key layout:**
```
squados:traces          LIST    JSON-serialised AgentSpan records (LIFO, max 500)
squados:traces:tokens   STRING  Cumulative token count (INCRBY)
```

`RedisTraceExporter` uses reflection (`Class.forName`) to load Jedis at
runtime. squad-core has **no compile-time Jedis dependency**. Users add:
```xml
<dependency>
  <groupId>redis.clients</groupId>
  <artifactId>jedis</artifactId>
  <version>5.1.0</version>
</dependency>
```
only when they want Redis tracing.

---

#### `ApprovalStore` — Human-in-the-loop

```java
public interface ApprovalStore {
    String submit(ApprovalRequest request);
    ApprovalStatus getStatus(String requestId);
    void approve(String requestId);
    void reject(String requestId, String reason);
}
```

Default implementation: `InProcessApprovalStore` (in-memory, single JVM).

---

#### `EventBus` — Agent communication

```java
public interface EventBus {
    void publish(String topic, Object payload);
    void subscribe(String topic, Consumer<SquadEvent> handler);
}
```

Default implementation: `InProcessEventBus` (in-memory, synchronous).

---

### 4.3 The Agent Lifecycle

```
Application startup
    │
    ▼
SquadRunner.run(AppClass.class, llmPort)
    │
    ├─► Scans classpath for @Agent classes in base package
    ├─► Creates AgentWrapper for each agent
    ├─► Wires LlmPort, LlmOptions (yml overrides > role defaults > global)
    ├─► Registers @OnEvent subscribers on the EventBus
    └─► Returns SquadContext
          │
          ▼
ctx.submitTo(AgentRole.ANALYST, "task description")
    │
    ▼
AgentWrapper.execute(TaskContext)
    │
    ├─► AgentCircuitBreaker.canProceed() — fail-fast if agent is unhealthy
    ├─► buildSystemPrompt(ctx) — @Agent.description + profile + taskId
    ├─► llm.chat(systemPrompt, userMessage, options)
    ├─► AgentResponse.of(raw, role, name, start) — wraps LlmResponse
    ├─► SquadTracer.getExporter().export(AgentSpan) — @Traced auto-recording
    └─► AgentCircuitBreaker.onSuccess() — update health state
```

---

### 4.4 Key Internal Components

#### `SquadRunner`
Entry point that bootstraps the framework. Scans for `@Agent` classes,
creates `AgentWrapper` instances, and returns a configured `SquadContext`.

```java
SquadContext ctx = SquadRunner.run(MyApp.class, llmPort);
```

#### `SquadContext`
The runtime gateway for submitting tasks to agents. Routes by `AgentRole`.

```java
// Text response
AgentResponse response = ctx.submitTo(AgentRole.ANALYST, "task text");

// Typed structured output
RiskAssessment result = ctx.submitTo(AgentRole.ANALYST, "task text", RiskAssessment.class);
```

#### `AgentWrapper`
Wraps each `@Agent` class. Handles:
- System prompt construction from `@Agent` metadata
- LLM call via `LlmPort`
- Circuit breaker state management
- Auto-recording of `AgentSpan` after every LLM call

#### `AgentCircuitBreaker`
Tracks consecutive failures per agent. Opens the circuit after threshold
failures; half-opens after a reset interval to allow recovery probes.

#### `VoteCollector`
Collects `Vote` objects from multiple agents and resolves them:
- `MAJORITY` — more than half approve
- `UNANIMOUS` — all must approve
- `WEIGHTED` — votes carry weight values

`TieBreaker` governs tie resolution: `ESCALATE`, `APPROVE`, or `REJECT`.

#### `JwtValidator`
Validates JWT tokens and produces `AgentIdentity` objects. Uses HS256 by
default. Token claims: `sub` (subject), `roles` (list), `tenantId`.

```java
AgentIdentity id = new JwtValidator().validate(token);
SecurityContext.set(id);
// ... call @SecureAgent method ...
SecurityContext.clear();
```

#### `SquadTracer`
Singleton registry for the active `TraceExporter`. Configure once at startup:

```java
SquadTracer.configure(new InMemoryTraceExporter());
// or
SquadTracer.configure(new RedisTraceExporter("localhost", 6379));
```

---

## 5. squad-spring-boot-starter

**Maven coordinates:**
```xml
<dependency>
  <groupId>io.github.sgpatel</groupId>
  <artifactId>squad-spring-boot-starter</artifactId>
  <version>3.4.0</version>
</dependency>
```

The starter provides **zero-boilerplate Spring Boot integration**. Add the
dependency and `@SquadApplication` — all infrastructure beans are
auto-configured.

### 5.1 Auto-Configuration

`SquadAutoConfiguration` wires the following beans, each guarded by
`@ConditionalOnMissingBean` so they can be overridden by the application.

| Bean | Type | Default | Override to… |
|------|------|---------|--------------|
| `llmPort` | `LlmPort` | `SpringAiLlmAdapter` (Ollama) | Use OpenAI, Anthropic, custom |
| `squadContext` | `SquadContext` | `SquadRunner.run(...)` | Custom agent wiring |
| `traceExporter` | `TraceExporter` | `InMemoryTraceExporter` | `RedisTraceExporter` for prod |
| `approvalStore` | `ApprovalStore` | `InProcessApprovalStore` | Database-backed store |
| `eventBus` | `EventBus` | `InProcessEventBus` | Kafka, RabbitMQ |
| `feedbackStore` | `FeedbackStore` | `InProcessFeedbackStore` | Persistent store |
| `auditLog` | `AuditLog` | `InMemoryAuditLog` | Database-backed log |
| `securityGuard` | `SecurityGuard` | `DefaultSecurityGuard` | Custom RBAC rules |
| `jwtValidator` | `JwtValidator` | `DefaultJwtValidator` | Custom JWT parsing |

#### `SpringAiLlmAdapter`
Bridges Spring AI's `ChatClient` to `LlmPort`. Extracts real token counts
from `ChatResponse.getMetadata().getUsage()`.

```java
public LlmResponse chat(String sys, String user, LlmOptions opts) {
    ChatResponse cr = chatClient.prompt().system(sys).user(user).call().chatResponse();
    int prompt     = usage.getPromptTokens().intValue();
    int completion = usage.getCompletionTokens().intValue();
    return new LlmResponse(cr.getResult().getOutput().getText(), prompt, completion, "ollama");
}
```

#### Overriding a bean

```java
@Configuration
public class MyConfig {

    // Replace default Ollama adapter with a custom LLM
    @Bean
    public LlmPort llmPort() {
        return new MyCustomLlmAdapter();
    }

    // Replace in-memory trace store with Redis
    @Bean
    public TraceExporter traceExporter() {
        RedisTraceExporter exp = new RedisTraceExporter("redis-host", 6379);
        SquadTracer.configure(exp);
        return exp;
    }
}
```

#### Setting `spring.main.web-application-type`

If Spring AI's Ollama starter pulls in WebFlux and your app is not a web
app, the starter sets `spring.main.web-application-type=none` via a system
property before Spring context initialization. Override in your own
`application.properties` if needed.

---

### 5.2 `application.properties` Reference

```properties
# ── Squad identity ─────────────────────────────────────────────────
squad.name=my-squad                     # Name shown in traces and logs

# ── LLM provider ───────────────────────────────────────────────────
squad.llm.provider=ollama               # ollama | openai | anthropic
squad.llm.model=llama3.2                # model name
squad.llm.temperature=0.5              # 0.0 – 1.0
squad.llm.max-tokens=2048

# ── Tracing ────────────────────────────────────────────────────────
squad.tracing.enabled=true
squad.tracing.exporter=memory           # memory | log | jaeger
squad.tracing.jaeger-url=http://localhost:14268/api/traces

# ── Security ───────────────────────────────────────────────────────
squad.security.enabled=false
squad.security.jwt-issuer=squados
squad.security.audit-log=true

# ── Approvals ──────────────────────────────────────────────────────
squad.approval.enabled=true

# ── Ollama (via Spring AI) ─────────────────────────────────────────
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.chat.options.temperature=0.3
spring.ai.ollama.chat.options.stream=false
```

---

## 6. Data Flow

### Simple task submission

```
Your code                squad-core                        LLM
─────────                ──────────                        ───
ctx.submitTo(            AgentWrapper.execute()
  ANALYST,         ──►     buildSystemPrompt()
  "task text"              llm.chat(sys, user, opts)  ──►  Ollama/OpenAI
)                          LlmResponse(content,            text + tokens
                             promptTok, completeTok)  ◄──
                           AgentResponse.of(raw,...)
                           SquadTracer.export(span)
               ◄──        return AgentResponse
```

### Typed structured output

```
ctx.submitTo(ANALYST, "task", RiskAssessment.class)
    │
    ▼
AgentWrapper detects responseType != null
    │
    ▼
LlmPort.chatStructured(sys, user, RiskAssessment.class, opts)
    │
    ▼ Spring AI maps JSON → RiskAssessment via BeanOutputConverter
    │
    ▼
RiskAssessment { riskLevel="HIGH", riskScore="0.87", ... }
```

### Multi-agent vote flow

```
Agent A: collector.submit(Vote.approve(...), "AgentA")
Agent B: collector.submit(Vote.reject(...), "AgentB")
Agent C: collector.submit(Vote.approve(...), "AgentC")
                │
                ▼
        VoteCollector.resolve()
                │
        VoteRule.MAJORITY: 2 approve, 1 reject → APPROVED
                │
                ▼
        VoteResult { outcome=APPROVED, approve=2, reject=1 }
```

### Event-driven pipeline

```
eventBus.publish("payments.incoming", payload)
    │
    ▼ InProcessEventBus dispatches to all subscribers
    │
    ▼ @OnEvent(topic="payments.incoming")
      GatewayAgent.onPaymentReceived(event)
    │
    ▼ ctx.submitTo(AgentRole.ANALYST, ...)
    │
    ▼ SentimentAgent analyses and returns result
```

---

## 7. Extension Points

### Custom LLM Provider

Implement `LlmPort` and expose it as a `@Bean`:

```java
public class AnthropicLlmAdapter implements LlmPort {
    @Override
    public LlmResponse chat(String sys, String user, LlmOptions opts) {
        // Call Anthropic API
        AnthropicResponse r = anthropicClient.complete(sys, user);
        return new LlmResponse(r.getText(), r.inputTokens(), r.outputTokens(), r.model());
    }

    @Override
    public <T> T chatStructured(String sys, String user, Class<T> type, LlmOptions opts) {
        // Parse structured JSON response
    }
}
```

### Custom Trace Exporter

```java
public class JaegerTraceExporter implements TraceExporter {
    @Override
    public void export(AgentSpan span) {
        jaegerClient.send(toJaegerSpan(span));
    }
    @Override public String name() { return "jaeger"; }
}
```

### Custom Approval Store

```java
@Service
public class DatabaseApprovalStore implements ApprovalStore {
    @Override
    public String submit(ApprovalRequest request) {
        ApprovalEntity e = approvalRepo.save(toEntity(request));
        slackClient.notify(request); // notify reviewers
        return e.getId();
    }
    // ...
}
```

### Token Tracking Wrapper

Wrap any `LlmPort` to capture real token counts, especially when the
underlying adapter returns `0` for tokens (common with local Ollama models):

```java
public class TokenTrackingLlmPort implements LlmPort {
    private final LlmPort delegate;
    private final AtomicInteger totalPrompt     = new AtomicInteger(0);
    private final AtomicInteger totalCompletion = new AtomicInteger(0);

    @Override
    public LlmResponse chat(String sys, String user, LlmOptions opts) {
        LlmResponse r = delegate.chat(sys, user, opts);
        int p = r.promptTokens() > 0 ? r.promptTokens() : (sys+user).length() / 4;
        int c = r.completionTokens() > 0 ? r.completionTokens() : r.content().length() / 4;
        totalPrompt.addAndGet(p);
        totalCompletion.addAndGet(c);
        return new LlmResponse(r.content(), p, c, r.model());
    }

    public int getTotalTokens() { return totalPrompt.get() + totalCompletion.get(); }
}
```

---

## 8. Dependency Policy

### squad-core — ZERO runtime dependencies

`squad-core` must never introduce a runtime dependency. This is enforced
by the build. The rationale: any Java application should be able to use
`squad-core` regardless of its framework (Spring, Quarkus, plain Java, etc.).

| What is allowed | What is forbidden |
|-----------------|-------------------|
| `java.*` packages | Spring Framework |
| Pure Java interfaces | Spring AI / LangChain4j |
| Reflection (`Class.forName`) | Jackson / Gson |
| — | Jedis / Lettuce |
| — | Any HTTP client |

The **only** optional compile-time pattern used is reflection for
`RedisTraceExporter` — it loads `redis.clients.jedis.JedisPool` via
`Class.forName` at runtime, failing with a clear error if Jedis is not
on the classpath.

### squad-spring-boot-starter — Thin Spring layer

The starter depends on:
- `squad-core` — the framework
- `spring-boot-autoconfigure` — for `@ConditionalOnMissingBean`
- `spring-ai-starter-model-ollama` — default LLM provider

All starter beans are `@ConditionalOnMissingBean` — the application can
override every single one.

---

## 9. Examples

### Minimal — Plain Java (no Spring)

```java
public class Main {
    public static void main(String[] args) {
        LlmPort llm = new MyLlmAdapter();
        SquadContext ctx = SquadRunner.run(Main.class, llm);

        AgentResponse response = ctx.submitTo(
            AgentRole.ANALYST,
            "What is the capital of France?"
        );
        System.out.println(response.content()); // Paris
    }
}

@Agent(role = AgentRole.ANALYST, name = "Geo", description = "You are a geography expert.")
class GeoAgent { }
```

### Spring Boot — zero config

```java
@SpringBootApplication
@SquadApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean
    public ApplicationRunner runner(SquadContext ctx) {
        return args -> {
            AgentResponse r = ctx.submitTo(AgentRole.ANALYST, "What is quantum computing?");
            System.out.println(r.content());
        };
    }
}

@Agent(role = AgentRole.ANALYST, name = "ResearchBot",
    description = "You are an expert research assistant. Answer clearly and concisely.")
class ResearchAgent { }
```

```properties
# application.properties
squad.name=my-squad
spring.ai.ollama.chat.options.model=llama3.2
```

### Fraud Detection — all 15 annotations

See `squad-examples/fraud-detection/` for a complete example using all 15
annotations with:
- 5 agents: Gateway, RiskAnalyst, Behaviour, Compliance, Underwriter
- 2 scenarios: low-risk (auto-approved) and high-risk (escalated)
- `@SquadVote`, `@AutoApproval`, `@AwaitApproval`, `@Improve`, `@Traced`

### Multi-JVM — Redis shared trace store

```java
// Service A (fraud-detection)
RedisTraceExporter exp = new RedisTraceExporter("localhost", 6379);
SquadTracer.configure(exp);
// ... runs agents, spans written to Redis ...

// Service B (dashboard) — sees same spans
RedisTraceExporter exp2 = new RedisTraceExporter("localhost", 6379);
List<AgentSpan> spans = exp2.getSpans();  // includes spans from Service A
```

---

## 10. Version History

| Version | Date | Key additions |
|---------|------|---------------|
| **3.4.0** | Mar 2026 | `RedisTraceExporter` (multi-JVM traces), real token tracking via `TokenTrackingLlmPort`, `AgentWrapper` auto-records spans |
| **3.3.0** | Mar 2026 | `squad-spring-boot-starter` (zero-config auto-configuration), Fraud Detection Squad (all 15 annotations), Snack Thief Squad |
| **3.2.0** | Mar 2026 | All 15 annotations, 340 unit tests, 20 lifecycle phases |
| **3.1.0** | — | `@Delegate` dynamic routing |
| **3.0.0** | — | `@SecureAgent` RBAC + JWT |
| **2.9.0** | — | `@Improve` few-shot learning |
| **2.8.0** | — | `@Traced` observability |
| **2.7.0** | — | `@AutoPlan` agentic loops |
| **2.6.0** | — | `@Eval` quality gate |
| **2.5.0** | — | `@OnEvent` event-driven agents |
| **2.4.0** | — | `@SquadVote` consensus voting |
| **2.3.0** | — | `@AwaitApproval` + `@AutoApproval` |
| **2.2.0** | — | `@SquadTool` real API calls |
| **2.1.0** | — | `@SquadPlan` typed output |
| **2.0.0** | — | Multi-node Redis Pub/Sub |
| **1.2.0** | — | pgvector memory, parallel agents (2.3× speedup) |
| **1.0.0** | — | Core framework, 102 tests |

---

## Quick Reference Card

```
DECLARE          @Agent  @SquadApplication  @MissionProfile

LLM OUTPUT       @SquadPlan  @Required  @SquadTool  @ToolParam  @AutoPlan

LIFECYCLE        @PostConstruct  @OnMessage

ORCHESTRATION    @SquadVote  @AwaitApproval  @AutoApproval  @Delegate  @OnEvent

QUALITY          @Eval  @Improve

OBSERVABILITY    @Traced

SECURITY         @SecureAgent
```

```
LlmPort ─────────────── Swap LLM providers
TraceExporter ───────── In-memory (dev) / Redis (prod)
ApprovalStore ────────── In-memory / Database-backed
EventBus ─────────────── In-memory / Kafka / RabbitMQ
FeedbackStore ────────── In-memory / Persistent
```

---

*Document version: 3.4.0 · Last updated: March 2026*
