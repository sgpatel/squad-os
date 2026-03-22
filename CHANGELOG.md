# SquadOS Changelog

## v1.2.0 (2026-03-22)
### Added
- PgVectorEpisodicStore: PostgreSQL + pgvector HNSW index for persistent episodic memory
- RedisWorkingStore: session-scoped Redis HASH with configurable TTL
- MemoryStoreFactory: inProcess / withPgVector / production factory methods
- OllamaEmbeddingAdapter: real semantic embeddings via Spring AI EmbeddingModel
- Daily Planner: pgvector persistent memory with pattern learning
- docker-compose.yml: one-command PostgreSQL + pgvector setup
- @ConditionalOnProperty: pgvector and real-embeddings toggle without code changes
- SQL migration: V1__squados_memory.sql with HNSW index

### Changed
- MemoryStore interface: added default findPromotable(sessionId) method
- MemoryRouter.promoteAndFlush: removed InProcessMemoryStore cast, uses interface
- Boot banner: updated tagline to "Multi-Agent AI Framework for Java"

### Tests
- Phase 8: 17 new tests for pgvector stores (G01-G17)
- Total: 136 tests, 0 failures

## v1.1.0 (2026-03-21)
### Added
- ParallelExecutor: runs all assigned agents simultaneously via CompletableFuture.allOf()
- SquadTask: fluent builder — .of(input).assignTo(roles).withLabel().withTimeout()
- SquadResult: .get(role), .wallClockMs(), .speedupRatio(), .allSucceeded()
- SquadContext.execute(SquadTask): new parallel API alongside existing submit()
- Java 21 virtual threads: one per LLM call, no pool sizing needed
- Daily Planner upgraded: 2.3x faster than sequential

### Tests
- Phase 7: 17 new tests for parallel execution (P01-P17)
- P16 proves: 3x300ms agents complete in <700ms wall-clock
- Total: 119 tests, 0 failures

## v1.0.0 (2026-03-21)
### Added
- @Agent, AgentRole, @PostConstruct annotations
- LlmPort interface + SpringAiLlmAdapter (Spring AI Ollama)
- SquadContext, AgentWrapper, AgentRegistry, AgentScanner
- SquadConfigParser (squad.yml), SquadConfigBridge
- @Memory, MemoryType, MemoryScope, MemoryOp, Importance
- InProcessMemoryStore (four-tier: Working/Semantic/Procedural/Episodic)
- MemoryRouter with cosine similarity search
- EmbeddingPort + MockEmbeddingPort
- MemoryDecayService (Ebbinghaus decay model)
- AgentMessageBus, @OnMessage, pub/sub routing
- AgentCircuitBreaker, AgentHealth (AtomicInteger counters)
- MissionState (ConcurrentHashMap + optimistic locking)
- TokenBudget (per-agent hard/soft caps)
- @MissionProfile (role-to-role messaging rules)
- squad-starter: Hello Squad on local Ollama llama3.2
- squad-examples/daily-planner: 3-agent daily planner
- 102 tests across 6 phases, 0 failures
