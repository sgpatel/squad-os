package io.squados.tests;

import io.squados.config.SquadConfig;
import io.squados.config.SquadConfigBridge;
import io.squados.config.SquadConfigParser;
import io.squados.context.SquadContext;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;
import io.squados.llm.MockLlmPort;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MockEmbeddingPort;

import java.util.Properties;

/**
 * Phase 6 test suite — Spring AI adapter wiring.
 *
 * Note: SpringAiLlmAdapter and LangChain4jEmbeddingAdapter are NOT tested here
 * with real API calls — that requires integration tests with live credentials.
 * These tests verify the wiring contracts, config bridge, and adapter interfaces.
 *
 * S01 — LlmPort interface contract: chat() returns non-null LlmResponse
 * S02 — LlmPort interface contract: chatStructured() type-safe return
 * S03 — MockLlmPort satisfies LlmPort — use as drop-in for SpringAiLlmAdapter
 * S04 — LlmOptions.withModel() creates new instance with model set
 * S05 — LlmOptions default provider model is null (resolved from squad.yml)
 * S06 — SquadConfigBridge translates anthropic provider correctly
 * S07 — SquadConfigBridge translates openai provider correctly
 * S08 — SquadConfigBridge translates ollama provider correctly
 * S09 — SquadConfigBridge unknown provider does not throw
 * S10 — SquadConfigBridge does not overwrite existing system properties
 * S11 — EmbeddingPort interface contract: embed() returns float[]
 * S12 — EmbeddingPort interface contract: dimensions() matches embed() length
 * S13 — MockEmbeddingPort satisfies EmbeddingPort — drop-in for LangChain4j
 * S14 — SquadContext boots with any LlmPort implementation
 * S15 — SquadContext.submit routes through LlmPort.chat
 * S16 — AgentRole defaults have null model (bridge injects at runtime)
 * S17 — Full wiring smoke test: config bridge -> squad context -> submit
 */
public class SquadOsPhase6Tests {

