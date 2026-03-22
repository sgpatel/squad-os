package io.squados.tests;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;
import io.squados.config.SquadConfigParser;
import io.squados.context.AgentRegistry;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.squados.execution.ParallelExecutor;
import io.squados.execution.SquadResult;
import io.squados.execution.SquadTask;
import io.squados.llm.MockLlmPort;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 7 — v1.1 Parallel Agents test suite.
 *
 * P01 — SquadTask.of() stores input correctly
 * P02 — SquadTask.assignTo() stores roles
 * P03 — SquadTask.withLabel() stores label
 * P04 — SquadTask.withTimeout() stores timeout
 * P05 — SquadTask with no roles uses all registered agents
 * P06 — ParallelExecutor runs all assigned roles
 * P07 — ParallelExecutor results keyed by role
 * P08 — ParallelExecutor all agents receive same input
 * P09 — SquadResult.allSucceeded() true when all pass
 * P10 — SquadResult.wallClockMs() is less than sum of latencies
 * P11 — SquadResult.speedupRatio() &gt; 1 for parallel execution
 * P12 — SquadResult.totalTokens() sums all agents
 * P13 — SquadResult.get() returns correct agent response
 * P14 — SquadContext.execute(SquadTask) works end-to-end
 * P15 — ParallelExecutor skips missing roles gracefully
 * P16 — 3 agents run truly in parallel (wall-clock &lt; sum)
 * P17 — SquadTask assigns all registered if no roles specified
 */
public class SquadOsPhase7Tests {

    @Agent(role = AgentRole.ANALYST,    name = "TestAnalyst")
    static class TestAnalystAgent {}

    @Agent(role = AgentRole.CRITIC,     name = "TestCritic")
    static class TestCriticAgent {}

    @Agent(role = AgentRole.EXECUTOR,   name = "TestExecutor")
    static class TestExecutorAgent {}

