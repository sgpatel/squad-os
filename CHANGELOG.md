## v3.7.0 (2026-04-11) — Streaming, Guardrails, Durable Workflows, Pipeline Orchestration
### Added
- **LLM Streaming** — token-by-token output via `@Streaming` annotation
  - `StreamToken` record + `TokenWriter` SPI (`StdoutTokenWriter`, `LoggerTokenWriter`, `NoOpTokenWriter`)
  - `LlmPort.chatStream()` default implementation; `MockLlmPort` splits responses into chunks
  - `SquadContext.submitStream()` — SSE-ready streaming submissions
- **Guardrail Engine** — pluggable safety + compliance filter pipeline via `@Guardrails`
  - 8 built-in filters: `PiiDetector`, `PromptInjectionDetector`, `ToxicityFilter`, `HallucinationDetector`,
    `GroundingFilter`, `SensitiveTopicFilter`, `RegulatoryComplianceFilter`, `ConfidentialDataFilter`
  - 3 actions: `LOG_ONLY`, `REDACT`, `BLOCK_AND_LOG`
  - `GuardrailAuditLog` — thread-safe violation history; `GuardrailException` (not retried)
- **Durable Workflow Engine** — checkpoint-based workflows surviving JVM restarts via `@DurableAgent`
  - `WorkflowState` state machine: PENDING → RUNNING → PAUSED → COMPLETED/FAILED
  - `DurableStore` SPI: `InProcessDurableStore` + `RedisDurableStore` (raw RESP, zero deps)
  - `DurableEngine` — idempotent submit (COMPLETED workflows return cached result)
  - `SquadContext.submitDurable()`, `pauseWorkflow()`, `resumeWorkflow()`, `getWorkflowState()`
- **Retry Engine** — exponential backoff via `@Retry`
  - Configurable: maxAttempts, backoffMs, multiplier, maxBackoffMs
  - Non-retryable: `RateLimitExceededException`, `GuardrailException`, `AgentSecurityException`
  - `RetryExhaustedException` carries agentName, attempts, totalElapsedMs
- **Pipeline Orchestration** — sequential multi-agent workflows via `@Pipeline` + `@Step`
  - `ConditionEvaluator` — `contains`, `startsWith`, `endsWith`, `matches`, `isEmpty`, `success`, `failure` + `||`/`&&`
  - `PipelineResult` — per-step `AgentResponse` map, `finalOutput()`, `skippedSteps()`, `totalTokens()`
  - `SquadContext.submitPipeline()` — discovers `@Pipeline` on agent class automatically
- **Rate Limiting** — sliding-window enforcement via `@RateLimit`
  - Per-agent calls/minute + tokens/hour tracking (ConcurrentHashMap, zero deps)
  - `RateLimitExceededException` — not retried by RetryEngine
- **Conversation History** — multi-turn sessions via `ConversationStore`
  - `InProcessConversationStore` — ConcurrentHashMap per session, configurable `maxTurns`
  - `LlmPort.chatWithHistory()` — full conversation-aware LLM calls
- **MCP Tool Integration** — Model Context Protocol server support via `@McpServer`
  - `McpToolProvider` SPI + `McpToolDefinition` record
  - `HttpMcpClient` — HTTP discovery + invocation (no external deps)
  - MCP tool list auto-injected into agent system prompt
- **Remote Squad Invocation** — cross-JVM agent calls via `@RemoteSquad`
  - `SquadClient` — HTTP client: `submit()`, `submitTo()`, `stream()`, `info()`, `health()`
  - `AgentAuthProvider` SPI: `ApiKeyAuth`, `JwtAuth`, `NoAuth`
  - `RemoteSquadInvoker.inject()` — standalone field injection (squad-core)
  - `RemoteSquadInjector` — Spring `BeanPostProcessor` for automatic injection (starter)
- **Agent HTTP API** — expose agents as REST endpoints via `@AgentAPI`
  - `AgentApiController` — POST submit, POST submit/{role}, POST submit/stream (SSE), GET info, GET health
  - `AgentApiRegistrar` — scans registry, registers routes via `RequestMappingHandlerMapping`
  - Virtual thread per SSE stream (Java 21)
- **New Annotations**: `@Streaming`, `@Guardrails`, `@DurableAgent`, `@Retry`, `@Pipeline`, `@Step`,
  `@Condition`, `@RateLimit`, `@McpServer`, `@RemoteSquad`, `@AgentAPI`
- **Gap Annotations** (defined, engines deferred): `@Timeout`, `@Cache`, `@Observe`, `@PromptTemplate`, `@AgentPool`, `@AgentTest`, `@Checkpoint`
- **New Exceptions**: `RateLimitExceededException`, `GuardrailException`, `RetryExhaustedException`,
  `DurableWorkflowException`, `AgentTimeoutException`
- **squad-spring-boot-starter** new beans:
  - `GuardrailEngine` (squad.guardrails.enabled=true)
  - `DurableStore` (squad.durable.enabled=true, store=memory|redis)
  - `ConversationStore` (squad.conversation.enabled=true)
  - `McpToolProvider` / `HttpMcpClient` (squad.mcp.enabled=true)
  - `RateLimitEnforcer` (always active)
  - `RemoteSquadInjector` BeanPostProcessor (always active)
  - `AgentApiRegistrar` (squad.agent-api.enabled=true)
  - `SpringAiEmbeddingAdapter` (squad.memory.enabled=true + EmbeddingModel present)
