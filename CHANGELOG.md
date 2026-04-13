## v3.9.0 (2026-04-13) — Tier 1/2/3 Feature Complete

### Tier 3 — Ecosystem / Platform
- **`squad-mcp-server`** — Expose any `@Agent` class as an MCP tool for Claude Desktop, Cursor, and any MCP client
  - JSON-RPC 2.0 over HTTP via JDK built-in `com.sun.net.httpserver` (zero external deps)
  - `McpServer.start(config, agents, llm)` — boots in one line; supports port=0 for ephemeral
  - `McpToolExporter` — auto-discovers `@Agent(name, description)` → MCP `tools/list` entries
  - `McpJsonRpc` — pure-Java JSON-RPC parsing/building
  - Spring Boot: `squad.mcp.server.enabled=true` + `squad.mcp.server.port=3000`
  - Phase 38 tests: 10 passing
- **GraalVM Native Image** — compile SquadOS apps to native binaries
  - `reflect-config.json` — 156 entries: all 61 annotations, 21 records, key execution/filter/exception classes
  - `resource-config.json` — classpath resources (properties, services)
  - `native-image.properties` — build-time/run-time initialisation directives
  - Located at `META-INF/native-image/io.github.sgpatel/squad-core/`
  - Phase 39 tests: 10 passing

### Tier 2 — Capability Gaps
- **`@StructuredOutput`** + `@OutputField` — type-safe LLM output parsing
  - `JsonSchemaGenerator` builds JSON schema prompts from annotated POJOs (pure reflection, no Jackson)
  - `StructuredOutputParser` extracts JSON from prose, populates fields via reflection, retries on malformed
  - Integrated at Step 9c in `AgentWrapper.execute()` — `AgentResponse.structuredOutput(Class<T>)` accessor
  - Phase 33 tests: 10 passing
- **`@Benchmark`** — golden dataset evaluation
  - `BenchmarkDataset` — inline builder, classpath JSON/CSV loading (pure Java)
  - `BenchmarkRunner` — runs agent on each case via `ctx.submit()`, scores with `EvalJudge`
  - `BenchmarkReport` — `passRate()`, `perCriteriaAverage()`, `isRegression()` with baseline comparison
  - `BenchmarkRegressionException` — thrown when `passRate < minScore && failOnRegression=true`
  - Phase 34 tests: 10 passing
- **`@OptimizePrompt`** — DSPy-style automated prompt optimisation
  - `PromptOptimizerEngine` — evaluate → collect failures → proposeRewrite → evaluate candidate → accept/reject
  - `PromptVersionStore` — in-process per-agent prompt version history (`best()`, `latest()`, `all()`)
  - `PromptVersion` record — iteration, prompt, score, changeRationale, createdAt
  - Phase 35 tests: 10 passing
- **`@Debate`** — multi-agent debate protocol
  - `DebateEngine` — initial positions → cross-critique → revision → word-overlap convergence check → VoteCollector → LLM consensus
  - `DebateResult` — all rounds, `voteResult()`, `consensus()`, `converged()`
  - Phase 36 tests: 10 passing
- **`OtelSpanExporter`** — async OTLP/HTTP trace export (Jaeger, Grafana Tempo, etc.)
  - Zero OTEL SDK dependency — hand-built OTLP JSON
  - `OtelTraceContext` — W3C traceparent format (`00-{traceId32}-{spanId16}-01`)
  - `OtelExporterConfig.fromEnv()` reads `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME`
  - Spring Boot: `squad.otel.enabled=true` + `squad.otel.endpoint=http://jaeger:4318`
  - Phase 37 tests: 10 passing

### Tier 2 — Dedicated Test Phases (implementations existed; coverage added)
- **`@Guardrails`** (Phase 40): 10 tests — `PiiDetector`, `PromptInjectionDetector`, `ToxicityFilter`, `GuardrailEngine` pipeline, `SquadContext` wiring. Fix: `AgentWrapper` now catches `GuardrailException` at Step 5 → `AgentResponse.failure()`
- **`@AgentMemory`** (Phase 41): 10 tests — `MemoryRecord`, `InProcessMemoryStore`, `MemoryRouter`, `MockEmbeddingPort` determinism, `MemoryManager` read/write/closeSession
- **`@SquadTool`** (Phase 42): 10 tests — `SquadToolRegistry` discovery, `SquadToolDefinition` schema prompt, `SquadToolExecutor` tool-call loop, `TOOL_RESULT` feedback
- **`@AutoPlan`** (Phase 43): 10 tests — `AutoPlanEngine` loop, stop-condition, `RETURN_BEST`/`THROW` policies, `PlanIteration`
- **`@RemoteSquad`** (Phase 44): 10 tests — `NoAuth`/`ApiKeyAuth`/`JwtAuth`, `RemoteSquadInvoker` field injection, `SquadClient` HTTP POST/health/error handling

