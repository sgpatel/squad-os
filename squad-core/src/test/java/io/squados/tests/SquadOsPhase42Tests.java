package io.squados.tests;

import io.squados.annotation.*;
import io.squados.llm.*;
import io.squados.tool.*;

import java.util.List;

/**
 * Phase 42 — @SquadTool tests.
 *
 * ST01  SquadToolRegistry discovers @SquadTool methods via register()
 * ST02  SquadToolDefinition.toSchemaPrompt() includes tool name and description
 * ST03  SquadToolRegistry.buildToolsPrompt() lists all registered tools
 * ST04  SquadToolExecutor returns LLM response when no tool call detected
 * ST05  SquadToolExecutor invokes Java method when LLM returns TOOL_CALL
 * ST06  SquadToolExecutor feeds TOOL_RESULT back to LLM for final answer
 * ST07  SquadToolExecutor returns error string for unknown tool name
 * ST08  @SquadTool annotation is readable on method with correct attributes
 * ST09  @ToolParam annotation is readable on method parameters
 * ST10  SquadToolRegistry.buildToolsPrompt() includes parameter descriptions
 */
public class SquadOsPhase42Tests {

    // ── Tool-bearing agent stub ────────────────────────────────────────

    @Agent(role = AgentRole.ANALYST, name = "StockAgent",
           description = "Analyses stock prices using real-time tool calls.")
    static class StockAgent {

        @SquadTool(name = "getStockPrice",
                   description = "Get the current stock price for a ticker symbol",
                   maxIterations = 3)
        public String getStockPrice(
            @ToolParam(description = "Stock ticker e.g. AAPL, TSLA") String ticker
        ) {
            return switch (ticker.toUpperCase()) {
                case "AAPL"  -> "182.50";
                case "TSLA"  -> "248.90";
                case "GOOGL" -> "175.30";
                default      -> "0.00";
            };
        }

        @SquadTool(name = "getMarketStatus",
                   description = "Check if the stock market is currently open")
        public String getMarketStatus() {
            return "OPEN";
        }

        @SquadTool(name = "boom",
                   description = "A tool that always throws an exception")
        public String boom() {
            throw new RuntimeException("intentional tool failure");
        }
    }

