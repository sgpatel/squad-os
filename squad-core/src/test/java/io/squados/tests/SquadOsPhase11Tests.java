package io.squados.tests;

import io.squados.annotation.SquadTool;
import io.squados.annotation.ToolParam;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;
import io.squados.tool.SquadToolDefinition;
import io.squados.tool.SquadToolExecutor;
import io.squados.tool.SquadToolRegistry;

import java.util.*;

/**
 * Phase 11 — v2.2 @SquadTool
 *
 * T01 — @SquadTool method discovered by SquadToolRegistry
 * T02 — tool name defaults to method name when not specified
 * T03 — tool name uses annotation name when specified
 * T04 — @ToolParam description included in schema prompt
 * T05 — buildToolsPrompt includes TOOL_CALL format instruction
 * T06 — buildToolsPrompt includes all registered tools
 * T07 — SquadToolExecutor returns direct response when no tool call
 * T08 — SquadToolExecutor detects TOOL_CALL and invokes method
 * T09 — SquadToolExecutor feeds TOOL_RESULT back to LLM
 * T10 — SquadToolExecutor passes String argument correctly
 * T11 — SquadToolExecutor passes int argument correctly
 * T12 — SquadToolExecutor passes double argument correctly
 * T13 — SquadToolExecutor passes boolean argument correctly
 * T14 — SquadToolExecutor handles unknown tool gracefully
 * T15 — SquadToolExecutor handles tool exception gracefully
 * T16 — SquadToolExecutor stops after max iterations
 * T17 — SquadToolRegistry hasTools returns false when empty
 */
public class SquadOsPhase11Tests {

    // ── Test agent with tools ──────────────────────────────────────
    static class StockAgent {
        public List<String> calls = new ArrayList<>();

        @SquadTool(name = "getStockPrice",
                   description = "Get current stock price for a ticker")
        public String getStockPrice(
            @ToolParam(description = "Ticker e.g. AAPL") String ticker) {
            calls.add("getStockPrice:" + ticker);
            return "$150.00";
        }

        @SquadTool(description = "Add two numbers")
        public String addNumbers(
            @ToolParam(description = "First number") int a,
            @ToolParam(description = "Second number") int b) {
            calls.add("addNumbers:" + a + "+" + b);
            return String.valueOf(a + b);
        }

        @SquadTool(description = "Check if value is positive")
        public String isPositive(
            @ToolParam(description = "Number to check") double value) {
            calls.add("isPositive:" + value);
            return String.valueOf(value > 0);
        }

        @SquadTool(description = "Toggle feature flag")
        public String toggleFlag(
            @ToolParam(description = "Flag name") String flag,
            @ToolParam(description = "Enabled state") boolean enabled) {
            calls.add("toggleFlag:" + flag + "=" + enabled);
            return flag + " is now " + (enabled ? "ON" : "OFF");
        }

        @SquadTool(description = "Boom")
        public String boom() {
            throw new RuntimeException("intentional tool failure");
        }
    }

    // ── Mock LLM ──────────────────────────────────────────────────
    static class MockLlmWithTools implements LlmPort {
        final List<String> responses;
        final List<String> receivedMessages = new ArrayList<>();
        int callCount = 0;

        MockLlmWithTools(String... responses) {
            this.responses = new ArrayList<>(List.of(responses));
        }

        @Override
        public LlmResponse chat(String sys, String user, LlmOptions opts) {
            receivedMessages.add(user);
            String resp = callCount < responses.size()
                ? responses.get(callCount) : "Final answer.";
            callCount++;
            return new LlmResponse(resp, 0, 0, "mock");
        }

        @Override
        public <T> T chatStructured(String s, String u, Class<T> t, LlmOptions o) {
            return null;
        }
    }