### squad-spring-boot-starter
- Added `@Bean` auto-configuration for all Tier 2/3 features:
  - `BenchmarkRunner` (`squad.benchmark.enabled=true`)
  - `PromptOptimizerEngine` + `PromptVersionStore` (`squad.optimize.enabled=true`)
  - `DebateEngine` (`squad.debate.enabled=true`)
  - `OtelSpanExporter` (`squad.otel.enabled=true`)
  - `McpServer` (`squad.mcp.server.enabled=true`)
- Added `SquadProperties` nested classes: `Otel`, `Benchmark`, `Optimize`, `Debate`; extended `Mcp` with `serverEnabled` + `serverPort`
- Added optional `squad-mcp-server` dependency

### Stats
- Test phases: 44 phases, 440+ test assertions, all passing
- New annotations (Tier 2): `@StructuredOutput`, `@OutputField`, `@Benchmark`, `@OptimizePrompt`, `@Debate`
- New modules: `squad-mcp-server`, `squad-test`
- New packages: `structured`, `benchmark`, `optimize`, `debate`, `otel`, `mcp/server`

## v3.9.0 (2026-04-13) — 5 AI Framework Innovations (Tier 1)
### Added
- **Reflexion Engine** (`@Reflexion`) — self-improving agents via score-critique-retry loops
  - `ReflexionEngine` scores agent output with `EvalJudge`, generates critique, re-runs LLM with enriched prompt
  - `ReflexionResult` — full iteration history, per-iteration `EvalScore`, `bestScore()`, `thresholdReached()`
  - `@Reflexion(maxIterations=3, scoreThreshold=0.80, criteria={FAITHFULNESS,COMPLETENESS,RELEVANCE})`
  - Integrated at step 9b in `AgentWrapper.execute()` — pre-checks (rate-limit, circuit-breaker) apply only once
  - Phase 27 tests: 10 passing
- **Agent Graph Topology** (`@Topology`, `@AgentEdge`) — formal typed multi-agent wiring
  - `TopologyGraph` — directed adjacency-list graph with BFS, DFS, Kahn topological sort, cycle detection
  - `TopologyEngine` — validates at boot, executes in topological order for DAGs, BFS rounds for cyclic layouts
  - `TopologyResult` — per-step `AgentResponse` map, execution order, elapsed time, final output
  - `EdgeType` enum: `DELEGATES`, `INFORMS`, `APPROVES`, `NOTIFIES`, `COMPETES`
  - `TopologyLayout` enum: `PIPELINE`, `STAR`, `HIERARCHY`, `MESH`, `RING`
  - `SquadContext.submitTopology(input)` — new entry point for graph execution
  - Phase 28 tests: 12 passing
- **Semantic Router** (`@SemanticRouter`) — embedding-based intelligent agent dispatch
  - `SemanticRouterEngine` — pre-computes agent description embeddings at boot; cosine-similarity routing at runtime
  - `CosineSimilarity` — pure-Java dot-product similarity utility (no external deps)
  - `SemanticRouteResult` — agent name, role, confidence score, fallback flag
  - `SquadContext.submitSemantic(task)` — new entry point for semantic routing
  - `AgentRegistry.getByName(String)` — new lookup method used by Topology and Semantic Router
  - `SquadConfig.forTesting(List<Class<?>>)` — test factory method, enables unit-testing SquadContext directly
  - Phase 29 tests: 10 passing
- **Cost-Aware Model Routing** (`@CostPolicy`) — automatic budget-based model degradation
  - `ModelPricingTable` — USD cents per 1K tokens for 16 models; loads `model-pricing.properties` from classpath
  - `CostTracker` — sliding-window per-agent cost accounting (thread-safe, purges stale entries)
  - `CostAwareLlmPort` — wraps any `LlmPort`; switches `primaryModel` → `fallbackModel` when spend ≥ `degradeAt × budget`
  - `@CostPolicy(primaryModel="gpt-4o", fallbackModel="gpt-4o-mini", budgetCentsPerHour=5.0, degradeAt=0.80)`
  - Wired via `AgentWrapper.applyCostPolicy()` called from `SquadContext.boot()`
  - Phase 30 tests: 10 passing
### Stats
- `squad-core-3.9.0.jar`: 262 classes (+22 vs 3.8.0)
- `squad-spring-boot-starter-3.9.0.jar`: 23 classes (unchanged)
- All 30 test phases passing (42 new tests across phases 27–30)
- 5 new annotations: `@Reflexion`, `@Topology`, `@AgentEdge`, `@SemanticRouter`, `@CostPolicy`
- 2 new enums: `EdgeType`, `TopologyLayout`
- 4 new packages: `reflexion`, `topology`, `router`, `cost`

