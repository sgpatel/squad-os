# SquadOS Changelog

## v2.1.0 (2026-03-22)
### Added
- @SquadPlan annotation — marks a class as typed LLM response schema
- @Required annotation — marks a field as mandatory in LLM response
- SquadPlanDeserialiser — JSON to typed object, zero external dependencies
  Supports: String, int, long, boolean, List<String>
  buildSchemaPrompt() auto-generates JSON schema for agent system prompt
  Strips markdown code fences automatically
  Validates @Required fields after deserialisation
- SquadContext.submit(input, Class<T>) — new typed API
- SquadContext.submitTo(role, input, Class<T>) — role-specific typed API
- SquadPlanException — thrown on deserialisation or validation failure
- Daily Planner upgraded: DayPlan + TimeEstimate + CoachAdvice typed output
- AgentRole.WILDCARD — for broadcast subscriptions in RedisAgentBus
- MessageType: PING, TASK_COMPLETE, ATTACK, HEAL, DEFEND added
- Phase 10: 17 new tests (S01-S17)

### Tests
- Total: 170 tests, 0 failures, 10 phases

## v2.0.0 (2026-03-22)
### Added
- RedisAgentBus: drop-in replacement for AgentMessageBus via Redis Pub/Sub
  Agents on different JVMs subscribe to same Redis channel
  Handler crash isolation preserved across nodes
  WILDCARD subscription for broadcast messages
- SquadRegistry: distributed agent discovery with TTL heartbeat
  register(), find(role), findAll(), deregisterAll()
  Stale nodes auto-expire after 30s without heartbeat
- RedisCircuitBreakerStore: distributed circuit state via Redis
  Circuit OPEN on Node A visible to Node B immediately
  recordFailure/recordSuccess/allowCall all via shared Redis
- AgentMessage.toJson() / fromJson() for Redis transport
- Phase 9: 17 new tests (N01-N17)

### Tests
- Total: 153 tests, 0 failures, 9 phases

## v1.2.0 (2026-03-21)
### Added
- PgVectorEpisodicStore: PostgreSQL + pgvector HNSW index
- RedisWorkingStore: session-scoped Redis HASH with 2h TTL
- MemoryStoreFactory: inProcess / withPgVector / production
- OllamaEmbeddingAdapter stub + real SpringAiEmbeddingAdapter in squad-starter
- Daily Planner: pgvector persistent memory with pattern learning
- docker-compose.yml: one-command PostgreSQL + pgvector
- @ConditionalOnProperty: pgvector/real-embeddings toggle
- SQL migration: V1__squados_memory.sql with HNSW index
- Phase 8: 17 new tests (G01-G17)

## v1.1.0 (2026-03-21)
### Added
- ParallelExecutor: CompletableFuture.allOf() + Java 21 virtual threads
- SquadTask: .of(input).assignTo(roles).withLabel().withTimeout()
- SquadResult: .get(role), .wallClockMs(), .speedupRatio(), .allSucceeded()
- SquadContext.execute(SquadTask): parallel execution API
- Daily Planner: 2.3x faster (parallel agents)
- Phase 7: 17 new tests (P01-P17)

## v1.0.0 (2026-03-21)
### Added
- @Agent, AgentRole, @PostConstruct, @OnMessage, @Memory, @MissionProfile
- LlmPort interface + SpringAiLlmAdapter (Spring AI Ollama)
- SquadContext, AgentWrapper, AgentRegistry, AgentScanner, SquadRunner
- SquadConfigParser (squad.yml), SquadConfigBridge
- InProcessMemoryStore (four-tier: Working/Semantic/Procedural/Episodic)
- MemoryRouter with cosine similarity search + Ebbinghaus decay
- AgentMessageBus pub/sub with @OnMessage routing
- AgentCircuitBreaker, AgentHealth (AtomicInteger counters)
- MissionState (ConcurrentHashMap + optimistic locking)
- TokenBudget (per-agent hard/soft caps)
- squad-starter: Hello Squad on local Ollama llama3.2
- squad-examples/daily-planner: 3-agent daily planner
- squad-examples/code-review: 3 specialist code reviewers
- Phases 1-6: 102 tests, 0 failures
