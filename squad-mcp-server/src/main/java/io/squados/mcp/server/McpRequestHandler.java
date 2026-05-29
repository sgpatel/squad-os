package io.squados.mcp.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.config.SquadConfig;
import io.squados.context.AgentRegistry;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.squados.llm.LlmPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * HTTP handler that implements the MCP JSON-RPC 2.0 protocol.
 *
 * Dispatches incoming POST requests to:
 * <ul>
 *   <li>{@code initialize}  — returns server capabilities</li>
 *   <li>{@code tools/list}  — returns all registered agents as tools</li>
 *   <li>{@code tools/call}  — executes a specific agent with the given task</li>
 * </ul>
 *
 * The handler boots a {@link SquadContext} from the provided agent classes and
 * LLM port at construction time.
 */
public final class McpRequestHandler implements HttpHandler {

    private final McpServerConfig          config;
    private final List<McpToolDefinition>  tools;
    private final Map<String, AgentWrapper> agentMap;
    private final SquadContext             ctx;

    /**
     * Create the handler, boot the SquadContext, and build the agent map.
     *
     * @param config       server configuration
     * @param agentClasses list of @Agent-annotated classes to expose
     * @param llm          LLM port to use for agent execution
     */
    public McpRequestHandler(McpServerConfig config,
                              List<Class<?>> agentClasses,
                              LlmPort llm) {
        this.config = config;
        this.tools  = McpToolExporter.export(agentClasses);

        // Boot a SquadContext with all agent classes
        SquadConfig squadConfig = SquadConfig.forTesting(agentClasses);
        this.ctx = new SquadContext(squadConfig, llm);
        this.ctx.boot();

        // Build name → wrapper map for targeted execution
        this.agentMap = new LinkedHashMap<>();
        AgentRegistry registry = ctx.getRegistry();
        for (AgentWrapper wrapper : registry.all()) {
            agentMap.put(wrapper.getName(), wrapper);
        }
    }

    // ── HttpHandler ───────────────────────────────────────────────────

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        // Only accept POST
        if (!"POST".equalsIgnoreCase(method)) {
            sendResponse(exchange, 405, buildMethodNotAllowedError());
            return;
        }

        // Read request body (bounded by maxRequestBytes)
        String body = readBody(exchange);
        if (body == null || body.isBlank()) {
            String id = "null";
            String response = McpJsonRpc.buildError(id,
                McpJsonRpc.PARSE_ERROR, "Empty request body");
            sendResponse(exchange, 200, response);
            return;
        }

        // Dispatch JSON-RPC method
        String id         = McpJsonRpc.parseId(body);
        String rpcMethod  = McpJsonRpc.parseMethod(body);

        if (rpcMethod == null) {
            sendResponse(exchange, 200,
                McpJsonRpc.buildError(id, McpJsonRpc.INVALID_REQUEST, "Missing method"));
            return;
        }

        String responseJson = switch (rpcMethod) {
            case "initialize"  -> handleInitialize(id);
            case "tools/list"  -> handleToolsList(id);
            case "tools/call"  -> handleToolsCall(id, body);
            default -> McpJsonRpc.buildError(id,
                McpJsonRpc.METHOD_NOT_FOUND, "Unknown method: " + rpcMethod);
        };

        sendResponse(exchange, 200, responseJson);
    }

    // ── Method handlers ───────────────────────────────────────────────

    private String handleInitialize(String id) {
        String result = McpJsonRpc.buildInitializeResult(
            config.serverName(), config.serverVersion());
        return McpJsonRpc.buildSuccess(id, result);
    }

    private String handleToolsList(String id) {
        String result = McpJsonRpc.buildToolsListResult(tools);
        return McpJsonRpc.buildSuccess(id, result);
    }

    private String handleToolsCall(String id, String body) {
        // Extract tool name from params.name
        String toolName = McpJsonRpc.parseStringParam(body, "name");
        if (toolName == null || toolName.isBlank()) {
            return McpJsonRpc.buildError(id,
                McpJsonRpc.INVALID_PARAMS, "Missing tool name in params");
        }

        // Extract task from params.arguments.task
        String task = McpJsonRpc.parseNestedStringParam(body, "arguments", "task");
        if (task == null) task = "";

        // Look up agent
        AgentWrapper wrapper = agentMap.get(toolName);
        if (wrapper == null) {
            return McpJsonRpc.buildError(id,
                McpJsonRpc.METHOD_NOT_FOUND, "Tool not found: " + toolName);
        }

        // Execute agent
        try {
            TaskContext taskCtx = new TaskContext(task, "mcp-" + System.currentTimeMillis(), "default");
            AgentResponse response = wrapper.execute(taskCtx);
            String content = response.isSuccess()
                    ? (response.content() != null ? response.content() : "")
                    : ("Error: " + response.errorMessage());
            return McpJsonRpc.buildSuccess(id, McpJsonRpc.buildToolsCallResult(content));
        } catch (Exception e) {
            return McpJsonRpc.buildError(id,
                McpJsonRpc.INTERNAL_ERROR, "Agent execution failed: " + e.getMessage());
        }
    }

    // ── I/O helpers ───────────────────────────────────────────────────

    private String readBody(HttpExchange exchange) throws IOException {
        int maxBytes = config.maxRequestBytes();
        try (InputStream is = exchange.getRequestBody()) {
            byte[] buf = is.readNBytes(maxBytes);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildMethodNotAllowedError() {
        return McpJsonRpc.buildError("null",
            McpJsonRpc.INVALID_REQUEST, "Only POST is supported");
    }
}
