# SquadOS

> **Multi-Agent AI Framework for Java.**
> Spring Boot patterns for AI agents — write one class, get a working squad.

[![Tests](https://img.shields.io/badge/tests-454%20passing-brightgreen)]()
[![Features](https://img.shields.io/badge/annotations-37%20active-blue)]()
[![Java](https://img.shields.io/badge/java-21-blue)]()
[![Maven Central](https://img.shields.io/badge/Maven%20Central-3.7.0-orange)](https://central.sonatype.com/artifact/io.github.sgpatel/squad-core)
[![License](https://img.shields.io/badge/license-MIT-green)]()

## What is SquadOS?

SquadOS is a production-ready multi-agent AI framework for Java. Define agents with annotations,
wire them together, and run complex AI workflows — all without leaving Spring Boot.

Think of it as **Spring Boot for AI agents**: the same convention-over-configuration philosophy,
the same annotation-driven development, but for orchestrating LLM-powered agents.

## Quick Start

**Option A — Spring Boot Starter (zero boilerplate):**

```xml
<dependency>
  <groupId>io.github.sgpatel</groupId>
  <artifactId>squad-spring-boot-starter</artifactId>
  <version>3.7.0</version>
</dependency>
```

```java
@SpringBootApplication
@SquadApplication
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
    // That's it. SquadContext, LlmPort, TraceExporter, ApprovalStore
    // — all auto-configured from application.properties
}
```

```properties
# application.properties
squad.name=my-squad
squad.llm.provider=ollama
squad.llm.model=llama3.2
squad.tracing.enabled=true
squad.security.enabled=false
```

**Option B — Core only (wire yourself):**

```xml
<dependency>
  <groupId>io.github.sgpatel</groupId>
  <artifactId>squad-core</artifactId>
  <version>3.7.0</version>
</dependency>
```

## Your First Agent

```java
@Agent(
    role = AgentRole.ANALYST,
    name = "ResearchAgent",
    description = "You are a research analyst. Answer questions with evidence."
)
public class ResearchAgent {

    @PostConstruct
    public void init() {
        System.out.println("ResearchAgent online.");
    }

    @SquadTool(description = "Search the web for information")
    public String search(@ToolParam(description = "Search query") String query) {
        return "Results for: " + query;
    }
}
```

```java
// In your ApplicationRunner:
AgentResponse response = ctx.submitTo(AgentRole.ANALYST, "What is quantum computing?");
System.out.println(response.content());
```

## The 30 Annotations

### Core Agent Lifecycle
| Annotation | Purpose |
|-----------|---------|
| `@Agent` | Declare an agent with role, name, description |
| `@SquadApplication` | Mark the Spring Boot entry point |
| `@PostConstruct` | Lifecycle hook — runs when agent boots |
| `@OnMessage` | React to messages from other agents |
| `@MissionProfile` | Activate agent for a named profile |

### Structured Output
| Annotation | Purpose |
|-----------|---------|
| `@SquadPlan` | Typed structured output from LLM |
| `@Required` | Mark a @SquadPlan field as mandatory |
| `@SquadTool` | Expose a Java method as an LLM tool |
| `@ToolParam` | Describe a tool parameter |

### Human-in-the-Loop
| Annotation | Purpose |
|-----------|---------|
| `@AwaitApproval` | Pause execution for human review |
| `@AutoApproval` | Auto-approve based on a condition expression |
| `@SquadVote` | Multi-agent consensus voting |

### Orchestration
| Annotation | Purpose |
|-----------|---------|
| `@OnEvent` | Event-driven agent activation |
| `@Eval` | Quality gate — auto-retry if score below threshold |
| `@AutoPlan` | Agentic plan-execute-reflect-replan loop |
| `@Pipeline` | Sequential multi-agent workflow definition |
| `@Step` | Single step within a @Pipeline |
| `@Condition` | Skip-condition expression on an agent |
| `@Delegate` | Dynamic routing to specialist agents |

### Reliability & Safety
| Annotation | Purpose |
|-----------|---------|
| `@Retry` | Exponential backoff retry (maxAttempts, backoffMs, multiplier) |
| `@Guardrails` | Pluggable safety/compliance filter pipeline |
| `@RateLimit` | Sliding-window calls/min + tokens/hour enforcement |
| `@DurableAgent` | Checkpoint-based workflow persistence across JVM restarts |
| `@Timeout` | Hard LLM deadline — fail or return fallback on expiry |
| `@Cache` | In-process response cache — EXACT (hash) or SEMANTIC (cosine) |
| `@Checkpoint` | Mid-workflow step persistence for `@DurableAgent` classes |

### Performance & Testing
| Annotation | Purpose |
|-----------|---------|
| `@AgentPool` | Multiple instances with ROUND_ROBIN / LEAST_BUSY / RANDOM routing |
| `@AgentTest` | Golden-set testing with JSON test cases and pass-rate threshold |
| `@PromptTemplate` | Externalise system prompts to classpath files with `{{variable}}` substitution |

### Streaming & Integration
| Annotation | Purpose |
|-----------|---------|
| `@Streaming` | Token-by-token LLM output via TokenWriter SPI |
| `@McpServer` | Connect agent to MCP (Model Context Protocol) tool servers |
| `@RemoteSquad` | Inject SquadClient for cross-JVM agent invocation (field-level) |
| `@AgentAPI` | Expose agent squad as REST HTTP endpoints |

### Observability & Learning
| Annotation | Purpose |
|-----------|---------|
| `@Observe` | Emit Micrometer/Prometheus metrics (calls, latency, tokens, errors) |
| `@Traced` | Record spans with token counts to any TraceExporter |
| `@Improve` | Few-shot learning from human feedback |
| `@SecureAgent` | RBAC + JWT access control |

## Typed Outputs with @SquadPlan

```java
@SquadPlan(description = "Payment fraud assessment")
public class RiskAssessment {
    @Required public String riskLevel;    // LOW / MEDIUM / HIGH / CRITICAL
    @Required public String riskScore;    // "0.0" to "1.0"
    public List<String>    riskFactors;
    public String          recommendation; // APPROVE / REVIEW / BLOCK
}

// Submit and get back a typed object:
RiskAssessment result = ctx.submitTo(AgentRole.ANALYST, transactionContext, RiskAssessment.class);
System.out.println(result.riskLevel);   // HIGH
System.out.println(result.riskScore);   // 0.87
```

## Multi-Agent Voting

```java
VoteCollector collector = new VoteCollector(
    "fraud-verdict", VoteRule.MAJORITY, TieBreaker.ESCALATE, 3, 30);

collector.submit(Vote.approve("Risk score clean", 1.0).withVoter("RiskAnalyst"), "RiskAnalyst");
collector.submit(Vote.approve("Behaviour normal", 1.0).withVoter("BehaviourAgent"), "BehaviourAgent");
collector.submit(Vote.reject("AML flag triggered", 1.0).withVoter("ComplianceAgent"), "ComplianceAgent");

VoteResult result = collector.resolve();
System.out.println(result.getOutcome()); // APPROVED (2-1)
```

## Human-in-the-Loop Approvals

```java
@AutoApproval(condition = "riskScore < 0.3 AND velocity < 5")
@AwaitApproval(
    reason      = "High-risk transaction requires review",
    timeoutHours = 4,
    onTimeout   = TimeoutPolicy.REJECT,
    escalateTo  = "senior-fraud-analyst",
    priority    = ApprovalPriority.HIGH
)
public PaymentDecision underwrite(String transactionContext) { ... }
```

## Observability with @Traced

```java
@Traced(spanName = "fraud-detection", trackTokens = true)
public class FraudAgent { }

// Configure exporter — in-memory (development):
SquadTracer.configure(new InMemoryTraceExporter());

// Or Redis (production, shared across JVMs):
SquadTracer.configure(new RedisTraceExporter("localhost", 6379));
```

## Security with @SecureAgent

```java
@SecureAgent(roles = {"compliance"}, auditLog = true)
public ComplianceReport check(String transaction) { ... }

// Set JWT identity before calling:
String token = JwtValidator.createTestToken("alice", "squados", expiry, "compliance");
AgentIdentity id = new JwtValidator().validate(token);
SecurityContext.set(id);
```

## Redis Shared Trace Store

For multi-JVM deployments — all squads write spans to the same Redis instance:

```java
// In every service that should share traces:
RedisTraceExporter exp = new RedisTraceExporter("localhost", 6379);
SquadTracer.configure(exp);
```

```xml
<!-- Add Jedis to pom.xml (only needed for Redis tracing) -->
<dependency>
  <groupId>redis.clients</groupId>
  <artifactId>jedis</artifactId>
  <version>5.1.0</version>
</dependency>
```

Redis keys:
- `squados:traces` — LIST of JSON AgentSpan records (newest first, max 500)
- `squados:traces:tokens` — STRING cumulative token count (INCRBY)

## Timeout, Cache & Observe

```java
@Agent(role = AgentRole.ANALYST, name = "FastAnalyst",
       description = "You are a fast research analyst.")
@Timeout(timeoutMs = 5000, action = "fallback", fallbackResponse = "Analysis timed out.")
@Cache(mode = "EXACT", ttlSeconds = 300)
@Observe(namespace = "myapp", tags = {"env=prod", "team=ai"})
public class FastAnalystAgent {}
```

On cache hit: zero tokens, instant response.
On timeout: returns fallback or throws `AgentTimeoutException`.
`@Observe` emits Prometheus metrics automatically when `spring-boot-actuator` is on the classpath.

## Prompt Templates

```
# src/main/resources/prompts/analyst-prompt.txt
You are {{agentName}}, a specialist {{role}} agent.
Mission: {{description}}
Active profile: {{profile}}
Task reference: {{taskId}}
```

```java
@Agent(role = AgentRole.ANALYST, name = "ResearchAnalyst",
       description = "Deep-dive technical research with citations.")
@PromptTemplate(path = "prompts/analyst-prompt.txt")
public class ResearchAnalystAgent {}
```

## Agent Pools

```java
@Agent(role = AgentRole.ANALYST, name = "AnalystPool",
       description = "High-throughput analyst.")
@AgentPool(size = 5, strategy = "LEAST_BUSY", maxQueueSize = 20)
public class HighThroughputAnalyst {}
```

```java
// Routes automatically across all 5 instances:
AgentResponse r = ctx.submitTo(AgentRole.ANALYST, "Analyse this dataset...");
```

## Golden-Set Testing with @AgentTest

```json
// src/test/resources/tests/analyst-tests.json
[
  { "input": "What is 2+2?",           "expectedContains": "4" },
  { "input": "Capital of France?",     "expectedContains": "Paris" },
  { "input": "Explain quantum physics", "expectedMinWords": 30 }
]
```

```java
@Agent(role = AgentRole.ANALYST, name = "Analyst")
@AgentTest(testCasesPath = "tests/analyst-tests.json", passRateMin = 0.9f)
public class AnalystAgent {}

// In your test suite:
ctx.runAgentTests();                          // all @AgentTest agents
ctx.runAgentTests(AgentRole.ANALYST);         // single role
```

## Checkpoints in Durable Workflows

```java
@Agent(role = AgentRole.EXECUTOR)
@DurableAgent
public class ClaimProcessorAgent {

    @Checkpoint(name = "validation")
    public String validateClaim(String input) {
        // Saved to DurableStore after first run.
        // Skipped and result returned from store on JVM restart.
        return "validated:" + input;
    }

    @Checkpoint(name = "enrichment")
    public String enrichClaim(String validated) {
        return "enriched:" + validated;
    }
}
```

## Metrics with @Observe (Spring Boot)

```properties
# application.properties — enable actuator endpoints
management.endpoints.web.exposure.include=prometheus,health
```

Prometheus metrics emitted automatically:
```
myapp_agent_calls_total{agent="FastAnalyst",role="ANALYST",status="success"} 42
myapp_agent_latency_seconds{agent="FastAnalyst",role="ANALYST"} 0.231
myapp_agent_tokens_total{agent="FastAnalyst",role="ANALYST",type="prompt"} 1050
```

## Modules

| Module | Description | Published |
|--------|-------------|-----------|
| `squad-core` | Framework core — zero runtime deps | ✅ Maven Central |
| `squad-spring-boot-starter` | Zero-config Spring Boot auto-configuration | ✅ Maven Central |
| `squad-dashboard` | React + Recharts live monitoring dashboard | Local only |
| `squad-examples/fraud-detection` | All 15 annotations — payment fraud detection | Local only |
| `squad-examples/snack-thief` | All 15 annotations — Karen stole the pizza 🍕 | Local only |
| `squad-examples/daily-planner` | @SquadPlan typed output demo | Local only |
| `squad-examples/multi-node` | Redis Pub/Sub multi-JVM demo | Local only |

## Examples

### Fraud Detection Squad (all 15 annotations)
```bash
ollama pull llama3.2
cd squad-examples/fraud-detection
mvn spring-boot:run
```

5 specialist agents: GatewayAgent · RiskAnalyst · BehaviourAgent · ComplianceAgent · UnderwriterAgent

**Scenario 1** (regular customer, known device):
```
[RiskAnalyst]  Risk Level: LOW  | Risk Score: 0.18
[SquadVote]    APPROVED (3-0)
[AutoApproval] riskScore 0.18 < 0.3 — AUTO-APPROVED
```

**Scenario 2** (Tor IP, flagged customer, unverified merchant):
```
[RiskAnalyst]  Risk Level: CRITICAL | Risk Score: 1.0
[SquadVote]    REJECTED (0-3)
[AwaitApproval] ESCALATING to senior-fraud-analyst
```

### Snack Thief Squad 🍕
```bash
cd squad-examples/snack-thief
mvn spring-boot:run
# Verdict: Karen is guilty (4-1)
```

### Live Dashboard
```bash
# Start Redis first:
docker run -d -p 6379:6379 redis:7-alpine

# Run fraud-detection (writes spans to Redis):
cd squad-examples/fraud-detection && mvn spring-boot:run

# Run dashboard (reads from same Redis):
cd squad-dashboard && mvn spring-boot:run

# Open http://localhost:8080
```

Dashboard features: 7 tabs · 8 live charts · Agent health · Vote history · Approval queue · Audit log · Feedback store

## LLM Streaming

```java
@Agent(role = AgentRole.ANALYST, name = "StreamAgent", description = "You stream your response.")
@Streaming(writer = StdoutTokenWriter.class)
public class StreamAgent { }

// In your runner:
ctx.submitStream("Explain quantum computing", token -> {
    System.out.print(token.text());  // prints each chunk as it arrives
    if (token.isLast()) System.out.println();
});
```

## Guardrail Filters

```java
@Agent(role = AgentRole.ANALYST, name = "SafeAgent", description = "...")
@Guardrails(
    filters = { PiiDetector.class, PromptInjectionDetector.class, ToxicityFilter.class },
    inputCheck  = true,
    outputCheck = true
)
public class SafeAgent { }
```

Available built-in filters: `PiiDetector`, `PromptInjectionDetector`, `ToxicityFilter`,
`HallucinationDetector`, `GroundingFilter`, `SensitiveTopicFilter`, `RegulatoryComplianceFilter`, `ConfidentialDataFilter`

## Pipeline Orchestration

```java
@Agent(role = AgentRole.STRATEGIST, name = "Orchestrator", description = "...")
@Pipeline(name = "research-pipeline", steps = {
    @Step(role = AgentRole.ANALYST,   name = "research"),
    @Step(role = AgentRole.REVIEWER,  name = "review",  inputFrom = "research"),
    @Step(role = AgentRole.WRITER,    name = "summarise", inputFrom = "review",
          condition = "success")
})
public class OrchestratorAgent { }

PipelineResult result = ctx.submitPipeline(AgentRole.STRATEGIST, "AI in healthcare");
System.out.println(result.finalOutput());   // last successful step output
System.out.println(result.totalTokens());   // sum across all steps
```

## Durable Workflows

```java
@Agent(role = AgentRole.ANALYST, name = "DurableAgent", description = "...")
@DurableAgent(store = "redis", ttlHours = 48)
public class DurableWorkflowAgent { }

// Idempotent — same workflowId returns cached result if already COMPLETED:
AgentResponse r = ctx.submitDurable(AgentRole.ANALYST, "wf-1234", "Process this");
ctx.pauseWorkflow("wf-1234");
ctx.resumeWorkflow("wf-1234");
Optional<WorkflowState> state = ctx.getWorkflowState("wf-1234");
```

## Rate Limiting

```java
@Agent(role = AgentRole.ANALYST, name = "RateLimitedAgent", description = "...")
@RateLimit(callsPerMinute = 10, tokensPerHour = 50_000)
public class RateLimitedAgent { }
// Throws RateLimitExceededException (not retried) when window is exceeded
```

## Retry with Backoff

```java
@Agent(role = AgentRole.ANALYST, name = "RetryAgent", description = "...")
@Retry(maxAttempts = 3, backoffMs = 500, multiplier = 2.0f, maxBackoffMs = 10_000)
public class RetryAgent { }
// Throws RetryExhaustedException after all attempts fail
// Not retried: RateLimitExceededException, GuardrailException, AgentSecurityException
```

## Remote Squad Invocation

```java
@Agent(role = AgentRole.STRATEGIST, name = "Orchestrator", description = "...")
public class OrchestratorAgent {

    @RemoteSquad(url = "http://analyst-squad:8080/api/analyst", auth = "api-key")
    private SquadClient analystSquad;

    // analystSquad.submit("task") → calls the remote squad over HTTP
}
```

## Agent HTTP API

```java
@Agent(role = AgentRole.ANALYST, name = "PublicAgent", description = "...")
@AgentAPI(path = "/api/analysis", auth = "api-key", version = "1.0")
public class PublicAgent { }
```

Automatically registers (when `squad.agent-api.enabled=true`):
- `POST /api/analysis/submit` — submit task to lead agent
- `POST /api/analysis/submit/{role}` — submit to specific role
- `POST /api/analysis/submit/stream` — SSE streaming response
- `GET  /api/analysis/info` — squad metadata
- `GET  /api/analysis/health` — health check

## MCP Tool Integration

```java
@Agent(role = AgentRole.ANALYST, name = "McpAgent", description = "...")
@McpServer(urls = {"http://tools:8090", "http://search:8091"}, timeoutMs = 3000)
public class McpAgent { }
// Tool list auto-discovered and injected into system prompt
```

## Spring Boot Starter — application.properties Reference

```properties
# Squad identity
squad.name=my-squad

# LLM (Ollama by default)
squad.llm.provider=ollama
squad.llm.model=llama3.2
squad.llm.temperature=0.5
squad.llm.max-tokens=2048

# Spring AI Ollama configuration (optional fallback defaults)
# Each agent can override temperature/maxTokens via @Agent role defaults or squad.yml
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2

# Tracing
squad.tracing.enabled=true
squad.tracing.exporter=memory        # log | memory

# Security
squad.security.enabled=false
squad.security.jwt-issuer=squados
squad.security.audit-log=true

# Approvals
squad.approval.enabled=true

# Guardrails
squad.guardrails.enabled=false

# Durable workflows
squad.durable.enabled=false
squad.durable.store=memory           # memory | redis
squad.durable.ttl-hours=24

# Conversation history
squad.conversation.enabled=false
squad.conversation.max-turns=20

# MCP tool servers
squad.mcp.enabled=false
squad.mcp.timeout-ms=5000

# Agent HTTP API
squad.agent-api.enabled=false

# Global API key (used by @AgentAPI auth + @RemoteSquad injection)
squad.api.key=

# Redis (shared by DurableStore, ConversationStore, TraceExporter when store=redis)
redis.host=localhost
redis.port=6379
redis.password=
```

## Version History

| Version | Key Features |
|---------|-------------|
| 1.2.0 | Core framework, parallel agents, pgvector memory |
| 2.0.0 | Multi-node Redis Pub/Sub |
| 2.1.0 | @SquadPlan typed output |
| 3.2.0 | All 15 annotations, 340 tests |
| 3.3.0 | squad-spring-boot-starter, Fraud Detection + Snack Thief examples |
| 3.4.0 | RedisTraceExporter, real token tracking, React dashboard |
| 3.5.0 | Conversation history, rate limiting |
| 3.6.0 | Remote squad invocation, MCP tool integration |
| **3.7.0** | **Streaming, Guardrails, Durable Workflows, Pipeline Orchestration, Agent HTTP API** |

## Requirements

- Java 21+
- Maven 3.8+
- Ollama (or any Spring AI-compatible LLM provider)
- Redis (optional — only for RedisTraceExporter multi-JVM tracing)

## License

MIT — use freely in commercial projects.

---

Built with ☕ and too many LLM calls by [@sgpatel](https://github.com/sgpatel)
