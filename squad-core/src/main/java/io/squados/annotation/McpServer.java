package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Connect this agent to one or more external MCP (Model Context Protocol) tool servers.
 *
 * urls     — MCP server base URLs to discover tools from
 * timeoutMs — Per-request timeout for tool discovery and invocation
 *
 * Tools discovered from MCP servers are automatically injected into the agent's
 * system prompt via McpToolProvider.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpServer {
    String[] urls()      default {};
    int      timeoutMs() default 5000;
}
