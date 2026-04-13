package io.squados.mcp.server;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java JSON-RPC 2.0 parsing and building for the MCP server.
 *
 * All parsing is done with regex and string manipulation — no external
 * JSON library required. This keeps the module dependency-free beyond JDK
 * and squad-core.
 *
 * Thread-safe (all methods are static and stateless).
 */
public final class McpJsonRpc {

    /** JSON-RPC error codes */
    public static final int PARSE_ERROR      = -32700;
    public static final int INVALID_REQUEST  = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS   = -32602;
    public static final int INTERNAL_ERROR   = -32603;

    private static final Pattern ID_PATTERN =
        Pattern.compile("\"id\"\\s*:\\s*(\"[^\"]*\"|\\d+|-\\d+|null)");
    private static final Pattern METHOD_PATTERN =
        Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PARAMS_PATTERN =
        Pattern.compile("\"params\"\\s*:\\s*\\{([^}]*)\\}");

    private McpJsonRpc() {}

    // ── Parsing ───────────────────────────────────────────────────────

    /**
     * Extract the JSON-RPC id field.
     * Returns the raw value (quoted string or number literal) as a String.
     * Returns "null" if id is absent or null.
     */
    public static String parseId(String json) {
        if (json == null) return "null";
        Matcher m = ID_PATTERN.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "null";
    }

    /**
     * Extract the JSON-RPC method field.
     * Returns null if not present.
     */
    public static String parseMethod(String json) {
        if (json == null) return null;
        Matcher m = METHOD_PATTERN.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extract a string value by key from the params object in a JSON-RPC request.
     *
     * Handles:
     *   - "key":"value"  (plain string)
     *   - "key": "value" (space before value)
     *
     * Returns null if the key is not found.
     */
    public static String parseStringParam(String json, String key) {
        if (json == null || key == null) return null;
        // Match "key":"value" with possible whitespace, handling escaped quotes inside value
        Pattern p = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }
        return null;
    }

    /**
     * Extract a string value from a nested object in params.
     * e.g. parseNestedParam(json, "arguments", "task") for params.arguments.task
     */
    public static String parseNestedStringParam(String json, String outerKey, String innerKey) {
        if (json == null) return null;
        // Find the outer object value
        Pattern outerP = Pattern.compile(
            "\"" + Pattern.quote(outerKey) + "\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher outerM = outerP.matcher(json);
        if (!outerM.find()) return null;
        String inner = "{" + outerM.group(1) + "}";
        return parseStringParam(inner, innerKey);
    }

    // ── Building ──────────────────────────────────────────────────────

    /**
     * Build a JSON-RPC 2.0 success response.
     *
     * @param id         the raw id value (e.g. "1", "\"req-1\"", "null")
     * @param resultJson the JSON string for the result field
     */
    public static String buildSuccess(String id, String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}";
    }

    /**
     * Build a JSON-RPC 2.0 error response.
     *
     * @param id      the raw id value
     * @param code    JSON-RPC error code (e.g. -32601)
     * @param message human-readable error message
     */
    public static String buildError(String id, int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"error\":{\"code\":" + code
                + ",\"message\":" + jsonString(message) + "}}";
    }

    /**
     * Build the result JSON for an MCP initialize response.
     */
    public static String buildInitializeResult(String serverName, String version) {
        return "{\"protocolVersion\":\"2024-11-05\""
                + ",\"capabilities\":{\"tools\":{}}"
                + ",\"serverInfo\":{\"name\":" + jsonString(serverName)
                + ",\"version\":" + jsonString(version) + "}}";
    }

    /**
     * Build the result JSON for a tools/list response.
     *
     * @param tools list of tool definitions to include
     */
    public static String buildToolsListResult(List<McpToolDefinition> tools) {
        StringBuilder sb = new StringBuilder("{\"tools\":[");
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toolToJson(tools.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Build the result JSON for a tools/call response.
     *
     * @param text the text content to return
     */
    public static String buildToolsCallResult(String text) {
        return "{\"content\":[{\"type\":\"text\",\"text\":" + jsonString(text) + "}]}";
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String toolToJson(McpToolDefinition tool) {
        return "{\"name\":" + jsonString(tool.name())
                + ",\"description\":" + jsonString(tool.description())
                + ",\"inputSchema\":" + tool.inputSchemaJson() + "}";
    }

    /**
     * Escape a Java string as a JSON string literal (including surrounding quotes).
     */
    public static String jsonString(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String unescapeJson(String escaped) {
        if (escaped == null) return null;
        return escaped
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    }
}
