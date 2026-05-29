package io.squados.boot;

import io.squados.mcp.McpToolDefinition;
import io.squados.mcp.McpToolProvider;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * HTTP client for MCP (Model Context Protocol) tool servers.
 *
 * Registered as a Spring bean when squad.mcp.enabled=true.
 * Injected into agents annotated with @McpServer by SquadAutoConfiguration.
 *
 * Implements the MCP discovery and invocation protocol over HTTP.
 */
public class HttpMcpClient implements McpToolProvider {

    private final HttpClient http;
    private final int        timeoutMs;

    public HttpMcpClient(int timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
    }

    @Override
    public List<McpToolDefinition> discoverTools(String serverUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/tools"))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return parseToolsJson(resp.body(), serverUrl);
            }
        } catch (Exception e) {
            System.err.println("[SquadOS] MCP tool discovery failed for " + serverUrl + ": " + e.getMessage());
        }
        return List.of();
    }

    @Override
    public String invokeTool(String serverUrl, String toolName, Map<String, Object> arguments) {
        try {
            String body = buildInvokeJson(toolName, arguments);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/tools/invoke"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (Exception e) {
            return "{\"error\": \"Tool invocation failed: " + e.getMessage() + "\"}";
        }
    }

    // ── Simple JSON handling (no external deps) ───────────────────────

    private List<McpToolDefinition> parseToolsJson(String json, String serverUrl) {
        // Simplified parser — production would use Jackson from Spring context
        List<McpToolDefinition> tools = new ArrayList<>();
        // Extract name/description pairs from simple JSON array
        String[] entries = json.split("\\{");
        for (String entry : entries) {
            if (entry.contains("\"name\"")) {
                String name  = extractString(entry, "name");
                String desc  = extractString(entry, "description");
                if (name != null && !name.isBlank()) {
                    tools.add(new McpToolDefinition(name, desc != null ? desc : "", Map.of(), serverUrl));
                }
            }
        }
        return tools;
    }

    private String buildInvokeJson(String toolName, Map<String, Object> arguments) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"tool\":\"").append(toolName).append("\",");
        sb.append("\"arguments\":{");
        arguments.forEach((k, v) ->
            sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (!arguments.isEmpty()) sb.setLength(sb.length() - 1);
        sb.append("}}");
        return sb.toString();
    }

    private String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx + search.length());
        if (colon < 0) return null;
        int start = json.indexOf("\"", colon + 1);
        if (start < 0) return null;
        int end = json.indexOf("\"", start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }
}