    @Agent(role = AgentRole.STRATEGIST, name = "TestStrategist")
    static class TestStrategistAgent {
        static AtomicInteger initCount = new AtomicInteger(0);
        @PostConstruct public void init() { initCount.incrementAndGet(); }
    }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase7Tests();
        String[] tests = {
            "P01_squadTaskStoresInput",
            "P02_squadTaskAssignToStoresRoles",
            "P03_squadTaskWithLabel",
            "P04_squadTaskWithTimeout",
            "P05_squadTaskNoRolesAllAgents",
            "P06_parallelExecutorRunsAllRoles",
            "P07_parallelExecutorResultsKeyedByRole",
            "P08_allAgentsReceiveSameInput",
            "P09_squadResultAllSucceeded",
            "P10_wallClockLessThanSumOfLatencies",
            "P11_speedupRatioAboveOne",
            "P12_totalTokensSumsAllAgents",
            "P13_squadResultGetByRole",
            "P14_squadContextExecuteEndToEnd",
            "P15_missingRoleSkippedGracefully",
            "P16_threeAgentsTrulyParallel",
            "P17_noRolesUsesAllRegistered",
        };

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 7 — v1.1 Parallel Agents     ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name);
                passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    → %s: %s%n", name,
                    c.getClass().getSimpleName(), c.getMessage());
                failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    → %s%n", name, e.getMessage());
                failed++;
            }
        }

        System.out.printf("%n  Results: %d passed, %d failed out of %d tests%n",
            passed, failed, passed + failed);
        if (failed > 0) {
            System.out.println("\n  PHASE 7 GATE: FAILED\n");
            System.exit(1);
        } else {
            System.out.println("\n  PHASE 7 GATE: ALL TESTS PASSED ✓");
            System.out.println("  v1.1.0 tag can be cut. Parallel agents operational.\n");
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void P01_squadTaskStoresInput() {
        SquadTask t = SquadTask.of("analyse this code");
        assertEquals("analyse this code", t.getInput(), "input stored");
    }

    void P02_squadTaskAssignToStoresRoles() {
        SquadTask t = SquadTask.of("task")
            .assignTo(AgentRole.ANALYST, AgentRole.CRITIC);
        assertEquals(2, t.getRoles().size(), "2 roles stored");
        assertTrue(t.getRoles().contains(AgentRole.ANALYST), "ANALYST present");
        assertTrue(t.getRoles().contains(AgentRole.CRITIC),  "CRITIC present");
        assertTrue(t.hasRoles(), "hasRoles() true");
    }

    void P03_squadTaskWithLabel() {
        SquadTask t = SquadTask.of("task").withLabel("Code Review");
        assertEquals("Code Review", t.getLabel(), "label stored");
    }

    void P04_squadTaskWithTimeout() {
        SquadTask t = SquadTask.of("task").withTimeout(30_000);
        assertEquals(30_000L, t.getTimeoutMs(), "timeout stored");
    }

    void P05_squadTaskNoRolesAllAgents() {
        SquadTask t = SquadTask.of("task");
        assertFalse(t.hasRoles(), "no roles — will use all registered");
    }

    void P06_parallelExecutorRunsAllRoles() {
        MockLlmPort mock = mock("parallel response");
        var registry     = registry3(mock);
        var executor     = new ParallelExecutor(registry);

        SquadTask task = SquadTask.of("test input")
            .assignTo(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR)
            .withLabel("Test Task");

        SquadResult result = executor.execute(task);

        assertEquals(3, result.roles().size(),  "3 roles in result");
        assertTrue(result.allSucceeded(),        "all succeeded");
    }

    void P07_parallelExecutorResultsKeyedByRole() {
        MockLlmPort mock = mock("response");
        var registry     = registry3(mock);
        var executor     = new ParallelExecutor(registry);

        SquadResult result = executor.execute(
            SquadTask.of("input").assignTo(AgentRole.ANALYST, AgentRole.CRITIC));

        assertNotNull(result.get(AgentRole.ANALYST), "ANALYST response present");
        assertNotNull(result.get(AgentRole.CRITIC),  "CRITIC response present");
        assertEquals(null, result.get(AgentRole.EXECUTOR), "EXECUTOR not assigned");
    }

    void P08_allAgentsReceiveSameInput() {
        MockLlmPort mock = mock("ok");
        var registry     = registry3(mock);
        var executor     = new ParallelExecutor(registry);

        executor.execute(
            SquadTask.of("the shared input")
                .assignTo(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR));

        // All 3 agents were called — each received the same user message
        assertEquals(3, mock.getCallCount(), "LLM called 3 times");
        mock.getCalls().forEach(call ->
            assertTrue(call.userMessage().contains("the shared input"),
                "each call contained the shared input")
        );
    }

    void P09_squadResultAllSucceeded() {
        MockLlmPort mock = mock("good response");
        var registry     = registry3(mock);
        var executor     = new ParallelExecutor(registry);
        var result       = executor.execute(
            SquadTask.of("input").assignTo(AgentRole.ANALYST, AgentRole.CRITIC));

        assertTrue(result.allSucceeded(), "allSucceeded when mock returns valid content");
        assertFalse(result.anyFailed(),   "anyFailed false");
        assertEquals(0, result.failures().size(), "no failures");
    }

    void P10_wallClockLessThanSumOfLatencies() throws Exception {
        // Use a slow mock — each call sleeps 200ms
        MockLlmPort slowMock = new MockLlmPort() {
            @Override
            public io.squados.llm.LlmResponse chat(String s, String u,
                    io.squados.llm.LlmOptions o) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                return new io.squados.llm.LlmResponse("slow response");
            }
        };

        var registry = registry3(slowMock);
        var executor = new ParallelExecutor(registry);
        var result   = executor.execute(
            SquadTask.of("input").assignTo(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR));

        long wall = result.wallClockMs();
        long sum  = result.sumOfLatenciesMs();

        // 3 × 200ms sequential = 600ms. Parallel should be ~200ms.
        // Wall-clock should be significantly less than sum.
        assertTrue(wall < sum,
            "wall-clock (" + wall + "ms) < sum of latencies (" + sum + "ms)");
        assertTrue(wall < 500,
            "parallel: 3×200ms agents complete in under 500ms, got " + wall + "ms");
    }

    void P11_speedupRatioAboveOne() throws Exception {
        MockLlmPort slowMock = new MockLlmPort() {
            @Override
            public io.squados.llm.LlmResponse chat(String s, String u,
                    io.squados.llm.LlmOptions o) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                return new io.squados.llm.LlmResponse("response");
            }
        };
        var result = new ParallelExecutor(registry3(slowMock)).execute(
            SquadTask.of("input").assignTo(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR));
        assertTrue(result.speedupRatio() > 1.0,
            "speedup ratio > 1.0 for parallel execution, got: " + result.speedupRatio());
    }

    void P12_totalTokensSumsAllAgents() {
        MockLlmPort mock = mock("hello");
        var result = new ParallelExecutor(registry3(mock)).execute(
            SquadTask.of("input").assignTo(AgentRole.ANALYST, AgentRole.CRITIC));
        // MockLlmPort sets promptTokens = message.length, completionTokens = content.length
        assertTrue(result.totalTokens() > 0, "total tokens > 0");
    }

    void P13_squadResultGetByRole() {
        // Both agents receive the SAME userMessage in parallel execution.
        // Use a default response and verify each role has a non-null response.
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("Task handled.");
        var result = new ParallelExecutor(registry3(mock)).execute(
            SquadTask.of("review this code")
                .assignTo(AgentRole.ANALYST, AgentRole.CRITIC));

        // Both roles must have a response
        assertNotNull(result.get(AgentRole.ANALYST), "ANALYST response present");
        assertNotNull(result.get(AgentRole.CRITIC),  "CRITIC response present");
        assertTrue(result.get(AgentRole.ANALYST).hasContent(), "ANALYST has content");
        assertTrue(result.get(AgentRole.CRITIC).hasContent(),  "CRITIC has content");

        // Each role's response is independent
        assertFalse(result.get(AgentRole.ANALYST) == result.get(AgentRole.CRITIC),
            "ANALYST and CRITIC are different response objects");
        assertEquals(AgentRole.ANALYST, result.get(AgentRole.ANALYST).role(), "role is ANALYST");
        assertEquals(AgentRole.CRITIC,  result.get(AgentRole.CRITIC).role(),  "role is CRITIC");
    }

    void P14_squadContextExecuteEndToEnd() {
        MockLlmPort mock = mock("context execute response");
        String yaml =
            "squad:\n  name: parallel-test\n  llm:\n    provider: ollama\n" +
            "    model: llama3.2\n  agents:\n" +
            "    - class: " + TestAnalystAgent.class.getName()   + "\n" +
            "    - class: " + TestCriticAgent.class.getName()    + "\n" +
            "    - class: " + TestExecutorAgent.class.getName()  + "\n";
        var config  = SquadConfigParser.parse(yaml);
        var context = new SquadContext(config, mock);
        context.boot();

        SquadResult result = context.execute(
            SquadTask.of("end to end test")
                .assignTo(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR)
                .withLabel("E2E Test")
        );

        assertTrue(result.allSucceeded(), "all 3 agents succeeded");
        assertEquals(3, result.roles().size(), "3 results");
        assertEquals("E2E Test", result.getTaskLabel(), "label preserved");
    }

    void P15_missingRoleSkippedGracefully() {
        MockLlmPort mock = mock("ok");
        var registry     = registry3(mock); // has ANALYST, CRITIC, EXECUTOR only
        var executor     = new ParallelExecutor(registry);

        // STRATEGIST is NOT in registry — should be skipped, not throw
        SquadResult result = executor.execute(
            SquadTask.of("input").assignTo(AgentRole.ANALYST, AgentRole.STRATEGIST));

        assertEquals(1, result.roles().size(),
            "Only ANALYST present — STRATEGIST skipped gracefully");
        assertNotNull(result.get(AgentRole.ANALYST), "ANALYST result present");
    }

    void P16_threeAgentsTrulyParallel() throws Exception {
        // Each agent sleeps 300ms. Sequential would be 900ms. Parallel should be ~300ms.
        MockLlmPort slowMock = new MockLlmPort() {
            @Override
            public io.squados.llm.LlmResponse chat(String s, String u,
                    io.squados.llm.LlmOptions o) {
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                return new io.squados.llm.LlmResponse("done");
            }
        };
        var result = new ParallelExecutor(registry3(slowMock)).execute(
            SquadTask.of("parallel test")
                .assignTo(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR)
                .withTimeout(5000));

        long wall = result.wallClockMs();
        assertTrue(wall < 700,
            "3 agents×300ms in parallel should finish under 700ms, got " + wall + "ms");
        System.out.printf("    (3 agents×300ms: wall=%dms, speedup=%.1fx)%n",
            wall, result.speedupRatio());
    }

    void P17_noRolesUsesAllRegistered() {
        MockLlmPort mock = mock("response");
        var registry     = registry3(mock);
        var executor     = new ParallelExecutor(registry);

        // No .assignTo() — should use all 3 registered agents
        SquadResult result = executor.execute(SquadTask.of("broadcast task"));
        assertEquals(3, result.roles().size(), "all 3 registered agents used");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private MockLlmPort mock(String response) {
        MockLlmPort m = new MockLlmPort();
        m.setDefaultResponse(response);
        return m;
    }

    private AgentRegistry registry3(MockLlmPort mock) {
        String yaml =
            "squad:\n  name: t\n  llm:\n    provider: ollama\n    model: llama3.2\n" +
            "  agents:\n" +
            "    - class: " + TestAnalystAgent.class.getName()  + "\n" +
            "    - class: " + TestCriticAgent.class.getName()   + "\n" +
            "    - class: " + TestExecutorAgent.class.getName() + "\n";
        var config   = SquadConfigParser.parse(yaml);
        var registry = new AgentRegistry();
        for (var cls : new Class[]{TestAnalystAgent.class, TestCriticAgent.class, TestExecutorAgent.class}) {
            var wrapper = new AgentWrapper(cls, null, config, mock);
            wrapper.setBreaker(new io.squados.health.AgentCircuitBreaker(
                new io.squados.bus.AgentMessageBus()));
            registry.register(wrapper);
        }
        return registry;
    }

    private static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertEquals(long e, long a, String msg) {
        if (e == a) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertNotNull(Object a, String msg) {
        if (a != null) return;
        throw new AssertionError(msg + " — expected non-null");
    }
    private static void assertTrue(boolean c, String msg) {
        if (c) return;
        throw new AssertionError(msg + " — expected true");
    }
    private static void assertFalse(boolean c, String msg) {
        if (!c) return;
        throw new AssertionError(msg + " — expected false");
    }
}
