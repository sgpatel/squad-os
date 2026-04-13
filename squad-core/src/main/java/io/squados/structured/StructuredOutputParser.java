package io.squados.structured;

import io.squados.exception.StructuredOutputException;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and deserialises JSON from an LLM response into a typed POJO.
 *
 * Strategy:
 *   1. Try to find a JSON object in the raw response (even if embedded in prose)
 *   2. Parse the JSON manually (zero external dependencies — no Jackson/Gson)
 *   3. Populate fields via reflection
 *   4. Validate required fields are present
 *   5. On failure: re-prompt with parse error if {@code retryOnMalformed=true}
 *
 * Supports: String, int/Integer, long/Long, double/Double, float/Float, boolean/Boolean.
 * Nested objects are stored as raw String (JSON substring).
 */
public class StructuredOutputParser {

    private static final Pattern JSON_BLOCK = Pattern.compile(
        "```(?:json)?\\s*([\\s\\S]*?)```|\\{[\\s\\S]*\\}", Pattern.DOTALL);

    private StructuredOutputParser() {}

    /**
     * Parse {@code rawContent} into an instance of {@code schemaClass}.
     * If parsing fails and the LLM port is available, retries up to {@code maxRetries}.
     */
    public static <T> StructuredOutputResult<T> parse(
            String rawContent,
            Class<T> schemaClass,
            boolean retryOnMalformed,
            int maxRetries,
            String systemPrompt,
            LlmPort llm,
            LlmOptions options) {

        int attempts = 0;
        String currentContent = rawContent;
        String lastError = null;

        while (attempts <= maxRetries) {
            attempts++;
            try {
                String json = extractJson(currentContent);
                T instance  = populate(schemaClass, json);
                validateRequired(schemaClass, json);
                return StructuredOutputResult.success(instance, rawContent, attempts);
            } catch (Exception e) {
                lastError = e.getMessage();
                System.err.printf("[StructuredOutput] Parse attempt %d failed for %s: %s%n",
                    attempts, schemaClass.getSimpleName(), lastError);

                if (!retryOnMalformed || llm == null || attempts > maxRetries) break;

                // Re-prompt with the error message
                String retryPrompt = buildRetryPrompt(currentContent, lastError,
                    JsonSchemaGenerator.buildJsonTemplate(schemaClass));
                LlmResponse retryResp = llm.chat(systemPrompt, retryPrompt, options);
                currentContent = retryResp != null ? retryResp.content() : "";
            }
        }

        throw new StructuredOutputException(schemaClass.getSimpleName(), lastError, attempts - 1);
    }

    // ── JSON extraction ───────────────────────────────────────────────

    public static String extractJson(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Empty LLM response");
        }
        // Try markdown fence first, then bare JSON object
        Matcher m = JSON_BLOCK.matcher(content);
        while (m.find()) {
            String candidate = m.group(1) != null ? m.group(1).trim() : m.group(0).trim();
            if (candidate.startsWith("{") && candidate.endsWith("}")) {
                return candidate;
            }
        }
        // Fallback: find outermost { }
        int start = content.indexOf('{');
        int end   = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        throw new IllegalArgumentException("No JSON object found in response: "
            + content.substring(0, Math.min(120, content.length())));
    }

    // ── Reflection-based population ───────────────────────────────────

    @SuppressWarnings("unchecked")
    public static <T> T populate(Class<T> cls, String json) throws Exception {
        var ctor = cls.getDeclaredConstructor();
        ctor.setAccessible(true);
        T instance = ctor.newInstance();
        List<Field> fields = JsonSchemaGenerator.collectFields(cls);
        for (Field field : fields) {
            field.setAccessible(true);
            String val = extractValue(json, field.getName());
            if (val == null) continue;
            setField(instance, field, val);
        }
        return instance;
    }

    private static void setField(Object instance, Field field, String rawValue) throws Exception {
        Class<?> type = field.getType();
        String   v    = rawValue.trim();
        // Strip surrounding quotes for string values
        if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);

        Object coerced;
        if      (type == String.class)                coerced = v;
        else if (type == int.class || type == Integer.class)   coerced = Integer.parseInt(v);
        else if (type == long.class || type == Long.class)     coerced = Long.parseLong(v);
        else if (type == double.class || type == Double.class) coerced = Double.parseDouble(v);
        else if (type == float.class || type == Float.class)   coerced = Float.parseFloat(v);
        else if (type == boolean.class || type == Boolean.class) coerced = Boolean.parseBoolean(v);
        else coerced = v; // nested objects / arrays stored as raw string

        field.set(instance, coerced);
    }

    // ── Minimal JSON value extraction (no external deps) ─────────────

    /**
     * Extract the raw value string for {@code key} from a flat JSON object.
     * Returns null if the key is absent. Does not recurse into nested objects.
     */
    static String extractValue(String json, String key) {
        // Match "key": <value> where value is string, number, boolean, null, or nested object
        Pattern p = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*"
            + "(?:"
            + "\"((?:[^\"\\\\]|\\\\.)*)\"" // quoted string  → group 1
            + "|(\\{[^}]*\\})"              // nested object  → group 2
            + "|(\\[[^\\]]*\\])"            // array          → group 3
            + "|([\\-\\d.eE+]+)"            // number         → group 4
            + "|(true|false|null)"          // literal        → group 5
            + ")"
        );
        Matcher m = p.matcher(json);
        if (!m.find()) return null;

        for (int g = 1; g <= 5; g++) {
            if (m.group(g) != null) {
                return (g == 1) ? "\"" + m.group(g) + "\"" : m.group(g);
            }
        }
        return null;
    }

    private static void validateRequired(Class<?> cls, String json) {
        List<String> required = JsonSchemaGenerator.requiredFields(cls);
        for (String field : required) {
            if (extractValue(json, field) == null) {
                throw new IllegalArgumentException(
                    "Required field '" + field + "' missing from JSON");
            }
        }
    }

    private static String buildRetryPrompt(String previousResponse, String error, String schema) {
        return "Your previous response could not be parsed as valid JSON.\n"
             + "Parse error: " + error + "\n\n"
             + "Previous response was:\n" + previousResponse + "\n\n"
             + "Please respond ONLY with a valid JSON object matching:\n" + schema + "\n"
             + "No prose, no markdown fences — just the JSON object.";
    }
}