## v3.8.0 (2026-04-12) — Dedicated Monitoring Dashboard (squad-dashboard-api + squad-dashboard-ui)
### Added
- **`squad-dashboard-api`** — standalone Spring Boot REST + SSE monitoring backend (port 8090)
  - `GET /api/v1/agents` — all registered agents with aggregated metrics (calls, errors, tokens, latency)
  - `GET /api/v1/traces` — up to 500 agent spans, filterable by agent/status
  - `GET /api/v1/metrics` — aggregate snapshot: totals, p95/p99 latency, per-agent breakdown, 60-min time-series
  - `GET /api/v1/workflows` — durable workflow states, filterable by RUNNING/PAUSED/COMPLETED/FAILED
  - `GET /api/v1/security` — security/audit events, filterable by severity and type
  - `GET /api/v1/health` — JVM heap, thread count, uptime, Redis/simulation mode, active agent count
  - `GET /api/v1/activity/stream` — SSE event stream (real-time fan-out to all connected UI clients)
  - `GET /api/v1/activity` — last 200 activity events (ring-buffer)
  - `GET /api/v1/ping` — health check with data source mode
  - Two data source modes — auto-selected at startup:
    - **Redis mode** (`squad.redis.enabled=true`): reads `squados:traces` (LIST), `squados:traces:tokens` (STRING), `squados:durable:*` (SCAN) — the exact keys written by `RedisTraceExporter` and `RedisDurableStore`; refreshes every 5s
    - **Simulation mode** (default): realistic synthetic data updated by `@Scheduled` tick every 3s; auto-fallback if Redis ping fails
  - `RedisDataReader` — zero-Jackson span/workflow parser matching `RedisTraceExporter` JSON and `RedisDurableStore` pipe-delimited format
  - `DashboardDataService` — derives per-agent metrics by aggregating real spans; rebuilds 60-min time-series buckets from span timestamps
  - `ActivityEventService` — thread-safe SSE fan-out with 200-event ring-buffer for late-joining clients
  - `CorsConfig` — CORS for React dev server (Vite proxy)
  - Compiler flag `-parameters` enabled — fixes Spring 6 `@RequestParam` name resolution without explicit `name=`
- **`squad-dashboard-ui`** — React 18 + TypeScript + Vite monitoring frontend (port 5173)
  - 7 tabs: **Overview**, **Agents**, **Traces**, **Metrics**, **Workflows**, **Security**, **Live Activity**
  - Overview: 8 KPI stat cards, calls/tokens/latency AreaCharts, error PieChart, top-5 agents BarChart
  - Agents: searchable list with success-rate progress bar, per-agent RadarChart performance profile
  - Traces: 200-row span table with agent/status filter, full detail side panel
  - Metrics: per-agent calls vs errors BarChart, token usage, latency BarCharts, 60-min call-rate LineChart, error breakdown
  - Workflows: state-filtered list (RUNNING/PAUSED/COMPLETED/FAILED), checkpoint step timeline, error display
  - Security: severity/type filters, PieChart + BarChart breakdown, event log
  - Activity: live SSE event stream with animated connection indicator (green/red pulse)
  - Top bar shows **⬢ Redis** or **◎ Simulation** badge — always visible data source indicator
  - `useSSE` hook — native `EventSource`, auto-reconnect, 200-event in-memory buffer
  - `useApi` hook — polling with configurable interval, loading/error state
  - Pure monitoring — no write operations (approval actions, agent control removed)
### Removed
- `squad-dashboard` — old monolithic Spring Boot + vanilla JS dashboard replaced by the split api/ui architecture
### Modules
- `io.github.sgpatel:squad-dashboard-api:3.8.0` (local, not published to Maven Central)
- `squad-dashboard-ui` (local Vite app, `npm run build` → `dist/`)

## v3.7.0 (2026-04-12) — Full Feature Release: 7 Gap Annotations Implemented
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
- **LLM Streaming, Guardrails, Durable Workflows, Pipeline Orchestration, Rate Limiting,
  Conversation History, MCP Tools, Remote Squads, Agent HTTP API** — see v3.5–v3.6 for details
- **New Annotations**: `@Streaming`, `@Guardrails`, `@DurableAgent`, `@Retry`, `@Pipeline`, `@Step`,
  `@Condition`, `@RateLimit`, `@McpServer`, `@RemoteSquad`, `@AgentAPI`
- **@Timeout** — Hard LLM call deadline via `CompletableFuture.orTimeout()`
  - `timeoutMs` configurable per agent; `action="fail"` throws `AgentTimeoutException`
  - `action="fallback"` returns `fallbackResponse` without touching the circuit breaker
  - Timeout errors counted by circuit breaker and reported via `@Observe`