    @io.squados.annotation.Agent(
        role = io.squados.annotation.AgentRole.STRATEGIST,
        name = "TestOracle",
        description = "Test strategist agent for Phase 6 wiring tests."
    )
    static class TestOracleAgent {}

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase6Tests();
        String[] tests = {
            "S01_llmPortContractChatReturnsResponse",
            "S02_llmPortContractStructuredReturnsNull",
            "S03_mockLlmPortSatisfiesInterface",
            "S04_llmOptionsWithModel",
            "S05_llmOptionsDefaultModelNull",
            "S06_bridgeTranslatesAnthropic",
            "S07_bridgeTranslatesOpenAi",
            "S08_bridgeTranslatesOllama",
            "S09_bridgeUnknownProviderNoThrow",
            "S10_bridgeDoesNotOverwriteExisting",
            "S11_embeddingPortContractReturnsFloatArray",
            "S12_embeddingPortDimensionsMatchEmbed",
            "S13_mockEmbeddingPortSatisfiesInterface",
            "S14_squadContextBootsWithAnyLlmPort",
            "S15_squadContextSubmitRoutesToLlmPort",
            "S16_agentRoleDefaultModelNull",
            "S17_fullWiringSmokeTest",
        };

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 6 — Spring AI Wiring Tests   ║");
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
            System.out.println("\n  PHASE 6 GATE: FAILED\n");
            System.exit(1);
        } else {
            System.out.println("\n  PHASE 6 GATE: ALL TESTS PASSED ✓");
            System.out.println("  v1.0.0-RC tag can be cut.");
            System.out.println("  Set ANTHROPIC_API_KEY and run squad-starter for live demo.\n");
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void S01_llmPortContractChatReturnsResponse() {
        LlmPort port = makeMock("Paris is the capital of France.");
        LlmResponse r = port.chat("You are a geographer.", "Capital of France?",
                                   LlmOptions.defaults());
        assertNotNull(r,           "chat() returns non-null");
        assertNotNull(r.content(), "content is non-null");
        assertTrue(r.hasContent(), "hasContent() true");
    }

    void S02_llmPortContractStructuredReturnsNull() {
        // MockLlmPort returns null for chatStructured — same contract as
        // SpringAiLlmAdapter when the model can't parse the type
        LlmPort port = makeMock("");
        Object result = port.chatStructured("sys", "user", String.class,
                                             LlmOptions.defaults());
        // null is valid — callers must null-check structured responses
        assertTrue(result == null || result instanceof String,
            "chatStructured returns null or typed value");
    }

    void S03_mockLlmPortSatisfiesInterface() {
        // MockLlmPort must be a valid drop-in for SpringAiLlmAdapter
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("mocked response");
        assertTrue(mock instanceof LlmPort, "MockLlmPort implements LlmPort");
        LlmResponse r = mock.chat("sys", "user", LlmOptions.defaults());
        assertEquals("mocked response", r.content(), "mock returns configured response");
    }

    void S04_llmOptionsWithModel() {
        LlmOptions base  = new LlmOptions(0.5f, 1024, null);
        LlmOptions wired = base.withModel("claude-sonnet-4-6");
        assertEquals("claude-sonnet-4-6", wired.model(), "model set via withModel()");
        assertEquals(base.temperature(), wired.temperature(), 0.001f, "temperature unchanged");
        assertEquals(base.maxTokens(),   wired.maxTokens(),           "maxTokens unchanged");
        // Original immutable
        assertEquals(null, base.model(), "original base model still null");
    }

    void S05_llmOptionsDefaultModelNull() {
        // Model must be null in AgentRole defaults — resolved from squad.yml at runtime
        for (io.squados.annotation.AgentRole role : io.squados.annotation.AgentRole.values()) {
            assertEquals(null, role.defaultOptions().model(),
                role + ".defaultOptions().model() must be null — resolved from squad.yml");
        }
    }

    void S06_bridgeTranslatesAnthropic() {
        SquadConfig cfg = makeConfig("anthropic", "claude-sonnet-4-6");
        Properties p = SquadConfigBridge.translate(cfg);
        assertEquals("true",              p.getProperty("spring.ai.anthropic.chat.enabled"),       "anthropic enabled");
        assertEquals("claude-sonnet-4-6", p.getProperty("spring.ai.anthropic.chat.options.model"), "model set");
    }

    void S07_bridgeTranslatesOpenAi() {
        SquadConfig cfg = makeConfig("openai", "gpt-4o");
        Properties p = SquadConfigBridge.translate(cfg);
        assertEquals("true",  p.getProperty("spring.ai.openai.chat.enabled"),       "openai enabled");
        assertEquals("gpt-4o",p.getProperty("spring.ai.openai.chat.options.model"), "model set");
    }

    void S08_bridgeTranslatesOllama() {
        SquadConfig cfg = makeConfig("ollama", "llama3");
        Properties p = SquadConfigBridge.translate(cfg);
        assertEquals("true",   p.getProperty("spring.ai.ollama.chat.enabled"),       "ollama enabled");
        assertEquals("llama3", p.getProperty("spring.ai.ollama.chat.options.model"), "model set");
    }

    void S09_bridgeUnknownProviderNoThrow() {
        SquadConfig cfg = makeConfig("future-provider", "some-model");
        // Must not throw — unknown providers are logged but not fatal
        Properties p = SquadConfigBridge.translate(cfg);
        assertTrue(p.isEmpty(), "no properties for unknown provider");
    }

    void S10_bridgeDoesNotOverwriteExisting() {
        String key = "spring.ai.anthropic.chat.options.model";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "my-custom-model");
            SquadConfigBridge.applyToSystemProperties();
            // applyToSystemProperties skips keys already set
            assertEquals("my-custom-model", System.getProperty(key),
                "existing system property not overwritten");
        } finally {
            // Restore
            if (original == null) System.clearProperty(key);
            else System.setProperty(key, original);
        }
    }

    void S11_embeddingPortContractReturnsFloatArray() {
        EmbeddingPort port = new MockEmbeddingPort();
        float[] vec = port.embed("hello world");
        assertNotNull(vec, "embed() returns non-null");
        assertTrue(vec.length > 0, "embed() returns non-empty array");
    }

    void S12_embeddingPortDimensionsMatchEmbed() {
        EmbeddingPort port = new MockEmbeddingPort();
        float[] vec = port.embed("test text");
        assertEquals(port.dimensions(), vec.length,
            "embed() length matches dimensions()");
    }

    void S13_mockEmbeddingPortSatisfiesInterface() {
        MockEmbeddingPort mock = new MockEmbeddingPort();
        assertTrue(mock instanceof EmbeddingPort,
            "MockEmbeddingPort implements EmbeddingPort");
        // Two calls with same text must return same vector (stable)
        float[] v1 = mock.embed("same text");
        float[] v2 = mock.embed("same text");
        for (int i = 0; i < v1.length; i++) {
            assertEquals(v1[i], v2[i], 0.0001f, "stable at dim " + i);
        }
    }

    void S14_squadContextBootsWithAnyLlmPort() {
        // Any LlmPort implementation must work as a drop-in
        LlmPort[] ports = {
            makeMock("response from mock"),
            new LlmPort() {
                public LlmResponse chat(String s, String u, LlmOptions o) {
                    return new LlmResponse("inline impl");
                }
                public <T> T chatStructured(String s, String u, Class<T> t, LlmOptions o) {
                    return null;
                }
            }
        };
        for (LlmPort port : ports) {
            SquadConfig cfg = makeSquadConfig();
            SquadContext ctx = new SquadContext(cfg, port);
            ctx.boot();
            assertTrue(ctx.isBooted(), "context booted with " + port.getClass().getSimpleName());
            assertEquals(1, ctx.getRegistry().count(), "1 agent registered");
        }
    }

    void S15_squadContextSubmitRoutesToLlmPort() {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("Spring AI response");
        SquadContext ctx = new SquadContext(makeSquadConfig(), mock);
        ctx.boot();

        var response = ctx.submit("What is the capital of France?");

        assertTrue(mock.wasCalled(),          "LlmPort.chat() was called");
        assertEquals(1, mock.getCallCount(),  "called exactly once");
        assertTrue(response.hasContent(),     "response has content");
        assertEquals("Spring AI response", response.content(), "content from LlmPort");

        // System prompt must contain agent identity
        String sysPrompt = mock.getLastCall().systemPrompt();
        assertTrue(sysPrompt.contains("TestOracle"), "system prompt contains agent name");
        assertTrue(sysPrompt.contains("STRATEGIST"), "system prompt contains role");
    }

    void S16_agentRoleDefaultModelNull() {
        // Validated in S05 but explicit here for clarity as a wiring contract
        assertNull(io.squados.annotation.AgentRole.STRATEGIST.defaultOptions().model(),
            "STRATEGIST default model is null — must be resolved from squad.yml");
        assertNull(io.squados.annotation.AgentRole.TANK.defaultOptions().model(),
            "TANK default model is null");
    }

    void S17_fullWiringSmokeTest() {
        // Simulate the full chain: squad.yml -> bridge -> context -> submit
        String yaml =
            "squad:\n" +
            "  name: smoke-test-squad\n" +
            "  profile: work\n" +
            "  llm:\n" +
            "    provider: anthropic\n" +
            "    model: claude-sonnet-4-6\n" +
            "  agents:\n" +
            "    - class: " + TestOracleAgent.class.getName() + "\n";

        SquadConfig config = SquadConfigParser.parse(yaml);

        // Bridge should map anthropic provider
        Properties bridged = SquadConfigBridge.translate(config);
        assertEquals("true", bridged.getProperty("spring.ai.anthropic.chat.enabled"),
            "bridge sets anthropic enabled");

        // Context boots with MockLlmPort (Spring AI not on test classpath)
        MockLlmPort mock = new MockLlmPort();
        mock.setResponse("capital of france", "Paris is the capital of France.");
        SquadContext ctx = new SquadContext(config, mock);
        ctx.boot();

        var response = ctx.submit("What is the capital of france?");

        assertTrue(response.isSuccess(),   "response is success");
        assertTrue(response.hasContent(),  "response has content");
        assertEquals("Paris is the capital of France.", response.content(),
            "mock response delivered correctly end-to-end");

        // Model resolved from squad.yml
        assertEquals("claude-sonnet-4-6", config.getLlm().getModel(),
            "model from squad.yml");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private MockLlmPort makeMock(String response) {
        MockLlmPort m = new MockLlmPort();
        m.setDefaultResponse(response);
        return m;
    }

    private SquadConfig makeConfig(String provider, String model) {
        String yaml =
            "squad:\n" +
            "  llm:\n" +
            "    provider: " + provider + "\n" +
            "    model: " + model + "\n" +
            "  agents:\n" +
            "    - class: " + TestOracleAgent.class.getName() + "\n";
        return SquadConfigParser.parse(yaml);
    }

    private SquadConfig makeSquadConfig() {
        return makeConfig("anthropic", "claude-sonnet-4-6");
    }

    private static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertEquals(float e, float a, float d, String msg) {
        if (Math.abs(e-a) <= d) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertNull(Object a, String msg) {
        if (a == null) return;
        throw new AssertionError(msg + " — expected null but was <" + a + ">");
    }
    private static void assertNotNull(Object a, String msg) {
        if (a != null) return;
        throw new AssertionError(msg + " — expected non-null");
    }
    private static void assertTrue(boolean c, String msg) {
        if (c) return;
        throw new AssertionError(msg + " — expected true");
    }
}
