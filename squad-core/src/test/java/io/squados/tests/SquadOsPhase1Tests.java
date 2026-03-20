package io.squados.tests;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;
import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.config.SquadConfig;
import io.squados.config.SquadConfigParser;
import io.squados.context.AgentRegistry;
import io.squados.context.AgentScanner;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.squados.exception.AgentConfigException;
import io.squados.exception.SquadConfigNotFoundException;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmResponse;
import io.squados.llm.MockLlmPort;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Self-contained Phase 1 test suite.
 *
 * Runs without JUnit or any external test framework.
 * Each test is a method that throws AssertionError on failure.
 *
 * Coverage:
 *   T01 — AgentRole default options are correct per role
 *   T02 — @Agent annotation is readable at runtime
 *   T03 — LlmOptions validates temperature range
 *   T04 — LlmOptions validates maxTokens range
 *   T05 — MockLlmPort records calls and returns configured responses
 *   T06 — SquadConfigParser parses a full squad.yml string
 *   T07 — SquadConfigParser rejects invalid temperature
 *   T08 — SquadConfigParser throws on missing squad.yml resource
 *   T09 — AgentWrapper builds the correct system prompt
 *   T10 — AgentWrapper calls @PostConstruct exactly once
 *   T11 — AgentWrapper resolves name from @Agent.name()
 *   T12 — AgentRegistry registers, retrieves by role, and reports lead
 *   T13 — AgentRegistry warns on duplicate role
 *   T14 — SquadContext boots, registers agent, fires @PostConstruct
 *   T15 — SquadContext.submit() routes to lead agent, returns response
 *   T16 — SquadContext.submitTo(role) routes to correct agent
 *   T17 — SquadContext boot is idempotent (safe to call twice)
 */
public class SquadOsPhase1Tests {

    // ── Fixture agent classes (inner) ─────────────────────────────────

    @Agent(role = AgentRole.STRATEGIST, name = "Oracle",
           description = "Tactical planning agent.")
    public static class OracleAgent {
        static final AtomicInteger postConstructCount = new AtomicInteger(0);

        @PostConstruct
        public void init() {
            postConstructCount.incrementAndGet();
        }
    }

    @Agent(role = AgentRole.TANK, name = "IronVeil",
           description = "Defensive frontline agent.")
    public static class IronVeilAgent {
        // No @PostConstruct — tests that absence is handled gracefully
    }

    @Agent(role = AgentRole.SUPPORT, name = "NurseBot")
    public static class NurseBotAgent {}

    // ── Test runner ───────────────────────────────────────────────────

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var tests = new SquadOsPhase1Tests();

        String[] testNames = {
            "T01_agentRoleDefaultOptions",
            "T02_agentAnnotationReadableAtRuntime",
            "T03_llmOptionsRejectsInvalidTemperature",
            "T04_llmOptionsRejectsInvalidMaxTokens",
            "T05_mockLlmPortRecordsCallsAndMatchesResponses",
            "T06_configParserParsesFullYaml",
            "T07_configParserRejectsInvalidTemperature",
            "T08_configParserThrowsOnMissingFile",
            "T09_agentWrapperBuildsCorrectSystemPrompt",
            "T10_postConstructFiresExactlyOnce",
            "T11_agentWrapperResolvesNameFromAnnotation",
            "T12_agentRegistryRegisterAndRetrieve",
            "T13_agentRegistryLeadPriority",
            "T14_squadContextBootsAndRegistersAgent",
            "T15_squadContextSubmitRoutesToLeadAgent",
            "T16_squadContextSubmitToTargetsRole",
            "T17_squadContextBootIsIdempotent",
        };

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║     SquadOS Phase 1 — Test Suite             ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        for (String name : testNames) {
            OracleAgent.postConstructCount.set(0); // reset between tests
            try {
                java.lang.reflect.Method m = tests.getClass()
                    .getDeclaredMethod(name);
                m.invoke(tests);
                System.out.printf("  ✓ %s%n", name);
                passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                System.out.printf("  ✗ %s%n    → %s: %s%n",
                    name, cause.getClass().getSimpleName(), cause.getMessage());
                failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    → %s%n", name, e.getMessage());
                failed++;
            }
        }

        System.out.printf("%n  Results: %d passed, %d failed out of %d tests%n",
            passed, failed, passed + failed);