- **@Cache** — In-process response cache; zero tokens on cache hit
  - `mode="EXACT"` — MD5 hash of (systemPrompt + userMessage); O(1) lookup
  - `mode="SEMANTIC"` — cosine similarity against stored embeddings; requires `EmbeddingPort`
  - `ttlSeconds` configurable TTL; entries evicted lazily on next access
  - New package: `io.squados.cache` (`CacheEngine`, `CacheEntry`)
- **@Observe** — Structured metrics emission per agent call
  - New `MetricsPort` SPI (`io.squados.metrics`); `InMemoryMetricsCollector` (zero deps)
  - `MicrometerMetricsAdapter` (starter): Prometheus counters + timers when `spring-boot-actuator` present
  - Emits: `{ns}_agent_calls_total`, `{ns}_agent_latency_seconds`, `{ns}_agent_tokens_total`, `{ns}_agent_errors_total`
  - Custom `namespace` + static `tags` per agent
- **@PromptTemplate** — Externalise system prompts to classpath files
  - `{{agentName}}`, `{{role}}`, `{{description}}`, `{{profile}}`, `{{taskId}}` substituted at runtime
  - Falls back gracefully to default inline prompt if file not found
- **@AgentPool** — High-throughput load balancing across multiple agent instances
  - `size` instances created at boot; all share the same metadata and `LlmOptions`
  - Strategies: `ROUND_ROBIN` (default), `LEAST_BUSY` (inflight tracking), `RANDOM`
  - `maxQueueSize` per instance; overflow falls back to round-robin
  - New package: `io.squados.pool` (`AgentPoolManager`)
  - `SquadContext.submitTo()` routes through pool transparently
- **@AgentTest** — Golden-set testing with JSON test cases
  - `testCasesPath` — classpath JSON: `[{"input":"...","expectedContains":"...","expectedMinWords":N}]`
  - `passRateMin` threshold (default 0.80); throws `EvalFailedException` if below
  - `SquadContext.runAgentTests()` — run all `@AgentTest` agents; `runAgentTests(role)` — targeted
  - New class: `AgentTestRunner` (`io.squados.eval`); zero-dep JSON parser
- **@Checkpoint** — Mid-workflow state persistence for `@DurableAgent` classes
  - Annotate methods with `@Checkpoint(name="step-name")`; return value saved to `DurableStore`
  - On JVM restart, checkpointed step is skipped and cached result returned
  - Key format: `chk:{workflowId}:{agentName}:{checkpointName}`
  - `CheckpointEngine` (`io.squados.checkpoint`) handles save/restore
  - `DurableStore` now injected into all `AgentWrapper` instances at boot
- **New Exceptions**: `RateLimitExceededException`, `GuardrailException`, `RetryExhaustedException`,
  `DurableWorkflowException`, `AgentTimeoutException`, `EvalFailedException` (pass-rate form)
- **squad-spring-boot-starter** new beans:
  - `GuardrailEngine` (squad.guardrails.enabled=true)
  - `DurableStore` (squad.durable.enabled=true, store=memory|redis)
  - `ConversationStore` (squad.conversation.enabled=true)
  - `McpToolProvider` / `HttpMcpClient` (squad.mcp.enabled=true)
  - `RateLimitEnforcer` (always active)
  - `RemoteSquadInjector` BeanPostProcessor (always active)
  - `AgentApiRegistrar` (squad.agent-api.enabled=true)
  - `SpringAiEmbeddingAdapter` (squad.memory.enabled=true + EmbeddingModel present)
  - `InMemoryMetricsCollector` (always active fallback)
  - `MicrometerMetricsAdapter` (when spring-boot-actuator + MeterRegistry present)
- **LlmPortConfig** — separate `@AutoConfiguration` to fix `BeanPostProcessor` early-instantiation
  - `RemoteSquadInjector` BeanPostProcessor forced early instantiation of `SquadAutoConfiguration`
  - Splitting `squadLlmPort` into `LlmPortConfig` ensures `ChatClient.Builder` is available
- **Phase 21-26 tests** — 114 new tests (DurableStore, GuardrailEngine, RetryEngine, Streaming, Pipeline, ConditionEvaluator)
### Fixed
- `LlmPortConfig`: removed incorrect `@ConditionalOnBean(name="ChatClient$Builder")` — `$` inner-class
  separator never matches Spring's registered bean name; `@ConditionalOnClass` alone is sufficient
- `pom.xml`: `spring-webmvc` made optional (was forcing Spring Web on all starter consumers)
- `SpringAiEmbeddingAdapter`: updated for Spring AI 1.0.0 GA — `embed(String)` now returns `float[]`
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
