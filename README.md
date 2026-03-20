# SquadOS — Phase 1

Role-based multi-agent AI framework for Java.  
Built on top of Spring AI and LangChain4j.  
Phase 1: annotations + config + context + LlmPort. Zero runtime dependencies.

---

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| JDK  | 21+     | https://adoptium.net |
| Maven | 3.8+  | https://maven.apache.org (optional — `run.sh` works without it) |

Check your versions:
```bash
java -version    # must show 21+
javac -version   # must show 21+
mvn -version     # optional
```

---

## Option A — Plain javac (no Maven)

```bash
git clone <this-repo>
cd squad-os
chmod +x run.sh

# Run all 17 tests
./run.sh

# Run the Hello Squad demo
./run.sh demo

# Run tests + demo
./run.sh all
```

Expected test output:
```
╔══════════════════════════════════════════════╗
║     SquadOS Phase 1 — Test Suite             ║
╚══════════════════════════════════════════════╝

  ✓ T01_agentRoleDefaultOptions
  ✓ T02_agentAnnotationReadableAtRuntime
  ✓ T03_llmOptionsRejectsInvalidTemperature
  ✓ T04_llmOptionsRejectsInvalidMaxTokens
  ✓ T05_mockLlmPortRecordsCallsAndMatchesResponses
  ✓ T06_configParserParsesFullYaml
  ✓ T07_configParserRejectsInvalidTemperature
  ✓ T08_configParserThrowsOnMissingFile
  ✓ T09_agentWrapperBuildsCorrectSystemPrompt
  ✓ T10_postConstructFiresExactlyOnce
  ✓ T11_agentWrapperResolvesNameFromAnnotation
  ✓ T12_agentRegistryRegisterAndRetrieve
  ✓ T13_agentRegistryLeadPriority
  ✓ T14_squadContextBootsAndRegistersAgent
  ✓ T15_squadContextSubmitRoutesToLeadAgent
  ✓ T16_squadContextSubmitToTargetsRole
  ✓ T17_squadContextBootIsIdempotent

  Results: 17 passed, 0 failed out of 17 tests

  PHASE 1 GATE: ALL TESTS PASSED ✓
```

---

## Option B — Maven

```bash
cd squad-os

# Compile + test
mvn clean test

# Run the starter demo
cd squad-starter
mvn exec:java
```

---

## Option C — IntelliJ IDEA (recommended)

1. Open IntelliJ → **File → Open** → select the `squad-os` folder
2. IntelliJ detects the multi-module Maven project automatically
3. Right-click `SquadOsPhase1Tests` → **Run**
4. Right-click `Main` in squad-starter → **Run**

> **Tip for IntelliJ:** If it shows "Cannot run — no main method", right-click  
> `squad-core/src/test/java` → **Mark Directory As → Test Sources Root**

---

## Option D — VS Code

1. Install the **Extension Pack for Java** (Microsoft)
2. Open the `squad-os` folder
3. VS Code auto-detects Maven modules
4. Click **Run** above `main()` in `Main.java` or `SquadOsPhase1Tests.java`

---

## Manual javac commands (if run.sh won't execute)

```bash
cd squad-os

# 1. Create output dir
mkdir -p out/core

# 2. Collect all source files
find squad-core/src/main/java -name "*.java" > sources.txt

# 3. Compile
javac --release 21 -d out/core @sources.txt

# 4. Compile tests
find squad-core/src/test/java -name "*.java" > test_sources.txt
javac --release 21 -cp out/core -d out/core @test_sources.txt

# 5. Run tests
java --release 21 -cp out/core io.squados.tests.SquadOsPhase1Tests

# 6. Compile and run the demo
mkdir -p out/starter
cp squad-starter/src/main/resources/squad.yml out/starter/
find squad-starter/src/main/java -name "*.java" > starter_sources.txt
javac --release 21 -cp out/core -d out/starter @starter_sources.txt
java --release 21 -cp "out/core:out/starter" com.example.Main

# On Windows replace : with ; in classpath:
# java --release 21 -cp "out/core;out/starter" com.example.Main
```

---

## Project structure

```
squad-os/
├── pom.xml                          Parent POM
├── run.sh                           Build + test runner (no Maven needed)
├── squad-core/                      The framework
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/squados/
│       │   ├── annotation/          @Agent, @SquadApplication, @PostConstruct, AgentRole
│       │   ├── llm/                 LlmPort, LlmOptions, LlmResponse, MockLlmPort
│       │   ├── config/              SquadConfig, SquadConfigParser
│       │   ├── context/             SquadContext, AgentWrapper, AgentRegistry, AgentScanner
│       │   ├── agent/               TaskContext, AgentResponse
│       │   └── exception/           Typed exceptions with actionable messages
│       └── test/java/io/squados/
│           └── tests/               SquadOsPhase1Tests (17 tests, no JUnit)
└── squad-starter/                   Hello Squad — what a developer writes
    ├── pom.xml
    └── src/main/
        ├── java/com/example/
        │   ├── OracleAgent.java     @Agent(role=STRATEGIST) — 15 lines
        │   └── Main.java            @SquadApplication entry point
        └── resources/
            └── squad.yml            8 lines of config
```

---

## What this does NOT do (yet)

- **No real LLM calls** — Phase 1 uses `MockLlmPort`. Wire `SpringAiLlmAdapter` in Phase 2.
- **No multi-agent messaging** — `@OnMessage` bus ships in Phase 3.
- **No memory** — `@Memory` with four-tier storage ships in Phase 2.
- **No circuit breaker** — `AgentCircuitBreaker` ships in Phase 3.

---

## Next: Phase 2 — Memory layer

Phase 2 adds `@Memory` annotation, four-tier storage (Working/Semantic/Procedural/Episodic),
`pgvector` integration via `spring-ai-pgvector-store`, and the Spring AI `LlmPort` adapter.

```
./run.sh    →  17/17 tests pass  →  cut v0.0.1 tag  →  start Phase 2
```