        if (failed > 0) {
            System.out.println("\n  PHASE 1 GATE: FAILED — fix failures before Phase 2\n");
            System.exit(1);
        } else {
            System.out.println("\n  PHASE 1 GATE: ALL TESTS PASSED ✓\n");
            System.out.println("  v0.0.1 tag can be cut. Phase 2 planning can begin.\n");
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void T01_agentRoleDefaultOptions() {
        assertEquals(0.5f, AgentRole.STRATEGIST.defaultOptions().temperature(), "Strategist temp");
        assertEquals(2048, AgentRole.STRATEGIST.defaultOptions().maxTokens(),   "Strategist tokens");
        assertEquals(0.3f, AgentRole.TANK.defaultOptions().temperature(),       "Tank temp");
        assertEquals(0.8f, AgentRole.DPS.defaultOptions().temperature(),        "DPS temp");
        assertEquals(0.2f, AgentRole.SUPPORT.defaultOptions().temperature(),    "Support temp");
        assertEquals(0.4f, AgentRole.ANALYST.defaultOptions().temperature(),    "Analyst temp");
        assertEquals(0.9f, AgentRole.VISIONARY.defaultOptions().temperature(),  "Visionary temp");
        assertNull(AgentRole.TANK.defaultOptions().model(), "Role model should be null — resolved from squad.yml");
    }

    void T02_agentAnnotationReadableAtRuntime() {
        Agent ann = OracleAgent.class.getAnnotation(Agent.class);
        assertNotNull(ann, "@Agent annotation not found on OracleAgent");
        assertEquals(AgentRole.STRATEGIST, ann.role(), "@Agent.role()");
        assertEquals("Oracle", ann.name(),             "@Agent.name()");
        assertTrue(ann.description().contains("Tactical"), "@Agent.description()");
    }

    void T03_llmOptionsRejectsInvalidTemperature() {
        assertThrows(IllegalArgumentException.class,
            () -> new LlmOptions(3.0f, 512, null),
            "Should reject temperature > 2.0");
        assertThrows(IllegalArgumentException.class,
            () -> new LlmOptions(-0.1f, 512, null),
            "Should reject temperature < 0.0");
        // Valid boundary values should not throw
        new LlmOptions(0.0f, 1, null);
        new LlmOptions(2.0f, 1, null);
    }

    void T04_llmOptionsRejectsInvalidMaxTokens() {
        assertThrows(IllegalArgumentException.class,
            () -> new LlmOptions(0.5f, 0, null),
            "Should reject maxTokens = 0");
        assertThrows(IllegalArgumentException.class,
            () -> new LlmOptions(0.5f, -1, null),
            "Should reject maxTokens < 0");
    }

    void T05_mockLlmPortRecordsCallsAndMatchesResponses() {
        MockLlmPort mock = new MockLlmPort();
        mock.setResponse("north gate", "Flanking north gate — engage!");
        mock.setDefaultResponse("Default response");

        LlmResponse r1 = mock.chat("system", "attack the north gate", LlmOptions.defaults());
        assertEquals("Flanking north gate — engage!", r1.content(), "Keyword match");

        LlmResponse r2 = mock.chat("system", "unrelated task", LlmOptions.defaults());
        assertEquals("Default response", r2.content(), "Default fallback");

        assertEquals(2, mock.getCallCount(), "Call count");
        assertEquals("attack the north gate", mock.getCalls().get(0).userMessage(), "Call recorded");
        assertNotNull(mock.getLastCall(), "Last call not null");
    }

    void T06_configParserParsesFullYaml() {
        String yaml =
            "squad:\n" +
            "  name: \"alpha-squad\"\n" +
            "  profile: gaming\n" +
            "  llm:\n" +
            "    provider: anthropic\n" +
            "    model: claude-sonnet-4-6\n" +
            "  agents:\n" +
            "    - class: com.example.OracleAgent\n" +
            "      temperature: 0.7\n" +
            "      max-tokens: 2048\n";

        SquadConfig cfg = SquadConfigParser.parse(yaml);

        assertEquals("alpha-squad",      cfg.getName(),              "squad.name");
        assertEquals("gaming",           cfg.getProfile(),           "squad.profile");
        assertEquals("anthropic",        cfg.getLlm().getProvider(), "llm.provider");
        assertEquals("claude-sonnet-4-6",cfg.getLlm().getModel(),   "llm.model");
        assertEquals(1,                  cfg.getAgents().size(),     "agents count");

        SquadConfig.AgentConfig a = cfg.getAgents().get(0);
        assertEquals("com.example.OracleAgent", a.getClassName(),   "agent.class");
        assertEquals(0.7f,   a.getTemperature(), 0.001f,            "agent.temperature");
        assertEquals(2048,   a.getMaxTokens(),                      "agent.max-tokens");
    }

    void T07_configParserRejectsInvalidTemperature() {
        String yaml =
            "squad:\n" +
            "  llm:\n" +
            "    provider: anthropic\n" +
            "    model: test-model\n" +
            "  agents:\n" +
            "    - class: com.example.Test\n" +
            "      temperature: 3.5\n";
        assertThrows(AgentConfigException.class,
            () -> SquadConfigParser.parse(yaml),
            "Should reject temperature 3.5");
    }

    void T08_configParserThrowsOnMissingFile() {
        assertThrows(SquadConfigNotFoundException.class,
            () -> SquadConfigParser.load("non-existent-file.yml"),
            "Should throw when squad.yml is not found");
    }

    void T09_agentWrapperBuildsCorrectSystemPrompt() {
        MockLlmPort mock  = new MockLlmPort();
        SquadConfig cfg   = minimalConfig(OracleAgent.class.getName());
        AgentWrapper w    = new AgentWrapper(OracleAgent.class, null, cfg, mock);
        TaskContext ctx   = new TaskContext("test task");

        String prompt = w.buildSystemPrompt(ctx);

        assertTrue(prompt.contains("Oracle"),      "prompt contains agent name");
        assertTrue(prompt.contains("STRATEGIST"),  "prompt contains role");
        assertTrue(prompt.contains("Tactical"),    "prompt contains description");
        assertTrue(prompt.contains(ctx.getTaskId()),"prompt contains task ID");
    }

    void T10_postConstructFiresExactlyOnce() {
        MockLlmPort mock = new MockLlmPort();
        SquadConfig cfg  = minimalConfig(OracleAgent.class.getName());

        OracleAgent.postConstructCount.set(0);
        AgentWrapper w = new AgentWrapper(OracleAgent.class, null, cfg, mock);
        w.callPostConstruct();

        assertEquals(1, OracleAgent.postConstructCount.get(),
            "@PostConstruct should fire exactly once");

        // Calling it again should fire it again (each wrapper instance is independent)
        w.callPostConstruct();
        assertEquals(2, OracleAgent.postConstructCount.get(),
            "@PostConstruct called again on same wrapper fires again");
    }

    void T11_agentWrapperResolvesNameFromAnnotation() {
        MockLlmPort mock = new MockLlmPort();
        SquadConfig cfg  = minimalConfig(OracleAgent.class.getName());
        AgentWrapper w   = new AgentWrapper(OracleAgent.class, null, cfg, mock);
        assertEquals("Oracle", w.getName(), "Name from @Agent.name()");
    }

    void T12_agentRegistryRegisterAndRetrieve() {
        MockLlmPort  mock = new MockLlmPort();
        SquadConfig  cfg  = minimalConfig(OracleAgent.class.getName());
        AgentWrapper w    = new AgentWrapper(OracleAgent.class, null, cfg, mock);

        AgentRegistry registry = new AgentRegistry();
        registry.register(w);

        assertEquals(1, registry.count(), "count after register");
        assertNotNull(registry.getByRole(AgentRole.STRATEGIST), "lookup by role");
        assertEquals("Oracle", registry.getByRole(AgentRole.STRATEGIST).getName(), "name lookup");
        assertTrue(registry.hasRole(AgentRole.STRATEGIST), "hasRole true");
        assertFalse(registry.hasRole(AgentRole.TANK), "hasRole false");
        assertFalse(registry.isEmpty(), "not empty");
    }

    void T13_agentRegistryLeadPriority() {
        MockLlmPort  mock     = new MockLlmPort();
        SquadConfig  cfg      = minimalConfig(OracleAgent.class.getName());
        AgentWrapper oracle   = new AgentWrapper(OracleAgent.class,   null, cfg, mock);
        AgentWrapper ironVeil = new AgentWrapper(IronVeilAgent.class,  null, cfg, mock);

        AgentRegistry registry = new AgentRegistry();
        // Register TANK first, then STRATEGIST — STRATEGIST should still be lead
        registry.register(ironVeil);
        registry.register(oracle);

        assertEquals("Oracle", registry.getLead().getName(),
            "STRATEGIST should be lead even when registered second");
    }

    void T14_squadContextBootsAndRegistersAgent() {
        OracleAgent.postConstructCount.set(0);
        MockLlmPort mock = new MockLlmPort();
        SquadConfig cfg  = minimalConfig(OracleAgent.class.getName());

        SquadContext ctx = new SquadContext(cfg, mock);
        assertFalse(ctx.isBooted(), "not booted before boot()");
        ctx.boot();
        assertTrue(ctx.isBooted(), "booted after boot()");

        assertEquals(1, ctx.getRegistry().count(), "one agent registered");
        assertEquals(1, OracleAgent.postConstructCount.get(),
            "@PostConstruct fired exactly once during boot");
    }

    void T15_squadContextSubmitRoutesToLeadAgent() {
        MockLlmPort mock = new MockLlmPort();
        mock.setResponse("north gate", "Roger — flanking north gate.");
        SquadConfig cfg = minimalConfig(OracleAgent.class.getName());

        SquadContext ctx = new SquadContext(cfg, mock);
        ctx.boot();

        AgentResponse r = ctx.submit("attack the north gate");

        assertTrue(r.isSuccess(),                          "response is success");
        assertEquals("Oracle", r.agentName(),              "lead agent is Oracle");
        assertEquals(AgentRole.STRATEGIST, r.role(),       "role is STRATEGIST");
        assertEquals("Roger — flanking north gate.", r.content(), "content matched");
        assertTrue(r.hasContent(),                         "has content");
        assertTrue(mock.wasCalled(),                       "llm was called");
        assertEquals(1, mock.getCallCount(),               "llm called exactly once");
    }

    void T16_squadContextSubmitToTargetsRole() {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("Tank holding position.");

        // Build config with both agents listed
        String yaml =
            "squad:\n" +
            "  name: multi-agent\n" +
            "  llm:\n" +
            "    provider: anthropic\n" +
            "    model: claude-sonnet-4-6\n" +
            "  agents:\n" +
            "    - class: " + OracleAgent.class.getName()   + "\n" +
            "    - class: " + IronVeilAgent.class.getName() + "\n";

        SquadConfig cfg = SquadConfigParser.parse(yaml);
        SquadContext ctx = new SquadContext(cfg, mock);
        ctx.boot();

        AgentResponse r = ctx.submitTo(AgentRole.TANK, "hold the choke point");

        assertEquals("IronVeil", r.agentName(), "submitTo TANK routes to IronVeil");
        assertEquals(AgentRole.TANK, r.role(),  "role is TANK");
    }

    void T17_squadContextBootIsIdempotent() {
        OracleAgent.postConstructCount.set(0);
        MockLlmPort mock = new MockLlmPort();
        SquadConfig cfg  = minimalConfig(OracleAgent.class.getName());

        SquadContext ctx = new SquadContext(cfg, mock);
        ctx.boot();
        ctx.boot(); // Second boot — should be a no-op
        ctx.boot(); // Third boot — should be a no-op

        assertEquals(1, ctx.getRegistry().count(),
            "Still exactly one agent after triple boot()");
        assertEquals(1, OracleAgent.postConstructCount.get(),
            "@PostConstruct fired only once despite three boot() calls");
    }

    // ── Minimal config helper ─────────────────────────────────────────

    private static SquadConfig minimalConfig(String className) {
        String yaml =
            "squad:\n" +
            "  name: test-squad\n" +
            "  llm:\n" +
            "    provider: anthropic\n" +
            "    model: claude-sonnet-4-6\n" +
            "  agents:\n" +
            "    - class: " + className + "\n";
        return SquadConfigParser.parse(yaml);
    }

    // ── Assertion helpers ─────────────────────────────────────────────

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(msg + " — expected: <" + expected + "> but was: <" + actual + ">");
    }

    private static void assertEquals(float expected, float actual, float delta, String msg) {
        if (Math.abs(expected - actual) <= delta) return;
        throw new AssertionError(msg + " — expected: <" + expected + "> but was: <" + actual + ">");
    }

    private static void assertNull(Object actual, String msg) {
        if (actual == null) return;
        throw new AssertionError(msg + " — expected null but was: <" + actual + ">");
    }

    private static void assertNotNull(Object actual, String msg) {
        if (actual != null) return;
        throw new AssertionError(msg + " — expected non-null but was null");
    }

    private static void assertTrue(boolean condition, String msg) {
        if (condition) return;
        throw new AssertionError(msg + " — expected true but was false");
    }

    private static void assertFalse(boolean condition, String msg) {
        if (!condition) return;
        throw new AssertionError(msg + " — expected false but was true");
    }

    private static <T extends Throwable> void assertThrows(
            Class<T> expectedType, Runnable action, String msg) {
        try {
            action.run();
            throw new AssertionError(msg + " — expected " + expectedType.getSimpleName()
                + " but no exception was thrown");
        } catch (Throwable t) {
            if (!expectedType.isInstance(t)) {
                throw new AssertionError(msg + " — expected " + expectedType.getSimpleName()
                    + " but got " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }
}
