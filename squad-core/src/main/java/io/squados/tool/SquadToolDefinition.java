package io.squados.tool;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Immutable descriptor for a single @SquadTool method.
 * Contains everything needed to invoke the tool and
 * describe it to the LLM in JSON schema format.
 */
public class SquadToolDefinition {

    public record ToolParamDef(
        String name,
        String type,
        String description,
        boolean required
    ) {}

    private final String              name;
    private final String              description;
    private final Method              method;
    private final Object              target;
    private final List<ToolParamDef>  params;
    private final int                 maxIterations;

    public SquadToolDefinition(String name, String description,
                               Method method, Object target,
                               List<ToolParamDef> params, int maxIterations) {
        this.name          = name;
        this.description   = description;
        this.method        = method;
        this.target        = target;
        this.params        = Collections.unmodifiableList(params);
        this.maxIterations = maxIterations;
    }

    public String              getName()          { return name; }
    public String              getDescription()   { return description; }
    public Method              getMethod()        { return method; }
    public Object              getTarget()        { return target; }
    public List<ToolParamDef>  getParams()        { return params; }
    public int                 getMaxIterations() { return maxIterations; }

    /**
     * Build the JSON schema string for this tool.
     * Appended to the agent system prompt so the LLM knows when and how to call it.
     *
     * Format (OpenAI-compatible function calling schema):
     * <pre>
     * Tool: getStockPrice
     * Description: Get current stock price for a ticker
     * Parameters:
     *   - ticker (string, required): Stock ticker e.g. AAPL
     *   - currency (string, optional): Currency e.g. USD
     * Call format: TOOL_CALL: getStockPrice {"ticker": "AAPL"}
     * </pre>
     */
    public String toSchemaPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(name).append("\n");
        sb.append("Description: ").append(description).append("\n");
        if (!params.isEmpty()) {
            sb.append("Parameters:\n");
            for (ToolParamDef p : params) {
                sb.append("  - ").append(p.name())
                  .append(" (").append(p.type())
                  .append(p.required() ? ", required" : ", optional").append(")")
                  .append(": ").append(p.description()).append("\n");
            }
        }
        sb.append("Call format: TOOL_CALL: ").append(name).append(" {");
        for (int i = 0; i < params.size(); i++) {
            ToolParamDef p = params.get(i);
            sb.append("\"").append(p.name()).append("\": \"<value>\"");
            if (i < params.size() - 1) sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }
}