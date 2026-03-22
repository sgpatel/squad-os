# SquadOS Changelog

## v3.1.0 (2026-03-22) — @Delegate dynamic routing
### Added
- @Delegate: routes tasks to specialist agents at runtime
  candidates: eligible roles, strategy: LLM_CHOICE | ROUND_ROBIN | LOAD_BALANCE | FIRST_MATCH
  fallback: used when no candidate matches, conditions: keyword expressions for FIRST_MATCH
  logDecision: audit trail, timeoutMs: delegation timeout
- DelegateStrategy enum: four routing strategies
- DelegationDecision: chosenRole, strategy, reasoning, fallback flag, timestamp
- DelegateRouter: all 4 strategies + active count tracking
  matchesCondition(): single keyword, OR, AND expressions
  startDelegation/endDelegation: live load counters per role
- Phase 20: 17 tests (D01-D17) — Total: 340 tests

## v3.0.0 (2026-03-22) — @SecureAgent RBAC + JWT
### Added
- @SecureAgent: access control on agent methods
  mode: AUTHENTICATED | PUBLIC, roles: required roles (any-match)
  auditLog, denyMessage, logOutput, rateLimit
- AccessMode enum: AUTHENTICATED / PUBLIC
- AgentIdentity: subject, roles, issuer, expiresAt — hasRole/hasAnyRole/isExpired/isAnonymous
  ANONYMOUS singleton, factory method AgentIdentity.of(subject, roles...)
- SecurityContext: ThreadLocal identity — set/current/clear/isAuthenticated
- JwtValidator: validates JWT tokens, extracts AgentIdentity
  createTestToken(): test token factory (no external deps)
  Validates: format (3 parts), expiry, issuer match, subject presence
- AuditLog: immutable AuditEntry records — getGranted/getDenied/getBySubject
- SecurityGuard: enforces @SecureAgent — PUBLIC bypass, AUTHENTICATED RBAC + rate limiting
- Phase 19: 17 tests (S01-S17) — Total: 323 tests

## v2.9.0 (2026-03-22) — @Improve few-shot learning
### Added
- @Improve: agents learn from human feedback without model retraining
  store: IN_PROCESS | PGVECTOR | REDIS, topK, minExamples, includeNegativeExamples, label
- FeedbackExample: input/output/GOOD/BAD label/note — toPromptExample() for injection
- FeedbackStore interface + InProcessFeedbackStore (Jaccard keyword similarity)
- ImproveEngine: retrieves similar examples, builds few-shot prompt enrichment
  saveFeedback(): convenience method for collecting human feedback
- Phase 18: 17 tests (F01-F17) — Total: 306 tests

## v2.8.0 (2026-03-22) — @Traced OpenTelemetry
### Added
- @Traced: OpenTelemetry spans per agent call
  spanName, trackTokens, trackIO, minDurationMs, traceOnError
- AgentSpan: traceId, spanId, role, name, latency, tokens (prompt/completion/total), status
- TraceExporter interface (pluggable: Jaeger/Datadog/Grafana/Log/Memory)
- LogTraceExporter: stdout, zero config
- InMemoryTraceExporter: getByName, getErrors, totalTokens, avgDurationMs
- SquadTracer: configure/record/recordMethod/newTrace singleton
- Phase 17: 17 tests (T01-T17) — Total: 289 tests

## v2.7.0 (2026-03-22) — @AutoPlan agentic loops
### Added
- @AutoPlan: plan-execute-reflect-replan until goal condition met
  goal, maxIterations, stopCondition, reflectOn, accumulate
  onMaxIterations: RETURN_BEST | RETURN_LAST | THROW
- PlanIteration: plan, output, reflection, goalMet, duration per cycle
- AutoPlanResult: final output, iteration history, getBestIteration()
- AutoPlanEngine: builds plan/execute/reflect prompts, injects context each iteration
- Phase 16: 17 tests (P01-P17) — Total: 272 tests

## v2.6.0 (2026-03-22) — @Eval quality gate
### Added
- @Eval: evaluates agent output, auto-retries below threshold
  judge, minScore, criteria, retryOnFail, maxRetries, onFail: THROW | RETURN_BEST
