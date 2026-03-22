# Contributing to SquadOS

Thank you for your interest in contributing to SquadOS — Multi-Agent AI Framework for Java.

## Setup

```bash
git clone https://github.com/sgpatel/squad-os
cd squad-os
mvn clean install -DskipTests   # builds all modules
mvn clean test                   # runs all 136 tests
```

## Project structure

- `squad-core/`  — the framework. Zero runtime dependencies. This is what gets published to Maven Central.
- `squad-starter/` — Hello Squad quickstart with Ollama.
- `squad-examples/` — real-world examples (daily-planner, code-review).

## Adding a new phase / feature

1. Write the production code in `squad-core/src/main/java/io/squados/`
2. Write 17 tests in `squad-core/src/test/java/io/squados/tests/SquadOsPhaseNTests.java`
3. Add the test execution to `squad-core/pom.xml`
4. All 17 tests must pass before a PR is merged

## Adding a new example

1. Create `squad-examples/your-example/`
2. Follow the pattern in `squad-examples/daily-planner/`
3. Three agents minimum, parallel execution via `SquadTask`
4. Must work with `mvn spring-boot:run` out of the box

## Pull request checklist

- [ ] `mvn clean test` passes (all tests green)
- [ ] New features have corresponding tests
- [ ] squad-core has no new runtime dependencies
- [ ] README updated if public API changed
- [ ] CHANGELOG.md entry added

## Commit message format

```
feat: short description of the feature

- Detail 1
- Detail 2

Tests: X new tests, Y total
```
