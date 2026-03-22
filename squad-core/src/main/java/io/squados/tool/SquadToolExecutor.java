package io.squados.tool;

import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Executes the tool-call / tool-result loop between the LLM and Java methods.
 *
 * Flow:
 * <pre>
 *   1. LLM responds with: TOOL_CALL: getStockPrice {"ticker": "AAPL"}
 *   2. Executor parses tool name and JSON args
 *   3. Invokes the Java method via reflection
 *   4. Feeds TOOL_RESULT back to LLM as a new message
 *   5. LLM uses the result to formulate final response
 *   6. Repeats until LLM returns text without TOOL_CALL
 * </pre>
 *
 * All without any changes to the LlmPort interface.
 * Works with Ollama, OpenAI, Anthropic — any LLM that follows the prompt.
 */
public class SquadToolExecutor {

    private static final String TOOL_CALL_PREFIX = "TOOL_CALL:";
    private static final String TOOL_RESULT_PREFIX = "TOOL_RESULT:";

    private final SquadToolRegistry registry;
    private final LlmPort           llm;

    public SquadToolExecutor(SquadToolRegistry registry, LlmPort llm) {
        this.registry = registry;
        this.llm      = llm;
    }

    /**
     * Execute a prompt with tool-call loop support.
     *
     * @param systemPrompt Agent system prompt (tools appended automatically)
     * @param userMessage  User input
     * @param options      LLM options
     * @return             Final LLM response after all tool calls resolved
     */
    public LlmResponse executeWithTools(String systemPrompt,
                                        String userMessage,
                                        LlmOptions options) {
        // Append tool definitions to system prompt
        String enrichedSystem = systemPrompt + registry.buildToolsPrompt();
        String currentMessage = userMessage;
        int maxIter = registry.getAll().values().stream()
            .mapToInt(SquadToolDefinition::getMaxIterations).max().orElse(5);

        for (int iteration = 0; iteration < maxIter; iteration++) {
            // Call LLM
            LlmResponse response = llm.chat(enrichedSystem, currentMessage, options);
            String content = response.content().trim();

            // Check if LLM wants to call a tool
            int toolCallIdx = content.indexOf(TOOL_CALL_PREFIX);
            if (toolCallIdx < 0) {
                // No tool call — this is the final response
                return response;
            }

            // Parse the tool call
            String toolCallLine = extractToolCallLine(content, toolCallIdx);
            System.out.printf("[SquadTool] Tool call detected: %s%n", toolCallLine);

            ToolCallRequest request = parseToolCall(toolCallLine);
            if (request == null) {
                System.out.printf("[SquadTool] Could not parse tool call: %s%n", toolCallLine);
                return response; // Return as-is
            }

            // Execute the tool
            String toolResult = executeToolCall(request);
            System.out.printf("[SquadTool] %s() returned: %s%n",
                request.toolName(), toolResult);

            // Feed result back — append to conversation
            currentMessage = currentMessage +
                "\n\n" + TOOL_CALL_PREFIX + " " + toolCallLine +
                "\n" + TOOL_RESULT_PREFIX + " " + request.toolName() +
                ": " + toolResult +
                "\n\nNow use this result to answer the original question.";
        }

        // Max iterations reached — final call without tool loop
        return llm.chat(enrichedSystem, currentMessage + "\n\nPlease provide your final answer.", options);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String extractToolCallLine(String content, int startIdx) {
        int lineStart = startIdx + TOOL_CALL_PREFIX.length();
        int lineEnd   = content.indexOf("\n", lineStart);
        return lineEnd < 0
            ? content.substring(lineStart).trim()
            : content.substring(lineStart, lineEnd).trim();
    }

    record ToolCallRequest(String toolName, Map<String, String> args) {}

    private ToolCallRequest parseToolCall(String line) {
        // Format: toolName {"key": "value", "key2": "value2"}
        int braceIdx = line.indexOf("{");
        if (braceIdx < 0) {
            // No args
            return new ToolCallRequest(line.trim(), new LinkedHashMap<>());
        }
        String toolName = line.substring(0, braceIdx).trim();
        String jsonArgs = line.substring(braceIdx);
        Map<String, String> args = parseSimpleJson(jsonArgs);
        return new ToolCallRequest(toolName, args);
    }

    private String executeToolCall(ToolCallRequest request) {
        SquadToolDefinition def = registry.get(request.toolName());
        if (def == null) {
            return "ERROR: Unknown tool '" + request.toolName() + "'";
        }
        try {
            Method method = def.getMethod();
            Object[] params = buildParams(method, request.args());
            Object result = method.invoke(def.getTarget(), params);
            return result == null ? "null" : result.toString();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.err.printf("[SquadTool] Error executing %s: %s%n",
                request.toolName(), cause.getMessage());
            return "ERROR: " + cause.getMessage();
        }
    }

    private Object[] buildParams(Method method, Map<String, String> args) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        Object[] result = new Object[params.length];
        // Use position-based matching — Java reflection does not preserve
        // parameter names at runtime unless compiled with -parameters flag.
        // JSON args are stored in insertion order (LinkedHashMap), so
        // position in map matches position in method signature.
        List<String> values = new ArrayList<>(args.values());
        for (int i = 0; i < params.length; i++) {
            String value = i < values.size() ? values.get(i) : "";
            result[i] = coerce(value, params[i].getType());
        }
        return result;
    }

    private Object coerce(String value, Class<?> type) {
        if (type == String.class)  return value;
        if (type == int.class || type == Integer.class)
            return value.isEmpty() ? 0 : Integer.parseInt(value.trim());
        if (type == long.class || type == Long.class)
            return value.isEmpty() ? 0L : Long.parseLong(value.trim());
        if (type == double.class || type == Double.class)
            return value.isEmpty() ? 0.0 : Double.parseDouble(value.trim());
        if (type == boolean.class || type == Boolean.class)
            return Boolean.parseBoolean(value.trim());
        return value;
    }

    /** Parse a simple flat JSON object {"k":"v","k2":"v2"} */
    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        String inner = json.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}"))   inner = inner.substring(0, inner.length()-1);
        int i = 0;
        while (i < inner.length()) {
            // skip whitespace and commas
            while (i < inner.length() && " ,\n\r\t".indexOf(inner.charAt(i)) >= 0) i++;
            if (i >= inner.length() || inner.charAt(i) != '"') break;
            // read key
            int ks = i+1, ke = ks;
            while (ke < inner.length() && inner.charAt(ke) != '"') ke++;
            String key = inner.substring(ks, ke); i = ke+1;
            // skip colon
            while (i < inner.length() && inner.charAt(i) != ':') i++; i++;
            // skip whitespace
            while (i < inner.length() && " \n\r\t".indexOf(inner.charAt(i)) >= 0) i++;
            if (i >= inner.length()) break;
            // read value
            String value;
            if (inner.charAt(i) == '"') {
                int vs = i+1, ve = vs;
                while (ve < inner.length()) {
                    if (inner.charAt(ve) == '\\') { ve+=2; continue; }
                    if (inner.charAt(ve) == '"') break;
                    ve++;
                }
                value = inner.substring(vs, ve); i = ve+1;
            } else {
                int vs = i;
                while (i < inner.length() && inner.charAt(i) != ',' && inner.charAt(i) != '}') i++;
                value = inner.substring(vs, i).trim();
            }
            result.put(key, value);
        }
        return result;
    }
}