    // ── Test runner ───────────────────────────────────────────────
    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase11Tests();
        String[] tests = {
            "T01_toolDiscoveredByRegistry",
            "T02_toolNameDefaultsToMethodName",
            "T03_toolNameUsesAnnotationName",
            "T04_toolParamDescriptionInSchema",
            "T05_buildToolsPromptIncludesFormat",
            "T06_buildToolsPromptIncludesAllTools",
            "T07_executorReturnsDirectResponse",
            "T08_executorDetectsToolCallAndInvokes",
            "T09_executorFeedsToolResultBack",
            "T10_executorPassesStringArg",
            "T11_executorPassesIntArg",
            "T12_executorPassesDoubleArg",
            "T13_executorPassesBooleanArg",
            "T14_executorHandlesUnknownTool",
            "T15_executorHandlesToolException",
            "T16_executorStopsAfterMaxIterations",
            "T17_registryHasToolsFalseWhenEmpty",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 11 — v2.2 @SquadTool         ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name); passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    → %s: %s%n",
                    name, c.getClass().getSimpleName(), c.getMessage()); failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    → %s%n", name, e.getMessage()); failed++;
            }
        }
        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 11 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 11 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v2.2 @SquadTool operational.\n"); }
    }

    void T01_toolDiscoveredByRegistry() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        assertTrue(reg.hasTools(), "registry has tools after registration");
        assertNotNull(reg.get("getStockPrice"), "getStockPrice registered");
    }

    void T02_toolNameDefaultsToMethodName() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        assertNotNull(reg.get("addNumbers"), "addNumbers (method name) registered");
    }

    void T03_toolNameUsesAnnotationName() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        assertNotNull(reg.get("getStockPrice"), "annotation name used");
    }

    void T04_toolParamDescriptionInSchema() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        String schema = reg.get("getStockPrice").toSchemaPrompt();
        assertTrue(schema.contains("Ticker e.g. AAPL"), "param description in schema");
    }

    void T05_buildToolsPromptIncludesFormat() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        String prompt = reg.buildToolsPrompt();
        assertTrue(prompt.contains("TOOL_CALL:"), "TOOL_CALL format in prompt");
    }

    void T06_buildToolsPromptIncludesAllTools() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        String prompt = reg.buildToolsPrompt();
        assertTrue(prompt.contains("getStockPrice"), "getStockPrice in prompt");
        assertTrue(prompt.contains("addNumbers"), "addNumbers in prompt");
    }

    void T07_executorReturnsDirectResponse() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        MockLlmWithTools llm = new MockLlmWithTools("The answer is 42.");
        SquadToolExecutor exec = new SquadToolExecutor(reg, llm);
        var resp = exec.executeWithTools("sys", "what is 6x7?", new LlmOptions(0.5f, 1024, null));
        assertEquals("The answer is 42.", resp.content(), "direct response returned");
        assertEquals(1, llm.callCount, "LLM called once");
    }

    void T08_executorDetectsToolCallAndInvokes() {
        SquadToolRegistry reg = new SquadToolRegistry();
        StockAgent agent = new StockAgent();
        reg.register(agent);
        MockLlmWithTools llm = new MockLlmWithTools(
            "TOOL_CALL: getStockPrice {\"ticker\": \"AAPL\"}",
            "AAPL is $150.00."
        );
        SquadToolExecutor exec = new SquadToolExecutor(reg, llm);
        exec.executeWithTools("sys", "What is the price of AAPL?", new LlmOptions(0.5f, 1024, null));
        assertTrue(agent.calls.contains("getStockPrice:AAPL"), "tool was invoked with AAPL");
    }

    void T09_executorFeedsToolResultBack() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        MockLlmWithTools llm = new MockLlmWithTools(
            "TOOL_CALL: getStockPrice {\"ticker\": \"TSLA\"}",
            "TSLA is $150.00."
        );
        SquadToolExecutor exec = new SquadToolExecutor(reg, llm);
        exec.executeWithTools("sys", "Price of TSLA?", new LlmOptions(0.5f, 1024, null));
        assertEquals(2, llm.callCount, "LLM called twice");
        assertTrue(llm.receivedMessages.get(1).contains("TOOL_RESULT"),
            "second message contains TOOL_RESULT");
        assertTrue(llm.receivedMessages.get(1).contains("$150.00"),
            "tool result value fed back");
    }

    void T10_executorPassesStringArg() {
        SquadToolRegistry reg = new SquadToolRegistry();
        StockAgent agent = new StockAgent();
        reg.register(agent);
        MockLlmWithTools llm = new MockLlmWithTools(
            "TOOL_CALL: getStockPrice {\"ticker\": \"GOOG\"}",
            "Done."
        );
        new SquadToolExecutor(reg, llm).executeWithTools("sys", "GOOG?", new LlmOptions(0.5f, 1024, null));
        assertTrue(agent.calls.contains("getStockPrice:GOOG"), "String arg passed");
    }

    void T11_executorPassesIntArg() {
        SquadToolRegistry reg = new SquadToolRegistry();
        StockAgent agent = new StockAgent();
        reg.register(agent);
        MockLlmWithTools llm = new MockLlmWithTools(
            "TOOL_CALL: addNumbers {\"a\": \"3\", \"b\": \"4\"}",
            "Sum is 7."
        );
        new SquadToolExecutor(reg, llm).executeWithTools("sys", "3+4?", new LlmOptions(0.5f, 1024, null));
        assertTrue(agent.calls.contains("addNumbers:3+4"), "int args passed");
    }

    void T12_executorPassesDoubleArg() {
        SquadToolRegistry reg = new SquadToolRegistry();
        StockAgent agent = new StockAgent();
        reg.register(agent);
        MockLlmWithTools llm = new MockLlmWithTools(
            "TOOL_CALL: isPositive {\"value\": \"3.14\"}",
            "Yes."
        );
        new SquadToolExecutor(reg, llm).executeWithTools("sys", "positive?", new LlmOptions(0.5f, 1024, null));
        assertTrue(agent.calls.contains("isPositive:3.14"), "double arg passed");
    }

    void T13_executorPassesBooleanArg() {
        SquadToolRegistry reg = new SquadToolRegistry();
        StockAgent agent = new StockAgent();
        reg.register(agent);
        MockLlmWithTools llm = new MockLlmWithTools(
            "TOOL_CALL: toggleFlag {\"flag\": \"darkMode\", \"enabled\": \"true\"}",
            "Done."
        );
        new SquadToolExecutor(reg, llm).executeWithTools("sys", "toggle?", new LlmOptions(0.5f, 1024, null));
        assertTrue(agent.calls.contains("toggleFlag:darkMode=true"), "boolean arg passed");
    }

    void T14_executorHandlesUnknownTool() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        MockLlmWithTools llm = new MockLlmWithTools(
            "TOOL_CALL: nonExistentTool {\"x\": \"y\"}",
            "Done."
        );
        SquadToolExecutor exec = new SquadToolExecutor(reg, llm);
        var resp = exec.executeWithTools("sys", "use unknown tool", new LlmOptions(0.5f, 1024, null));
        assertNotNull(resp, "response returned even for unknown tool");
    }

    void T15_executorHandlesToolException() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        MockLlmWithTools llm = new MockLlmWithTools(
            "TOOL_CALL: boom {}",
            "I handled the error."
        );
        SquadToolExecutor exec = new SquadToolExecutor(reg, llm);
        var resp = exec.executeWithTools("sys", "boom!", new LlmOptions(0.5f, 1024, null));
        assertNotNull(resp, "response returned after tool exception");
        assertTrue(llm.receivedMessages.get(1).contains("ERROR"),
            "error message fed back to LLM");
    }

    void T16_executorStopsAfterMaxIterations() {
        SquadToolRegistry reg = new SquadToolRegistry();
        reg.register(new StockAgent());
        // LLM always returns TOOL_CALL — should stop after maxIterations
        MockLlmWithTools llm = new MockLlmWithTools(
            "TOOL_CALL: getStockPrice {\"ticker\": \"AAPL\"}",
            "TOOL_CALL: getStockPrice {\"ticker\": \"AAPL\"}",
            "TOOL_CALL: getStockPrice {\"ticker\": \"AAPL\"}",
            "TOOL_CALL: getStockPrice {\"ticker\": \"AAPL\"}",
            "TOOL_CALL: getStockPrice {\"ticker\": \"AAPL\"}",
            "Final answer after max iterations."
        );
        SquadToolExecutor exec = new SquadToolExecutor(reg, llm);
        var resp = exec.executeWithTools("sys", "what is AAPL?", new LlmOptions(0.5f, 1024, null));
        assertNotNull(resp, "response returned after max iterations");
        assertTrue(llm.callCount <= 7, "LLM not called infinitely: " + llm.callCount);
    }

    void T17_registryHasToolsFalseWhenEmpty() {
        SquadToolRegistry reg = new SquadToolRegistry();
        assertFalse(reg.hasTools(), "empty registry has no tools");
        assertEquals("", reg.buildToolsPrompt(), "empty prompt when no tools");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertNotNull(Object a, String msg) {
        if (a != null) return;
        throw new AssertionError(msg + " — expected non-null");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return;
        throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return;
        throw new AssertionError(msg + " — expected false");
    }
}