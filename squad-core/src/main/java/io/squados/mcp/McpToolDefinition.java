package io.squados.mcp;

import java.util.Map;

/**
 * Describes a single tool discovered from an MCP server.
 *
 * name        — Tool identifier (e.g. "get_weather")
 * description — Human-readable description injected into agent system prompt
 * parameters  — JSON-Schema-style parameter map (name → type)
 * serverUrl   — Origin MCP server URL
 */
public record McpToolDefinition(
        String              name,
        String              description,
        Map<String, String> parameters,
        String              serverUrl
) {
    /** Renders a prompt-friendly tool descriptor. */
    public String toPromptEntry() {
        StringBuilder sb = new StringBuilder();
        sb.append("TOOL: ").append(name).append(" — ").append(description);
        if (!parameters.isEmpty()) {
            sb.append("\n  Parameters: ");
            parameters.forEach((k, v) -> sb.append(k).append("(").append(v).append(") "));
        }
        return sb.toString();
    }
}
