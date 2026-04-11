package io.squados.mcp;

import java.util.List;

/**
 * SPI for discovering and invoking tools from MCP (Model Context Protocol) servers.
 *
 * Implementations:
 *   - HttpMcpClient (in squad-spring-boot-starter) — real HTTP discovery
 *   - MockMcpToolProvider — for tests
 */
public interface McpToolProvider {

    /**
     * Discover all tools from the given MCP server URL.
     * Results are injected into the agent's system prompt.
     */
    List<McpToolDefinition> discoverTools(String serverUrl);

    /**
     * Invoke a tool and return the result string.
     *
     * @param serverUrl  MCP server base URL
     * @param toolName   Name of the tool to invoke
     * @param arguments  Tool arguments as a key-value map
     * @return           Tool result as a string (typically JSON)
     */
    String invokeTool(String serverUrl, String toolName,
                      java.util.Map<String, Object> arguments);

    /**
     * Build the MCP tools section of the system prompt from a list of definitions.
     */
    default String buildMcpPrompt(List<McpToolDefinition> tools) {
        if (tools.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n## Available MCP Tools\n");
        for (McpToolDefinition tool : tools) {
            sb.append("\n").append(tool.toPromptEntry());
        }
        return sb.toString();
    }
}
