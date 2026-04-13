package io.squados.mcp.server;

/**
 * Describes a single MCP tool derived from a SquadOS @Agent class.
 *
 * Each @Agent becomes one tool:
 *   - name        — from @Agent(name=...) or simple class name
 *   - description — from @Agent(description=...)
 *   - inputSchema — fixed JSON Schema with a single "task" string property
 *   - serverUrl   — empty string for locally-hosted tools
 */
public final class McpToolDefinition {

    private final String name;
    private final String description;
    private final String inputSchemaJson;
    private final String serverUrl;

    public McpToolDefinition(String name, String description,
                              String inputSchemaJson, String serverUrl) {
        this.name            = name;
        this.description     = description;
        this.inputSchemaJson = inputSchemaJson;
        this.serverUrl       = serverUrl;
    }

    public String name()            { return name; }
    public String description()     { return description; }
    public String inputSchemaJson() { return inputSchemaJson; }
    public String serverUrl()       { return serverUrl; }

    /** Standard input schema JSON for all agent tools. */
    public static String standardInputSchema() {
        return "{\"type\":\"object\""
                + ",\"properties\":{"
                + "\"task\":{\"type\":\"string\",\"description\":\"The task to execute\"}"
                + "}"
                + ",\"required\":[\"task\"]}";
    }

    @Override
    public String toString() {
        return "McpToolDefinition{name='" + name + "', description='" + description + "'}";
    }
}