    // ── Runner ────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 42 — @SquadTool                               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "ST01_registryDiscoversToolMethods",
            "ST02_toolDefinitionSchemaPrompt",
            "ST03_registryBuildsToolsPrompt",
            "ST04_executorReturnsDirectLlmResponse",
            "ST05_executorInvokesToolMethod",
            "ST06_executorFeedsToolResultBack",
            "ST07_executorHandlesUnknownTool",
            "ST08_squadToolAnnotationReadable",
            "ST09_toolParamAnnotationReadable",
            "ST10_toolsPromptIncludesParameters",
        };
        var t = new SquadOsPhase42Tests();
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s%n         → %s%n", test, c.getMessage());
                failed++;
            }
        }
        System.out.println();
        System.out.printf("Phase 42 result: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void ST01_registryDiscoversToolMethods() {
        SquadToolRegistry registry = new SquadToolRegistry();
        registry.register(new StockAgent());

        assert registry.hasTools()               : "Registry should have tools";
        assert registry.get("getStockPrice") != null : "getStockPrice should be registered";
        assert registry.get("getMarketStatus") != null : "getMarketStatus should be registered";
        assert registry.getAll().size() == 3     : "Should have 3 tools (incl. boom)";
        System.out.printf("         → registered %d tools%n", registry.getAll().size());
    }

    void ST02_toolDefinitionSchemaPrompt() {
        SquadToolRegistry registry = new SquadToolRegistry();
        registry.register(new StockAgent());

        SquadToolDefinition def = registry.get("getStockPrice");
        assert def != null : "getStockPrice should be registered";

        String schema = def.toSchemaPrompt();
        assert schema.contains("getStockPrice")             : "Schema should include tool name";
        assert schema.contains("stock price")               : "Schema should include description";
        assert schema.contains("TOOL_CALL: getStockPrice")  : "Schema should include call format";
    }

    void ST03_registryBuildsToolsPrompt() {
        SquadToolRegistry registry = new SquadToolRegistry();
        registry.register(new StockAgent());

        String prompt = registry.buildToolsPrompt();
        assert prompt.contains("AVAILABLE TOOLS")   : "Should have tools section header";
        assert prompt.contains("getStockPrice")      : "Should list getStockPrice";
        assert prompt.contains("getMarketStatus")    : "Should list getMarketStatus";
        assert prompt.contains("TOOL_CALL:")         : "Should explain call format";
    }

    void ST04_executorReturnsDirectLlmResponse() {
        SquadToolRegistry registry = new SquadToolRegistry();
        registry.register(new StockAgent());

        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("The market looks bullish today based on my analysis.");

        SquadToolExecutor executor = new SquadToolExecutor(registry, llm);
        LlmResponse response = executor.executeWithTools(
            "You are a stock analyst.", "What is the market doing?",
            new LlmOptions(0.5f, 512, null));

        assert response != null                          : "Response should not be null";
        assert response.content().contains("bullish")    : "Should return direct LLM response";
    }

    void ST05_executorInvokesToolMethod() {
        SquadToolRegistry registry = new SquadToolRegistry();
        registry.register(new StockAgent());

        // First LLM call → tool call; second call (after TOOL_RESULT) → final answer
        MockLlmPort llm = new MockLlmPort();
        llm.setResponse("What is the price",
            "TOOL_CALL: getStockPrice {\"ticker\": \"AAPL\"}");
        llm.setResponse("TOOL_RESULT",
            "The current price of AAPL is $182.50 per share.");

        SquadToolExecutor executor = new SquadToolExecutor(registry, llm);
        LlmResponse response = executor.executeWithTools(
            "Stock analyst agent.", "What is the price of AAPL?",
            new LlmOptions(0.3f, 512, null));

        assert response != null                          : "Response should not be null";
        assert response.content().contains("182")        : "Final response should include tool result";
        System.out.printf("         → final response: %s%n",
            response.content().substring(0, Math.min(60, response.content().length())));
    }

    void ST06_executorFeedsToolResultBack() {
        SquadToolRegistry registry = new SquadToolRegistry();
        registry.register(new StockAgent());

        MockLlmPort llm = new MockLlmPort();
        // First call: return tool call for market status
        llm.setResponse("market open",
            "Let me check. TOOL_CALL: getMarketStatus {}");
        // Second call (contains TOOL_RESULT): return final
        llm.setResponse("TOOL_RESULT",
            "The market is currently OPEN. Good time to trade!");

        SquadToolExecutor executor = new SquadToolExecutor(registry, llm);
        LlmResponse response = executor.executeWithTools(
            "Stock agent.", "Is the market open right now?",
            new LlmOptions(0.3f, 512, null));

        assert response.content().contains("OPEN") : "Response should reflect tool result";
    }

    void ST07_executorHandlesUnknownTool() {
        SquadToolRegistry registry = new SquadToolRegistry();
        registry.register(new StockAgent());

        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("TOOL_CALL: nonExistentTool {\"arg\": \"value\"}");

        SquadToolExecutor executor = new SquadToolExecutor(registry, llm);
        // Should not throw — should loop and eventually return
        LlmResponse response = executor.executeWithTools(
            "Agent.", "Call an unknown tool.",
            new LlmOptions(0.3f, 512, null));

        // The executor will feed back an error result and loop until maxIter
        assert response != null : "Executor should not throw on unknown tool";
    }

    void ST08_squadToolAnnotationReadable() throws Exception {
        var method = StockAgent.class.getDeclaredMethod("getStockPrice", String.class);
        SquadTool ann = method.getAnnotation(SquadTool.class);

        assert ann != null                                 : "@SquadTool should be present";
        assert "getStockPrice".equals(ann.name())         : "Name should be getStockPrice";
        assert ann.description().contains("stock price")  : "Description should match";
        assert ann.maxIterations() == 3                   : "maxIterations should be 3";
    }

    void ST09_toolParamAnnotationReadable() throws Exception {
        var method = StockAgent.class.getDeclaredMethod("getStockPrice", String.class);
        var params = method.getParameters();

        assert params.length == 1 : "Should have 1 parameter";
        ToolParam ann = params[0].getAnnotation(ToolParam.class);
        assert ann != null                              : "@ToolParam should be present";
        assert ann.description().contains("ticker")    : "Description should mention ticker";
        assert ann.required()                          : "Parameter should be required by default";
    }

    void ST10_toolsPromptIncludesParameters() {
        SquadToolRegistry registry = new SquadToolRegistry();
        registry.register(new StockAgent());

        String prompt = registry.buildToolsPrompt();
        // getStockPrice has a "ticker" parameter
        assert prompt.contains("ticker")    : "Prompt should include parameter name 'ticker'";
        assert prompt.contains("string")    : "Prompt should include parameter type";
    }
}
