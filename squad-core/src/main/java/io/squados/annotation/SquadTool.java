package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as a tool that LLM agents can invoke.
 *
 * The framework automatically:
 *   1. Discovers all @SquadTool methods in the agent class
 *   2. Builds a JSON schema for each tool from parameter types
 *   3. Appends tool definitions to the agent system prompt
 *   4. Intercepts tool_call responses from the LLM
 *   5. Invokes the Java method with parsed arguments
 *   6. Feeds the result back to the LLM as a tool_result
 *   7. Repeats until the LLM returns a final text response
 *
 * Supported parameter types: String, int, Integer, long, Long,
 *   double, Double, boolean, Boolean
 *
 * Usage:
 * <pre>
 * {@literal @}Agent(role = AgentRole.ANALYST, name = "StockBot", description = "...")
 * public class StockBotAgent {
 *
 *     {@literal @}SquadTool(
 *         name = "getStockPrice",
 *         description = "Get the current stock price for a ticker symbol"
 *     )
 *     public String getStockPrice(
 *         {@literal @}ToolParam(description = "Stock ticker e.g. AAPL, TSLA") String ticker
 *     ) {
 *         return stockService.getPrice(ticker);
 *     }
 * }
 * </pre>
 *
 * The LLM will call this tool when it needs a stock price,
 * and receive the result before formulating its final response.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface SquadTool {

    /** Tool name exposed to the LLM. Defaults to method name. */
    String name() default "";

    /** Human-readable description — tells the LLM WHEN to use this tool. */
    String description();

    /**
     * Maximum number of tool call iterations before giving up.
     * Prevents infinite loops if the LLM keeps calling tools.
     */
    int maxIterations() default 5;
}