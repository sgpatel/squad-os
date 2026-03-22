# SquadOS Changelog

## v2.9.0 (2026-03-22) — @Improve few-shot learning
### Added
- @Improve: agents learn from human feedback without retraining
  topK, minExamples, includeNegativeExamples, label, store
- FeedbackExample: input + output + GOOD/BAD label + note
  toPromptExample(): formats for few-shot injection
- FeedbackStore interface + InProcessFeedbackStore (Jaccard similarity)
- ImproveEngine: retrieves examples, builds few-shot prompt enrichment
  saveFeedback(): convenience method for collecting human feedback
- Phase 18: 17 tests (F01-F17) — Total: 306 tests

## v2.8.0 (2026-03-22) — @Traced OpenTelemetry
### Added
- @Traced: records OpenTelemetry spans per agent call
  spanName, trackTokens, trackIO, minDurationMs, traceOnError
- AgentSpan: traceId, spanId, role, name, latency, tokens, status
- TraceExporter interface (pluggable: Jaeger, Datadog, Grafana, Log, Memory)
- LogTraceExporter: stdout, zero config
- InMemoryTraceExporter: getByName, getErrors, totalTokens, avgDurationMs
- SquadTracer: configure/record/recordMethod/newTrace singleton
- Phase 17: 17 tests (T01-T17) — Total: 289 tests

## v2.7.0 (2026-03-22) — @AutoPlan agentic loops
### Added
- @AutoPlan: plan-execute-reflect-replan until goal condition met
  goal, maxIterations, stopCondition, reflectOn, accumulate
  onMaxIterations: RETURN_BEST | RETURN_LAST | THROW
- PlanIteration: snapshot of one cycle (plan, output, reflection, goalMet)
- AutoPlanResult: final output + iteration history + getBestIteration()
- AutoPlanEngine: orchestrates loop, injects context each iteration
- Phase 16: 17 tests (P01-P17) — Total: 272 tests

## v2.6.0 (2026-03-22) — @Eval quality gate
### Added
- @Eval: evaluates agent output, auto-retries below threshold
  judge, minScore, criteria, retryOnFail, maxRetries, onFail
- EvalCriteria: FAITHFULNESS | COMPLETENESS | RELEVANCE | CLARITY | CORRECTNESS | SAFETY
- EvalScore: per-criterion scores, overall average, attempt tracking
- EvalJudge: LLM-based scorer, regex score extraction
- EvalRunner: orchestrates eval + retry loop, injects feedback on retry
- Phase 15: 17 tests (Q01-Q17) — Total: 255 tests

## v2.5.0 (2026-03-22) — @OnEvent event-driven squads
### Added
- @OnEvent: trigger agent methods on Kafka/webhook/timer/in-process events
  topic, filter, cron, concurrency, retryOnError, maxRetries
- SquadEvent: topic, payload, source, headers, retry count
- EventSource: pluggable interface (Kafka, webhook, Redis, in-process)
- InProcessEventBus: wildcard subscriptions, crash isolation
- EventRouter: discovers @OnEvent methods, retry with backoff
- Phase 14: 17 tests (E01-E17) — Total: 238 tests

## v2.4.0 (2026-03-22) — @SquadVote consensus
### Added
- @SquadVote: collects votes from agents, resolves consensus
- VoteRule: MAJORITY | UNANIMOUS | ANY | SUPERMAJORITY | WEIGHTED
- TieBreaker: ESCALATE | REJECT | APPROVE | ABSTAIN
- Vote: approve/reject/abstain with reason, voter name, weight
- VoteCollector: thread-safe, CountDownLatch, timeout
- VoteResult: outcome, breakdown, weighted totals, audit summary
- Phase 13: 17 tests (V01-V17) — Total: 221 tests

## v2.3.0 (2026-03-22) — @AwaitApproval + @AutoApproval
### Added
- @AwaitApproval: pauses agent, waits for human approval
  timeoutHours, onTimeout (REJECT|APPROVE|ESCALATE), priority, escalateTo
- @AutoApproval: rule-based instant approval
  condition syntax: field < value, AND, OR, rejectOnMatch
- ApprovalRequest: PENDING → APPROVED/REJECTED/TIMED_OUT state machine
- ApprovalStore + InProcessApprovalStore
- ApprovalEngine: evaluates conditions, orchestrates full flow
- Phase 12: 17 tests (A01-A17) — Total: 204 tests

## v2.2.0 (2026-03-22) — @SquadTool real API calls
### Added
- @SquadTool: marks Java method as tool the LLM can invoke
- @ToolParam: describes parameter with description + required flag
- SquadToolRegistry: discovers @SquadTool methods via reflection
- SquadToolExecutor: TOOL_CALL/TOOL_RESULT loop, position-based args
  Supports: String, int, long, double, boolean
  Graceful error handling — exceptions returned as ERROR: message
- Phase 11: 17 tests (T01-T17) — Total: 187 tests

## v2.1.0 (2026-03-22) — @SquadPlan typed output
### Added
- @SquadPlan: marks class as typed LLM response schema
- @Required: marks field as mandatory in LLM response
- SquadPlanDeserialiser: JSON → typed object, zero external deps
  Strips code fences, validates @Required fields
- SquadContext.submit(input, Class<T>): new typed API
- Phase 10: 17 tests (S01-S17) — Total: 170 tests

## v2.0.0 (2026-03-22) — Multi-node Squads via Redis
### Added
- RedisAgentBus: drop-in replacement for AgentMessageBus
  Agents on different JVMs subscribe to same Redis channel
- SquadRegistry: distributed agent discovery with 30s TTL heartbeat
- RedisCircuitBreakerStore: shared circuit state across all nodes
- AgentMessage.toJson/fromJson for Redis transport
- AgentRole.WILDCARD for broadcast subscriptions
- Phase 9: 17 tests (N01-N17) — Total: 153 tests

## v1.2.0 (2026-03-21) — pgvector persistent memory
### Added
- PgVectorEpisodicStore: PostgreSQL + pgvector HNSW index
- RedisWorkingStore: session-scoped Redis HASH with 2h TTL
- MemoryStoreFactory: inProcess / withPgVector / production
- Real embeddings via SpringAiEmbeddingAdapter (nomic-embed-text)
- Phase 8: 17 tests — Total: 136 tests

## v1.1.0 (2026-03-21) — Parallel agents
### Added
- ParallelExecutor with Java 21 virtual threads
- SquadTask / SquadResult with speedupRatio()
- Daily Planner: 2.3x speedup measured
- Phase 7: 17 tests — Total: 119 tests

## v1.0.0 (2026-03-21) — Core framework
### Added
- @Agent, AgentRole, @PostConstruct, @OnMessage, @Memory, @MissionProfile
- LlmPort + SpringAiLlmAdapter (Spring AI Ollama)
- SquadContext, AgentRegistry, AgentScanner, SquadRunner
- SquadConfigParser (squad.yml), SquadConfigBridge
- InProcessMemoryStore (four-tier memory)
- AgentMessageBus pub/sub with @OnMessage routing
- AgentCircuitBreaker, AgentHealth
- MissionState + TokenBudget
- Phases 1-6: 102 tests
