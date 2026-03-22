# Contributing to SquadOS

Multi-Agent AI Framework for Java — contributions welcome!

## Setup

```bash
git clone https://github.com/sgpatel/squad-os
cd squad-os
mvn clean install -DskipTests   # builds all modules
mvn clean test                   # runs all 170 tests
```

## Project structure

- `squad-core/` — the framework. Zero runtime dependencies. Published to Maven Central.
- `squad-starter/` — Hello Squad quickstart with Ollama.
- `squad-examples/` — real-world examples (daily-planner, code-review, embedding-demo).

## Adding a new feature (phase conventions)

1. Write production code in `squad-core/src/main/java/io/squados/`
2. Write 17 tests in `squad-core/src/test/java/io/squados/tests/SquadOsPhaseNTests.java`
3. Add the test execution to `squad-core/pom.xml`
4. All 17 tests must pass before merging

## Adding a @SquadPlan output class

```java
@SquadPlan(description = "Your output description")
public class MyOutput {
    @Required public String summary;        // mandatory field
    public List<String> items;              // optional list
    public int score;                       // optional int
}

// Use it:
MyOutput output = ctx.submit("Do the thing", MyOutput.class);
```

## Adding a new example

1. Create `squad-examples/your-example/`
2. Follow the pattern in `squad-examples/daily-planner/`
3. Three agents minimum, parallel execution via `SquadTask`
4. Use `@SquadPlan` for typed output

## Pull request checklist

- [ ] `mvn clean test` passes (170 tests green)
- [ ] New features have corresponding tests
- [ ] squad-core has no new runtime dependencies
- [ ] README.md and CHANGELOG.md updated

## Commit format

```
feat: short description

- Detail 1
- Detail 2

Tests: X new, Y total
```

## Maven Central releases

Releases are automated — push a git tag `vX.Y.Z` to trigger GitHub Actions:
1. Runs all tests
2. Publishes squad-core to Maven Central
3. Creates GitHub Release with dependency snippet
