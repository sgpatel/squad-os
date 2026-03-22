# Contributing to SquadOS

Multi-Agent AI Framework for Java — 18 phases, 306 tests, contributions welcome!

## Setup

```bash
git clone https://github.com/sgpatel/squad-os
cd squad-os
mvn clean install -DskipTests   # build all modules
mvn clean test                   # run all 306 tests
```

## Project structure

- `squad-core/` — the framework. Zero runtime deps. Published to Maven Central.
- `squad-starter/` — Hello Squad quickstart with Ollama.
- `squad-examples/` — daily-planner, code-review, multi-node, embedding-demo, snack-thief.

## Adding a new feature (phase convention)

1. Write production code in `squad-core/src/main/java/io/squados/`
2. Write 17 tests in `squad-core/src/test/java/io/squados/tests/SquadOsPhaseNTests.java`
3. Add the test execution to `squad-core/pom.xml`
4. All 17 tests must pass before merging
5. Update README.md, CHANGELOG.md with the new phase

## Quick reference — annotation packages

| Package | Contains |
|---------|---------|
| `io.squados.annotation` | All annotations + enums |
| `io.squados.approval` | @AwaitApproval + @AutoApproval engine |
| `io.squados.eval` | @Eval engine |
| `io.squados.event` | @OnEvent + EventRouter + InProcessEventBus |
| `io.squados.improve` | @Improve + FeedbackStore + ImproveEngine |
| `io.squados.plan` | @AutoPlan engine |
| `io.squados.tool` | @SquadTool registry + executor |
| `io.squados.trace` | @Traced + SquadTracer + exporters |
| `io.squados.vote` | @SquadVote + VoteCollector + VoteResult |
| `io.squados.memory` | Four-tier memory + pgvector + Redis |
| `io.squados.bus` | AgentMessageBus + RedisAgentBus |
| `io.squados.context` | SquadContext + SquadRunner + SquadRegistry |

## Pull request checklist

- [ ] `mvn clean test` passes (306 tests green)
- [ ] squad-core has no new runtime dependencies
- [ ] New features have 17 tests in a new Phase file
- [ ] README.md feature table updated
- [ ] CHANGELOG.md entry added

## Maven Central releases

Push a git tag `vX.Y.Z` → GitHub Actions publishes `squad-core` automatically.
Always bump versions in all poms before tagging.
Never tag the same version twice — Maven Central is immutable.