- **Phase 21-26 tests** — 114 new tests (DurableStore, GuardrailEngine, RetryEngine, Streaming, Pipeline, ConditionEvaluator)
### Modules published to Maven Central
- `io.github.sgpatel:squad-core:3.7.0`
- `io.github.sgpatel:squad-spring-boot-starter:3.7.0`

## v3.6.0 (2026-04-05) — Remote Squads + MCP Tools
### Added
- `remote` package — SquadClient, AgentAuthProvider SPI, ApiKeyAuth / JwtAuth / NoAuth
- `mcp` package — McpToolProvider, McpToolDefinition, prompt builder
- `@RemoteSquad` + `@McpServer` annotations
- `RemoteSquadInvoker` standalone injection helper (squad-core)

## v3.5.0 (2026-03-29) — Conversation + Rate Limiting
### Added
- `conversation` package — ConversationStore + InProcessConversationStore
- `ratelimit` package — RateLimitEnforcer (sliding-window, zero deps)
- `@RateLimit` annotation; `RateLimitExceededException`
- `LlmPort.chatWithHistory()` default method

## v3.4.0 (2026-03-23) — Redis Tracing + Real Token Counts
### Added
- `RedisTraceExporter` — shared trace store for multi-JVM SquadOS deployments
  - Stores AgentSpan records in Redis LIST `squados:traces` (LIFO, max 500)
  - Cumulative token count in `squados:traces:tokens` (INCRBY)
  - Zero external JSON deps — built-in serialiser
  - Uses reflection to avoid compile-time Jedis dep in squad-core (stays zero-dep)
  - Users add `redis.clients:jedis` only if they use Redis tracing
- `TokenTrackingLlmPort` — intercepts every `llm.chat()` call
  - Captures real `promptTokens` + `completionTokens` from Spring AI
  - Falls back to content-length estimation (~4 chars/token) if provider returns 0
- `squad-dashboard` — live React + Recharts monitoring UI
  - 7 tabs: Overview, Agents, Traces, Votes, Approvals, Security, Improve
  - 8 live charts: PieChart, BarChart, AreaChart via Recharts 2.8
  - Agent health cards, vote progress bars, approval queue with one-click actions
  - Audit log + feedback store with search/filter
  - Auto-refresh every 5s, sticky header + footer
  - Connects to shared Redis — shows spans from any SquadOS JVM
- `AgentWrapper` now auto-records `AgentSpan` after every `llm.chat()` call
  - Spans flow to configured `TraceExporter` automatically
  - No manual span code needed in agents
### Modules published to Maven Central
- `io.github.sgpatel:squad-core:3.4.0`
- `io.github.sgpatel:squad-spring-boot-starter:3.4.0`

## v3.3.0 (2026-03-22) — Spring Boot Starter + Fraud Detection Squad
### Added
- `squad-spring-boot-starter` — zero-config Spring Boot auto-configuration
  - Auto-wires: LlmPort, SquadContext, TraceExporter, ApprovalStore, EventBus,
    FeedbackStore, AuditLog, SecurityGuard, JwtValidator
  - All beans @ConditionalOnMissingBean — fully overridable
  - IDE autocomplete via `spring-configuration-metadata.json`
  - `application.properties` driven: squad.name, squad.llm.model, squad.tracing.exporter...
- `squad-examples/fraud-detection` — all 15 annotations in one production use case
  - 5 specialist agents: GatewayAgent, RiskAnalyst, BehaviourAgent, ComplianceAgent, UnderwriterAgent
  - 2 scenarios: low-risk (auto-approved) + high-risk (escalated)
  - 4 typed @SquadPlan outputs: PaymentEvent, RiskAssessment, FraudReport, PaymentDecision
- `squad-examples/snack-thief` — all 15 annotations, Karen is guilty (4-1 vote)

## v3.2.0 (2026-03-21) — All 15 Annotations
### Added
- @Traced, @SecureAgent, @Delegate, @Improve, @AutoPlan, @Eval, @OnEvent
- @SquadVote, @AwaitApproval, @AutoApproval, @SquadTool, @SquadPlan, @OnMessage
- Total: 340 tests, 20 phases, all 15 annotations

## v3.1.0 — @Delegate dynamic routing
## v3.0.0 — @SecureAgent RBAC + JWT
## v2.9.0 — @Improve few-shot learning
## v2.8.0 — @Traced observability
## v2.7.0 — @AutoPlan agentic loops
## v2.6.0 — @Eval quality gate
## v2.5.0 — @OnEvent event-driven agents
## v2.4.0 — @SquadVote consensus voting
## v2.3.0 — @AwaitApproval + @AutoApproval
## v2.2.0 — @SquadTool real API calls
## v2.1.0 — @SquadPlan typed output
## v2.0.0 — Multi-node Redis Pub/Sub
## v1.2.0 — pgvector + Redis memory
## v1.1.0 — Parallel agents (2.3x speedup)
## v1.0.0 — Core framework (102 tests)