- EvalCriteria: FAITHFULNESS | COMPLETENESS | RELEVANCE | CLARITY | CORRECTNESS | SAFETY
- EvalScore: per-criterion scores, overall average, attempt number, feedback
- EvalJudge: LLM-based scorer, regex score extraction, mockScore for testing
- EvalRunner: eval + retry loop, injects judge feedback into retry prompt
- Phase 15: 17 tests (Q01-Q17) — Total: 255 tests

## v2.5.0 (2026-03-22) — @OnEvent event-driven
### Added
- @OnEvent: trigger agent methods on events
  topic, filter (keyword conditions), cron, concurrency, retryOnError, maxRetries
- SquadEvent: topic, payload, source, headers, retry count
- EventSource: pluggable interface (Kafka, webhook, Redis, in-process)
- InProcessEventBus: wildcard (*) subscriptions, crash isolation per handler
- EventRouter: discovers @OnEvent methods, retry with exponential backoff
- Phase 14: 17 tests (E01-E17) — Total: 238 tests

## v2.4.0 (2026-03-22) — @SquadVote consensus
### Added
- VoteRule: MAJORITY | UNANIMOUS | ANY | SUPERMAJORITY | WEIGHTED
- TieBreaker: ESCALATE | REJECT | APPROVE | ABSTAIN
- Vote: approve/reject/abstain with reason, voter name, weight
- VoteCollector: thread-safe, CountDownLatch, timeout, tally()
- VoteResult: outcome, approve/reject/abstain counts, weighted totals, audit summary
- Phase 13: 17 tests (V01-V17) — Total: 221 tests

## v2.3.0 (2026-03-22) — @AwaitApproval + @AutoApproval
### Added
- @AwaitApproval: pauses agent, waits for human decision
  timeoutHours, onTimeout (REJECT|APPROVE|ESCALATE), priority, escalateTo
- @AutoApproval: evaluates conditions instantly without human
  condition: field < value, AND, OR, rejectOnMatch
- ApprovalRequest: PENDING -> APPROVED/REJECTED/TIMED_OUT state machine
- ApprovalStore + InProcessApprovalStore
- ApprovalEngine: condition evaluation + orchestration
- Phase 12: 17 tests (A01-A17) — Total: 204 tests

## v2.2.0 (2026-03-22) — @SquadTool
### Added
- @SquadTool: marks Java method as LLM-invocable tool
- @ToolParam: parameter description + required flag
- SquadToolRegistry: discovers @SquadTool methods via reflection
- SquadToolExecutor: TOOL_CALL/TOOL_RESULT loop, position-based arg matching
- Phase 11: 17 tests — Total: 187 tests

## v2.1.0 (2026-03-22) — @SquadPlan
### Added
- @SquadPlan: typed LLM response schema
- @Required: mandatory field validation
- SquadPlanDeserialiser: JSON -> typed object, zero external deps, strips code fences
- SquadContext.submit(input, Class<T>): typed API
- Phase 10: 17 tests (S01-S17) — Total: 170 tests

## v2.0.0 (2026-03-22) — Multi-node Redis
### Added
- RedisAgentBus: agents across JVMs via Redis Pub/Sub
- SquadRegistry: distributed agent discovery with 30s heartbeat
- RedisCircuitBreakerStore: shared circuit state across nodes
- Phase 9: 17 tests (N01-N17) — Total: 153 tests

## v1.2.0 (2026-03-21) — pgvector memory
### Added
- PgVectorEpisodicStore: PostgreSQL + pgvector HNSW index
- RedisWorkingStore: session-scoped Redis HASH with 2h TTL
- MemoryStoreFactory: inProcess / withPgVector / production
- Phase 8: 17 tests — Total: 136 tests

## v1.1.0 (2026-03-21) — Parallel agents
### Added
- ParallelExecutor with Java 21 virtual threads
- SquadTask / SquadResult with speedupRatio()
- Phase 7: 17 tests — Total: 119 tests

## v1.0.0 (2026-03-21) — Core framework
### Added
- @Agent, AgentRole, @PostConstruct, @OnMessage, @Memory, @MissionProfile
- LlmPort + SpringAiLlmAdapter (Spring AI Ollama)
- SquadContext, AgentRegistry, AgentScanner, SquadRunner
- SquadConfigParser (squad.yml), SquadConfigBridge
- InProcessMemoryStore (four-tier memory)
- AgentMessageBus pub/sub with @OnMessage routing
- AgentCircuitBreaker, AgentHealth, MissionState, TokenBudget
- Phases 1-6: 102 tests
