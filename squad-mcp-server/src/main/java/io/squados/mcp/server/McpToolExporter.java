package io.squados.mcp.server;

import io.squados.annotation.Agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans a list of agent classes annotated with {@code @Agent} and returns
 * their MCP tool definitions.
 *
 * Each {@code @Agent}-annotated class becomes one tool with:
 * <ul>
 *   <li>name        — {@code @Agent(name=...)} or the simple class name</li>
 *   <li>description — {@code @Agent(description=...)}</li>
 *   <li>inputSchema — fixed schema with a single "task" string property</li>
 * </ul>
 *
 * Classes without {@code @Agent} are silently skipped.
 */
public final class McpToolExporter {

    private McpToolExporter() {}

    /**
     * Export a list of agent classes as MCP tool definitions.
     *
     * @param agentClasses classes to inspect (non-null, may be empty)
     * @return list of tool definitions, one per {@code @Agent}-annotated class
     */
    public static List<McpToolDefinition> export(List<Class<?>> agentClasses) {
        List<McpToolDefinition> tools = new ArrayList<>();
        if (agentClasses == null) return tools;

        for (Class<?> cls : agentClasses) {
            Agent ann = cls.getAnnotation(Agent.class);
            if (ann == null) continue;

            String name = (ann.name() == null || ann.name().isBlank())
                    ? cls.getSimpleName()
                    : ann.name();

            String description = ann.description();

            tools.add(new McpToolDefinition(
                name,
                description,
                McpToolDefinition.standardInputSchema(),
                "" // local server — no remote URL
            ));
        }

        return tools;
    }
}
