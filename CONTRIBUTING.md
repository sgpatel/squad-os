# Contributing to SquadOS

Multi-Agent AI Framework for Java — 20 phases, 340 tests, contributions welcome!

## Setup

```bash
git clone https://github.com/sgpatel/squad-os
cd squad-os
mvn clean install -DskipTests   # build all modules
mvn clean test                   # run all 340 tests
```

## Project structure

```
squad-os/
├── squad-core/          Framework — zero runtime deps, published to Maven Central
├── squad-starter/       Hello Squad quickstart with Ollama
└── squad-examples/
    ├── daily-planner/   @SquadPlan + pgvector memory
    ├── code-review/     Multi-agent code review
    ├── multi-node/      Redis Pub/Sub two-node demo
    ├── embedding-demo/  Semantic similarity comparison
    └── snack-thief/     All 15 annotations — office pizza crime 🍕
```

## Annotation packages at a glance

| Package | Contains |
|---------|---------|
| `io.squados.annotation` | All annotations + enums (AgentRole, VoteRule, AccessMode, DelegateStrategy...) |
| `io.squados.approval` | @AwaitApproval + @AutoApproval engine |
| `io.squados.delegate` | @Delegate + DelegateRouter + DelegationDecision |
| `io.squados.eval` | @Eval + EvalJudge + EvalRunner + EvalScore |
| `io.squados.event` | @OnEvent + EventRouter + InProcessEventBus + SquadEvent |
| `io.squados.improve` | @Improve + FeedbackStore + ImproveEngine + FeedbackExample |
| `io.squados.plan` | @AutoPlan + AutoPlanEngine + PlanIteration + AutoPlanResult |
| `io.squados.security` | @SecureAgent + SecurityGuard + JwtValidator + AuditLog + SecurityContext |
| `io.squados.tool` | @SquadTool + SquadToolRegistry + SquadToolExecutor |
| `io.squados.trace` | @Traced + SquadTracer + AgentSpan + TraceExporter implementations |
| `io.squados.vote` | VoteCollector + VoteResult + Vote |
| `io.squados.memory` | Four-tier memory + PgVectorEpisodicStore + RedisWorkingStore |
| `io.squados.bus` | AgentMessageBus + RedisAgentBus + AgentMessage |
| `io.squados.context` | SquadContext + SquadRunner + SquadRegistry + AgentRegistry |
| `io.squados.exception` | All typed exceptions |

## Adding a new feature (phase convention)

1. Write production code in `squad-core/src/main/java/io/squados/`
2. Write exactly **17 tests** in `squad-core/src/test/java/io/squados/tests/SquadOsPhaseNTests.java`
3. Add the test execution entry to `squad-core/pom.xml`
4. All 17 tests must pass before merging
5. Update README.md feature table, CHANGELOG.md, CONTRIBUTING.md package table

### Test file naming convention
```
SquadOsPhase01Tests.java   (phases 1-6 are combined)
SquadOsPhase07Tests.java   (parallel agents)
SquadOsPhase08Tests.java   (pgvector memory)
...
SquadOsPhase20Tests.java   (delegate routing)
```

### Test ID convention
Each test has a 2-letter prefix + 2-digit number:
```
V01-V17   @SquadVote tests
E01-E17   @OnEvent tests
Q01-Q17   @Eval tests
P01-P17   @AutoPlan tests
T01-T17   @Traced tests
F01-F17   @Improve tests
S01-S17   @SecureAgent tests
D01-D17   @Delegate tests
```

## squad-core dependency rules

**CRITICAL: squad-core must have zero runtime dependencies.**

- No Spring AI in squad-core production code
- No LangChain4j in squad-core production code
- No external JWT libraries in squad-core
- No Jackson / Gson — use hand-rolled JSON parsing only
- Adapters (SpringAiLlmAdapter etc.) live in squad-starter and squad-examples

## Pull request checklist

- [ ] `mvn clean test` passes (340 tests green)
- [ ] squad-core has zero new runtime dependencies
- [ ] New feature has exactly 17 tests in a new Phase file
- [ ] Package reference table in CONTRIBUTING.md updated
- [ ] README.md feature table updated
- [ ] CHANGELOG.md entry added with version

## Maven Central releases

Push a git tag `vX.Y.Z` to trigger GitHub Actions publish of `squad-core`.
Always bump all pom versions before tagging.
Maven Central is immutable — never tag the same version twice.

**IMPORTANT: Always ask before pushing any git tag.**
