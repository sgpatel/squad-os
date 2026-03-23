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
